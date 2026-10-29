import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { TaskTracker } from "@/features/tasks/task-tracker";
import type { TaskSnapshot } from "@/lib/api/types";

function task(
  id: string,
  status: TaskSnapshot["status"] = "RUNNING",
): TaskSnapshot {
  return {
    id,
    templateId: "demo-1",
    templateTitle: id,
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
    steps: [],
  };
}
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

describe("TaskTracker", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  test("isolates query errors from task failure and other tasks; resume and terminal stop", async () => {
    const requests: string[] = [];
    let aUnavailable = true;
    const tracker = new TaskTracker({
      intervalMs: 2000,
      listActive: async () => [],
      getTask: async (id) => {
        requests.push(id);
        if (id === "a" && aUnavailable) throw new Error("网络不可用");
        return task(id, id === "a" ? "FAILED" : "SUCCEEDED");
      },
    });
    tracker.start();
    await vi.advanceTimersByTimeAsync(0);
    tracker.register(task("a"));
    tracker.register(task("b"));
    await vi.advanceTimersByTimeAsync(2000);
    expect(tracker.snapshot().tasks.a.snapshot.status).toBe("RUNNING");
    expect(tracker.snapshot().tasks.a.queryError).toBeTruthy();
    expect(tracker.snapshot().tasks.b.snapshot.status).toBe("SUCCEEDED");
    expect(tracker.snapshot().tasks.b.queryError).toBeNull();
    await vi.advanceTimersByTimeAsync(10000);
    expect(requests).toEqual(["a", "b"]);
    aUnavailable = false;
    await tracker.resume("a");
    expect(tracker.snapshot().tasks.a.snapshot.status).toBe("FAILED");
    expect(tracker.snapshot().tasks.a.snapshot.errorMessage).toBe("模拟失败");
    expect(tracker.snapshot().tasks.a.queryError).toBeNull();
    await vi.advanceTimersByTimeAsync(10000);
    expect(requests).toEqual(["a", "b", "a"]);
    expect(vi.getTimerCount()).toBe(0);
    tracker.dispose();
  });

  test("restores every active task independently of history pages", async () => {
    const active = Array.from({ length: 24 }, (_, i) => task(`task-${i}`));
    const tracker = new TaskTracker({
      intervalMs: 2000,
      listActive: async () => active,
      getTask: async (id) => task(id, "SUCCEEDED"),
    });
    tracker.start();
    await vi.advanceTimersByTimeAsync(0);
    expect(Object.keys(tracker.snapshot().tasks)).toHaveLength(24);
    expect(tracker.snapshot().tasks["task-23"].snapshot.status).toBe("RUNNING");
    await vi.advanceTimersByTimeAsync(2000);
    expect(tracker.snapshot().tasks["task-23"].snapshot.status).toBe(
      "SUCCEEDED",
    );
    expect(vi.getTimerCount()).toBe(0);
    tracker.dispose();
  });

  test("disposing cancels old requests and late responses cannot update a new tracker", async () => {
    const pending = deferred<TaskSnapshot>();
    let signal: AbortSignal | undefined;
    const old = new TaskTracker({
      intervalMs: 2000,
      listActive: async () => [],
      getTask: async (_id, requestSignal) => {
        signal = requestSignal;
        return pending.promise;
      },
    });
    old.start();
    await vi.advanceTimersByTimeAsync(0);
    old.register(task("a"));
    await vi.advanceTimersByTimeAsync(2000);
    expect(signal?.aborted).toBe(false);
    old.dispose();
    expect(signal?.aborted).toBe(true);
    const current = new TaskTracker({
      intervalMs: 2000,
      listActive: async () => [],
      getTask: async (id) => task(id),
    });
    current.start();
    await vi.advanceTimersByTimeAsync(0);
    pending.resolve(task("a", "SUCCEEDED"));
    await vi.advanceTimersByTimeAsync(0);
    expect(current.snapshot().tasks.a).toBeUndefined();
    expect(old.snapshot().tasks.a.snapshot.status).toBe("RUNNING");
    current.dispose();
  });

  test("preserves terminal snapshots against older history or active snapshots", async () => {
    const tracker = new TaskTracker({
      intervalMs: 2000,
      listActive: async () => [],
      getTask: async (id) => task(id),
    });
    tracker.start();
    await vi.advanceTimersByTimeAsync(0);
    tracker.register(task("a", "SUCCEEDED"));
    tracker.register(task("a", "RUNNING"));
    expect(tracker.snapshot().tasks.a.snapshot.status).toBe("SUCCEEDED");
    await vi.advanceTimersByTimeAsync(6000);
    expect(vi.getTimerCount()).toBe(0);
    tracker.dispose();
  });

  test("active-list failure is visible and can be retried", async () => {
    let failed = true;
    const tracker = new TaskTracker({
      intervalMs: 2000,
      listActive: async () => {
        if (failed) throw new Error("不可用");
        return [task("a")];
      },
      getTask: async (id) => task(id),
    });
    tracker.start();
    await vi.advanceTimersByTimeAsync(0);
    expect(tracker.snapshot().loadError).toBeTruthy();
    failed = false;
    await tracker.reloadActive();
    expect(tracker.snapshot().loadError).toBeNull();
    expect(tracker.snapshot().tasks.a.snapshot.status).toBe("RUNNING");
    tracker.dispose();
  });
});

