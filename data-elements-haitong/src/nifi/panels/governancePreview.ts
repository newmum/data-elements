import { assertExactJsonNumbers } from '../utils/preciseJson.ts';
import type { PreviewResponse } from '../api/fieldMapping';

export function parsePreviewSamples(text: string): Record<string, unknown>[] {
  let parsed: unknown;
  try { assertExactJsonNumbers(text); parsed = JSON.parse(text); }
  catch (error) { throw error instanceof SyntaxError ? new Error('样本 JSON 格式不正确，请检查逗号、引号和括号') : error; }
  if (!Array.isArray(parsed) || parsed.length > 100 || parsed.some(row => !row || typeof row !== 'object' || Array.isArray(row))) {
    throw new Error('请填写 JSON 对象数组，最多 100 行；每行样本须为一个对象');
  }
  return parsed;
}

export function checkedPreview(result: PreviewResponse): PreviewResponse {
  if (!Array.isArray(result?.resultRows) || !Array.isArray(result?.rowResults) || result.rowResults.length > 100 || result.resultRows.length > 100
    || result.resultRows.some(row => !row || typeof row !== 'object' || Array.isArray(row))
    || result.rowResults.some(row => !row || !Number.isInteger(row.rowIndex) || row.rowIndex < 0 || row.rowIndex >= result.rowResults.length || typeof row.success !== 'boolean' || !Array.isArray(row.errors) || row.errors.some(error => typeof error !== 'string'))
    || new Set(result.rowResults.map(row => row.rowIndex)).size !== result.rowResults.length
    || result.resultRows.length !== result.rowResults.filter(row => row.success).length) {
    throw new Error('预览服务返回的行结果不完整，请重新执行预览');
  }
  return result;
}

export function previewValue(value: unknown): string {
  if (value === undefined) return '未输出';
  if (value === null) return 'NULL';
  if (value === '') return '空字符串';
  return typeof value === 'object' ? JSON.stringify(value) : String(value);
}
export function previewCell(record: Record<string, unknown>, name: string): unknown {
  return Object.hasOwn(record, name) ? record[name] : undefined;
}

export interface PreviewTableRow { key: string; sequence: number; record: Record<string, unknown> }
export function previewTableRows(records: Record<string, unknown>[], indices?: number[]): PreviewTableRow[] {
  return records.map((record, index) => ({ key: 'sample-' + index, sequence: (indices?.[index] ?? index) + 1, record }));
}
