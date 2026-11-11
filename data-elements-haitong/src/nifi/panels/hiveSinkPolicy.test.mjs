import test from 'node:test';
import assert from 'node:assert/strict';
import { hiveHdfsMergeUnavailable, hiveSinkCanvas, withHiveHdfsMergeDefaults } from '../utils/hiveSinkPolicy.ts';
import { normalizeCanvasDsl } from '../types/dsl.ts';
import { buildCompositeCanvas } from '../utils/compositeCanvas.ts';

const node = (id, manifestKey, category) => ({ id, manifestKey, category, label: id, x: 0, y: 0, config: {} });
const source = () => node('s', 'source.mysql', 'source');
const mapping = id => node(id, 'transform.field-enrichment', 'transform');
const rawTarget = target => ({...target,config:{...target.config,targetTableId:target.id,accessChain:{
  rootSourceTableId:'root-source',sourceTableId:'staging-table',targetTableId:target.id,
  targetCount:2,targetDatasourceCount:2,targetIndex:1,
}}});
const hive = (id='h') => rawTarget(node(id, 'sink.hive', 'sink'));
const jdbc = () => node('j', 'sink.jdbc', 'sink');
const edge = (source, target) => ({ id: source + target, source, target, outlet: 'default' });
const graph = (nodes, edges) => ({ version: 1, nodes, edges });

test('saving a legacy Hive design applies the merge default only to eligible HDFS targets', () => {
  const off = { ...hive('off'), config: { hiveHdfsMerge: false } };
  const partitioned = { ...hive('partitioned'), config: { partitionPlan: { enabled: true } } };
  const recordPut = { ...hive('standard'), config: { hiveWriteMode: 'HIVE_RECORD_PUT' } };
  const legacyJdbc = { ...jdbc(), config: { dbType: 'HIVE' } };
  const saved = graph([hive(), off, partitioned, recordPut, legacyJdbc], []);
  const prepared = withHiveHdfsMergeDefaults(saved);
  assert.deepEqual(prepared.nodes.map(target => target.config.hiveHdfsMerge), [true, false, undefined, undefined, undefined]);
  assert.equal(saved.nodes[0].config.hiveHdfsMerge, undefined);
  assert.equal(hiveHdfsMergeUnavailable(prepared.nodes[0]), undefined);
  assert.match(hiveHdfsMergeUnavailable(partitioned), /分区/);
  assert.match(hiveHdfsMergeUnavailable(recordPut), /写入组件/);
  assert.match(hiveHdfsMergeUnavailable(legacyJdbc), /通用 JDBC/);
});

test('Downstream Hive target omits consecutive mapping nodes on load and repeated normalization is stable', () => {
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

test('Hive as a source does not bypass JDBC governance; downstream dbType HIVE target does', () => {
  const s = { ...source(), manifestKey: 'source.hive' };
  const dsl = graph([s,mapping('a'),jdbc()], [edge('s','a'),edge('a','j')]);
  assert.equal(hiveSinkCanvas(dsl), dsl);
  const h = rawTarget({ ...jdbc(), config: { dbType: 'hive' } });
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
      nodes.push(mapping('m'+i),hive('h'+i));
      edges.push(edge('s','m'+i),edge('m'+i,'h'+i));
    }
    const dsl=graph(nodes,edges), projected=hiveSinkCanvas(dsl);
    assert.equal(projected.nodes.length, count+1); assert.equal(projected.edges.length,count);
    const chain={items:[{taskId:'t0',pipeline:{id:'p0',dsl}},{taskId:'t1',pipeline:{id:'p1',dsl}}],links:[]};
    const display=buildCompositeCanvas(chain,'p0',dsl);
    assert.equal(Object.values(display.nodes).some(n => n.node.category==='transform'),false);
  }
});

test('single direct Hive and parallel first-hop Hive preserve governance in editor and composite canvas',()=>{
  const h=node('h','sink.hive','sink');
  for(const targets of [[h],[h,jdbc()]]){
    const dsl=graph([source(),mapping('a'),...targets],[edge('s','a'),...targets.map(target=>edge('a',target.id))]);
    assert.equal(hiveSinkCanvas(dsl),dsl);
    assert.ok(normalizeCanvasDsl(dsl).nodes.some(n=>n.id==='a'));
    assert.ok(Object.values(buildCompositeCanvas(null,'direct',dsl).nodes).some(n=>n.node.id==='a'));
  }
});
test('first hop and malformed chain context cannot remove governance',()=>{
  for(const patch of [{targetIndex:0},{targetCount:1},{targetDatasourceCount:0},{targetIndex:2},
    {targetCount:101},{targetCount:'2'},{targetIndex:1.5},{sourceTableId:'root-source'},{targetTableId:'foreign'}]){
    const h=hive();h.config.accessChain={...h.config.accessChain,...patch};
    const dsl=graph([source(),mapping('a'),h],[edge('s','a'),edge('a','h')]);
    assert.equal(hiveSinkCanvas(dsl),dsl,JSON.stringify(patch));
  }
});
test('legacy top-level serial context survives normalization and removes only downstream Hive governance',()=>{
  for(const current of ['root','staging']) {
    const s=source();s.config.sourceTableId=current;
    const h=node('h','sink.hive','sink');h.config.targetTableId=current==='root'?'staging':'raw';
    const saved={...graph([s,mapping('a'),h],[edge('s','a'),edge('a','h')]),accessChain:{rootSourceTableId:'root',targetTableIds:['staging','raw']}};
    const normalized=normalizeCanvasDsl(saved);
    assert.equal(normalized.nodes.length,current==='root'?3:2);
    assert.deepEqual(normalizeCanvasDsl(JSON.stringify(normalized)),normalized);
    assert.equal(saved.nodes.length,3);
  }
});
test('raw downstream context remains on the sink after serialization and repeated editor normalization',()=>{
  const dsl=graph([source(),mapping('a'),hive()],[edge('s','a'),edge('a','h')]);
  const normalized=normalizeCanvasDsl(JSON.stringify(dsl));
  assert.deepEqual(normalized.nodes.find(n=>n.id==='h').config,hive().config);
  assert.deepEqual(normalizeCanvasDsl(JSON.stringify(normalized)),normalized);
});
