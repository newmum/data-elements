import test from 'node:test';
import assert from 'node:assert/strict';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {clone,type SourceBook} from '../../src/report/core/model';
import {deliveryManifest,hasRegistrationDraft,rebaseRegistrationDraft,savedDeliveryTable,resourceWithDeliveryReceipt} from '../../src/report/services/reportDelivery';
import {normalizedReportFile,tableResource,type UploadManifest,type UploadTable} from '../../src/report/services/uploadWorkbench';
import {isReportReady} from '../../src/report/services/reportSchema';
import ReportDeliveryTable from '../../src/report/components/ReportDeliveryTable';

function table(key:string):UploadTable{return {key,tid:key+'-id',fileId:key+'-file',fileName:key+'.csv',sheetName:'Sheet1',tableName:'validation_'+key,tableNameCn:'验证数据表'+key,businessType:'业务表',fieldCount:2,recordCount:1,status:'success',normalized:true,normalizationVersion:1,startRow:1,endRow:2,startColumn:1,endColumn:2,
  fields:[{columnName:'record_id',columnComment:'记录编号',columnType:'varchar',primaryKey:true,nullable:false,isTimestamp:false},{columnName:'occurred_at',columnComment:'发生时间',columnType:'varchar',standardField:'DATETIME',timeFormat:'iso',timezone:'Asia/Shanghai',primaryKey:false,nullable:false,isTimestamp:false}]};}
function saved():UploadManifest{return {revision:'saved-1',files:[{fileId:'a-file',fileName:'a.csv'},{fileId:'b-file',fileName:'b.csv'}],tables:[table('a'),table('b')],receipts:[]};}

test('successful history uses its own schema snapshot after current registration is edited',()=>{
  const baseline=saved(),original=clone(baseline.tables[0]);
  baseline.receipts=[{id:'receipt',fileId:'a-file',fileName:'a.csv',tableId:'a-id',tableName:original.tableName,status:'success',rowCount:1,reportedAt:'2026-11-01T14:47:00Z',registrationSnapshot:original}];
  baseline.tables[0].tableNameCn='更新的登记名称';baseline.tables[0].fields[0].columnName='updated_id';
  const historical=savedDeliveryTable(baseline,'a');
  assert.equal(historical.tableNameCn,original.tableNameCn);assert.equal(historical.fields[0].columnName,'record_id');
  assert.equal(baseline.tables[0].fields[0].columnName,'updated_id');assert.equal(baseline.receipts[0].status,'success');
});

test('invalid unsaved schema cannot affect saved delivery headers or converted values',async()=>{
  const baseline=saved(),draft=clone(baseline);draft.tables[0].tableName='';draft.tables[0].fields[1].standardField='SFZH';draft.tables[0].normalized=false;
  assert.equal(hasRegistrationDraft(baseline,draft),true);
  const snapshot=savedDeliveryTable(baseline,'a');assert.equal(isReportReady(snapshot,baseline.tables),true);
  const book:SourceBook={id:'a-file',filename:'a.csv',size:30,importedAt:'2026-11-01',sheets:[{name:'Sheet1',cells:[[{v:'id'},{v:'time'}],[{v:'A1'},{v:'2026-11-01 09:00:00'}]],hidden:false,hiddenRows:[],hiddenCols:[],merges:[],rowCount:2,colCount:2,notes:[]}]};
  const output=await normalizedReportFile(tableResource(book,snapshot)).text();
  assert.match(output,/"record_id","occurred_at"/);assert.match(output,/2026-11-01T09:00:00\+08:00/);
  assert.equal(draft.tables[0].fields[1].standardField,'SFZH');assert.equal(draft.tables[0].tableName,'');
});

