import test from 'node:test';
import assert from 'node:assert/strict';
import { columnLabels, columnsForMapping, mappingColumnNodes, missingMappingColumnProbes } from './fieldMappingColumns.ts';
import { findUniqueCaseInsensitiveFieldMatches } from '../utils/fieldNameMatching.ts';

const source = (config) => ({ id: 'source', category: 'source', manifestKey: 'source.mysql', config });
const target = (config) => ({ id: 'target', category: 'sink', manifestKey: 'sink.jdbc', config });
const fields = (columns) => Object.entries(columnLabels(columns)).map(([name, label]) => ({ name, label, path: `/${name}` }));

test('columns without comments still participate in both directions of case-insensitive matching', () => {
  for (const [left, right] of [['CASE_ID', 'case_id'], ['case_id', 'CASE_ID']]) {
    const matches = findUniqueCaseInsensitiveFieldMatches(
      fields([{ columnName: left }]), fields([{ columnName: right, columnComment: '' }]));
    assert.equal(matches.length, 1);
    assert.equal(matches[0].source.path, `/${left}`);
    assert.equal(matches[0].target.path, `/${right}`);
    assert.equal(matches[0].differsOnlyByCase, true);
  }
});

test('different comments do not prevent a physical identifier match', () => {
  const matches = findUniqueCaseInsensitiveFieldMatches(
    fields([{ columnName: 'LOG_ID', columnComment: '来源日志主键' }]),
    fields([{ columnName: 'log_id', columnComment: '日志编号' }]));
  assert.equal(matches.length, 1);
});

test('blank columns are excluded, missing comments are retained, spelling stays unchanged', () => {
  assert.deepEqual(columnLabels([{ columnName: 'LOG_ID' }, { columnName: 'client_ip', columnComment: null },
    { columnName: 'case_id', columnComment: '案件编号' }, { columnName: '', columnComment: '无效列' }]),
  { LOG_ID: '', client_ip: '', case_id: '案件编号' });
});

test('legacy saved task with zero source columns schedules a probe for its selected table', () => {
  const nodes = { source: source({ table: 'log_case_access', sourceColumns: [] }),
    target: target({ table: 'ODS_log_case_access', targetColumns: [{ columnName: 'CASE_ID' }] }) };
  const context = mappingColumnNodes('mapping', nodes, {
    input: { source: 'source', target: 'mapping' }, output: { source: 'mapping', target: 'target' },
  });
  const probes = missingMappingColumnProbes(context);
  assert.equal(probes.length, 1);
  assert.equal(probes[0].nodeId, 'source');
  assert.equal(probes[0].table, 'log_case_access');
  assert.equal(probes[0].manifestKey, 'source.mysql');
  nodes.source.config.sourceColumns = [{ columnName: 'case_id' }];
  assert.deepEqual(missingMappingColumnProbes(context), []);
});

test('table-specific cached fields work without reopening or probing the source node', () => {
  const node = source({ table: 'events', sourceColumns: [], sourceColumnsByTable: { EVENTS: [{ columnName: 'ID' }] } });
  assert.deepEqual(columnLabels(columnsForMapping(node, 'source')), { ID: '' });
  assert.deepEqual(missingMappingColumnProbes({ source: node }), []);
});

test('a SQL output is not replaced with raw table fields or sent for table probing', () => {
  const node = { id: 'sql', category: 'transform', manifestKey: 'transform.sql', config: {
    outputColumns: [{ columnName: 'TOTAL' }], sourceColumns: [{ columnName: 'RAW_AMOUNT' }],
  } };
  assert.deepEqual(columnLabels(columnsForMapping(node, 'source')), { TOTAL: '' });
  assert.deepEqual(missingMappingColumnProbes({ source: node }), []);
});

test('ambiguous case variants require manual selection', () => {
  assert.deepEqual(findUniqueCaseInsensitiveFieldMatches(fields([{ columnName: 'ID' }, { columnName: 'id' }]),
    fields([{ columnName: 'Id' }])), []);
});
