import type { ReportTable, ReportTableField } from './reportApi';

const EXCEL_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

function safeFilePart(value: string): string {
  return value.replace(/[\\/:*?"<>|]/g, '_').trim().slice(0, 56) || '数据表';
}

export function reportSheetName(table: Pick<ReportTable, 'displayName'>): string {
  return safeFilePart(table.displayName).replace(/[\[\]]/g, '_').replace(/^'+|'+$/g, '').slice(0, 31) || '数据表';
}

/** English identifiers distinguish fields even when their Chinese labels repeat. */
export function reportFieldHeader(field: Pick<ReportTableField, 'name' | 'displayName'>): string {
  const name = field.name.trim(), label = field.displayName.trim();
  return label && label !== name ? `${label}（${name}）` : name;
}

export function reportTemplateHeaders(fields: ReportTableField[]): string[] {
  const names = fields.map(field => field.name.trim().toLowerCase());
  if (!names.length || names.some(name => !name) || new Set(names).size !== names.length) {
    throw new Error('登记字段英文名为空或重复，无法生成唯一表头，请先修正字段登记信息');
  }
  return fields.map(reportFieldHeader);
}

/** Preserve the template's column order and reject ambiguous old Chinese headers. */
export function assertReportUploadHeaders(headers: string[], fields: ReportTableField[]): void {
  const expected = reportTemplateHeaders(fields);
  const actual = headers.map(header => header.trim());
  if (new Set(actual).size !== actual.length) {
    throw new Error('上传文件存在重复表头，无法区分同名字段，请重新下载“中文名（英文名）”模板后填写');
  }
  if (actual.length !== expected.length) {
    throw new Error(`上传文件字段数与登记不一致：应为 ${expected.length} 列，实际 ${actual.length} 列，请使用当前表模板`);
  }
  actual.forEach((header, index) => {
    const field = fields[index], label = field.displayName.trim();
    const uniqueLabel = label && fields.filter(candidate => candidate.displayName.trim() === label).length === 1;
    const asciiHeader = expected[index].replace(/（/g, '(').replace(/）/g, ')');
    if (header === expected[index] || header === asciiHeader || header === field.name.trim() || (uniqueLabel && header === label)) return;
    throw new Error(`第 ${index + 1} 列表头不匹配，应为“${expected[index]}”。请使用当前表模板，勿修改表头或调整列顺序`);
  });
}

export async function createReportUploadTemplate(table: ReportTable, fields: ReportTableField[]): Promise<ArrayBuffer> {
  const headers = reportTemplateHeaders(fields);
  const XLSX = await import('xlsx');
  const workbook = XLSX.utils.book_new();
  const worksheet = XLSX.utils.aoa_to_sheet([headers]);
  worksheet['!cols'] = headers.map(header => ({ wch: Math.max(18, Math.min(64, [...header].reduce((width, char) => width + (char.charCodeAt(0) > 255 ? 2 : 1), 0) + 4)) }));
  worksheet['!freeze'] = { xSplit: 0, ySplit: 1 };
  XLSX.utils.book_append_sheet(workbook, worksheet, reportSheetName(table));
  return XLSX.write(workbook, { bookType: 'xlsx', type: 'array', compression: true });
}

/**
 * Creates a one-sheet data-entry workbook from the selected table's registered
 * fields.  A report workbook must contain only its data sheet: placing an
 * instructions sheet before it makes generic Excel readers infer an unrelated
 * one-column schema before they reach the actual table data.
 */
export async function downloadReportUploadTemplate(table: ReportTable, fields: ReportTableField[]): Promise<void> {
  if (!fields.length) throw new Error(`“${table.displayName}”尚未读取到已登记字段，暂不能生成模板`);
  const bytes = await createReportUploadTemplate(table, fields);
  const url = URL.createObjectURL(new Blob([bytes], { type: EXCEL_MIME }));
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `${safeFilePart(table.displayName)}_数据上报模板.xlsx`;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1_000);
}
