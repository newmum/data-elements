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
  'isCurrentInit', 'trackLoad', 'init', 'collectSourceFields', 'buildDdlTargets', 'currentDdlInput', 'regenerateDdl', 'handleSave',
  'materializationTimeType', 'targetTableItems', 'syncOdsSystemTimeTypes',
  'isHiveTarget',
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
  const columns = [{ columnName: 'person_id', dataType: 'varchar', length: 32 },
    { columnName: 'occurred_at', dataType: 'varchar', columnType: 'varchar', sourceDataType: 'varchar',
      standardField: 'DATETIME', length: 100, defaultValue: '' },
    { columnName: 'birth_date', dataType: 'varchar', sourceDataType: 'varchar', standardField: 'DATE', length: 32 },
    { columnName: 'ODS_RKSJ', dataType: 'date', length: 0 }];
  const datasource = Object.fromEntries(Array.from({ length: size }, (_, i) => [
    `db-${i}`, { dbType: i === 1 ? 'hive' : 'oracle', dbName: `target-${i}` },
  ]));
  const context = vm.createContext({
    ref, computed: getter => ({ get value() { return getter(); } }),
    sourceTableName: ref('TC_RKXT.T_SJYCC_CKB'), datasourceMap: ref(datasource),
    sourceFileConfig: ref(null),
    fieldCollecting: ref(false),
    jsonFormRef: ref({
      validate: async () => {}, getValue: name => form[name],
      setValue: value => Object.assign(form, plain(value)), getFormData: async () => plain(form),
      updateFieldOptions: () => {},
    }),
    tableRef: ref({ validate: async () => {}, getData: () => columns,
      setData: rows => columns.splice(0, columns.length, ...rows) }),
    nifiNodeOptions: ref([]), resolveNifiNodeId: () => 'node-1',
    props: { type: 'add', id: 'source-table', existingTargets: [] }, initRun: 0, open: ref(true),
    loadError: ref(''), knownTargets: ref([]), recoveryTarget: ref(null), templateLoading: ref(false),
    loadPending: ref({}), formRules: ref([]), formData: ref({}), templateTableItems: ref([]),
    datasourceOptions: ref([]), nifiNodeTreeOptions: ref([]),
    buildDatasourceOptions: () => {}, loadManagedNifiNodeOptions: () => {}, createFormRules: () => [],
    nextTick: async () => {}, getDefaultDatasourcePaths: () => [['ODS', 'db-0']],
    normalizeDatasourcePaths: value => value, loadTemplateTableItems: async items => {
      context.templateTableItems.value = items;
    },
    ddlGenerating: ref(false), ddlPlans: ref([]), ddlSqlByTarget: ref({}), activeDdlKey: ref(''),
    ddlSourceFingerprint: ref(''), ddlWasEdited: ref(false), ddlMode: ref(false), isSaveing: ref(false),
    ElMessage: Object.fromEntries(['error', 'warning', 'success'].map(key => [key, message => notices.push({ key, message })])),
    ElMessageBox: { confirm: async () => {} }, emit: (...args) => emitted.push(plain(args)), console,
    $common: { post: async (route, body) => {
      calls.push({ route, body: plain(body) });
      if (route === '/ods/targetDatasource/options' || route === '/ods/nifi-node/access-options') return {};
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
            targetPhysicalExists: context.existingPhysical === true,
            targetDatabaseType: datasource[target.targetDbId].dbType })),
        } };
      }
      if (route.endsWith('/createTable')) {
        assert.equal(body.validateOnly, true, 'Only validation is allowed');
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
  return { context, form, calls, notices, emitted, actual: context.actual };
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

const multi = fixture();
await multi.actual.init();
multi.form.tableName = 'ODS_USER_CONFIRMED';
multi.actual.syncTargetTableNameInput(multi.form.tableName);
for (const paths of [[['ODS', 'db-0'], ['ODS', 'db-1']], [['ODS', 'db-1']], [['ODS', 'db-1'], ['ODS', 'db-0']]]) {
  multi.form.dbId = paths;
  multi.actual.syncTargetDatasourceSelection(paths);
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

// An existing Hive table must block the entire mixed-target plan before any write.
const existingHive = fixture();
await existingHive.actual.init();
existingHive.form.dbId = [['ODS', 'db-0'], ['ODS', 'db-1']];
existingHive.actual.syncTargetDatasourceSelection(existingHive.form.dbId);
existingHive.context.existingPhysical = true;
await existingHive.actual.handleSave();
assert.equal(existingHive.calls.filter(c => c.route === '/ods/createTapleApply').length, 0);
assert.equal(existingHive.calls.filter(c => c.route === '/ods/dataAggReset' && !c.body.dryRun).length, 0);
assert.ok(existingHive.notices.some(n => n.key === 'warning' && /Hive.*修改目标表名.*手动删除/.test(n.message)));
assert.equal(existingHive.emitted.length, 0);

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
  if (size === 20) await f.actual.regenerateDdl(await f.actual.currentDdlInput());
  else await assert.rejects(f.actual.currentDdlInput(), /最多选择 20/);
  assert.equal(f.calls.length - before, size === 20 ? 1 : 0);
  requestCounts.push({ targets: size, ddlRequests: f.calls.length - before, rejected: size > 20 });
}
console.log(JSON.stringify({ defaultCases: defaults.length, existingNamePreserved: true,
  hiveTargetNamesLowercase: true, mixedTargetNamesFollowDialect: true, previewValidationAndSaveAgree: true,
  mismatchedPreviewRejected: true, requestCounts }));

const ftpRequestCounts = [];
for (const fields of [20, 100]) {
  const f = fixture(1);
  f.context.ftpTemplate = { fileName: 'People.csv', remotePath: '/managed', format: 'csv',
    charset: 'GB18030', delimiter: '\t', jsonRecordPath: '$', excelSheetName: '' };
  await f.actual.init();
  f.context.tableRef.value.setData(Array.from({ length: fields }, (_, i) => ({
    columnName: `field_${i}`, dataType: 'varchar', length: 100,
  })));
  assert.equal(f.calls.length, 3);
  assert.equal(f.calls.find(c => c.route === '/ods/getTableTempalte').body.resolveSourceFile, true);
  const before = f.calls.length;
  await f.actual.handleSave();
  assert.deepEqual(f.emitted.at(-1)[1].sourceFileConfig, f.context.ftpTemplate);
  const checks = f.calls.filter(c => c.route === '/ods/getTableTempalte' && c.body.sourceFileConfig);
  assert.equal(checks.length, 1);
  assert.equal(checks[0].body.sourceFileConfig.delimiter, '\t');
  ftpRequestCounts.push({ fields, initialRequests: before, saveRequests: f.calls.length - before });
  await f.actual.collectSourceFields();
  const collection = f.calls.find(c => c.route === '/dst/database/metadata/collectColumns');
  assert.equal(collection.body.sourceFileConfig.delimiter, '\t');
  assert.equal(collection.body.sourceFileConfig.charset, 'GB18030');
  assert.deepEqual(f.calls.filter(c => c.route === '/ods/getTableTempalte').at(-1).body.sourceFileConfig, f.context.ftpTemplate);
}
assert.equal(ftpRequestCounts[0].saveRequests, ftpRequestCounts[1].saveRequests);
console.log(JSON.stringify({ ftpParserForwarded: true, literalTabPreserved: true, ftpRequestCounts }));
