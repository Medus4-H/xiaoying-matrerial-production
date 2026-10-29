"use client";

import { FormEvent, useEffect, useRef, useState } from "react";

type Event = Record<string, string | number | boolean>;
type State = "checking" | "disabled" | "login" | "ready";

export default function DiagnosticPage() {
  const [state, setState] = useState<State>("checking");
  const [secret, setSecret] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [events, setEvents] = useState<Event[]>([]);
  const [metrics, setMetrics] = useState<Event>({});
  const [requestId, setRequestId] = useState("");
  const [taskId, setTaskId] = useState("");
  const [level, setLevel] = useState("");
  const [since, setSince] = useState("");
  const controller = useRef<AbortController | null>(null);

  useEffect(() => {
    const abort = new AbortController();
    controller.current = abort;
    void fetch("/api/diagnostics/session", {
      cache: "no-store",
      signal: abort.signal,
    })
      .then((response) => {
        if (abort.signal.aborted) return;
        if (response.status === 404) setState("disabled");
        else if (response.status === 401) setState("login");
        else if (response.ok) setState("ready");
        else {
          setMessage("诊断服务暂不可用，请稍后重试。");
          setState("disabled");
        }
      })
      .catch(() => {
        if (!abort.signal.aborted) {
          setMessage("诊断服务暂不可用，请稍后重试。");
          setState("disabled");
        }
      });
    return () => abort.abort();
  }, []);

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setMessage("");
    try {
      const response = await fetch("/api/diagnostics/session", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ secret }),
        cache: "no-store",
        signal: controller.current?.signal,
      });
      if (response.ok) setState("ready");
      else
        setMessage(
          response.status === 429
            ? "尝试次数过多，请稍后再试。"
            : "无法建立诊断会话，请检查凭据和来源。 ",
        );
    } catch {
      if (!controller.current?.signal.aborted) setMessage("诊断服务暂不可用。");
    } finally {
      setSecret("");
      setBusy(false);
    }
  }

  async function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setMessage("");
    const query = new URLSearchParams();
    if (requestId.trim()) query.set("requestId", requestId.trim());
    if (taskId.trim()) query.set("taskId", taskId.trim());
    if (level) query.set("level", level);
    if (since) query.set("since", new Date(since).toISOString());
    try {
      const [logsResponse, metricsResponse] = await Promise.all([
        fetch(`/api/diagnostics/events?${query}`, {
          cache: "no-store",
          signal: controller.current?.signal,
        }),
        fetch("/api/diagnostics/metrics", {
          cache: "no-store",
          signal: controller.current?.signal,
        }),
      ]);
      if (logsResponse.status === 401 || metricsResponse.status === 401) {
        setEvents([]);
        setMetrics({});
        setState("login");
        setMessage("诊断会话已过期，请重新登录。");
        return;
      }
      if (!logsResponse.ok || !metricsResponse.ok)
        throw new Error("unavailable");
      const data: { events: Event[] } = await logsResponse.json();
      setEvents(data.events);
      setMetrics((await metricsResponse.json()) as Event);
    } catch {
      if (!controller.current?.signal.aborted)
        setMessage("查询失败，请检查筛选条件后重试。");
    } finally {
      setBusy(false);
    }
  }

  async function logout() {
    setBusy(true);
    try {
      const response = await fetch("/api/diagnostics/logout", {
        method: "POST",
        cache: "no-store",
        signal: controller.current?.signal,
      });
      if (!response.ok) throw new Error("logout failed");
      setEvents([]);
      setMetrics({});
      setState("login");
      setMessage("");
    } catch {
      if (!controller.current?.signal.aborted) setMessage("退出失败，请重试。");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="diagnostic-page panel">
      <h1>开发诊断</h1>
      <p>
        仅本实例的内存记录，重启后丢失；不是完整审计日志。请勿用于生产环境。
      </p>
      {message && <p role="alert">{message}</p>}
      {state === "checking" && <p>正在检查诊断服务…</p>}
      {state === "disabled" && <p>诊断功能未启用或暂不可用。</p>}
      {state === "login" && (
        <form onSubmit={login} className="diagnostic-form">
          <label>
            诊断凭据
            <input
              type="password"
              autoComplete="off"
              value={secret}
              onChange={(event) => setSecret(event.target.value)}
              required
            />
          </label>
          <button disabled={busy} type="submit">
            进入诊断
          </button>
        </form>
      )}
      {state === "ready" && (
        <>
          <form onSubmit={search} className="diagnostic-form">
            <label>
              请求编号
              <input
                value={requestId}
                onChange={(event) => setRequestId(event.target.value)}
              />
            </label>
            <label>
              任务编号
              <input
                value={taskId}
                onChange={(event) => setTaskId(event.target.value)}
              />
            </label>
            <label>
              级别
              <select
                value={level}
                onChange={(event) => setLevel(event.target.value)}
              >
                <option value="">全部</option>
                <option>INFO</option>
                <option>WARN</option>
                <option>ERROR</option>
                <option>DEBUG</option>
              </select>
            </label>
            <label>
              起始时间
              <input
                type="datetime-local"
                value={since}
                onChange={(event) => setSince(event.target.value)}
              />
            </label>
            <button disabled={busy} type="submit">
              查询
            </button>
            <button disabled={busy} type="button" onClick={() => void logout()}>
              退出诊断
            </button>
          </form>
          <h2>当前运行快照</h2>
          <p>内存与 GC 指标是整个 JVM 的采样，不代表某个任务的资源消耗。</p>
          <pre>
            {Object.keys(metrics).length
              ? JSON.stringify(metrics, null, 2)
              : "查询后显示"}
          </pre>
          <h2>事件（{events.length}）</h2>
          {events.length ? (
            <ol>
              {events.map((item, index) => (
                <li key={`${item.timestamp}-${index}`}>
                  <pre>{JSON.stringify(item, null, 2)}</pre>
                </li>
              ))}
            </ol>
          ) : (
            <p>暂无匹配事件，点击查询刷新。</p>
          )}
        </>
      )}
    </main>
  );
}
