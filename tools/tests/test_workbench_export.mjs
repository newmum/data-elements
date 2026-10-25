import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';
import { createRequire } from 'node:module';
const root = new URL('../../', import.meta.url);
const source = await fs.readFile(new URL('lowcode/01.系统门户/01.数据源管理/01.工作台(my-workbench).vue', root), 'utf8');
const section = source.split('// WORKBENCH_EXPORT_START')[1].split('// WORKBENCH_EXPORT_END')[0];
const compiler={};vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js',root),'utf8'),compiler);
const block=compiler.Babel.transform(section.slice(section.indexOf('\n')+1),{filename:'workbench-export.ts',presets:['typescript']}).code;
const require = createRequire(new URL('data-elements-chengtian/package.json', root));
const ExcelJS = require('exceljs');
// Use the app's same-realm function factory: ExcelJS detects arrays by their realm.
const service = {post:async()=>{throw new Error('network unavailable')}};
const actual = new Function('ref','ExcelJS','Date','setTimeout','notify','$common','activeScope','payloadOf',
  block+'\nreturn {collectReportRows,createReportWorkbook,reportHeaders,reportValues,exportData,exporting,exportError};')
  (value=>({value}),ExcelJS,Date,setTimeout,()=>{},service,{value:'all'},value=>value);
const makeRow = i => ({datasourceId:String(i), orgName:'科信总队', appName:'测试应用', dbName:'数据源'+i, dbType:'postgresql', contactName:'创建者', directoryCount:10,
  registrationStartTime:'2026-10-14 09:30:00', annotatedCount:8, dictionaryCount:1,businessCount:5,logCount:2,completedCount:8,registrationRate:80,
  sourceAssetStatus:1,accessMode:'数据抽取', accessedTableCount:3,accessCandidateCount:5,accessStartTime:'2026-10-14T10:00:00',runningTaskCount:2,errorTaskCount:1,idleTaskCount:0});
for (const count of [0,20,100,201]) {
  const all=Array.from({length:count},(_,i)=>makeRow(i+1));let requests=0;
  const rows=await actual.collectReportRows(async p=>{requests++;assert.equal(p.pageSize,100);assert.equal(p.ownerOnly,true);assert.equal(p.sortField,'orgName');return {scope:'ORG',total:count,list:all.slice((p.pageNum-1)*100,p.pageNum*100)};},true);
  assert.equal(rows.length,count);assert.equal(requests,Math.max(1,Math.ceil(count/100)));
  console.log(JSON.stringify({items:count,requests,businessSql:requests*3}));
}
await assert.rejects(actual.collectReportRows(async()=>({scope:'ALL',total:5001,list:[]}),false),/超过/);
await assert.rejects(actual.collectReportRows(async()=>({scope:'ALL',total:2,list:[makeRow(1),makeRow(1)]}),false),/重复/);
await assert.rejects(actual.collectReportRows(async()=>({scope:'ALL',total:2,list:[makeRow(1)]}),false),/不完整/);
await assert.rejects(actual.collectReportRows(async()=>({scope:'INVALID',total:0,list:[]}),false),/返回异常/);
let page=0;
await assert.rejects(actual.collectReportRows(async()=>++page===1?{scope:'ORG',total:101,list:Array.from({length:100},(_,i)=>makeRow(i+1))}:{scope:'ALL',total:101,list:[makeRow(101)]},false),/权限范围/);
page=0;
await assert.rejects(actual.collectReportRows(async()=>++page===1?{scope:'ALL',total:101,list:Array.from({length:100},(_,i)=>makeRow(i+1))}:{scope:'ALL',total:102,list:[makeRow(101),makeRow(102)]},false),/数量/);
const sample=[makeRow(1),{...makeRow(2),dbName:'=HYPERLINK("https://invalid.example","test")',contactName:null,registrationEndTime:null}];
const original=actual.createReportWorkbook(sample);
const decoded=new ExcelJS.Workbook();await decoded.xlsx.load(await original.xlsx.writeBuffer());
const sheet=decoded.worksheets[0];
assert.equal(sheet.getCell('A1').value,'数据源情况');assert.equal(sheet.getCell('G1').value,'数据登记情况');assert.equal(sheet.getCell('P1').value,'数据接入情况');
assert.equal(sheet.getCell('K2').value,'已登记业务表');assert.equal(sheet.getCell('L2').value,'已登记日志表');
assert.equal(sheet.columnCount,19);assert.equal(sheet.getCell('F3').value,'创建者');assert.equal(sheet.getCell('L3').value,2);
assert.equal(sheet.getCell('M3').value,0.8);assert.equal(sheet.getCell('M3').numFmt,'0.0%');
assert.equal(sheet.getCell('H3').type,ExcelJS.ValueType.Date);assert.equal(sheet.getCell('H3').value.toISOString(),'2026-10-14T09:30:00.000Z');
assert.equal(sheet.getCell('G3').type,ExcelJS.ValueType.Number);assert.equal(sheet.getCell('N4').value,null);
assert.equal(sheet.getCell('D4').type,ExcelJS.ValueType.String);assert.equal(sheet.getCell('F4').value,'');
assert.equal(sheet.views[0].ySplit,2);assert.equal(sheet.views[0].xSplit,4);
const empty=actual.createReportWorkbook([]);assert.equal(empty.worksheets[0].getCell('S2').value,'接入进展情况');
await actual.exportData();assert.equal(actual.exporting.value,false);assert.match(actual.exportError.value,/network unavailable/);
await fs.mkdir(new URL('logs/workbench-export/',root),{recursive:true});
await fs.writeFile(new URL('logs/workbench-export/test-template.xlsx',root),await original.xlsx.writeBuffer());
console.log('Workbook roundtrip, creator/log columns, typed dates/counts, formula text safety, empty export, bounded pages, changed scope/total and failure recovery passed.');
