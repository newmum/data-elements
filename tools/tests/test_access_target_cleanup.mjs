import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

// Execute the real Vue handlers with an isolated transport. No business data is deleted.
const root = new URL('../../', import.meta.url);
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
const helpers = ['isHiveTarget', 'targetCleanupConfirmText', 'targetCleanupSummary'];
const paths = [
  'lowcode/03.数据接入/2.3.1 数据汇聚(data-convergence).vue',
  'lowcode/03.数据接入/2.3.1 Hive元数据库(data-convergence-hive).vue',
  'lowcode/03.数据接入/2.3.2 接入监控(access-monitoring).vue',
  'lowcode/03.数据接入/2.3.1 数据汇聚(data-convergence)/接入申请(access-apply).vue',
];
const ref = value => ({ value });
const target = { targetTableId: 'target-1', targetDbId: 'db-1', targetTableName: 'ods_person', targetDbType: 'hive' };
let assertions = 0;
for (const [index, file] of paths.entries()) {
  const source = await fs.readFile(new URL(file, root), 'utf8');
  const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)?.[1];
  const names = [...helpers, ...(index === 3 ? ['handleDeleteExistingTarget']
    : index === 2 ? ['deleteTargetPayload', 'deleteSingleTarget', 'deleteAllTargets'] : ['handleTaskRecovery'])];
  const ast = babel.Babel.transform(script, { ast: true, code: false, configFile: false, babelrc: false,
    parserOpts: { sourceType: 'module', plugins: ['typescript'] } }).ast;
  const selected = names.map(name => {
    const node = ast.program.body.find(n => n.type === 'VariableDeclaration' && n.declarations.some(d => d.id?.name === name));
    assert.ok(node, `Missing real handler ${file} ${name}`);
    return script.slice(node.start, node.end);
  });
  const executable = babel.Babel.transform(selected.join('\n'), {
    filename: 'cleanup.ts', presets: ['typescript'], configFile: false, babelrc: false,
  }).code + '\nglobalThis.actual = {' + names.join(',') + '};';
  function fixture(outcome = { targetHivePhysicalRetained: true, targetPhysicalDeleted: false }) {
    const calls = [], notices = [], confirmations = [], emitted = [];
    const messages = Object.fromEntries(['error', 'warning', 'success'].map(key => [key, message => notices.push({ key, message })]));
    const context = vm.createContext({
      console, datasourceMap: ref({ 'db-1': { dbType: 'hive' } }), props: { id: 'source-1' },
      knownTargets: ref([target]), recoveryTarget: ref(target), recoveryDeleting: ref(false),
      scheduleVisible: ref(true), targetDrawerVisible: ref(true), accessTaskId: ref('task-1'),
      deleteDrawerRow: ref({ sourceTableId: 'source-1' }), deleteDrawerVisible: ref(true),
      deleteDrawerTargets: ref([target]), deletingTargets: ref(false), canDeleteTarget: () => true,
      operationErrorMessage: error => error.message, tableRef: ref({ refresh: async () => {} }), refreshTable: async () => {},
      $message: messages, ElMessage: messages,
      ElMessageBox: { confirm: async text => { confirmations.push(text); } },
      emit: (...args) => emitted.push(args),
      $common: { post: async (route, body) => {
        assert.equal(route, '/ods/dataAggReset');
        calls.push({ route, body });
        if (body.batchDryRun) return { data: { batchDryRun: true, sideEffectsApplied: false,
          results: body.targets.map(item => ({ ...item, sideEffectsApplied: false })) } };
        if (body.dryRun) return { data: { targetTableId: body.targetTableId, targetHivePhysicalRetained: true } };
        return { data: outcome };
      } },
    });
    vm.runInContext(executable, context);
    return { context, actual: context.actual, calls, notices, confirmations, emitted };
  }
  async function cleanup(f) {
    if (index === 3) await f.actual.handleDeleteExistingTarget(target);
    else if (index === 2) await f.actual.deleteSingleTarget(target);
    else await f.actual.handleTaskRecovery({ tid: 'task-1', sourceTableId: 'source-1' }, {}, 'target', target);
  }
  const hive = fixture();
  await cleanup(hive);
  assert.ok(hive.confirmations.every(text => /Hive.*保留/.test(text)), file);
  assert.equal(hive.notices.filter(item => item.key === 'error').length, 0, file);
  assert.ok(hive.notices.some(item => item.key === 'success' && /Hive.*保留/.test(item.message)), file);
  const pending = fixture({ targetCleanupPending: true, targetMetadataRetained: true, targetPhysicalDeleted: false });
  await cleanup(pending);
  assert.equal(pending.notices.filter(item => item.key === 'error').length, 0, file);
  assert.ok(pending.notices.some(item => item.key === 'warning' && /待重试/.test(item.message)), file);
  if (index === 3) {
    assert.equal(pending.context.knownTargets.value.length, 1);
    assert.equal(pending.emitted.length, 0, 'Do not remove a pending registration from the UI');
  }
  for (const size of [20, 100]) {
    const before = hive.calls.length;
    const summary = hive.actual.targetCleanupSummary(Array.from({ length: size }, () => ({ targetHivePhysicalRetained: true })));
    assert.match(summary.message, new RegExp(`${size} 个 Hive`));
    assert.equal(hive.calls.length, before, 'Rendering summaries adds zero requests at 20/100 rows');
  }
  if (index === 2) {
    for (const size of [20, 100]) {
      const bulk = fixture();
      bulk.context.deleteDrawerTargets.value = Array.from({ length: size }, (_, i) => ({ ...target, targetTableId: `target-${i}` }));
      await bulk.actual.deleteAllTargets();
      assert.equal(bulk.calls.filter(c => c.body.dryRun).length, Math.ceil(size / 20));
      assert.ok(bulk.calls.filter(c => c.body.dryRun).every(c => c.body.batchDryRun === true && c.body.targets.length <= 20));
      assert.equal(bulk.calls.filter(c => !c.body.dryRun).length, size);
      assert.equal(bulk.notices.filter(item => item.key === 'error').length, 0);
    }
    const denied = fixture();
    denied.context.$common.post = async () => ({ data: { batchDryRun: true, sideEffectsApplied: false,
      results: [{ targetTableId: 'foreign-id', targetDbId: 'db-1', sideEffectsApplied: false }] } });
    await denied.actual.deleteAllTargets();
    assert.equal(denied.confirmations.length, 0, 'Mismatched batch IDs are refused before confirmation or writes');
    assert.ok(denied.notices.some(item => item.key === 'error'));
  }
  assertions += 1;
}
console.log(JSON.stringify({ components: assertions, hiveNoFalseDeletionError: true, pendingRegistrationsRetained: true,
  summaryRequestsAt20And100: 0, monitorPreflightRequests: { targets20: 1, targets100: 5 }, boundedBatchSize: 20 }));
