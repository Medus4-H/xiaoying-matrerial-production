import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { expect, test, vi } from "vitest";
import { AppProviders } from "@/components/providers/AppProviders";
import { useIdentity } from "@/components/identity/IdentityProvider";
import { VisitorIdentity } from "@/components/identity/VisitorIdentity";
import { TaskHistory } from "@/components/tasks/TaskHistory";
import { TaskDetail } from "@/components/tasks/TaskDetail";
import { useTaskTracker } from "@/features/tasks/TaskTrackerProvider";
import { server } from "./server";
import type { TaskSnapshot } from "@/lib/api/types";

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: React.ComponentProps<"a">) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));
function task(id: string, status: TaskSnapshot["status"]): TaskSnapshot {
  return {
    id,
    templateId: "demo-1",
    templateTitle: "旅行明信片",
    status,
    currentStage: "GENERATE",
    failedStage: status === "FAILED" ? "GENERATE" : null,
    errorCode: status === "FAILED" ? "SIMULATED_FAILURE" : null,
    errorMessage: status === "FAILED" ? "模拟失败" : null,
    resultUrl: status === "SUCCEEDED" ? `/api/tasks/${id}/result` : null,
    createdAt: "2026-01-01T00:00:00Z",
    startedAt: null,
    finishedAt: null,
    updatedAt: "2026-01-01T00:00:00Z",
    steps: [
      {
        stage: "GENERATE",
        sequence: 2,
        status: status === "FAILED" ? "FAILED" : "SUCCEEDED",
        startedAt: null,
        finishedAt: null,
        errorCode: null,
        errorMessage: null,
      },
    ],
  };
}

function taskLink(id: string): HTMLAnchorElement | null {
  return (
    (screen
      .queryAllByRole("link", { name: "查看详情" })
      .find((link) => link.getAttribute("href") === `/tasks/${id}`) as
      | HTMLAnchorElement
      | undefined) ?? null
  );
}
async function findTaskLink(id: string) {
  return waitFor(() => {
    const link = taskLink(id);
    expect(link).not.toBeNull();
    return link;
  });
}

test("task details display only valid completed step durations", async () => {
  const snapshot = task("duration", "FAILED");
  snapshot.steps = [
    {
      stage: "ASSET_CHECK",
      sequence: 1,
      status: "SUCCEEDED",
      startedAt: "2026-01-01T00:00:00Z",
      finishedAt: "2026-01-01T00:00:01.250Z",
      errorCode: null,
      errorMessage: null,
    },
    {
      stage: "GENERATE",
      sequence: 2,
      status: "FAILED",
      startedAt: "2026-01-01T00:00:03Z",
      finishedAt: "2026-01-01T00:00:02Z",
      errorCode: null,
      errorMessage: "模拟失败",
    },
    {
      stage: "FINALIZE",
      sequence: 3,
      status: "SKIPPED",
      startedAt: null,
      finishedAt: null,
      errorCode: null,
      errorMessage: null,
    },
  ];
  server.use(
    http.get("*/api/tasks/duration", () => HttpResponse.json(snapshot)),
  );
  render(
    <AppProviders>
      <TaskDetail id="duration" />
    </AppProviders>,
  );
  expect(await screen.findByText(/素材检查：完成.*耗时 1.25 秒/)).toBeTruthy();
  expect(screen.getByText(/模拟生成：失败.*耗时不可用/)).toBeTruthy();
  expect(screen.getByText(/结果整理：已跳过/)).toBeTruthy();
  expect(screen.queryByText(/-1.00 秒/)).toBeNull();
});

test("task details do not invent durations for missing timestamps", async () => {
  server.use(
    http.get("*/api/tasks/missing-times", () =>
      HttpResponse.json(task("missing-times", "SUCCEEDED")),
    ),
  );
  render(
    <AppProviders>
      <TaskDetail id="missing-times" />
    </AppProviders>,
  );
  expect(await screen.findByText(/模拟生成：完成.*耗时不可用/)).toBeTruthy();
});

test("history displays success, failure and an accessible result preview", async () => {
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
      <TaskHistory />
    </AppProviders>,
  );
  expect(await screen.findByText(/模拟失败/)).toBeTruthy();
  expect(screen.getByText("制作成功")).toBeTruthy();
  expect(screen.queryByText(/任务编号/)).toBeNull();
  expect(
    screen.getByRole("button", { name: /预览旅行明信片的制作结果/ }),
  ).toBeTruthy();
  expect(screen.getByAltText("制作结果缩略图").getAttribute("src")).toBe(
    "/api/tasks/success/result",
  );
  const trigger = screen.getByRole("button", { name: /预览.*制作结果/ });
  await user.click(trigger);
  expect(screen.getByRole("dialog", { name: "结果预览" })).toBeTruthy();
  expect(screen.getByAltText(/结果图片/).getAttribute("src")).toBe(
    "/api/tasks/success/result",
  );
  await user.keyboard("{Escape}");
  await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  expect(document.activeElement).toBe(trigger);
});

