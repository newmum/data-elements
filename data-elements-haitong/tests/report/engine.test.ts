import test from 'node:test';
import assert from 'node:assert/strict';
import { initialWorkspace, demoBook } from '../../src/report/data/demo';
import { clone, isBlank } from '../../src/report/core/model';
import type { Resource, Field } from '../../src/report/core/model';
import { validateSchema, validateResource, selectableRows, parseTime, prepareValue, matchDictionary, dictionaryErrors, fingerprint } from '../../src/report/core/engine';
import { suggestNames, normalizeEn, acceptReliableNames } from '../../src/report/core/names';
import { defaultRegion, resourceFromRegion, regionErrors } from '../../src/report/core/import';
import { createTask, retryTask, countTask } from '../../src/report/services/mockSync';
import { reportRowsForSubmission } from '../../src/report/services/reportApi';
const setup = () => { const w = initialWorkspace(); return { r: w.resources[0], ds: w.dictionaries }; };
const field = (r: Resource, en: string) => r.schema.fields.find(f => f.en === en)!;
const set = (r: Resource, i: number, en: string, v: any) => r.rows[i].values[field(r, en).id] = v;
const validate = (r: Resource, ds: any[]) => { r.dirty = false; r.validation = validateResource(r, ds); return r.validation; };
test('演示数据真实校验：240 = 232合格 + 8异常', () => { const { r, ds } = setup(); assert.equal(selectableRows(r, ds).size, 232); assert.equal(r.validation!.issues.length, 8); });
test('普通空值不含0、false、字符串null', () => { for (const v of [null, '', '  ', undefined])
    assert.ok(isBlank(v)); for (const v of [0, false, '0', 'null', 'N/A'])
    assert.ok(!isBlank(v)); });
