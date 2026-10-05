/** Read-only live contract verification. Credentials come only from the invoking environment. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),{sm3}=require('sm-crypto');
const account=process.env.QUALITY_TEST_ACCOUNT,password=process.env.QUALITY_TEST_PASSWORD;
assert(account&&password,'Set QUALITY_TEST_ACCOUNT and QUALITY_TEST_PASSWORD in the invoking environment');
const bases=['http://localhost:3000/dev-api','http://localhost:3010/dev-api'];
const login=await fetch(bases[0]+'/portal/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({account,password:sm3(password),appId:'1995678661281710081'}),signal:AbortSignal.timeout(20000)}).then(r=>r.json());
assert.equal(login.code,0,'Development login must succeed');
assert(login.data?.token,'Development login must return a session');
const headers={'Content-Type':'application/json',token:login.data.token};
const checks=[];
const pass=name=>{checks.push(name);console.log('PASS '+name);};
async function raw(side,path,body={},method='POST'){return fetch(bases[side]+path,{method,headers,...(method==='POST'?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(20000)}).then(r=>r.json());}
async function call(side,path,body={},method='POST'){const r=await raw(side,path,body,method);assert.equal(r.code,0,path+' must succeed: '+String(r.message||r.msg||'').slice(0,300));return r.data;}
const page=value=>{const row=Array.isArray(value?.list)?value:value?.list;const rawTotal=value?.total??row?.total;const list=row?.list===null&&(rawTotal===0||rawTotal==='0')?[]:row?.list;assert(Array.isArray(list),'Real page list must be present');const total=Number(rawTotal);assert(Number.isInteger(total)&&total>=0);return {list,total};};
const id=row=>String(row.tid??row.id);
const tenant0=await call(0,'/sym/tenant/current',{},'GET'),tenant1=await call(1,'/sym/tenant/current',{},'GET');
assert.equal(id(tenant0),id(tenant1));pass('Both platform proxies retain the same server-owned tenant session');
const results=[];
for(let side=0;side<2;side++){
 const templates=await call(side,'/dwm/quality/options/templates');assert.equal(templates.length,9);assert.deepEqual(new Set(templates.map(t=>t.ruleType||t.rule_type)),new Set(['NOT_NULL','NOT_EMPTY','UNIQUE','RANGE','ENUM','LENGTH','REGEX','TIMELINESS','REFERENCE']));
 const sources=await call(side,'/dwm/quality/options/datasources');assert(Array.isArray(sources));
 const organizations=await call(side,'/dwm/quality/options/organizations');assert(Array.isArray(organizations));
 const tasks=page(await call(side,'/dwm/quality/task/page',{page:1,size:15}));
 const runs=page(await call(side,'/dwm/quality/run/page',{page:1,size:15}));
 const orders=page(await call(side,'/dwm/quality/workorders/page',{page:1,size:15}));
 const summary=await call(side,'/dwm/quality/summary');assert(summary&&typeof summary==='object');
 if(tasks.list.length){const detail=await call(side,'/dwm/quality/task/detail',{taskId:id(tasks.list[0])});assert.equal(id(detail),id(tasks.list[0]));assert(Array.isArray(detail.rules));}
 if(runs.list.length){const runId=id(runs.list[0]);const detail=await call(side,'/dwm/quality/run/detail',{runId});assert.equal(id(detail),runId);assert(Array.isArray(detail.metrics)&&Array.isArray(detail.shards));page(await call(side,'/dwm/quality/run/issues',{runId,page:1,size:20,severity:'HIGH'}));}
 if(sources.length){const tables=await call(side,'/dwm/quality/options/tables',{datasourceId:id(sources[0])});assert(Array.isArray(tables));if(tables.length)assert(Array.isArray(await call(side,'/dwm/quality/options/columns',{tableId:id(tables[0])})));}
 for(const path of ['/dwm/quality/task/detail','/dwm/quality/run/detail']){const r=await raw(side,path,path.includes('task')?{taskId:'quality_absent_readonly_verification'}:{runId:'quality_absent_readonly_verification'});assert.notEqual(r.code,0,'Missing object must not become successful empty data');}
 const empty=page(await call(side,'/dwm/quality/task/page',{keyword:'QUALITY_READONLY_NO_MATCH_20260928',page:1,size:15}));assert.equal(empty.total,0);
 const stats={sources:sources.length,organizations:organizations.length,tasks:tasks.total,runs:runs.total,orders:orders.total};results.push(stats);pass(`Port ${side===0?3000:3010}: templates, source/table/field options, lists, detail, samples, filters and absent-object guards`);
}
assert.deepEqual(results[0],results[1]);pass('Shared tasks, runs and workorders are consistent between both frontends');
const unauthenticated=await fetch(bases[1]+'/dwm/quality/task/page',{method:'POST',headers:{'Content-Type':'application/json'},body:'{}',signal:AbortSignal.timeout(20000)}).then(r=>r.json());assert.notEqual(unauthenticated.code,0);pass('Unauthenticated requests cannot read quality business data');
console.log(JSON.stringify({mode:'read-only',checks:checks.length,counts:results[1],businessRecordsChanged:false},null,2));