test("history request failure displays retry rather than an empty history", async () => {
  let unavailable = true;
  server.use(
    http.get("*/api/tasks", () =>
      unavailable
        ? HttpResponse.json({ message: "暂不可用" }, { status: 503 })
        : HttpResponse.json({ items: [], page: 1, pageSize: 20, total: 0 }),
    ),
  );
  const user = userEvent.setup();
  render(
    <AppProviders>
      <TaskHistory />
    </AppProviders>,
  );
  expect((await screen.findByRole("alert")).textContent).toContain("暂不可用");
  expect(screen.queryByText(/暂无制作记录/)).toBeNull();
  unavailable = false;
  await user.click(screen.getByRole("button", { name: "重试" }));
  expect(await screen.findByText(/暂无制作记录/)).toBeTruthy();
});

test("new task is shown immediately even while history is empty", async () => {
  function Register() {
    const { tracker } = useTaskTracker();
    return (
      <button
        onClick={() => tracker?.register(task("new-task", "QUEUED"), true)}
      >
        接受任务
      </button>
    );
  }
  const user = userEvent.setup();
  render(
    <AppProviders>
      <Register />
      <TaskHistory templateId="demo-1" />
    </AppProviders>,
  );
  await screen.findByText(/暂无制作记录/);
  await user.click(screen.getByRole("button", { name: "接受任务" }));
  expect(await findTaskLink("new-task")).toBeTruthy();
  expect(screen.getByText("等待处理")).toBeTruthy();
});

test("task details fetch independently and show a clear missing-task state", async () => {
  server.use(
    http.get("*/api/tasks/missing", () =>
      HttpResponse.json({ message: "任务不存在" }, { status: 404 }),
    ),
  );
  render(
    <AppProviders>
      <TaskDetail id="missing" />
    </AppProviders>,
  );
  expect((await screen.findByRole("alert")).textContent).toContain(
    "任务不存在",
  );
});

test("recently created tasks do not permanently overflow the first history page", async () => {
  const items = Array.from({ length: 20 }, (_, i) => ({
    ...task(`saved-${i}`, "SUCCEEDED"),
    createdAt: `2026-01-${String(21 - i).padStart(2, "0")}T00:00:00Z`,
  }));
  server.use(
    http.get("*/api/tasks", () =>
      HttpResponse.json({ items, page: 1, pageSize: 20, total: 20 }),
    ),
  );
  function RegisterOld() {
    const { tracker } = useTaskTracker();
    return (
      <button
        onClick={() =>
          tracker?.register(
            {
              ...task("old-created", "SUCCEEDED"),
              createdAt: "2025-01-01T00:00:00Z",
            },
            true,
          )
        }
      >
        添加旧任务
      </button>
    );
  }
  const user = userEvent.setup();
  render(
    <AppProviders>
      <RegisterOld />
      <TaskHistory />
    </AppProviders>,
  );
  await findTaskLink("saved-0");
  await user.click(screen.getByRole("button", { name: "添加旧任务" }));
  expect(taskLink("old-created")).toBeNull();
  expect(screen.getAllByRole("article")).toHaveLength(20);
});

test("query failure keeps the running task visible and recovery reads the server state", async () => {
  let unavailable = true;
  const running = task("live", "RUNNING");
  server.use(
    http.get("*/api/tasks/active", () =>
      HttpResponse.json({ items: [running] }),
    ),
    http.get("*/api/tasks", () =>
      HttpResponse.json({ items: [running], page: 1, pageSize: 20, total: 1 }),
    ),
    http.get("*/api/tasks/live", () =>
      unavailable
        ? HttpResponse.json({ message: "查询暂不可用" }, { status: 503 })
        : HttpResponse.json(task("live", "SUCCEEDED")),
    ),
  );
  function QueryNow() {
    const { tracker, state } = useTaskTracker();
    return (
      <button
        disabled={!state.tasks.live}
        onClick={() => void tracker?.resume("live")}
      >
        查询任务
      </button>
    );
  }
  const user = userEvent.setup();
  render(
    <AppProviders>
      <QueryNow />
      <TaskHistory />
    </AppProviders>,
  );
  await waitFor(() =>
    expect(
      screen.getByRole("button", { name: "查询任务" }).hasAttribute("disabled"),
    ).toBe(false),
  );
  await user.click(screen.getByRole("button", { name: "查询任务" }));
  expect((await screen.findByRole("alert")).textContent).toContain(
    "查询暂不可用",
  );
  expect(screen.getByText("处理中")).toBeTruthy();
  expect(screen.queryByText("制作失败")).toBeNull();
  unavailable = false;
  await user.click(screen.getByRole("button", { name: "恢复查询" }));
  expect(await screen.findByText("制作成功")).toBeTruthy();
  expect(screen.queryByRole("alert")).toBeNull();
});

