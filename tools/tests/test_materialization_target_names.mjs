import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../../', import.meta.url);
const component = new URL('lowcode/03.数据接入/2.3.1 数据汇聚(data-convergence)/接入申请(access-apply).vue', root);
const source = await fs.readFile(component, 'utf8');
const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)?.[1];
assert.ok(script);
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
const ast = babel.Babel.transform(script, {
  ast: true, code: false, configFile: false, babelrc: false,
  parserOpts: { sourceType: 'module', plugins: ['typescript'] },
}).ast;
const names = [
  'normalizeDbId', 'normalizeDbIds', 'syncDbTypeByDatasource', 'buildTargetTableName',
  'sameStringList', 'normalizeTargetTableName', 'targetDatasourceIds', 'targetTableNameInput', 'targetTablePlans',
  'syncTargetDatasourceSelection', 'syncTargetTableNameInput', 'normalizeFormPayload',
  'isCurrentInit', 'trackLoad', 'init', 'buildDdlTargets', 'currentDdlInput', 'regenerateDdl', 'handleSave',
  'materializationTimeType', 'targetTableItems', 'syncOdsSystemTimeTypes',
  'isHiveTarget',
  'normalizeCode', 'orderTargetDatasourceIds', 'getOptionValue', 'buildDatasourceOptions', 'getDefaultDatasourcePaths',
  'materializationProgress', 'materializationElapsed', 'materializationAwaitingDecision',
  'materializationStartedAt', 'materializationTimer', 'startMaterializationProgress', 'stopMaterializationProgress', 'confirmMaterialization',
];
const selected = names.map(name => {
  const node = ast.program.body.find(n => n.type === 'VariableDeclaration'
    && n.declarations.some(d => d.id?.name === name));
  assert.ok(node, `Missing production declaration ${name}`);
  return script.slice(node.start, node.end);
});
const executable = babel.Babel.transform(selected.join('\n'), {
  filename: 'materialization.ts', presets: ['typescript'], configFile: false, babelrc: false,
}).code;
const expose = '\nglobalThis.actual = {' + names.join(',') + '};';
const ref = value => ({ value });
const plain = value => JSON.parse(JSON.stringify(value));

