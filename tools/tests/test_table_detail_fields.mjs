import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../../', import.meta.url);
const file = 'lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/03.登记数据表(register-dbTable-catalog).vue';
const source = await fs.readFile(new URL(file, root), 'utf8');
const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
const ast = babel.Babel.transform(script, { ast: true, code: false }).ast;
const names = ['loadDetailTable', 'loadFields', 'loadDictionaryTables', 'unwrapList', 'nullableText', 'fieldExportRows', 'exportDetailFields', 'detailFieldType', 'detailFieldLength'];
const functions = names.map(name => {
  const node = ast.program.body.find(node => node.type === 'FunctionDeclaration' && node.id.name === name);
  assert.ok(node, name);
  return script.slice(node.start, node.end);
}).join('\n');

for (const count of [20, 100]) {
  const calls = [], downloads = [];
  const rows = Array.from({ length: count }, (_, i) => ({ tid: `c${i}`, columnName: `field_${i}`, columnComment: i ? `中文字段${i}` : '=SUM(1,2)', columnType: 'varchar(64)', nullable: i % 2, primaryKey: i === 0, timeRoles: i === 1 ? ['timestamp'] : [], standardField: 'DATE', dictionaryRelation: i === 2 ? { enabled: true, dictionaryTable: 'dict_case' } : null }));
  const context = vm.createContext({
    props: { detailTableId: 'table' }, detailMode: { value: true }, detailSavedSnapshot: { value: '' }, dbId: { value: 'source' }, store: { data: {} }, tableRows: { value: [] }, tableLoading: { value: false }, tableLoadError: { value: '' }, governanceLoadedMap: {}, fieldMap: {}, fieldLoadingMap: {}, fieldErrorMap: {}, dictionaryTables: { value: [] },
    normalizeCatalogConfig: value => value, hasGovernanceContent: () => false, isPhaseSavedCatalog: () => false, normalizeCatalogTableRow: value => value, normalizeFieldRows: value => value, loadTableGovernance: async () => {}, ensureDictionaryProfilesLoaded: async () => {},
    $common: { postSilently: async (route, payload) => { calls.push({ route, payload }); if (route.endsWith('/detail')) return { tid: 'source' }; return { list: payload.tid ? [{ tid: 'table', dbId: 'source', tableName: 'police_case' }] : [], total: 0 }; }, getSilently: async (route, payload) => { calls.push({ route, payload }); return rows; } },
    activeFields: { value: rows }, activeTable: { value: { tableName: 'police_case' } }, normalizePrimaryKey: value => Boolean(value), normalizeStandardField: value => value, standardFieldOptions: [{ value: 'DATE', label: '日期' }], timeRolesFor: value => value.timeRoles,
    URL: { createObjectURL: value => { downloads.push(value); return 'blob:test'; }, revokeObjectURL: () => {} }, Blob, document: { createElement: () => ({ click() {} }) }, setTimeout: callback => callback(),
  });
  vm.runInContext(functions + '\nObject.assign(globalThis,{loadDetailTable,loadFields,loadDictionaryTables,fieldExportRows,exportDetailFields,nullableText});', context);
  await context.loadDetailTable();
  await context.loadFields(context.tableRows.value[0], false, false);
  await context.loadDictionaryTables();
  assert.equal(calls.length, 4, `${count} fields: fixed four requests, without per-field fetches`);
  assert.equal(calls.filter(call => call.route.endsWith('/columns')).length, 1);
  assert.ok(calls.every(call => !call.route.includes('preview') && !call.route.includes('collectColumns')));
  assert.equal(context.fieldMap.police_case.length, count);
  assert.equal(context.fieldExportRows().length, count + 1, 'all fields exported');
  assert.equal(context.fieldExportRows()[1][2], rows[0].columnComment, 'current edited values are exported');
  for (const [field, type, length] of [
    [{ columnType: 'varchar(64)' }, 'varchar', '64'],
    [{ columnType: 'decimal(18, 2)' }, 'decimal', '18, 2'],
    [{ columnType: 'varchar', length: 255 }, 'varchar', 255],
    [{ columnType: 'datetime(6)' }, 'datetime', ''],
    [{ columnType: "enum('A','B')" }, 'enum', ''],
    [{ dataType: 'bigint', columnSize: 19 }, 'bigint', 19],
    [{}, '未知类型', ''],
  ]) {
    const original = JSON.stringify(field);
    assert.equal(context.detailFieldType(field), type);
    assert.equal(context.detailFieldLength(field), length);
    assert.equal(JSON.stringify(field), original, 'metadata presentation never changes physical structure');
  }
  assert.equal(context.fieldExportRows()[3][6], JSON.stringify(rows[2].dictionaryRelation));
  for (const [input, expected] of [[0, '否'], [false, '否'], ['0', '否'], [1, '是'], [true, '是'], ['YES', '是'], [null, '未知']]) assert.equal(context.nullableText(input), expected);
  context.exportDetailFields();
  const csv = await downloads[0].text();
  assert.ok(csv.includes('"是否为空"')); assert.ok(csv.includes('"\'=SUM(1,2)"')); assert.ok(csv.includes(`field_${count - 1}`)); assert.ok(csv.includes('时间戳'));
  console.log(`PASS: ${count} fields, four requests, snapshot-only reads, complete CSV governance export and formula protection`);
}
