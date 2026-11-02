import test from 'node:test';
import assert from 'node:assert/strict';
import {STANDARD_FORMAT_OPTIONS,timeRolesFor,updateReportField} from '../../src/report/services/fieldGovernance';
import {reportFieldIssues} from '../../src/report/services/reportSchema';
import {normalizedReportFile,tableResource,type UploadField,type UploadTable} from '../../src/report/services/uploadWorkbench';
import type {SourceBook} from '../../src/report/core/model';

const field=(name:string):UploadField=>({columnName:name,columnComment:'验证字段',columnType:'varchar',primaryKey:false,nullable:true,isTimestamp:false,standardField:'DATETIME'});
test('time property selection moves only the chosen roles and keeps legacy timestamp compatibility',()=>{
  const fields=[{...field('source_time'),isTimestamp:true,timeRoles:['timestamp','business'] as const},field('target_time')].map(row=>({...row,timeRoles:'timeRoles'in row?[...row.timeRoles]:undefined}));
  assert.deepEqual(timeRolesFor({...field('legacy_time'),isTimestamp:true}),['timestamp']);
  const next=updateReportField(fields,1,{timeRoles:['timestamp','occurrence']});
  assert.deepEqual(next[0].timeRoles,['business']);assert.equal(next[0].isTimestamp,false);
  assert.deepEqual(next[1].timeRoles,['timestamp','occurrence']);assert.equal(next[1].isTimestamp,true);
  const text=updateReportField(next,1,{standardField:'LXDH'});assert.deepEqual(text[1].timeRoles,[]);assert.equal(text[1].isTimestamp,false);
  const keyed=updateReportField(text,1,{primaryKey:true});assert.equal(keyed[1].nullable,false);assert.equal(fields[1].primaryKey,false);
});
test('standard phone and identity formats are applied to actual delivery text, retaining leading zeroes',async()=>{
  assert.deepEqual(STANDARD_FORMAT_OPTIONS.map(row=>row.value),['LXDH','SFZH','DATE','DATETIME']);
  const table:UploadTable={key:'validation-format',fileId:'validation-file',fileName:'validation.csv',tableName:'validation_formats',tableNameCn:'格式验证表',sheetName:'validation',businessType:'业务表',fieldCount:2,recordCount:1,status:'success',startRow:1,endRow:2,startColumn:1,endColumn:2,
    fields:[{...field('mobile'),standardField:'LXDH'},{...field('identity'),standardField:'SFZH'}]};
  const book:SourceBook={id:'validation-file',filename:'validation.csv',size:50,importedAt:'2026-11-01',sheets:[{name:'validation',cells:[[{v:'手机'},{v:'证件'}],[{v:' 00000000000 '},{v:'00000000000000000x'}]],rowCount:2,colCount:2,hidden:false,hiddenRows:[],hiddenCols:[],merges:[],notes:[]}]};
  const content=await normalizedReportFile(tableResource(book,table)).text();
  assert.match(content,/"mobile"/);assert.match(content,/"00000000000"/);assert.match(content,/"00000000000000000X"/);
  book.sheets[0].cells[1][0].v='123';assert.throws(()=>normalizedReportFile(tableResource(book,table)),/11位/);
  book.sheets[0].cells[1][0].v='00000000000';book.sheets[0].cells[1][1].v='invalid';assert.throws(()=>normalizedReportFile(tableResource(book,table)),/18位/);
});
test('invalid time properties are rejected and non-temporal formats cannot masquerade as timestamps',()=>{
  const bad={...field('invalid_role'),timeRoles:['unknown'] as UploadField['timeRoles']};
  assert.ok(reportFieldIssues(bad,[bad]).some(message=>message.includes('时间属性')));
  const phone={...field('mobile'),isTimestamp:true,standardField:'LXDH' as const};assert.ok(reportFieldIssues(phone,[phone]).some(message=>message.includes('时间戳')));
});
