import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";

// Run only against a disposable demo stack. This creates accounts, files and tasks.
const base = process.env.BASE_URL ?? "http://127.0.0.1:3000";
const origin = process.env.PUBLIC_ORIGIN ?? "http://localhost:3109";
const jar = new Map();

async function call(path, { method = "GET", body, scope } = {}) {
  const headers = { Origin: origin };
  if (jar.size)
    headers.Cookie = [...jar]
      .map(([name, value]) => `${name}=${value}`)
      .join("; ");
  if (scope) headers["X-Studio-User"] = scope;
  if (body && !(body instanceof FormData))
    headers["Content-Type"] = "application/json";
  const response = await fetch(new URL(path, base), {
    method,
    headers,
    body:
      body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
  });
  for (const cookie of response.headers.getSetCookie()) {
    const [pair] = cookie.split(";");
    const index = pair.indexOf("=");
    const name = pair.slice(0, index);
    const value = pair.slice(index + 1);
    if (/max-age=0(?:;|$)/i.test(cookie) || !value) jar.delete(name);
    else jar.set(name, value);
  }
  return response;
}

async function json(path, options, expected = 200) {
  const response = await call(path, options);
  assert.equal(
    response.status,
    expected,
    `${options?.method ?? "GET"} ${path}`,
  );
  return response.json();
}

const cover = await call("/api/templates/demo-1/cover");
assert.equal(cover.status, 200);
const image = await cover.blob();
async function createTask(userId) {
  const form = new FormData();
  form.set("file", image, "fixture.jpg");
  const asset = await json(
    "/api/assets",
    { method: "POST", body: form, scope: userId },
    201,
  );
  const task = await json(
    "/api/tasks",
    {
      method: "POST",
      scope: userId,
      body: {
        templateId: "demo-1",
        assetIds: [asset.id],
        simulation: {
          assetCheckDelayMs: 0,
          generateDelayMs: 0,
          finalizeDelayMs: 0,
        },
      },
    },
    202,
  );
  return { asset, task };
}
const history = (userId) => json("/api/tasks", { scope: userId });
const password = "demo-password-123";
const runId = randomUUID();
const emailA = `scope-a-${runId}@example.test`;
const emailB = `scope-b-${runId}@example.test`;
const visitor = await json("/api/session", { method: "POST" }, 201);
const previous = await createTask(visitor.id);
const accountB = await json("/api/auth/register", {
  method: "POST",
  scope: visitor.id,
  body: {
    channel: "email",
    destination: emailB,
    password,
    code: "123456",
    importGuest: false,
  },
});
assert.notEqual(accountB.id, visitor.id);
assert.equal((await history(accountB.id)).total, 0);
assert.equal(
  (await call(`/api/tasks/${previous.task.id}`, { scope: accountB.id })).status,
  404,
);
assert.equal(
  (
    await call(`/api/assets/${previous.asset.id}/content`, {
      scope: accountB.id,
    })
  ).status,
  404,
);
assert.equal(
  (await call("/api/auth/logout", { method: "POST", scope: accountB.id }))
    .status,
  204,
);
assert.equal((await json("/api/me")).id, visitor.id);
assert.equal((await history(visitor.id)).items[0].id, previous.task.id);

const accountA = await json("/api/auth/register", {
  method: "POST",
  scope: visitor.id,
  body: {
    channel: "email",
    destination: emailA,
    password,
    code: "123456",
    importGuest: false,
  },
});
const owned = await createTask(accountA.id);
assert.equal((await history(accountA.id)).items[0].id, owned.task.id);
assert.equal(
  (await call("/api/auth/logout", { method: "POST", scope: accountA.id }))
    .status,
  204,
);
assert.equal((await json("/api/me")).id, visitor.id);
const loginB = await json("/api/auth/login", {
  method: "POST",
  body: { identifier: emailB, password },
});
assert.equal(loginB.id, accountB.id);
assert.equal((await history(loginB.id)).total, 0);
assert.equal(
  (await call(`/api/tasks/${owned.task.id}`, { scope: loginB.id })).status,
  404,
);
assert.equal(
  (await call(`/api/assets/${owned.asset.id}/content`, { scope: loginB.id }))
    .status,
  404,
);
assert.equal((await call("/api/tasks", { scope: accountA.id })).status, 409);
const loginA = await json("/api/auth/login", {
  method: "POST",
  body: { identifier: emailA, password },
});
assert.equal(loginA.id, accountA.id);
assert.equal((await history(loginA.id)).items[0].id, owned.task.id);
console.log(
  "PASS: guest separation and restoration, two-account switching, history, task and asset isolation, stale identity guard",
);
