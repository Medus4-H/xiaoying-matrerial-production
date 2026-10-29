import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { once } from "node:events";

const base = process.env.BASE_URL;
const origin = process.env.PUBLIC_ORIGIN;
assert.ok(base && origin, "BASE_URL and PUBLIC_ORIGIN are required");
const url = (path) => new URL(path, base);
const session = await fetch(url("/api/session"), {
  method: "POST",
  headers: { Origin: origin },
});
assert.equal(session.status, 201);
const cookie = session.headers.get("set-cookie").split(";")[0];
const bytes = await readFile("/fixtures/example.jpg");
const form = new FormData();
form.append("file", new Blob([bytes], { type: "image/jpeg" }), "example.jpg");
const upload = await fetch(url("/api/assets"), {
  method: "POST",
  headers: { Cookie: cookie, Origin: origin },
  body: form,
});
assert.equal(upload.status, 201);
const asset = await upload.json();
const create = await fetch(url("/api/tasks"), {
  method: "POST",
  headers: {
    Cookie: cookie,
    Origin: origin,
    "Content-Type": "application/json",
  },
  body: JSON.stringify({
    templateId: "demo-1",
    assetIds: [asset.id],
    simulation: {
      assetCheckDelayMs: 120000,
      generateDelayMs: 0,
      finalizeDelayMs: 0,
    },
  }),
});
assert.equal(create.status, 202);
const task = await create.json();
let running;
for (let attempt = 0; attempt < 100; attempt++) {
  const response = await fetch(url(`/api/tasks/${task.id}`), {
    headers: { Cookie: cookie },
  });
  running = await response.json();
  if (running.status === "RUNNING") break;
  await new Promise((resolve) => setTimeout(resolve, 50));
}
assert.equal(running.status, "RUNNING");
assert.equal(running.currentStage, "ASSET_CHECK");
console.log(
  "READY: task is running; kill and restart the isolated backend, then send a line",
);
process.stdin.resume();
await once(process.stdin, "data");
const response = await fetch(url(`/api/tasks/${task.id}`), {
  headers: { Cookie: cookie },
});
assert.equal(response.status, 200);
const interrupted = await response.json();
assert.equal(interrupted.status, "FAILED");
assert.equal(interrupted.errorCode, "SERVER_RESTARTED");
assert.equal(interrupted.currentStage, "ASSET_CHECK");
assert.deepEqual(
  interrupted.steps.map((step) => step.status),
  ["FAILED", "SKIPPED", "SKIPPED"],
);
console.log("PASS: interrupted task persisted as failed without resuming");
process.stdin.pause();
