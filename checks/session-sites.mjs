import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";

// Run only against two fresh, isolated Compose projects. This is an API-level
// cookie-jar test, not a browser upload or a substitute for browser acceptance.
const sites = ["A", "B"].map((key) => ({
  base: process.env[`SITE_${key}`],
  origin: process.env[`ORIGIN_${key}`],
  cookieName: process.env[`COOKIE_${key}`],
}));
const legacyName = process.env.LEGACY_COOKIE_NAME || "studio_session";
assert.ok(sites.every((site) => site.base && site.origin && site.cookieName));
assert.notEqual(sites[0].cookieName, sites[1].cookieName);
const jar = new Map();
const fixture = await readFile("/fixtures/example.jpg");

async function request(site, path, options = {}) {
  const headers = new Headers(options.headers);
  if (jar.size)
    headers.set(
      "Cookie",
      [...jar].map(([key, value]) => `${key}=${value}`).join("; "),
    );
  if (options.method && options.method !== "GET")
    headers.set("Origin", site.origin);
  const response = await fetch(new URL(path, site.base), {
    ...options,
    headers,
  });
  for (const header of response.headers.getSetCookie()) {
    const [pair] = header.split(";");
    const index = pair.indexOf("=");
    const name = pair.slice(0, index);
    const value = pair.slice(index + 1);
    if (!value || /max-age=0(?:;|$)/i.test(header)) jar.delete(name);
    else jar.set(name, value);
  }
  return response;
}

async function identity(site) {
  const response = await request(site, "/api/me");
  assert.equal(response.status, 200);
  return (await response.json()).id;
}

async function submit(site) {
  const form = new FormData();
  form.append(
    "file",
    new Blob([fixture], { type: "image/jpeg" }),
    "example.jpg",
  );
  const uploaded = await request(site, "/api/assets", {
    method: "POST",
    body: form,
  });
  assert.equal(uploaded.status, 201);
  const asset = await uploaded.json();
  const accepted = await request(site, "/api/tasks", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ templateId: "demo-1", assetIds: [asset.id] }),
  });
  assert.equal(accepted.status, 202);
  const task = await accepted.json();
  for (let attempt = 0; attempt < 60; attempt++) {
    const response = await request(site, `/api/tasks/${task.id}`);
    assert.equal(response.status, 200);
    const state = await response.json();
    if (state.status === "SUCCEEDED") return { asset, task: state };
    assert.notEqual(state.status, "FAILED");
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error("Timed out waiting for task completion");
}

const first = await request(sites[0], "/api/session", { method: "POST" });
assert.equal(first.status, 201);
const firstId = (await first.json()).id;
assert.ok(jar.has(sites[0].cookieName));
jar.set(legacyName, jar.get(sites[0].cookieName));
jar.delete(sites[0].cookieName);
jar.delete(`${sites[0].cookieName}_migrated`);
assert.equal(
  await identity(sites[0]),
  firstId,
  "valid legacy identity must migrate",
);
assert.ok(jar.has(legacyName), "migration must preserve the original cookie");
assert.ok(jar.has(sites[0].cookieName));

assert.equal((await request(sites[1], "/api/me")).status, 401);
const second = await request(sites[1], "/api/session", { method: "POST" });
assert.equal(second.status, 201);
const secondId = (await second.json()).id;
assert.notEqual(firstId, secondId);
assert.ok(jar.has(sites[0].cookieName) && jar.has(sites[1].cookieName));

const taskA = await submit(sites[0]);
const taskB = await submit(sites[1]);
for (let round = 0; round < 3; round++) {
  assert.equal(await identity(sites[0]), firstId);
  assert.equal(await identity(sites[1]), secondId);
}
for (const [site, own, other] of [
  [sites[0], taskA, taskB],
  [sites[1], taskB, taskA],
]) {
  const history = await request(site, "/api/tasks?page=1&pageSize=20");
  assert.equal(history.status, 200);
  const tasks = (await history.json()).items;
  assert.ok(
    tasks.some(
      (task) => task.id === own.task.id && task.status === "SUCCEEDED",
    ),
  );
  assert.ok(!tasks.some((task) => task.id === other.task.id));
  assert.equal((await request(site, own.task.resultUrl)).status, 200);
  assert.equal(
    (await request(site, `/api/tasks/${other.task.id}`)).status,
    404,
  );
  assert.equal(
    (await request(site, `/api/assets/${other.asset.id}/content`)).status,
    404,
  );
}
const registration = await request(sites[0], "/api/auth/register", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    channel: "email",
    destination: `${randomUUID()}@example.com`,
    code: process.env.AUTH_DEMO_CODE || "123456",
    password: "test-only-password",
    importGuest: false,
  }),
});
assert.equal(registration.status, 200);
const accountId = (await registration.json()).id;
const scopedName = sites[0].cookieName;
assert.ok(jar.has(scopedName) && jar.has(`${scopedName}_visitor`));
jar.set(legacyName, jar.get(scopedName));
jar.set(`${legacyName}_visitor`, jar.get(`${scopedName}_visitor`));
jar.delete(scopedName);
jar.delete(`${scopedName}_visitor`);
jar.delete(`${scopedName}_migrated`);
assert.equal(await identity(sites[0]), accountId);
assert.ok(
  jar.has(scopedName) && jar.has(`${scopedName}_visitor`),
  "proxy must preserve both migrated cookies",
);
assert.equal(
  (await request(sites[0], "/api/auth/logout", { method: "POST" })).status,
  204,
);
assert.equal(
  await identity(sites[0]),
  firstId,
  "logout must restore the legacy visitor",
);
assert.ok(
  (
    await (await request(sites[0], "/api/tasks?page=1&pageSize=20")).json()
  ).items.some((task) => task.id === taskA.task.id),
);
assert.equal(
  await identity(sites[1]),
  secondId,
  "account switching must not affect the other site",
);
console.log(
  "PASS: legacy migration, separate site cookies, repeated switching, task history and resource isolation",
);