function fixture(size = 2) {
  const form = {};
  const calls = [];
  const notices = [];
  const emitted = [];
  const timers = new Map();
  let timerId=0;
  const columns = [{ columnName: 'person_id', dataType: 'varchar', length: 32 },
    { columnName: 'occurred_at', dataType: 'varchar', columnType: 'varchar', sourceDataType: 'varchar',
      standardField: 'DATETIME', length: 100, defaultValue: '' },
    { columnName: 'birth_date', dataType: 'varchar', sourceDataType: 'varchar', standardField: 'DATE', length: 32 },
    { columnName: 'ODS_RKSJ', dataType: 'date', length: 0 }];
  const datasource = Object.fromEntries(Array.from({ length: size }, (_, i) => [
    `db-${i}`, { value: `db-${i}`, layerCode: 'ODS', domainCode: i === 0 ? 'huiju' : i === 1 ? 'yuanshi' : 'other',
      dbType: i === 1 ? 'hive' : 'oracle', dbName: `target-${i}` },
  ]));
  const context = vm.createContext({
    setInterval: callback=>{timers.set(++timerId,callback);return timerId;},clearInterval: id=>timers.delete(id),
    ref, computed: getter => ({ get value() { return getter(); } }),
    sourceTableName: ref('TC_RKXT.T_SJYCC_CKB'), datasourceMap: ref(datasource),
    sourceFileConfig: ref(null),
    templateWarnings: ref([]),
    jsonFormRef: ref({
      validate: async () => {}, getValue: name => form[name],
      setValue: value => Object.assign(form, plain(value)), getFormData: async () => plain(form),
      updateFieldOptions: () => {},
    }),
    tableRef: ref({ validate: async () => {}, getData: () => columns,
      setData: rows => columns.splice(0, columns.length, ...rows) }),
    nifiNodeOptions: ref([]), resolveNifiNodeId: () => 'node-1',
    props: { type: 'add', id: 'source-table', existingTargets: [] }, initRun: 0, open: ref(true),
    loadError: ref(''), loadErrorCode: ref(''), knownTargets: ref([]), recoveryTarget: ref(null), templateLoading: ref(false),
    loadPending: ref({}), formRules: ref([]), formData: ref({}), templateTableItems: ref([]),
    datasourceOptions: ref([]), datasourcePathMap: ref({}), datasourceLeafIds: ref([]), nifiNodeTreeOptions: ref([]),
    loadManagedNifiNodeOptions: () => {}, createFormRules: () => [],
    nextTick: async () => {},
    normalizeDatasourcePaths: value => value, loadTemplateTableItems: async items => {
      context.templateTableItems.value = items;
    },
    ddlGenerating: ref(false), ddlPlans: ref([]), ddlSqlByTarget: ref({}),
    ddlWarningsByTarget: ref({}), ddlTableItemsByTarget: ref({}), activeDdlKey: ref(''),
    ddlSourceFingerprint: ref(''), ddlWasEdited: ref(false), ddlMode: ref(false), isSaveing: ref(false),
    ElMessage: Object.fromEntries(['error', 'warning', 'success'].map(key => [key, message => notices.push({ key, message })])),
    ElMessageBox: { confirm: async () => {} }, emit: (...args) => emitted.push(plain(args)), console,
    askExistingDecision: async (targets, canReuse) => {
      calls.push({route:'user-confirm', targets:plain(targets),canReuse});
      return context.existingChoice || 'flow';
    },
    $common: { post: async (route, body) => {
      calls.push({ route, body: plain(body) });
      if (route === '/ods/targetDatasource/options') {
        const list = context.optionRows || Object.values(context.datasourceMap.value);
        return { data: { list, options: list.length ? [{ value: body.taskCategory === 'distribution' ? 'dept-1' : 'ODS', children: list }] : [] } };
      }
      if (route === '/ods/nifi-node/access-options') return {};
      if (route === '/dst/database/metadata/collectColumns') return { data: columns };
      if (route === '/ods/getTableTempalte' || route === '/ods/queryTargetTableInfo') return { data: {
        propList: { sourceTableName: context.sourceTableName.value, tableName: 'ODS_SAVED_CUSTOM', dbId: [['ODS', 'db-0']],
          sourceFileConfig: context.ftpTemplate || null },
        tableItems: columns,
      } };
      if (route.endsWith('/getCreateTableDDL')) return { data: { batchGenerate: true,
        results: body.targets.map(target => ({
          dbId: target.propList.dbId, requestedTableName: target.propList.tableName,
          tableName: context.renameResponse ? 'WRONG_NAME' : target.propList.tableName,
          ddl: `CREATE TABLE ${target.propList.tableName} (person_id varchar(32))`,
        })),
      } };
      if (route === '/ods/dataAggReset') {
        assert.equal(body.dryRun, true, 'No deletion is allowed in this test');
        return { data: { batchDryRun: true, sideEffectsApplied: false,
          results: body.targets.map(target => ({ ...target, sideEffectsApplied: false,
            targetPhysicalExists: context.existingPhysical === true || context.existingDbIds?.includes(target.targetDbId),
            targetDatabaseType: datasource[target.targetDbId].dbType })),
        } };
      }
      if (route.endsWith('/createTable')) {
        assert.equal(body.validateOnly, true, 'Only validation is allowed');
        if (body.batchReuseValidation) {
          if (context.incompatibleExisting) throw new Error('已有目标表结构不兼容：ODS_UUID（缺少字段）');
          return {data:{batchReuseValidation:true,sideEffectsApplied:false,results:body.targets.map(target=>({
            dbId:target.dbId, requestedTableName:target.tableName, valid:true, physicalTableReused:true,sideEffectsApplied:false,
          }))}};
        }
        return { data: { batchValidateOnly: true, sideEffectsApplied: false,
          results: body.targets.map(target => ({ dbId: target.dbId, requestedTableName: target.tableName,
            valid: true, sideEffectsApplied: false })),
        } };
      }
      if (route === '/ods/createTapleApply') return { data: { targetTableId: `fake-${body.propList.dbId}` } };
      throw new Error(`Unexpected route ${route}`);
    } },
  });
  vm.runInContext(executable + expose, context);
  return { context, form, calls, notices, emitted, timers, actual: context.actual };
}

