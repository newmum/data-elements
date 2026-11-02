import test,{type TestContext} from 'node:test';
import assert from 'node:assert/strict';
import {loadRegistrationFilesPage,loadReportTablesPage,type UploadManifest,type UploadTable} from '../../src/report/services/uploadWorkbench';
import {applyParsedFile,fileParsingLocked,parsedFileTables,parsingRegions} from '../../src/report/services/reportFiles';
import type {SourceBook} from '../../src/report/core/model';

function table(index=0):UploadTable{return {key:`validation-table-${index}`,tid:`validation-snapshot-${index}`,fileId:`validation-file-${Math.floor(index/2)}`,fileName:'validation.xlsx',tableName:`validation_table_${index}`,tableNameCn:`验证表${index}`,fieldCount:2,recordCount:1,status:'success',sheetName:index%2?'第二工作表':'第一工作表',startRow:1,endRow:2,startColumn:1,endColumn:2,fields:[{columnName:'id',columnComment:'编号',columnType:'varchar',primaryKey:false,nullable:true,isTimestamp:false},{columnName:'name',columnComment:'名称',columnType:'varchar',primaryKey:false,nullable:true,isTimestamp:false}],businessType:'业务表',normalized:true,normalizationVersion:1};}
function manifest(count=2):UploadManifest{return {revision:'validation-v1',tables:Array.from({length:count},(_,i)=>table(i)),files:Array.from({length:Math.ceil(count/2)},(_,i)=>({fileId:`validation-file-${i}`,fileName:'validation.xlsx'})),failedTables:[],receipts:[]};}
function session(t:TestContext){const descriptors=['window','localStorage','sessionStorage'].map(key=>[key,Object.getOwnPropertyDescriptor(globalThis,key)] as const),old=globalThis.fetch;const values=new Map([['token','validation-session']]),storage={getItem:(key:string)=>values.get(key)??null,setItem:(key:string,value:string)=>values.set(key,value),removeItem:(key:string)=>values.delete(key)};Object.defineProperty(globalThis,'window',{configurable:true,value:{location:{href:'http://validation.test/#/report',origin:'http://validation.test',hash:'#/report',search:''}}});for(const key of ['localStorage','sessionStorage'])Object.defineProperty(globalThis,key,{configurable:true,value:storage});t.after(()=>{globalThis.fetch=old;for(const [key,descriptor] of descriptors){if(descriptor)Object.defineProperty(globalThis,key,descriptor);else Reflect.deleteProperty(globalThis,key);}});}

test('20/100 table hierarchies and report pages each use one request and validate nested file ownership',async t=>{
  session(t);
  for(const count of [20,100]){
    const source=manifest(count);let calls=0,foreign=false;
    globalThis.fetch=async(_input,init)=>{calls++;const body=JSON.parse(String(init?.body));assert.equal(body.dbId,'validation-source');assert.equal(body.revision,source.revision);
      const list=body.action==='file-page'?source.files.map((file,i)=>({key:'file:'+file.fileId,...file,tableKeys:[source.tables[i*2].key,foreign?source.tables[(i*2+2)%count].key:source.tables[i*2+1].key],failedKeys:[],updatedAt:'',fieldCount:4,recordCount:2,readyCount:2,locked:false})):source.tables.map(row=>({key:row.key,updatedAt:''}));
      return new Response(JSON.stringify({code:0,data:{revision:source.revision,total:list.length,list,summary:{all:count,pending:count,reported:0}}}));};
    const query={pageNo:1,pageSize:100,search:'',sortField:'updatedAt',sortDir:'desc' as const};
    assert.equal((await loadRegistrationFilesPage('validation-source',source,query)).list.length,count/2);assert.equal(calls,1);
    assert.equal((await loadReportTablesPage('validation-source',source,query,'pending')).list.length,count);assert.equal(calls,2);
    foreign=true;await assert.rejects(loadRegistrationFilesPage('validation-source',source,query),/跨文件/);
    foreign=false;source.tables[0].normalized=false;await assert.rejects(loadReportTablesPage('validation-source',source,query),/未规范/);
    source.receipts=[{id:'validation-receipt',tableId:source.tables[0].tid!,fileId:source.tables[0].fileId,tableName:source.tables[0].tableName,fileName:'validation.xlsx',status:'success',reportedAt:'2026-11-01T00:00:00Z',rowCount:1}];
    assert.equal((await loadReportTablesPage('validation-source',source,query)).list.length,count,'historical successes remain visible and read-only');
  }
});

test('file parsing retains every worksheet region, preserves registered identity and invalidates only this file',()=>{
  const source=manifest(4),file=source.files[0],existing=source.tables.slice(0,2);
  const book:SourceBook={id:'local-file',filename:file.fileName,size:40,importedAt:'2026-11-01',sheets:['第一工作表','第二工作表'].map(name=>({name,cells:[[{v:'id'},{v:'name'}],[{v:'A001'},{v:'验证值'}]],rowCount:2,colCount:2,hidden:false,hiddenRows:[],hiddenCols:[],merges:[],notes:[]}))};
  const regions=parsingRegions(book,existing);assert.equal(regions.length,2);
  const parsed=parsedFileTables(file,book,regions,existing,'utf-8','');
  assert.deepEqual(parsed.map(row=>row.key),existing.map(row=>row.key));assert.ok(parsed.every(row=>!row.normalized&&row.recordCount===1));
  const updated=applyParsedFile(source,file,parsed);
  assert.equal(updated.tables[0],source.tables[2]);assert.deepEqual(updated.tables.slice(2).map(row=>row.tid),existing.map(row=>row.tid));assert.equal(source.tables[0].normalized,true);
  assert.throws(()=>applyParsedFile(source,file,parsed.slice(0,1)),/已登记/);
  assert.throws(()=>applyParsedFile(source,file,[{...parsed[0],tid:source.tables[2].tid},parsed[1]]),/归属/);
  assert.throws(()=>applyParsedFile(source,file,[{...parsed[0],key:source.tables[2].key},parsed[1]]),/跨文件/);
  source.receipts=[{id:'receipt',tableId:existing[0].tid!,fileId:file.fileId,tableName:existing[0].tableName,fileName:file.fileName,status:'success',rowCount:1,reportedAt:'2026-11-01'}];
  assert.equal(fileParsingLocked(file.fileId,source),false);assert.equal(applyParsedFile(source,file,parsed).tables.length,source.tables.length);
  source.receipts[0].status='sending';assert.equal(fileParsingLocked(file.fileId,source),true);assert.throws(()=>applyParsedFile(source,file,parsed),/正在上报/);
});

test('multiple regions on one worksheet cannot silently overlap or discard a registered region',()=>{
  const source=manifest(),file=source.files[0],book:SourceBook={id:'file',filename:file.fileName,size:40,importedAt:'2026-11-01',sheets:[{name:'第一工作表',cells:[[{v:'id'},{v:'name'}],[{v:'A001'},{v:'验证值'}]],rowCount:2,colCount:2,hidden:false,hiddenRows:[],hiddenCols:[],merges:[],notes:[]}]};
  const regions=parsingRegions(book,[source.tables[0]]);regions.push({...regions[0],id:'new-region'});
  assert.throws(()=>parsedFileTables(file,book,regions,source.tables,'utf-8',''),/重叠/);
});
