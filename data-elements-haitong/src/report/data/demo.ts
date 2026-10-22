import type { SourceBook, SourceSheet, Value, Resource, Dictionary, SyncTask } from '../core/model';
import { clone } from '../core/model';
import { defaultRegion, resourceFromRegion } from '../core/import';
import { validateResource } from '../core/engine';
export const demoDictionary: Dictionary = { id: 'dict-equipment', version: 1, name: '装备状态', source: 'upload', entries: [{ code: '01', label: '在库' }, { code: '02', label: '已领用' }, { code: '03', label: '维修中' }] };
export function sheet(name: string, rows: Value[][]): SourceSheet { return { name, cells: rows.map(r => r.map(v => ({ v }))), rowCount: rows.length, colCount: Math.max(0, ...rows.map(r => r.length)), hidden: false, hiddenRows: [], hiddenCols: [], merges: [], notes: [] }; }
export function demoBook(): SourceBook {
    const header = ['领用单号', 'line_no', 'equipment_name', '装备编号', '所属单位', '领用人', '装备状态（status_code）', '更新时间', 'issue_time', '备注'];
    const data: Value[][] = Array.from({ length: 240 }, (_, i) => { const d = String(i % 28 + 1).padStart(2, '0'); return [`LY202608${String(Math.floor(i / 3) + 1).padStart(4, '0')}`, i % 3 + 1, ['执法记录仪', '对讲机', '便携照明设备', '执勤装备箱'][i % 4], `EQ-${String(i + 1).padStart(5, '0')}`, `直属单位${['A', 'B', 'C', 'D'][i % 4]}`, `民警${String(i % 18 + 1).padStart(3, '0')}`, i % 30 === 29 ? '09' : ['02', '01', '03'][i % 3], `2026-08-${d} 16:30:00`, `2026-08-${d} 09:15:00`, i % 5 === 0 ? '定期领用' : null]; });
    const inventory: Value[][] = [['变更编号', 'equipment_name', '库存数量', '变更时间', '更新时间'], ...Array.from({ length: 80 }, (_, i): Value[] => [`BG-${String(i + 1).padStart(4, '0')}`, ['执法记录仪', '对讲机'][i % 2], 30 + i, `2026-08-20 08:00:00`, `2026-08-21 10:00:00`])];
    return { id: 'file-equipment', filename: '装备领用记录.xlsx', size: 25600, importedAt: '2026-09-05T09:00:00+08:00', sheets: [sheet('领用明细', [header, ...data]), sheet('库存变更', inventory), sheet('装备状态代码表', [['代码', '名称'], ['01', '在库'], ['02', '已领用'], ['03', '维修中']])] };
}
export function initialWorkspace(): {
    resources: Resource[];
    dictionaries: Dictionary[];
} {
    const book = demoBook();
    const first = resourceFromRegion(book, defaultRegion(book.sheets[0], 0), true);
    first.id = 'resource-demo-issues';
    first.schema.name = '装备领用明细';
    first.schema.englishName = 'equipment_issue';
    first.schema.description = '装备领用明细数据资源，供公安网业务系统同步使用。';
    first.schema.fields = first.schema.fields.map(f => ({ ...f, confirmed: true, pk: ['issue_no', 'line_no'].includes(f.en), time: { ...f.time, timezone: f.type === 'datetime' ? 'Asia/Shanghai' : '' }, dictionary: f.en === 'status_code' ? { id: demoDictionary.id, version: 1, match: 'code', output: 'preserve', confirmed: false, mappings: {} } : undefined }));
    first.schema.timestamp = first.schema.fields.find(f => f.en === 'updated_at')!.id;
    first.schema.eventTime = first.schema.fields.find(f => f.en === 'issue_time')!.id;
    first.schema.reviewed = true;
    first.schemaVersion = 1;
    first.savedSchema = clone(first.schema);
    first.validation = validateResource(first, [demoDictionary]);
    const second = resourceFromRegion(book, defaultRegion(book.sheets[1], 1), true);
    second.schema.englishName = 'inventory_change';
    second.savedSchema = clone(second.schema);
    const schedule = sheet('值班排班', [['排班编号', '值班人员', '班次', '值班日期', '更新时间', '备注'], ...Array.from({ length: 120 }, (_, i): Value[] => [`PB-${String(i + 1).padStart(4, '0')}`, `民警${String(i % 18 + 1).padStart(3, '0')}`, i % 2 ? '晚班' : '早班', `2026-08-${String(i % 28 + 1).padStart(2, '0')}`, '2026-09-01 10:00:00', null])]);
    const third = resourceFromRegion({ id: 'file-shift', filename: '值班排班.csv', size: 8400, sheets: [schedule], importedAt: book.importedAt }, defaultRegion(schedule, 0), true);
    third.schema.englishName = 'duty_schedule';
    third.savedSchema = clone(third.schema);
    return { resources: [first, second, third], dictionaries: [clone(demoDictionary)] };
}

export function initialSyncRecords(resources: Resource[], dictionaries: Dictionary[]): SyncTask[] {
    const resource = resources.find(item => item.id === 'resource-demo-issues') || resources[0];
    if (!resource?.validation) return [];
    const createRecord = (id: string, rowIndexes: number[], failedIndexes: number[] = []): SyncTask => ({
        id,
        resourceId: resource.id,
        source: clone(resource.source),
        schema: clone(resource.schema),
        schemaVersion: resource.schemaVersion,
        dataVersion: resource.dataVersion,
        dictionaries: clone(dictionaries.filter(d => resource.schema.fields.some(f => f.dictionary?.id === d.id))),
        createdAt: '2026-09-05T08:42:00+08:00',
        endedAt: '2026-09-05T08:42:03+08:00',
        stage: 'done',
        scenario: failedIndexes.length ? 'partial' : 'success',
        target: '公安网数据接收区',
        items: rowIndexes.map((rowIndex, itemIndex) => {
            const row = resource.rows[rowIndex];
            const failed = failedIndexes.includes(itemIndex);
            return { rowId: row.id, sourceRow: row.sourceRow, values: Object.fromEntries(resource.schema.fields.map(f => [f.en, resource.validation!.prepared[row.id][f.id]])), state: failed ? 'failed' : 'success', reason: failed ? '接收校验未通过，可在任务详情中仅重试失败项' : '接收确认成功' };
        })
    });
    return [createRecord('SYNC-20260905-0002', [6, 7, 8, 9], [3]), createRecord('SYNC-20260905-0001', [0, 1, 2, 3, 4, 5])];
}
