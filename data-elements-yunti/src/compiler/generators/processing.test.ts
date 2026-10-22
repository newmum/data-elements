import { describe, it, expect } from 'vitest';
import { compile } from '../compile';
import { makeFlow, n, e } from '../test-utils';
import type { FlowNode } from '@/domain/flow-dsl';

function single(node: FlowNode): string {
  const flow = makeFlow(
    [
      n('t', 'trigger', { path: '/x' }),
      node,
      n('r', 'response', { mode: 'var', source: 'out' }),
    ],
    [e('e0', 't', node.id), e('e1', node.id, 'r')],
  );
  const r = compile(flow);
  expect(r.ok, JSON.stringify(r.problems)).toBe(true);
  return r.script;
}

describe('processing generators', () => {
  it('script wraps code in an IIFE', () => {
    const s = single(n('x', 'script', { code: 'return 1 + 1;', outputVar: 'out' }));
    expect(s).toContain('var out = (() => {');
    expect(s).toContain('return 1 + 1;');
    expect(s).toContain('})();');
  });

  it('transform (list mode) emits a .map with mappings', () => {
    const s = single(
      n('x', 'transform', {
        sourceVar: 'orders',
        listMode: true,
        mappings: [
          { target: 'orderId', expr: 'item.id' },
          { target: 'amount', expr: 'item.total' },
        ],
        outputVar: 'out',
      }),
    );
    expect(s).toContain('orders.map(item =>');
    expect(s).toContain('orderId: item.id');
    expect(s).toContain('amount: item.total');
  });

  it('filter emits .filter', () => {
    const s = single(
      n('x', 'filter', { sourceVar: 'list', predicate: 'item.ok', outputVar: 'out' }),
    );
    expect(s).toContain('list.filter(item => item.ok)');
  });

  it('aggregate emits an aggregate() call with groupBy', () => {
    const s = single(
      n('x', 'aggregate', {
        sourceVar: 'rows',
        groupBy: ['city'],
        aggregations: [{ field: 'amt', op: 'sum', as: 'total' }],
        outputVar: 'out',
      }),
    );
    expect(s).toContain('aggregate(rows,');
    expect(s).toContain('groupBy: ["city"]');
  });

  it('sort emits a comparator', () => {
    const s = single(
      n('x', 'sort', {
        sourceVar: 'list',
        sortBy: [{ field: 'age', order: 'desc' }],
        outputVar: 'out',
      }),
    );
    expect(s).toContain('list.sort((a, b) =>');
    expect(s).toContain('a.age');
  });

  it('paginate emits total + list slice', () => {
    const s = single(
      n('x', 'paginate', { sourceVar: 'list', page: '1', size: '10', outputVar: 'out' }),
    );
    expect(s).toContain('total: list.size()');
    expect(s).toContain('limit(__size)');
  });

  it('join emits an index + merge loop', () => {
    const s = single(
      n('x', 'join', {
        leftVar: 'orders',
        rightVar: 'users',
        leftKey: 'user_id',
        rightKey: 'id',
        type: 'left',
        outputVar: 'out',
      }),
    );
    expect(s).toContain('var out = (() => {');
    expect(s).toContain('__right[__r.id] = __r;');
    expect(s).toContain('__right[__l.user_id]');
  });
});
