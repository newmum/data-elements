import test from 'node:test';
import assert from 'node:assert/strict';
import { sameExecutableDsl, sameDesignDsl, reconcileDeployedDsl } from '../utils/pipelineVersion.ts';

const design = () => ({ version: 1, nodes: [
  { id: 'source', manifestKey: 'source.mysql', label: '来源', category: 'source', x: 0, y: 120,
    config: { syncMode: 'FULL_THEN_INCR', incrementalColumn: 'updated_at', table: 'events' } },
  { id: 'sink', manifestKey: 'sink.dameng', label: '目标', category: 'sink', x: 500, y: 120, config: { table: 'ODS_events' } },
], edges: [{ id: 'link', source: 'source', target: 'sink' }] });

test('a Java round trip with null outlet is runnable without another save', () => {
  const local = design(), remote = structuredClone(local);
  remote.edges[0].outlet = null;
  assert.equal(sameExecutableDsl(local, remote), true);
  assert.equal(sameDesignDsl(local, remote), true);
  assert.deepEqual(local, design());
});

test('layout, array order and generated connection IDs do not change the executable version', () => {
  const local = design(), remote = structuredClone(local);
  remote.nodes.reverse(); remote.nodes[0].x += 100;
  remote.edges[0].id = 'server-connection';
  assert.equal(sameExecutableDsl(local, remote), true);
  assert.equal(sameDesignDsl(local, remote), false);
});

test('real config, label, wiring and outlet edits still require deployment', () => {
  const local = design();
  for (const mutate of [
    dsl => { dsl.nodes[0].config.table = 'other'; },
    dsl => { dsl.nodes[0].label = '其他来源'; },
    dsl => { dsl.edges[0].target = 'source'; },
    dsl => { dsl.edges[0].outlet = 'failure'; },
    dsl => { dsl.nodes[0].config.optional = null; },
  ]) {
    const remote = structuredClone(local); mutate(remote);
    assert.equal(sameExecutableDsl(local, remote), false);
  }
});

test('deployment accepts server-bound config but preserves positions and UI connection IDs', () => {
  const submitted = design(), current = structuredClone(submitted), deployed = structuredClone(submitted);
  current.nodes[0].x = 240;
  deployed.nodes[0].config.mappings = '{"mappings":[{"source":"id","target":"id"}]}';
  deployed.edges[0].outlet = null; deployed.edges[0].id = 'server-id';
  const accepted = reconcileDeployedDsl(current, submitted, deployed);
  assert.equal(accepted.nodes[0].x, 240);
  assert.equal(accepted.edges[0].id, 'link');
  assert.equal(sameExecutableDsl(accepted, deployed), true);
  assert.equal(current.nodes[0].config.mappings, undefined);
});

test('edits made during deployment and deleted nodes are preserved instead of overwritten', () => {
  const submitted = design(), current = structuredClone(submitted), deployed = structuredClone(submitted);
  current.nodes[0].config.table = 'new_table';
  assert.equal(reconcileDeployedDsl(current, submitted, deployed), undefined);
  current.nodes.splice(0, 1);
  assert.equal(reconcileDeployedDsl(current, submitted, deployed), undefined);
});

test('20 and 100 node comparisons perform no row-wise requests and retain every config difference', () => {
  for (const count of [20, 100]) {
    const local = design();
    local.nodes = Array.from({ length: count }, (_, i) => ({ ...local.nodes[0], id: `node-${i}`, config: { tenantBoundResourceId: `resource-${i}` } }));
    local.edges = [];
    const remote = structuredClone(local); remote.nodes.reverse();
    assert.equal(sameExecutableDsl(local, remote), true);
    remote.nodes[0].config.tenantBoundResourceId = 'unauthorized-id';
    assert.equal(sameExecutableDsl(local, remote), false);
  }
});
