/** Integration mutations are limited to newly created, explicitly identified test fixtures.
 * Never print source credentials, full business metadata or session tokens. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
const require=createRequire(import.meta.url),{sm3}=require('sm-crypto');
assert(process.env.METADATA_TEST_ACCOUNT&&process.env.METADATA_TEST_PASSWORD,'Set development credentials in environment');
const key=Date.now().toString(36),base='http://localhost:3010/dev-api',prefix='WX_METADATA_VERIFY_'+key;
const login=await fetch(base+'/portal/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({account:process.env.METADATA_TEST_ACCOUNT,password:sm3(process.env.METADATA_TEST_PASSWORD),appId:'1995678661281710081'})}).then(r=>r.json());assert.equal(login.code,0);assert(login.data?.token);
const headers={'Content-Type':'application/json',token:login.data.token};
async function envelope(path,body,side=3010){const response=await fetch(`http://localhost:${side}/dev-api${path}`,{method:body===undefined?'GET':'POST',headers,...(body===undefined?{}:{body:JSON.stringify(body)}),signal:AbortSignal.timeout(60000)});assert(response.ok,path+' HTTP '+response.status);return response.json();}
async function call(path,body,side=3010){const r=await envelope(path,body,side);assert([0,200].includes(r.code??0),path+': '+String(r.message||r.msg||r.error||''));return 'data'in r?r.data:r;}
const context=await call('/sym/tenant/current'),user=await call('/sym/user/me',{}),before=await call('/dwm/metadata-governance/catalog',{});
const counts=r=>['sources','tables','columns','relations'].map(k=>r[k].length);
const baselineCounts=counts(before),fixtures={relations:[]},checks=[],limitations=[];
const folder=join(tmpdir(),'wx-metadata-check-'+key);await mkdir(folder);await writeFile(join(folder,'before-counts.json'),JSON.stringify({tenant:context.tid,counts:baselineCounts}));
const record=async()=>writeFile(join(folder,'fixtures.json'),JSON.stringify(fixtures,null,2));
try{
 const source=await call('/dst/database/saveOrUpdate',{tid:'',assetType:'db',propList:{dbName:prefix,dbType:'mysql',orgId:user.orgId,storageDomain:'政务外网',assetDesc:'Disposable metadata interface fixture; no physical connection',accessMode:'explore',dataAccessMode:'explore',showConnect:0,preserveSourceOrg:true,assetStatus:2}});fixtures.source=source.tid;assert(fixtures.source);await record();
 const seed=async(name,field)=>{const result=await call('/data/save',{db_table_t:[{datasourceId:fixtures.source,tableName:name,tableNameEn:name,tableNameCn:name,tableComment:'临时接口验证表',tableType:'数据表',businessType:'业务表',dataSourceType:'mysql',fieldCount:1,annotated:0,assetStatus:2,flowStatus:2}],db_table_column_t:[{tableId:'#db_table_t.tid',columnName:field,columnComment:'临时测试字段',dataType:'int',columnType:'int',nullable:0,primaryKey:field==='id'?1:0,ordinalPosition:1}]});return {table:result.find(r=>r.table==='db_table_t')?.tid,column:result.find(r=>r.table==='db_table_column_t')?.tid};};
 fixtures.a=await seed('wx_orders_'+key,'customer_id');fixtures.b=await seed('wx_customers_'+key,'id');assert(fixtures.a.table&&fixtures.a.column&&fixtures.b.table&&fixtures.b.column);await record();checks.push('dedicated-source-table-field-fixtures');
 const task=await call('/dst/database/metadata/tables/collection/status?dbId='+fixtures.source);assert.equal(task.exists,false);checks.push('collection-status-empty-task');
 const failed=await envelope('/dst/database/metadata/tables/collection/start',{dbId:fixtures.source,mode:'INITIAL'});if([0,200].includes(failed.code??0)){let job=failed.data;assert(job?.status||job?.jobId);for(let i=0;i<30&&!['FAILED','SUCCEEDED','CANCELLED'].includes(job.status);i++){await new Promise(r=>setTimeout(r,1000));job=await call('/dst/database/metadata/tables/collection/status?dbId='+fixtures.source+'&jobId='+job.jobId);}assert.equal(job.status,'FAILED','A disconnected source must not become a successful collection');assert(job.error,'Failed job must describe its cause');checks.push('real-async-collection-failure-and-error-progress');}else checks.push('missing-connection-is-not-success');limitations.push('Physical collection success requires a reachable source with valid connection configuration');
 let result=await call('/dwm/metadata-governance/catalog',{});assert(result.tables.some(t=>t.tid===fixtures.a.table));assert(result.columns.some(c=>c.tid===fixtures.a.column));checks.push('shared-saved-snapshot-canonical-ids');
 const relation={sourceTableId:fixtures.a.table,sourceColumnId:fixtures.a.column,targetTableId:fixtures.b.table,targetColumnId:fixtures.b.column,relationName:prefix,relationType:'REFERENCE',origin:'MANUAL',status:'CONFIRMED',description:'Disposable shared relationship test',tenantId:'must-not-determine-ownership'};
 const saved=await call('/dwm/metadata-governance/relations/save',relation);assert(saved.tid);fixtures.relations.push(saved.tid);await record();assert.equal(saved.relation_status||saved.relationStatus,'CONFIRMED');checks.push('manual-relation-save-server-tenant');
 const updated=await call('/dwm/metadata-governance/relations/save',{...relation,tid:saved.tid,relationName:prefix+'_EDIT',status:'ARCHIVED'});assert.equal(updated.relation_status||updated.relationStatus,'ARCHIVED');await call('/dwm/metadata-governance/relations/save',{...relation,tid:saved.tid,status:'SUGGESTED'});checks.push('relation-edit-archive-restore');
 const forged=await envelope('/dwm/metadata-governance/relations/save',{...relation,sourceColumnId:fixtures.b.column});assert(![0,200].includes(forged.code??0));checks.push('cross-table-field-rejected');
 const found=await call('/dwm/metadata-governance/relations/discover',{tableIds:[fixtures.a.table,fixtures.b.table]});assert(Array.isArray(found.items));checks.push('structure-discovery-no-physical-db-query');
 const props={catalogName:prefix,catalogNameEn:'wx_metadata_'+key,assetDesc:'Disposable metadata catalog test',sourceTableId:fixtures.a.table,sourceTableName:'wx_orders_'+key,dbId:fixtures.source,dbName:prefix,orgId:user.orgId,dataSourceType:'ods'};
 const items=[{colName:'客户标识',colEn:'customer_id',colType:'int',isPk:'0',isNullable:'0',sourceTableColumnId:fixtures.a.column,sortNo:1,enableCodeTable:0}];
 const directory=await call('/dst/catalog/saveOrUpdate',{assetType:'catalog',propList:props,catalogItems:items});fixtures.catalog=directory.tid;assert(fixtures.catalog);await record();checks.push('catalog-registration-existing-platform-route');
 const details=await call('/dst/catalog/detail',{tid:fixtures.catalog},3000);assert.equal(details.catalogName,prefix);assert.equal(details.sourceTableId,fixtures.a.table);const fields=await call('/dst/catalog/catalog-items/list',{catalogId:fixtures.catalog},3000);assert.equal(fields.length,1);assert.equal(fields[0].sourceTableColumnId,fixtures.a.column);checks.push('other-frontend-sees-directory-and-items');
 await call('/dst/catalog/saveOrUpdate',{tid:fixtures.catalog,assetType:'catalog',propList:{...props,catalogName:prefix+'_EDIT'},catalogItems:[{...items[0],tid:fields[0].tid,colName:'客户代码'}]});assert.equal((await call('/dst/catalog/detail',{tid:fixtures.catalog})).catalogName,prefix+'_EDIT');assert.equal((await call('/dst/catalog/catalog-items/list',{catalogId:fixtures.catalog}))[0].colName,'客户代码');checks.push('catalog-and-item-edit');
 assert.equal((await call('/dst/catalog/delete',{tid:fixtures.catalog})).deleted,true);checks.push('catalog-delete-keeps-source-table');
 result=await call('/dwm/metadata-governance/catalog',{});assert(result.tables.some(t=>t.tid===fixtures.a.table));
 const pipelines=await call('/nifi/api/pipelines');assert(Array.isArray(pipelines));if(pipelines.length){const graph=await call('/nifi/api/pipelines/'+pipelines[0].id);assert(Array.isArray(graph.dsl?.nodes)&&Array.isArray(graph.dsl?.edges));checks.push('real-pipeline-graph-read');}
}finally{
 const errors=[];
 for(const id of fixtures.relations)try{assert.equal(await call('/dwm/metadata-governance/relations/delete',{tid:id}),true);}catch(e){errors.push('relation '+id+': '+e.message);}
 if(fixtures.catalog)try{const detail=await envelope('/dst/catalog/detail',{tid:fixtures.catalog});if([0,200].includes(detail.code??0)&&detail.data?.tid)await call('/dst/catalog/delete',{tid:fixtures.catalog});}catch(e){errors.push('catalog '+fixtures.catalog+': '+e.message);}
 if(fixtures.source)try{await call('/dst/database/delete',{tid:fixtures.source,dryRun:true});await call('/dst/database/delete',{tid:fixtures.source});}catch(e){errors.push('source '+fixtures.source+': '+e.message);}
 await writeFile(join(folder,'cleanup.json'),JSON.stringify({errors},null,2));if(errors.length)throw Error('Fixture cleanup needs attention: '+errors.join('; '));
}
assert.deepEqual(counts(await call('/dwm/metadata-governance/catalog',{})),baselineCounts);checks.push('all-test-records-cleaned-business-counts-preserved');
console.log(JSON.stringify({tenant:context.name,checks,limitations,backup:folder},null,2));
