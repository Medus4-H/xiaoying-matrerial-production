import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const catalog = JSON.parse(await readFile("/project/backend/src/main/resources/error-catalog.json", "utf8"));
const backup = JSON.parse(await readFile("/project/backend/src/main/resources/error-catalog-fallback.json", "utf8"));
assert.deepEqual(backup, catalog, "Packaged fallback must match the canonical catalog");
const frontend = await readFile("/project/frontend/src/lib/api/error-codes.ts", "utf8");
const codes = new Set(Object.values(catalog.entries).map((item) => item.code));
const entries = [...frontend.matchAll(/^\s+([A-Z_]+):\s*"([A-Z_]+)"/gm)];
assert.ok(entries.length > 0, "Frontend control-flow codes must be declared");
for (const [, name, code] of entries) {
  assert.equal(name, code, `Mismatched client code ${name}`);
  assert.ok(codes.has(code), `Client code ${code} is absent from the backend catalog`);
}
console.log(`Checked ${entries.length} frontend codes against the backend error catalog`);