const defaults = [
  ['TC_RKXT.T_SJYCC_CKB', 'ODS_T_SJYCC_CKB'], ['public.education', 'ODS_education'],
  ['"OWNER"."Table"', 'ODS_Table'], ['[owner].[Table]', 'ODS_Table'],
  ['`database`.`Table`', 'ODS_Table'], ['public.ODS_person', 'ODS_person'], ['person', 'ODS_person'],
];
for (const [sourceName, targetName] of defaults) {
  const f = fixture();
  f.context.sourceTableName.value = sourceName;
  await f.actual.init();
  assert.equal(f.context.loadError.value, '');
  assert.equal(f.form.tableName, targetName, 'The initialized form must use only the source leaf');
  assert.equal(f.context.sourceTableName.value, sourceName, 'Retain the source read identity');
  assert.equal(f.calls.length, 3, 'Initialization must stay at three independent requests');
}
const view = fixture();
view.context.props.type = 'view';
await view.actual.init();
assert.equal(view.form.tableName, 'ODS_SAVED_CUSTOM', 'Viewing an existing target must keep its registered name');
assert.deepEqual(view.form.dbId, [['ODS', 'db-0']], 'Viewing must not replace the saved target selection');

const domainCases = [
  { codes: ['other', 'yuanshi', 'huiju'], expected: ['db-1', 'db-2'] },
  { codes: ['yuanshi'], expected: ['db-0'] },
  { codes: ['huiju'], expected: ['db-0'] },
  { codes: ['other'], expected: [] },
  { codes: [], expected: [] },
  { codes: [' huiju ', 'YUANSHI', 'huiju', 'yuanshi'], expected: ['db-0', 'db-1', 'db-2', 'db-3'] },
];
for (const { codes, expected } of domainCases) {
  const f = fixture();
  f.context.optionRows = codes.map((domainCode, i) => ({ value: `db-${i}`, domainCode, layerCode: 'ODS',
    dbType: 'oracle', label: '名称与编码无关' }));
  await f.actual.init();
  assert.deepEqual(f.form.dbId, expected.map(id => ['ODS', id]));
  assert.equal(f.calls.length, 3, 'Default selection must not add per-domain or per-datasource reads');
}
const authorizedLeaves = fixture();
authorizedLeaves.context.optionRows = [
  { value: 'allowed', layerCode: 'ODS', domainCode: 'huiju', dbType: 'oracle' },
  { value: 'allowed', layerCode: 'ODS', domainCode: 'huiju', dbType: 'oracle' },
  { value: 'another-layer', layerCode: 'DWD', domainCode: 'yuanshi', dbType: 'oracle' },
];
await authorizedLeaves.actual.init();
assert.deepEqual(authorizedLeaves.form.dbId, [['ODS', 'allowed']], 'Only authorized ODS leaves can be selected, without duplicates');
console.log(JSON.stringify({ odsDomainDefaultCases: domainCases.length, savedSelectionPreserved: true,
  defaultsDeduplicated: true, unrelatedDomainsExcluded: true }));

for (const size of [20, 100]) {
  const f = fixture(size);
  f.context.props.taskCategory = 'distribution';
  await f.actual.init();
  assert.equal(f.context.loadError.value, '');
  assert.deepEqual(f.form.dbId, [], 'Distribution must wait for an explicit department datasource selection');
  assert.equal(f.calls.length, 3, 'Department targets must keep the three fixed initialization reads');
  assert.equal(f.calls[0].body.taskCategory, 'distribution');
  assert.deepEqual(plain(f.context.datasourcePathMap.value['db-0']), ['dept-1', 'db-0']);
  f.actual.syncTargetDatasourceSelection([['dept-1', 'db-1']]);
  assert.equal(f.actual.targetTablePlans.value[0].tableName, 'ods_t_sjycc_ckb', 'Department paths retain Hive target naming');
  f.context.props.type = 'view';
  await f.actual.init();
  assert.deepEqual(f.form.dbId, [['ODS', 'db-0']], 'Viewing saved distribution targets must preserve their existing paths');
  console.log(JSON.stringify({ distributionChoices: size, initialRequests: 3, noImplicitSelection: true, departmentPaths: true }));
}

