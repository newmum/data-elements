import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../../', import.meta.url);
const file = new URL('lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/03.登记数据表(register-dbTable-catalog).vue', root);
const source = await fs.readFile(file, 'utf8');
const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
const ast = babel.Babel.transform(script, { ast: true, code: false, parserOpts: { sourceType: 'module', plugins: ['typescript'] } }).ast;
const names = ['dictionaryPreviewTableData', 'loadDictionaryPreview', 'loadBusinessPreview', 'normalizePreviewResult'];
const selected = names.map(name => {
  const node = ast.program.body.find(n => n.type === 'FunctionDeclaration' ? n.id.name === name : n.type === 'VariableDeclaration' && n.declarations.some(d => d.id.name === name));
  assert.ok(node, name);return script.slice(node.start, node.end);
});
const code = babel.Babel.transform(selected.join('\n'), { filename: 'registration-preview.ts', presets: ['typescript'] }).code;
const plain = value => JSON.parse(JSON.stringify(value));
function fixture({ structured = false, count = 240, fail = false } = {}) {
  const rows = Array.from({ length: count }, (_, index) => ({ id: index + 1, category: index < 120 ? 'A' : 'B' }));
  const reads = [];
  const context = vm.createContext({
    computed: get => ({ get value() { return get(); } }), isStructuredSource: () => structured,
    activeTable: { value: { tid: 'table-a', tableName: 'demo', dictionaryStructureType: 'multi' } }, activeTableName: { value: 'demo' },
    selectedCategoryRow: { value: null }, dictionaryStructureType: { value: 'multi' }, roleColumn: () => 'category',
    dbId: { value: 'db-a' }, dictionaryPreviewRequestVersion: { value: 0 }, businessPreviewRequestVersion: { value: 0 },
    dictionaryPreview: { loaded: false, loading: false, rows: [], columns: [], total: 0, initialPage: null, tableName: '' },
    tablePreview: { loaded: false, loading: false, rows: [], fields: [], total: 0, tableName: '' },
    activeFields: { value: [{ columnName: 'id' }, { columnName: 'category' }] },
    connectionErrorMap: {}, previewRowValue: (row, key) => row[key], unwrapList: value => Array.isArray(value) ? value : value.list || [],
    loadDictionaryGroupedValues: async () => {}, loadForceStandardElements: async () => {},
    ensureDictionaryCategoryRows: () => [], tryAutoForceStandardMatch: () => {},
    reportConnectionFailure: () => false, apiPreviewSnapshotForTable: () => null, isPushSource: { value: false },
    loadDictionaryPreviewTranslations: async () => {}, console: { warn() {} },
    $common: { postSilently: async (route, payload) => {
      reads.push({ route, payload: plain(payload) });
      if (fail) throw new Error('read failed');
      if (route.endsWith('/preview-data')) {
        assert.ok(payload.pageSize >= 1 && payload.pageSize <= 100);
        const filtered = payload.conditions?.length ? rows.filter(row => row.category === payload.conditions[0].value) : rows;
        const start = (payload.pageNo - 1) * payload.pageSize;
        return { rows: filtered.slice(start, start + payload.pageSize), columns: ['id', 'category'], total: filtered.length };
      }
      return { rows: rows.slice(0, payload.sampleSize), columns: ['id', 'category'], total: Math.min(count, payload.sampleSize) };
    } },
  });
  vm.runInContext(code + '\nglobalThis.actual = {' + names.join(',') + '};', context);
  return { context, actual: context.actual, reads };
}
const db = fixture();
await db.actual.loadDictionaryPreview();
assert.equal(db.reads[0].payload.pageSize, 20);
assert.equal(db.context.dictionaryPreview.message, '');
let page = await db.actual.dictionaryPreviewTableData.value({ pageNo: 1, pageSize: 20 });
assert.equal(db.reads.length, 1, 'The same server first page should not be fetched twice');
assert.equal(page.list[0].id, 1);assert.equal(page.total, 240);
page = await db.actual.dictionaryPreviewTableData.value({ pageNo: 2, pageSize: 20 });
assert.equal(db.reads.length, 2);assert.equal(page.list[0].id, 21);
for (const size of [20, 100]) {
  const start = db.reads.length;page = await db.actual.dictionaryPreviewTableData.value({ pageNo: 2, pageSize: size });
  assert.equal(db.reads.length - start, 1, `Exactly one backend request for ${size} rows`);
  assert.equal(page.list.length, size);assert.equal(page.list[0].id, size + 1);
}
db.context.selectedCategoryRow.value = { categoryValue: 'B' };
page = await db.actual.dictionaryPreviewTableData.value({ pageNo: 2, pageSize: 20 });
assert.equal(page.list[0].id, 141);assert.equal(page.total, 120);
assert.equal(db.reads.at(-1).payload.conditions[0].value, 'B');
page = await db.actual.dictionaryPreviewTableData.value({ pageNo: 20, pageSize: 20 });assert.equal(page.list.length, 0);assert.equal(page.total, 120);
const empty = fixture({ count: 0 });await empty.actual.loadDictionaryPreview();
assert.equal((await empty.actual.dictionaryPreviewTableData.value({ pageNo: 1, pageSize: 20 })).total, 0);
const failed = fixture({ fail: true });await failed.actual.loadDictionaryPreview();
assert.equal(failed.context.dictionaryPreview.initialPage, null);
await failed.actual.dictionaryPreviewTableData.value({ pageNo: 1, pageSize: 20 });assert.equal(failed.reads.length, 2, 'An error must not become an empty cached page');
const ftp = fixture({ structured: true });await ftp.actual.loadDictionaryPreview();
assert.equal(ftp.reads[0].payload.sampleSize, 200);
page = await ftp.actual.dictionaryPreviewTableData.value({ pageNo: 2, pageSize: 20 });assert.equal(page.list[0].id, 21);assert.equal(ftp.reads.length, 1);
const business = fixture();await business.actual.loadBusinessPreview();
assert.equal(business.reads.length, 1);assert.ok(business.reads[0].route.endsWith('/sample-data'));
assert.equal(business.reads[0].payload.sampleSize, 10);assert.equal(business.context.tablePreview.rows.length, 10);
assert.match(source, /:page-sizes="isStructuredSource\(\) \? \[20, 50, 100, 200\] : \[20, 50, 100\]"/);
console.log('PASS: server first page, page 2, 20/100 rows each one request, category filtering, last/empty/error pages, bounded FTP sample, and step-four sample');
