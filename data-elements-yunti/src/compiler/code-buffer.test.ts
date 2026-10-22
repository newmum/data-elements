import { describe, it, expect } from 'vitest';
import { CodeBuffer } from './code-buffer';

describe('CodeBuffer', () => {
  it('indents and dedents', () => {
    const b = new CodeBuffer('  ');
    b.line('a').indent().line('b').dedent().line('c');
    expect(b.toString()).toBe('a\n  b\nc');
  });

  it('block() wraps a callback in one indent level', () => {
    const b = new CodeBuffer('  ');
    b.line('if (x) {');
    b.block(() => b.line('doThing();'));
    b.line('}');
    expect(b.toString()).toBe('if (x) {\n  doThing();\n}');
  });

  it('comment() prefixes //', () => {
    const b = new CodeBuffer();
    b.comment('hello');
    expect(b.toString()).toBe('// hello');
  });
});