test('旧版浏览器缺少 structuredClone 时仍可复制上报数据', () => {
    const descriptor = Object.getOwnPropertyDescriptor(globalThis, 'structuredClone');
    Object.defineProperty(globalThis, 'structuredClone', { configurable: true, writable: true, value: undefined });
    try {
        const original = { field: '人员编号', rows: [{ id: 'P001', status: false }, { id: 'P002', status: null }] };
        const copied = clone(original);
        copied.rows[0].id = 'CHANGED';
        assert.equal(original.rows[0].id, 'P001');
        assert.deepEqual(copied.rows[1], original.rows[1]);
    } finally {
        if (descriptor) Object.defineProperty(globalThis, 'structuredClone', descriptor);
        else Reflect.deleteProperty(globalThis, 'structuredClone');
    }
});
test('纯中文和纯英文补全均保持原表头', () => { assert.equal(suggestNames(['记录编号']).en, 'record_id'); assert.equal(suggestNames(['equipment_name']).zh, '装备名称'); });
test('同格双语和上下两行双语', () => { assert.equal(suggestNames(['记录编号（record_id）']).en, 'record_id'); assert.equal(suggestNames(['记录编号', 'record_id']).zh, '记录编号'); });
test('IP地址不错误拆分，未知code不强行赋予业务含义', () => { assert.equal(suggestNames(['IP地址']).en, 'ip_address'); assert.equal(suggestNames(['code']).zh, ''); });
test('矛盾中英文必须人工确认', () => { assert.ok(suggestNames(['领用时间（return_time）']).ambiguous); });
test('英文规范化', () => { assert.equal(normalizeEn('Record ID'), 'record_id'); assert.equal(normalizeEn('RecordID'), 'record_id'); });
test('名称唯一与非法字符阻断', () => { const { r, ds } = setup(); field(r, 'equipment_id').en = 'equipment_name'; assert.ok(validateSchema(r.schema, ds).some(e => e.code === 'EN_DUPLICATE')); r.schema.fields[0].en = '123 invalid'; assert.ok(validateSchema(r.schema, ds).some(e => e.code === 'EN_NAME')); });
test('名称改名不改变来源数据映射', () => { const { r } = setup(); const f = field(r, 'equipment_id'), value = r.rows[0].values[f.id]; f.en = 'new_equipment_code'; assert.equal(r.rows[0].values[f.id], value); });
test('主键缺失阻断全部资源', () => { const { r, ds } = setup(); r.schema.fields.forEach(f => f.pk = false); assert.ok(validate(r, ds).global.some(e => e.code === 'PK_REQUIRED')); assert.equal(selectableRows(r, ds).size, 0); });
test('联合键单列可重复，组合唯一', () => { const { r, ds } = setup(); assert.equal(validate(r, ds).duplicateGroups, 0); field(r, 'line_no').pk = false; assert.equal(validate(r, ds).duplicateGroups, 80); assert.equal(r.validation!.eligible.length, 0); });
test('主键全量检查，当前页之外重复也阻断', () => { const { r, ds } = setup(); set(r, 239, 'issue_no', r.rows[0].values[field(r, 'issue_no').id]); set(r, 239, 'line_no', 1); assert.equal(validate(r, ds).duplicateGroups, 1); assert.equal(selectableRows(r, ds).size, 0); });
test('主键空值为资源阻断', () => { const { r, ds } = setup(); set(r, 0, 'issue_no', ' '); assert.ok(validate(r, ds).global.some(e => e.code === 'PK_EMPTY')); });
test('联合主键元组不会因拼接分隔符冲突', () => { const { r, ds } = setup(); r.rows = r.rows.slice(0, 2); field(r, 'line_no').type = 'text'; set(r, 0, 'issue_no', 'a|b'); set(r, 0, 'line_no', 'c'); set(r, 1, 'issue_no', 'a'); set(r, 1, 'line_no', 'b|c'); assert.equal(validate(r, ds).duplicateGroups, 0); });
test('必填普通字段只阻断对应行', () => { const { r, ds } = setup(); field(r, 'equipment_name').nullable = 'forbid'; set(r, 0, 'equipment_name', null); assert.equal(validate(r, ds).global.length, 0); assert.equal(r.validation!.eligible.length, 231); });
test('两类时间角色分别必填；兼任必须确认', () => { const { r, ds } = setup(); r.schema.eventTime = ''; assert.ok(validateSchema(r.schema, ds).some(e => e.code === 'TIME_ROLE')); r.schema.eventTime = r.schema.timestamp; assert.ok(validateSchema(r.schema, ds).some(e => e.code === 'TIME_SHARED')); r.schema.sameTimeConfirmed = true; assert.ok(!validateSchema(r.schema, ds).some(e => e.code === 'TIME_SHARED')); });
test('严格日历校验，闰年有效/非法日期失败', () => { const { r } = setup(); const f = field(r, 'updated_at'); assert.equal(parseTime('2024-02-29', f), '2024-02-29'); assert.throws(() => parseTime('2026-02-29', f)); assert.throws(() => parseTime('2026-13-01', f)); });
test('日期不编造时间，时区按确认规则处理', () => { const { r } = setup(); const f = field(r, 'updated_at'); assert.equal(parseTime('2026-08-01', f), '2026-08-01'); assert.equal(parseTime('2026-08-01 10:30', f), '2026-08-01T10:30+08:00'); f.time.timezone = ''; assert.throws(() => parseTime('2026-08-01 10:30', f)); });
test('歧义日月格式与Unix单位必须明确', () => { const { r } = setup(); const f = field(r, 'updated_at'); assert.throws(() => parseTime('03/04/2026', f)); f.time.format = 'dmy'; assert.equal(parseTime('03/04/2026', f), '2026-04-03'); f.time.format = 'mdy'; assert.equal(parseTime('03/04/2026', f), '2026-03-04'); f.time.format = 'unix-s'; assert.equal(parseTime('0', f), '1970-01-01T00:00:00.000Z'); });
test('字典01不等于1，保留原值不自动转码', () => { const { r, ds } = setup(); const f = field(r, 'status_code'); assert.equal(matchDictionary('01', f, ds)!.label, '在库'); assert.throws(() => matchDictionary('1', f, ds)); f.dictionary!.match = 'label'; assert.equal(prepareValue('已领用', f, r.schema, ds), '已领用'); });
test('显式转换输出代码，原值不修改', () => { const { r, ds } = setup(); const f = field(r, 'status_code'); f.dictionary = { ...f.dictionary!, match: 'label', output: 'code', confirmed: true }; set(r, 0, 'status_code', '已领用'); assert.equal(prepareValue(r.rows[0].values[f.id], f, r.schema, ds), '02'); assert.equal(r.rows[0].values[f.id], '已领用'); });
test('名称同名异码不自动取第一个', () => { const { r, ds } = setup(); ds[0].entries.push({ code: '04', label: '已领用' }); const f = field(r, 'status_code'); f.dictionary!.match = 'label'; assert.throws(() => matchDictionary('已领用', f, ds)); f.dictionary!.match = 'manual'; f.dictionary!.mappings = { '已领用': '04' }; assert.equal(matchDictionary('已领用', f, ds)!.code, '04'); });
test('辅助字典重复代码无效', () => { const { ds } = setup(); ds[0].entries.push({ code: '01', label: '在库' }); assert.ok(dictionaryErrors(ds[0]).length > 0); });
test('数值转换不能损失前导零或长整数精度', () => { const { r, ds } = setup(); const f = field(r, 'equipment_id'); assert.equal(prepareValue('000123', f, r.schema, ds), '000123'); f.type = 'integer'; assert.throws(() => prepareValue('000123', f, r.schema, ds)); assert.throws(() => prepareValue('12345678901234567', f, r.schema, ds)); });
test('同一行多问题只计一次异常记录', () => { const { r, ds } = setup(); const old = r.validation!.eligible.length; field(r, 'equipment_name').nullable = 'forbid'; set(r, 29, 'equipment_name', null); const v = validate(r, ds); assert.equal(v.eligible.length, old); assert.equal(v.issues.length, 9); });
test('修改结构或字典使旧校验立即失效', () => { const { r, ds } = setup(); const fp = fingerprint(r, ds); field(r, 'equipment_id').zh = '装备业务编号'; assert.notEqual(fingerprint(r, ds), fp); assert.equal(selectableRows(r, ds).size, 0); validate(r, ds); ds[0].version++; assert.equal(selectableRows(r, ds).size, 0); });
test('同步任务锁定深拷贝，重复选择/空任务被拒绝', () => { const { r, ds } = setup(); const id = r.validation!.eligible[0]; assert.throws(() => createTask(r, [], ds, 'success')); assert.throws(() => createTask(r, [id, id], ds, 'success')); const t = createTask(r, [id], ds, 'success'); field(r, 'issue_no').en = 'changed'; ds[0].entries[0].label = 'changed'; assert.equal(t.schema.fields[0].en, 'issue_no'); assert.equal(t.dictionaries[0].entries[0].label, '在库'); assert.ok('issue_no' in t.items[0].values); });
test('已同步和结果待确认记录不可重复提交', () => { const { r, ds } = setup(); const id = r.validation!.eligible[0]; r.sync[id] = 'success'; assert.throws(() => createTask(r, [id], ds, 'success')); r.sync[id] = 'unknown'; assert.throws(() => createTask(r, [id], ds, 'success')); });
test('未完善表信息时仍可将当前表全部数据带入确认上报', () => { const { r, ds } = setup(); const id = r.rows[0].id, f = r.schema.fields[0]; r.validation = undefined; r.dirty = true; const task = createTask(r, [id], ds, 'success'); assert.equal(task.items[0].values[f.en], r.rows[0].values[f.id]); });
test('整表上报按登记英文名读取任务快照，不能把已填写主键传为空', () => { const { r, ds } = setup(); const id = r.validation!.eligible[0]; const task = createTask(r, [id], ds, 'success'); const vehicleField = task.schema.fields[0]; task.schema.fields[0] = { ...vehicleField, en: 'vehicle_id' };
    task.items[0].values = { vehicle_id: 'VEH202609100001' };
    assert.deepEqual(reportRowsForSubmission(task), [{ vehicle_id: 'VEH202609100001', ...Object.fromEntries(task.schema.fields.slice(1).map(field => [field.en, null])) }]);
});
test('只重试明确失败项，保留原快照', () => { const { r, ds } = setup(); const t = createTask(r, r.validation!.eligible.slice(0, 3), ds, 'partial'); t.items[0].state = 'success'; t.items[1].state = 'failed'; t.items[2].state = 'unknown'; const retry = retryTask(t, {}); assert.equal(retry.items.length, 1); assert.equal(retry.parentId, t.id); assert.equal(retry.items[0].rowId, t.items[1].rowId); });
test('任务统计在后端回执前仍保持数量守恒', () => { const { r, ds } = setup(); const t = createTask(r, r.validation!.eligible.slice(0, 7), ds, 'unknown'); t.stage = 'done'; t.items.forEach(x => x.state = 'unknown'); const c = countTask(t); assert.equal(c.unknown, 7); assert.equal(c.total, c.success + c.failed + c.unknown + c.processing); });
test('多个工作表独立字段ID，双行表头不进入数据', () => { const b = demoBook(); b.sheets[0].cells.splice(1, 0, b.sheets[0].cells[0]); b.sheets[0].rowCount++; const region = defaultRegion(b.sheets[0], 0); region.header = 'bilingual'; region.dataStart = 3; const r = resourceFromRegion(b, region); assert.equal(r.rows.length, 240); assert.equal(r.rows[0].sourceRow, 3); assert.equal(r.schema.fields[0].headers.length, 2); });
test('同表多区域重叠阻止，隐藏内容需确认', () => { const b = demoBook(); const a = defaultRegion(b.sheets[0], 0), c = clone(a); c.id = 'second'; assert.ok(regionErrors([a, c], b).some(s => s.includes('重叠'))); b.sheets[0].hiddenRows = [2]; assert.ok(regionErrors([a], b).some(s => s.includes('隐藏'))); a.hiddenConfirmed = true; assert.equal(regionErrors([a], b).length, 0); });

