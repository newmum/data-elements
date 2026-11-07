import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../../', import.meta.url);
const source = await fs.readFile(new URL('lowcode/05.数据治理/2.5.1 数据中心(data-warehouse).vue', root), 'utf8');
const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
const ast = babel.Babel.transform(script, {
  ast: true, code: false, configFile: false, babelrc: false,
  parserOpts: { sourceType: 'module', plugins: ['typescript'] },
}).ast;
const names = ['layers', 'layersLoading', 'layersError', 'loadLayers', 'configSaving', 'configDialog',
  'handleAction', 'reindexDomains', 'moveDomain', 'saveNodeConfig'];
const selected = names.map(name => {
  const declaration = ast.program.body.find(node => node.type === 'VariableDeclaration'
    && node.declarations.some(item => item.id?.name === name));
  assert.ok(declaration, `Missing production declaration ${name}`);
  return script.slice(declaration.start, declaration.end);
});
const executable = babel.Babel.transform(selected.join('\n'), {
  filename: 'resource-center.ts', presets: ['typescript'], configFile: false, babelrc: false,
}).code + '\nglobalThis.actual = {' + names.join(',') + '};';

function fixture() {
  let resolve, reject;
  const pendingSaves = [];
  let beginRefresh;
  const refreshStarted = new Promise(done => { beginRefresh = done; });
  const requests = [];
  const saves = [];
  const notices = [];
  const context = vm.createContext({
    ref: value => ({ value }),
    ElMessage: Object.fromEntries(['success', 'warning', 'error'].map(level => [level, message => notices.push({ level, message })])),
    $common: {
      get: path => {
        requests.push(path);
        beginRefresh();
        return new Promise((success, failure) => { resolve = success; reject = failure; });
      },
      post: (path, body) => {
        saves.push({ path, body: JSON.parse(JSON.stringify(body)) });
        return new Promise((resolve, reject) => { pendingSaves.push({ resolve, reject }); });
      },
    },
  });
  vm.runInContext(executable, context);
  return {
    actual: context.actual, requests, saves, notices, refreshStarted,
    resolve: value => resolve(value), reject: error => reject(error),
    resolveSave: value => pendingSaves.shift().resolve(value), rejectSave: error => pendingSaves.shift().reject(error),
  };
}
for (const size of [20, 100]) {
  const f = fixture();
  const request = f.actual.loadLayers();
  assert.equal(f.actual.layersLoading.value, true);
  await f.actual.loadLayers();
  assert.deepEqual(f.requests, ['/dwm/center/layer-list'], 'Concurrent loads must reuse the in-flight work');
  const domains = Array.from({ length: size }, (_, index) => ({ id: `domain-${index}`, tableCount: 1 }));
  f.resolve([{ name: 'DWD', domains: [{ name: '标准库', items: domains }] }]);
  await request;
  assert.equal(f.actual.layersLoading.value, false);
  assert.equal(f.actual.layersError.value, '');
  assert.equal(f.actual.layers.value[0].domains[0].items.length, size);
  assert.equal(f.requests.length, 1, `${size} items must require exactly one overview request`);
}
const failure = fixture();
let pending = failure.actual.loadLayers();
failure.reject(new Error('request timed out'));
await pending;
assert.equal(failure.actual.layersLoading.value, false);
assert.match(failure.actual.layersError.value, /加载失败/);
assert.equal(failure.actual.layers.value.length, 0);
pending = failure.actual.loadLayers();
assert.equal(failure.actual.layersError.value, '');
failure.resolve([{ name: 'DST', domains: [] }]);
await pending;
assert.equal(failure.actual.layers.value[0].name, 'DST');
assert.equal(failure.requests.length, 2, 'Retry is one explicit request');
pending = failure.actual.loadLayers();
failure.resolve({ unexpected: true });
await pending;
assert.match(failure.actual.layersError.value, /加载失败/);
assert.equal(failure.actual.layers.value[0].name, 'DST', 'Failed refresh preserves the last successful overview');
assert.ok(source.includes('!layersLoading && !layersError && !layers.length'), 'Only a successful empty response may display the empty state');
console.log('Resource center: 20/100 items each use one request; duplicate loads, failure, retry and invalid responses verified.');

const layerForm = (size) => ({
  visible: true,
  targetType: 'layer',
  targetId: 'layer-id',
  layerCode: 'dwd',
  name: ' 基础层 ',
  datasourceId: '',
  domains: Array.from({ length: size }, (_, index) => ({
    targetId: `domain-${index}`,
    name: ` 分域 ${index} `,
    code: index === 0 ? '' : ` DOMAIN_${index} `,
    layerCode: index === 0 ? ' DOMAIN_0 ' : 'previous-code',
    datasourceId: index === 0 ? '' : `datasource-${index}`,
    sortNo: index === 0 ? 10 : undefined,
  })),
});

