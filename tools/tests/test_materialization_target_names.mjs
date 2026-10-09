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
  'sameStringList', 'targetDatasourceIds', 'targetTableNameInput', 'targetTablePlans',
  'syncTargetDatasourceSelection', 'syncTargetTableNameInput', 'normalizeFormPayload',
  'isCurrentInit', 'trackLoad', 'init', 'buildDdlTargets', 'currentDdlInput', 'regenerateDdl', 'handleSave',
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
  const columns = [{ columnName: 'person_id', dataType: 'varchar', length: 32 }];
  const datasource = Object.fromEntries(Array.from({ length: size }, (_, i) => [
    `db-${i}`, { dbType: i === 1 ? 'hive' : 'oracle', dbName: `target-${i}` },
  ]));
  const context = vm.createContext({
    ref, computed: getter => ({ get value() { return getter(); } }),
    sourceTableName: ref('TC_RKXT.T_SJYCC_CKB'), datasourceMap: ref(datasource),
    jsonFormRef: ref({
      validate: async () => {}, getValue: name => form[name],
      setValue: value => Object.assign(form, plain(value)), getFormData: async () => plain(form),
      updateFieldOptions: () => {},
    }),
    tableRef: ref({ validate: async () => {}, getData: () => columns }),
    nifiNodeOptions: ref([]), resolveNifiNodeId: () => 'node-1', syncOdsSystemTimeTypes: () => {},
    materializationTimeType: type => type === 'oracle' ? 'date' : 'timestamp',
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
      if (route === '/ods/getTableTempalte' || route === '/ods/queryTargetTableInfo') return { data: {
        propList: { sourceTableName: context.sourceTableName.value, tableName: 'ODS_SAVED_CUSTOM', dbId: [['ODS', 'db-0']] },
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
          results: body.targets.map(target => ({ ...target, sideEffectsApplied: false })),
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
  assert.equal(multi.form.tableName, 'ODS_USER_CONFIRMED', 'Changing targets must preserve custom input');
  assert.ok(multi.actual.targetTablePlans.value.every(plan => plan.tableName === multi.form.tableName));
}
await multi.actual.handleSave();
assert.equal(multi.notices.filter(n => n.key === 'error').length, 0);
const generated = multi.calls.find(c => c.route.endsWith('/getCreateTableDDL')).body.targets;
const validated = multi.calls.find(c => c.route.endsWith('/createTable')).body.targets;
const saved = multi.calls.filter(c => c.route === '/ods/createTapleApply');
assert.equal(saved.length, 2);
for (let i = 0; i < saved.length; i++) {
  assert.equal(generated[i].propList.tableName, 'ODS_USER_CONFIRMED');
  assert.equal(validated[i].tableName, 'ODS_USER_CONFIRMED');
  assert.deepEqual(saved[i].body.propList, generated[i].propList, 'Saving must reuse the preview target configuration');
}
assert.equal(multi.emitted.at(-1)[0], 'save');

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
  multiTargetCustomNamePreserved: true, previewValidationAndSaveAgree: true,
  mismatchedPreviewRejected: true, requestCounts }));
