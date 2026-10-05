import test from 'node:test';
import assert from 'node:assert/strict';
import { registeredDatasourceId, resolveDatasourceId } from '../api/datasourceIdentity.ts';
import { upstreamTableSources } from './materializeSources.ts';

test('legacy IDs are recognized before lists load and explicit selection wins', () => {
  assert.equal(registeredDatasourceId({ targetDbId: 'old-target' }), 'old-target');
  assert.equal(registeredDatasourceId({ sourceDbId: 'old-source' }), 'old-source');
  assert.equal(resolveDatasourceId({ selectedDatabaseId: 'new', targetDbId: 'old' }, []), 'new');
});
test('connection fallback requires one matching endpoint and account', () => {
  const config = { jdbcUrl: 'jdbc:mysql://host:3306/db', username: 'app' };
  const item = { value: 'one', jdbcURL: config.jdbcUrl, username: 'app' };
  assert.equal(resolveDatasourceId(config, [item]), 'one');
  assert.equal(resolveDatasourceId(config, [item, { ...item, value: 'two' }]), '');
  assert.equal(resolveDatasourceId(config, [{ ...item, username: 'other' }]), '');
});
test('sources follow upstream edges through transforms, avoid cycles and unrelated nodes', () => {
  const nodes = {
    target: { id: 'target', category: 'sink', config: {} },
    map: { id: 'map', category: 'transform', config: {} },
    source: { id: 'source', category: 'source', label: '来源库', manifestKey: 'source.hive', config: { tables: 'events,logs', sourceTableId: 'registered-events' } },
    other: { id: 'other', category: 'source', config: { table: 'unrelated' } },
  };
  const edges = { a: { source: 'map', target: 'target' }, b: { source: 'source', target: 'map' }, c: { source: 'target', target: 'map' } };
  const result = upstreamTableSources('target', nodes, edges);
  assert.deepEqual(result.map((source) => source.table), ['events', 'logs']);
  assert.equal(result[0].manifestKey, 'source.hive');
  assert.equal(result[0].sourceTableId, 'registered-events');
});
