import type { Catalog } from '../domain/types';
import type { Field, FormValues } from './common';
export function extensionFields(catalog: Catalog[], entityType: string): Field[] {
    return catalog.filter(item => item.category === 'extension' && item.subjectType === entityType && item.status === 'enabled').map(item => ({
        name: `extension_${item.code}`, label: item.name,
        type: item.sensitive ? 'text' : item.value === 'NUMBER' ? 'number' : item.value === 'BOOLEAN' ? 'switch' : item.value === 'ENUM' ? 'select' : 'text',
        required: item.required, span: 12, options: item.validation?.options?.map(value => ({ value, label: value })),
        max: item.validation?.maxLength || 4096, min: item.validation?.min,
        help: item.sensitive ? '已保存的内容显示为受保护；输入新内容会替换原值。' : item.value === 'DATE' ? '日期格式为 YYYY-MM-DD。' : undefined,
    }));
}
export function extensionInitial(ext?: Record<string, unknown>): FormValues { return Object.fromEntries(Object.entries(ext || {}).map(([key, value]) => [`extension_${key}`, value])); }
export function extensionValues(catalog: Catalog[], entityType: string, values: FormValues): Record<string, unknown> {
    const result: Record<string, unknown> = {};
    for (const field of catalog.filter(item => item.category === 'extension' && item.subjectType === entityType && item.status === 'enabled')) {
        let value = values[`extension_${field.code}`]; if (value === undefined || value === '') continue;
        if (value !== '[受保护]' && field.sensitive) {
            if (field.value === 'NUMBER') { value = Number(value); if (!Number.isFinite(value)) throw new Error(`${field.name}须为数字`); }
            if (field.value === 'BOOLEAN') { if (!['true', 'false'].includes(String(value))) throw new Error(`${field.name}须填写true或false`); value = String(value) === 'true'; }
        }
        result[field.code] = value;
    }
    return result;
}