test("history is hidden while account switching is in progress", async () => {
  let release: (() => void) | undefined;
  server.use(
    http.get("*/api/tasks", () =>
      HttpResponse.json({
        items: [task("old-task", "SUCCEEDED")],
        page: 1,
        pageSize: 20,
        total: 1,
      }),
    ),
    http.post("*/api/auth/login", async () => {
      await new Promise<void>((resolve) => {
        release = resolve;
      });
      return HttpResponse.json({
        id: "other",
        displayName: "其他账号",
        type: "ACCOUNT",
        expiresAt: "2099-01-01T00:00:00Z",
      });
    }),
  );
  function Switch() {
    const identity = useIdentity();
    return (
      <button
        onClick={() => void identity.login("other@example.com", "password123")}
      >
        切换测试账号
      </button>
    );
  }
  const user = userEvent.setup();
  render(
    <AppProviders>
      <Switch />
      <TaskHistory />
    </AppProviders>,
  );
  await findTaskLink("old-task");
  await user.click(screen.getByRole("button", { name: "切换测试账号" }));
  await waitFor(() => expect(release).toBeDefined());
  expect(taskLink("old-task")).toBeNull();
  release?.();
});

test("switching account in the same tab shows only the new account history", async () => {
  let current = "a";
  server.use(
    http.get("*/api/me", () =>
      HttpResponse.json({
        id: current,
        displayName: `账号 ${current}`,
        type: "ACCOUNT",
        expiresAt: "2099-01-01T00:00:00Z",
      }),
    ),
    http.get("*/api/tasks", () =>
      HttpResponse.json({
        items:
          current === "a"
            ? [task("only-a", "SUCCEEDED")]
            : [task("only-b", "SUCCEEDED")],
        page: 1,
        pageSize: 20,
        total: 1,
      }),
    ),
    http.post("*/api/auth/login", () => {
      current = "b";
      return HttpResponse.json({
        id: "b",
        displayName: "账号 b",
        type: "ACCOUNT",
        expiresAt: "2099-01-01T00:00:00Z",
      });
    }),
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
      <TaskHistory />
    </AppProviders>,
  );
  await findTaskLink("only-a");
  await user.click(screen.getByRole("button", { name: "切换账号" }));
  await findTaskLink("only-b");
  expect(taskLink("only-a")).toBeNull();
});

test("returning to a tab after another tab changes account refreshes identity and history", async () => {
  let current = "a";
  server.use(
    http.get("*/api/me", () =>
      HttpResponse.json({
        id: current,
        displayName: `账号 ${current}`,
        type: "ACCOUNT",
        expiresAt: "2099-01-01T00:00:00Z",
      }),
    ),
    http.get("*/api/tasks", () =>
      HttpResponse.json({
        items:
          current === "a"
            ? [task("only-a", "SUCCEEDED")]
            : [task("only-b", "SUCCEEDED")],
        page: 1,
        pageSize: 20,
        total: 1,
      }),
    ),
  );
  function AccountName() {
    const identity = useIdentity();
    return <span>{identity.user?.displayName}</span>;
  }
  render(
    <AppProviders>
      <AccountName />
      <TaskHistory />
    </AppProviders>,
  );
  await findTaskLink("only-a");
  expect(screen.getByText("账号 a")).toBeTruthy();
  current = "b";
  fireEvent.focus(window);
  await screen.findByText("账号 b");
  await findTaskLink("only-b");
  expect(taskLink("only-a")).toBeNull();
});

