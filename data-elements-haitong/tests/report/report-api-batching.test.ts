import test, { type TestContext } from 'node:test';
import assert from 'node:assert/strict';
import { loadReportContext, reportDatasourceIdFromLocation } from '../../src/report/services/reportApi';

function contextFixture(t: TestContext, sourceCount: number, options: { foreignRow?: boolean; missingTenant?: boolean } = {}) {
  const descriptors = ['window', 'localStorage', 'sessionStorage'].map(key => [key, Object.getOwnPropertyDescriptor(globalThis, key)] as const);
  const previousFetch = globalThis.fetch;
  const stored = new Map([['token', 'test-platform-token']]);
  const storage = { getItem: (key: string) => stored.get(key) ?? null,
    setItem: (key: string, value: string) => { stored.set(key, value); },
    removeItem: (key: string) => { stored.delete(key); } };
  const ids = Array.from({ length: sourceCount }, (_, index) => `visible-${index}`);
  const requests: string[] = [];
  const reply = (data: unknown) => new Response(JSON.stringify({ code: 0, data }));
  Object.defineProperty(globalThis, 'window', { configurable: true, value: { location: {
    href: 'http://report.test/haitong/#/report?datasourceId=foreign-source', origin: 'http://report.test',
    hash: '#/report?datasourceId=foreign-source', search: '?datasourceId=legacy-source',
  } } });
  for (const key of ['localStorage', 'sessionStorage']) Object.defineProperty(globalThis, key, { configurable: true, value: storage });
  globalThis.fetch = async (input, init) => {
    const path = new URL(String(input), 'http://report.test').pathname.replace(/^\/(?:prod|dev)-api/, '');
    requests.push(path);
    if (path === '/sym/user/me') return reply({ tid: 'current-user', realName: '测试用户' });
    if (path === '/sym/tenant/current') return reply(options.missingTenant ? {} : { tid: 'current-tenant', name: '当前租户' });
    assert.equal(path, '/dst/database/departmentDataSources');
    const body = JSON.parse(String(init?.body)) as { mode: string; datasourceIds: string[] };
    if (body.mode === 'summary') return reply({ list: ids.map(tid => ({ tid, dbName: tid, accessMode: 'upload' })) });
    assert.equal(body.mode, 'reportTables');
    assert.ok(body.datasourceIds.every(id => ids.includes(id)), 'URL parameters cannot grant source access');
    return reply({ total: body.datasourceIds.length, list: body.datasourceIds.map(id => ({
      tid: `${id}-table`, datasourceId: options.foreignRow ? 'foreign-source' : id,
      tableName: 'TEST_TABLE', businessType: '业务表', fieldCount: 2,
    })) });
  };
  t.after(() => {
    globalThis.fetch = previousFetch;
    for (const [key, descriptor] of descriptors) {
      if (descriptor) Object.defineProperty(globalThis, key, descriptor);
      else Reflect.deleteProperty(globalThis, key);
    }
  });
  return requests;
}

test('20 和 100 个可见数据源均为 5 次上下文请求，不按行补查，URL 不扩大权限', async t => {
  for (const count of [20, 100]) await t.test(`${count} 项`, async child => {
    const requests = contextFixture(child, count);
    assert.equal(reportDatasourceIdFromLocation(), 'foreign-source');
    const context = await loadReportContext(reportDatasourceIdFromLocation());
    assert.equal(context.tables.length, count);
    assert.equal(context.user.userId, 'current-user');
    assert.equal(context.tenant.tenantId, 'current-tenant');
    assert.equal(requests.length, 5);
    assert.equal(requests.filter(path => path === '/dst/database/departmentDataSources').length, 3);
  });
});

test('批量结果夹带未请求数据源时拒绝整批，不展示越权表', async t => {
  contextFixture(t, 20, { foreignRow: true });
  await assert.rejects(loadReportContext(), /数据源|范围/);
});