for (const size of [20, 100]) {
  const f = fixture();
  const form = layerForm(size);
  form.domains.push({ targetId: 'view-only', code: 'legacy', readonly: true });
  f.actual.configDialog.value = form;

  const save = f.actual.saveNodeConfig();
  assert.equal(f.actual.configSaving.value, true);
  await f.actual.saveNodeConfig();
  assert.deepEqual(f.saves, [{
    path: '/dwm/center/node/save',
    body: {
      layerId: 'layer-id',
      name: '基础层',
      datasourceId: '',
      domains: Array.from({ length: size }, (_, index) => ({
        targetId: `domain-${index}`,
        datasourceId: index === 0 ? '' : `datasource-${index}`,
        dictName: `分域 ${index}`,
        dictCode: `domain_${index}`,
        sortNo: index === 0 ? 10 : index + 1,
      })),
    },
  }], `${size} editable domains must use one save request with the original mappings`);
  assert.deepEqual(f.requests, [], 'Refresh waits for a successful save');
  f.resolveSave({ success: true });
  await f.refreshStarted;
  assert.deepEqual(f.requests, ['/dwm/center/layer-list']);
  f.resolve([]);
  await save;
  assert.equal(form.visible, false);
  assert.equal(f.actual.configSaving.value, false);
  assert.deepEqual(f.notices, [{ level: 'success', message: '保存成功' }]);
}

const reordered = fixture();
const reorderedForm = layerForm(2);
reorderedForm.domains[0].sortNo = 0;
reorderedForm.domains[1].sortNo = 23;
reordered.actual.configDialog.value = reorderedForm;
const move = reordered.actual.moveDomain(0, 1);
assert.deepEqual(reordered.saves, [
  { path: '/sym/dictSaveOrUpdate', body: { tid: 'domain-0', sortNo: 23 } },
  { path: '/sym/dictSaveOrUpdate', body: { tid: 'domain-1', sortNo: 0 } },
]);
assert.deepEqual(reorderedForm.domains.map(({ targetId, sortNo }) => [targetId, sortNo]),
  [['domain-0', 0], ['domain-1', 23]]);
reordered.resolveSave({});
reordered.resolveSave({});
await move;
assert.deepEqual(reorderedForm.domains.map(({ targetId, sortNo, index }) => [targetId, sortNo, index]),
  [['domain-1', 0, 0], ['domain-0', 23, 1]]);
const saveReordered = reordered.actual.saveNodeConfig();
assert.equal(reordered.saves.length, 3);
assert.equal(reordered.saves[2].path, '/dwm/center/node/save');
assert.deepEqual(reordered.saves[2].body.domains.map(({ targetId, sortNo }) => [targetId, sortNo]),
  [['domain-1', 0], ['domain-0', 23]]);
reordered.resolveSave({ success: true });
await reordered.refreshStarted;
reordered.resolve([]);
await saveReordered;

const saveFailure = fixture();
const failedForm = layerForm(2);
saveFailure.actual.configDialog.value = failedForm;
let saving = saveFailure.actual.saveNodeConfig();
saveFailure.resolveSave({ success: false, message: '目标数据源不可用' });
await saving;
assert.deepEqual(saveFailure.notices, [{ level: 'error', message: '目标数据源不可用' }]);
assert.equal(failedForm.visible, true);
assert.equal(failedForm.domains[1].code, ' DOMAIN_1 ');
assert.equal(saveFailure.actual.configSaving.value, false);
assert.deepEqual(saveFailure.requests, []);
saving = saveFailure.actual.saveNodeConfig();
const apiError = new Error('request rejected');
saveFailure.rejectSave(apiError);
await assert.rejects(saving, apiError);
assert.equal(failedForm.visible, true);
assert.equal(saveFailure.actual.configSaving.value, false);
assert.deepEqual(saveFailure.requests, []);

const single = fixture();
single.actual.configDialog.value = {
  ...layerForm(0), targetType: 'domain', targetId: 'domain-id', layerCode: 'domain_code', datasourceId: 'datasource-id',
};
saving = single.actual.saveNodeConfig();
assert.deepEqual(single.saves, [{
  path: '/dwm/center/node/save',
  body: { targetType: 'domain', targetId: 'domain-id', layerCode: 'domain_code', name: '基础层', datasourceId: 'datasource-id' },
}]);
single.resolveSave({ success: true });
await single.refreshStarted;
single.resolve([]);
await saving;
console.log('Warehouse layer save: 20/100 domains each use one POST and one success refresh. Reorder values, failed responses and the single-domain request were verified.');
