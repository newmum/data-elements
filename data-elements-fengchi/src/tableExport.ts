import type { Cell, Row } from './data';

export type ExportFormat = 'csv' | 'tsv' | 'json';
export type ExportQuantity = 'all' | string | number;

/** Sources are already authorized, loaded rows in the order shown to the user. */
export interface ExportSource {
  key: string;
  label: string;
  rows: readonly Row[];
}

export interface PreparedExport {
  filename: string;
  mime: string;
  content: string;
  count: number;
  scope: string;
  format: ExportFormat;
}

export interface ExportSnapshot {
  title: string;
  columns: readonly string[];
  sources: readonly ExportSource[];
}

/** Cell values are primitive, so shallow row copies isolate the whole snapshot. */
export function createExportSnapshot(snapshot: ExportSnapshot): ExportSnapshot {
  return {
    title: snapshot.title,
    columns: [...snapshot.columns],
    sources: snapshot.sources.map(source => ({ ...source, rows: source.rows.map(row => ({ ...row })) })),
  };
}

export function validateExportCount(quantity: ExportQuantity, available: number):
  { ok: true; count: number } | { ok: false; error: string } {
  if (!Number.isSafeInteger(available) || available < 1) {
    return { ok: false, error: '此范围内没有可导出的记录。' };
  }
  if (quantity === 'all') return { ok: true, count: available };
  if (typeof quantity === 'string' && !/^\d+$/.test(quantity)) {
    return { ok: false, error: `请输入 1 到 ${available} 之间的整数。` };
  }
  const count = Number(quantity);
  if (!Number.isSafeInteger(count) || count < 1 || count > available) {
    return { ok: false, error: `请输入 1 到 ${available} 之间的整数。` };
  }
  return { ok: true, count };
}

export function buildExportFilename(title: string, format: ExportFormat): string {
  // Keep readable Chinese names, but remove path separators, control characters,
  // reserved Windows names and trailing dots/spaces from browser download names.
  let stem = title.replace(/[\u0000-\u001f\u007f<>:"/\\|?*]/g, '_').trim()
    .replace(/^[. ]+|[. ]+$/g, '').slice(0, 100).replace(/[. ]+$/g, '');
  if (!stem) stem = 'data';
  if (/^(?:con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)/i.test(stem)) stem = `_${stem}`;
  return `${stem}.${format}`;
}

function delimitedCell(value: Cell | undefined, delimiter: string): string {
  if (typeof value === 'number' && !Number.isFinite(value)) throw new Error('数据包含无效数字，无法导出。');
  let text = value == null ? '' : String(value);
  // Spreadsheet apps may execute text formulas even when a CSV field is quoted.
  // Prefix risky strings (including whitespace-prefixed formulas), never numbers.
  if (typeof value === 'string' && /^(?:[\t\r\n]|\s*[=+\-@])/.test(text)) text = `'${text}`;
  return text.includes(delimiter) || /["\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

function rowCell(row: Row, column: string): Cell {
  // A missing field named "constructor" or "__proto__" must not read from the
  // object prototype. Missing columns follow the same NULL policy as real NULL.
  return Object.prototype.hasOwnProperty.call(row, column) ? row[column] ?? null : null;
}

/** Pure serialization. Downloading is a separate, explicit confirmation action. */
export function prepareTableExport({ title, columns, source, format, quantity = 'all' }: {
  title: string;
  columns: readonly string[];
  source: ExportSource;
  format: ExportFormat;
  quantity?: ExportQuantity;
}): PreparedExport {
  const selection = validateExportCount(quantity, source.rows.length);
  if (!selection.ok) throw new Error(selection.error);
  if (!columns.length) throw new Error('没有可导出的字段。');
  const rows = source.rows.slice(0, selection.count);
  let content: string;
  let mime: string;
  if (format === 'json') {
    const records = rows.map(row => Object.fromEntries(columns.map(column => {
      const value = rowCell(row, column);
      if (typeof value === 'number' && !Number.isFinite(value)) throw new Error('数据包含无效数字，无法导出。');
      return [column, value];
    })));
    content = `${JSON.stringify(records, null, 2)}\n`;
    mime = 'application/json;charset=utf-8';
  } else if (format === 'csv' || format === 'tsv') {
    const delimiter = format === 'csv' ? ',' : '\t';
    const lines = [columns.map(column => delimitedCell(column, delimiter)).join(delimiter)];
    for (const row of rows) lines.push(columns.map(column => delimitedCell(rowCell(row, column), delimiter)).join(delimiter));
    // UTF-8 BOM makes Chinese text work when opened directly in spreadsheet apps.
    content = `\uFEFF${lines.join('\r\n')}\r\n`;
    mime = format === 'csv' ? 'text/csv;charset=utf-8' : 'text/tab-separated-values;charset=utf-8';
  } else {
    throw new Error('不支持的导出格式。');
  }
  return { filename: buildExportFilename(title, format), mime, content, count: selection.count, scope: source.key, format };
}