test('当前租户身份缺失时拒绝加载清单，不能落入空租户缓存', async t => {
  const requests = contextFixture(t, 20, { missingTenant: true });
  await assert.rejects(loadReportContext(), /租户信息不完整/);
  assert.equal(requests.filter(path => path === '/dst/database/departmentDataSources').length, 0);
});

test('上报工作台按数据源分组和分页读取表，而不是逐个数据源查询', async () => {
  const originalWindow = Object.getOwnPropertyDescriptor(globalThis, 'window');
  const originalLocalStorage = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
  const originalSessionStorage = Object.getOwnPropertyDescriptor(globalThis, 'sessionStorage');
  const originalFetch = globalThis.fetch;
  const stored = new Map([['token', 'test-platform-token']]);
  const storage = {
    getItem: (key: string) => stored.get(key) ?? null,
    setItem: (key: string, value: string) => { stored.set(key, value); },
    removeItem: (key: string) => { stored.delete(key); },
  };
  const sourceIds = Array.from({ length: 202 }, (_, index) => `source-${String(index).padStart(3, '0')}`);
  const batchCalls: Array<{ ids: string[]; pageNum: number; pageSize: number }> = [];
  const summaryCalls: unknown[] = [];
  const reply = (data: unknown) => new Response(JSON.stringify({ code: 0, data }), {
    status: 200, headers: { 'Content-Type': 'application/json' },
  });
  try {
    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      value: { location: { href: 'http://report.test/report/', origin: 'http://report.test', hash: '#/report', search: '' } },
    });
    Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: storage });
    Object.defineProperty(globalThis, 'sessionStorage', { configurable: true, value: storage });
    globalThis.fetch = async (input, options) => {
      const path = new URL(String(input), 'http://report.test').pathname.replace(/^\/(?:prod|dev)-api/, '');
      if (path === '/sym/user/me') return reply({ userId: 'test-user' });
      if (path === '/sym/tenant/current') return reply({ tenantId: 'test-tenant' });
      if (path !== '/dst/database/departmentDataSources') throw new Error(`Unexpected request: ${path}`);
      const body = JSON.parse(String(options?.body ?? '{}')) as {
        mode: string; datasourceIds?: string[]; pageNum?: number; pageSize?: number;
      };
      if (body.mode === 'summary') {
        summaryCalls.push(body);
        return reply({ list: sourceIds.map(id => ({ tid: id, dbName: id, accessMode: 'upload' })) });
      }
      assert.equal(body.mode, 'reportTables');
      const ids = body.datasourceIds ?? [];
      const pageNum = body.pageNum ?? 1;
      const pageSize = body.pageSize ?? 100;
      batchCalls.push({ ids, pageNum, pageSize });
      const rows = ids.flatMap(id => Array.from({ length: id === sourceIds[0] ? 101 : 1 }, (_, index) => ({
        tid: `${id}-table-${index}`, datasourceId: id, tableName: `TABLE_${index}`,
        tableNameCn: `${id}表${index}`, businessType: '业务表', fieldCount: 2,
      })));
      return reply({ pageNum, pageSize, total: rows.length,
        list: rows.slice((pageNum - 1) * pageSize, pageNum * pageSize) });
    };

    const context = await loadReportContext(sourceIds[0]);
    assert.equal(context.tables.length, 302);
    assert.equal(context.tables.filter(table => table.datasourceId === sourceIds[0]).length, 101);
    assert.equal(summaryCalls.length, 1);
    assert.deepEqual(batchCalls.map(call => [call.ids.length, call.pageNum, call.pageSize]), [
      [1, 1, 100], [1, 2, 100], [200, 1, 100], [200, 2, 100], [1, 1, 100],
    ]);
    assert.ok(batchCalls.every(call => call.ids.length <= 200));
  } finally {
    globalThis.fetch = originalFetch;
    for (const [key, descriptor] of [
      ['window', originalWindow], ['localStorage', originalLocalStorage], ['sessionStorage', originalSessionStorage],
    ] as const) {
      if (descriptor) Object.defineProperty(globalThis, key, descriptor);
      else Reflect.deleteProperty(globalThis, key);
    }
  }
});
