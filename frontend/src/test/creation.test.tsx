import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, expect, test, vi } from "vitest";
import { AppProviders } from "@/components/providers/AppProviders";
import { VisitorIdentity } from "@/components/identity/VisitorIdentity";
import { TemplateWorkspace } from "@/components/templates/TemplateWorkspace";
import { server } from "./server";

const template = {
  id: "demo-2",
  title: "双人",
  categoryId: "portrait",
  categoryName: "人像",
  description: "测试",
  coverUrl: "/cover.jpg",
  requiredImageCount: 2,
  acceptedMimeTypes: ["image/jpeg", "image/png"],
  maxFileSizeBytes: 8,
};
const asset = (id: string) => ({
  id,
  previewUrl: `/api/assets/${id}/content`,
  mimeType: "image/jpeg",
  sizeBytes: 3,
  width: 1,
  height: 1,
});
const task = {
  id: "task-1",
  templateId: "demo-2",
  templateTitle: "双人",
  status: "QUEUED",
  currentStage: null,
  failedStage: null,
  errorCode: null,
  errorMessage: null,
  resultUrl: null,
  createdAt: "2026-01-01T00:00:00Z",
  startedAt: null,
  finishedAt: null,
  updatedAt: "2026-01-01T00:00:00Z",
  steps: [],
};
const image = (name: string) => new File(["123"], name, { type: "image/jpeg" });
function deferred() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function renderWorkspace(count = 2, controls = false) {
  render(
    <AppProviders>
      <TemplateWorkspace
        template={{ ...template, requiredImageCount: count }}
        showSimulationControls={controls}
      />
    </AppProviders>,
  );
}
async function ready() {
  return screen.findByRole("button", { name: "开始制作" });
}
function slot(index: number) {
  return screen.getByLabelText(`上传第 ${index} 张图片`) as HTMLInputElement;
}
function choose(index: number, file: File) {
  fireEvent.change(slot(index), { target: { files: [file] } });
}

beforeEach(() => {
  let id = 0;
  Object.defineProperty(URL, "createObjectURL", {
    configurable: true,
    value: vi.fn(() => `blob:test-${++id}`),
  });
  Object.defineProperty(URL, "revokeObjectURL", {
    configurable: true,
    value: vi.fn(),
  });
});

test("template controls slot count and rejects invalid file type or size", async () => {
  let uploads = 0;
  server.use(
    http.post("*/api/assets", () => {
      uploads++;
      return HttpResponse.json(asset("a"), { status: 201 });
    }),
  );
  renderWorkspace(3);
  const submit = await ready();
  expect(screen.getAllByLabelText(/上传第 \d 张图片/)).toHaveLength(3);
  expect(submit.hasAttribute("disabled")).toBe(true);
  choose(1, new File(["x"], "bad.gif", { type: "image/gif" }));
  expect(await screen.findByText(/不支持.*类型/)).toBeTruthy();
  choose(1, new File(["123456789"], "big.jpg", { type: "image/jpeg" }));
  expect(await screen.findByText(/图片大小超过模板限制/)).toBeTruthy();
  expect(uploads).toBe(0);
});

test("uploads block submission, accepted tasks release the form and retain selected assets", async () => {
  const uploadGate = deferred();
  const submitGate = deferred();
  let submitted: unknown;
  let calls = 0;
  let uploads = 0;
  server.use(
    http.post("*/api/assets", async () => {
      const number = ++uploads;
      if (number === 1) await uploadGate.promise;
      return HttpResponse.json(asset(number === 1 ? "first" : "second"), {
        status: 201,
      });
    }),
    http.post("*/api/tasks", async ({ request }) => {
      calls++;
      submitted = await request.json();
      await submitGate.promise;
      return HttpResponse.json(task, { status: 202 });
    }),
  );
  const user = userEvent.setup();
  renderWorkspace();
  const submit = await ready();
  choose(1, image("slow.jpg"));
  choose(2, image("second.jpg"));
  expect((await screen.findAllByText(/上传中/)).length).toBeGreaterThan(0);
  expect(submit.hasAttribute("disabled")).toBe(true);
  await act(async () => {
    uploadGate.resolve();
  });
  await waitFor(() => expect(submit.hasAttribute("disabled")).toBe(false));
  await user.click(submit);
  expect(await screen.findByRole("button", { name: "提交中…" })).toHaveProperty(
    "disabled",
    true,
  );
  expect(calls).toBe(1);
  expect(submitted).toEqual({
    templateId: "demo-2",
    assetIds: ["first", "second"],
  });
  await act(async () => {
    submitGate.resolve();
  });
  expect(
    (await screen.findByRole("link", { name: "查看详情" })).getAttribute(
      "href",
    ),
  ).toBe("/tasks/task-1");
  expect((await ready()).hasAttribute("disabled")).toBe(false);
  expect(screen.getAllByRole("button", { name: /移除第/ })).toHaveLength(2);
});

