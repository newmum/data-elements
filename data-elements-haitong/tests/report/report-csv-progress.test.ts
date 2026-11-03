import test from 'node:test';
import assert from 'node:assert/strict';
import {normalizedReportFile,prepareReportFile} from '../../src/report/services/reportCsv';
import {tableResource,type UploadTable} from '../../src/report/services/uploadWorkbench';
import type {SourceBook} from '../../src/report/core/model';

function fixture(){
  const table:UploadTable={key:'validation_progress',tid:'validation_table',fileId:'validation_file',fileName:'validation.csv',sheetName:'data',tableName:'validation_progress',tableNameCn:'进度验证表',businessType:'业务表',fieldCount:2,recordCount:2,status:'success',startRow:1,endRow:3,startColumn:1,endColumn:2,fields:[
    {columnName:'record_id',columnComment:'记录编号',columnType:'varchar',primaryKey:true,nullable:false,isTimestamp:false},
    {columnName:'occurred_at',columnComment:'发生时间',columnType:'datetime',standardField:'DATETIME',primaryKey:false,nullable:false,isTimestamp:true},
  ]};
  const book:SourceBook={id:'validation_file',filename:'validation.csv',size:60,importedAt:'2026-11-03',sheets:[{name:'data',cells:[[{v:'编号'},{v:'时间'}],[{v:'V1'},{v:'2026-11-03 09:00:00'}],[{v:'V2'},{v:'2026-11-03T09:00:00+09:00'}]],rowCount:3,colCount:2,hidden:false,hiddenRows:[],hiddenCols:[],merges:[],notes:[]}]};
  return {table,resource:tableResource(book,table)};
}

test('hidden time-source settings have safe defaults and retain explicit source offsets',async()=>{
  const {table,resource}=fixture();
  assert.deepEqual(resource.schema.fields[1].time,{format:'iso',timezone:'Asia/Shanghai'});
  const csv=await normalizedReportFile(resource).text();
  assert.match(csv,/2026-11-03T09:00:00\+08:00/);assert.match(csv,/2026-11-03T09:00:00\+09:00/);
  assert.equal(table.fields[1].timeFormat,undefined);assert.equal(table.fields[1].timezone,undefined);
  assert.equal(await (await prepareReportFile(resource)).text(),csv);
});

test('100,000-row preparation reports actual row counts, yields, and preserves exact CSV output',async()=>{
  const {resource}=fixture(),[id,time]=resource.schema.fields;
  resource.rows=Array.from({length:100_000},(_,i)=>({id:`row-${i}`,sourceRow:i+2,formulaFields:[],precisionFields:[],values:{[id.id]:`V${i}`,[time.id]:'2026-11-03 09:00:00'}}));
  let ticks=0;const timer=setTimeout(()=>ticks++,0);const seen:number[]=[];
  const file=await prepareReportFile(resource,value=>{assert.equal(value.total,100_000);seen.push(value.completed);});clearTimeout(timer);
  assert.ok(ticks>0);assert.equal(seen[0],0);assert.equal(seen.at(-1),100_000);assert.equal(seen.length,101);
  assert.ok(file.size<10*1024*1024);assert.equal(await file.text(),await normalizedReportFile(resource).text());
});

test('duplicates across chunks and cancellation stop before any upload',async()=>{
  const {resource}=fixture(),[id,time]=resource.schema.fields;
  resource.rows=Array.from({length:2001},(_,i)=>({id:`row-${i}`,sourceRow:i+2,formulaFields:[],precisionFields:[],values:{[id.id]:i===2000?'V0':`V${i}`,[time.id]:'2026-11-03 09:00:00'}}));
  await assert.rejects(prepareReportFile(resource),/第2002行 主键重复/);
  const controller=new AbortController();
  await assert.rejects(prepareReportFile(resource,value=>{if(value.completed===1000)controller.abort();},controller.signal),{name:'AbortError'});
});

test('wide tables yield by field volume and stop at the byte limit without materializing all output',async()=>{
  const {resource}=fixture(),template=resource.schema.fields[0];
  resource.schema.fields=Array.from({length:200},(_,i)=>({...template,id:`field-${i}`,en:`field_${i}`,pk:false}));
  const values=Object.fromEntries(resource.schema.fields.map(field=>[field.id,'x'.repeat(1000)]));
  resource.rows=Array.from({length:100},(_,i)=>({id:`row-${i}`,sourceRow:i+2,formulaFields:[],precisionFields:[],values}));
  const processed:number[]=[];
  await assert.rejects(prepareReportFile(resource,value=>processed.push(value.completed)),/超过10 MB/);
  assert.ok(processed.length>1);assert.ok(processed.at(-1)!<100);
});
