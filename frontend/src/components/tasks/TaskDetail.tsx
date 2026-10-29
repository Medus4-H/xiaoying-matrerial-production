"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { useEffect } from "react";
import { useIdentity } from "@/components/identity/IdentityProvider";
import { useTaskTracker } from "@/features/tasks/TaskTrackerProvider";
import { getTask } from "@/lib/api/tasks";
import { TaskCard } from "./TaskCard";

export function TaskDetail({ id }: { id: string }) {
  const identity = useIdentity();
  const { tracker, state } = useTaskTracker();
  const tracked = state.tasks[id]?.snapshot;
  const detail = useQuery({
    queryKey: ["tasks", identity.user?.id, "detail", id],
    queryFn: ({ signal }) => getTask(id, signal, identity.user!.id),
    enabled:
      identity.status === "ready" &&
      !identity.transitioning &&
      !identity.checking &&
      !!identity.user &&
      !tracked,
    staleTime: Infinity,
    refetchOnMount: false,
  });
  useEffect(() => {
    if (detail.data && tracker) tracker.register(detail.data);
  }, [detail.data, tracker]);
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
  const task = tracked ?? detail.data;
  return (
    <section className="panel task-detail">
      <Link href="/history" className="back-link">
        ← 返回我的记录
      </Link>
      <h1>任务详情</h1>
      {!task && detail.isPending && <p role="status">加载任务…</p>}
      {!task && detail.isError && (
        <div role="alert">
          <p>{detail.error.message}</p>
          <button type="button" onClick={() => void detail.refetch()}>
            重试
          </button>
        </div>
      )}
      {task && <TaskCard task={task} detailed />}
    </section>
  );
}
