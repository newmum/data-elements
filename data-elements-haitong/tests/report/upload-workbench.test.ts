import test,{type TestContext} from 'node:test';
import assert from 'node:assert/strict';
import {loadUploadContext,normalizedReportFile,tableResource,type UploadTable} from '../../src/report/services/uploadWorkbench';
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
