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
const names = ['loadDetailTable', 'loadFields', 'loadDictionaryTables', 'unwrapList', 'nullableText', 'fieldExportRows', 'exportDetailFields'];
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
  assert.equal(context.fieldExportRows()[3][6], JSON.stringify(rows[2].dictionaryRelation));
  for (const [input, expected] of [[0, '否'], [false, '否'], ['0', '否'], [1, '是'], [true, '是'], ['YES', '是'], [null, '未知']]) assert.equal(context.nullableText(input), expected);
  context.exportDetailFields();
  const csv = await downloads[0].text();
  assert.ok(csv.includes('"是否为空"')); assert.ok(csv.includes('"\'=SUM(1,2)"')); assert.ok(csv.includes(`field_${count - 1}`)); assert.ok(csv.includes('时间戳'));
  console.log(`PASS: ${count} fields, four requests, snapshot-only reads, complete CSV governance export and formula protection`);
}

const governanceNode = ast.program.body.find(node => node.type === 'FunctionDeclaration' && node.id.name === 'buildGovernanceConfig');
const governanceScript = script.slice(governanceNode.start, governanceNode.end);
const dictionary = { dictionaryStructureType:'multi', dictionaryCategoryNameField:'type_name', dictionaryCategories:[{categoryValue:'A',categoryCode:'kept'}], dictionaryProfiles:[{codeField:'code',labelField:'name'}], dictionaryProfile:{codeField:'code',labelField:'name'} };
const originalDictionary = JSON.stringify(dictionary);
const state = vm.createContext({
  normalizeCatalogConfig: value => value,
  buildDictionaryProfilesForTable: () => { throw new Error('detail field editing must not rebuild dictionary rules'); },
  timeRolesFor: row => row.timeRoles || [], normalizePrimaryKey: value => Boolean(value),
  isDictionaryRequired: row => Boolean(row.dictionaryRelation?.enabled),
});
vm.runInContext(governanceScript+'\nglobalThis.build=buildGovernanceConfig;',state);
const config = state.build({tableName:'dict',businessType:'字典表',fieldGovernanceConfig:dictionary},[{columnName:'code',columnComment:'业务编码',timeRoles:[]}],true);
for (const key of Object.keys(dictionary)) assert.equal(JSON.stringify(config[key]),JSON.stringify(dictionary[key]),`detail preserves ${key}`);
assert.equal(JSON.stringify(dictionary),originalDictionary,'dictionary configuration is not mutated');
assert.equal(config.fields[0].columnComment,'业务编码');
console.log('PASS: dictionary detail field edits preserve existing category and translation rules');

const batchNode = ast.program.body.find(node => node.type === 'FunctionDeclaration' && node.id.name === 'applyBatchFieldCommentsFromText');
const batchScript = script.slice(batchNode.start, batchNode.end);
const selectionRows = [{columnName:'one',columnComment:'原一'}, {columnName:'two',columnComment:'原二'}];
const batchState = vm.createContext({
  activeFields:{value:selectionRows}, selectedBusinessFields:{value:[selectionRows[0]]},
  batchCommentDialog:{visible:true,content:'one 新一\ntwo 新二'},
  validateFieldComment:()=>{}, $message:{warning:()=>{},success:()=>{}},
});
vm.runInContext(batchScript+'\nglobalThis.apply=applyBatchFieldCommentsFromText;',batchState);
batchState.apply();
assert.equal(selectionRows[0].columnComment,'新一');
assert.equal(selectionRows[1].columnComment,'原二','batch edits cannot change unselected fields');
batchState.selectedBusinessFields.value=[];
batchState.batchCommentDialog.content='one 再一\ntwo 再二';
batchState.apply();
assert.equal(selectionRows[0].columnComment,'再一');
assert.equal(selectionRows[1].columnComment,'再二','without a selection all current fields remain available');
console.log('PASS: batch comments honor selected fields and default to the current table');

const persistNode = ast.program.body.find(node => node.type === 'FunctionDeclaration' && node.id.name === 'persistGovernanceTable');
const persistScript = script.slice(persistNode.start, persistNode.end);
const pendingRows = [
  {columnName:'one',nullable:0,_nullableEdited:true},
  {columnName:'two',nullable:0,_nullableEdited:true},
  {columnName:'three',nullable:null},
];
let finishSave, sent;
const pendingSave = new Promise(resolve => { finishSave = resolve; });
const table = {tid:'table',tableName:'validation',tableComment:'验证表',businessType:'字典表',fieldGovernanceConfig:{}};
const saveState = vm.createContext({
  detailMode:{value:true}, fieldMap:{validation:pendingRows}, activeTableName:{value:'validation'},
  dbId:{value:'source'}, db:{value:{}}, tableRows:{value:[table]},
  ensureFieldsForTables:async()=>{}, isValidationExemptBusinessType:()=>false,
  nullableText:value=>value==null?'未知':value===0?'否':'是', normalizePrimaryKey:()=>false,
  timeRolesFor:()=>[], normalizeCatalogConfig:value=>value, buildGovernanceConfig:()=>({}),
  $common:{post:async(_route,body)=>{sent=body;return pendingSave;}},
  isPendingBusinessType:()=>false, applyCatalogToMap:()=>{}, applySavedCatalogToMap:()=>{},
});
vm.runInContext(persistScript+'\nglobalThis.persist=persistGovernanceTable;',saveState);
const saving=saveState.persist(table,false);
await new Promise(setImmediate);
assert.equal(sent.columns[0].nullableEdited,true);
assert.equal(sent.columns[2].nullableEdited,false);
pendingRows[1].nullable=1;
pendingRows[2].nullable=0; pendingRows[2]._nullableEdited=true;
finishSave({});
await saving;
assert.equal(pendingRows[0]._nullableEdited,false,'the acknowledged edit is cleared');
assert.equal(pendingRows[1]._nullableEdited,true,'an edit made during the request remains pending');
assert.equal(pendingRows[2]._nullableEdited,true,'a new edit during the request remains pending');
console.log('PASS: a late save cannot clear newer nullable edits');

const saveDetailNode = ast.program.body.find(node => node.type === 'FunctionDeclaration' && node.id.name === 'saveDetailFields');
const saveDetailScript = script.slice(saveDetailNode.start, saveDetailNode.end);
let completeDetailSave;
const saveDetailPending = new Promise(resolve => { completeDetailSave=resolve; });
const detailState = vm.createContext({
  detailSavedSnapshot:{value:''}, currentValue:{value:'提交时'},
  fieldExportRows:()=>[[detailState.currentValue.value]],
  save:()=>saveDetailPending, $message:{success:()=>{},warning:()=>{}},
});
vm.runInContext(saveDetailScript+'\nglobalThis.saveDetails=saveDetailFields;',detailState);
const savingDetails=detailState.saveDetails();
detailState.currentValue.value='提交后继续编辑';
completeDetailSave({success:true});
assert.equal(await savingDetails,true);
assert.equal(detailState.detailSavedSnapshot.value,JSON.stringify([['提交时']]),'later edits remain distinguishable from the saved snapshot');
console.log('PASS: the saved snapshot cannot absorb later edits');
