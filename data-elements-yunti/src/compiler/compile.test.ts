import { describe, it, expect } from 'vitest';
import { compile } from './compile';
import { makeFlow, n, e, orderSummaryFlow } from './test-utils';

describe('compile', () => {
  it('compiles the order-summary flow into 服务脚本', () => {
    const r = compile(orderSummaryFlow());
    expect(r.ok).toBe(true);
    expect(r.script).toContain("db.datasource(flowDataSource.ensureRuntime('order_db'))");
    expect(r.script).toContain('#{userId}');
    expect(r.script).toContain('var orders =');
    expect(r.script).toContain('return { code: 0');
    // header present
    expect(r.script).toContain('FlowServe 自动生成');
  });

  it('does not emit a script when there are errors', () => {
    // sql-query missing datasource & sql => errors
    const flow = makeFlow(
      [
        n('t', 'trigger', { path: '/x' }),
        n('q', 'sql-query', { datasource: '', sql: '' }),
        n('r', 'response', { mode: 'wrap', source: 'q' }),
      ],
      [e('e0', 't', 'q'), e('e1', 'q', 'r')],
    );
    const r = compile(flow);
    expect(r.ok).toBe(false);
    expect(r.script).toBe('');
    expect(r.problems.some((p) => p.severity === 'error')).toBe(true);
  });

  it('emits trigger parameter docs', () => {
    const r = compile(orderSummaryFlow());
    expect(r.script).toContain('userId');
    expect(r.script).toContain('请求参数');
  });
});

