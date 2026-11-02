import test,{type TestContext} from 'node:test';
import assert from 'node:assert/strict';
import {loadUploadContext,loadRegistrationPage,normalizedReportFile,parseUploadedFiles,tableResource,type UploadTable} from '../../src/report/services/uploadWorkbench';
import {createReportSourceLoader} from '../../src/report/services/reportSource';
import type {SourceBook} from '../../src/report/core/model';

function sample(){
 const book:SourceBook={id:'file',filename:'people.csv',size:20,importedAt:'2026-10-27',sheets:[{name:'people',cells:[[{v:'编号'},{v:'说明'}],[{v:'001'},{v:'原始值'}]],hidden:false,hiddenRows:[],hiddenCols:[],merges:[],rowCount:2,colCount:2,notes:[]}]};
 const table:UploadTable={key:'table',fileId:'file',fileName:book.filename,sheetName:'people',tableName:'people',tableNameCn:'人员',fieldCount:2,recordCount:1,status:'success',startRow:1,endRow:2,startColumn:1,endColumn:2,fields:[{columnName:'id',columnComment:'编号',columnType:'varchar',primaryKey:false,nullable:true,isTimestamp:false},{columnName:'description',columnComment:'说明',columnType:'varchar',primaryKey:false,nullable:true,isTimestamp:false}]};
 return tableResource(book,table);
}
test('normalized delivery uses current field names and edited row values, one table per CSV',async()=>{
 const resource=sample(),field=resource.schema.fields[1];field.en='remark';resource.rows[0].values[field.id]='修改,"内容"';
 const file=normalizedReportFile(resource);assert.equal(file.name,'people.csv');const text=await file.text();
 assert.match(text,/"id","remark"/);assert.match(text,/"001","修改,""内容"""/);assert.doesNotMatch(text,/原始值/);
});
test('invalid numeric and non-null rules prevent delivery before any request',()=>{
 const resource=sample(),field=resource.schema.fields[0];field.type='integer';assert.throws(()=>normalizedReportFile(resource),/前导零/);
 field.type='text';field.nullable='forbid';resource.rows[0].values[field.id]='';assert.throws(()=>normalizedReportFile(resource),/不允许为空/);
});
function contextFixture(t:TestContext,count:number){
 const descriptors=['window','localStorage','sessionStorage'].map(key=>[key,Object.getOwnPropertyDescriptor(globalThis,key)] as const),previousFetch=globalThis.fetch;
 const stored=new Map([['token','test-platform-token']]);const storage={getItem:(key:string)=>stored.get(key)??null,setItem:(key:string,value:string)=>stored.set(key,value),removeItem:(key:string)=>stored.delete(key)};
 Object.defineProperty(globalThis,'window',{configurable:true,value:{location:{href:'http://report.test/#/report',origin:'http://report.test',hash:'#/report',search:''}}});
 for(const key of ['localStorage','sessionStorage'])Object.defineProperty(globalThis,key,{configurable:true,value:storage});
 const requests:string[]=[];globalThis.fetch=async input=>{const path=new URL(String(input),'http://report.test').pathname.replace(/^\/(?:prod|dev)-api/,'');requests.push(path);
  const data=path==='/sym/user/me'?{id:'user'}:path==='/sym/tenant/current'?{tid:'tenant'}:{list:Array.from({length:count},(_,i)=>({tid:'source-'+i,dbName:'数据源'+i,accessMode:'upload'}))};return new Response(JSON.stringify({code:0,data}));};
 t.after(()=>{globalThis.fetch=previousFetch;for(const[key,descriptor]of descriptors){if(descriptor)Object.defineProperty(globalThis,key,descriptor);else Reflect.deleteProperty(globalThis,key);}});return requests;
}
test('20/100 datasource choices need three reads and allow a first upload without registered tables',async t=>{
 for(const count of[20,100])await t.test(String(count),async child=>{const calls=contextFixture(child,count);const result=await loadUploadContext('source-0');assert.equal(result.sources.length,count);assert.equal(result.context.tables.length,0);assert.equal(result.context.datasourceId,'source-0');assert.equal(calls.length,3);});
});
test('untrusted URL datasource cannot fall back to another source',async t=>{contextFixture(t,20);await assert.rejects(loadUploadContext('foreign-source'),/不存在|不是/);});

test('a 20-file parse keeps one batch request and permits the bounded storage reads to finish',async t=>{
 contextFixture(t,20);const originalTimeout=AbortSignal.timeout,deadlines:number[]=[];
 AbortSignal.timeout=milliseconds=>{deadlines.push(milliseconds);return new AbortController().signal;};
 t.after(()=>{AbortSignal.timeout=originalTimeout;});
 for(const count of[1,20]){
  let reads=0;const ids=Array.from({length:count},(_,i)=>'validation-file-'+i);
  globalThis.fetch=async(_input,init)=>{reads++;const body=JSON.parse(String(init?.body));assert.equal(body.action,'parse');assert.deepEqual(body.fileIds,ids);return new Response(JSON.stringify({code:0,data:{files:[]}}));};
  await parseUploadedFiles(ids);assert.equal(reads,1);assert.ok(deadlines.at(-1)!>=count*20_000+10_000);
 }
});

test('20/100 registration rows use one page request and reject unexpected table keys',async t=>{
 for(const count of[20,100])await t.test(String(count),async child=>{
  contextFixture(child,count);let requests=0,foreign=false;
  const tables=Array.from({length:count},(_,i)=>({...sampleTable(),key:'table-'+i}));
  globalThis.fetch=async(_input,init)=>{requests++;const body=JSON.parse(String(init?.body));assert.equal(body.action,'table-page');assert.equal(body.sortField,'updatedAt');assert.equal(body.sortDir,'desc');
   return new Response(JSON.stringify({code:0,data:{revision:'v1',total:count,list:tables.map(table=>({key:foreign?'foreign-key':table.key,updatedAt:'2026-11-01T00:00:00Z'}))}}));};
  const manifest={revision:'v1',files:[],tables};const query={pageNo:1,pageSize:count,search:'',sortField:'updatedAt',sortDir:'desc' as const};
  assert.equal((await loadRegistrationPage('source',manifest,query)).list.length,count);assert.equal(requests,1);
  foreign=true;await assert.rejects(loadRegistrationPage('source',manifest,query),/不属于当前数据源/);
 });
});
function sampleTable():UploadTable{return{key:'table',fileId:'file',fileName:'data.csv',tableName:'data_table',tableNameCn:'数据表',fields:[],fieldCount:0,recordCount:1,status:'success'};}
test('changing source uses one request without reloading user, tenant or candidate sources',async t=>{
 for(const count of [20,100])await t.test(String(count),async child=>{
  const calls=contextFixture(child,count),context=await loadUploadContext('source-0');calls.length=0;
  const previousFetch=globalThis.fetch;globalThis.fetch=async(input,init)=>{
   const path=String(input);if(path.includes('/report-registration')){
    calls.push('/dst/database/metadata/report-registration');assert.equal(JSON.parse(String(init?.body)).dbId,'source-'+(count-1));
    return new Response(JSON.stringify({code:0,data:{revision:'selected',files:[],tables:[],receipts:[]}}));
   }return previousFetch(input,init);
  };
  const loaded=await createReportSourceLoader().load('source-'+(count-1),context.sources);assert.equal(loaded?.manifest.revision,'selected');
  assert.deepEqual(calls,['/dst/database/metadata/report-registration']);
 });
});
