import assert from "node:assert/strict";

let raw = "";
for await (const chunk of process.stdin) raw += chunk;
const entries = raw.split("\n").flatMap((line) => {
  try { return [JSON.parse(line)]; } catch { return []; }
});
const created = entries.filter((item) => item.event === "task.created" && item.requestId && item.taskId);
assert.ok(created.length > 0, "task creation must link request and task IDs");
const linked = created.find((item) =>
  entries.some((http) => http.event === "http.request" && http.requestId === item.requestId && http.route === "/api/tasks") &&
  entries.some((step) => step.event === "task.stage.finished" && step.taskId === item.taskId && typeof step.durationMs === "number"),
);
assert.ok(linked, "request, task and stage must correlate");
assert.ok(entries.some((item) => item.event === "task.claimed" && typeof item.queueWaitMs === "number" && typeof item.activeThreads === "number"));
assert.ok(entries.some((item) => item.event === "task.stage.finished" && typeof item.globalHeapUsedBytes === "number"));
const taskPolls = entries.filter((item) => item.event === "http.request" && item.method === "GET" && item.route === "/api/tasks/{id}" && item.status === 200);
assert.equal(taskPolls.length, 0, "fast successful polling should not flood logs");
assert.ok(!raw.includes("example.jpg"));
assert.ok(!raw.includes("fake.png"));
if (process.env.DIAGNOSTICS_SECRET) assert.ok(!raw.includes(process.env.DIAGNOSTICS_SECRET));
console.log("PASS: request/task correlation, queue and JVM snapshots, polling noise and fixture-name privacy");
