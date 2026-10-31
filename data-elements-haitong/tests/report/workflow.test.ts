import test from 'node:test';
import assert from 'node:assert/strict';
import {hasReportSuccess,isReportReady,reportSchemaIssues,reportFieldIssues,REPORT_BUSINESS_TYPES} from '../../src/report/services/reportSchema';
import {applyTableSchema,tableResource,normalizedReportFile,type UploadTable} from '../../src/report/services/uploadWorkbench';
import type {SourceBook} from '../../src/report/core/model';

const table=(count=2):UploadTable=>({key:'validation',tid:'registered',fileId:'file',fileName:'validation.csv',sheetName:'sheet',tableName:'validation_events',tableNameCn:'上报验证',businessType:'业务表',status:'success',fieldCount:count,recordCount:1,
  startRow:1,endRow:2,startColumn:1,endColumn:count,
  fields:Array.from({length:count},(_,i)=>({columnName:'field_'+i,columnComment:'字段'+i,columnType:'varchar',nullable:true,primaryKey:false,isTimestamp:false}))});
test('only explicitly saved valid tables progress to reporting; missing categories stay pending',()=>{
  const draft=table();assert.equal(reportSchemaIssues(draft).length,0);assert.equal(isReportReady(draft),false);
  draft.normalized=true;assert.equal(isReportReady(draft),false);draft.normalizationVersion=1;assert.equal(isReportReady(draft),true);
  draft.businessType='';assert.equal(isReportReady(draft),false);assert.ok(reportSchemaIssues(draft).includes('选择表类型'));
  draft.businessType='日志表';draft.normalized=false;assert.equal(isReportReady(draft),false);
});
test('legacy success remains viewable but never grants a new normalization approval',()=>{
  const row=table();assert.equal(isReportReady(row),false);
  assert.equal(hasReportSuccess(row,[{id:'receipt',fileId:'file',tableId:'registered',tableName:'validation_events',fileName:'validation.csv',status:'success',rowCount:1,reportedAt:''}]),true);
  row.tid='other';assert.equal(hasReportSuccess(row,[{tableId:'registered',status:'success'} as never]),false);
});
test('same registration classifications, dictionaries and optional timestamp rules are respected',()=>{
  const row=table();for(const category of REPORT_BUSINESS_TYPES.filter(x=>x!=='字典表')){row.businessType=category;assert.equal(reportSchemaIssues(row).length,0);}
  row.businessType='字典表';assert.ok(reportSchemaIssues(row).length);row.fields[0].dictionaryRole='code';row.fields[1].dictionaryRole='label';assert.equal(reportSchemaIssues(row).length,0);
  row.fields[0].isTimestamp=true;assert.ok(reportFieldIssues(row.fields[0],row.fields).length);row.fields[0].standardField='DATETIME';assert.equal(reportSchemaIssues(row).length,0);
  row.fields[1].isTimestamp=true;row.fields[1].standardField='DATE';assert.ok(reportSchemaIssues(row).some(x=>x.includes('一个时间戳')));
});
test('20/100 fields validate locally, reject duplicate names and do not need network reads',()=>{
  for(const count of [20,100]){const row=table(count);row.normalized=true;row.normalizationVersion=1;assert.equal(isReportReady(row),true);row.fields[count-1].columnName=row.fields[0].columnName;assert.equal(isReportReady(row),false);}
});
test('manual bilingual header/data-start selection skips title/header rows and uses normalized format',async()=>{
  const cells=[['说明'],['id','occurred_at'],['编号','发生时间'],['001','2026-01-01 12:00:00']].map(row=>row.map(v=>({v})));
  const book:SourceBook={id:'file',filename:'validation.xlsx',size:100,importedAt:'',sheets:[{name:'sheet',cells,rowCount:4,colCount:2,hidden:false,hiddenRows:[],hiddenCols:[],merges:[],notes:[]}]};
  const row={...table(),startRow:2,endRow:4,startColumn:1,endColumn:2,headerMode:'bilingual' as const,dataStartRow:4};
  row.fields[1].standardField='DATETIME';row.fields[1].isTimestamp=true;
  const resource=tableResource(book,row);assert.equal(resource.rows.length,1);assert.equal(resource.schema.fields[1].type,'datetime');assert.equal(resource.schema.timestamp,resource.schema.fields[1].id);
  assert.equal(tableResource(book,{...row,dataStartRow:undefined}).rows.length,1);
  const text=await normalizedReportFile(resource).text();assert.match(text,/001/);assert.match(text,/2026-01-01/);assert.doesNotMatch(text,/说明|发生时间/);
});
test('source time units and DATE/DATETIME output are applied before approval or delivery',async()=>{
  const row={...table(1),startRow:1,endRow:2,startColumn:1,endColumn:1};
  const book:SourceBook={id:'file',filename:'validation.csv',size:100,importedAt:'',sheets:[{name:'sheet',cells:[[{v:'发生时间'}],[{v:'1767268800'}]],rowCount:2,colCount:1,hidden:false,hiddenRows:[],hiddenCols:[],merges:[],notes:[]}]};
  row.fields[0].isTimestamp=true;row.fields[0].standardField='DATETIME';row.fields[0].timeFormat='unix-s';
  const resource=tableResource(book,row);assert.match(await normalizedReportFile(resource).text(),/2026-01-01T12:00:00.000Z/);
  row.fields[0].standardField='DATE';assert.match(await normalizedReportFile(applyTableSchema(resource,row)).text(),/"2026-01-01"/);
  row.fields[0].standardField='DATETIME';row.fields[0].timeFormat='ymd';resource.rows[0].values[resource.schema.fields[0].id]='2026-01-01';
  assert.throws(()=>normalizedReportFile(applyTableSchema(resource,row)),/缺少时分秒/);
});
test('optional primary keys, when selected, reject duplicates and blank values',()=>{
  const row=table(1);row.fields[0].primaryKey=true;row.fields[0].nullable=false;
  const book:SourceBook={id:'file',filename:'validation.csv',size:100,importedAt:'',sheets:[{name:'sheet',cells:[[{v:'编号'}],[{v:'1'}],[{v:'1'}]],rowCount:3,colCount:1,hidden:false,hiddenRows:[],hiddenCols:[],merges:[],notes:[]}]};
  row.endRow=3;const resource=tableResource(book,row);assert.throws(()=>normalizedReportFile(resource),/主键重复/);
  resource.rows[1].values[resource.schema.fields[0].id]='';assert.throws(()=>normalizedReportFile(resource),/不允许为空/);
});
