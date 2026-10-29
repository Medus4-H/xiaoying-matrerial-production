import assert from 'node:assert/strict';
const base = process.env.BASE_URL;
const origin = process.env.PUBLIC_ORIGIN;
const cookieName = process.env.SESSION_COOKIE_NAME;
assert.ok(base && origin && cookieName, 'BASE_URL, PUBLIC_ORIGIN, SESSION_COOKIE_NAME are required');
const get = (path, options = {}) => fetch(new URL(path, base), options);
const anonymous = await get('/api/me');
assert.equal(anonymous.status, 401);
const failure = await anonymous.json();
assert.equal(failure.code, 'SESSION_REQUIRED');
assert.ok(failure.requestId);
assert.ok(!JSON.stringify(failure).includes('/Users/'));
const create = async () => {
  const response = await get('/api/session', { method: 'POST', headers: { Origin: origin } });
  assert.equal(response.status, 201);
  const header = response.headers.get('set-cookie');
  assert.ok(header.startsWith(cookieName + '='));
  assert.match(header, /HttpOnly/i);
  assert.match(header, /SameSite=Lax/i);
  const user = await response.json();
  assert.ok(!('sessionTokenHash' in user));
  assert.ok(!('token' in user));
  return { user, cookie: header.split(';')[0] };
};
const a = await create(), b = await create();
assert.notEqual(a.user.id, b.user.id);
for (const visitor of [a, b]) {
  const response = await get('/api/me', { headers: { Cookie: visitor.cookie } });
  assert.equal(response.status, 200);
  assert.match(response.headers.get('cache-control'), /no-store/);
  assert.equal((await response.json()).id, visitor.user.id);
}
const reused = await get('/api/session', { method: 'POST', headers: { Cookie: a.cookie, Origin: origin } });
assert.equal(reused.status, 200);
assert.equal((await reused.json()).id, a.user.id);
const forbidden = await get('/api/session', { method: 'POST', headers: { Origin: 'https://untrusted.invalid' } });
assert.equal(forbidden.status, 403);
assert.equal((await forbidden.json()).code, 'ORIGIN_DENIED');
const categories = await (await get('/api/categories')).json();
assert.equal(categories.items.length, 3);
const all = await (await get('/api/templates')).json();
assert.equal(all.items.length, 6);
assert.deepEqual([...new Set(all.items.map(item => item.requiredImageCount))].sort(), [1, 2, 3]);
for (const category of categories.items) {
  const filtered = await (await get('/api/templates?categoryId=' + category.id)).json();
  assert.equal(filtered.items.length, 2);
  assert.ok(filtered.items.every(item => item.categoryId === category.id));
}
assert.equal((await (await get('/api/templates?categoryId=missing')).json()).items.length, 0);
for (const template of all.items) {
  const detail = await get('/api/templates/' + template.id);
  assert.equal(detail.status, 200);
  const body = await detail.json();
  assert.deepEqual(body.acceptedMimeTypes, ['image/jpeg', 'image/png']);
  assert.ok(body.coverUrl.startsWith('/api/'));
  const cover = await get(body.coverUrl);
  assert.equal(cover.status, 200);
  assert.match(cover.headers.get('content-type'), /image\/jpeg/);
  const bytes = new Uint8Array(await cover.arrayBuffer());
  assert.ok(bytes.length > 1024 && bytes[0] === 0xff && bytes[1] === 0xd8, 'cover is a JPEG');
}
const missing = await get('/api/templates/missing');
assert.equal(missing.status, 404);
assert.equal((await missing.json()).code, 'TEMPLATE_NOT_FOUND');
assert.equal((await get('/api/not-found')).status, 404);
assert.equal((await get('/api/session', { method: 'PUT' })).status, 405);
console.log('PASS: visitor creation/reuse/isolation, origin validation, categories, filters, six covers, requirements, empty list and 404');
