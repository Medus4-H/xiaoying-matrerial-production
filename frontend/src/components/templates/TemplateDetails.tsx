"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { appConfig } from "@/config/app";
import { ApiError, request } from "@/lib/api/client";
import type { TemplateDetail } from "@/lib/api/types";
import { TemplateWorkspace } from "./TemplateWorkspace";

export function TemplateDetails({ id }: { id: string }) {
  const template = useQuery({
    queryKey: ["template", id],
    queryFn: ({ signal }) =>
      request<TemplateDetail>(
        `${appConfig.api.templates}/${encodeURIComponent(id)}`,
        { signal },
      ),
  });

  if (template.isPending) return <p role="status">加载模板…</p>;
  if (template.isError) {
    const title =
      template.error instanceof ApiError && template.error.status === 404
        ? "模板不存在"
        : "模板加载失败";
    return (
      <section className="panel" role="alert">
        <h1>{title}</h1>
        <p>{template.error.message}</p>
        <Link href="/">返回模板列表</Link>
        <button onClick={() => template.refetch()}>重试</button>
      </section>
    );
  }

  return (
    <TemplateWorkspace
      template={template.data}
      showSimulationControls={appConfig.showSimulationControls}
    />
  );
}
