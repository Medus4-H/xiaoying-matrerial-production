import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { expect, test } from "vitest";
import { AppProviders } from "@/components/providers/AppProviders";
import { VisitorIdentity } from "@/components/identity/VisitorIdentity";
import { useIdentity } from "@/components/identity/IdentityProvider";
import { server } from "./server";

const visitor = {
  id: "user-1",
  displayName: "访客一",
  expiresAt: "2099-01-01T00:00:00Z",
};
function SecondConsumer() {
  const identity = useIdentity();
  return <span>{identity.user?.displayName}</span>;
}

test("two consumers share one session creation after a 401", async () => {
  let sessions = 0;
  server.use(
    http.get("*/api/me", () =>
      HttpResponse.json(
        { code: "SESSION_REQUIRED", message: "请建立身份" },
        { status: 401 },
      ),
    ),
    http.post("*/api/session", () => {
      sessions++;
      return HttpResponse.json(visitor, { status: 201 });
    }),
  );
  render(
    <AppProviders>
      <VisitorIdentity />
      <SecondConsumer />
    </AppProviders>,
  );
  await screen.findByRole("button", { name: /访客一/ });
  expect(screen.getAllByText("访客一").length).toBeGreaterThan(0);
  expect(sessions).toBe(1);
});

test("visitor dialog explains identity and restores focus on close", async () => {
  server.use(http.get("*/api/me", () => HttpResponse.json(visitor)));
  const user = userEvent.setup();
  render(
    <AppProviders>
      <VisitorIdentity />
    </AppProviders>,
  );
  const trigger = await screen.findByRole("button", { name: /访客一/ });
  await user.click(trigger);
  expect(screen.getByRole("dialog", { name: "访客身份" })).toBeTruthy();
  expect(screen.getByText(/无法找回/)).toBeTruthy();
  await user.keyboard("{Escape}");
  await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  expect(document.activeElement).toBe(trigger);
});

test("network errors do not create a visitor and provide retry", async () => {
  let sessions = 0;
  server.use(
    http.get("*/api/me", () =>
      HttpResponse.json({ message: "不可用" }, { status: 503 }),
    ),
    http.post("*/api/session", () => {
      sessions++;
      return HttpResponse.json(visitor);
    }),
  );
  render(
    <AppProviders>
      <VisitorIdentity />
    </AppProviders>,
  );
  await screen.findByRole("button", { name: /身份加载失败/ });
  expect(sessions).toBe(0);
});

test("late identity response cannot replace the newly established visitor", async () => {
  let release: (() => void) | undefined;
  let reads = 0;
  let delivered: (() => void) | undefined;
  const oldResponseDelivered = new Promise<void>((resolve) => {
    delivered = resolve;
  });
  const oldVisitor = {
    id: "old-user",
    displayName: "旧访客",
    expiresAt: "2000-01-01T00:00:00Z",
  };
  server.use(
    http.get("*/api/me", async () => {
      reads++;
      if (reads === 1) return HttpResponse.json(oldVisitor);
      await new Promise<void>((resolve) => {
        release = resolve;
      });
      delivered?.();
      return HttpResponse.json(oldVisitor);
    }),
    http.post("*/api/session", () =>
      HttpResponse.json(visitor, { status: 201 }),
    ),
  );
  function RetryIdentity() {
    const identity = useIdentity();
    return <button onClick={identity.retry}>重新查询身份</button>;
  }
  const user = userEvent.setup();
  render(
    <AppProviders>
      <VisitorIdentity />
      <RetryIdentity />
    </AppProviders>,
  );
  await screen.findByRole("button", { name: "建立新访客身份" });
  await user.click(screen.getByRole("button", { name: "重新查询身份" }));
  await waitFor(() => expect(release).toBeDefined());
  await user.click(screen.getByRole("button", { name: "建立新访客身份" }));
  await screen.findByRole("button", { name: "访客一" });
  await act(async () => {
    release?.();
    await oldResponseDelivered;
  });
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "访客一" })).toBeTruthy(),
  );
  expect(screen.queryByText("旧访客")).toBeNull();
});

