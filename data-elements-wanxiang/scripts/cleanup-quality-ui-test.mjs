/** Remove only this implementation's never-run, disabled UI test configuration. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),{sm3}=require('../../data-elements-front/node_modules/sm-crypto');
const account=process.env.QUALITY_TEST_ACCOUNT,password=process.env.QUALITY_TEST_PASSWORD;
assert(account&&password,'Set test credentials in the invoking environment');
const base='http://localhost:3010/dev-api';
const login=await fetch(base+'/portal/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({account,password:sm3(password),appId:'1995678661281710081'}),signal:AbortSignal.timeout(20000)}).then(r=>r.json());assert.equal(login.code,0);assert(login.data?.token);
const headers={'Content-Type':'application/json',token:login.data.token};
async function call(path,body){const r=await fetch(base+path,{method:'POST',headers,body:JSON.stringify(body),signal:AbortSignal.timeout(20000)}).then(r=>r.json());assert.equal(r.code,0,path);return r.data;}
const code='DQ_NEW_UI_VERIFY_20260929_001',found=await call('/dwm/quality/task/page',{page:1,size:20,keyword:code});
assert(Array.isArray(found.list));const rows=found.list.filter(r=>(r.task_code||r.taskCode)===code);assert.equal(rows.length,1,'Exactly the implementation-created task must be present');
const taskId=String(rows[0].tid??rows[0].id),task=await call('/dwm/quality/task/detail',{taskId});
assert.match(task.task_name||task.taskName,/^新版数据质量联调配置/);assert.equal(Number(task.status),2,'Test task must remain disabled');assert.equal(Number(task.sample_limit??task.sampleLimit),0);assert(!(task.last_run_id||task.lastRunId),'Task must never have run');assert.match(task.description,/仅验证新版共享任务配置持久化/);
assert.equal(task.rules.length,1);const rule=task.rules[0];assert.equal(rule.rule_type??rule.ruleType,'LENGTH');const parameters=rule.parameters??JSON.parse(rule.parameters_json??rule.parametersJson);assert.deepEqual(parameters,{minLength:0,maxLength:10});assert.equal(rule.severity,'MEDIUM');assert.equal(Number(rule.weight??rule.weight_value??rule.weightValue),10);
const runs=await call('/dwm/quality/run/page',{page:1,size:20,keyword:code});assert.equal(Number(runs.total),0,'No execution or historical business data may be associated with this cleanup');
assert.notEqual(await call('/dwm/quality/task/delete',{taskId}),false);assert.equal(Number((await call('/dwm/quality/task/page',{page:1,size:20,keyword:code})).total),0);
console.log(JSON.stringify({removedTestTask:taskId,taskCode:code,sourceRecordsChanged:false,originalTasksOrReportsChanged:false}));