test('saving one table preserves another table draft and its previously approved delivery version',()=>{
  const baseline=saved(),draft=clone(baseline);draft.tables[0].tableNameCn='新的中文名称';draft.tables[0].normalized=false;draft.tables[1].tableName='';draft.tables[1].normalized=false;
  const next=clone(baseline);next.revision='saved-2';next.tables[0].tableNameCn='新的中文名称';
  const editor=rebaseRegistrationDraft(baseline,draft,next,['a']);
  assert.equal(editor.revision,'saved-2');assert.equal(editor.tables[0].normalized,true);assert.equal(editor.tables[1].tableName,'');
  assert.equal(savedDeliveryTable(next,'b').tableName,'validation_b');assert.equal(hasRegistrationDraft(next,editor),true);
});

test('delivery updates only its row count and never submits, discards or approves registration drafts',()=>{
  const baseline=saved(),draft=clone(baseline);draft.tables[0].fields[0].columnName='pending_name';draft.tables[0].normalized=false;draft.tables[1].tableNameCn='未保存修改';draft.tables[1].normalized=false;
  const payload=deliveryManifest(baseline,'a',2);
  assert.equal(payload.tables[0].recordCount,2);assert.equal(baseline.tables[0].recordCount,1);assert.equal(payload.tables[0].fields[0].columnName,'record_id');assert.equal(payload.tables[1].tableNameCn,baseline.tables[1].tableNameCn);
  const response={...payload,revision:'saved-2'},editor=rebaseRegistrationDraft(baseline,draft,response);
  assert.equal(editor.tables[0].recordCount,2);assert.equal(editor.tables[0].fields[0].columnName,'pending_name');assert.equal(editor.tables[1].tableNameCn,'未保存修改');
  assert.equal(isReportReady(savedDeliveryTable(response,'a'),response.tables),true);assert.equal(hasRegistrationDraft(response,editor),true);
});

test('delivery rejects unknown and unapproved saved tables, and returned schema cannot mutate the saved version',()=>{
  const baseline=saved();baseline.tables[1].normalized=false;
  assert.throws(()=>savedDeliveryTable(baseline,'foreign'));assert.throws(()=>savedDeliveryTable(baseline,'b'));
  const selected=savedDeliveryTable(baseline,'a');selected.fields[0].columnName='changed';assert.equal(baseline.tables[0].fields[0].columnName,'record_id');
});

test('reported rows offer read-only data viewing while pending rows offer reporting',()=>{
  const baseline=saved();baseline.receipts=[{id:'receipt',fileId:'a-file',fileName:'a.csv',tableId:'a-id',tableName:'validation_a',status:'success',rowCount:1,reportedAt:'2026-11-01T01:00:00Z'}];
  const markup=renderToStaticMarkup(createElement(ReportDeliveryTable,{rows:baseline.tables,manifest:baseline,busy:false,onReport:()=>{},onPreview:()=>{}}));
  assert.match(markup,/>查看数据</);assert.match(markup,/>上报数据</);assert.doesNotMatch(markup,/请先保存第二步的规范修改/);
});

test('file delivery receipts populate row display without inventing individual ingestion acknowledgments',()=>{
  const target=table('a'),book:SourceBook={id:'a-file',filename:'a.csv',size:30,importedAt:'2026-11-01',sheets:[{name:'Sheet1',cells:[[{v:'id'},{v:'time'}],[{v:'A1'},{v:'2026-11-01 09:00:00'}]],hidden:false,hiddenRows:[],hiddenCols:[],merges:[],rowCount:2,colCount:2,notes:[]}]};
  const resource=tableResource(book,target),receipt={id:'receipt',fileId:'a-file',fileName:'a.csv',tableId:'a-id',tableName:'validation_a',status:'failed',rowCount:1,reportedAt:'2026-11-01T01:00:00Z'};
  assert.deepEqual(Object.values(resourceWithDeliveryReceipt(resource,target,[receipt]).sync),['failed']);
  assert.deepEqual(Object.values(resourceWithDeliveryReceipt(resource,target,[]).sync),['idle']);
  const confirmed=resourceWithDeliveryReceipt(resource,target,[receipt,{...receipt,id:'success',status:'success'}]);
  assert.equal(confirmed.locked,true);assert.deepEqual(Object.values(confirmed.sync),['success']);assert.deepEqual(resource.sync,{});
});
