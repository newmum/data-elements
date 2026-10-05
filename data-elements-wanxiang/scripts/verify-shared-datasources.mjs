/** Integration verification creates and removes ONLY its own explicitly named fixture.
 * No source database is created or deleted; credentials and response bodies are not logged.
 * Run with DATA_ELEMENTS_TEST_PASSWORD in the process environment, never in a saved file.
 */
import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
const require=createRequire(import.meta.url);
const {sm3}=require('sm-crypto');
const base=process.env.DATA_ELEMENTS_TEST_API||'http://localhost:8088';
const tenant='2084109831682699265';
if(!process.env.DATA_ELEMENTS_TEST_PASSWORD)throw new Error('Missing test-login environment credential');
const started=Date.now();
const login=await fetch(base+'/portal/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({account:'manager',password:sm3(process.env.DATA_ELEMENTS_TEST_PASSWORD),appId:'1995678661281710081',tenantId:tenant})}).then(r=>r.json());
const token=typeof login.data==='string'?login.data:login.data?.token;
assert.ok(token,'Platform login did not return a session');
async function call(path,body,timeoutMs=30000){
 let response;try{response=await fetch(base+path,{method:body===undefined?'GET':'POST',headers:{token,...(body===undefined?{}:{'Content-Type':'application/json'})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(timeoutMs)});}catch(error){throw new Error(path+' request failed: '+error.name);}
 assert.equal(response.ok,true,path+' HTTP failure');
 const envelope=await response.json();
 assert.ok([0,1,200].includes(envelope.code),path+' rejected: '+(envelope.msg||envelope.message||'unknown'));
 return envelope.data;
}
const context=await call('/sym/tenant/current');assert.equal(context.tid,tenant);
const user=await call('/sym/user/me',{});
const list=await call('/dst/database/page',{pageNum:1,pageSize:1000,includeMetadataCounts:true,conditions:[]});
const real=list.list[0];assert.ok(real?.tid);
const detail=await call('/dst/database/detail',{tid:real.tid});assert.equal(detail.tid,real.tid);
const tables=await call('/dst/database/metadata/tables?dbId='+encodeURIComponent(real.tid)+'&includeGovernance=false');assert.ok(Array.isArray(tables));
const status=await call('/dst/database/metadata/tables/collection/status?dbId='+encodeURIComponent(real.tid));assert.equal(typeof status.status,'string');
const report={tenant:context.name,sourceCount:list.list.length,objectCount:tables.length,fieldCount:real.fieldCount,checks:['login','tenant','source-list','source-detail','snapshot-table-list','real-collection-status']};
async function waitProjection(id,expected){let last=[];for(let n=0;n<12;n++){const page=await call('/dst/database/page',{pageNum:1,pageSize:20,conditions:[{field:'tid',type:'eq',value:id}],includeMetadataCounts:true});last=page.list;if(page.list.some(row=>String(row.tid||row.id)===String(id))===expected)return;await new Promise(resolve=>setTimeout(resolve,500));}console.log({projectionFixture:id,returned:last.map(row=>({tid:row.tid,id:row.id,status:row.assetStatus,keys:Object.keys(row)}))});throw new Error('Search projection did not become consistent');}
let fixture='';
try{
 // The shared page deliberately excludes codex_* databases. Do not use that
 // prefix when verifying ordinary registration/search projection consistency.
 const props={dbName:'WX_数据源接口联调_20260928_'+Date.now(),dbType:'mysql',orgId:user.orgId,appId:real.appId,storageDomain:'政务外网',host:'127.0.0.1',port:1,database:'wx_contract_no_database',username:'wx_contract_no_login',jdbcURL:'jdbc:mysql://127.0.0.1:1/wx_contract_no_database',assetDesc:'Disposable datasource UI integration fixture; no source database access',accessMode:'explore',dataAccessMode:'explore',showConnect:1,preserveSourceOrg:true,assetStatus:2};
 const tick=Date.now();
 const saved=await call('/dst/database/saveOrUpdate',{tid:'',assetType:'db',deferIndexRefresh:true,propList:props});
 fixture=saved.tid;assert.ok(fixture);report.saveMs=Date.now()-tick;
 const stored=await call('/dst/database/detail',{tid:fixture});assert.equal(stored.dbName,props.dbName);assert.equal(stored.orgId,user.orgId);assert.equal(stored.appId,real.appId);report.checks.push('create-authoritative-record');
 await call('/dst/database/saveOrUpdate',{tid:fixture,assetType:'db',deferIndexRefresh:true,propList:{...props,assetDesc:'Updated disposable fixture',isEnable:0}});
 const edited=await call('/dst/database/detail',{tid:fixture});assert.equal(edited.assetDesc,'Updated disposable fixture');report.checks.push('edit-authoritative-record');report.enableFieldSupported=edited.isEnable!==undefined;
 const projection=await call('/dst/maintenance/refresh',{tid:fixture,assetType:'db'},120000);assert.equal(projection.refreshed,true);report.checks.push('deferred-index-refresh');
 await waitProjection(fixture,true);report.checks.push('saved-source-visible-in-shared-list');
 const tested=await call('/dst/database/metadata/test-connection',{tid:fixture},120000);assert.equal(tested.connected,false);report.checks.push('real-connection-failure-is-not-success');
 const preview=await call('/dst/database/delete',{tid:fixture,dryRun:true});assert.notEqual(preview.exists,false);report.checks.push('delete-impact-preview');
}finally{
 if(fixture){const deleted=await call('/dst/database/delete',{tid:fixture},120000);assert.ok(deleted.deleted||deleted.alreadyDeleted);report.checks.push('delete-own-fixture');await waitProjection(fixture,false);report.checks.push('own-fixture-removed');}
}
report.totalMs=Date.now()-started;
console.log(JSON.stringify(report,null,2));
