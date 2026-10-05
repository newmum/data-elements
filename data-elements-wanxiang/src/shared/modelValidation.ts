import type { ViewModel } from '../features/er/core/model';
export interface ModelValues { name: string; code?: string; domain?: string; entityIds?: string[] }
export function modelValidation(values: ModelValues, diagrams: ViewModel[], editingId?: string): Record<string, string> {
  const errors: Record<string, string> = {};
  const name = values.name?.trim() ?? '';
  const code = values.code?.trim() ?? '';
  if (!name || name.length > 80) errors.name = '请输入 1–80 个字符的模型名称。';
  if (code && !/^[A-Za-z][A-Za-z0-9_]{0,79}$/.test(code)) errors.code = '以英文字母开头，只能包含字母、数字和下划线，最多 80 个字符。';
  else if (code && diagrams.some(diagram => diagram.id !== editingId && diagram.modelInfo?.code?.toLowerCase() === code.toLowerCase())) errors.code = '模型代码已存在，请使用其他代码。';
  if ((values.domain?.trim().length ?? 0) > 60) errors.domain = '业务域最多 60 个字符。';
  return errors;
}
export function duplicateModelCode(code: string | undefined, diagrams: ViewModel[]): string {
  if (!code) return '';
  const base = code.slice(0, 69);
  const used = new Set(diagrams.map(diagram => diagram.modelInfo?.code?.toLowerCase()));
  let suffix = 1;
  while (used.has(`${base}_COPY_${suffix}`.toLowerCase())) suffix += 1;
  return `${base}_COPY_${suffix}`;
}
