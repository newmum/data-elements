import test from 'node:test';
import assert from 'node:assert/strict';
import { columnLabels, columnsForMapping, mappingColumnNodes, missingMappingColumnProbes } from './fieldMappingColumns.ts';
import { findUniqueCaseInsensitiveFieldMatches } from '../utils/fieldNameMatching.ts';
import { mappingTableReference, mappingTableSelectionPatch } from './fieldMappingColumns.ts';
import fs from 'node:fs';

const manifests = fs.readdirSync(new URL('../../../../data-elements-parent/src/main/resources/manifests/sources/', import.meta.url))
  .filter(name => name.endsWith('.json')).map(name => JSON.parse(fs.readFileSync(new URL(`../../../../data-elements-parent/src/main/resources/manifests/sources/${name}`, import.meta.url), 'utf8').replace(/^\uFEFF/, '')));
for (const name of ['jdbc-sink.json', 'hive-sink.json', 'kafka.json']) manifests.push(JSON.parse(fs.readFileSync(new URL(`../../../../data-elements-parent/src/main/resources/manifests/sinks/${name}`, import.meta.url), 'utf8').replace(/^\uFEFF/, '')));

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

test('file and protocol datasets never become JDBC schema references even with old database aliases', () => {
  for (const manifestKey of ['source.sftp', 'source.ftp', 'source.api', 'source.kafka', 'source.minio', 'source.hdfs', 'source.hbase', 'source.elasticsearch']) {
    const node = { ...source({ table: 'events', sourceTableId: 'old-table', registeredDatasourceId: 'other-source',
      sourceColumns: [{ columnName: 'ID' }] }), manifestKey };
    assert.equal(mappingTableReference(node, 'source', [], manifests), undefined, manifestKey);
    assert.deepEqual(columnLabels(columnsForMapping(node, 'source')), { ID: '' });
    assert.equal(node.config.sourceTableId, 'old-table', 'Reading the editor never rewrites the saved source');
  }
  assert.equal(mappingTableReference({ ...target({ table: 'events', targetDbId: 'db' }), manifestKey: 'sink.kafka' }, 'target', [], manifests), undefined);
});

test('database contracts use the registered table ID and datasource together, with a legacy name fallback', () => {
  for (const manifest of manifests.filter(item => item.compile?.controllerServices?.some(service => service.type === 'org.apache.nifi.dbcp.DBCPConnectionPool'))) {
    const side = manifest.category === 'source' ? 'source' : 'target';
    const node = { id: 'database', category: manifest.category, manifestKey: manifest.key,
      config: { table: 'owner.events', registeredDatasourceId: 'db', [side === 'source' ? 'sourceTableId' : 'targetTableId']: 'table-id' } };
    assert.deepEqual(mappingTableReference(node, side, [], manifests), { datasourceId: 'db', table: 'owner.events', tableId: 'table-id' }, manifest.key);
    delete node.config[side === 'source' ? 'sourceTableId' : 'targetTableId'];
    assert.deepEqual(mappingTableReference(node, side, [], manifests), { datasourceId: 'db', table: 'owner.events' });
    node.config.table = '';
    assert.equal(mappingTableReference(node, side, [], manifests), undefined);
  }
  assert.equal(mappingTableReference(source({ table: 'events' }), 'source', [], manifests), undefined);
  assert.equal(mappingTableReference({ ...source({ table: 'events', sourceDbId: 'db' }), category: 'transform' }, 'source', [], manifests), undefined);
});

test('changing a table clears its old registered ID; reading the same table preserves its binding', () => {
  for (const [node, key] of [[source({ table: 'old', sourceTableId: 'old-id' }), 'sourceTableId'],
    [target({ table: 'old', targetTableId: 'old-id' }), 'targetTableId']]) {
    assert.deepEqual(mappingTableSelectionPatch(node, 'old'), { table: 'old' });
    const changed = { ...node, config: { ...node.config, ...mappingTableSelectionPatch(node, 'new') } };
    assert.equal(changed.config[key], undefined);
    assert.equal(node.config[key], 'old-id');
  }
});

