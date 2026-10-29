import { beforeEach, expect, test } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "./server";
import { ApiError, request } from "@/lib/api/client";
import {
  clearErrorBundleCache,
  resolveErrorMessage,
} from "@/lib/api/error-i18n";

beforeEach(() => clearErrorBundleCache());

test("resolves a translated error while preserving its code and request ID", async () => {
  let bundleRequests = 0;
  server.use(
    http.get("*/api/i18n/errors", () => {
      bundleRequests++;
      return HttpResponse.json({
        version: 1,
        locale: "zh-CN",
        items: { "auth.login_failed": "翻译后的登录错误" },
      });
    }),
    http.get("*/api/example", () =>
      HttpResponse.json(
        {
          code: "LOGIN_FAILED",
          messageKey: "auth.login_failed",
          message: "服务端提示",
          requestId: "req-1",
        },
        { status: 401 },
      ),
    ),
  );
  for (let i = 0; i < 2; i++) {
    try {
      await request("/api/example");
      throw new Error("expected request failure");
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({
        code: "LOGIN_FAILED",
        message: "翻译后的登录错误",
        requestId: "req-1",
      });
    }
  }
  expect(bundleRequests).toBe(1);
});

test("falls back to safe server message if translation is unavailable or missing", async () => {
  server.use(
    http.get(
      "*/api/i18n/errors",
      () => new HttpResponse(null, { status: 503 }),
    ),
  );
  await expect(
    resolveErrorMessage("missing.key", "安全服务端提示", "en"),
  ).resolves.toBe("安全服务端提示");
  server.use(
    http.get("*/api/i18n/errors", () =>
      HttpResponse.json({ version: 1, locale: "en", items: {} }),
    ),
  );
  await expect(
    resolveErrorMessage("missing.key", "安全服务端提示", "en"),
  ).resolves.toBe("安全服务端提示");
});

test("unexpected server error includes its request ID for support", async () => {
  server.use(
    http.get("*/api/example", () =>
      HttpResponse.json(
        {
          code: "INTERNAL_ERROR",
          message: "服务暂时不可用",
          requestId: "req-support-1",
        },
        { status: 500 },
      ),
    ),
  );
  await expect(request("/api/example")).rejects.toMatchObject({
    requestId: "req-support-1",
    message: "服务暂时不可用（请求编号：req-support-1）",
  });
});
