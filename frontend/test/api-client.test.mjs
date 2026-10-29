import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import ts from "typescript";

async function loadClient(fetch) {
  const source = await readFile(
    new URL("../src/lib/api/client.ts", import.meta.url),
    "utf8",
  );
  const compiled = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
  }).outputText;
  const exports = {};
  const require = (name) => {
    if (name === "@/config/app") return { appConfig: { errorLocale: "zh-CN" } };
    if (name === "@/lib/api/error-codes")
      return {
        ErrorCodes: {
          IDENTITY_CHANGED: "IDENTITY_CHANGED",
          REQUEST_FAILED: "REQUEST_FAILED",
        },
      };
    if (name === "@/lib/api/error-i18n")
      return { resolveErrorMessage: async (_key, fallback) => fallback };
    throw new Error(`Unexpected import: ${name}`);
  };
  new Function("exports", "fetch", "require", compiled)(
    exports,
    fetch,
    require,
  );
  return exports;
}

test("successful API request keeps same-origin credentials and disables caching", async () => {
  let received;
  const { request } = await loadClient(async (path, options) => {
    received = { path, options };
    return { ok: true, json: async () => ({ id: "demo-1" }) };
  });

  assert.deepEqual(await request("/api/templates/demo-1"), { id: "demo-1" });
  assert.equal(received.path, "/api/templates/demo-1");
  assert.equal(received.options.credentials, "same-origin");
  assert.equal(received.options.cache, "no-store");
});

test("API failures preserve status and structured error code", async () => {
  const { request } = await loadClient(async () => ({
    ok: false,
    status: 404,
    json: async () => ({ code: "TEMPLATE_NOT_FOUND", message: "模板不存在" }),
  }));

  await assert.rejects(request("/api/templates/missing"), (error) => {
    assert.equal(error.status, 404);
    assert.equal(error.code, "TEMPLATE_NOT_FOUND");
    assert.equal(error.message, "模板不存在");
    return true;
  });
});
