import test from 'node:test';
import assert from 'node:assert/strict';
import {existsSync} from 'node:fs';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import UploadProgress from '../../src/report/components/UploadProgress';
import {reportStepCompletion,withDefaultTableTypes} from '../../src/report/services/reportProgress';
import {inferBusinessType} from '../../src/report/services/tableClassification';
import type {UploadManifest,UploadTable} from '../../src/report/services/uploadWorkbench';

function table(key='table'):UploadTable{return{key,tid:key,fileId:'file',fileName:'events.csv',sheetName:'events',tableName:key,tableNameCn:'业务数据',fieldCount:1,recordCount:1,status:'success',startRow:1,endRow:2,startColumn:1,endColumn:1,businessType:'业务表',normalized:true,normalizationVersion:1,fields:[{columnName:'id',columnComment:'编号',columnType:'varchar',primaryKey:false,nullable:true,isTimestamp:false}]};}
function manifest(tables:UploadTable[]=[]):UploadManifest{return{revision:'v1',files:[{fileId:'file',fileName:'events.csv'}],tables,receipts:[],failedTables:[]};}
test('upload status reports real phases and processed file counts without a fake percentage',()=>{
 const uploading=renderToStaticMarkup(createElement(UploadProgress,{stage:{phase:0,completed:2,total:5}}));
 assert.match(uploading,/已处理 2 \/ 5 份文件/);assert.match(uploading,/aria-live="polite"/);assert.doesNotMatch(uploading,/%/);
 for(const phase of[1,2] as const){const markup=renderToStaticMarkup(createElement(UploadProgress,{stage:{phase,completed:5,total:5}}));assert.match(markup,phase===1?/正在识别表头与数据表/:/正在准备登记信息/);}
});
test('step badges reflect actual upload, all-table normalization and any successful report',()=>{
 assert.deepEqual(reportStepCompletion({...manifest(),files:[]}),{uploaded:false,normalized:false,reported:false});
 assert.deepEqual(reportStepCompletion(manifest()),{uploaded:true,normalized:false,reported:false});
 const approved=manifest([table()]);assert.deepEqual(reportStepCompletion(approved),{uploaded:true,normalized:true,reported:false});
 const partial=manifest([table(),{...table('pending'),normalized:false}]);partial.receipts=[{id:'receipt',fileId:'file',tableId:'table',tableName:'table',fileName:'events.csv',status:'success',rowCount:1,reportedAt:'2026-11-01'}];
 assert.deepEqual(reportStepCompletion(partial),{uploaded:true,normalized:false,reported:true});
 approved.failedTables=[{...table('failed'),status:'failed'}];assert.equal(reportStepCompletion(approved).normalized,false);
 const missing=manifest([table()]);missing.files.push({fileId:'unparsed',fileName:'unparsed.csv'});assert.equal(reportStepCompletion(missing).normalized,false);
});
test('auto typing preserves manual decisions and historic successful schemas',()=>{
 const source=manifest([{...table(),businessType:'日志表'},{...table('dict'),businessType:'',tableName:'sys_dict',normalized:false}]);
 const result=withDefaultTableTypes(source);assert.equal(result.tables[0].businessType,'日志表');assert.equal(result.tables[1].businessType,'字典表');assert.equal(source.tables[1].businessType,'');
 source.receipts=[{id:'old',fileId:'file',tableId:'dict',tableName:'dict',fileName:'events.csv',status:'success',rowCount:1,reportedAt:'2026-11-01'}];
 assert.equal(withDefaultTableTypes(source).tables[1],source.tables[1]);
});
test('20/100 inferred defaults require no network reads and match registration rules',async()=>{
 const cases=[['case_info','案件受理','业务表'],['app_log','访问日志','日志表'],['sys_dict','字典数据','字典表'],['approval_flow','审批过程','过程表'],['audit_backup','历史审计备份','备份表']];
 const original=new URL('../../../data-elements-chengtian/src/utils/metadata-table-classification.js',import.meta.url);
 const reference=existsSync(original)?(await import(original.href)).inferBusinessType:undefined;
 const previous=globalThis.fetch;let reads=0;globalThis.fetch=async()=>{reads++;throw new Error('classification must be local');};
 try{for(const count of[20,100]){
  const rows=Array.from({length:count},(_,index)=>{const[name,comment,expected]=cases[index%cases.length];const row={...table('row_'+index),tableName:name,tableNameCn:comment,businessType:''};assert.equal(inferBusinessType(row).type,expected);if(reference)assert.deepEqual(inferBusinessType(row),reference(row));return row;});
  assert.equal(withDefaultTableTypes(manifest(rows)).tables.length,count);assert.equal(reads,0);
 }}finally{globalThis.fetch=previous;}
});
