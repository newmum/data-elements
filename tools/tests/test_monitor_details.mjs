import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';
import { createRequire } from 'node:module';

// Execute actual SFC handlers with isolated transport: no live logs are cleared.
const root = new URL('../../', import.meta.url);
const require = createRequire(new URL('data-elements-chengtian/package.json', root));
const dayjs = require('dayjs');
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
const paths = {
  delay: 'lowcode/03.数据接入/2.3.2 接入监控(access-monitoring)/延时监控(delay-monitor).vue',
  log: 'lowcode/03.数据接入/2.3.2 接入监控(access-monitoring)/异常日志(error-log-panel).vue',
};
const scripts = {};
for (const [kind, path] of Object.entries(paths)) {
  let script = (await fs.readFile(new URL(path, root), 'utf8')).match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
  const ast = babel.Babel.transform(script, { ast: true, code: false, parserOpts: { sourceType: 'module', plugins: ['typescript'] } }).ast;
  for (const node of ast.program.body.filter(n => n.type === 'ImportDeclaration').reverse()) script = script.slice(0, node.start) + script.slice(node.end);
  scripts[kind] = babel.Babel.transform(script, { filename: 'monitor.ts', presets: ['typescript'] }).code;
}
function fixture(kind, transport) {
  const calls = [], notices = [], watches = [], unmounts = [];
  const props = { modelValue: false, taskId: 'task-a', pipelineId: 'flow-a' };
  const request = Object.fromEntries(['get', 'post'].map(method => [method, async (...args) => { calls.push({ method, args }); return transport(...args); }]));
  const context = vm.createContext({
    ref: value => ({ value }), reactive: value => value,
    computed: options => ({ get value() { return typeof options === 'function' ? options() : options.get(); }, set value(v) { options.set(v); } }),
    watch: (get, callback, options) => { watches.push(() => callback(get())); if (options?.immediate) callback(get()); },
    onBeforeUnmount: cb => unmounts.push(cb), defineProps: () => props, defineEmits: () => () => {},
    dayjs, AbortController, window: { $request: request, $common: { copyText() {} } },
    ElMessage: { success: text => notices.push(text) }, ElMessageBox: { confirm: async () => {} },
  });
  const names = kind === 'delay' ? 'fetchData,rows,total,page,pageSize,loadError,loading,handleSearch,handleReset,handleSort,handlePage,draft,sort'
    : 'fetchErrors,errorList,filteredList,filter,keyword,loadError,loading,handleClear';
  vm.runInContext(scripts[kind] + `\nglobalThis.actual = {${names}};`, context);
  props.modelValue = true;
  return { props, calls, notices, actual: context.actual, close: () => { props.modelValue = false; watches.forEach(cb => cb()); } };
}
for (const size of [20, 100]) {
  const f = fixture('delay', async (_, body) => ({ data: { code: 200, data: { list: Array.from({ length: body.pageSize }, (_, i) => ({ tid: i + 1 })), total: 240 } } }));
  f.actual.pageSize.value = size;
  await f.actual.fetchData();
  assert.equal(f.calls.length, 1, `One request for ${size} rows`);
  assert.equal(f.actual.rows.value.length, size);
  f.actual.handlePage(2); await new Promise(resolve => setImmediate(resolve));
  assert.equal(f.calls.length, 2); assert.equal(f.calls[1].args[1].pageNum, 2);
  f.actual.handleSort({ prop: 'monitorTime', order: 'ascending' }); await new Promise(resolve => setImmediate(resolve));
  assert.equal(f.calls.length, 3); assert.equal(f.calls[2].args[1].pageNum, 1);
  assert.equal(f.calls[2].args[1].sortDir, 'asc');
}
const awaitingPage = [];
const pagination = fixture('delay', () => new Promise(resolve => awaitingPage.push(resolve)));
pagination.actual.total.value = 240; pagination.actual.page.value = 2;
const paging = pagination.actual.fetchData();
assert.equal(pagination.actual.total.value, 240, 'Keep total while fetching; Element Plus otherwise resets current page');
awaitingPage[0]({ data: { list: [], total: 240 } }); await paging;
for (const kind of ['delay', 'log']) {
  const fetch = kind === 'delay' ? 'fetchData' : 'fetchErrors';
  for (const body of [{ code: 500, msg: '测试：服务读取失败' }, { code: 200, data: null }]) {
    const f = fixture(kind, async () => ({ data: body })); await f.actual[fetch]();
    assert.ok(f.actual.loadError.value, 'Business and malformed responses must not become empty success');
    assert.equal(f.actual.loading.value, false);
  }
  const failed = fixture(kind, async () => { throw { response: { status: 403 } }; });
  await failed.actual[fetch](); assert.match(failed.actual.loadError.value, /权限/);
  const pending = [];
  const stale = fixture(kind, () => new Promise(resolve => pending.push(resolve)));
  const first = stale.actual[fetch](); const second = stale.actual[fetch]();
  const response = id => ({ data: kind === 'delay' ? { list: [{ tid: id }], total: 1 } : [{ id, phase: 'RUNTIME', message: id }] });
  pending[1](response('new')); await second; pending[0](response('old')); await first;
  assert.equal((kind === 'delay' ? stale.actual.rows : stale.actual.errorList).value[0][kind === 'delay' ? 'tid' : 'id'], 'new');
  const third = stale.actual[fetch](); stale.close(); pending[2](response('closed')); await third;
  assert.equal((kind === 'delay' ? stale.actual.rows : stale.actual.errorList).value.length, 0, 'Closed dialog drops late response');
}
for (const wrapped of [false, true]) {
  const data = [{ id: 'err-1', phase: 'RUNTIME', message: '连接失败', nodeLabel: '数据库写入' }];
  const f = fixture('log', async () => ({ data: wrapped ? { code: 200, data } : data }));
  await f.actual.fetchErrors(); assert.equal(f.calls[0].args[0], '/nifi/api/pipelines/flow-a/errors');
  assert.equal(f.actual.errorList.value.length, 1);
  f.actual.filter.value = 'DEPLOYMENT'; assert.equal(f.actual.filteredList.value.length, 0);
  f.actual.filter.value = 'ALL'; f.actual.keyword.value = '数据库'; assert.equal(f.actual.filteredList.value.length, 1);
  assert.equal(f.calls.length, 1, 'Filter and search add no row requests');
}
const clear = fixture('log', async route => ({ data: route.includes('/delete/') ? '' : [] }));
await clear.actual.handleClear(); assert.equal(clear.calls[0].args[0], '/nifi/api/pipelines/delete/flow-a/errors');
assert.equal(clear.calls.length, 2, 'Clear 204 then one refresh'); assert.equal(clear.actual.loadError.value, '');
console.log('PASS: 20/100 paging, remote sort, raw/wrapped arrays, clear 204, business/HTTP/malformed failures, stale and closed requests, local filtering without N+1');
