/** Hot-load only this integration's shared Magic resources, preserving metadata.
 * Credentials come from the process environment. Exact pre-update resources are
 * exported to an OS temporary backup directory before any update.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),{sm3}=require('../../data-elements-front/node_modules/sm-crypto');
if(!process.env.DATA_ELEMENTS_TEST_PASSWORD)throw new Error('Missing test-login environment credential');
const base=process.env.DATA_ELEMENTS_TEST_API||'http://localhost:8088';
const login=await fetch(base+'/portal/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({account:'manager',password:sm3(process.env.DATA_ELEMENTS_TEST_PASSWORD),appId:'1995678661281710081',tenantId:'2084109831682699265'})}).then(r=>r.json());
const token=typeof login.data==='string'?login.data:login.data?.token;
assert.ok(token,'Platform login did not return a session: '+String(login.msg||login.message||login.code));
async function call(route,body){const result=await fetch(base+route,{method:body===undefined?'GET':'POST',headers:{token,...(body===undefined?{}:{'Content-Type':'application/json'})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)}).then(r=>r.json());assert.ok([0,1,200].includes(result.code),route+' rejected: '+(result.msg||result.message));return result.data;}
const context=await call('/sym/tenant/current');assert.equal(context.tid,'2084109831682699265');
const normalize=value=>value.replace(/\r\n/g,'\n').trim();
const files=['01.字段标准引用清单.ms','02.保存字段标准引用.ms'];
const prepared=[];
for(const filename of files){
 const content=await fs.readFile(new URL('../backend/standard-landing/'+filename,import.meta.url),'utf8');
 const split=content.indexOf('================================'),metadata=JSON.parse(content.slice(0,split)),script=content.slice(split+32).trim();
 assert.equal(metadata.groupId,'dwm_standard_landing_group_01');
 const resource=await call('/api/web/resource/file/'+metadata.id);
 assert.equal(resource.id,metadata.id);assert.equal(resource.groupId,metadata.groupId);assert.equal(resource.path,metadata.path);assert.equal(resource.method,metadata.method);
 assert.ok(resource.script&&resource.script.includes('tenantRuntime.id()'),'Unexpected or missing live script; do not overwrite');
 const previous=script.replace('// Dameng treats the empty string as NULL. Use a real null expected value and\n// explicit empty/null predicates so a first binding still has an atomic guard.\nvar expectedDbId = expectedStandardId == \'\' ? null : expectedStandardId\n','').replace("((#{expectedDbId} is null and\n         (data_standard_id is null or trim(data_standard_id) is null or trim(data_standard_id)=''))\n        or trim(data_standard_id)=#{expectedDbId})","coalesce(data_standard_id,'')=#{expectedStandardId}");
 assert.ok([normalize(script),normalize(previous)].includes(normalize(resource.script)),'Live script differs from the reviewed integration versions; inspect before publishing');
 prepared.push({filename,script,resource});
}
const backup=await fs.mkdtemp(path.join(os.tmpdir(),'wx-standard-landing-backup-'));
for(const entry of prepared)await fs.writeFile(path.join(backup,entry.resource.id+'.json'),JSON.stringify(entry.resource,null,2)+'\n');
const changed=[];
for(const {script,resource} of prepared){
 if(normalize(resource.script)!==normalize(script)){await call('/api/web/resource/file/api/save',{...resource,script});changed.push(resource.id);}
 const loaded=await call('/api/web/resource/file/'+resource.id);assert.equal(normalize(loaded.script),normalize(script),'Published script did not load');
}
console.log(JSON.stringify({tenant:context.name,backup,changed,verified:prepared.map(e=>e.resource.id)},null,2));
