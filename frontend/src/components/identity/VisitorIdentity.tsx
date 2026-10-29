"use client";

import { FormEvent, useEffect, useRef, useState } from "react";
import { appConfig } from "@/config/app";
import { request } from "@/lib/api/client";
import { useIdentity } from "./IdentityProvider";
import { AccountOverview } from "./AccountOverview";

type Mode = "details" | "register" | "login" | "bind";
type Channel = "email" | "phone";

function IdentityDialog({
  onDismiss,
  initialMode,
}: {
  onDismiss: () => void;
  initialMode: Mode;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const closeButton = useRef<HTMLButtonElement>(null);
  const identity = useIdentity();
  const [mode, setMode] = useState<Mode>(initialMode);
  const [channel, setChannel] = useState<Channel>("email");
  const [destination, setDestination] = useState("");
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [importGuest, setImportGuest] = useState(false);
  const [code, setCode] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const element = dialog.current;
    if (!element) return;
    if (typeof element.showModal === "function") element.showModal();
    else element.setAttribute("open", "");
    closeButton.current?.focus();
    return () => {
      if (element.open && typeof element.close === "function") element.close();
    };
  }, []);

  async function withFeedback(action: () => Promise<void>) {
    setBusy(true);
    setMessage("");
    try {
      await action();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "请求失败，请重试");
    } finally {
      setBusy(false);
    }
  }

  function selectChannel(value: Channel) {
    setChannel(value);
    setDestination("");
    setCode("");
    setMessage("");
  }

  async function showCode() {
    await withFeedback(async () => {
      const result = await request<{ demoCode: string }>(
        appConfig.api.demoCode,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ channel, destination }),
        },
      );
      setMessage(`演示验证码：${result.demoCode}（未实际发送）`);
    });
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    void withFeedback(async () => {
      if (mode === "register" || mode === "bind") {
        const input = { channel, destination, code };
        if (mode === "register")
          await identity.register({ ...input, password, importGuest });
        else await identity.bind(input);
        setMode("details");
        setPassword("");
        setCode("");
        setDestination("");
      } else {
        await identity.login(identifier, password);
        onDismiss();
      }
    });
  }

  function startBinding(value: Channel) {
    selectChannel(value);
    setPassword("");
    setMode("bind");
  }

  const title =
    mode === "details"
      ? identity.user?.type === "ACCOUNT"
        ? "账号信息"
        : "访客身份"
      : mode === "register"
        ? "注册账号"
        : mode === "bind"
          ? channel === "email"
            ? "绑定邮箱"
            : "绑定手机号"
          : "登录账号";
  return (
    <dialog
      ref={dialog}
      className="identity-dialog"
      aria-labelledby="identity-dialog-title"
      onKeyDown={(event) => {
        if (event.key === "Escape") {
          event.preventDefault();
          if (!busy) onDismiss();
        }
      }}
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onDismiss();
      }}
    >
      <div className="dialog-heading">
        <h2 id="identity-dialog-title">{title}</h2>
        <button
          ref={closeButton}
          type="button"
          aria-label="关闭访客身份"
          disabled={busy}
          onClick={onDismiss}
        >
          ×
        </button>
      </div>
      {mode === "details" ? (
        <div className="identity-details">
          {identity.user?.type === "ACCOUNT" ? (
            <>
              <div className="account-profile-heading">
                <p className="identity-name">个人账号</p>
                <span className="account-status">已登录</span>
              </div>
              <div className="account-contact">
                <p>邮箱：{identity.user.email || "未绑定"}</p>
                {!identity.user.email && (
                  <button type="button" onClick={() => startBinding("email")}>
                    绑定邮箱
                  </button>
                )}
              </div>
              <div className="account-contact">
                <p>手机号：{identity.user.phone || "未绑定"}</p>
                {!identity.user.phone && (
                  <button type="button" onClick={() => startBinding("phone")}>
                    绑定手机号
                  </button>
                )}
              </div>
              <AccountOverview
                userId={identity.user.id}
                onNavigate={onDismiss}
              />
            </>
          ) : (
            <>
              <p className="identity-name">{identity.user?.displayName}</p>
              <p>
                身份保存在当前浏览器中。会话过期或清除浏览器数据后，未注册访客的素材和记录无法找回。注册时可选择保存属于自己的访客资料。
              </p>
            </>
          )}
          <div className="identity-actions">
            {identity.user?.type !== "ACCOUNT" && (
              <button
                type="button"
                onClick={() => {
                  setMessage("");
                  setMode("register");
                }}
              >
                注册账号
              </button>
            )}
            <button
              type="button"
              onClick={() => {
                setMessage("");
                setMode("login");
              }}
            >
              切换 / 登录账号
            </button>
            {identity.user?.type === "ACCOUNT" && (
              <button
                type="button"
                disabled={busy}
                onClick={() =>
                  void withFeedback(async () => {
                    await identity.logout();
                    onDismiss();
                  })
                }
              >
                退出账号
              </button>
            )}
          </div>
        </div>
      ) : (
        <form onSubmit={submit} className="account-form">
          {mode === "register" && (
            <>
              <p>选择一种方式注册。默认创建独立账号，不继承当前访客记录。</p>
              <label className="guest-import-choice">
                <input
                  type="checkbox"
                  checked={importGuest}
                  onChange={(event) => setImportGuest(event.target.checked)}
                />
                将当前访客的素材和历史记录保存到新账号（仅当这些资料属于我时勾选）
              </label>
              <div className="identity-actions" aria-label="选择注册方式">
                <button
                  type="button"
                  aria-pressed={channel === "email"}
                  onClick={() => selectChannel("email")}
                >
                  邮箱注册
                </button>
                <button
                  type="button"
                  aria-pressed={channel === "phone"}
                  onClick={() => selectChannel("phone")}
                >
                  手机号注册
                </button>
              </div>
            </>
          )}
          {mode === "register" || mode === "bind" ? (
            <>
              <p>验证码仅供演示，不会发送邮件或短信。</p>
              <label>
                {channel === "email" ? "邮箱" : "手机号"}
                <input
                  type={channel === "email" ? "email" : "tel"}
                  value={destination}
                  onChange={(e) => {
                    setDestination(e.target.value);
                    setCode("");
                    setMessage("");
                  }}
                  required
                  autoComplete={channel === "email" ? "email" : "tel"}
                />
              </label>
              <div className="code-field">
                <label>
                  验证码
                  <input
                    value={code}
                    onChange={(e) => setCode(e.target.value)}
                    required
                    inputMode="numeric"
                  />
                </label>
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => void showCode()}
                >
                  获取验证码
                </button>
              </div>
            </>
          ) : (
            <>
              <p>
                使用已注册的邮箱或手机号登录。访客资料留在当前浏览器，退出账号后可恢复。
              </p>
              <label>
                邮箱或手机号
                <input
                  value={identifier}
                  onChange={(e) => setIdentifier(e.target.value)}
                  required
                  autoComplete="username"
                />
              </label>
            </>
          )}
          {mode !== "bind" && (
            <label>
              密码
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={8}
                maxLength={128}
                autoComplete={
                  mode === "register" ? "new-password" : "current-password"
                }
              />
            </label>
          )}
          {mode === "bind" && <p>该联系方式未被其他账号使用时才能绑定。</p>}
          <div className="identity-actions">
            <button type="submit" disabled={busy}>
              {busy
                ? "处理中…"
                : mode === "register"
                  ? "完成注册"
                  : mode === "bind"
                    ? "完成绑定"
                    : "登录"}
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={() => {
                setMessage("");
                setMode("details");
              }}
            >
              返回
            </button>
          </div>
        </form>
      )}
      {message && (
        <p role="status" className="account-message">
          {message}
        </p>
      )}
    </dialog>
  );
}