const multi = fixture();
await multi.actual.init();
multi.form.tableName = 'ODS_USER_CONFIRMED';
multi.actual.syncTargetTableNameInput(multi.form.tableName);
for (const paths of [[['ODS', 'db-0'], ['ODS', 'db-1']], [['ODS', 'db-1']], [['ODS', 'db-1'], ['ODS', 'db-0']]]) {
  multi.form.dbId = paths;
  multi.actual.syncTargetDatasourceSelection(paths);
  const expectedIds = paths.length === 1 ? ['db-1'] : ['db-0', 'db-1'];
  assert.deepEqual(plain(multi.actual.targetDatasourceIds.value), expectedIds,
    'Display and materialization follow the configured domain order, even after reversed clicks');
  assert.deepEqual(plain(multi.actual.normalizeFormPayload(multi.form).targetDbIds), expectedIds);
  assert.equal(multi.form.tableName, paths.length === 1 ? 'ods_user_confirmed' : multi.actual.targetTableNameInput.value);
  for (const plan of multi.actual.targetTablePlans.value) {
    assert.equal(plan.tableName, multi.actual.normalizeTargetTableName(multi.form.tableName, multi.context.datasourceMap.value[plan.dbId].dbType));
  }
}
// Mixed targets share the entered base name while each uses its own physical naming policy.
multi.form.tableName = 'ODS_User_CONFIRMED';
multi.actual.syncTargetTableNameInput(multi.form.tableName);
await multi.actual.handleSave();
assert.equal(multi.notices.filter(n => n.key === 'error').length, 0);
const generated = multi.calls.find(c => c.route.endsWith('/getCreateTableDDL')).body.targets;
const validated = multi.calls.find(c => c.route.endsWith('/createTable')).body.targets;
const saved = multi.calls.filter(c => c.route === '/ods/createTapleApply');
assert.equal(saved.length, 2);
assert.deepEqual(saved.map(c => c.body.propList.dbId), ['db-0', 'db-1'],
  'Reversed clicks must still materialize huiju first and yuanshi second');
for (let i = 0; i < saved.length; i++) {
  const expectedName = generated[i].propList.dbType === 'hive' ? 'ods_user_confirmed' : 'ODS_User_CONFIRMED';
  assert.equal(generated[i].propList.tableName, expectedName);
  assert.equal(validated[i].tableName, expectedName);
  assert.deepEqual(saved[i].body.propList, generated[i].propList, 'Saving must reuse the preview target configuration');
  assert.deepEqual(saved[i].body.tableItems, generated[i].tableItems, 'Persisted types must match this target DDL');
  const fields = generated[i].tableItems;
  assert.equal(fields.find(f => f.columnName === 'occurred_at').dataType,
    generated[i].propList.dbType === 'oracle' ? 'date' : 'timestamp');
  assert.equal(fields.find(f => f.columnName === 'birth_date').dataType, 'date');
  assert.equal(fields.find(f => f.columnName === 'occurred_at').sourceDataType, 'varchar');
  assert.equal(fields.find(f => f.columnName === 'occurred_at').defaultValue, null);
}
assert.equal(multi.emitted.at(-1)[0], 'save');
assert.equal(multi.timers.size,0,'Successful materialization stops its local clock');
assert.equal(multi.actual.materializationProgress.value.completed,2,'Completion comes from successful target responses');

const changedOrder = fixture();
await changedOrder.actual.init();
changedOrder.context.datasourceLeafIds.value = ['db-1', 'db-0'];
assert.deepEqual(plain(changedOrder.actual.getDefaultDatasourcePaths()), [['ODS','db-1'],['ODS','db-0']],
  'Moving domains in the administration page changes the canonical order; codes are not hardcoded ranks');
assert.deepEqual(plain(changedOrder.actual.normalizeFormPayload({dbId:[['ODS','db-0'],['ODS','db-1']]}).targetDbIds), ['db-1','db-0']);
changedOrder.context.props.taskCategory = 'distribution';
assert.deepEqual(plain(changedOrder.actual.normalizeFormPayload({dbId:[['ODS','db-0'],['ODS','db-1']]}).targetDbIds), ['db-0','db-1'],
  'Department datasource selections keep their explicit distribution order');