test('one shared governance discovers JDBC and Hive targets through independent transport adapters', () => {
  const nodes = {
    source: source({ table: 'events', sourceColumns: [{ columnName: 'ID' }] }),
    backfill: { ...source({ table: 'events', sourceColumns: [{ columnName: 'ID' }] }), id: 'backfill' },
    hiveTime: { id: 'hiveTime', category: 'transform', manifestKey: 'transform.timestamp', config: {} },
    merge: { id: 'merge', category: 'transform', manifestKey: 'transform.merge', config: {} },
    hive: { ...target({ table: 'ods_events', targetColumns: [{ columnName: 'id' }] }), id: 'hive', manifestKey: 'sink.hive' },
    oracle: { ...target({ table: 'ODS_EVENTS', targetColumns: [{ columnName: 'ID' }] }), id: 'oracle' },
    failure: { ...target({ table: 'ERRORS' }), id: 'failure' },
  };
  const edges = Object.fromEntries([
    ['source', 'mapping'], ['backfill', 'mapping'], ['mapping', 'hiveTime'], ['hiveTime', 'merge'],
    ['merge', 'hive'], ['mapping', 'oracle'], ['hiveTime', 'hiveTime', 'failure'], ['mapping', 'failure', 'retry'],
  ].map(([source, target, outlet], index) => [index, { source, target, outlet }]));
  const context = mappingColumnNodes('mapping', nodes, edges);
  assert.deepEqual(context.sources.map(node => node.id), ['source', 'backfill']);
  assert.deepEqual(context.targets.map(node => node.id), ['oracle', 'hive']);
  assert.equal(context.target.id, 'oracle', 'Hive edge order must not change the canonical logical output');
  assert.deepEqual(missingMappingColumnProbes(context), []);
});

test('downstream governance owns a separate schema and graph cycles terminate', () => {
  const nodes = {
    next: { id: 'next', manifestKey: 'transform.field-enrichment', category: 'transform', config: {} },
    adapter: { id: 'adapter', manifestKey: 'transform.timestamp', category: 'transform', config: {} },
    target: target({ table: 'separate_contract' }),
  };
  const edges = {
    first: { source: 'mapping', target: 'adapter' },
    cycle: { source: 'adapter', target: 'mapping' },
    separate: { source: 'adapter', target: 'next' },
    output: { source: 'next', target: 'target' },
  };
  assert.deepEqual(mappingColumnNodes('mapping', nodes, edges).targets, []);
});

test('SQL and external scripts form schema boundaries instead of borrowing renamed sink fields', () => {
  for (const manifestKey of ['transform.sql', 'transform.java', 'transform.python', 'transform.shell']) {
    const nodes = { source: source({ table: 'events', sourceColumns: [{ columnName: 'ID' }] }),
      transform: { id: 'transform', manifestKey, category: 'transform', config: { outputColumns: [{ columnName: 'ACCOUNT_ID' }] } },
      target: target({ table: 'events_ods', targetColumns: [{ columnName: 'ID' }] }),
      hive: { ...target({ table: 'accounts', targetColumns: [{ columnName: 'ACCOUNT_ID' }] }), id: 'hive', manifestKey: 'sink.hive' } };
    const edges = { input: { source: 'source', target: 'mapping' }, direct: { source: 'mapping', target: 'target' },
      transformed: { source: 'mapping', target: 'transform' }, output: { source: 'transform', target: 'hive' } };
    assert.deepEqual(mappingColumnNodes('mapping', nodes, edges).targets.map(node => node.id), ['target'], manifestKey);
    const upstream = mappingColumnNodes('nextMapping', nodes, { input: { source: 'source', target: 'transform' }, output: { source: 'transform', target: 'nextMapping' } });
    assert.equal(upstream.source.id, 'transform');
    assert.equal(columnsForMapping(upstream.source, 'source')[0].columnName, 'ACCOUNT_ID');
    delete nodes.transform.config.outputColumns;
    const unknown = mappingColumnNodes('nextMapping', nodes, { input: { source: 'source', target: 'transform' }, output: { source: 'transform', target: 'nextMapping' } });
    assert.equal(unknown.source.id, 'transform', 'Unknown script output must not be replaced by raw source ID');
    assert.deepEqual(missingMappingColumnProbes(unknown), []);
  }
});

test('20 and 100 target fields remain local graph work and preserve per-target physical identity', () => {
  for (const count of [20, 100]) {
    const columns = Array.from({ length: count }, (_, index) => ({ columnName: `FIELD_${index}` }));
    const nodes = { source: source({ table: 'events', sourceColumns: columns }),
      target: target({ table: 'events_ods', targetColumns: columns }),
      hive: { ...target({ table: 'events_hive', targetColumns: columns.map(column => ({ columnName: column.columnName.toLowerCase() })) }), id: 'hive', manifestKey: 'sink.hive' } };
    const context = mappingColumnNodes('mapping', nodes, { input: { source: 'source', target: 'mapping' },
      ob: { source: 'mapping', target: 'target' }, hive: { source: 'mapping', target: 'hive' } });
    assert.equal(context.targets.length, 2);
    assert.equal(columnsForMapping(context.targets[0], 'target').length, count);
    assert.equal(columnsForMapping(context.targets[1], 'target')[0].columnName, 'field_0');
    assert.deepEqual(missingMappingColumnProbes(context), []);
  }
});
