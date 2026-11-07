import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import test from 'node:test';

const require = createRequire(import.meta.url);
const { loadDirectory } = require('../.test-build/src/panshi/services/catalogApi.js');
const session = require('../.test-build/src/services/api.js');
const platformSession = require('../.test-build/src/shared/platformSession.js');

test('directory editor loads governed detail and existing item contract in its session', async (context) => {
  context.mock.method(session, 'getSession', () => ({
    authenticated: true, principalId: 'validation-user', tenant: { tid: 'validation-tenant' },
  }));
  context.mock.method(platformSession, 'requirePlatformSessionToken', async () => 'validation-token');
  const originalWindow = globalThis.window;
  globalThis.window = { location: { hash: '#/resource/catalogs' } };
  context.after(() => { globalThis.window = originalWindow; });
  const requests = [];
  const detail = { tid: 'validation-catalog', catalogName: '验证目录', props: { shareType: 'DEPARTMENT' } };
  const items = [{ colName: '标识', colEn: 'id', colType: 'VARCHAR' }];
  context.mock.method(globalThis, 'fetch', async (path, options) => {
    requests.push({ path, method: options.method, body: JSON.parse(options.body) });
    return Response.json({ code: 200, data: path.endsWith('/resource-command') ? detail : items });
  });

  assert.deepEqual(await loadDirectory('validation-catalog'), { detail, items });
  assert.deepEqual(requests, [
    {
      path: '/dev-api/dwm/center/resource-command', method: 'POST',
      body: { kind: 'catalogs', method: 'GET', action: 'directory-detail', id: 'validation-catalog' },
    },
    {
      path: '/dev-api/dst/catalog/catalog-items/list', method: 'POST',
      body: { tid: 'validation-catalog' },
    },
  ]);
});
