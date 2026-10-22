/** A simple indentation-aware line emitter for generating readable 服务脚本. */
export class CodeBuffer {
  private lines: string[] = [];
  private depth = 0;
  private readonly unit: string;

  constructor(indentUnit = '    ') {
    this.unit = indentUnit;
  }

  indent(): this {
    this.depth++;
    return this;
  }

  dedent(): this {
    this.depth = Math.max(0, this.depth - 1);
    return this;
  }

  /** Emit a line (or several, splitting on \n) at the current indent. */
  line(text = ''): this {
    if (text === '') {
      this.lines.push('');
      return this;
    }
    for (const raw of text.split('\n')) {
      this.lines.push(raw.length ? this.unit.repeat(this.depth) + raw : '');
    }
    return this;
  }

  blank(): this {
    this.lines.push('');
    return this;
  }

  comment(text: string): this {
    for (const raw of text.split('\n')) {
      this.line(`// ${raw}`);
    }
    return this;
  }

  /** Run a callback inside one extra indent level (e.g. a block body). */
  block(fn: () => void): this {
    this.indent();
    fn();
    this.dedent();
    return this;
  }

  toString(): string {
    return this.lines.join('\n');
  }
}

