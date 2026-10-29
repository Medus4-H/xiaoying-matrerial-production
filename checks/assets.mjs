import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const base = process.env.BASE_URL;
const origin = process.env.PUBLIC_ORIGIN;
const maxBytes = Number(process.env.ASSET_MAX_BYTES);
assert.ok(base && origin, "BASE_URL and PUBLIC_ORIGIN are required");
assert.ok(
  Number.isSafeInteger(maxBytes) && maxBytes > 0,
  "ASSET_MAX_BYTES must be positive",
);
const url = (path) => new URL(path, base);
async function visitor() {
  const response = await fetch(url("/api/session"), {
    method: "POST",
    headers: { Origin: origin },
  });
  assert.equal(response.status, 201);
  return response.headers.get("set-cookie").split(";")[0];
}
const a = await visitor();
const b = await visitor();
const bytes = await readFile("/fixtures/example.jpg");
const form = new FormData();
form.append("file", new Blob([bytes], { type: "image/jpeg" }), "example.jpg");
const created = await fetch(url("/api/assets"), {
  method: "POST",
  headers: { Cookie: a, Origin: origin },
  body: form,
});
assert.equal(created.status, 201, await created.clone().text());
const asset = await created.json();
assert.equal(asset.mimeType, "image/jpeg");
assert.equal(asset.sizeBytes, bytes.length);
assert.ok(asset.width > 0 && asset.height > 0);
assert.ok(asset.previewUrl.startsWith("/api/assets/"));
assert.ok(!JSON.stringify(asset).includes("/Users/"));
const own = await fetch(url(asset.previewUrl), { headers: { Cookie: a } });
assert.equal(own.status, 200);
assert.deepEqual(Buffer.from(await own.arrayBuffer()), bytes);
assert.match(own.headers.get("cache-control"), /no-store/);
assert.equal(own.headers.get("x-content-type-options"), "nosniff");
assert.equal(
  (await fetch(url(asset.previewUrl), { headers: { Cookie: b } })).status,
  404,
);
assert.equal((await fetch(url(asset.previewUrl))).status, 401);
const bad = new FormData();
bad.append(
  "file",
  new Blob([new Uint8Array([1, 2, 3])], { type: "image/png" }),
  "fake.png",
);
assert.equal(
  (
    await fetch(url("/api/assets"), {
      method: "POST",
      headers: { Cookie: a, Origin: origin },
      body: bad,
    })
  ).status,
  400,
);
const tooLarge = new FormData();
tooLarge.append(
  "file",
  new Blob([new Uint8Array(maxBytes + 1)], { type: "image/png" }),
  "large.png",
);
const oversized = await fetch(url("/api/assets"), {
  method: "POST",
  headers: { Cookie: a, Origin: origin },
  body: tooLarge,
});
assert.equal(oversized.status, 413);
assert.match((await oversized.json()).message, /超过|过大/);
const denied = new FormData();
denied.append("file", new Blob([bytes], { type: "image/jpeg" }), "example.jpg");
assert.equal(
  (
    await fetch(url("/api/assets"), {
      method: "POST",
      headers: { Cookie: a, Origin: "https://untrusted.invalid" },
      body: denied,
    })
  ).status,
  403,
);
console.log(
  "PASS: real multipart upload, persisted bytes, owner isolation and request validation",
);
