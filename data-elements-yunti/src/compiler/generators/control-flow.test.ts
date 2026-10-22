import { describe, it, expect } from 'vitest';
import { compile } from '../compile';
import { makeFlow, n, e } from '../test-utils';

const resp = () => n('r', 'response', { mode: 'wrap', wrap: true, source: '' });

function count(haystack: string, needle: string): number {
  return haystack.split(needle).length - 1;
}

describe('control-flow generators', () => {
  it('condition emits if/else with both branches, no double-emit', () => {
    const flow = makeFlow(
      [
        n('t', 'trigger', { path: '/x' }),
        n('c', 'condition', { expr: 'userId > 0' }),
        n('a', 'set-var', { name: 'msgA', expr: "'A'" }),
        n('b', 'set-var', { name: 'msgB', expr: "'B'" }),
        resp(),
      ],
      [
        e('e0', 't', 'c'),
        e('e1', 'c', 'a', 'true'),
        e('e2', 'c', 'b', 'false'),
        e('e3', 'a', 'r'),
        e('e4', 'b', 'r'),
      ],
    );
    const r = compile(flow);
    expect(r.ok, JSON.stringify(r.problems)).toBe(true);
    expect(r.script).toContain('if (userId > 0) {');
    expect(r.script).toContain('var msgA = ');
    expect(r.script).toContain('} else {');
    expect(r.script).toContain('var msgB = ');
    expect(count(r.script, 'var msgA =')).toBe(1);
    // response emitted after the if/else
    expect(r.script.indexOf('return')).toBeGreaterThan(r.script.indexOf('var msgB ='));
  });

  it('foreach emits a for-loop body before the continuation', () => {
    const flow = makeFlow(
      [
        n('t', 'trigger', { path: '/x' }),
        n('fe', 'foreach', { listVar: 'items', itemVar: 'it', collectVar: 'acc' }),
        n('lg', 'log', { level: 'info', message: 'in loop' }),
        resp(),
      ],
      [e('e0', 't', 'fe'), e('e1', 'fe', 'lg', 'body'), e('e2', 'fe', 'r', 'out')],
    );
    const r = compile(flow);
    expect(r.ok, JSON.stringify(r.problems)).toBe(true);
    expect(r.script).toContain('var acc = [];');
    expect(r.script).toContain('for (var it in items) {');
    expect(r.script).toContain('log.info("in loop");');
    expect(r.script.indexOf('log.info')).toBeLessThan(r.script.indexOf('return'));
  });

  it('try-catch wraps protected + catch branches', () => {
    const flow = makeFlow(
      [
        n('t', 'trigger', { path: '/x' }),
        n('tc', 'try-catch', { errorVar: 'err' }),
        n('p', 'set-var', { name: 'okv', expr: '1' }),
        n('c', 'set-var', { name: 'errv', expr: '0' }),
        resp(),
      ],
      [
        e('e0', 't', 'tc'),
        e('e1', 'tc', 'p', 'out'),
        e('e2', 'tc', 'c', 'error'),
        e('e3', 'p', 'r'),
        e('e4', 'c', 'r'),
      ],
    );
    const r = compile(flow);
    expect(r.ok, JSON.stringify(r.problems)).toBe(true);
    expect(r.script).toContain('try {');
    expect(r.script).toContain('var okv = 1;');
    expect(r.script).toContain('} catch (err) {');
    expect(r.script).toContain('var errv = 0;');
  });

  it('switch emits chained if/else-if/else', () => {
    const flow = makeFlow(
      [
        n('t', 'trigger', { path: '/x' }),
        n('sw', 'switch', {
          expr: 'status',
          cases: [
            { value: 'A', port: 'caseA' },
            { value: 'B', port: 'caseB' },
          ],
        }),
        n('a', 'set-var', { name: 'va', expr: '1' }),
        n('b', 'set-var', { name: 'vb', expr: '2' }),
        n('d', 'set-var', { name: 'vd', expr: '3' }),
        resp(),
      ],
      [
        e('e0', 't', 'sw'),
        e('e1', 'sw', 'a', 'caseA'),
        e('e2', 'sw', 'b', 'caseB'),
        e('e3', 'sw', 'd', 'default'),
        e('e4', 'a', 'r'),
        e('e5', 'b', 'r'),
        e('e6', 'd', 'r'),
      ],
    );
    const r = compile(flow);
    expect(r.ok, JSON.stringify(r.problems)).toBe(true);
    expect(r.script).toContain('if (status == "A") {');
    expect(r.script).toContain('} else if (status == "B") {');
    expect(r.script).toContain('} else {');
  });

  it('validate emits guard early-returns', () => {
    const flow = makeFlow(
      [
        n('t', 'trigger', { path: '/x' }),
        n('v', 'validate', {
          rules: [{ field: 'userId', rule: 'required', message: 'userId 不能为空' }],
          errorCode: 400,
        }),
        resp(),
      ],
      [e('e0', 't', 'v'), e('e1', 'v', 'r')],
    );
    const r = compile(flow);
    expect(r.ok, JSON.stringify(r.problems)).toBe(true);
    expect(r.script).toContain("if (userId == null || userId == '') {");
    expect(r.script).toContain('return response.json(400,');
  });
});
