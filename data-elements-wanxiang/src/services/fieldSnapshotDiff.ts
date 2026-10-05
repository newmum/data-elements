import type { Row } from './api';

export interface FieldChange { name: string; before?: Row; after?: Row; changed?: { label: string; before: string; after: string }[] }
export interface FieldSnapshotDiff { added: FieldChange[]; removed: FieldChange[]; modified: FieldChange[]; beforeCount: number; afterCount: number }

const value = (row: Row, key: string) => String(row[key] ?? '').trim();
const fieldName = (row: Row) => value(row, 'column_name').toLocaleLowerCase();
const properties: { key: string; label: string }[] = [
  { key: 'column_type', label: '字段类型' }, { key: 'data_type', label: '数据类型' },
  { key: 'length', label: '长度' }, { key: 'nullable', label: '可空' },
  { key: 'primary_key', label: '主键' }, { key: 'is_unique', label: '唯一' },
  { key: 'column_comment', label: '说明' },
];

/** Compare physical names; a rename cannot be proven without a stable source-column identity. */
export function diffFieldSnapshots(before: Row[], after: Row[]): FieldSnapshotDiff {
  const oldFields = new Map(before.filter(fieldName).map(row => [fieldName(row), row]));
  const newFields = new Map(after.filter(fieldName).map(row => [fieldName(row), row]));
  const added: FieldChange[] = [], removed: FieldChange[] = [], modified: FieldChange[] = [];
  for (const [name, row] of newFields) {
    const previous = oldFields.get(name);
    if (!previous) { added.push({ name: value(row, 'column_name'), after: row }); continue; }
    const changed = properties.filter(property => value(previous, property.key) !== value(row, property.key)).map(property => ({ label: property.label, before: value(previous, property.key), after: value(row, property.key) }));
    if (changed.length) modified.push({ name: value(row, 'column_name'), before: previous, after: row, changed });
  }
  for (const [name, row] of oldFields) if (!newFields.has(name)) removed.push({ name: value(row, 'column_name'), before: row });
  return { added, removed, modified, beforeCount: before.length, afterCount: after.length };
}
