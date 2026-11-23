import assert from 'node:assert/strict';
import test from 'node:test';
import { buildTemplateFlow, parseTemplateFlow, type TemplateSpec } from '../src/pages/development/accessTemplateDefinition.ts';

const spec = (targets: string[]): TemplateSpec => ({ topology: 'PARALLEL', sourceType: 'ANY',
  targets, intervalSeconds: 60, fetchSize: 10000, sourceSchedule: 'REGISTERED',
  engine: 'CANVAS', hiveMergeMaxAgeSeconds: 30 });

for (const relational of ['OCEANBASE_ORACLE', 'ORACLE']) {
  test(`${relational} and Hive share governance before fan-out in newly generated templates`, () => {
    const input = spec([relational, 'HIVE']);
    const before = JSON.stringify(input);
    const flow = buildTemplateFlow(input);
    assert.equal(JSON.stringify(input), before);
    assert.equal(flow.nodes.length, 4);
    assert.equal(flow.edges.length, 3);
    const source = flow.nodes.filter(n => n.category === 'source');
    const governance = flow.nodes.filter(n => n.manifestKey === 'transform.field-enrichment');
    assert.equal(source.length, 1);
    assert.equal(source[0].config.manualBackfill, true);
    assert.equal(governance.length, 1);
    assert.equal(governance[0].config.sharedGovernance, true);
    assert.equal(governance[0].config.useRegisteredMappings, true);
    assert.deepEqual(flow.edges.filter(e => e.source === source[0].id).map(e => e.target), [governance[0].id]);
    assert.deepEqual(new Set(flow.edges.filter(e => e.source === governance[0].id).map(e => e.target)),
      new Set(flow.nodes.filter(n => n.category === 'sink').map(n => n.id)));
    const jdbc = flow.nodes.find(n => n.manifestKey === 'sink.jdbc')!;
    const hive = flow.nodes.find(n => n.manifestKey === 'sink.hive')!;
    assert.equal(jdbc.config.insertThenMerge, true);
    assert.equal(jdbc.config.writerType, 'LINEWELL_PUT_DATABASE_RECORD');
    assert.equal(jdbc.config.hiveTimestampFormat, undefined);
    assert.equal(hive.config.hiveTimestampFormat, true);
    assert.equal(hive.config.hiveWriteMode, undefined);
    assert.equal(hive.config.mergeMaxBinAge, '30 sec');
    assert.equal(new Set(flow.edges.map(e => e.id)).size, flow.edges.length);
  });
}

test('shared parallel generation supports target order and more than two targets', () => {
  const flow = buildTemplateFlow(spec(['HIVE', 'ORACLE', 'MYSQL']));
  assert.equal(flow.nodes.length, 5);
  assert.equal(flow.edges.length, 4);
  assert.equal(flow.nodes.filter(n => n.manifestKey === 'transform.field-enrichment').length, 1);
  assert.deepEqual(flow.nodes.filter(n => n.category === 'sink').map(n => n.config.templateBinding),
    ['target:0', 'target:1', 'target:2']);
});

test('single-target and generated serial recipes retain their existing path', () => {
  const single = buildTemplateFlow(spec(['ORACLE']));
  assert.equal(single.nodes.length, 3);
  assert.equal(single.edges.length, 2);
  assert.equal(single.nodes[0].config.manualBackfill, undefined);
  const serial = buildTemplateFlow({ ...spec(['ORACLE', 'HIVE']), engine: 'GENERATED', topology: 'SERIAL' });
  assert.equal(serial.nodes.length, 5);
  assert(serial.edges.some(e => e.source === 'target-0' && e.target === 'governance-1'));
  assert.equal(serial.nodes.find(n => n.manifestKey === 'transform.field-enrichment')!.config.sharedGovernance, undefined);
});

test('explicitly imported historical branch rules are preserved until the user regenerates the flow', () => {
  const branch = buildTemplateFlow({ ...spec(['ORACLE', 'HIVE']), engine: 'GENERATED' });
  branch.nodes[1].config.executionMode = 'LOCAL';
  const imported = parseTemplateFlow({ dsl: branch });
  assert.deepEqual(imported, branch);
  assert.equal(imported.nodes.filter(n => n.manifestKey === 'transform.field-enrichment').length, 2);
});