test("visitor can register while retaining the same identity", async () => {
  server.use(
    http.get("*/api/me", () =>
      HttpResponse.json({ ...visitor, type: "GUEST" }),
    ),
    http.post("*/api/auth/demo-code", () =>
      HttpResponse.json({ demoCode: "123456" }),
    ),
    http.post("*/api/auth/register", async ({ request }) => {
      expect(request.headers.get("X-Studio-User")).toBe("user-1");
      const body = (await request.json()) as Record<string, string>;
      expect(body.channel).toBe("email");
      expect(body.destination).toBe("person@example.com");
      expect(body.code).toBe("123456");
      expect(body.importGuest).toBe(true);
      return HttpResponse.json({
        ...visitor,
        type: "ACCOUNT",
        displayName: "person@example.com",
      });
    }),
  );
  const user = userEvent.setup();
  render(
    <AppProviders>
      <VisitorIdentity />
    </AppProviders>,
  );
  await user.click(await screen.findByRole("button", { name: "访客一" }));
  await user.click(screen.getByRole("button", { name: "注册账号" }));
  await user.click(
    screen.getByLabelText(/将当前访客的素材和历史记录保存到新账号/),
  );
  await user.type(screen.getByLabelText("邮箱"), "person@example.com");
  await user.type(screen.getByLabelText("密码"), "password123");
  await user.type(screen.getByLabelText("验证码"), "123456");
  await user.click(screen.getByRole("button", { name: "完成注册" }));
  await screen.findByRole("button", { name: "person@example.com" });
});

test("expired account can sign in without creating a new visitor", async () => {
  let visitors = 0;
  server.use(
    http.get("*/api/me", () =>
      HttpResponse.json({
        ...visitor,
        type: "ACCOUNT",
        expiresAt: "2000-01-01T00:00:00Z",
      }),
    ),
    http.post("*/api/session", () => {
      visitors++;
      return HttpResponse.json(visitor);
    }),
    http.post("*/api/auth/login", () =>
      HttpResponse.json({
        ...visitor,
        type: "ACCOUNT",
        displayName: "person@example.com",
      }),
    ),
  );
  const user = userEvent.setup();
  render(
    <AppProviders>
      <VisitorIdentity />
    </AppProviders>,
  );
  await user.click(await screen.findByRole("button", { name: "登录账号" }));
  await user.type(screen.getByLabelText("邮箱或手机号"), "person@example.com");
  await user.type(screen.getByLabelText("密码"), "password123");
  await user.click(screen.getByRole("button", { name: "登录" }));
  await screen.findByRole("button", { name: "person@example.com" });
  expect(visitors).toBe(0);
});

test("uncertain login response suspends old identity until the session is checked again", async () => {
  let switched = false;
  server.use(
    http.get("*/api/me", () =>
      HttpResponse.json(
        switched
          ? { ...visitor, id: "user-2", type: "ACCOUNT", displayName: "新账号" }
          : { ...visitor, type: "ACCOUNT" },
      ),
    ),
    http.post("*/api/auth/login", () => {
      switched = true;
      return HttpResponse.json({ message: "连接中断" }, { status: 503 });
    }),
  );
  const user = userEvent.setup();
  render(
    <AppProviders>
      <VisitorIdentity />
      <SecondConsumer />
    </AppProviders>,
  );
  await user.click(await screen.findByRole("button", { name: "访客一" }));
  await user.click(screen.getByRole("button", { name: "切换 / 登录账号" }));
  await user.type(screen.getByLabelText("邮箱或手机号"), "second@example.com");
  await user.type(screen.getByLabelText("密码"), "password123");
  await user.click(screen.getByRole("button", { name: "登录" }));
  await user.click(
    await screen.findByRole("button", { name: "身份加载失败，重试" }),
  );
  await screen.findByRole("button", { name: "新账号" });
  expect(screen.queryByText("访客一")).toBeNull();
});

test("network loss after login suspends the old identity until the session is checked again", async () => {
  let switched = false;
  server.use(
    http.get("*/api/me", () =>
      HttpResponse.json(
        switched
          ? { ...visitor, id: "user-2", type: "ACCOUNT", displayName: "新账号" }
          : { ...visitor, type: "ACCOUNT" },
      ),
    ),
    http.post("*/api/auth/login", () => {
      switched = true;
      return HttpResponse.error();
    }),
  );
  const user = userEvent.setup();
  render(
    <AppProviders>
      <VisitorIdentity />
      <SecondConsumer />
    </AppProviders>,
  );
  await user.click(await screen.findByRole("button", { name: "访客一" }));
  await user.click(screen.getByRole("button", { name: "切换 / 登录账号" }));
  await user.type(screen.getByLabelText("邮箱或手机号"), "second@example.com");
  await user.type(screen.getByLabelText("密码"), "password123");
  await user.click(screen.getByRole("button", { name: "登录" }));
  await user.click(
    await screen.findByRole("button", { name: "身份加载失败，重试" }),
  );
  await screen.findByRole("button", { name: "新账号" });
  expect(screen.queryByText("访客一")).toBeNull();
});

