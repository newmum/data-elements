import type { Resource, Dictionary, SyncTask, Scenario, RegisteredTableTarget } from '../core/model';
import { uid, clone } from '../core/model';
export function createTask(r: Resource, ids: string[], dicts: Dictionary[], scenario: Scenario, targetTable?: RegisteredTableTarget): SyncTask {
    const selectedRows = new Map(r.rows.map(row => [row.id, row]));
    if (!ids.length || new Set(ids).size !== ids.length || ids.some(id => !selectedRows.has(id) || (r.sync[id] && r.sync[id] !== 'idle')))
        throw new Error('上报范围已变化，请重新打开确认窗口后提交');
    const schema = targetTable ? { ...r.schema, name: targetTable.displayName, englishName: targetTable.name } : r.schema;
    return { id: uid('SYNC').toUpperCase(), resourceId: r.id, source: clone(r.source), schema: clone(schema), schemaVersion: r.schemaVersion, dataVersion: r.dataVersion, dictionaries: clone(dicts.filter(d => r.schema.fields.some(f => f.dictionary?.id === d.id))), createdAt: new Date().toISOString(), stage: 'sending', scenario, target: targetTable ? `FJGHCC 非结构化存储 · ${targetTable.displayName}` : 'FJGHCC 非结构化存储 FTP', targetTable: targetTable ? clone(targetTable) : undefined, items: ids.map(id => { const row = selectedRows.get(id)!; const prepared = r.validation?.prepared[id]; return { rowId: id, sourceRow: row.sourceRow, values: Object.fromEntries(r.schema.fields.map(f => [f.en, prepared?.[f.id] ?? row.values[f.id] ?? null])), state: 'sending', reason: '' }; }) };
}
export function countTask(t: SyncTask) { return { total: t.items.length, success: t.items.filter(x => x.state === 'success').length, failed: t.items.filter(x => x.state === 'failed').length, unknown: t.items.filter(x => x.state === 'unknown').length, processing: t.items.filter(x => ['sending', 'waiting'].includes(x.state)).length }; }
export function retryTask(t: SyncTask, current: Record<string, string>): SyncTask { const items = t.items.filter(x => x.state === 'failed' && (current[x.rowId] === undefined || current[x.rowId] === 'failed')); if (!items.length)
    throw new Error('没有可重试的明确失败项'); return { ...clone(t), id: uid('SYNC').toUpperCase(), parentId: t.id, createdAt: new Date().toISOString(), endedAt: undefined, stage: 'sending', scenario: 'success', items: items.map(x => ({ ...clone(x), state: 'sending', reason: '' })) }; }
