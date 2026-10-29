"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { appConfig } from "@/config/app";
import { request } from "@/lib/api/client";
import type { TemplateDetail, Items } from "@/lib/api/types";

export function TemplateCatalog() {
  const [category, setCategory] = useState("");
  const categories = useQuery({
    queryKey: ["categories"],
    queryFn: ({ signal }) =>
      request<Items<{ id: string; name: string }>>(appConfig.api.categories, {
        signal,
      }),
  });
  const templates = useQuery({
    queryKey: ["templates", category],
    queryFn: ({ signal }) => {
      const filter = category
        ? `?categoryId=${encodeURIComponent(category)}`
        : "";
      return request<Items<TemplateDetail>>(
        `${appConfig.api.templates}${filter}`,
        { signal },
      );
    },
  });

  return (
    <section>
      <h1>探索模板</h1>
      <p>选择模板，了解素材要求。</p>
      <div className="filters" aria-label="模板分类">
        <button aria-pressed={!category} onClick={() => setCategory("")}>
          全部
        </button>
        {categories.data?.items.map((item) => (
          <button
            key={item.id}
            aria-pressed={category === item.id}
            onClick={() => setCategory(item.id)}
          >
            {item.name}
          </button>
        ))}
      </div>
      {categories.isError && (
        <button onClick={() => categories.refetch()}>分类获取失败，重试</button>
      )}
      {templates.isPending ? (
        <p role="status">加载模板…</p>
      ) : templates.isError ? (
        <div role="alert">
          <p>{templates.error.message}</p>
          <button onClick={() => templates.refetch()}>重新加载</button>
        </div>
      ) : templates.data.items.length === 0 ? (
        <p>当前分类暂无模板。</p>
      ) : (
        <div className="template-grid">
          {templates.data.items.map((item) => (
            <Link
              href={`/templates/${encodeURIComponent(item.id)}`}
              className="template-card"
              key={item.id}
            >
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={item.coverUrl} alt={item.title} />
              <div>
                <h2>{item.title}</h2>
                <p>
                  {item.categoryName} · 需上传 {item.requiredImageCount} 张图片
                </p>
              </div>
            </Link>
          ))}
        </div>
      )}
    </section>
  );
}