test("a stale pending response cannot stop tracking a newer pending task", async () => {
  vi.useFakeTimers();
  const stale = { ...task("stale"), updatedAt: "2026-01-01T00:00:01Z" };
  const newer = { ...task("stale"), updatedAt: "2026-01-01T00:00:02Z" };
  let calls = 0;
  const tracker = new TaskTracker({
    intervalMs: 2000,
    listActive: async () => [],
    getTask: async () => {
      calls++;
      return calls === 1 ? stale : task("stale", "SUCCEEDED");
    },
  });
  tracker.start();
  await vi.advanceTimersByTimeAsync(0);
  tracker.register(newer);
  await vi.advanceTimersByTimeAsync(2000);
  expect(tracker.snapshot().tasks.stale.snapshot.updatedAt).toBe(
    newer.updatedAt,
  );
  await vi.advanceTimersByTimeAsync(2000);
  expect(calls).toBe(2);
  expect(tracker.snapshot().tasks.stale.snapshot.status).toBe("SUCCEEDED");
  tracker.dispose();
  vi.useRealTimers();
});

test("background tabs pause polls and foreground resumes unfinished tasks", async () => {
  vi.useFakeTimers();
  let calls = 0;
  const tracker = new TaskTracker({
    intervalMs: 2000,
    listActive: async () => [],
    getTask: async (id) => {
      calls++;
      return task(id, "SUCCEEDED");
    },
  });
  tracker.start();
  await vi.advanceTimersByTimeAsync(0);
  tracker.register(task("a"));
  tracker.setVisible(false);
  await vi.advanceTimersByTimeAsync(10000);
  expect(calls).toBe(0);
  tracker.setVisible(true);
  await vi.advanceTimersByTimeAsync(0);
  expect(calls).toBe(1);
  expect(tracker.snapshot().tasks.a.snapshot.status).toBe("SUCCEEDED");
  tracker.dispose();
  vi.useRealTimers();
});

test("a stalled task query times out without changing task execution status", async () => {
  vi.useFakeTimers();
  const tracker = new TaskTracker({
    intervalMs: 2000,
    timeoutMs: 3000,
    listActive: async () => [],
    getTask: (_id, signal) =>
      new Promise((_resolve, reject) => {
        signal.addEventListener(
          "abort",
          () => reject(new DOMException("aborted", "AbortError")),
          { once: true },
        );
      }),
  });
  tracker.start();
  await vi.advanceTimersByTimeAsync(0);
  tracker.register(task("stalled"));
  await vi.advanceTimersByTimeAsync(5000);
  expect(tracker.snapshot().tasks.stalled.snapshot.status).toBe("RUNNING");
  expect(tracker.snapshot().tasks.stalled.queryError).toMatch(/超时/);
  await vi.advanceTimersByTimeAsync(10000);
  expect(vi.getTimerCount()).toBe(0);
  tracker.dispose();
  vi.useRealTimers();
});
