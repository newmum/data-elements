import type { ReportReceipt,UploadField,UploadTable } from './uploadWorkbench';

// Same table categories and DATE/DATETIME contracts as datasource registration.
export const REPORT_BUSINESS_TYPES=['业务表','日志表','字典表','过程表','备份表'];
export const hasReportSuccess=(table:UploadTable,receipts:ReportReceipt[]=[])=>!!table.tid&&receipts.some(row=>row.tableId===table.tid&&row.status==='success');
export function reportFieldIssues(field:UploadField,fields:UploadField[]):string[]{
  const issues:string[]=[];
  if(!/^[A-Za-z][A-Za-z0-9_]{0,63}$/.test(field.columnName)||fields.filter(row=>row.columnName.toLowerCase()===field.columnName.toLowerCase()).length>1)issues.push('字段英文名无效或重复');
  if(!/[\u4e00-\u9fff]/.test(field.columnComment||''))issues.push('中文名须包含中文');
  if(!['varchar','char','text','int','bigint','decimal','float','double','date','datetime','timestamp','boolean'].includes(field.columnType.toLowerCase()))issues.push('重新选择字段数据类型');
  if(field.isTimestamp&&!/^(date|datetime|timestamp)$/i.test(field.columnType)&&!field.standardField)issues.push('时间戳须选择日期/时间类型或统一格式');
  if(field.standardField&&!['DATE','DATETIME'].includes(field.standardField))issues.push('统一格式无效');
  if(field.timeFormat&&!['iso','ymd','dmy','mdy','unix-s','unix-ms'].includes(field.timeFormat))issues.push('日期时间来源格式无效');
  if(field.timezone&&!['Asia/Shanghai','Asia/Tokyo','UTC','source'].includes(field.timezone))issues.push('来源时区无效');
  return issues;
}
export function reportSchemaIssues(table:UploadTable,tables:UploadTable[]=[table]):string[]{
  const issues:string[]=[];
  if(table.status==='failed'||!table.fields.length)issues.push('请先设置表头和数据区域');
  const headerRows=table.headerMode==='none'?0:table.headerMode==='bilingual'?2:1;
  const dataStart=table.dataStartRow??(table.startRow||0)+headerRows;
  if(table.legacy||!table.fileId||!table.sheetName||!Number.isInteger(table.startRow)||!Number.isInteger(table.endRow)||!Number.isInteger(dataStart)||(table.startRow||0)<1||dataStart<(table.startRow||0)+headerRows||dataStart>(table.endRow||0)||(table.endColumn||table.fields.length)-(table.startColumn||1)+1!==table.fields.length||table.recordCount<1)issues.push('请关联原文件并设置有效的数据区域');
  if(!/^[A-Za-z][A-Za-z0-9_]{0,63}$/.test(table.tableName))issues.push('表名须以字母开头，仅含字母、数字或下划线');
  if(tables.some(row=>row.key!==table.key&&row.tableName.toLowerCase()===table.tableName.toLowerCase()))issues.push('数据表英文名重复');
  if(!/[\u4e00-\u9fff]/.test(table.tableNameCn||''))issues.push('数据表中文名须包含中文');
  if(!REPORT_BUSINESS_TYPES.includes(table.businessType||''))issues.push('选择表类型');
  table.fields.forEach((field,index)=>{
    const label=field.columnComment||`字段${index+1}`;
    issues.push(...reportFieldIssues(field,table.fields).map(issue=>`${label}：${issue}`));
  });
  const timestamps=table.fields.filter(field=>field.isTimestamp).length;
  if(timestamps>1)issues.push('仅能设置一个时间戳字段');
  if(table.businessType==='字典表'&&(table.fields.filter(f=>f.dictionaryRole==='code').length!==1||table.fields.filter(f=>f.dictionaryRole==='label').length!==1))issues.push('分别指定一个字典代码列和名称列');
  return [...new Set(issues)];
}
export function isReportReady(table:UploadTable,tables:UploadTable[]=[table]):boolean{
  return table.normalized===true&&table.normalizationVersion===1&&!!table.tid&&reportSchemaIssues(table,tables).length===0;
}
