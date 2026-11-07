import type { Cell, Row, TableDef } from './data';
import { gridRowKey } from './gridRangeSelection';

export type TableSort = { field: string; order: 'ascend' | 'descend' } | null;
export type ActionSelection = { source: 'checked' | 'cells' | 'form' | 'none'; rows: Row[] };

/** Only explicit row checks, a cell range, or the displayed form record are actionable. */
export function selectedActionRows(rows: readonly Row[], checked: readonly (string | number | bigint)[], cellRows: readonly Cell[], formId?: Cell): ActionSelection {
  const source = checked.length ? 'checked' : cellRows.length ? 'cells' : formId !== undefined ? 'form' : 'none';
  const ids: readonly Cell[] = source === 'checked' ? checked.filter((id): id is string | number => typeof id === 'string' || typeof id === 'number') : source === 'cells' ? cellRows : source === 'form' ? [formId!] : [];
  const wanted = new Set(ids.map(gridRowKey));
  return { source, rows: rows.filter(row => wanted.has(gridRowKey(row.id))) };
}

/** Immutable deletion of exact IDs. Missing IDs do not broaden the operation. */
export function withoutRows(table: TableDef, ids: readonly Cell[]): TableDef {
  const wanted = new Set(ids.map(gridRowKey));
  const rows = table.rows.filter(row => !wanted.has(gridRowKey(row.id)));
  return rows.length === table.rows.length ? table : { ...table, rows };
}

export function sortTableRows(rows: readonly Row[], sort: TableSort): Row[] {
  if (!sort) return [...rows];
  return [...rows].sort((a, b) => {
    const x = a[sort.field], y = b[sort.field];
    const result = typeof x === 'number' && typeof y === 'number' ? x - y : String(x ?? '').localeCompare(String(y ?? ''));
    return sort.order === 'ascend' ? result : -result;
  });
}
