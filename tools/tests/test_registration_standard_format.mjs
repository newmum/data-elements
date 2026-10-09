import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

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
const functions = [
  'suggestField', 'fieldSuggestion', 'autoStandardField', 'normalizeStandardField',
  'normalizeFieldRows', 'normalizePrimaryKey', 'normalizeTimeRoles', 'isTimestampEnabled',
  'normalizeFieldBase', 'normalizeIdentifier', 'buildColumnType', 'catalogForTable', 'savedCatalogFieldMap',
  'setStandardField', 'clearStandardField', 'applyAutoStandardFields',
];
const selected = functions.map(name => {
  const node = ast.program.body.find(n => n.type === 'FunctionDeclaration' && n.id?.name === name);
  assert.ok(node, `Missing production function ${name}`);
  return script.slice(node.start, node.end);
});
for (const name of ['standardFieldOptions', 'timeRoleOptions']) {
  const node = ast.program.body.find(n => n.type === 'VariableDeclaration' && n.declarations.some(d => d.id?.name === name));
  assert.ok(node, `Missing production format options ${name}`);
  selected.push(script.slice(node.start, node.end));
}
let reads = 0;
const fieldMap = {};
const catalogMap = {};
const context = vm.createContext({
  fieldMap, catalogMap,
  $common: new Proxy({}, { get() { reads++; throw new Error('Field inference must not issue requests'); } }),
  db: new Proxy({}, { get() { reads++; throw new Error('Field inference must not query a database'); } }),
});
vm.runInContext(selected.join('\n'), context);
const row = (columnName, columnType, columnComment = '') => ({ columnName, columnType, columnComment });

// Real registration defect: 案件发现地点 and its address/code share an ambiguous sj substring.
const cases = [
  [row('fxasjsj', 'datetime', '报案人提供的案件发现时间'), 'DATETIME'],
  [row('slsj', 'datetime', '公安机关正式受理案件的时间'), 'DATETIME'],
  [row('fxasjdd', 'varchar(200)', '报案人提供的案件发现地点'), ''],
  [row('fxasjdd_xzqhdm', 'varchar(12)', '发现地点的行政区划编码'), ''],
  [row('fxasjdd_dzmc', 'varchar(200)', '发现地点的具体地址描述'), ''],
  [row('updated_by', 'varchar(50)', '更新人'), ''],
  [row('candidate', 'varchar(200)', '候选项'), ''],
  [row('runtime_version', 'varchar(50)', '运行版本'), ''],
  [row('sjly', 'varchar(50)', '数据来源'), ''],
  [row('rqbh', 'varchar(50)', '容器编号'), ''],
  [row('sj', 'varchar(50)', '数据编号'), ''],
  [row('rq', 'varchar(50)', '容器'), ''],
  [row('create_time', 'timestamp(6)', '创建时间'), 'DATETIME'],
  [row('updated_at', 'varchar(50)'), 'DATETIME'],
  [row('updateTime', 'varchar(50)'), 'DATETIME'],
  [row('businessDate', 'varchar(50)'), 'DATE'],
  [row('birthday', 'date', '出生日期'), 'DATE'],
  [row('cjsj', 'timestamp with time zone', '创建日期'), 'DATETIME'],
  [row('nativeDate', 'date', '业务时间'), 'DATE'],
  [row('rq', 'varchar(10)', '登记日期'), 'DATE'],
  [row('slsj', 'varchar(19)', '受理时间'), 'DATETIME'],
  [row('opaque', 'DATETIME2(6)'), 'DATETIME'],
  [row('opaque', 'timestamptz'), 'DATETIME'],
  [row('sjhm', 'varchar(11)', '手机号码'), 'LXDH'],
  [row('sfzh', 'varchar(18)', '公民身份号码'), 'SFZH'],
];
for (const [field, expected] of cases) {
  assert.equal(context.suggestField(field).standardField, expected, `${field.columnName}: honor type and semantic boundaries`);
  assert.equal(context.autoStandardField(field) || '', expected, 'Both automatic inference entry points must agree');
}

// Saved/manual governance remains authoritative, including deliberate clearing and time roles.
catalogMap.target = { fieldGovernanceConfig: { fields: [
  { columnName: 'manual_text_date', standardField: 'DATETIME', timeRoles: ['business'] },
  { columnName: 'native_time', standardField: '', standardFieldExplicitlyCleared: true, timeRoles: [] },
] } };
fieldMap.target = [{ columnName: 'native_time', timeRoles: ['timestamp'] }];
const restored = context.normalizeFieldRows([
  row('manual_text_date', 'varchar(50)', '业务标记'),
  row('native_time', 'datetime', '业务时间'),
], 'target');
assert.equal(restored[0].standardField, 'DATETIME', 'Do not erase manual text time formats');
assert.deepEqual(Array.from(restored[0].timeRoles), ['business']);
assert.equal(restored[1].standardField, '', 'Explicit clearing must suppress automatic inference');
assert.deepEqual(Array.from(restored[1].timeRoles), [], 'Saved empty time roles override stale temporary roles');
context.setStandardField(restored[0], 'DATE');
assert.equal(restored[0].standardField, 'DATE');
context.clearStandardField(restored[0]);
assert.equal(restored[0].standardFieldExplicitlyCleared, true);

const batchChecks = [];
for (const size of [20, 100]) {
  const before = reads;
  const input = Array.from({ length: size }, (_, i) => row(`fxasjdd_address_${i}`, 'varchar(200)', '案件发现地点'));
  fieldMap[`batch_${size}`] = context.normalizeFieldRows(input, `batch_${size}`);
  context.applyAutoStandardFields();
  assert.ok(fieldMap[`batch_${size}`].every(f => f.standardField === ''), 'Location rows remain unformatted');
  assert.equal(reads - before, 0, 'Neither list normalization nor inference may issue per-row reads');
  assert.deepEqual(input, Array.from({ length: size }, (_, i) => row(`fxasjdd_address_${i}`, 'varchar(200)', '案件发现地点')), 'Keep API metadata unchanged');
  batchChecks.push({ items: size, addedReadRequests: reads - before, addedSqlQueries: 0 });
}
console.log(JSON.stringify({ classificationCases: cases.length, savedManualAndClearedSettingsPreserved: true, batchChecks }));
