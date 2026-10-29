import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile, unlink, writeFile } from "node:fs/promises";

const { BASE_URL: base, PUBLIC_ORIGIN: origin } = process.env;
assert.ok(base && origin, "BASE_URL and PUBLIC_ORIGIN are required");
const url = (path) => new URL(path, base);
const proofFile = "/proof/persistence-state.json";
const digest = (bytes) => createHash("sha256").update(bytes).digest("hex");
const phase = process.argv[2];

async function request(path, options = {}) {
  return fetch(url(path), options);
}

async function content(path, cookie) {
  const response = await request(path, { headers: { Cookie: cookie } });
  assert.equal(response.status, 200);
  return Buffer.from(await response.arrayBuffer());
}

if (phase === "prepare") {
  const session = await request("/api/session", {
    method: "POST",
    headers: { Origin: origin },
  });
  assert.equal(session.status, 201);
  const cookie = session.headers.get("set-cookie").split(";")[0];
  const user = await session.json();
  const bytes = await readFile("/fixtures/example.jpg");
  const body = new FormData();
  body.append("file", new Blob([bytes], { type: "image/jpeg" }), "example.jpg");
  const uploaded = await request("/api/assets", {
    method: "POST",
    headers: { Cookie: cookie, Origin: origin },
    body,
  });
  assert.equal(uploaded.status, 201);
  const asset = await uploaded.json();
  const accepted = await request("/api/tasks", {
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
        assetCheckDelayMs: 20,
        generateDelayMs: 20,
        finalizeDelayMs: 20,
      },
    }),
  });
  assert.equal(accepted.status, 202);
  const initial = await accepted.json();
  const deadline = Date.now() + 15000;
  let completed;
  while (Date.now() < deadline) {
    const response = await request(`/api/tasks/${initial.id}`, {
      headers: { Cookie: cookie },
    });
    assert.equal(response.status, 200);
    const task = await response.json();
    if (task.status === "SUCCEEDED" || task.status === "FAILED") {
      completed = task;
      break;
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  assert.equal(completed?.status, "SUCCEEDED");
  const result = await content(completed.resultUrl, cookie);
  assert.deepEqual(await content(asset.previewUrl, cookie), bytes);
  await writeFile(
    proofFile,
    JSON.stringify({
      cookie,
      userId: user.id,
      assetUrl: asset.previewUrl,
      assetHash: digest(bytes),
      taskId: initial.id,
      resultUrl: completed.resultUrl,
      resultHash: digest(result),
    }),
    { mode: 0o600 },
  );
  console.log(
    "PASS: isolated session, original asset and completed result prepared",
  );
} else if (phase === "verify") {
  const proof = JSON.parse(await readFile(proofFile, "utf8"));
  const headers = { Cookie: proof.cookie };
  const me = await request("/api/me", { headers });
  assert.equal(me.status, 200);
  assert.equal((await me.json()).id, proof.userId);
  assert.equal(
    digest(await content(proof.assetUrl, proof.cookie)),
    proof.assetHash,
  );
  const taskResponse = await request(`/api/tasks/${proof.taskId}`, { headers });
  assert.equal(taskResponse.status, 200);
  const task = await taskResponse.json();
  assert.equal(task.status, "SUCCEEDED");
  assert.equal(task.resultUrl, proof.resultUrl);
  assert.equal(
    digest(await content(proof.resultUrl, proof.cookie)),
    proof.resultHash,
  );
  const history = await request("/api/tasks", { headers });
  assert.equal(history.status, 200);
  assert.ok(
    (await history.json()).items.some((item) => item.id === proof.taskId),
  );
  const other = await request("/api/session", {
    method: "POST",
    headers: { Origin: origin },
  });
  assert.equal(other.status, 201);
  const otherCookie = other.headers.get("set-cookie").split(";")[0];
  for (const path of [
    proof.assetUrl,
    `/api/tasks/${proof.taskId}`,
    proof.resultUrl,
  ]) {
    assert.equal(
      (await request(path, { headers: { Cookie: otherCookie } })).status,
      404,
    );
  }
  await unlink(proofFile);
  console.log(
    "PASS: session, task history, asset and result bytes survived container recreation; owner isolation preserved",
  );
} else {
  throw new Error("Expected prepare or verify");
}
