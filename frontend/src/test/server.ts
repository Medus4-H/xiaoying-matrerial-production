import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";

export const server = setupServer(
  http.get("*/api/tasks/active", () => HttpResponse.json({ items: [] })),
  http.get("*/api/tasks", () =>
    HttpResponse.json({ items: [], page: 1, pageSize: 20, total: 0 }),
  ),
  http.get("*/api/me", () =>
    HttpResponse.json({
      id: "test-user",
      displayName: "测试访客",
      expiresAt: "2099-01-01T00:00:00Z",
    }),
  ),
);