test("a conflicting visitor backup displays the server error when login is rejected", async () => {
  server.use(
    http.get("*/api/me", () =>
      HttpResponse.json({ ...visitor, type: "GUEST" }),
    ),
    http.post("*/api/auth/login", () =>
      HttpResponse.json(
        {
          code: "GUEST_BACKUP_EXISTS",
          message: "当前浏览器已有待恢复的访客记录，请先保存当前访客资料",
        },
        { status: 409 },
      ),
    ),
  );
  const user = userEvent.setup();
  render(
    <AppProviders>
      <VisitorIdentity />
    </AppProviders>,
  );
  await user.click(await screen.findByRole("button", { name: "访客一" }));
  await user.click(screen.getByRole("button", { name: "切换 / 登录账号" }));
  await user.type(screen.getByLabelText("邮箱或手机号"), "a@example.com");
  await user.type(screen.getByLabelText("密码"), "password123");
  await user.click(screen.getByRole("button", { name: "登录" }));
  expect(
    await screen.findByText(
      "当前浏览器已有待恢复的访客记录，请先保存当前访客资料",
    ),
  ).toBeTruthy();
});

test("registration lets a visitor choose only a phone and its code", async () => {
  server.use(
    http.get("*/api/me", () =>
      HttpResponse.json({ ...visitor, type: "GUEST" }),
    ),
    http.post("*/api/auth/register", async ({ request }) => {
      expect(await request.json()).toEqual({
        channel: "phone",
        destination: "13800138000",
        code: "123456",
        password: "password123",
        importGuest: false,
      });
      return HttpResponse.json({
        ...visitor,
        type: "ACCOUNT",
        email: null,
        phone: "13800138000",
        displayName: "13800138000",
      });
    }),
  );
  const user = userEvent.setup();
  render(
    <AppProviders>
      <VisitorIdentity />
    </AppProviders>,
  );
  await user.click(await screen.findByRole("button", { name: "访客一" }));
  await user.click(screen.getByRole("button", { name: "注册账号" }));
  await user.click(screen.getByRole("button", { name: "手机号注册" }));
  expect(screen.queryByLabelText("邮箱")).toBeNull();
  await user.type(screen.getByLabelText("手机号"), "13800138000");
  await user.type(screen.getByLabelText("验证码"), "123456");
  await user.type(screen.getByLabelText("密码"), "password123");
  await user.click(screen.getByRole("button", { name: "完成注册" }));
  await screen.findByRole("button", { name: "13800138000" });
});

test("an email-only account can bind a phone and see the updated contact", async () => {
  server.use(
    http.get("*/api/me", () =>
      HttpResponse.json({
        ...visitor,
        type: "ACCOUNT",
        email: "person@example.com",
        phone: null,
        displayName: "person@example.com",
      }),
    ),
    http.post("*/api/auth/bind", async ({ request }) => {
      expect(request.headers.get("X-Studio-User")).toBe("user-1");
      expect(await request.json()).toEqual({
        channel: "phone",
        destination: "13800138000",
        code: "123456",
      });
      return HttpResponse.json({
        ...visitor,
        type: "ACCOUNT",
        email: "person@example.com",
        phone: "13800138000",
        displayName: "person@example.com",
      });
    }),
  );
  const user = userEvent.setup();
  render(
    <AppProviders>
      <VisitorIdentity />
    </AppProviders>,
  );
  await user.click(
    await screen.findByRole("button", { name: "person@example.com" }),
  );
  await user.click(screen.getByRole("button", { name: "绑定手机号" }));
  await user.type(screen.getByLabelText("手机号"), "13800138000");
  await user.type(screen.getByLabelText("验证码"), "123456");
  expect(screen.queryByLabelText("密码")).toBeNull();
  await user.click(screen.getByRole("button", { name: "完成绑定" }));
  await screen.findByText(/手机号：13800138000/);
  expect(screen.queryByRole("button", { name: "绑定手机号" })).toBeNull();
});

test("a phone-only account can bind email with a demo code without entering a password", async () => {
  server.use(
    http.get("*/api/me", () =>
      HttpResponse.json({
        ...visitor,
        type: "ACCOUNT",
        email: null,
        phone: "13800138000",
        displayName: "13800138000",
      }),
    ),
    http.post("*/api/auth/demo-code", async ({ request }) => {
      expect(await request.json()).toEqual({
        channel: "email",
        destination: "person@example.com",
      });
      return HttpResponse.json({ demoCode: "123456" });
    }),
    http.post("*/api/auth/bind", async ({ request }) => {
      expect(request.headers.get("X-Studio-User")).toBe("user-1");
      expect(await request.json()).toEqual({
        channel: "email",
        destination: "person@example.com",
        code: "123456",
      });
      return HttpResponse.json({
        ...visitor,
        type: "ACCOUNT",
        email: "person@example.com",
        phone: "13800138000",
        displayName: "person@example.com",
      });
    }),
  );
  const user = userEvent.setup();
  render(
    <AppProviders>
      <VisitorIdentity />
    </AppProviders>,
  );
  await user.click(await screen.findByRole("button", { name: "13800138000" }));
  await user.click(screen.getByRole("button", { name: "绑定邮箱" }));
  expect(screen.queryByLabelText("密码")).toBeNull();
  await user.type(screen.getByLabelText("邮箱"), "person@example.com");
  await user.click(screen.getByRole("button", { name: "获取验证码" }));
  expect(await screen.findByText(/演示验证码：123456/)).toBeTruthy();
  await user.type(screen.getByLabelText("验证码"), "123456");
  await user.click(screen.getByRole("button", { name: "完成绑定" }));
  await screen.findByText(/邮箱：person@example.com/);
});

