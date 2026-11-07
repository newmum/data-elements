import type {Cell, Row, TableDef} from './data';

/** Compare an editable cell with its committed value, including after a primary-key edit. */
export function isCellChanged(baselineTable: TableDef | undefined, row: Row, columnName: string, originalId: Cell = row.id): boolean {
  if (!Object.prototype.hasOwnProperty.call(row, columnName)) return false;
  const originalRow = baselineTable?.rows.find(candidate => Object.is(candidate.id, originalId));
  return !originalRow || !Object.prototype.hasOwnProperty.call(originalRow, columnName) || !Object.is(originalRow[columnName], row[columnName]);
}
