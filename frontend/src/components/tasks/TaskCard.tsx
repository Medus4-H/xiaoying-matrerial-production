"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useTaskTracker } from "@/features/tasks/TaskTrackerProvider";
import type { TaskSnapshot } from "@/lib/api/types";
import { stageNames, statusNames, stepNames } from "./task-labels";

function stepDuration(step: TaskSnapshot["steps"][number]): string {
  if (step.status !== "SUCCEEDED" && step.status !== "FAILED") return "";
  if (!step.startedAt || !step.finishedAt) return " · 耗时不可用";
  const duration = Date.parse(step.finishedAt) - Date.parse(step.startedAt);
  if (!Number.isFinite(duration) || duration < 0) return " · 耗时不可用";
  return ` · 耗时 ${(duration / 1000).toFixed(2)} 秒`;
}

export function TaskCard({
  task,
  detailed = false,
  onSelect,
  selected = false,
}: {
  task: TaskSnapshot;
  detailed?: boolean;
  onSelect?: () => void;
  selected?: boolean;
}) {
  const { tracker, state } = useTaskTracker();
  const tracked = state.tasks[task.id];
  const current = tracked?.snapshot ?? task;
  const [preview, setPreview] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  const close = () => {
    setPreview(false);
    window.setTimeout(() => trigger.current?.focus(), 0);
  };
  const date = new Date(current.createdAt);
  return (
    <article className="task-card">
      <div className="task-card-heading">
        <h3>{current.templateTitle}</h3>
        <span className={`task-status status-${current.status.toLowerCase()}`}>
          {statusNames[current.status]}
        </span>
      </div>
      <p className="task-time">
        创建时间：
        {Number.isNaN(date.getTime())
          ? current.createdAt
          : date.toLocaleString("zh-CN")}
      </p>
      {current.status === "RUNNING" && (
        <p>
          当前阶段：
          {current.currentStage ? stageNames[current.currentStage] : "准备中"}
        </p>
      )}
      {current.status === "FAILED" && (
        <p className="task-failure">
          {current.failedStage ? `${stageNames[current.failedStage]}：` : ""}
          {current.errorMessage || "任务处理失败"}
        </p>
      )}
      {tracked?.queryError && (
        <div className="query-error" role="alert">
          <p>
            状态查询暂时失败：{tracked.queryError}。保留上次状态（更新于{" "}
            {new Date(tracked.lastUpdated).toLocaleString("zh-CN")}）。
          </p>
          <button
            type="button"
            onClick={() => void tracker?.resume(current.id)}
          >
            恢复查询
          </button>
        </div>
      )}
      {detailed && (
        <div className="task-steps">
          <h4>处理步骤</h4>
          {current.steps.length ? (
            <ol>
              {current.steps.map((step) => (
                <li key={step.stage}>
                  {stageNames[step.stage]}：{stepNames[step.status]}
                  {stepDuration(step)}
                  {step.errorMessage ? ` · ${step.errorMessage}` : ""}
                </li>
              ))}
            </ol>
          ) : (
            <p>暂无步骤记录</p>
          )}
        </div>
      )}
      <div className="task-actions">
        {onSelect && (
          <button type="button" onClick={onSelect} aria-pressed={selected}>
            在预览区查看
          </button>
        )}
        {current.status === "SUCCEEDED" && current.resultUrl && (
          <button
            ref={trigger}
            type="button"
            className="task-result-preview"
            onClick={() => setPreview(true)}
            aria-label={`预览${current.templateTitle}的制作结果`}
          >
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={current.resultUrl} alt="制作结果缩略图" />
            <span>预览结果</span>
          </button>
        )}
        {!detailed && (
          <Link href={`/tasks/${encodeURIComponent(current.id)}`}>
            查看详情
          </Link>
        )}
      </div>
      {preview && current.resultUrl && (
        <ResultPreview url={current.resultUrl} onClose={close} />
      )}
    </article>
  );
}

function ResultPreview({ url, onClose }: { url: string; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const closeButton = useRef<HTMLButtonElement>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    const element = dialog.current;
    if (!element) return;
    if (typeof element.showModal === "function") element.showModal();
    else element.setAttribute("open", "");
    closeButton.current?.focus();
    return () => {
      if (element.open && typeof element.close === "function") element.close();
    };
  }, []);
  return (
    <dialog
      ref={dialog}
      className="result-dialog"
      aria-labelledby="result-dialog-title"
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onKeyDown={(event) => {
        if (event.key === "Escape") {
          event.preventDefault();
          onClose();
        }
      }}
    >
      <div className="dialog-heading">
        <h2 id="result-dialog-title">结果预览</h2>
        <button
          ref={closeButton}
          type="button"
          onClick={onClose}
          aria-label="关闭结果预览"
        >
          ×
        </button>
      </div>
      {failed ? (
        <p role="alert">结果图片暂时无法加载，请稍后重试。</p>
      ) : (
        <>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={url} alt="制作结果图片" onError={() => setFailed(true)} />
        </>
      )}
    </dialog>
  );
}