export function VisitorIdentity() {
  const identity = useIdentity();
  const [mode, setMode] = useState<Mode | null>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const dismiss = () => {
    setMode(null);
    window.setTimeout(() => trigger.current?.focus(), 0);
  };

  if (identity.checking) return <span role="status">正在确认账号…</span>;
  if (identity.status === "loading")
    return <span role="status">建立身份…</span>;
  if (identity.status === "error")
    return <button onClick={identity.retry}>身份加载失败，重试</button>;
  if (identity.status === "expired")
    return (
      <>
        <div role="alert" className="identity-expired">
          <span>
            会话已过期。已注册用户可登录恢复资料；未注册访客的旧资料无法找回。
          </span>
          <button type="button" onClick={() => setMode("login")}>
            登录账号
          </button>
          <button disabled={identity.renewing} onClick={identity.renew}>
            {identity.renewing ? "正在建立…" : "建立新访客身份"}
          </button>
          {identity.renewalError && <span>建立身份失败，请重试。</span>}
        </div>
        {mode && <IdentityDialog initialMode={mode} onDismiss={dismiss} />}
      </>
    );
  return (
    <>
      <button
        ref={trigger}
        className="identity-trigger"
        type="button"
        onClick={() => setMode("details")}
        aria-haspopup="dialog"
      >
        {identity.user?.displayName}
      </button>
      {mode && <IdentityDialog initialMode={mode} onDismiss={dismiss} />}
    </>
  );
}
