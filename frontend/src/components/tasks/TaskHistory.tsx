"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { appConfig } from "@/config/app";
import { useIdentity } from "@/components/identity/IdentityProvider";
import { useTaskTracker } from "@/features/tasks/TaskTrackerProvider";
import { getTaskHistory } from "@/lib/api/tasks";
import { TaskCard } from "./TaskCard";
import { WorkspacePreview } from "./WorkspacePreview";

export function TaskHistory({
  templateId,
  workspace,
}: {
  templateId?: string;
  workspace?: {
    coverUrl: string;
    selectedTaskId: string | null;
    onSelect: (id: string) => void;
  };
}) {
  const identity = useIdentity();
  const { tracker, state } = useTaskTracker();
  const scope = `${identity.user?.id ?? ""}:${identity.epoch}:${templateId ?? ""}`;
  const [pagination, setPagination] = useState({ scope, page: 1 });
  const [exampleSelection, setExampleSelection] = useState<{
    scope: string;
    id: string;
  } | null>(null);
  const page = pagination.scope === scope ? pagination.page : 1;
  const pageSize = appConfig.taskHistoryPageSize;
  const history = useQuery({
    queryKey: [
      "tasks",
      identity.user?.id,
      "history",
      templateId ?? "",
      page,
      pageSize,
    ],
    queryFn: ({ signal }) =>
      getTaskHistory(page, pageSize, templateId, signal, identity.user!.id),
    enabled:
      identity.status === "ready" &&
      !identity.transitioning &&
      !identity.checking &&
      !!identity.user,
  });
  if (
    identity.status !== "ready" ||
    identity.transitioning ||
    identity.checking
  )
    return (
      <p role="status">
        {identity.checking
          ? "正在确认账号…"
          : identity.transitioning
            ? "正在切换账号…"
            : identity.status === "expired"
              ? "会话已过期，请先登录或建立新访客身份。"
              : identity.status === "error"
                ? "访客身份不可用，请先重试。"
                : "正在准备访客身份…"}
      </p>
    );
  const saved = history.data?.items ?? [];
  const ids = new Set(saved.map((item) => item.id));
  const newItems =
    page === 1
      ? Object.values(state.tasks)
          .filter(
            (entry) =>
              entry.createdHere &&
              !ids.has(entry.snapshot.id) &&
              (!templateId || entry.snapshot.templateId === templateId),
          )
          .map((entry) => entry.snapshot)
      : [];
  const items = [
    ...newItems,
    ...saved.map((task) => state.tasks[task.id]?.snapshot ?? task),
  ]
    .sort(
      (a, b) =>
        b.createdAt.localeCompare(a.createdAt) || b.id.localeCompare(a.id),
    )
    .slice(0, pageSize);
  const pages = Math.max(1, Math.ceil((history.data?.total ?? 0) / pageSize));
  const selectedTracked = workspace?.selectedTaskId
    ? state.tasks[workspace.selectedTaskId]
    : undefined;
  const selected =
    items.find((item) => item.id === workspace?.selectedTaskId) ??
    (selectedTracked?.createdHere &&
    (!templateId || selectedTracked.snapshot.templateId === templateId)
      ? selectedTracked.snapshot
      : undefined) ??
    items[0];
  const showExample =
    !!selected &&
    exampleSelection?.scope === scope &&
    exampleSelection.id === selected.id;
  return (
    <section
      className="history-panel"
      aria-label={templateId ? "当前模板制作记录" : "个人制作记录"}
    >
      {workspace && (
        <WorkspacePreview
          coverUrl={workspace.coverUrl}
          task={selected}
          showExample={showExample}
          onToggleExample={
            selected
              ? () =>
                  setExampleSelection(
                    showExample ? null : { scope, id: selected.id },
                  )
              : undefined
          }
          queryError={
            selected ? state.tasks[selected.id]?.queryError : undefined
          }
          onResume={
            selected ? () => void tracker?.resume(selected.id) : undefined
          }
        />
      )}
      <div className="history-heading">
        <h2>{templateId ? "制作记录" : "我的记录"}</h2>
        <button
          type="button"
          onClick={() => void history.refetch()}
          disabled={history.isFetching}
          aria-label="刷新记录"
        >
          刷新
        </button>
      </div>
      {state.loadError && (
        <div role="alert" className="query-error">
          <p>未完成任务恢复失败：{state.loadError}</p>
          <button type="button" onClick={() => void tracker?.reloadActive()}>
            重试恢复
          </button>
        </div>
      )}
      {state.loading && <p role="status">正在恢复未完成任务…</p>}
      {history.isPending && !history.data && <p role="status">加载记录…</p>}
      {history.isError && (
        <div role="alert" className="query-error">
          <p>记录加载失败：{history.error.message}</p>
          <button type="button" onClick={() => void history.refetch()}>
            重试
          </button>
        </div>
      )}
      {!history.isPending && !history.isError && items.length === 0 && (
        <p>暂无制作记录，提交任务后会显示在这里。</p>
      )}
      {items.length > 0 && (
        <div className="task-grid">
          {items.map((task) => (
            <TaskCard
              key={task.id}
              task={task}
              onSelect={
                workspace
                  ? () => {
                      setExampleSelection(null);
                      workspace.onSelect(task.id);
                    }
                  : undefined
              }
              selected={task.id === selected?.id}
            />
          ))}
        </div>
      )}
      {history.data && pages > 1 && (
        <div className="pagination">
          <button
            type="button"
            disabled={page <= 1}
            onClick={() => setPagination({ scope, page: page - 1 })}
          >
            上一页
          </button>
          <span>
            第 {page} / {pages} 页
          </span>
          <button
            type="button"
            disabled={page >= pages}
            onClick={() => setPagination({ scope, page: page + 1 })}
          >
            下一页
          </button>
        </div>
      )}
    </section>
  );
}
