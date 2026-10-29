import type { TaskSnapshot } from "@/lib/api/types";

export interface TrackedTask {
  snapshot: TaskSnapshot;
  queryError: string | null;
  lastUpdated: number;
  createdHere: boolean;
}
export interface TrackerState {
  tasks: Record<string, TrackedTask>;
  loading: boolean;
  loadError: string | null;
}
interface TrackerOptions {
  intervalMs: number;
  timeoutMs?: number;
  listActive: (signal: AbortSignal) => Promise<TaskSnapshot[]>;
  getTask: (id: string, signal: AbortSignal) => Promise<TaskSnapshot>;
  onChanged?: () => void;
}
class TaskQueryTimeout extends Error {
  constructor() {
    super("查询超时，请恢复查询");
  }
}

const unfinished = (task: TaskSnapshot) =>
  task.status === "QUEUED" || task.status === "RUNNING";
const message = (error: unknown) =>
  error instanceof Error ? error.message : "查询失败，请重试";

/** Owns task polling for exactly one identity. Views subscribe without starting timers. */
export class TaskTracker {
  private state: TrackerState = { tasks: {}, loading: true, loadError: null };
  private listeners = new Set<() => void>();
  private timers = new Map<string, ReturnType<typeof setTimeout>>();
  private requests = new Map<string, AbortController>();
  private activeRequest: AbortController | null = null;
  private disposed = true;
  private visible = true;

  constructor(private readonly options: TrackerOptions) {}

  snapshot = () => this.state;
  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };
  private update(state: TrackerState) {
    this.state = state;
    for (const listener of this.listeners) listener();
  }

  start() {
    if (!this.disposed) return;
    this.disposed = false;
    void this.reloadActive();
  }
  dispose() {
    this.disposed = true;
    this.activeRequest?.abort();
    this.activeRequest = null;
    for (const timer of this.timers.values()) clearTimeout(timer);
    this.timers.clear();
    for (const request of this.requests.values()) request.abort();
    this.requests.clear();
  }

  private async withTimeout<T>(
    controller: AbortController,
    run: () => Promise<T>,
  ): Promise<T> {
    let timedOut = false;
    const timer = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, this.options.timeoutMs ?? 10000);
    try {
      return await run();
    } catch (error) {
      if (timedOut) throw new TaskQueryTimeout();
      throw error;
    } finally {
      clearTimeout(timer);
    }
  }

  async reloadActive() {
    if (this.disposed) return;
    this.activeRequest?.abort();
    const controller = new AbortController();
    this.activeRequest = controller;
    this.update({ ...this.state, loading: true, loadError: null });
    try {
      const items = await this.withTimeout(controller, () =>
        this.options.listActive(controller.signal),
      );
      if (this.disposed || controller.signal.aborted) return;
      for (const item of items) this.register(item);
      this.update({ ...this.state, loading: false, loadError: null });
    } catch (error) {
      if (
        this.disposed ||
        (controller.signal.aborted && !(error instanceof TaskQueryTimeout))
      )
        return;
      this.update({ ...this.state, loading: false, loadError: message(error) });
    } finally {
      if (this.activeRequest === controller) this.activeRequest = null;
    }
  }

  register(task: TaskSnapshot, createdHere = false) {
    if (this.disposed) return;
    const previous = this.state.tasks[task.id];
    if (previous && !unfinished(previous.snapshot) && unfinished(task)) return;
    if (
      previous &&
      unfinished(previous.snapshot) &&
      unfinished(task) &&
      Date.parse(task.updatedAt) < Date.parse(previous.snapshot.updatedAt)
    ) {
      this.schedule(task.id, this.options.intervalMs);
      return;
    }
    const tracked: TrackedTask = {
      snapshot: task,
      queryError: previous?.queryError ?? null,
      lastUpdated: Date.now(),
      createdHere: createdHere || previous?.createdHere || false,
    };
    if (!unfinished(task)) {
      tracked.queryError = null;
      this.clearTimer(task.id);
      this.requests.get(task.id)?.abort();
      this.requests.delete(task.id);
    }
    this.update({
      ...this.state,
      tasks: { ...this.state.tasks, [task.id]: tracked },
    });
    if (unfinished(task) && !tracked.queryError)
      this.schedule(task.id, this.options.intervalMs);
    if (
      createdHere ||
      (previous && unfinished(previous.snapshot) && !unfinished(task))
    )
      this.options.onChanged?.();
  }

  private clearTimer(id: string) {
    const timer = this.timers.get(id);
    if (timer !== undefined) clearTimeout(timer);
    this.timers.delete(id);
  }
  private schedule(id: string, delay: number) {
    if (
      this.disposed ||
      !this.visible ||
      this.timers.has(id) ||
      this.requests.has(id)
    )
      return;
    const entry = this.state.tasks[id];
    if (!entry || !unfinished(entry.snapshot) || entry.queryError) return;
    this.timers.set(
      id,
      setTimeout(() => {
        this.timers.delete(id);
        void this.fetch(id);
      }, delay),
    );
  }

  private async fetch(id: string) {
    const existing = this.state.tasks[id];
    if (
      this.disposed ||
      !existing ||
      !unfinished(existing.snapshot) ||
      this.requests.has(id)
    )
      return;
    this.clearTimer(id);
    const controller = new AbortController();
    this.requests.set(id, controller);
    try {
      const task = await this.withTimeout(controller, () =>
        this.options.getTask(id, controller.signal),
      );
      if (this.disposed || controller.signal.aborted) return;
      this.requests.delete(id);
      const prior = this.state.tasks[id];
      if (!prior || !unfinished(prior.snapshot)) return;
      this.update({
        ...this.state,
        tasks: { ...this.state.tasks, [id]: { ...prior, queryError: null } },
      });
      this.register(task);
    } catch (error) {
      if (
        this.disposed ||
        (controller.signal.aborted && !(error instanceof TaskQueryTimeout))
      )
        return;
      const prior = this.state.tasks[id];
      if (prior && unfinished(prior.snapshot))
        this.update({
          ...this.state,
          tasks: {
            ...this.state.tasks,
            [id]: { ...prior, queryError: message(error) },
          },
        });
    } finally {
      if (this.requests.get(id) === controller) this.requests.delete(id);
    }
  }

  async resume(id: string) {
    if (this.disposed || this.requests.has(id)) return;
    const entry = this.state.tasks[id];
    if (!entry || !unfinished(entry.snapshot)) return;
    this.update({
      ...this.state,
      tasks: { ...this.state.tasks, [id]: { ...entry, queryError: null } },
    });
    await this.fetch(id);
  }

  setVisible(visible: boolean) {
    if (this.disposed || this.visible === visible) return;
    this.visible = visible;
    if (!visible) {
      for (const id of this.timers.keys()) this.clearTimer(id);
      return;
    }
    for (const id of Object.keys(this.state.tasks)) this.schedule(id, 0);
  }
}
