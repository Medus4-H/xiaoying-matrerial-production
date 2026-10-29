"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { appConfig } from "@/config/app";
import { useIdentity } from "@/components/identity/IdentityProvider";
import { useTaskTracker } from "@/features/tasks/TaskTrackerProvider";
import { getTaskHistory } from "@/lib/api/tasks";
import { TaskCard } from "./TaskCard";

export function TaskHistory({ templateId }: { templateId?: string }) {
  const identity = useIdentity();
  const { tracker, state } = useTaskTracker();
  const [page, setPage] = useState(1);
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
  return (
    <section
      className="history-panel"
      aria-label={templateId ? "当前模板制作记录" : "个人制作记录"}
    >
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
            <TaskCard key={task.id} task={task} />
          ))}
        </div>
      )}
      {history.data && pages > 1 && (
        <div className="pagination">
          <button
            type="button"
            disabled={page <= 1}
            onClick={() => setPage((value) => value - 1)}
          >
            上一页
          </button>
          <span>
            第 {page} / {pages} 页
          </span>
          <button
            type="button"
            disabled={page >= pages}
            onClick={() => setPage((value) => value + 1)}
          >
            下一页
          </button>
        </div>
      )}
    </section>
  );
}
