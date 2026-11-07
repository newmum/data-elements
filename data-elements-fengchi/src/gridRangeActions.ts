import type {Cell, Row, TableDef} from './data';
export type SelectedCellRange = {rowIds: Cell[]; columns: string[]; count: number};

/** TSV preserves spreadsheet-shaped ranges, including quoted tabs/newlines. */
export function rangeAsTsv(table: TableDef, range: SelectedCellRange): string {
  const rows = new Map(table.rows.map(row => [row.id, row]));
  const quote = (value: Cell | undefined) => {
    const text = value === null || value === undefined ? '' : String(value);
    return /[\t\r\n"]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
  };
  return range.rowIds.map(id => range.columns.map(column => quote(rows.get(id)?.[column])).join('\t')).join('\r\n');
}

export function rangeClearBlocker(table: TableDef, range: SelectedCellRange): string | null {
  for (const name of range.columns) {
    const column = table.columns.find(item => item.name === name);
    if (!column || column.primary) return '选区包含主键，不能清空。';
    if (!column.nullable && !/^(?:var)?char\b|^text\b|^(?:tiny|medium|long)text\b/i.test(column.type)) return '选区包含必填的数值或日期字段，请编辑为有效值。';
  }
  return null;
}

export function clearRangeContents(table: TableDef, range: SelectedCellRange): TableDef {
  const blocker = rangeClearBlocker(table, range);
  if (blocker) throw new Error(blocker);
  const ids = new Set(range.rowIds);
  const changes: Row = Object.fromEntries(range.columns.map(name => [name, table.columns.find(column => column.name === name)?.nullable ? null : '']));
  return {...table, rows: table.rows.map(row => ids.has(row.id) ? {...row, ...changes} : row)};
}
