import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const base = process.env.BASE_URL;
const origin = process.env.PUBLIC_ORIGIN;
assert.ok(base && origin, "BASE_URL and PUBLIC_ORIGIN are required");
const url = (path) => new URL(path, base);
async function visitor() {
  const response = await fetch(url("/api/session"), {
    method: "POST",
    headers: { Origin: origin },
  });
  assert.equal(response.status, 201);
  return response.headers.get("set-cookie").split(";")[0];
}
const a = await visitor();
const b = await visitor();
const bytes = await readFile("/fixtures/example.jpg");
const form = new FormData();
form.append("file", new Blob([bytes], { type: "image/jpeg" }), "example.jpg");
const upload = await fetch(url("/api/assets"), {
  method: "POST",
  headers: { Cookie: a, Origin: origin },
  body: form,
});
assert.equal(upload.status, 201);
const assetId = (await upload.json()).id;
const stages = ["ASSET_CHECK", "GENERATE", "FINALIZE"];
async function create(simulation) {
  const response = await fetch(url("/api/tasks"), {
    method: "POST",
    headers: { Cookie: a, Origin: origin, "Content-Type": "application/json" },
    body: JSON.stringify({
      templateId: "demo-1",
      assetIds: [assetId],
      simulation,
    }),
  });
  assert.equal(response.status, 202, await response.clone().text());
  return response.json();
}
async function finished(id) {
  const deadline = Date.now() + 15000;
  while (Date.now() < deadline) {
    const response = await fetch(url("/api/tasks/" + id), {
      headers: { Cookie: a },
    });
    assert.equal(response.status, 200);
    const task = await response.json();
    if (task.status === "SUCCEEDED" || task.status === "FAILED") return task;
    await new Promise((resolve) => setTimeout(resolve, 50));
  }
  throw new Error("Task timed out: " + id);
}
const slow = {
  assetCheckDelayMs: 1000,
  generateDelayMs: 150,
  finalizeDelayMs: 150,
};
const one = await create(slow);
const two = await create({ ...slow, failAt: "GENERATE" });
assert.notEqual(one.id, two.id);
assert.ok(["QUEUED", "RUNNING"].includes(one.status));
const active = await (
  await fetch(url("/api/tasks/active"), { headers: { Cookie: a } })
).json();
assert.ok(active.items.some((item) => item.id === one.id));
assert.ok(active.items.some((item) => item.id === two.id));
assert.equal(
  (await fetch(url("/api/tasks/" + one.id), { headers: { Cookie: b } })).status,
  404,
);
const [success, failure] = await Promise.all([
  finished(one.id),
  finished(two.id),
]);
assert.equal(success.status, "SUCCEEDED");
assert.deepEqual(
  success.steps.map((step) => step.status),
  ["SUCCEEDED", "SUCCEEDED", "SUCCEEDED"],
);
assert.equal(failure.status, "FAILED");
assert.equal(failure.failedStage, "GENERATE");
assert.deepEqual(
  failure.steps.map((step) => step.status),
  ["SUCCEEDED", "FAILED", "SKIPPED"],
);
const result = await fetch(url(success.resultUrl), { headers: { Cookie: a } });
assert.equal(result.status, 200);
assert.match(result.headers.get("content-type"), /image\/jpeg/);
const picture = new Uint8Array(await result.arrayBuffer());
assert.ok(picture.length > 1024 && picture[0] === 0xff && picture[1] === 0xd8);
assert.equal(
  (await fetch(url(success.resultUrl), { headers: { Cookie: b } })).status,
  404,
);
for (const stage of stages) {
  const task = await create({
    failAt: stage,
    assetCheckDelayMs: 20,
    generateDelayMs: 20,
    finalizeDelayMs: 20,
  });
  const failed = await finished(task.id);
  assert.equal(failed.status, "FAILED");
  assert.equal(failed.failedStage, stage);
  assert.deepEqual(
    failed.steps.map((step) => step.status),
    stages.map((_, i) =>
      i < stages.indexOf(stage)
        ? "SUCCEEDED"
        : i === stages.indexOf(stage)
          ? "FAILED"
          : "SKIPPED",
    ),
  );
}
const history = await (
  await fetch(url("/api/tasks?templateId=demo-1"), { headers: { Cookie: a } })
).json();
assert.equal(history.total, 5);
assert.equal(
  (await (await fetch(url("/api/tasks"), { headers: { Cookie: b } })).json())
    .total,
  0,
);
assert.equal(
  (
    await (
      await fetch(url("/api/tasks/active"), { headers: { Cookie: a } })
    ).json()
  ).items.length,
  0,
);
assert.equal(
  (
    await (
      await fetch(url("/api/tasks/" + one.id), { headers: { Cookie: a } })
    ).json()
  ).status,
  "SUCCEEDED",
);
console.log(
  "PASS: asynchronous acceptance, concurrent tasks, three failure stages, result preview, history and user isolation",
);
