import type { Schema, Resource } from './model';
export const EN_TABLE_PATTERN = /^[a-z][a-z0-9_]{0,62}$/;
const RESERVED = new Set(['select', 'from', 'table', 'where', 'order', 'group', 'user', 'insert', 'delete', 'update', 'drop']);
export function tableNameErrors(s: Pick<Schema, 'name' | 'englishName'>): { code: string; message: string }[] {
    const out: { code: string; message: string }[] = [];
    if (!s.name?.trim() || !/[\u3400-\u9fff]/.test(s.name) || s.name.trim().length > 64)
        out.push({ code: 'TABLE_ZH_NAME', message: '请填写含中文的表中文名，最多64个字符' });
    if (!s.englishName || !EN_TABLE_PATTERN.test(s.englishName) || RESERVED.has(s.englishName))
        out.push({ code: 'TABLE_EN_NAME', message: '表英文名须以小写字母开头，仅含小写字母、数字和下划线，最多63个字符，不能使用保留字' });
    return out;
}
export function tableNameConflict(englishName: string, resourceId: string, resources: Resource[]): boolean {
    return !!englishName && resources.some(r => r.id !== resourceId &&
        [r.schema.englishName, r.savedSchema.englishName].some(n => n === englishName));
}
