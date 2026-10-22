import type { Flow, FlowEdge, FlowNode } from '@/domain/flow-dsl';

/** Test helpers for building small flows by hand. */
export function n(id: string, type: string, data: Record<string, unknown> = {}): FlowNode {
  return { id, type, name: id, position: { x: 0, y: 0 }, data };
}

export function e(
  id: string,
  source: string,
  target: string,
  sourcePort = 'out',
  targetPort = 'in',
): FlowEdge {
  return { id, source, target, sourcePort, targetPort };
}

export function makeFlow(nodes: FlowNode[], edges: FlowEdge[], name = 'test'): Flow {
  return {
    flowId: 'fl_test',
    name,
    description: '',
    version: 1,
    group: 'g',
    nodes,
    edges,
    settings: { timeoutMs: 10000, transactional: false, errorStrategy: 'fail-fast' },
  };
}

/** trigger(GET /order/summary, userId) -> sql-query(orders) -> transform -> response */
export function orderSummaryFlow(): Flow {
  return makeFlow(
    [
      n('t', 'trigger', {
        method: 'GET',
        path: '/order/summary',
        group: '订单中心',
        request: {
          query: [{ name: 'userId', type: 'long', required: true, desc: '用户ID' }],
          path: [],
          header: [],
          body: null,
        },
      }),
      n('q', 'sql-query', {
        datasource: 'order_db',
        sql: 'SELECT * FROM orders WHERE user_id = #{userId} LIMIT 100',
        outputVar: 'orders',
      }),
      n('tf', 'transform', {
        sourceVar: 'orders',
        listMode: true,
        mappings: [
          { target: 'orderId', expr: 'item.id' },
          { target: 'amount', expr: 'item.total_amount' },
        ],
        outputVar: 'result',
      }),
      n('r', 'response', { mode: 'wrap', wrap: true, source: 'result', statusCode: 200 }),
    ],
    [e('e0', 't', 'q'), e('e1', 'q', 'tf'), e('e2', 'tf', 'r')],
    '订单查询服务',
  );
}
