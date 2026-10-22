import { describe, it, expect } from 'vitest';
import type { Flow, FlowEdge, FlowNode } from './flow-dsl';
import { validateFlow, hasErrors } from './validators';

function node(id: string, type: string, data: Record<string, unknown> = {}): FlowNode {
  return { id, type, position: { x: 0, y: 0 }, data };
}
function edge(id: string, source: string, target: string, sourcePort = 'out'): FlowEdge {
  return { id, source, target, sourcePort, targetPort: 'in' };
}
function flow(nodes: FlowNode[], edges: FlowEdge[]): Flow {
  return {
    flowId: 'fl_test',
    name: 'test',
    description: '',
    version: 1,
    group: 'g',
    nodes,
    edges,
    settings: { timeoutMs: 1000, transactional: false, errorStrategy: 'fail-fast' },
  };
}

const validTrigger = () => node('t', 'trigger', { path: '/x', method: 'GET' });
const validResponse = () => node('r', 'response', { mode: 'wrap' });

function messages(problems: ReturnType<typeof validateFlow>): string[] {
  return problems.map((p) => p.message);
}

describe('validateFlow', () => {
  it('flags a missing trigger', () => {
    const f = flow([validResponse()], []);
    const p = validateFlow(f);
    expect(messages(p).some((m) => m.includes('触发器'))).toBe(true);
    expect(hasErrors(p)).toBe(true);
  });

  it('flags two triggers', () => {
    const f = flow(
      [node('t1', 'trigger', { path: '/a' }), node('t2', 'trigger', { path: '/b' }), validResponse()],
      [edge('e1', 't1', 'r')],
    );
    expect(messages(validateFlow(f)).some((m) => m.includes('只能有一个'))).toBe(true);
  });

  it('flags a missing response', () => {
    const f = flow([validTrigger()], []);
    expect(messages(validateFlow(f)).some((m) => m.includes('响应节点'))).toBe(true);
  });

  it('flags a self-loop edge', () => {
    const s = node('s', 'set-var', { name: 'x', expr: '1' });
    const f = flow([validTrigger(), s, validResponse()], [
      edge('e0', 't', 's'),
      edge('e1', 's', 's'),
      edge('e2', 's', 'r'),
    ]);
    const p = validateFlow(f);
    expect(messages(p).some((m) => m.includes('自环'))).toBe(true);
  });

  it('flags a cycle', () => {
    const a = node('a', 'set-var', { name: 'a', expr: '1' });
    const b = node('b', 'set-var', { name: 'b', expr: '2' });
    const f = flow([validTrigger(), a, b, validResponse()], [
      edge('e0', 't', 'a'),
      edge('e1', 'a', 'b'),
      edge('e2', 'b', 'a'),
      edge('e3', 'b', 'r'),
    ]);
    expect(messages(validateFlow(f)).some((m) => m.includes('环'))).toBe(true);
  });

  it('flags duplicate edges', () => {
    const f = flow([validTrigger(), validResponse()], [
      edge('e1', 't', 'r'),
      edge('e2', 't', 'r'),
    ]);
    expect(messages(validateFlow(f)).some((m) => m.includes('重复连线'))).toBe(true);
  });

  it('warns about an orphan node', () => {
    const orphan = node('o', 'set-var', { name: 'o', expr: '1' });
    const f = flow([validTrigger(), validResponse(), orphan], [edge('e1', 't', 'r')]);
    const p = validateFlow(f);
    const orphanProblem = p.find((x) => x.nodeId === 'o');
    expect(orphanProblem?.severity).toBe('warning');
    expect(orphanProblem?.message).toContain('孤立');
  });

  it('errors on missing required config (sql-query without sql/datasource)', () => {
    const sql = node('q', 'sql-query', { datasource: '', sql: '' });
    const f = flow([validTrigger(), sql, validResponse()], [
      edge('e0', 't', 'q'),
      edge('e1', 'q', 'r'),
    ]);
    const p = validateFlow(f);
    const sqlProblems = p.filter((x) => x.nodeId === 'q' && x.severity === 'error');
    expect(sqlProblems.length).toBeGreaterThanOrEqual(2); // datasource + sql
  });

  it('passes a minimal valid flow', () => {
    const f = flow([validTrigger(), validResponse()], [edge('e1', 't', 'r')]);
    expect(hasErrors(validateFlow(f))).toBe(false);
  });
});
