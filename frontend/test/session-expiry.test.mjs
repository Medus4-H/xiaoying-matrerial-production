import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import ts from "typescript";

async function loadExpiry() {
  const source = await readFile(
    new URL("../src/lib/session-expiry.ts", import.meta.url),
    "utf8",
  );
  const compiled = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
  }).outputText;
  const exports = {};
  new Function("exports", compiled)(exports);
  return exports;
}

test("session expiry transitions at its deadline and caps long timers", async () => {
  const { getExpiryState } = await loadExpiry();
  const now = Date.parse("2026-01-01T00:00:00Z");
  assert.deepEqual(getExpiryState("2026-01-01T00:00:10Z", now), {
    expired: false,
    delay: 10000,
  });
  assert.deepEqual(getExpiryState("2026-01-01T00:00:10Z", now + 10000), {
    expired: true,
    delay: null,
  });
  assert.deepEqual(getExpiryState("invalid", now), {
    expired: true,
    delay: null,
  });
  assert.equal(getExpiryState("2030-01-01T00:00:00Z", now).delay, 2147483647);
});

test("delayed identity response schedules against the actual clock", async () => {
  const { getExpiryRefreshDelay } = await loadExpiry();
  const mountedAt = Date.parse("2026-01-01T00:00:00Z");
  const expiresAt = "2026-01-01T00:00:19Z";
  assert.equal(
    getExpiryRefreshDelay(expiresAt, mountedAt, mountedAt + 9000),
    10000,
  );
  assert.equal(
    getExpiryRefreshDelay(expiresAt, mountedAt, mountedAt + 20000),
    0,
  );
  assert.equal(
    getExpiryRefreshDelay(expiresAt, mountedAt + 20000, mountedAt + 20000),
    null,
  );
});
