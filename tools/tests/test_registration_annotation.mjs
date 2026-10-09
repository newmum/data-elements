import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const root = new URL('../../', import.meta.url);
const component = new URL('lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/03.登记数据表(register-dbTable-catalog).vue', root);
const source = await fs.readFile(component, 'utf8');
const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)?.[1];
assert.ok(script, 'The canonical registration component must have a script');
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
const ast = babel.Babel.transform(script, {
  ast: true, code: false, configFile: false, babelrc: false,
  parserOpts: { sourceType: 'module', plugins: ['typescript'] },
}).ast;
const functions = ['firstText', 'tableChineseName', 'isAnnotatedForRegistration', 'normalizeCatalogTableRow', 'applySavedCatalogConfigToTable'];
const constants = ['tableBusinessTypeOrder', 'tables'];
const selected = [];
for (const name of functions) {
  const node = ast.program.body.find(n => n.type === 'FunctionDeclaration' && n.id?.name === name);
  assert.ok(node, `Missing production function ${name}`);
  selected.push(script.slice(node.start, node.end));
}
for (const name of constants) {
  const node = ast.program.body.find(n => n.type === 'VariableDeclaration' && n.declarations.some(d => d.id?.name === name));
  assert.ok(node, `Missing production list definition ${name}`);
  selected.push(script.slice(node.start, node.end));
}
let requests = 0;
const catalogs = new Map();
const context = vm.createContext({
  dbId: { value: 'current-source' },
  tableRows: { value: [] },
  catalogAllowedBusinessTypes: { value: ['字典表'] },
  computed: getter => ({ get value() { return getter(); } }),
  normalizeCatalogConfig: value => value || {},
  catalogForTableObject: table => catalogs.get(table.tableName) || {},
  normalizeDictionaryProfiles: value => Array.isArray(value) ? value : [],
  categoryRowsFromProfiles: () => [],
  $common: new Proxy({}, { get() { requests++; throw new Error('List normalization must never issue a request'); } }),
});
vm.runInContext(selected.join('\n') + '\nglobalThis.registrationTables = tables;', context);

const matrix = [
  [null, false], [undefined, false], [0, false], [false, false], ['0', false],
  ['false', false], [true, true], [1, true], ['1', true], ['true', true],
];
for (const [annotated, expected] of matrix) {
  const row = { tid: 'target', tableName: 'public.education', annotated, businessType: '字典表', fieldCount: 10 };
  catalogs.set(row.tableName, { tid: 'source-catalog', fieldGovernanceConfig: { dictionaryStructureType: 'single', dictionaryCategoryNameField: 'category_name' } });
  const result = context.normalizeCatalogTableRow(row, { annotated: true });
  assert.equal(result.annotated, expected, `Source annotation ${String(annotated)} must be authoritative over a catalog and stale local state`);
  assert.equal(result.catalogId, 'source-catalog', 'Keep the lineage/catalog association');
  assert.equal(result.dictionaryStructureType, 'single', 'Keep reusable dictionary settings');
  assert.equal(result.fieldCount, 10, 'Keep physical field metadata');
  assert.equal(row.annotated, annotated, 'Reading must not mutate the API response');
}

const summaries = [];
for (const size of [20, 100]) {
  const before = requests;
  context.tableRows.value = Array.from({ length: size }, (_, i) => {
    const row = { tid: String(i), tableName: `public.table_${i}`, annotated: i % 2 ? 1 : null, businessType: i % 4 === 3 ? '业务表' : '字典表' };
    catalogs.set(row.tableName, { tid: `historical-catalog-${i}` });
    return context.normalizeCatalogTableRow(row, { annotated: true });
  });
  context.catalogAllowedBusinessTypes.value = ['字典表'];
  assert.equal(context.registrationTables.value.length, size / 4, 'Step 3 includes only explicitly marked dictionary tables');
  context.catalogAllowedBusinessTypes.value = ['业务表', '日志表'];
  assert.equal(context.registrationTables.value.length, size / 4, 'Step 4 follows the same annotation boundary');
  assert.equal(requests - before, 0, 'Extra requests must remain zero for both list sizes');
  summaries.push({ inputRows: size, dictionaryRows: size / 4, businessRows: size / 4, addedReadRequests: requests - before });
}
console.log(JSON.stringify({ canonicalComponent: fileURLToPath(component), annotationAndCatalogCases: matrix.length, retainedLineageAndSettings: true, listChecks: summaries }));