// Existing Hive/ordinary tables are reused only after an explicit choice and a batch schema check.
const existingHive = fixture();
await existingHive.actual.init();
existingHive.form.dbId = [['ODS', 'db-0'], ['ODS', 'db-1']];
existingHive.actual.syncTargetDatasourceSelection(existingHive.form.dbId);
existingHive.context.existingPhysical = true;
await existingHive.actual.handleSave();
assert.equal(existingHive.calls.filter(c => c.route === '/ods/createTapleApply').length, 2);
assert.ok(existingHive.calls.filter(c => c.route === '/ods/createTapleApply').every(c=>c.body.reuseExistingTable===true));
assert.equal(existingHive.calls.filter(c => c.route === '/ods/dataAggReset' && !c.body.dryRun).length, 0);
assert.equal(existingHive.calls.filter(c=>c.route.endsWith('/createTable') && c.body.batchReuseValidation).length,1);
assert.equal(existingHive.calls.find(c=>c.route==='user-confirm').canReuse,true,'Physical table without registration can be adopted');
assert.equal(existingHive.emitted.length, 1);
assert.equal(existingHive.timers.size,0,'Early exit also stops its local clock');

for (const type of ['hive','mrshive','mysql','postgresql','kingbase8','oracle','dameng','sqlserver']) {
  const mixed=fixture(); mixed.context.datasourceMap.value['db-1'].dbType=type;
  await mixed.actual.init(); mixed.form.dbId=[['ODS','db-1'],['ODS','db-0']];
  mixed.actual.syncTargetDatasourceSelection(mixed.form.dbId); mixed.context.existingDbIds=['db-1'];
  await mixed.actual.handleSave();
  const registrations=mixed.calls.filter(c=>c.route==='/ods/createTapleApply');
  assert.deepEqual(registrations.map(c=>c.body.propList.dbId),['db-0','db-1']);
  assert.deepEqual(registrations.map(c=>c.body.reuseExistingTable),[false,true]);
  assert.equal(mixed.emitted.length,1);
  assert.equal(mixed.calls.findIndex(c=>c.body?.batchReuseValidation)<mixed.calls.findIndex(c=>c.route==='/ods/createTapleApply'),true,
    'All reused targets are checked before creating the missing target');
}
const incompatible=fixture(); await incompatible.actual.init();
incompatible.form.dbId=[['ODS','db-0'],['ODS','db-1']]; incompatible.context.existingPhysical=true;
incompatible.context.incompatibleExisting=true; await incompatible.actual.handleSave();
assert.equal(incompatible.calls.filter(c=>c.route==='/ods/createTapleApply').length,0);
assert.equal(incompatible.emitted.length,0);
const cancelled=fixture(); await cancelled.actual.init(); cancelled.context.existingPhysical=true;
cancelled.context.existingChoice='cancel'; await cancelled.actual.handleSave();
assert.equal(cancelled.calls.filter(c=>c.route==='/ods/createTapleApply').length,0);

const timed=fixture();
timed.actual.startMaterializationProgress();
timed.actual.startMaterializationProgress();
assert.equal(timed.timers.size,1,'Repeated starts cannot leak clock intervals');
const readsBeforeTick=timed.calls.length;
[...timed.timers.values()][0]();
assert.equal(timed.calls.length,readsBeforeTick,'Elapsed-time updates never poll a backend');
timed.actual.stopMaterializationProgress();
assert.equal(timed.timers.size,0,'Unmount/finish cleanup stops the interval');
const failed=fixture();
failed.context.jsonFormRef.value.validate=async()=>{throw new Error('invalid form');};
await failed.actual.handleSave();
assert.equal(failed.timers.size,0,'Validation failure also stops the interval');
assert.equal(failed.context.isSaveing.value,false,'Validation failure restores submit state');

for (const type of ['hive', 'Hive', 'mrshive']) {
  const hive = fixture();
  hive.context.datasourceMap.value['db-0'].dbType = type;
  await hive.actual.init();
  assert.equal(hive.form.tableName, 'ods_t_sjycc_ckb');
  hive.form.tableName = 'ODS_Manually_EDITED';
  hive.actual.syncTargetTableNameInput(hive.form.tableName);
  assert.equal(hive.form.tableName, 'ods_manually_edited');
  const input = await hive.actual.currentDdlInput();
  assert.equal(input.targets[0].propList.tableName, 'ods_manually_edited');
}

