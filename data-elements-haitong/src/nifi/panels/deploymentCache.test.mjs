import test from 'node:test';
import assert from 'node:assert/strict';
import { QueryClient } from '@tanstack/react-query';
import { acceptDeploymentResult } from '../utils/deploymentCache.ts';

const pipeline = { id: 'flow', name: 'test', status: 'STOPPED', nifiProcessGroupId: 'native-group', lastDeployedHash: 'new-hash',
  dsl: { version: 1, nodes: [{ id: 'source', config: { bound: true } }], edges: [] } };

test('late pre-deployment status/detail reads cannot overwrite the deployment response', async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const keys = [['pipeline', 'flow', 1], ['pipeline-status', 'flow', 1]];
  const finish = [];
  let cancelled = 0;
  const pending = keys.map(queryKey => client.fetchQuery({ queryKey, queryFn: ({ signal }) => new Promise(resolve => {
    signal.addEventListener('abort', () => { cancelled++; });
    finish.push(() => resolve({ status: 'SAVED', deployed: false, dsl: { nodes: [] } }));
  }) }).catch(() => undefined));
  await acceptDeploymentResult(client, { pipeline }, 'flow', 1, () => true);
  finish.forEach(resolve => resolve()); await Promise.all(pending);
  assert.equal(cancelled, 2);
  assert.equal(client.getQueryData(keys[0]).dsl.nodes[0].config.bound, true);
  assert.equal(client.getQueryData(keys[1]).deployed, true);
  assert.equal(client.getQueryData(keys[1]).currentHash, 'new-hash');
  client.clear();
});

test('another session and unrelated pipeline caches stay isolated', async () => {
  const client = new QueryClient();
  client.setQueryData(['pipeline', 'flow', 2], { sentinel: 'other-tenant' });
  client.setQueryData(['pipeline', 'unrelated', 1], { sentinel: 'other-flow' });
  await acceptDeploymentResult(client, { pipeline }, 'flow', 1, () => true);
  assert.equal(client.getQueryData(['pipeline', 'flow', 2]).sentinel, 'other-tenant');
  assert.equal(client.getQueryData(['pipeline', 'unrelated', 1]).sentinel, 'other-flow');
  await acceptDeploymentResult(client, { pipeline: { ...pipeline, lastDeployedHash: 'stale' } }, 'flow', 1, () => false);
  assert.equal(client.getQueryData(['pipeline', 'flow', 1]).lastDeployedHash, 'new-hash');
  client.clear();
});

test('a mismatched resource ID never seeds a deployment or another pipeline', async () => {
  const client = new QueryClient();
  await acceptDeploymentResult(client, { pipeline: { ...pipeline, id: 'other' } }, 'flow', 1, () => true);
  assert.equal(client.getQueryData(['pipeline', 'flow', 1]), undefined);
  assert.equal(client.getQueryData(['pipeline-status', 'flow', 1]), undefined);
  assert.equal(client.getQueryData(['pipeline', 'other', 1]), undefined);
  client.clear();
});
