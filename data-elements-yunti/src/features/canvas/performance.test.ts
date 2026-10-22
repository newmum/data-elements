import { describe, expect, it } from 'vitest';
import { computeLayeredLayout } from './layout';
import { compile } from '@/compiler';
import { validateFlow } from '@/domain/validators';
import type { Flow, FlowEdge, FlowNode } from '@/domain/flow-dsl';

function largeFlow(count: number): Flow {
  const nodes: FlowNode[] = [
    {
      id: 'trigger',
      type: 'trigger',
      name: 'API 触发器',
      position: { x: 80, y: 120 },
      data: {
        method: 'GET',
        path: '/perf',
        group: '性能',
        auth: { required: false, roles: [] },
        request: { query: [], path: [], header: [], body: null },
      },
    },
  ];
  const edges: FlowEdge[] = [];
  let previous = 'trigger';
  for (let i = 0; i < count - 2; i++) {
    const id = `node_${i}`;
    nodes.push({
      id,
      type: 'script',
      name: `脚本 ${i}`,
      position: { x: 220 + i * 20, y: 120 + (i % 6) * 90 },
      data: { outputVar: `v${i}`, code: `return ${i};` },
    });
    edges.push({ id: `e_${i}`, source: previous, target: id, sourcePort: 'out', targetPort: 'in' });
    previous = id;
  }
  nodes.push({
    id: 'response',
    type: 'response',
    name: '响应',
    position: { x: 680, y: 120 },
    data: { mode: 'wrap', wrap: true, source: `v${count - 3}`, statusCode: 200 },
  });
  edges.push({ id: 'e_response', source: previous, target: 'response', sourcePort: 'out', targetPort: 'in' });
  return {
    flowId: 'fl_perf',
    name: '性能压测编排',
    description: '',
    version: 1,
    group: '性能',
    nodes,
    edges,
    settings: { timeoutMs: 10000, transactional: false, errorStrategy: 'fail-fast' },
  };
}

describe('canvas/core performance baseline', () => {
  it('handles a 200-node flow in core validation/layout/compile paths', () => {
    const flow = largeFlow(200);
    const started = performance.now();
    const problems = validateFlow(flow);
    const positions = computeLayeredLayout(flow);
    const result = compile(flow);
    const elapsed = performance.now() - started;

    expect(problems.filter((p) => p.severity === 'error')).toHaveLength(0);
    expect(Object.keys(positions)).toHaveLength(200);
    expect(result.ok).toBe(true);
    expect(elapsed).toBeLessThan(1200);
  });
});
