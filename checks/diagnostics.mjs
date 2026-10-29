import assert from "node:assert/strict";

const base = process.env.BASE_URL;
const origin = process.env.PUBLIC_ORIGIN;
const secret = process.env.DIAGNOSTICS_SECRET;
assert.ok(base && origin && secret);
const get = (path, options) => fetch(new URL(path, base), options);
const login = (password, source = origin) => get("/api/diagnostics/session", {
  method: "POST",
  headers: { Origin: source, "Content-Type": "application/json" },
  body: JSON.stringify({ secret: password }),
});
assert.equal((await get("/api/diagnostics/events")).status, 401);
assert.equal((await login(secret, "https://untrusted.invalid")).status, 403);
assert.equal((await login("incorrect")).status, 401);
const response = await login(secret);
assert.equal(response.status, 200);
const header = response.headers.get("set-cookie");
assert.match(header, /HttpOnly/i);
assert.match(header, /SameSite=Strict/i);
assert.match(header, /Path=\/api\/diagnostics/i);
const cookie = header.split(";")[0];
const visitor = await get("/api/session", { method: "POST", headers: { Origin: origin } });
assert.equal(visitor.status, 201);
const requestId = visitor.headers.get("x-request-id");
assert.ok(requestId);
const filters = new URLSearchParams({ requestId, level: "INFO", since: "2000-01-01T00:00:00Z" });
const filtered = await get(`/api/diagnostics/events?${filters}`, { headers: { Cookie: cookie } });
assert.equal(filtered.status, 200);
const filteredEvents = (await filtered.json()).events;
assert.ok(filteredEvents.some((entry) => entry.requestId === requestId && entry.event === "http.request"));
assert.ok(filteredEvents.every((entry) => entry.requestId === requestId && entry.level === "INFO"));
const deniedLogout = await get("/api/diagnostics/logout", { method: "POST", headers: { Cookie: cookie, Origin: "https://untrusted.invalid" } });
assert.equal(deniedLogout.status, 403);
const invalid = await get("/api/diagnostics/events?taskId=bad", { headers: { Cookie: cookie } });
assert.equal(invalid.status, 400);
const result = await get("/api/diagnostics/events", { headers: { Cookie: cookie } });
assert.equal(result.status, 200);
assert.match(result.headers.get("cache-control"), /no-store/);
const body = await result.text();
assert.ok(!body.includes(secret));
assert.ok(!body.includes("untrusted.invalid"));
const metrics = await get("/api/diagnostics/metrics", { headers: { Cookie: cookie } });
assert.equal(metrics.status, 200);
assert.equal(typeof (await metrics.json()).queueDepth, "number");
assert.equal((await get("/api/diagnostics/logout", { method: "POST", headers: { Cookie: cookie, Origin: origin } })).status, 200);
assert.equal((await get("/api/diagnostics/events", { headers: { Cookie: cookie } })).status, 401);
for (let attempt = 0; attempt < 10; attempt++) assert.equal((await login("incorrect")).status, 401);
assert.equal((await login(secret)).status, 429);
console.log("PASS: diagnostic authorization, origin, filtering, no-store, metrics and logout through Next.js proxy");
