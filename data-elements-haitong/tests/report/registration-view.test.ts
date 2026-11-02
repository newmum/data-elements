import {createElement} from 'react';
import test from 'node:test';
import assert from 'node:assert/strict';
import {renderToStaticMarkup} from 'react-dom/server';
import PreviewPanel from '../../src/report/components/PreviewPanel';
import {tableResource,tableSample,normalizedReportFile,type UploadTable} from '../../src/report/services/uploadWorkbench';
import type {SourceBook} from '../../src/report/core/model';

function sample(){
  const table:UploadTable={key:'date-table',fileId:'date-file',fileName:'dates.csv',sheetName:'dates',tableName:'event_dates',tableNameCn:'日期表',fieldCount:1,recordCount:1,status:'success',startRow:1,endRow:2,startColumn:1,endColumn:1,
    fields:[{columnName:'event_date',columnComment:'日期',columnType:'date',primaryKey:false,nullable:false,isTimestamp:false,timeFormat:'dmy',standardField:'DATE'}]};
  const book:SourceBook={id:'date-file',filename:'dates.csv',size:30,importedAt:'2026-11-01',sheets:[{name:'dates',cells:[[{v:'原始日期列'}],[{v:'01/11/2026'}]],hidden:false,hiddenRows:[],hiddenCols:[],merges:[],rowCount:2,colCount:1,notes:[]}]};
  return tableResource(book,table);
}
test('normalized preview shows converted values and registered headers without a raw-value switch or tooltip',()=>{
  const markup=renderToStaticMarkup(createElement(PreviewPanel,{resource:sample(),dictionaries:[],onEdit:()=>{},onDelete:()=>{}}));
  assert.match(markup,/2026-11-01/);assert.match(markup,/event_date/);assert.doesNotMatch(markup,/01\/11\/2026|原始日期列|原始数据|原值：/);
});
test('step-two original sample is explicit and read-only',()=>{
  const markup=renderToStaticMarkup(createElement(PreviewPanel,{resource:sample(),view:'original',readOnly:true,dictionaries:[],onEdit:()=>{},onDelete:()=>{}}));
  assert.match(markup,/01\/11\/2026/);assert.match(markup,/原始日期列/);assert.doesNotMatch(markup,/同步状态|>编辑<|>删除</);
});

test('ten-row raw samples skip empty records and cannot truncate subsequent full-table delivery',async()=>{
  const cells=[[{v:'原始日期列'}],...Array.from({length:25},()=>[[{v:''}],[{v:'01/11/2026'}]]).flat()];
  const book:SourceBook={id:'dates',filename:'dates.csv',size:300,importedAt:'2026-11-01',sheets:[{name:'dates',cells,hidden:false,hiddenRows:[],hiddenCols:[],merges:[],rowCount:cells.length,colCount:1,notes:[]}]};
  const table:UploadTable={key:'dates',fileId:'dates',fileName:'dates.csv',sheetName:'dates',tableName:'validation_dates',tableNameCn:'日期样例表',fieldCount:1,recordCount:25,status:'success',startRow:1,endRow:cells.length,startColumn:1,endColumn:1,fields:[{columnName:'event_date',columnComment:'日期',columnType:'date',primaryKey:false,nullable:false,isTimestamp:false,timeFormat:'dmy',standardField:'DATE'}]};
  const preview=tableSample(book,table),full=tableResource(book,table);
  assert.equal(preview.rows.length,10);assert.equal(full.rows.length,25);assert.equal(preview.rows[0].sourceRow,3);
  assert.equal((await normalizedReportFile(full).text()).split('\r\n').length,26);
  const markup=renderToStaticMarkup(createElement(PreviewPanel,{resource:preview,view:'original',readOnly:true,compact:true,sampleLimit:10,dictionaries:[],onEdit:()=>{},onDelete:()=>{}}));
  assert.match(markup,/01\/11\/2026/);assert.doesNotMatch(markup,/ant-pagination|role="tab"|字段信息|规范后数据|>编辑<|>删除</);
  assert.throws(()=>tableSample(book,table,11));
});

test('compact delivery preview shows actual success, failure and pending states even in read-only mode',()=>{
  const resource=sample();resource.rows=Array.from({length:12},(_,i)=>({...resource.rows[0],id:'row'+i,sourceRow:i+2}));
  resource.sync={row0:'success',row1:'failed',row2:'unknown'};
  const markup=renderToStaticMarkup(createElement(PreviewPanel,{resource,readOnly:true,compact:true,sampleLimit:10,showDeliveryStatus:true,dictionaries:[],onEdit:()=>{},onDelete:()=>{}}));
  assert.match(markup,/已上报/);assert.match(markup,/投递失败/);assert.match(markup,/未上报/);assert.match(markup,/结果待确认/);
  assert.doesNotMatch(markup,/规范后数据|已投递|spreadsheet-column-letter|source-row-number|同步成功|ant-pagination|>编辑<|>删除</);
  assert.match(markup,/data-row-key="row9"/);assert.doesNotMatch(markup,/data-row-key="row10"/);
  assert.equal(resource.rows.length,12);
});
