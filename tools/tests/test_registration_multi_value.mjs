import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../../', import.meta.url);
const source = await fs.readFile(new URL('lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/03.登记数据表(register-dbTable-catalog).vue', root), 'utf8');
const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
const ast = babel.Babel.transform(script, { ast: true, code: false, parserOpts: { sourceType: 'module', plugins: ['typescript'] } }).ast;
const names = ['relationSeparator', 'splitRelationValues', 'joinRelationTranslations'];
const context = vm.createContext({});
vm.runInContext(names.map(name => {
  const node = ast.program.body.find(n => n.type === 'FunctionDeclaration' && n.id.name === name);
  assert.ok(node, name);
  return script.slice(node.start, node.end);
}).join('\n'), context);

const labels = new Map([['A', '甲'], ['B', '乙'], ['A B', '带空格编码']]);
const relation = { multiValue: true, multiValueSeparator: ',' };
for (const value of ['A,B', 'A，B', 'A B', 'A\tB', 'A\u3000B', 'A\u00a0B', 'A、B', 'A;B', 'A；B']) {
  assert.equal(context.joinRelationTranslations(value, relation, labels), '甲,乙', value);
}
assert.equal(context.joinRelationTranslations(' A，X A；B、 ', relation, labels), '甲,X,甲,乙');
assert.equal(context.joinRelationTranslations(['A', null, 'B', 'A'], relation, labels), '甲,乙,甲');
assert.equal(context.joinRelationTranslations(null, relation, labels), '');
assert.equal(context.joinRelationTranslations('X，Y', relation, labels), '');
assert.equal(context.joinRelationTranslations('A B||B', { multiValue: true, multiValueSeparator: '||' }, labels), '带空格编码||乙');
assert.equal(context.joinRelationTranslations('A，B', { multiValue: true, multiValueSeparator: ' ' }, labels), '甲 乙');
assert.equal(context.joinRelationTranslations('A B', { multiValue: false }, labels), '带空格编码');
for (const count of [20, 100]) {
  const results = Array.from({ length: count }, () => context.joinRelationTranslations('A，B A', relation, labels));
  assert.ok(results.every(value => value === '甲,乙,甲'));
}
console.log('Registration preview: common/custom separators, arrays, unknowns, duplicates and 20/100 rows passed.');

const batchNames = ['collectRelationPreviewValues', 'loadDictionaryPreviewTranslations',
  'isSafeDictionaryIdentifier', 'buildPreviewDictionaryWhereClause', 'sqlStringLiteral', 'formatPreviewDictionaryConditionValue'];
for (const name of batchNames) {
  const node = ast.program.body.find(n => n.type === 'FunctionDeclaration' && n.id.name === name);
  assert.ok(node, name);
  vm.runInContext(script.slice(node.start, node.end), context);
}
for (const name of ['DICTIONARY_PREVIEW_BATCH_SIZE', 'DICTIONARY_PREVIEW_MAX_VALUES']) {
  const node = ast.program.body.find(n => n.type === 'VariableDeclaration' && n.declarations.some(d => d.id?.name === name));
  assert.ok(node, name);
  vm.runInContext(script.slice(node.start, node.end), context);
}
for (const count of [20, 100]) {
  const calls = [];
  const relation = { enabled: true, multiValue: true, multiValueSeparator: ',', sourceType: 'dictionary',
    dictionaryTableId: 'current-tenant-dictionary', dictionaryKeyField: 'code', dictionaryLabelField: 'label',
    conditions: [{ field: 'enabled', operator: '=', value: '1' }, { field: 'category', operator: '=', value: 'status' }] };
  const translations = {};
  Object.assign(context, {
    dictionaryPreviewTranslationVersion: { value: 0 }, dictionaryPreviewTranslationMap: translations,
    tablePreview: { fields: [{ columnName: 'codes' }], rows: Array.from({ length: count }, () => ({ codes: 'A，X A；B' })) },
    clearDictionaryPreviewTranslations: () => { for (const key of Object.keys(translations)) delete translations[key]; },
    previewGovernanceField: () => ({ dictionaryRelation: relation }), previewRowValue: (row, field) => row[field],
    resolvePreviewDictionaryTableId: value => value.dictionaryTableId, normalizePreviewResult: value => value,
    isPreviewDictionaryRelationActive: () => true, dictionaryPreviewValueKey: (field, value) => JSON.stringify([field, value]),
    quoteIdentifier: name => `"${name}"`,
    $common: { postSilently: async (route, payload) => {
      calls.push({ route, payload });
      assert.equal(route, '/dst/db/sample');assert.equal(payload.tableId, 'current-tenant-dictionary');
      assert.equal(payload.sampleSize, 3);assert.match(payload.whereClause, /'A'/);assert.match(payload.whereClause, /'B'/);
      assert.match(payload.whereClause, /"enabled" = '1'/);assert.match(payload.whereClause, /"category" = 'status'/);
      assert.doesNotMatch(payload.whereClause, /[，；、]/);
      return { rows: [{ code: 'A', label: '甲' }, { code: 'B', label: '乙' }] };
    } },
  });
  await context.loadDictionaryPreviewTranslations();
  assert.equal(calls.length, 1, `${count} records must share one dictionary query`);
  assert.equal(translations[JSON.stringify(['codes', 'A，X A；B'])], '甲,X,甲,乙');
}
console.log('Registration dictionary translation: 20/100 rows each issue one bounded query; category/status filters preserved.');
