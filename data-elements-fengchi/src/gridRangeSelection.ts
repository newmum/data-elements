import type {Cell} from './data';

export type GridCellAddress = {rowId: Cell; column: string};
export type GridSelection = {rowIds: Cell[]; columns: string[]; cells: GridCellAddress[][]; count: number};
export const emptyGridSelection = (): GridSelection => ({rowIds: [], columns: [], cells: [], count: 0});
export const gridRowKey = (id: Cell) => JSON.stringify([typeof id, id]);
export const gridCellKey = (id: Cell, column: string) => JSON.stringify([gridRowKey(id), column]);

/** Input order must be visual order, not source-data order (sorting/pagination). */
export function rectangularSelection(rowIds: Cell[], columns: string[], anchor: GridCellAddress, end: GridCellAddress): GridSelection {
  const a = rowIds.findIndex(id => gridRowKey(id) === gridRowKey(anchor.rowId));
  const b = rowIds.findIndex(id => gridRowKey(id) === gridRowKey(end.rowId));
  const x = columns.indexOf(anchor.column), y = columns.indexOf(end.column);
  if ([a,b,x,y].some(i => i < 0)) return emptyGridSelection();
  const selectedRows = rowIds.slice(Math.min(a,b),Math.max(a,b)+1);
  const selectedColumns = columns.slice(Math.min(x,y),Math.max(x,y)+1);
  return {rowIds: selectedRows, columns: selectedColumns, cells: selectedRows.map(rowId => selectedColumns.map(column => ({rowId,column}))), count: selectedRows.length * selectedColumns.length};
}

/** Spreadsheet-compatible TSV: retain empty/NULL cells, quote embedded separators. */
export function selectionToTsv(selection: GridSelection, read: (rowId: Cell, column: string) => unknown): string {
  const quote = (value: unknown) => {const text = String(value ?? '');return /[\t\r\n"]/.test(text) ? `"${text.replaceAll('"','""')}"` : text;};
  return selection.cells.map(row => row.map(cell => quote(read(cell.rowId,cell.column))).join('\t')).join('\n');
}

/** Edge velocity shared by synchronous pointer moves and sustained RAF scrolling. */
export function edgeScrollDelta(position: number, min: number, max: number): number {
  if(max<=min)return 0;
  const zone=Math.min(28,(max-min)/2);
  if(position<min+zone)return -Math.min(18,Math.max(1,(min+zone-position)/2));
  if(position>max-zone)return Math.min(18,Math.max(1,(position-max+zone)/2));
  return 0;
}
