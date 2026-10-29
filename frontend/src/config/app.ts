function boundedNumber(
  input: string | undefined,
  fallback: number,
  min: number,
  max: number,
): number {
  const value = Number(input);
  return Number.isFinite(value) &&
    Number.isInteger(value) &&
    value >= min &&
    value <= max
    ? value
    : fallback;
}

// 协议路径集中定义；浏览器始终使用相对地址，不依赖宿主机或容器地址。
export const appConfig = {
  name: "小影 · 素材制作",
  identityChangeKey: "xiaoying:identity-change",
  errorLocale: process.env.NEXT_PUBLIC_ERROR_LOCALE === "en" ? "en" : "zh-CN",
  requestFailureMessage:
    process.env.NEXT_PUBLIC_ERROR_LOCALE === "en"
      ? "Request failed. Please try again later."
      : "请求失败，请稍后重试",
  errorBundleCacheMs: 300000,
  errorBundleTimeoutMs: 3000,
  taskPollIntervalMs: boundedNumber(
    process.env.NEXT_PUBLIC_TASK_POLL_INTERVAL_MS,
    2000,
    250,
    600000,
  ),
  taskQueryTimeoutMs: boundedNumber(
    process.env.NEXT_PUBLIC_TASK_QUERY_TIMEOUT_MS,
    10000,
    1000,
    600000,
  ),
  taskHistoryPageSize: boundedNumber(
    process.env.NEXT_PUBLIC_TASK_HISTORY_PAGE_SIZE,
    20,
    1,
    50,
  ),
  showSimulationControls:
    process.env.NEXT_PUBLIC_SHOW_SIMULATION_CONTROLS === "true",
  api: {
    errorBundle: "/api/i18n/errors",
    me: "/api/me",
    session: "/api/session",
    register: "/api/auth/register",
    bind: "/api/auth/bind",
    login: "/api/auth/login",
    logout: "/api/auth/logout",
    demoCode: "/api/auth/demo-code",
    categories: "/api/categories",
    templates: "/api/templates",
    assets: "/api/assets",
    tasks: "/api/tasks",
  },
} as const;