test("binding network loss reloads the account after the server applies the change", async () => {
  let bound = false;
  let identityReads = 0;
  server.use(
    http.get("*/api/me", () => {
      identityReads++;
      return HttpResponse.json({
        ...visitor,
        type: "ACCOUNT",
        email: bound ? "p***@example.com" : null,
        phone: "138****8000",
        displayName: "138****8000",
      });
    }),
    http.post("*/api/auth/bind", () => {
      bound = true;
      return HttpResponse.error();
    }),
  );
  const user = userEvent.setup();
  render(
    <AppProviders>
      <VisitorIdentity />
    </AppProviders>,
  );
  await user.click(await screen.findByRole("button", { name: "138****8000" }));
  await user.click(screen.getByRole("button", { name: "绑定邮箱" }));
  await user.type(screen.getByLabelText("邮箱"), "person@example.com");
  await user.type(screen.getByLabelText("验证码"), "123456");
  await user.click(screen.getByRole("button", { name: "完成绑定" }));
  await waitFor(() => expect(identityReads).toBeGreaterThan(1));
});

test("account dialog displays real task count, recent creation and a history link", async () => {
  const task = {
    id: "id-not-shown",
    templateId: "demo-1",
    templateTitle: "旅行",
    status: "SUCCEEDED",
    currentStage: null,
    failedStage: null,
    errorCode: null,
    errorMessage: null,
    resultUrl: "/api/tasks/id-not-shown/result",
    createdAt: "2026-10-28T08:00:00Z",
    startedAt: null,
    finishedAt: null,
    updatedAt: "2026-10-28T08:00:00Z",
    steps: [],
  };
  server.use(
    http.get("*/api/me", () =>
      HttpResponse.json({
        ...visitor,
        type: "ACCOUNT",
        email: "person@example.com",
        phone: null,
        displayName: "person@example.com",
      }),
    ),
    http.get("*/api/tasks", ({ request }) => {
      expect(request.headers.get("X-Studio-User")).toBe("user-1");
      expect(new URL(request.url).searchParams.get("pageSize")).toBe("1");
      return HttpResponse.json({
        items: [task],
        page: 1,
        pageSize: 1,
        total: 13,
      });
    }),
  );
  const user = userEvent.setup();
  render(
    <AppProviders>
      <VisitorIdentity />
    </AppProviders>,
  );
  await user.click(
    await screen.findByRole("button", { name: "person@example.com" }),
  );
  expect(await screen.findByText("13")).toBeTruthy();
  expect(screen.getByText("累计制作任务")).toBeTruthy();
  expect(screen.getByText("最近制作")).toBeTruthy();
  expect(
    screen.getByRole("link", { name: "查看我的记录" }).getAttribute("href"),
  ).toBe("/history");
  expect(screen.queryByText(/id-not-shown/)).toBeNull();
});

test("account statistics show a retry on request failure rather than zero", async () => {
  let available = false;
  server.use(
    http.get("*/api/me", () =>
      HttpResponse.json({
        ...visitor,
        type: "ACCOUNT",
        email: "person@example.com",
        displayName: "person@example.com",
      }),
    ),
    http.get("*/api/tasks", () =>
      available
        ? HttpResponse.json({ items: [], total: 0, page: 1, pageSize: 1 })
        : HttpResponse.json({ message: "暂不可用" }, { status: 503 }),
    ),
  );
  const user = userEvent.setup();
  render(
    <AppProviders>
      <VisitorIdentity />
    </AppProviders>,
  );
  await user.click(
    await screen.findByRole("button", { name: "person@example.com" }),
  );
  expect(await screen.findByText("制作概览暂时不可用")).toBeTruthy();
  expect(screen.queryByText("0")).toBeNull();
  available = true;
  await user.click(screen.getByRole("button", { name: "重试加载概览" }));
  expect(await screen.findByText("0")).toBeTruthy();
  expect(screen.getByText("暂无记录")).toBeTruthy();
});