test("failed replacement keeps the previously uploaded image", async () => {
  let uploads = 0;
  server.use(
    http.post("*/api/assets", () => {
      uploads++;
      return uploads === 2
        ? HttpResponse.json({ message: "上传失败" }, { status: 500 })
        : HttpResponse.json(asset("old"), { status: 201 });
    }),
  );
  renderWorkspace(1);
  await ready();
  choose(1, image("old.jpg"));
  await screen.findByAltText("第 1 张已上传图片");
  choose(1, image("broken.jpg"));
  expect(await screen.findByText(/上传失败/)).toBeTruthy();
  expect(screen.getByAltText("第 1 张已上传图片").getAttribute("src")).toBe(
    "/api/assets/old/content",
  );
  expect(
    screen.getByRole("button", { name: "开始制作" }).hasAttribute("disabled"),
  ).toBe(false);
});

test("new replacement and removal invalidate late upload responses", async () => {
  const first = deferred();
  const third = deferred();
  let uploads = 0;
  server.use(
    http.post("*/api/assets", async () => {
      const number = ++uploads;
      if (number === 1) await first.promise;
      if (number === 3) await third.promise;
      return HttpResponse.json(asset(`image-${number}`), { status: 201 });
    }),
  );
  const user = userEvent.setup();
  renderWorkspace(1);
  const submit = await ready();
  choose(1, image("first.jpg"));
  await waitFor(() => expect(uploads).toBe(1));
  choose(1, image("second.jpg"));
  await waitFor(() =>
    expect(
      screen.getByAltText("第 1 张已上传图片").getAttribute("src"),
    ).toContain("image-2"),
  );
  await act(async () => {
    first.resolve();
  });
  expect(
    screen.getByAltText("第 1 张已上传图片").getAttribute("src"),
  ).toContain("image-2");
  choose(1, image("third.jpg"));
  await waitFor(() => expect(uploads).toBe(3));
  await user.click(screen.getByRole("button", { name: "移除第 1 张图片" }));
  await act(async () => {
    third.resolve();
  });
  expect(screen.queryByAltText("第 1 张已上传图片")).toBeNull();
  expect(submit.hasAttribute("disabled")).toBe(true);
  expect(URL.revokeObjectURL).toHaveBeenCalled();
});

test("unknown submit outcome warns to inspect history and does not retry automatically", async () => {
  let calls = 0;
  server.use(
    http.post("*/api/assets", () =>
      HttpResponse.json(asset("a"), { status: 201 }),
    ),
    http.post("*/api/tasks", () => {
      calls++;
      return HttpResponse.error();
    }),
  );
  const user = userEvent.setup();
  renderWorkspace(1);
  const submit = await ready();
  choose(1, image("a.jpg"));
  await waitFor(() => expect(submit.hasAttribute("disabled")).toBe(false));
  await user.click(submit);
  expect(await screen.findByText(/无法确认.*历史/)).toBeTruthy();
  expect(calls).toBe(1);
  expect(submit.hasAttribute("disabled")).toBe(false);
});

test("optional simulation controls submit only selected demo settings", async () => {
  let body: unknown;
  server.use(
    http.post("*/api/assets", () =>
      HttpResponse.json(asset("a"), { status: 201 }),
    ),
    http.post("*/api/tasks", async ({ request }) => {
      body = await request.json();
      return HttpResponse.json(task, { status: 202 });
    }),
  );
  const user = userEvent.setup();
  renderWorkspace(1, true);
  const submit = await ready();
  choose(1, image("a.jpg"));
  await waitFor(() => expect(submit.hasAttribute("disabled")).toBe(false));
  await user.click(screen.getByText("模拟设置"));
  const panel = screen.getByLabelText("模拟设置");
  await user.selectOptions(
    within(panel).getByLabelText("指定失败阶段"),
    "GENERATE",
  );
  await user.click(submit);
  expect(
    (await screen.findByRole("link", { name: "查看详情" })).getAttribute(
      "href",
    ),
  ).toBe("/tasks/task-1");
  expect(body).toEqual({
    templateId: "demo-2",
    assetIds: ["a"],
    simulation: { failAt: "GENERATE" },
  });
});

