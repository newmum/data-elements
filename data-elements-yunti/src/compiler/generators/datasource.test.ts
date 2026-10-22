import { describe, it, expect } from 'vitest';
import { compile } from '../compile';
import { makeFlow, n, e } from '../test-utils';
import type { FlowNode } from '@/domain/flow-dsl';

function single(dsNode: FlowNode): string {
  const flow = makeFlow(
    [
      n('t', 'trigger', { path: '/x' }),
      dsNode,
      n('r', 'response', { mode: 'var', source: 'out' }),
    ],
    [e('e0', 't', dsNode.id), e('e1', dsNode.id, 'r')],
  );
  const r = compile(flow);
  expect(r.ok, JSON.stringify(r.problems)).toBe(true);
  return r.script;
}

describe('datasource generators', () => {
  it('sql nodes resolve platform datasource IDs at runtime', () => {
    const s = single(
      n('x', 'sql-query', {
        datasource: 'registered_ds_id',
        sql: 'select 1',
        outputVar: 'out',
      }),
    );
    expect(s).toContain("db.datasource(flowDataSource.ensureRuntime('registered_ds_id'))");
  });

  it('kafka-consume emits kafka.consume with options', () => {
    const s = single(
      n('x', 'kafka-consume', {
        datasource: 'order_kafka',
        topic: 'order-events',
        maxRecords: 100,
        commit: false,
        outputVar: 'out',
      }),
    );
    expect(s).toContain("kafka.consume('order_kafka'");
    expect(s).toContain('topic: "order-events"');
    expect(s).toContain('maxRecords: 100');
  });

  it('kafka-produce emits kafka.send', () => {
    const s = single(
      n('x', 'kafka-produce', {
        datasource: 'k',
        topic: 't',
        value: 'payload',
        outputVar: 'out',
      }),
    );
    expect(s).toContain("kafka.send('k', \"t\"");
  });

  it('es-query (dsl mode) emits es.search', () => {
    const s = single(
      n('x', 'es-query', {
        datasource: 'log_es',
        index: 'order-*',
        mode: 'dsl',
        dsl: '{ "query": { "term": { "user_id": 1 } } }',
        outputVar: 'out',
      }),
    );
    expect(s).toContain("flowEs.search('log_es', 'order-*'");
    expect(s).toContain('term');
  });

  it('es-query (builder mode) builds a bool query', () => {
    const s = single(
      n('x', 'es-query', {
        datasource: 'log_es',
        index: 'order',
        mode: 'builder',
        builder: { conditions: [{ field: 'status', op: 'match', value: 'PAID' }] },
        outputVar: 'out',
      }),
    );
    expect(s).toContain('bool');
    expect(s).toContain('match');
  });

  it('http-call emits http.connect chain', () => {
    const s = single(
      n('x', 'http-call', {
        url: 'https://api.test/orders',
        method: 'GET',
        outputVar: 'out',
      }),
    );
    expect(s).toContain('http.connect("https://api.test/orders")');
    expect(s).toContain('.get()');
  });

  it('redis emits redis.get', () => {
    const s = single(
      n('x', 'redis', { datasource: 'cache', op: 'get', key: 'u:1', outputVar: 'out' }),
    );
    expect(s).toContain('flowRedis.op("cache", "get", "u:1"');
  });
});
