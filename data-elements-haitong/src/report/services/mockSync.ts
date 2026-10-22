import type { Resource, Dictionary, SyncTask, Scenario, TaskItem, RegisteredTableTarget } from '../core/model';
import { uid, clone } from '../core/model';
import { submitRegisteredReport, type ReportContext } from './reportApi';
export interface SyncService {
    submit(task: SyncTask, onUpdate: (task: SyncTask) => void, sourceFile?: File): () => void;
    query(task: SyncTask): SyncTask;
}
export function createTask(r: Resource, ids: string[], dicts: Dictionary[], scenario: Scenario, targetTable?: RegisteredTableTarget): SyncTask {
    const selectedRows = new Map(r.rows.map(row => [row.id, row]));
    if (!ids.length || new Set(ids).size !== ids.length || ids.some(id => !selectedRows.has(id) || (r.sync[id] && r.sync[id] !== 'idle')))
        throw new Error('上报范围已变化，请重新打开确认窗口后提交');
    const schema = targetTable ? { ...r.schema, name: targetTable.displayName, englishName: targetTable.name } : r.schema;
    return { id: uid('SYNC').toUpperCase(), resourceId: r.id, source: clone(r.source), schema: clone(schema), schemaVersion: r.schemaVersion, dataVersion: r.dataVersion, dictionaries: clone(dicts.filter(d => r.schema.fields.some(f => f.dictionary?.id === d.id))), createdAt: new Date().toISOString(), stage: 'sending', scenario, target: targetTable ? `FJGHCC 非结构化存储 · ${targetTable.displayName}` : 'FJGHCC 非结构化存储 FTP', targetTable: targetTable ? clone(targetTable) : undefined, items: ids.map(id => { const row = selectedRows.get(id)!; const prepared = r.validation?.prepared[id]; return { rowId: id, sourceRow: row.sourceRow, values: Object.fromEntries(r.schema.fields.map(f => [f.en, prepared?.[f.id] ?? row.values[f.id] ?? null])), state: 'sending', reason: '' }; }) };
}
export function countTask(t: SyncTask) { return { total: t.items.length, success: t.items.filter(x => x.state === 'success').length, failed: t.items.filter(x => x.state === 'failed').length, unknown: t.items.filter(x => x.state === 'unknown').length, processing: t.items.filter(x => ['sending', 'waiting'].includes(x.state)).length }; }
export function taskLabel(t: SyncTask): string { const c = countTask(t); if (t.stage === 'sending')
    return '同步中'; if (t.stage === 'waiting')
    return '等待回执'; if (c.unknown)
    return '结果待确认'; if (c.success === c.total)
    return '全部成功'; if (c.failed === c.total)
    return '全部失败'; return '部分成功'; }
export function retryTask(t: SyncTask, current: Record<string, string>): SyncTask { const items = t.items.filter(x => x.state === 'failed' && (current[x.rowId] === undefined || current[x.rowId] === 'failed')); if (!items.length)
    throw new Error('没有可重试的明确失败项'); return { ...clone(t), id: uid('SYNC').toUpperCase(), parentId: t.id, createdAt: new Date().toISOString(), endedAt: undefined, stage: 'sending', scenario: 'success', items: items.map(x => ({ ...clone(x), state: 'sending', reason: '' })) }; }
let reportContext: ReportContext | undefined;

/** Called only after the shared platform session and selected data source pass backend validation. */
export function configureReportSync(context: ReportContext | undefined): void {
    reportContext = context;
}

/**
 * The old prototype simulated a timer and fabricated row results.  The same
 * UI contract now delegates the original file to the tenant-scoped report-file
 * endpoint.  A successful response means the server-side FTP/SFTP transfer
 * completed; any rejected transfer is returned as a failed task instead of
 * being hidden.
 */
export const reportSyncService: SyncService = {
    submit(task, onUpdate, sourceFile) {
        let cancelled = false;
        onUpdate({ ...clone(task), stage: 'sending', items: task.items.map(item => ({ ...item, state: 'sending' })) });
        if (!reportContext) {
            queueMicrotask(() => onUpdate({ ...clone(task), stage: 'done', endedAt: new Date().toISOString(), items: task.items.map(item => ({ ...item, state: 'failed', reason: '数据上报上下文尚未加载完成' })) }));
            return () => { cancelled = true; };
        }
        void submitRegisteredReport(reportContext, task, sourceFile)
            .then(result => {
                if (cancelled) return;
                onUpdate({
                    ...clone(task),
                    stage: 'done',
                    endedAt: new Date().toISOString(),
                    target: result.targetDatasourceName ? `FJGHCC 非结构化存储 · ${result.targetDatasourceName}` : 'FJGHCC 非结构化存储 FTP',
                    items: task.items.map(item => ({ ...item, state: 'success', reason: '原始上报文件已成功传入 FJGHCC 关联的 FTP 存储' })),
                });
            })
            .catch((error: Error) => {
                if (cancelled) return;
                onUpdate({
                    ...clone(task),
                    stage: 'done',
                    endedAt: new Date().toISOString(),
                    items: task.items.map(item => ({ ...item, state: 'failed', reason: error.message || '平台未接受本次上报' })),
                });
            });
        return () => { cancelled = true; };
    },
    query(task) { return task; },
};
