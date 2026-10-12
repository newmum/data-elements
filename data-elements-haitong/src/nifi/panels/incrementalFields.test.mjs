import test from 'node:test';
import assert from 'node:assert/strict';
import { incrementalConfigurationError } from '../utils/incrementalFields.ts';
const node = (column = '', syncMode = 'FULL_THEN_INCR') => ({ id: 'source', category: 'source', manifestKey: 'source.oracle', label: '来源表', x: 0, y: 0,
  config: { sourceTableId: 'registered', table: 'EVENTS', syncMode, incrementalColumn: column } });
const dsl = nodes => ({ version: 1, nodes, edges: [] });

test('historical missing field stays empty and requires manual entry for incremental save', () => {
  const design = dsl([node()]);
  assert.match(incrementalConfigurationError(design), /主增量字段/);
  assert.equal(design.nodes[0].config.incrementalColumn, '');
  design.nodes[0].config.incrementalColumn = 'MANUAL_VERSION';
  assert.equal(incrementalConfigurationError(design), undefined);
  assert.equal(design.nodes[0].config.incrementalColumn, 'MANUAL_VERSION');
});

test('full sync and non-database sources do not require the incremental field', () => {
  for (const mode of ['FULL', 'PERIODIC_FULL']) assert.equal(incrementalConfigurationError(dsl([node('', mode)])), undefined);
  assert.equal(incrementalConfigurationError(dsl([{ ...node(), manifestKey: 'source.ftp' }])), undefined);
});

test('20 or 100 sources are validated locally and their manual choices remain intact', () => {
  for (const count of [20, 100]) {
    const input = dsl(Array.from({ length: count }, (_, i) => ({ ...node(`MANUAL_${i}`), id: `source_${i}` })));
    const before = structuredClone(input);
    assert.equal(incrementalConfigurationError(input), undefined);
    assert.deepEqual(input, before);
  }
});