const renamed = fixture();
await renamed.actual.init();
renamed.context.renameResponse = true;
await assert.rejects(renamed.actual.regenerateDdl(await renamed.actual.currentDdlInput()), /表名与输入框不一致/);
renamed.form.tableName = 'ODS_TC_RKXT.T_SJYCC_CKB';
await assert.rejects(renamed.actual.currentDdlInput(), /不能包含用户名/);

const requestCounts = [];
for (const size of [20, 100]) {
  const f = fixture(size);
  await f.actual.init();
  f.form.dbId = Array.from({ length: size }, (_, i) => ['ODS', `db-${i}`]);
  const before = f.calls.length;
  assert.equal(before, 3, '20/100 option initialization must use the same three reads');
  if (size === 20) await f.actual.regenerateDdl(await f.actual.currentDdlInput());
  else await assert.rejects(f.actual.currentDdlInput(), /最多选择 20/);
  assert.equal(f.calls.length - before, size === 20 ? 1 : 0);
  requestCounts.push({ targets: size, initialRequests: before, ddlRequests: f.calls.length - before, rejected: size > 20 });
}
console.log(JSON.stringify({ defaultCases: defaults.length, existingNamePreserved: true,
  hiveTargetNamesLowercase: true, mixedTargetNamesFollowDialect: true, previewValidationAndSaveAgree: true,
  mismatchedPreviewRejected: true, requestCounts }));

const ftpRequestCounts = [];
{
  const f = fixture(1);
  const original = { columnName: 'UNID', dataType: 'varchar', columnType: 'text', length: 64,
    primaryKey: 1, sourceDataType: 'text', sourceColumnType: 'text' };
  const edited = plain(f.actual.targetTableItems([{ ...original, defaultValue: 'source_specific_function()' }], 'oceanbaseoracle'))[0];
  assert.equal(edited.defaultValue, null);
  assert.equal(edited.columnType, 'varchar(64)');
  assert.equal(edited.sourceColumnType, 'text');
  assert.equal(original.columnType, 'text');
  const resized = plain(f.actual.targetTableItems([{ ...original, columnType: 'varchar(32)', length: 128 }], 'oceanbaseoracle'))[0];
  assert.equal(resized.columnType, 'varchar(128)');
  const unbounded = plain(f.actual.targetTableItems([{ ...original, dataType: 'text', length: 0 }], 'oceanbaseoracle'))[0];
  assert.equal(unbounded.columnType, 'text');
  console.log(JSON.stringify({ editedColumnTypeSynchronized: true, editedLengthSynchronized: true, sourceTypesPreserved: true }));
}
for (const fields of [20, 100]) {
  const f = fixture(1);
  f.context.ftpTemplate = { fileName: 'People.csv', remotePath: '/managed', format: 'csv',
    charset: 'GB18030', delimiter: '\t', jsonRecordPath: '$', excelSheetName: '' };
  await f.actual.init();
  f.context.tableRef.value.setData(Array.from({ length: fields }, (_, i) => ({
    columnName: `field_${i}`, dataType: 'varchar', length: 100,
  })));
  assert.equal(f.calls.length, 3);
  assert.deepEqual(plain(f.calls.find(c => c.route === '/ods/getTableTempalte').body), { tid: f.context.props.id });
  const before = f.calls.length;
  await f.actual.handleSave();
  assert.deepEqual(f.emitted.at(-1)[1].sourceFileConfig, f.context.ftpTemplate);
  assert.equal(f.calls.filter(c => c.route === '/ods/getTableTempalte').length, 1);
  assert.equal(f.calls.filter(c => c.route === '/dst/database/metadata/collectColumns').length, 0);
  ftpRequestCounts.push({ fields, initialRequests: before, saveRequests: f.calls.length - before });
}
assert.equal(ftpRequestCounts[0].saveRequests, ftpRequestCounts[1].saveRequests);
console.log(JSON.stringify({ ftpParserForwarded: true, literalTabPreserved: true, ftpRequestCounts }));
