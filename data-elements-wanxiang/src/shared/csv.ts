/** Plain-data CSV export. Prefix spreadsheet formulas rather than allowing active cells. */
export function csvCell(value: unknown): string {
  const text = typeof value === 'object' && value !== null ? JSON.stringify(value) : String(value ?? '');
  const safe = /^[\s]*[=+@-]/.test(text) ? `'${text}` : text;
  return `"${safe.replaceAll('"','""')}"`;
}
export function toCsv<T extends object>(rows: T[]): string {
  if (!rows.length) return '';
  const keys=Array.from(new Set(rows.flatMap(row=>Object.keys(row))));
  return '\ufeff'+[keys.map(csvCell).join(','),...rows.map(row=>keys.map(key=>csvCell((row as Record<string,unknown>)[key])).join(','))].join('\r\n');
}
