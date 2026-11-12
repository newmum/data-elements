import test from 'node:test';
import assert from 'node:assert/strict';
import { planSaveSync, saveSyncMode } from '../utils/saveSyncPlan.ts';

const mapping = { primaryProcessorIds: { n: 'p' }, processorIdToCanvasNode: { p: 'n' } };
const dsl = config => ({ version: 1, nodes: [{ id: 'n', manifestKey: 'source.jdbc', label: '来源', category: 'source', x: 0, y: 0, config }], edges: [] });
const row = (field, status, currentValue = '5') => ({ nodeId: 'n', field, status, currentValue });

test('first deployment and historical missing mappings take different paths', () => {
  assert.equal(saveSyncMode(null, null), 'deploy');
  assert.equal(saveSyncMode(null, mapping, 'old-hash'), 'missing-mapping');
  assert.equal(saveSyncMode(null, null, 'old-hash'), 'missing-mapping');
  assert.equal(saveSyncMode('group', null), 'missing-mapping');
  assert.equal(saveSyncMode('group', { ...mapping, primaryProcessorIds: {} }), 'missing-mapping');
  assert.equal(saveSyncMode('group', { ...mapping, processorIdToCanvasNode: {} }), 'missing-mapping');
  assert.equal(saveSyncMode('group', mapping), 'sync');
});

test('safe changes synchronize; conflicts remain for review and other configuration requires redeploy', () => {
  const before = dsl({ batchSize: '5', host: 'old' });
  const after = dsl({ batchSize: '10', host: 'new' });
  const result = planSaveSync(before, after, [row('batchSize', 'READY'), row('maxRowsPerRun', 'CONFLICT')]);
  assert.deepEqual(result.automatic.map(item => item.field), ['batchSize']);
  assert.deepEqual(result.unresolved.map(item => item.field), ['maxRowsPerRun']);
  assert.equal(result.needsRedeploy, true);
  assert.equal(planSaveSync(before, dsl({ batchSize: '10', host: 'old' }), [row('batchSize', 'READY')]).needsRedeploy, false);
  assert.equal(planSaveSync(undefined, after, []).needsRedeploy, true);
});

test('a missing legacy baseline is automatic only when the old design matches NiFi', () => {
  assert.equal(planSaveSync(dsl({ batchSize: '5' }), dsl({ batchSize: '10' }), [row('batchSize', 'REVIEW', '5')]).automatic.length, 1);
  assert.equal(planSaveSync(dsl({ batchSize: '6' }), dsl({ batchSize: '10' }), [row('batchSize', 'REVIEW', '5')]).unresolved.length, 1);
  assert.equal(planSaveSync(dsl({}), dsl({ batchSize: '10' }), [row('batchSize', 'REVIEW', null)]).unresolved.length, 1);
});
