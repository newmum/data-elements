import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { test } from 'node:test';

// Test the exact released low-code source, not a second implementation.
const sourcePath = process.env.REGISTRATION_SOURCE_PATH || new URL('../../data-elements/db/migrations/resources/registration-independent-loading-20260926/pages/register-dbTable-catalog.vue', import.meta.url);
const source = fs.readFileSync(sourcePath, 'utf8');
function loadFunction(name, scope) {
  const start = source.search(new RegExp(`(?:async )?function ${name}\\(`));
  assert.ok(start >= 0, `function ${name} exists`);
  const rest = source.slice(start);
  const next = rest.slice(1).search(/\n(?:async )?function \w+\(/);
  const code = next < 0 ? rest : rest.slice(0, next + 1);
  vm.runInNewContext(code + `\nthis.testFunction = ${name};`, scope);
  return scope.testFunction;
}
const tick = () => new Promise(resolve => setImmediate(resolve));
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };

test('saved MySQL columns render without waiting for governance or preview', async () => {
  const governance = deferred();
  const scope = {
    fieldMap: {}, fieldLoadingMap: {}, store: { data: {} },
    loadTableGovernance: () => governance.promise,
    waitForFieldsLoaded: () => Promise.resolve(), unwrapList: value => value,
    normalizeFieldRows: value => value, console,
    $message: { warning: () => assert.fail('unexpected field error') },
    $common: {
      get: async (path, params) => { assert.equal(path, '/dst/database/metadata/columns'); assert.equal(params.snapshotOnly, true); return [{ columnName: 'ID' }]; },
      post: () => assert.fail('existing columns must not trigger physical collection'),
    },
  };
  const loadFields = loadFunction('loadFields', scope);
  await loadFields({ tid: 'table-a', tableName: 'A' });
  assert.equal(scope.fieldMap.A.length, 1);
  assert.equal(scope.fieldLoadingMap.A, false);
  governance.resolve();
});

function previewScope() {
  return {
    activeTable: { value: { tid: 'table-a', tableName: 'A' } }, activeTableName: { value: 'A' },
    activeFields: { value: [{ columnName: 'ID' }] }, dbId: { value: 'source-a' },
    dictionaryPreviewRequestVersion: { value: 0 },
    dictionaryPreview: { loading: false, loaded: false, tableName: '', columns: [], rows: [], total: 0 },
    isStructuredSource: () => false, normalizePreviewResult: value => value,
    loadDictionaryGroupedValues: async () => {},
    ensureDictionaryCategoryRows: () => [], applyAutomaticForceStandardMatches: () => {},
    ensureForceStandardValueMapping: async () => {}, console: { warn() {} },
  };
}
const sample = id => ({ columns: ['ID'], rows: [{ ID: id }], total: 1 });

test('preview rows leave the skeleton before grouped/enrichment queries finish', async () => {
  const grouped = deferred(), scope = previewScope();
  scope.$common = { postSilently: async () => sample('A') };
  scope.loadDictionaryGroupedValues = () => grouped.promise;
  const pending = loadFunction('loadDictionaryPreview', scope)();
  await tick();
  assert.equal(scope.dictionaryPreview.loaded, true);
  assert.equal(scope.dictionaryPreview.loading, false);
  assert.equal(scope.dictionaryPreview.rows[0].ID, 'A');
  grouped.resolve(); await pending;
});

test('a stale preview response cannot overwrite the newly selected table', async () => {
  const old = deferred(), current = deferred(), scope = previewScope();
  scope.$common = { postSilently: (_path, body) => body.tableId === 'table-a' ? old.promise : current.promise };
  const loadPreview = loadFunction('loadDictionaryPreview', scope);
  const first = loadPreview();
  scope.activeTable.value = { tid: 'table-b', tableName: 'B' }; scope.activeTableName.value = 'B';
  scope.dictionaryPreviewRequestVersion.value += 1; scope.dictionaryPreview.loading = false;
  const second = loadPreview(); current.resolve(sample('B')); await second;
  old.resolve(sample('A')); await first;
  assert.equal(scope.dictionaryPreview.rows[0].ID, 'B');
  assert.equal(scope.dictionaryPreview.tableName, 'B');
});

test('preview errors remain local and do not erase configured fields', async () => {
  const scope = previewScope();
  scope.$common = { postSilently: async () => { throw new Error('ORA-00942: 表或视图不存在'); } };
  await loadFunction('loadDictionaryPreview', scope)();
  assert.equal(scope.dictionaryPreview.loading, false);
  assert.equal(scope.dictionaryPreview.loaded, true);
  assert.match(scope.dictionaryPreview.message, /ORA-00942/);
  assert.equal(scope.activeFields.value.length, 1);
});

test('an auxiliary grouping failure preserves successfully loaded preview rows', async () => {
  const scope = previewScope(); scope.$common = { postSilently: async () => sample('A') };
  scope.loadDictionaryGroupedValues = async () => { throw new Error('grouping unavailable'); };
  await loadFunction('loadDictionaryPreview', scope)();
  assert.equal(scope.dictionaryPreview.rows[0].ID, 'A');
  assert.equal(scope.dictionaryPreview.loading, false);
});

test('dictionary field loading no longer replaces the whole configuration panel', () => {
  assert.doesNotMatch(source, /v-else-if="activeFieldLoading && isActiveDictionaryTable"/);
  assert.match(source, /v-if="dictionaryPreview.loading && !dictionaryPreview.loaded"/);
  assert.match(source, /governanceLoadedMap\[table.tid\] = true/);
});
