/** Read-only standard/binding/resource contract audit. Never changes tenant or business data. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),{sm3}=require('../../data-elements-front/node_modules/sm-crypto');
assert(process.env.METADATA_TEST_PASSWORD,'Use a development credential from the invoking environment');
const auth=await fetch('http://localhost:3010/dev-api/portal/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({account:process.env.METADATA_TEST_ACCOUNT||'manager',password:sm3(process.env.METADATA_TEST_PASSWORD),appId:'1995678661281710081'})}).then(r=>r.json());
assert.equal(auth.code,0);assert(auth.data?.token);
const headers={'Content-Type':'application/json',token:auth.data.token};
async function call(side,route,body){const response=await fetch(`http://localhost:${side}/dev-api${route}`,{method:body===undefined?'GET':'POST',headers,...(body===undefined?{}:{body:JSON.stringify(body)}),signal:AbortSignal.timeout(30000)});const r=await response.json();const successCodes=route.startsWith('/api/web/resource/')?[1]:[0,200];assert(response.ok&&successCodes.includes(r.code??0),route+': '+String(r.msg||r.message||response.status));return 'data'in r?r.data:r;}
function pageOf(v){if(Array.isArray(v))return {list:v,total:v.length};assert(v&&typeof v==='object');if(Array.isArray(v.list))return {list:v.list,total:Number(v.total??v.list.length)};return pageOf(v.list);}
function classCount(v){if(Array.isArray(v))return v.reduce((n,r)=>n+classCount(r),0);if(!v||typeof v!=='object')return 0;return (v.tid!=null&&String(v.tid)!=='0'?1:0)+classCount(v.children);}
const summary=[];
for(const side of [3000,3010]){
 const tenant=await call(side,'/sym/tenant/current');
 const elements=pageOf(await call(side,'/dwm/standard/element/page',{pageNum:1,pageSize:1000}));
 const codes=await call(side,'/dwm/standard/code/list',{}),classes=await call(side,'/dwm/standard/standard-class-list',{});
 const landing=await call(side,'/dwm/standard/landing/list',{}),bindings=await call(side,'/dwm/metadata-governance/bindings/list',{});
 assert(Array.isArray(elements.list)&&Array.isArray(codes)&&classes&&typeof classes==='object');
 assert(Array.isArray(landing.items)&&Array.isArray(bindings.items)&&Array.isArray(bindings.rules));
 summary.push({tenant:String(tenant.tid),elements:elements.total,codes:codes.length,classes:classCount(classes),standardFields:landing.items.length,extraBindings:bindings.items.length,configuredTaskRules:bindings.rules.length});
}
assert.deepEqual(summary[0],summary[1]);
const files=['catalog','relations_validate','foreign_key_preview','foreign_key_create','bindings_list','bindings_save'];
// The runtime ID belongs to file_content, not the api_file_t primary key.
const ids=['dwm_metadata_governance_catalog_01','dwm_metadata_governance_validate_01','dwm_metadata_governance_fk_preview_01','dwm_metadata_governance_fk_create_01','dwm_metadata_governance_bindings_list_01','dwm_metadata_governance_bindings_save_01'];
for(let i=0;i<ids.length;i++){const f=await call(3010,'/api/web/resource/file/'+ids[i]);assert(f?.script&&f.groupId==='dwm_metadata_governance_group_01',files[i]+' must be cold-loadable from the existing shared group');}
console.log(JSON.stringify({checks:8,mode:'read-only',consistentBetweenFrontends:true,counts:summary[1],coldLoadedResources:files,businessRecordsChanged:false},null,2));
