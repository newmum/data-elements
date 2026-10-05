export type TableDensity = 'small' | 'middle' | 'large';
export interface TablePreferences { density: TableDensity; hiddenColumns: string[]; pageSize: number }
export function normalizeTablePreferences(value: unknown): TablePreferences {
  const raw = value && typeof value === 'object' ? value as Record<string, unknown> : {};
  return {
    density: ['small', 'middle', 'large'].includes(String(raw.density)) ? raw.density as TableDensity : 'middle',
    hiddenColumns: Array.isArray(raw.hiddenColumns) ? [...new Set(raw.hiddenColumns.filter((key): key is string => typeof key === 'string' && key.length < 180))].slice(0, 100) : [],
    pageSize: [20, 50, 100].includes(Number(raw.pageSize)) ? Number(raw.pageSize) : 20,
  };
}
export function safeTablePage(page: number, total: number, pageSize: number): number {
  const size = Number.isFinite(pageSize) && pageSize > 0 ? Math.floor(pageSize) : 20;
  const count = Number.isFinite(total) ? Math.max(0, total) : 0;
  return Math.min(Math.max(1, Number.isFinite(page) ? Math.floor(page) : 1), Math.max(1, Math.ceil(count / size)));
}
export function tablePreferenceKey(title: string): string { return `wanxiang:table-view:${encodeURIComponent(title)}`; }
