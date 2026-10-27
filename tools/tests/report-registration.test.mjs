import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import fs from 'node:fs';
const source=fs.readFileSync(new URL('../../lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/01.登记数据源(register-db).vue',import.meta.url),'utf8');
function fixture(post){
 const calls=[];const scope={currentDb:{value:{tid:'source'}},$common:{post},URL,URLSearchParams,
 window:{location:{hostname:'localhost',port:'3000',protocol:'http:',href:'http://localhost:3000/#/datasource-manage/index',assign(url){calls.push(url);}}},
 registerModalContext:{open:{value:true}}};vm.createContext(scope);
 const start=source.indexOf('async function fetchReportFileHistory('),end=source.indexOf('const save = async',start);
 vm.runInContext(source.slice(start,end)+'\nthis.history=fetchReportFileHistory;this.open=openReportWorkbench;',scope);
 return {scope,calls};
}
test('20/100 results use one paged request with no per-file reads',async()=>{
 for(const total of [20,100]){let requests=0;const {scope}=fixture(async(_url,body)=>{requests++;assert.equal(body.action,'history');assert.equal(body.dbId,'source');assert.equal(body.pageSize,20);return{list:[],total};});
 assert.equal((await scope.history({pageNo:2,pageSize:20})).total,total);assert.equal(requests,1);}
});
test('workbench navigation closes registration and carries only the source identifier',()=>{
 const {scope,calls}=fixture();assert.equal(scope.open('source').navigationHandled,true);assert.equal(scope.registerModalContext.open.value,false);
 assert.equal(calls[0],'http://localhost:3002/#/report?datasourceId=source');assert.doesNotMatch(calls[0],/token|password/);
});
test('registration keeps five steps and removes upload editing and file requirements',()=>{
 assert.doesNotMatch(source,/reportUploadSkipped|reportRegistrationInput|saveReportRegistration|uploadReportRegistrationFiles/);
 assert.doesNotMatch(source,/step\.skipped[^;]*accessMode\.value.*upload/);
 const start=source.indexOf('<div v-if="accessMode === \'upload\'" class="report-registration-panel">');
 const panel=source.slice(start,source.indexOf('<template v-else-if',start));
 assert.ok(panel.includes('fetchReportFileHistory'));assert.doesNotMatch(panel,/<input|update:model-value|removeReport|下载.*模板/);
 assert.match(panel,/:data="fetchReportFileHistory"/);
 const next=source.slice(source.indexOf('const next = async'),source.indexOf('const finish ='));
 assert.match(next,/await save\(\)/);assert.match(next,/openReportWorkbench/);assert.doesNotMatch(next,/先上传|先保存.*数据表/);
});
test('all three workbench steps run after file upload and use real report receipts',()=>{
 const app=fs.readFileSync(new URL('../../data-elements-haitong/src/report/App.tsx',import.meta.url),'utf8');
 assert.ok(app.indexOf("title:'上传数据文件'")<app.indexOf("title:'整理数据规范'"));
 assert.ok(app.indexOf("title:'整理数据规范'")<app.indexOf("title:'选择数据并上报'"));
 assert.doesNotMatch(app,/downloadReportUploadTemplate|bindResourceToTarget|请选择前面五步/);
 assert.match(app,/submitUploadTable/);assert.match(app,/loadReportReceipts/);
});
