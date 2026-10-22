export interface DiffLine {
  before?: string;
  after?: string;
  left?: number;
  right?: number;
  changed: boolean;
}

/** Bound comparison work for unusually large generated scripts. Both versions always remain visible. */
export function compareLines(original: string, modified: string): DiffLine[] {
  const a = original.split("\n"),
    b = modified.split("\n");
  if (a.length * b.length > 1_000_000)
    return Array.from({ length: Math.max(a.length, b.length) }, (_, i) => ({
      before: a[i],
      after: b[i],
      left: i < a.length ? i + 1 : undefined,
      right: i < b.length ? i + 1 : undefined,
      changed: a[i] !== b[i],
    }));
  const width = b.length + 1;
  const table = new Uint32Array((a.length + 1) * width);
  for (let i = a.length - 1; i >= 0; i--)
    for (let j = b.length - 1; j >= 0; j--)
      table[i * width + j] =
        a[i] === b[j]
          ? table[(i + 1) * width + j + 1] + 1
          : Math.max(table[(i + 1) * width + j], table[i * width + j + 1]);
  const lines: DiffLine[] = [];
  let i = 0,
    j = 0;
  while (i < a.length || j < b.length) {
    if (i < a.length && j < b.length && a[i] === b[j]) {
      lines.push({
        before: a[i],
        after: b[j],
        left: i + 1,
        right: j + 1,
        changed: false,
      });
      i++;
      j++;
    } else if (
      i < a.length &&
      (j >= b.length || table[(i + 1) * width + j] >= table[i * width + j + 1])
    ) {
      lines.push({ before: a[i], left: i + 1, changed: true });
      i++;
    } else {
      lines.push({ after: b[j], right: j + 1, changed: true });
      j++;
    }
  }
  return lines;
}
