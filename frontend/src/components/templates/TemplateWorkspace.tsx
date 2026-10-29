"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { useIdentity } from "@/components/identity/IdentityProvider";
import { useTaskTracker } from "@/features/tasks/TaskTrackerProvider";
import { TaskHistory } from "@/components/tasks/TaskHistory";
import { UploadSlot, type SlotValue } from "@/features/upload/UploadSlot";
import { ApiError } from "@/lib/api/client";
import { createTask } from "@/lib/api/tasks";
import type {
  CreateTaskRequest,
  TaskSnapshot,
  TaskStage,
  TemplateDetail,
} from "@/lib/api/types";

function formatFileSize(bytes: number): string {
  const units = ["B", "KiB", "MiB", "GiB"];
  let value = bytes;
  let index = 0;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index++;
  }
  return `${Math.round(value * 100) / 100} ${units[index]}`;
}

interface Props {
  template: TemplateDetail;
  showSimulationControls?: boolean;
  onAccepted?: (task: TaskSnapshot) => void;
}

export function TemplateWorkspace({
  template,
  showSimulationControls = false,
  onAccepted,
}: Props) {
  const identity = useIdentity();
  const { tracker } = useTaskTracker();
  const scope = `${identity.user?.id ?? ""}:${identity.epoch}:${template.id}`;
  const [selection, setSelection] = useState<{
    scope: string;
    id: string;
  } | null>(null);
  return (
    <div className="workspace">
      <section className="panel creation-panel">
        <Link href="/" className="back-link">
          ← 返回模板
        </Link>
        <h1>{template.title}</h1>
        <p>{template.description}</p>
        <h2>上传素材</h2>
        <p>
          {template.requiredImageCount} 张图片 ·{" "}
          {template.acceptedMimeTypes
            .map((type) => type.replace("image/", "").toUpperCase())
            .join(" / ")}{" "}
          · 每张不超过 {formatFileSize(template.maxFileSizeBytes)}
        </p>
        {identity.status === "ready" &&
        identity.user &&
        !identity.transitioning ? (
          <div hidden={identity.checking}>
            <CreationForm
              key={`${template.id}:${identity.user.id}:${identity.epoch}`}
              template={template}
              userId={identity.user.id}
              showSimulationControls={showSimulationControls}
              onAccepted={(task) => {
                setSelection({ scope, id: task.id });
                tracker?.register(task, true);
                onAccepted?.(task);
              }}
            />
          </div>
        ) : (
          <p role="status">
            {identity.status === "expired"
              ? "会话已过期，请先登录或建立新访客身份。"
              : identity.status === "error"
                ? "访客身份不可用，请先重试。"
                : "正在准备访客身份…"}
          </p>
        )}
        {identity.checking && <p role="status">正在确认账号…</p>}
      </section>
      <div className="panel">
        <TaskHistory
          key={template.id}
          templateId={template.id}
          workspace={{
            coverUrl: template.coverUrl,
            selectedTaskId: selection?.scope === scope ? selection.id : null,
            onSelect: (id) => setSelection({ scope, id }),
          }}
        />
      </div>
    </div>
  );
}

function CreationForm({
  template,
  showSimulationControls,
  onAccepted,
  userId,
}: Props & { userId: string }) {
  const [slots, setSlots] = useState<SlotValue[]>(() =>
    Array.from({ length: template.requiredImageCount }, () => ({
      asset: null,
      uploading: false,
    })),
  );
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [failAt, setFailAt] = useState<"" | TaskStage>("");
  const inFlight = useRef<AbortController | null>(null);
  const locked = useRef(false);
  const active = useRef(true);

  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      inFlight.current?.abort();
    };
  }, []);

  const changeSlot = useCallback((index: number, value: SlotValue) => {
    setSlots((previous) =>
      previous.map((item, i) => (i === index ? value : item)),
    );
  }, []);
  const ready =
    slots.length === template.requiredImageCount &&
    slots.every((slot) => slot.asset && !slot.uploading);

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!ready || locked.current) return;
    locked.current = true;
    setSubmitting(true);
    setSubmitError(null);
    const abort = new AbortController();
    inFlight.current = abort;
    const input: CreateTaskRequest = {
      templateId: template.id,
      assetIds: slots.map((slot) => slot.asset!.id),
    };
    if (showSimulationControls && failAt) input.simulation = { failAt };
    try {
      const task = await createTask(input, abort.signal, userId);
      if (!active.current || abort.signal.aborted) return;
      onAccepted?.(task);
    } catch (error) {
      if (!active.current || abort.signal.aborted) return;
      if (
        error instanceof ApiError &&
        error.status >= 400 &&
        error.status < 500 &&
        error.status !== 408
      )
        setSubmitError(`提交失败：${error.message}`);
      else
        setSubmitError(
          "无法确认任务是否受理，请先检查历史记录，再决定是否重新提交。",
        );
    } finally {
      if (active.current && !abort.signal.aborted) {
        locked.current = false;
        inFlight.current = null;
        setSubmitting(false);
      }
    }
  };

  return (
    <form onSubmit={submit}>
      <p className="upload-progress" role="status">
        已准备 {slots.filter((slot) => slot.asset && !slot.uploading).length} /{" "}
        {template.requiredImageCount} 张图片
      </p>
      <div className="upload-grid">
        {slots.map((value, index) => (
          <UploadSlot
            key={index}
            userId={userId}
            index={index}
            value={value}
            onChange={changeSlot}
            acceptedMimeTypes={template.acceptedMimeTypes}
            maxFileSizeBytes={template.maxFileSizeBytes}
            disabled={submitting}
          />
        ))}
      </div>
      {showSimulationControls && (
        <details className="simulation-settings" aria-label="模拟设置">
          <summary>模拟设置</summary>
          <label htmlFor="simulation-failure">指定失败阶段</label>
          <select
            id="simulation-failure"
            value={failAt}
            disabled={submitting}
            onChange={(event) =>
              setFailAt(event.target.value as "" | TaskStage)
            }
          >
            <option value="">正常执行</option>
            <option value="ASSET_CHECK">素材检查</option>
            <option value="GENERATE">模拟生成</option>
            <option value="FINALIZE">结果整理</option>
          </select>
          <p>仅用于演示；阶段耗时可在环境配置中调整。</p>
        </details>
      )}
      <button
        className="create-button"
        type="submit"
        disabled={!ready || submitting}
      >
        {submitting ? "提交中…" : "开始制作"}
      </button>
      {submitError && (
        <p role="alert" className="upload-error">
          {submitError}
        </p>
      )}
    </form>
  );
}
