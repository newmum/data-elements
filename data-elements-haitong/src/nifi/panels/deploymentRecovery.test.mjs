import test from 'node:test';
import assert from 'node:assert/strict';
import { recoverDeployment, uncertainDeployment } from '../utils/deploymentRecovery.ts';

const before = { id: 'flow', designHash: 'saved', status: 'STOPPED', lastDeployedAt: 1, updatedAt: 1 };
test('lost response is confirmed using reads without another mutation', async () => {
  let reads = 0;
  const result = await recoverDeployment(before, async () => ++reads < 3 ? { ...before, status: 'DEPLOYING' }
    : { ...before, nifiProcessGroupId: 'new', lastDeployedHash: 'compiled', lastDeployedAt: 2 }, () => true, async () => {});
  assert.equal(reads, 3); assert.equal(result.processGroupId, 'new');
});
test('old deployment, failed deployment and edited or foreign designs are never accepted', async () => {
  for (const after of [before, { ...before, id: 'foreign' }, { ...before, designHash: 'edited' },
    { ...before, status: 'DEPLOY_FAILED', updatedAt: 2 }]) {
    assert.equal(await recoverDeployment(before, async () => after, () => true, async () => {}, 2), undefined);
  }
});
test('candidate replacement and session invalidation retain their own boundaries', async () => {
  const candidate = { processGroupId: 'candidate', designHash: 'saved' };
  const result = await recoverDeployment(before, async () => ({ ...before, nodeMapping: { candidate } }), () => true, async () => {});
  assert.equal(result.mode, 'candidate-created');
  assert.equal(await recoverDeployment(before, async () => { throw Error('unavailable'); }, () => false, async () => {}), undefined);
  assert.equal(uncertainDeployment({ response: { status: 400 } }), false);
  assert.equal(uncertainDeployment({ response: { status: 504 } }), true);
});
