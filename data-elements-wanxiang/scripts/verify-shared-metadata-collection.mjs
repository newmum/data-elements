/** Physical collection verification against MySQL information_schema only.
 * Creates its own registered source; never reads business rows or alters physical tables.
 * Credentials are supplied only by the invoking environment and are cleared before cleanup. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
const required=['METADATA_TEST_ACCOUNT','METADATA_TEST_PASSWORD','METADATA_PHYSICAL_HOST','METADATA_PHYSICAL_USER','METADATA_PHYSICAL_PASSWORD'];
assert(required.every(k=>process.env[k]),'Provide the development login and allowed MySQL test connection in environment');
const require=createRequire(import.meta.url),{sm3}=require('sm-crypto');
const base='http://localhost:3010/dev-api',key=Date.now().toString(36),name='WX_METADATA_PHYSICAL_'+key;
const response=await fetch(base+'/portal/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({account:process.env.METADATA_TEST_ACCOUNT,password:sm3(process.env.METADATA_TEST_PASSWORD),appId:'1995678661281710081'})}).then(r=>r.json());
assert.equal(response.code,0);assert(response.data?.token);
const headers={'Content-Type':'application/json',token:response.data.token};
async function call(route,body){const http=await fetch(base+route,{method:body===undefined?'GET':'POST',headers,...(body===undefined?{}:{body:JSON.stringify(body)}),signal:AbortSignal.timeout(60000)});assert(http.ok,'HTTP '+http.status+' '+route);const envelope=await http.json();assert([0,200].includes(envelope.code??0),route+': '+String(envelope.message||envelope.msg||envelope.error||''));return 'data'in envelope?envelope.data:envelope;}
const before=await call('/dwm/metadata-governance/catalog',{}),count=r=>['sources','tables','columns','relations'].map(k=>r[k].length);
const user=await call('/sym/user/me',{}),tenant=await call('/sym/tenant/current');
const folder=join(tmpdir(),'wx-metadata-physical-'+key);await mkdir(folder);
const record=value=>writeFile(join(folder,'verification.json'),JSON.stringify(value,null,2));
let sourceId='',failure='',tableCount=0,columnCount=0;
const props={dbName:name,dbType:'mysql',orgId:user.orgId,storageDomain:'政务外网',assetDesc:'Disposable system-metadata collection verification',accessMode:'explore',dataAccessMode:'explore',showConnect:1,preserveSourceOrg:true,assetStatus:2,host:process.env.METADATA_PHYSICAL_HOST,port:Number(process.env.METADATA_PHYSICAL_PORT||3306),database:'information_schema',username:process.env.METADATA_PHYSICAL_USER,password:process.env.METADATA_PHYSICAL_PASSWORD};
async function collect(mode){let job=await call('/dst/database/metadata/tables/collection/start',{dbId:sourceId,mode});assert(job.jobId,'The collection must return a real job ID');for(let i=0;i<90&&!['FAILED','SUCCEEDED','CANCELLED'].includes(job.status);i++){await new Promise(r=>setTimeout(r,1000));job=await call('/dst/database/metadata/tables/collection/status?dbId='+sourceId+'&jobId='+job.jobId);}assert.equal(job.status,'SUCCEEDED','Physical collection: '+String(job.error||job.status));assert.equal(Number(job.progressPercent),100);return job;}
try{
 sourceId=(await call('/dst/database/saveOrUpdate',{assetType:'db',deferIndexRefresh:true,propList:props})).tid;assert(sourceId);await record({sourceId,tenant:tenant.tid,before:count(before)});
 assert.equal((await call('/dst/database/metadata/test-connection',{tid:sourceId})).connected,true);
 const initial=await collect('INITIAL');assert(Number(initial.persistedCount)>0);
 let catalog=await call('/dwm/metadata-governance/catalog',{});const tables=catalog.tables.filter(t=>String(t.datasource_id??t.datasourceId)===sourceId);assert(tables.length>0);tableCount=tables.length;
 await call('/dst/database/metadata/collectColumns',{tableId:tables[0].tid});
 catalog=await call('/dwm/metadata-governance/catalog',{});const tableIds=new Set(tables.map(t=>String(t.tid)));columnCount=catalog.columns.filter(c=>tableIds.has(String(c.table_id??c.tableId))).length;assert(columnCount>0,'Collected fields must be visible in the shared snapshot');
 await collect('REFRESH');catalog=await call('/dwm/metadata-governance/catalog',{});assert.equal(catalog.tables.filter(t=>String(t.datasource_id??t.datasourceId)===sourceId).length,tableCount,'Recollection must not duplicate source tables');
 console.log(JSON.stringify({tenant:tenant.name,connection:true,initialCollection:true,fieldCollection:true,recollection:true,tableCount,columnCount,verification:folder}));
}catch(error){failure=String(error.message);throw error;}
finally{
 if(sourceId){
  // showConnect=0 deliberately clears the saved connection including the test password.
  await call('/dst/database/saveOrUpdate',{tid:sourceId,assetType:'db',deferIndexRefresh:true,propList:{dbName:name,dbType:'mysql',orgId:user.orgId,storageDomain:'政务外网',accessMode:'explore',dataAccessMode:'explore',showConnect:0,preserveSourceOrg:true}});
  const detail=await call('/dst/database/detail',{tid:sourceId});assert(!detail.password&&!detail.jdbcURL&&!detail.host,'Temporary connection must be removed before soft deletion');
  await call('/dst/database/delete',{tid:sourceId,dryRun:true});await call('/dst/database/delete',{tid:sourceId});
 }
 const after=await call('/dwm/metadata-governance/catalog',{});assert.deepEqual(count(after),count(before));await record({sourceId,tenant:tenant.tid,before:count(before),after:count(after),tableCount,columnCount,failure,cleaned:true});console.log('Physical collection fixtures and credentials cleared; pre-existing metadata preserved.');
}
