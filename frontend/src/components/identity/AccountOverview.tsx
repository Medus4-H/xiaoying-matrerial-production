"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { getTaskHistory } from "@/lib/api/tasks";

export function AccountOverview({
  userId,
  onNavigate,
}: {
  userId: string;
  onNavigate: () => void;
}) {
  const overview = useQuery({
    queryKey: ["tasks", userId, "overview"],
    queryFn: ({ signal }) => getTaskHistory(1, 1, undefined, signal, userId),
  });
  const latest = overview.data?.items[0]?.createdAt;
  const date = latest ? new Date(latest) : null;
  const latestText =
    latest && date && !Number.isNaN(date.getTime())
      ? date.toLocaleString("zh-CN")
      : latest || "暂无记录";

  return (
    <section className="account-overview" aria-label="制作概览">
      <div className="account-overview-heading">
        <h3>制作概览</h3>
        <Link href="/history" onClick={onNavigate}>
          查看我的记录
        </Link>
      </div>
      {overview.isPending ? (
        <p role="status">正在加载制作概览…</p>
      ) : overview.isError ? (
        <div className="account-overview-error" role="alert">
          <p>制作概览暂时不可用</p>
          <button type="button" onClick={() => void overview.refetch()}>
            重试加载概览
          </button>
        </div>
      ) : (
        <dl className="account-overview-grid">
          <div>
            <dt>累计制作任务</dt>
            <dd>{overview.data.total}</dd>
          </div>
          <div>
            <dt>最近制作</dt>
            <dd>{latestText}</dd>
          </div>
        </dl>
      )}
    </section>
  );
}
