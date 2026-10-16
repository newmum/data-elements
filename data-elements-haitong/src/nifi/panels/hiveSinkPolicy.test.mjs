import test from 'node:test';
import assert from 'node:assert/strict';
import { hiveSinkCanvas } from '../utils/hiveSinkPolicy.ts';
import { normalizeCanvasDsl } from '../types/dsl.ts';
import { buildCompositeCanvas } from '../utils/compositeCanvas.ts';

const node = (id, manifestKey, category) => ({ id, manifestKey, category, label: id, x: 0, y: 0, config: {} });
const source = () => node('s', 'source.mysql', 'source');
const mapping = id => node(id, 'transform.field-enrichment', 'transform');
const hive = () => node('h', 'sink.hive', 'sink');
const jdbc = () => node('j', 'sink.jdbc', 'sink');
const edge = (source, target) => ({ id: source + target, source, target, outlet: 'default' });
const graph = (nodes, edges) => ({ version: 1, nodes, edges });

test('Hive target omits consecutive mapping nodes on load and repeated normalization is stable', () => {
  const dsl = graph([source(), mapping('a'), mapping('b'), hive()], [edge('s','a'),edge('a','b'),edge('b','h')]);
  const before = structuredClone(dsl), projected = normalizeCanvasDsl(dsl);
  assert.deepEqual(projected.nodes.map(n => n.id), ['s','h']);
  assert.deepEqual(projected.edges.map(e => [e.source,e.target]), [['s','h']]);
  assert.deepEqual(normalizeCanvasDsl(projected), projected);
  assert.deepEqual(dsl, before);
  assert.deepEqual(Object.keys(buildCompositeCanvas(null, 'p', dsl).nodes), ['s','h']);
});

test('shared JDBC mapping and its parent mappings survive while Hive receives raw source', () => {
  const dsl = graph([source(),mapping('a'),mapping('b'),hive(),jdbc()], [edge('s','a'),edge('a','b'),edge('b','h'),edge('b','j')]);
  const projected = hiveSinkCanvas(dsl);
  assert.equal(projected.nodes.length, 5);
  assert.deepEqual(projected.edges.map(e => [e.source,e.target]), [['s','a'],['a','b'],['s','h'],['b','j']]);
});

test('Hive as a source does not bypass JDBC governance; dbType HIVE target does', () => {
  const s = { ...source(), manifestKey: 'source.hive' };
  const dsl = graph([s,mapping('a'),jdbc()], [edge('s','a'),edge('a','j')]);
  assert.equal(hiveSinkCanvas(dsl), dsl);
  const h = { ...jdbc(), config: { dbType: 'hive' } };
  assert.deepEqual(hiveSinkCanvas(graph([source(),mapping('a'),h], [edge('s','a'),edge('a','j')])).nodes.map(n => n.id), ['s','j']);
});

test('diamond mapping branches produce one raw edge; incomplete drafts remain editable', () => {
  const dsl = graph([source(),mapping('a'),mapping('b'),hive()], [edge('s','a'),edge('s','b'),edge('a','h'),edge('b','h')]);
  assert.equal(hiveSinkCanvas(dsl).edges.length, 1);
  for (const edges of [[edge('a','h')], [edge('a','b'),edge('b','a'),edge('b','h')], [{...edge('s','a'),outlet:'failure'},edge('a','h')]]) {
    const draft = graph(dsl.nodes, edges);
    assert.equal(hiveSinkCanvas(draft), draft);
  }
});

test('20/100 Hive branches need zero remote reads and context lanes use the same projection', () => {
  for (const count of [20,100]) {
    const nodes = [source()], edges = [];
    for (let i=0;i<count;i++) {
      nodes.push(mapping('m'+i),{...hive(),id:'h'+i});
      edges.push(edge('s','m'+i),edge('m'+i,'h'+i));
    }
    const dsl=graph(nodes,edges), projected=hiveSinkCanvas(dsl);
    assert.equal(projected.nodes.length, count+1); assert.equal(projected.edges.length,count);
    const chain={items:[{taskId:'t0',pipeline:{id:'p0',dsl}},{taskId:'t1',pipeline:{id:'p1',dsl}}],links:[]};
    const display=buildCompositeCanvas(chain,'p0',dsl);
    assert.equal(Object.values(display.nodes).some(n => n.node.category==='transform'),false);
  }
});