test("leaving the form aborts pending upload and releases its preview", async () => {
  const gate = deferred();
  let started = false;
  server.use(
    http.post("*/api/assets", async () => {
      started = true;
      await gate.promise;
      return HttpResponse.json(asset("late"), { status: 201 });
    }),
  );
  const { unmount } = render(
    <AppProviders>
      <TemplateWorkspace template={{ ...template, requiredImageCount: 1 }} />
    </AppProviders>,
  );
  await ready();
  choose(1, image("pending.jpg"));
  await waitFor(() => expect(started).toBe(true));
  unmount();
  expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:test-1");
  await act(async () => {
    gate.resolve();
  });
  expect(screen.queryByAltText("第 1 张已上传图片")).toBeNull();
});

test("an expired visitor cannot submit and a new visitor does not inherit a pending upload", async () => {
  const gate = deferred();
  let started = false;
  server.use(
    http.get("*/api/me", () =>
      HttpResponse.json({
        id: "old",
        displayName: "旧访客",
        expiresAt: new Date(Date.now() + 700).toISOString(),
      }),
    ),
    http.post("*/api/session", () =>
      HttpResponse.json(
        { id: "new", displayName: "新访客", expiresAt: "2099-01-01T00:00:00Z" },
        { status: 201 },
      ),
    ),
    http.post("*/api/assets", async () => {
      started = true;
      await gate.promise;
      return HttpResponse.json(asset("old-asset"), { status: 201 });
    }),
  );
  const user = userEvent.setup();
  render(
    <AppProviders>
      <VisitorIdentity />
      <TemplateWorkspace template={{ ...template, requiredImageCount: 1 }} />
    </AppProviders>,
  );
  await ready();
  choose(1, image("pending.jpg"));
  await waitFor(() => expect(started).toBe(true));
  await user.click(
    await screen.findByRole(
      "button",
      { name: "建立新访客身份" },
      { timeout: 3000 },
    ),
  );
  await screen.findByRole("button", { name: "新访客" });
  await act(async () => {
    gate.resolve();
  });
  expect(screen.queryByAltText("第 1 张已上传图片")).toBeNull();
  expect((await ready()).hasAttribute("disabled")).toBe(true);
});

test("a submit timeout is treated as an unknown outcome", async () => {
  server.use(
    http.post("*/api/assets", () =>
      HttpResponse.json(asset("a"), { status: 201 }),
    ),
    http.post("*/api/tasks", () =>
      HttpResponse.json({ message: "请求超时" }, { status: 408 }),
    ),
  );
  const user = userEvent.setup();
  renderWorkspace(1);
  const submit = await ready();
  choose(1, image("a.jpg"));
  await waitFor(() => expect(submit.hasAttribute("disabled")).toBe(false));
  await user.click(submit);
  expect(await screen.findByText(/无法确认.*历史/)).toBeTruthy();
});

test("small configured file limits remain understandable", async () => {
  renderWorkspace(1);
  await ready();
  expect(screen.getByText(/每张不超过 8 B/)).toBeTruthy();
});

test("binding a contact preserves an already uploaded image in the creation form", async () => {
  server.use(
    http.get("*/api/me", () =>
      HttpResponse.json({
        id: "user-1",
        type: "ACCOUNT",
        email: "person@example.com",
        phone: null,
        displayName: "person@example.com",
        expiresAt: "2099-01-01T00:00:00Z",
      }),
    ),
    http.post("*/api/assets", () =>
      HttpResponse.json(asset("saved-asset"), { status: 201 }),
    ),
    http.post("*/api/auth/bind", () =>
      HttpResponse.json({
        id: "user-1",
        type: "ACCOUNT",
        email: "person@example.com",
        phone: "13800138000",
        displayName: "person@example.com",
        expiresAt: "2099-01-01T00:00:00Z",
      }),
    ),
  );
  const user = userEvent.setup();
  render(
    <AppProviders>
      <VisitorIdentity />
      <TemplateWorkspace template={{ ...template, requiredImageCount: 1 }} />
    </AppProviders>,
  );
  await ready();
  choose(1, image("kept.jpg"));
  await waitFor(() =>
    expect(
      screen.getByRole("button", { name: "开始制作" }).hasAttribute("disabled"),
    ).toBe(false),
  );
  await user.click(screen.getByRole("button", { name: "person@example.com" }));
  await user.click(screen.getByRole("button", { name: "绑定手机号" }));
  await user.type(screen.getByLabelText("手机号"), "13800138000");
  await user.type(screen.getByLabelText("验证码"), "123456");
  expect(screen.queryByLabelText("密码")).toBeNull();
  await user.click(screen.getByRole("button", { name: "完成绑定" }));
  await screen.findByText(/手机号：13800138000/);
  expect(
    screen.getByRole("button", { name: "开始制作" }).hasAttribute("disabled"),
  ).toBe(false);
});
