import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { expect, test, vi } from "vitest";
import { AppProviders } from "@/components/providers/AppProviders";
import { useIdentity } from "@/components/identity/IdentityProvider";
import { TemplateWorkspace } from "@/components/templates/TemplateWorkspace";
import { TaskHistory } from "@/components/tasks/TaskHistory";
import { useTaskTracker } from "@/features/tasks/TaskTrackerProvider";
import { useState } from "react";
import type { TaskSnapshot, TemplateDetail } from "@/lib/api/types";
import { server } from "./server";

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: React.ComponentProps<"a">) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

const template: TemplateDetail = {
  id: "demo-1",
  title: "旅行明信片",
  categoryId: "creative",
  categoryName: "创意图片",
  description: "示例",
  coverUrl: "/cover.jpg",
  requiredImageCount: 2,
  acceptedMimeTypes: ["image/jpeg"],
  maxFileSizeBytes: 5242880,
};

function task(id: string, status: TaskSnapshot["status"]): TaskSnapshot {
  return {
    id,
    templateId: template.id,
    templateTitle: template.title,
    status,
    currentStage: status === "RUNNING" ? "GENERATE" : null,
    failedStage: status === "FAILED" ? "GENERATE" : null,
    errorCode: null,
    errorMessage: status === "FAILED" ? "模拟失败" : null,
    resultUrl: status === "SUCCEEDED" ? `/api/tasks/${id}/result` : null,
    createdAt:
      id === "success" ? "2026-01-02T00:00:00Z" : "2026-01-01T00:00:00Z",
    startedAt: null,
    finishedAt: null,
    updatedAt: "2026-01-02T00:00:00Z",
    steps: [],
  };
}

test("empty workspace explains the preset example and the required uploads", async () => {
  render(
    <AppProviders>
      <TemplateWorkspace template={template} />
    </AppProviders>,
  );
  expect(await screen.findByText("模板示例")).toBeTruthy();
  expect(screen.getByText(/预置演示图片/)).toBeTruthy();
  expect(screen.getByText("已准备 0 / 2 张图片")).toBeTruthy();
});

test("selecting a history task shows its result or failure in the preview", async () => {
  server.use(
    http.get("*/api/tasks", () =>
      HttpResponse.json({
        items: [task("success", "SUCCEEDED"), task("failure", "FAILED")],
        page: 1,
        pageSize: 20,
        total: 2,
      }),
    ),
  );
  const user = userEvent.setup();
  render(
    <AppProviders>
      <TemplateWorkspace template={template} />
    </AppProviders>,
  );
  const preview = await screen.findByRole("region", { name: "作品预览" });
  expect(
    (await within(preview).findByAltText("制作结果")).getAttribute("src"),
  ).toBe("/api/tasks/success/result");
  const cards = await screen.findAllByRole("article");
  await user.click(
    within(cards[1]).getByRole("button", { name: "在预览区查看" }),
  );
  expect(within(preview).getByText(/模拟失败/)).toBeTruthy();
  expect(within(preview).queryByAltText("制作结果")).toBeNull();
});

test("template example remains accessible when history already has a task", async () => {
  server.use(
    http.get("*/api/tasks", () =>
      HttpResponse.json({
        items: [task("success", "SUCCEEDED")],
        page: 1,
        pageSize: 20,
        total: 1,
      }),
    ),
  );
  const user = userEvent.setup();
  render(
    <AppProviders>
      <TemplateWorkspace template={template} />
    </AppProviders>,
  );
  const preview = await screen.findByRole("region", { name: "作品预览" });
  expect(await within(preview).findByAltText("制作结果")).toBeTruthy();
  await user.click(
    within(preview).getByRole("button", { name: "查看模板示例" }),
  );
  expect(within(preview).getByAltText("模板示例图片").getAttribute("src")).toBe(
    "/cover.jpg",
  );
  await user.click(within(preview).getByRole("button", { name: "返回任务" }));
  expect(within(preview).getByAltText("制作结果")).toBeTruthy();
});

test("switching accounts removes the previous account's selected preview", async () => {
  let current = "a";
  const profile = (id: string) => ({
    id,
    displayName: id,
    type: "ACCOUNT",
    expiresAt: "2099-01-01T00:00:00Z",
  });
  server.use(
    http.get("*/api/me", () => HttpResponse.json(profile(current))),
    http.post("*/api/auth/login", () => {
      current = "b";
      return HttpResponse.json(profile(current));
    }),
    http.get("*/api/tasks", () =>
      HttpResponse.json({
        items: current === "a" ? [task("success", "SUCCEEDED")] : [],
        page: 1,
        pageSize: 20,
        total: current === "a" ? 1 : 0,
      }),
    ),
  );
  function Switch() {
    const identity = useIdentity();
    return (
      <button
        onClick={() => void identity.login("b@example.com", "password123")}
      >
        切换账号
      </button>
    );
  }
  const user = userEvent.setup();
  render(
    <AppProviders>
      <Switch />
      <TemplateWorkspace template={template} />
    </AppProviders>,
  );
  const preview = await screen.findByRole("region", { name: "作品预览" });
  expect(await within(preview).findByAltText("制作结果")).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "切换账号" }));
  expect(await screen.findByText("模板示例")).toBeTruthy();
  expect(screen.queryByAltText("制作结果")).toBeNull();
});

