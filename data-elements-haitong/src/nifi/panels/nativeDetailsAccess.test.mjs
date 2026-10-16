import test from 'node:test';
import assert from 'node:assert/strict';
import { nativeDetailsAccess } from '../utils/nativeDetailsAccess.ts';

const settled = { isPending: false, isError: false };
const deployed = { id: 'selected', deployed: true, processGroupId: 'native-group' };

test('existing native flow stays viewable after a design/compiler change or failed replacement', () => {
  for (const status of ['RUNNING', 'RUN_ERROR', 'STOPPED', 'SAVED', 'DEPLOY_FAILED', 'DEPLOYING']) {
    const access = nativeDetailsAccess('selected', { ...deployed, status,
      currentHash: 'new-compiler-hash', lastDeployedHash: 'previous-compiler-hash' }, settled);
    assert.equal(access.canOpen, true, status);
    assert.equal(access.tooltip.includes('保存'), false);
  }
  assert.equal(nativeDetailsAccess('selected', { ...deployed, status: 'RUNNING' },
    { isPending: false, isError: true }).canOpen, true);
});

test('native details never borrow the previous task or ignore a missing group', () => {
  assert.equal(nativeDetailsAccess('other', { ...deployed, status: 'RUNNING' }, settled).canOpen, false);
  assert.equal(nativeDetailsAccess(null, { ...deployed, status: 'RUNNING' }, settled).canOpen, false);
  for (const processGroupId of [null, '', ' ']) {
    assert.equal(nativeDetailsAccess('selected', { ...deployed, status: 'STOPPED', processGroupId }, settled).canOpen, false);
  }
});

test('pending, failed status reads and failed deployments show their actual reason', () => {
  assert.match(nativeDetailsAccess('selected', undefined, { ...settled, isPending: true }).tooltip, /正在确认/);
  assert.match(nativeDetailsAccess('selected', undefined, { ...settled, isError: true }).tooltip, /无法确认/);
  const status = { id: 'selected', deployed: false, processGroupId: null };
  assert.match(nativeDetailsAccess('selected', { ...status, status: 'DEPLOY_FAILED' }, settled).tooltip, /错误与告警/);
  assert.match(nativeDetailsAccess('selected', { ...status, status: 'DEPLOYING' }, settled).tooltip, /正在部署/);
  assert.match(nativeDetailsAccess('selected', { ...status, status: 'RUNNING' }, settled).tooltip, /流程关联/);
  assert.match(nativeDetailsAccess('selected', { ...status, status: 'SAVED' }, settled).tooltip, /保存/);
});

test('a removed or unauthorized task cannot reuse a cached deployment link', () => {
  for (const status of [401, 403, 404]) {
    const access = nativeDetailsAccess('selected', { ...deployed, status: 'RUNNING' },
      { ...settled, isError: true, error: { response: { status } } });
    assert.equal(access.canOpen, false);
    assert.equal(access.tooltip.includes('保存'), false);
    assert.match(access.tooltip, status === 401 ? /重新登录/ : /任务列表/);
  }
});
