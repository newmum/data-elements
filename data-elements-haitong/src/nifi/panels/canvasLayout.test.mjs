import test from 'node:test';
import assert from 'node:assert/strict';
import { arrangeInitialPipelineLayout, arrangeLegacyGeneratedPipelineLayout } from '../types/dsl.ts';
import { sameExecutableDsl } from '../utils/pipelineVersion.ts';

const parallel = () => ({ version: 1, nodes: [
  { id: 'source', category: 'source', manifestKey: 'source.jdbc', x: 0, y: 120, config: { templateBinding: 'source' } },
  ...[0, 1].flatMap(index => [
    { id: `map-${index}`, category: 'transform', manifestKey: 'transform.field-enrichment', x: 320, y: 50 + index * 180, config: { templateBinding: `governance:${index}` } },
    { id: `sink-${index}`, category: 'sink', manifestKey: index ? 'sink.hive' : 'sink.jdbc', x: 640, y: 50 + index * 180, config: { templateBinding: `target:${index}` } },
  ]),
], edges: [0, 1].flatMap(index => [
  { id: `in-${index}`, source: 'source', target: `map-${index}` },
  { id: `out-${index}`, source: `map-${index}`, target: `sink-${index}` },
]) });

test('released OB/Hive template has separate columns and branch lanes with a centered source', () => {
  const before = parallel(), after = arrangeLegacyGeneratedPipelineLayout(before);
  assert.equal(sameExecutableDsl(before, after), true);
  assert.deepEqual(before.nodes.map(node => node.x), [0, 320, 640, 320, 640]);
  const nodes = Object.fromEntries(after.nodes.map(node => [node.id, node]));
  assert.ok(nodes['map-0'].x - nodes.source.x >= 540);
  assert.ok(nodes['sink-0'].x - nodes['map-0'].x >= 540);
  assert.ok(nodes['map-1'].y - nodes['map-0'].y >= 560);
  assert.equal(nodes.source.y, (nodes['map-0'].y + nodes['map-1'].y) / 2);
  assert.equal(nodes['map-0'].y, nodes['sink-0'].y);
  assert.equal(arrangeLegacyGeneratedPipelineLayout(after), after);
});

test('saved custom coordinates and unrecognized canvases stay unchanged', () => {
  const moved = parallel(); moved.nodes[1].x = 875;
  assert.equal(arrangeLegacyGeneratedPipelineLayout(moved), moved);
  const arbitrary = parallel(); arbitrary.nodes.forEach(node => { node.config = {}; });
  assert.equal(arrangeLegacyGeneratedPipelineLayout(arbitrary), arbitrary);
});

test('new templates reserve full card height and never change execution configuration', () => {
  const before = parallel(); before.nodes.forEach(node => { node.x = 0; node.y = 0; });
  const after = arrangeInitialPipelineLayout(before);
  assert.equal(sameExecutableDsl(before, after), true);
  for (let index = 0; index < after.nodes.length; index++) for (const other of after.nodes.slice(index + 1)) {
    const node = after.nodes[index];
    assert.ok(Math.abs(node.x - other.x) >= 540 || Math.abs(node.y - other.y) >= 560);
  }
});
