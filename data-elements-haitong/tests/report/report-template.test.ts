import test from 'node:test';
import assert from 'node:assert/strict';
import * as XLSX from 'xlsx';
import { parseSource, resourceFromRegion, defaultRegion } from '../../src/report/core/import';
import { createTask } from '../../src/report/services/mockSync';
import { reportRowsForSubmission, type ReportTable, type ReportTableField } from '../../src/report/services/reportApi';
import { assertReportUploadHeaders, createReportUploadTemplate, reportTemplateHeaders, reportSheetName } from '../../src/report/services/reportTemplate';
import { bindResourceToTarget } from '../../src/report/services/reportImport';

const table: ReportTable = { id: 'test-table', datasourceId: 'test-source', datasourceName: '测试数据源', name: 'AA', displayName: '测试数据表1', businessType: '业务表', fieldCount: 2, recordCount: 0, timestampField: 'a2', businessTimeField: 'a2', updatedAt: '', tableType: 'TABLE', governanceFields: [] };
const fields: ReportTableField[] = [
  { name: 'a1', displayName: '测试', dataType: 'varchar(32)', primaryKey: true, nullable: false, dictionaryName: '', standardLabel: '', timeRoles: [] },
  { name: 'a2', displayName: '测试', dataType: 'datetime', primaryKey: false, nullable: false, dictionaryName: '', standardLabel: '', timeRoles: ['时间戳', '业务时间'] },
];

test('同名中文字段生成唯一中英文表头，空中文名使用英文名', () => {
  assert.deepEqual(reportTemplateHeaders(fields), ['测试（a1）', '测试（a2）']);
  assert.deepEqual(reportTemplateHeaders([{ ...fields[0], displayName: '' }]), ['a1']);
  assert.deepEqual(reportTemplateHeaders([{ ...fields[0], displayName: 'a1' }]), ['a1']);
});
test('空英文名和重复英文登记字段不可生成模板', () => {
  assert.throws(() => reportTemplateHeaders([{ ...fields[0], name: '' }]), /英文名为空或重复/);
  assert.throws(() => reportTemplateHeaders([fields[0], { ...fields[1], name: 'A1' }]), /英文名为空或重复/);
});
test('新表头和英文表头能上传，中英文括号均可识别', () => {
  for (const headers of [['测试（a1）', '测试（a2）'], ['测试(a1)', '测试(a2)'], ['a1', 'a2'], [' 测试（a1） ', '测试（a2）']]) {
    assert.doesNotThrow(() => assertReportUploadHeaders(headers, fields));
  }
});
test('旧的唯一中文表头兼容，同名中文表头明确拒绝', () => {
  assert.doesNotThrow(() => assertReportUploadHeaders(['编码', '日期'], fields.map((field, index) => ({ ...field, displayName: ['编码', '日期'][index] }))));
  assert.throws(() => assertReportUploadHeaders(['测试', '测试'], fields), /重复表头/);
  assert.throws(() => assertReportUploadHeaders(['测试', 'a2'], fields), /第 1 列表头不匹配/);
});
test('缺列、多列、空表头、错表头、重复英文表头、调换列均不能静默错位', () => {
  for (const headers of [['a1'], ['a1', 'a2', 'a3'], ['', 'a2'], ['a1', 'bad'], ['a1', 'a1'], ['测试（a2）', '测试（a1）']]) {
    assert.throws(() => assertReportUploadHeaders(headers, fields));
  }
});
test('模板保持一个工作表和一行表头，扩宽中英文列，无示例数据混入', async () => {
  const bytes = await createReportUploadTemplate(table, fields);
  const workbook = XLSX.read(bytes, { type: 'array', cellStyles: true });
  assert.deepEqual(workbook.SheetNames, [table.displayName]);
  const sheet = workbook.Sheets[table.displayName];
  assert.deepEqual(XLSX.utils.sheet_to_json(sheet, { header: 1 }), [['测试（a1）', '测试（a2）']]);
  assert(sheet['!cols']![0].wch! >= 18);
});
test('工作表名遵循 Excel 31 字符和非法字符限制', () => {
  const name = reportSheetName({ displayName: "'名称[]/" + '长'.repeat(40) });
  assert(name.length <= 31);
  assert(!/[\[\]\/]/.test(name));
  assert(!name.startsWith("'"));
});
test('真实 XLSX 解析、空行跳过、登记绑定、提交快照保持两列独立，日期不串位', async () => {
  const bytes = await createReportUploadTemplate(table, fields);
  const workbook = XLSX.read(bytes, { type: 'array' });
  XLSX.utils.sheet_add_aoa(workbook.Sheets[table.displayName], [['0001', '2026-09-29 12:34:56'], [null, null], ['0002', '2026-09-30 00:00:00']], { origin: 'A2' });
  const file = new File([XLSX.write(workbook, { type: 'array', bookType: 'xlsx' })], '测试数据表1.xlsx');
  const parsed = await parseSource(file);
  const resource = bindResourceToTarget(resourceFromRegion(parsed, defaultRegion(parsed.sheets[0], 0)), table, fields);
  assert.equal(resource.rows.length, 2);
  assert.deepEqual(resource.schema.fields.map(field => field.en), ['a1', 'a2']);
  assert.equal(resource.schema.timestamp, resource.schema.fields[1].id);
  assert.equal(resource.schema.eventTime, resource.schema.fields[1].id);
  assert.equal(resource.schema.fields[0].pk, true);
  const task = createTask(resource, resource.rows.map(row => row.id), [], 'success', table);
  assert.deepEqual(reportRowsForSubmission(task), [{ a1: '0001', a2: '2026-09-29 12:34:56' }, { a1: '0002', a2: '2026-09-30 00:00:00' }]);
});
test('模板未填数据可识别为空，重复旧表头在登记绑定入口被拒绝', async () => {
  const bytes = await createReportUploadTemplate(table, fields);
  const parsed = await parseSource(new File([bytes], '测试数据表1.xlsx'));
  const resource = resourceFromRegion(parsed, defaultRegion(parsed.sheets[0], 0));
  assert.equal(bindResourceToTarget(resource, table, fields).rows.length, 0);
  resource.schema.fields.forEach(field => { field.headers = ['测试']; });
  assert.throws(() => bindResourceToTarget(resource, table, fields), /重复表头/);
});
test('登记模板保存的中文字段类型仍正确绑定日期时间角色', async () => {
  const parsed = await parseSource(new File([await createReportUploadTemplate(table, fields)], '测试数据表1.xlsx'));
  const bound = bindResourceToTarget(resourceFromRegion(parsed, defaultRegion(parsed.sheets[0], 0)), table, fields.map((field, index) => ({ ...field, dataType: ['字符串(32)', '日期时间(32)'][index] })));
  assert.deepEqual(bound.schema.fields.map(field => field.type), ['text', 'datetime']);
});