// Portal v0.2: table names are independent schema metadata, not file names or row keys.
import { tableNameErrors, tableNameConflict } from '../../src/report/core/tableNames';
test('表中文名与英文名均为必填', () => { assert.equal(tableNameErrors({ name: '', englishName: '' }).length, 2); });
test('表英文名校验：小写字母开头、63字符以内且非保留字', () => { for (const englishName of ['Equipment', '01_table', 'has space', 'select', 'a'.repeat(64)]) assert.ok(tableNameErrors({ name: '装备表', englishName }).length); assert.equal(tableNameErrors({ name: '装备表', englishName: 'equipment_issue_2026' }).length, 0); });
test('表中文名不能仅填写英文或空白', () => { for (const name of ['English', '   ', '装'.repeat(65)]) assert.ok(tableNameErrors({ name, englishName: 'equipment' }).some(e => e.code === 'TABLE_ZH_NAME')); });
test('同会话其他资源英文表名冲突必须阻断，自身除外', () => { const { resources } = initialWorkspace(); assert.ok(tableNameConflict('inventory_change', resources[0].id, resources)); assert.ok(!tableNameConflict('equipment_issue', resources[0].id, resources)); });
test('改表名不改源文件、字段ID、主键及原始值', () => { const { r } = setup(); const before = clone([r.source, r.schema.fields, r.rows]); r.schema.name = '新的装备明细'; r.schema.englishName = 'new_equipment_issue'; assert.deepEqual([r.source, r.schema.fields, r.rows], before); });
test('改表名立即使旧校验指纹失效', () => { const { r, ds } = setup(); r.schema.englishName = 'new_table_name'; assert.equal(selectableRows(r, ds).size, 0); });
test('新上传中文表名不会虚构已确认的英文表名', () => { const book = demoBook(); const r = resourceFromRegion(book, defaultRegion(book.sheets[0], 0)); assert.equal(r.schema.englishName, ''); assert.ok(validateSchema(r.schema, []).some(e => e.code === 'TABLE_EN_NAME')); });
test('中英文表名进入同步快照且不随后续改名变化', () => { const { r, ds } = setup(); const t = createTask(r, r.validation!.eligible.slice(0, 2), ds, 'success'); r.schema.name = '别的表'; r.schema.englishName = 'other_table'; assert.equal(t.schema.name, '装备领用明细'); assert.equal(t.schema.englishName, 'equipment_issue'); });
test('同一Excel的不同工作表各自独立命名', () => { const { resources } = initialWorkspace(); const first = resources[0], second = resources[1], source = first.source.filename; first.schema.name = '表一中文名'; first.schema.englishName = 'table_one'; assert.equal(second.schema.englishName, 'inventory_change'); assert.equal(first.source.filename, source); assert.equal(second.source.filename, source); });
test('失败重试保留原任务中英文表名', () => { const { r, ds } = setup(); const task = createTask(r, r.validation!.eligible.slice(0, 1), ds, 'failure'); task.items[0].state = 'failed'; const retry = retryTask(task, {}); assert.equal(retry.schema.englishName, task.schema.englishName); assert.equal(retry.schema.name, task.schema.name); });