test("logout then login through the interface shows only the new account history", async () => {
  let current = "a";
  const profile = (id: string) => ({
    id,
    displayName: `账号 ${id}`,
    type: id === "guest" ? "GUEST" : "ACCOUNT",
    expiresAt: "2099-01-01T00:00:00Z",
  });
  server.use(
    http.get("*/api/me", () => HttpResponse.json(profile(current))),
    http.get("*/api/tasks", () =>
      HttpResponse.json({
        items:
          current === "a"
            ? [task("private-a", "SUCCEEDED")]
            : current === "b"
              ? [task("private-b", "SUCCEEDED")]
              : [],
        page: 1,
        pageSize: 20,
        total: current === "guest" ? 0 : 1,
      }),
    ),
    http.post("*/api/auth/logout", () => {
      current = "guest";
      return new HttpResponse(null, { status: 204 });
    }),
    http.post("*/api/session", () => HttpResponse.json(profile(current))),
    http.post("*/api/auth/login", () => {
      current = "b";
      return HttpResponse.json(profile(current));
    }),
  );
  const user = userEvent.setup();
  render(
    <AppProviders>
      <VisitorIdentity />
      <TaskHistory />
    </AppProviders>,
  );
  await findTaskLink("private-a");
  await user.click(screen.getByRole("button", { name: "账号 a" }));
  await user.click(screen.getByRole("button", { name: "退出账号" }));
  await screen.findByRole("button", { name: "账号 guest" });
  expect(taskLink("private-a")).toBeNull();
  await user.click(screen.getByRole("button", { name: "账号 guest" }));
  await user.click(screen.getByRole("button", { name: "切换 / 登录账号" }));
  await user.type(screen.getByLabelText("邮箱或手机号"), "b@example.com");
  await user.type(screen.getByLabelText("密码"), "password123");
  await user.click(screen.getByRole("button", { name: "登录" }));
  await screen.findByRole("button", { name: "账号 b" });
  await findTaskLink("private-b");
  expect(taskLink("private-a")).toBeNull();
});

test("another tab's account change hides history until the new identity is confirmed", async () => {
  let current = "a";
  let release: (() => void) | undefined;
  server.use(
    http.get("*/api/me", async () => {
      const requested = current;
      if (requested === "b")
        await new Promise<void>((resolve) => {
          release = resolve;
        });
      return HttpResponse.json({
        id: requested,
        displayName: `账号 ${requested}`,
        type: "ACCOUNT",
        expiresAt: "2099-01-01T00:00:00Z",
      });
    }),
    http.get("*/api/tasks", ({ request }) => {
      expect(request.headers.get("X-Studio-User")).toBe(current);
      return HttpResponse.json({
        items: [task(`only-${current}`, "SUCCEEDED")],
        page: 1,
        pageSize: 20,
        total: 1,
      });
    }),
  );
  render(
    <AppProviders>
      <TaskHistory />
    </AppProviders>,
  );
  await findTaskLink("only-a");
  current = "b";
  fireEvent(
    window,
    new StorageEvent("storage", {
      key: "xiaoying:identity-change",
      newValue: "other-session",
    }),
  );
  await waitFor(() => expect(release).toBeDefined());
  expect(taskLink("only-a")).toBeNull();
  release?.();
  await findTaskLink("only-b");
});

test("rechecks the latest account when two other-tab changes arrive during one identity request", async () => {
  let current = "a";
  let releaseFirst: (() => void) | undefined;
  let releaseSecond: (() => void) | undefined;
  server.use(
    http.get("*/api/me", async () => {
      const requested = current;
      if (requested === "b")
        await new Promise<void>((resolve) => {
          releaseFirst = resolve;
        });
      if (requested === "c")
        await new Promise<void>((resolve) => {
          releaseSecond = resolve;
        });
      return HttpResponse.json({
        id: requested,
        displayName: `账号 ${requested}`,
        type: "ACCOUNT",
        expiresAt: "2099-01-01T00:00:00Z",
      });
    }),
    http.get("*/api/tasks", ({ request }) => {
      if (request.headers.get("X-Studio-User") !== current)
        return HttpResponse.json(
          { code: "IDENTITY_CHANGED", message: "账号已切换" },
          { status: 409 },
        );
      return HttpResponse.json({
        items: [task(`only-${current}`, "SUCCEEDED")],
        page: 1,
        pageSize: 20,
        total: 1,
      });
    }),
  );
  function AccountName() {
    const identity = useIdentity();
    return <span>{identity.user?.displayName}</span>;
  }
  render(
    <AppProviders>
      <AccountName />
      <TaskHistory />
    </AppProviders>,
  );
  await findTaskLink("only-a");
  current = "b";
  fireEvent(
    window,
    new StorageEvent("storage", {
      key: "xiaoying:identity-change",
      newValue: "b",
    }),
  );
  await waitFor(() => expect(releaseFirst).toBeDefined());
  current = "c";
  fireEvent(
    window,
    new StorageEvent("storage", {
      key: "xiaoying:identity-change",
      newValue: "c",
    }),
  );
  releaseFirst?.();
  await waitFor(() => expect(releaseSecond).toBeDefined());
  expect(screen.queryByText("账号 b")).toBeNull();
  releaseSecond?.();
  await screen.findByText("账号 c");
  await findTaskLink("only-c");
  expect(taskLink("only-a")).toBeNull();
  expect(taskLink("only-b")).toBeNull();
});
