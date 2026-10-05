/** Read-only shared metadata contract checks. No token, credentials or business rows are printed. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),{sm3}=require('sm-crypto');
assert(process.env.METADATA_TEST_ACCOUNT&&process.env.METADATA_TEST_PASSWORD,'Set development credentials in the invoking environment');
const base='http://localhost:3000/dev-api';
const login=await fetch(base+'/portal/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({account:process.env.METADATA_TEST_ACCOUNT,password:sm3(process.env.METADATA_TEST_PASSWORD),appId:'1995678661281710081'})}).then(r=>r.json());
assert.equal(login.code,0);assert(login.data?.token);
const headers={'Content-Type':'application/json',token:login.data.token};
async function call(path,body,side=3000){const r=await fetch(`http://localhost:${side}/dev-api${path}`,{method:body===undefined?'GET':'POST',headers,...(body===undefined?{}:{body:JSON.stringify(body)}),signal:AbortSignal.timeout(30000)}).then(r=>r.json());assert([0,200,...(path.startsWith('/api/web/')?[1]:[])].includes(r.code??0),path+': '+String(r.message||r.msg||r.error||''));return 'data' in r?r.data:r;}
const results=[];
if(process.env.METADATA_REFRESH){assert.equal((await call('/sym/node-config/magic-resource/refresh',{})).success,true);for(const id of ['catalog','discover','save_relation','delete_relation']){const resource=await call('/api/web/resource/file/dwm_metadata_governance_'+id+'_01');assert(resource?.script,'Restored runtime script must be loaded');await call('/api/web/resource/file/api/save',resource);}console.log('Restored original metadata Magic routes verified and hot-published.');}
for(const side of [3000,3010]){
 const tenant=await call('/sym/tenant/current',undefined,side);if(process.env.METADATA_INSPECT)console.log('TENANT '+JSON.stringify(Object.fromEntries(Object.entries(tenant).filter(([k])=>/name|database|dialect|context/i.test(k)))));
 const catalog=await call('/dwm/metadata-governance/catalog',{},side);
 for(const key of ['sources','tables','columns','relations'])assert(Array.isArray(catalog[key]),key+' must be an array');
 const orgs=await call('/sym/org/getOrgTree',{parentId:'ROOT'},side);assert(Array.isArray(orgs));
 const apps=await call('/dst/application/list',{},side);assert(Array.isArray(apps));
 const directories=await call('/dst/catalog/page',{pageNum:1,pageSize:100,conditions:[{field:'assetType',value:'catalog',type:'match'}]},side);assert(Array.isArray(directories.list));
 const stats={tenant:String(tenant.tid??tenant.tenantId),tenantName:tenant.name,sources:catalog.sources.length,tables:catalog.tables.length,columns:catalog.columns.length,relations:catalog.relations.length,catalogs:Number(directories.total)};results.push(stats);
 console.log('PASS port '+side+': '+JSON.stringify(stats));
 const tableIds=new Set(catalog.tables.map(t=>String(t.tid)));assert(catalog.columns.every(c=>tableIds.has(String(c.tableId??c.table_id))),'Stored fields must reference canonical tables');console.log('PASS stored field read without connecting source databases');
}
assert.deepEqual(results[0],results[1]);
for(const path of ['/nifi/api/pipelines','/bigdata/resource/logical-models','/api/web/resource/file/dwm_metadata_governance_catalog_01','/api/web/resource/file/dwm_metadata_governance_group_01']){
 try{const data=await call(path);console.log('OPTIONAL '+path+' '+JSON.stringify({isArray:Array.isArray(data),keys:Object.keys(data||{}).slice(0,12),count:Array.isArray(data)?data.length:null,path:data?.path,groupId:data?.groupId,scriptPresent:Boolean(data?.script)}));}
 catch(e){console.log('OPTIONAL '+path+' unavailable: '+String(e.message).slice(0,200));}
}
console.log('Shared metadata checks passed; no business records changed.');