test("a failed result image has a clear fallback", async () => {
  server.use(
    http.get("*/api/tasks", () =>
      HttpResponse.json({
        items: [task("success", "SUCCEEDED")],
        page: 1,
        pageSize: 20,
        total: 1,
      }),
    ),
  );
  render(
    <AppProviders>
      <TemplateWorkspace template={template} />
    </AppProviders>,
  );
  fireEvent.error(await screen.findByAltText("制作结果"));
  expect(screen.getByText("图片暂时无法加载，请稍后重试。")).toBeTruthy();
});

test("the preview shows the server's recorded step states", async () => {
  const running = task("running", "RUNNING");
  running.steps = [
    {
      stage: "ASSET_CHECK",
      sequence: 1,
      status: "SUCCEEDED",
      startedAt: null,
      finishedAt: null,
      errorCode: null,
      errorMessage: null,
    },
    {
      stage: "GENERATE",
      sequence: 2,
      status: "RUNNING",
      startedAt: null,
      finishedAt: null,
      errorCode: null,
      errorMessage: null,
    },
    {
      stage: "FINALIZE",
      sequence: 3,
      status: "PENDING",
      startedAt: null,
      finishedAt: null,
      errorCode: null,
      errorMessage: null,
    },
  ];
  server.use(
    http.get("*/api/tasks", () =>
      HttpResponse.json({ items: [running], page: 1, pageSize: 20, total: 1 }),
    ),
  );
  render(
    <AppProviders>
      <TemplateWorkspace template={template} />
    </AppProviders>,
  );
  const preview = await screen.findByRole("region", { name: "作品预览" });
  expect(await within(preview).findByText("素材检查：完成")).toBeTruthy();
  expect(within(preview).getByText("模拟生成：处理中")).toBeTruthy();
  expect(within(preview).getByText("结果整理：等待")).toBeTruthy();
});

test("a newly accepted task remains visible in preview while browsing an older page", async () => {
  server.use(
    http.get("*/api/tasks", ({ request }) => {
      const page = Number(new URL(request.url).searchParams.get("page"));
      return HttpResponse.json({
        items: [task(`page-${page}`, "SUCCEEDED")],
        page,
        pageSize: 20,
        total: 21,
      });
    }),
  );
  function Example() {
    const { tracker } = useTaskTracker();
    const [selected, setSelected] = useState<string | null>(null);
    return (
      <>
        <button
          onClick={() => {
            tracker?.register(task("new", "QUEUED"), true);
            setSelected("new");
          }}
        >
          接受新任务
        </button>
        <TaskHistory
          templateId="demo-1"
          workspace={{
            coverUrl: "/cover.jpg",
            selectedTaskId: selected,
            onSelect: setSelected,
          }}
        />
      </>
    );
  }
  const user = userEvent.setup();
  render(
    <AppProviders>
      <Example />
    </AppProviders>,
  );
  await screen.findByRole("button", { name: "下一页" });
  await user.click(screen.getByRole("button", { name: "下一页" }));
  await screen.findByText("第 2 / 2 页");
  await user.click(screen.getByRole("button", { name: "接受新任务" }));
  const preview = screen.getByRole("region", { name: "作品预览" });
  expect(
    within(preview).getByText("任务已受理，正在等待后台处理。"),
  ).toBeTruthy();
});

test("a selected task outside the history page can resume a failed query from the preview", async () => {
  let unavailable = true;
  server.use(
    http.get("*/api/tasks", ({ request }) => {
      const page = Number(new URL(request.url).searchParams.get("page"));
      return HttpResponse.json({
        items: [task(`page-${page}`, "SUCCEEDED")],
        page,
        pageSize: 20,
        total: 21,
      });
    }),
    http.get("*/api/tasks/new", () =>
      unavailable
        ? HttpResponse.json({ message: "暂不可用" }, { status: 503 })
        : HttpResponse.json(task("new", "SUCCEEDED")),
    ),
  );
  function Example() {
    const { tracker } = useTaskTracker();
    const [selected, setSelected] = useState<string | null>(null);
    return (
      <>
        <button
          onClick={() => {
            tracker?.register(task("new", "QUEUED"), true);
            setSelected("new");
            void tracker?.resume("new");
          }}
        >
          接受新任务
        </button>
        <TaskHistory
          templateId="demo-1"
          workspace={{
            coverUrl: "/cover.jpg",
            selectedTaskId: selected,
            onSelect: setSelected,
          }}
        />
      </>
    );
  }
  const user = userEvent.setup();
  render(
    <AppProviders>
      <Example />
    </AppProviders>,
  );
  await screen.findByRole("button", { name: "下一页" });
  await user.click(screen.getByRole("button", { name: "下一页" }));
  await screen.findByText("第 2 / 2 页");
  await user.click(screen.getByRole("button", { name: "接受新任务" }));
  const preview = screen.getByRole("region", { name: "作品预览" });
  expect(await within(preview).findByText(/状态查询暂时失败/)).toBeTruthy();
  expect(within(preview).getAllByText("等待处理").length).toBeGreaterThan(0);
  unavailable = false;
  await user.click(within(preview).getByRole("button", { name: "恢复查询" }));
  expect(await within(preview).findByAltText("制作结果")).toBeTruthy();
  expect(within(preview).queryByText(/状态查询暂时失败/)).toBeNull();
});
