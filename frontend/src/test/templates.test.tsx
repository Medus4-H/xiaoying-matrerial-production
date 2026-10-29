import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { expect, test, vi } from "vitest";
import { AppProviders } from "@/components/providers/AppProviders";
import { TemplateCatalog } from "@/components/templates/TemplateCatalog";
import { TemplateDetails } from "@/components/templates/TemplateDetails";
import { server } from "./server";

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: React.ComponentProps<"a">) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));
const item = {
  id: "demo-1",
  title: "旅行",
  categoryId: "travel",
  categoryName: "旅行",
  description: "旅行模板",
  coverUrl: "/api/templates/demo-1/cover",
  requiredImageCount: 1,
  acceptedMimeTypes: ["image/jpeg"],
  maxFileSizeBytes: 5242880,
};

test("category filtering requests matching data and exposes empty state", async () => {
  server.use(
    http.get("*/api/categories", () =>
      HttpResponse.json({
        items: [
          { id: "travel", name: "旅行" },
          { id: "pets", name: "宠物" },
        ],
      }),
    ),
    http.get("*/api/templates", ({ request }) =>
      HttpResponse.json({
        items:
          new URL(request.url).searchParams.get("categoryId") === "pets"
            ? []
            : [item],
      }),
    ),
  );
  const user = userEvent.setup();
  render(
    <AppProviders>
      <TemplateCatalog />
    </AppProviders>,
  );
  expect(await screen.findByRole("link", { name: /旅行/ })).toHaveProperty(
    "href",
    "http://localhost:3000/templates/demo-1",
  );
  expect(screen.getByText("旅行 · 需上传 1 张图片")).toBeTruthy();
  await user.click(await screen.findByRole("button", { name: "宠物" }));
  expect(await screen.findByText("当前分类暂无模板。")).toBeTruthy();
  expect(
    screen.getByRole("button", { name: "宠物" }).getAttribute("aria-pressed"),
  ).toBe("true");
});

test("template loading, request failure and missing detail are distinct", async () => {
  server.use(
    http.get("*/api/categories", () => HttpResponse.json({ items: [] })),
    http.get("*/api/templates", async () => {
      await new Promise((resolve) => setTimeout(resolve, 40));
      return HttpResponse.json({ message: "服务暂不可用" }, { status: 503 });
    }),
    http.get("*/api/templates/missing", () =>
      HttpResponse.json(
        { code: "TEMPLATE_NOT_FOUND", message: "不存在" },
        { status: 404 },
      ),
    ),
  );
  const { unmount } = render(
    <AppProviders>
      <TemplateCatalog />
    </AppProviders>,
  );
  expect(screen.getByRole("status").textContent).toContain("加载模板");
  expect((await screen.findByRole("alert")).textContent).toContain(
    "服务暂不可用",
  );
  unmount();
  render(
    <AppProviders>
      <TemplateDetails id="missing" />
    </AppProviders>,
  );
  expect((await screen.findByRole("alert")).textContent).toContain(
    "模板不存在",
  );
});
