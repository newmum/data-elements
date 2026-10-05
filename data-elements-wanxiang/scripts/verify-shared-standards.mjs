/** Exercises the shared standard lifecycle using disposable, uniquely named fixtures.
 * It never connects to a physical source database, edits existing standards, or logs credentials.
 * DATA_ELEMENTS_TEST_PASSWORD must be provided only in the process environment.
 */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),{sm3}=require('sm-crypto');
const base=process.env.DATA_ELEMENTS_TEST_API||'http://localhost:8088',tenant='2084109831682699265';
if(!process.env.DATA_ELEMENTS_TEST_PASSWORD)throw new Error('Missing test-login environment credential');
const started=Date.now(),checks=[],fixtures={};
const login=await fetch(base+'/portal/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({account:'manager',password:sm3(process.env.DATA_ELEMENTS_TEST_PASSWORD),appId:'1995678661281710081',tenantId:tenant})}).then(r=>r.json());
const token=typeof login.data==='string'?login.data:login.data?.token;
assert.ok(token,'Platform login did not return a session: '+String(login.msg||login.message||login.code));
async function envelope(path,body){const response=await fetch(base+path,{method:body===undefined?'GET':'POST',headers:{token,...(body===undefined?{}:{'Content-Type':'application/json'})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)});assert.ok(response.ok,path+' HTTP failure');return response.json();}
async function call(path,body){const result=await envelope(path,body);assert.ok([0,1,200].includes(result.code),path+' rejected: '+(result.msg||result.message||'unknown'));return result.data;}
async function rejected(path,body,code){const result=await envelope(path,body);assert.equal(result.code,code,path+' should reject invalid input');}
const readElement=tid=>call('/dwm/standard/element/queryById',{tid});
const publish=(tid,value)=>call('/dwm/standard/element/updatePublishStatus',{tid,publishStatus:value});
const context=await call('/sym/tenant/current');assert.equal(context.tid,tenant);checks.push('real-login-and-intended-tenant');
const user=await call('/sym/user/me',{}),userId=user.id||user.userId||user.tid;
const originalPage=await call('/dwm/standard/element/page',{pageNum:1,pageSize:1000});
const originalCodes=await call('/dwm/standard/code/list',{});
assert.ok(Array.isArray(originalPage.list)&&Array.isArray(originalCodes));checks.push('shared-element-and-code-list');
const [classes,types,rules]=await Promise.all([call('/dwm/standard/standard-class-list',{}),call('/sym/dict?code=colType'),call('/dwm/quality/list',{})]);
assert.ok(classes&&Array.isArray(types)&&Array.isArray(rules));checks.push('original-form-options');
for(const id of ['dwm_standard_landing_list_01','dwm_standard_landing_save_01']){const file=await call('/api/web/resource/file/'+id);assert.ok(file.script&&file.script.includes('tenantRuntime.id()'));}checks.push('both-live-magic-scripts-loaded');
const originalLanding=await call('/dwm/standard/landing/list',{});assert.ok(Array.isArray(originalLanding.items));checks.push('real-field-coverage-not-preview-counts');
await rejected('/dwm/standard/landing/save',{},400);checks.push('binding-required-parameter-validation');
const key=String(Date.now()),name='WX标准联调'+key;
const codeBody={dictName:name+'代码集',dictCode:'WX_CODE_'+key,codeSet:'WX_CODE_'+key,codeValue:'__HEADER__',codeName:name+'代码集',description:'仅用于数据标准接口联调，完成后删除',dictItemValue:JSON.stringify([{code:'01',name:'第一项',description:'保留前导零'},{code:'02',name:'第二项',description:'平面代码条目'}]),status:0,createdBy:userId,updatedBy:userId};
const metaBody={metaName:name,metaCode:'WX_META_'+key,standardEncode:'WX_'+key,bizDef:'仅用于标准完整字段及审批流程联调',dataTypeId:'varchar',fieldType:'varchar',fieldLength:20,numericPrecision:null,numericScale:null,formatPattern:'^WX-[0-9]{6}$',exampleValue:'WX-000001',standardCodeSet:codeBody.codeSet,isNullable:0,dataLevel:'2',dataCategoryId:null,dataCategoryName:null,dataCategoryPath:null,qualityRule:rules[0]?.tid||null,customRule:'原有规则说明保留',publishStatus:0,createdBy:userId,updatedBy:userId};
try{
 let code=await call('/dwm/standard/code/saveOrUpdate',codeBody);fixtures.code=code.tid;assert.ok(fixtures.code);assert.equal(Number(code.status),0);
 code=await call('/dwm/standard/code/queryById',{tid:fixtures.code});assert.equal(JSON.parse(code.dictItemValue)[0].code,'01');assert.equal(JSON.parse(code.dictItemValue)[1].description,'平面代码条目');checks.push('code-create-leading-zero-flat-items-and-description');
 code=await call('/dwm/standard/code/saveOrUpdate',{...codeBody,tid:fixtures.code,status:1});assert.equal(Number(code.status),1);
 code=await call('/dwm/standard/code/saveOrUpdate',{...codeBody,tid:fixtures.code,status:0,description:'下线后修改'});assert.equal(Number(code.status),0);assert.equal(code.description,'下线后修改');checks.push('code-publish-offline-and-edit');
 let meta=await call('/dwm/standard/element/metaSaveOrUpdate',{...metaBody,valueDomainId:fixtures.code});fixtures.element=meta.tid;assert.ok(fixtures.element);assert.equal(Number(meta.publishStatus),0);
 meta=await readElement(fixtures.element);for(const field of ['metaName','metaCode','standardEncode','formatPattern','exampleValue','customRule','fieldLength','isNullable','dataLevel'])assert.equal(String(meta[field]),String(metaBody[field]),field+' not preserved');assert.equal(meta.valueDomainId,fixtures.code);checks.push('element-create-complete-original-fields');
 let pending=await call('/dwm/standard/element/page',{pageNum:1,pageSize:1000,publishStatus:0});assert.ok(pending.list.some(r=>r.tid===fixtures.element));checks.push('saved-standard-enters-review-list');
 meta=await publish(fixtures.element,1);assert.equal(Number(meta.publishStatus),1);pending=await call('/dwm/standard/element/page',{pageNum:1,pageSize:1000,publishStatus:0});assert.ok(!pending.list.some(r=>r.tid===fixtures.element));checks.push('approve-publish-removes-from-review');
 await publish(fixtures.element,0);meta=await call('/dwm/standard/element/metaSaveOrUpdate',{...metaBody,tid:fixtures.element,valueDomainId:fixtures.code,bizDef:'下线修改后再次审批',exampleValue:'WX-000002'});assert.equal(Number(meta.publishStatus),0);assert.equal(meta.exampleValue,'WX-000002');await publish(fixtures.element,1);checks.push('offline-edit-reapprove');
 assert.ok(new RegExp((await readElement(fixtures.element)).formatPattern).test('WX-000002'));assert.equal(new RegExp(metaBody.formatPattern).test('bad'),false);checks.push('encoding-shares-real-format-pattern');
 const props={dbName:name+'字段绑定测试',dbType:'mysql',orgId:user.orgId,storageDomain:'政务外网',assetDesc:'Disposable standard binding fixture; no physical source access',accessMode:'explore',dataAccessMode:'explore',showConnect:0,preserveSourceOrg:true,assetStatus:2};
 const source=await call('/dst/database/saveOrUpdate',{tid:'',assetType:'db',deferIndexRefresh:true,propList:props});fixtures.source=source.tid;assert.ok(fixtures.source);
 const tableName='wx_standard_fixture_'+key;
 // Seed only our disposable tenant-scoped field fixture; offline import is outside this module.
 const seeded=await call('/data/save',{
  db_table_t:[{datasourceId:fixtures.source,tableName,tableNameEn:tableName,tableNameCn:'标准落地临时验证表',tableComment:'标准落地临时验证表',tableType:'数据表',businessType:'业务表',dataSourceType:'mysql',fieldCount:1,annotated:0,assetStatus:2,flowStatus:2}],
  db_table_column_t:[{tableId:'#db_table_t.tid',columnName:'standard_code',columnComment:'原始标准编码注释',dataType:'varchar',columnType:'varchar(20)',length:20,nullable:0,primaryKey:0,ordinalPosition:1}],
 });
 fixtures.table=seeded.find(r=>r.table==='db_table_t')?.tid;
 fixtures.column=seeded.find(r=>r.table==='db_table_column_t')?.tid;
 assert.ok(fixtures.table&&fixtures.column);checks.push('disposable-tenant-field-fixture');
 let landing=await call('/dwm/standard/landing/list',{});const entity=landing.entities.find(e=>e.name===tableName),field=entity?.fields.find(f=>f.name==='standard_code');assert.ok(entity&&field);fixtures.table=entity.id;fixtures.column=field.id;
 const beforeTables=await call('/dst/database/metadata/tables?dbId='+fixtures.source+'&includeGovernance=false');
 const binding={tableId:fixtures.table,columnId:fixtures.column,standardId:fixtures.element,expectedStandardId:null};
 assert.equal((await call('/dwm/standard/landing/save',{...binding,dryRun:true})).valid,true);checks.push('binding-safe-preview');
 await call('/dwm/standard/landing/save',binding);landing=await call('/dwm/standard/landing/list',{});assert.equal(landing.items.find(i=>i.field_id===fixtures.column).target_id,fixtures.element);checks.push('bind-authoritative-field-standard');
 await rejected('/dwm/standard/landing/save',{...binding,standardId:null},409);checks.push('stale-binding-optimistic-conflict');
 await publish(fixtures.element,0);landing=await call('/dwm/standard/landing/list',{});assert.equal(landing.items.find(i=>i.field_id===fixtures.column).status,'STALE');checks.push('offline-standard-reference-marked-unavailable');
 await call('/dwm/standard/landing/save',{...binding,standardId:null,expectedStandardId:fixtures.element});
 await rejected('/dwm/standard/landing/save',binding,409);checks.push('unpublished-standard-cannot-bind');
 const afterTables=await call('/dst/database/metadata/tables?dbId='+fixtures.source+'&includeGovernance=false');
 const pickTable=rows=>{const row=rows.find(r=>r.tid===fixtures.table);return [row?.annotated,row?.businessType,row?.catalogId,row?.tableComment,row?.flowStatus,row?.assetStatus];};assert.deepEqual(pickTable(afterTables),pickTable(beforeTables));
 landing=await call('/dwm/standard/landing/list',{});assert.equal(landing.items.find(i=>i.field_id===fixtures.column).target_id,null);assert.equal(landing.entities.find(e=>e.id===fixtures.table).fields[0].displayName,'原始标准编码注释');checks.push('unbind-preserves-comments-registration-and-catalog');
}finally{
 // Exact IDs are only populated after creating our own fixtures; never infer cleanup targets.
 const cleanupErrors=[];
 if(fixtures.source)try{await call('/dst/database/delete',{tid:fixtures.source,dryRun:true});await call('/dst/database/delete',{tid:fixtures.source});checks.push('remove-own-source-and-field-fixture');}catch(e){cleanupErrors.push('source: '+e.message);}
 if(fixtures.element)try{await publish(fixtures.element,0);await call('/dwm/standard/element/meatDeleteById',{tid:fixtures.element});checks.push('remove-own-element-fixture');}catch(e){cleanupErrors.push('element: '+e.message);}
 if(fixtures.code)try{await call('/dwm/standard/code/saveOrUpdate',{...codeBody,tid:fixtures.code,status:0});await call('/dwm/standard/code/deleteById',{tid:fixtures.code});checks.push('remove-own-code-fixture');}catch(e){cleanupErrors.push('code: '+e.message);}
 if(cleanupErrors.length)throw new Error('Fixture cleanup needs attention: '+cleanupErrors.join('; '));
}
const finalPage=await call('/dwm/standard/element/page',{pageNum:1,pageSize:1000});assert.ok(!finalPage.list.some(r=>r.tid===fixtures.element));
const finalCodes=await call('/dwm/standard/code/list',{});assert.ok(!finalCodes.some(r=>r.tid===fixtures.code));checks.push('deleted-fixtures-absent-from-shared-lists');
const finalLanding=await call('/dwm/standard/landing/list',{});assert.ok(!finalLanding.entities.some(r=>r.id===fixtures.table));assert.ok(!finalLanding.items.some(r=>r.field_id===fixtures.column));
assert.equal(finalPage.total,originalPage.total);assert.equal(finalCodes.length,originalCodes.length);assert.equal(finalLanding.entities.length,originalLanding.entities.length);assert.equal(finalLanding.coverage.totalFields,originalLanding.coverage.totalFields);checks.push('original-business-counts-preserved');
console.log(JSON.stringify({tenant:context.name,originalStandards:originalPage.total,originalCodeSets:originalCodes.length,originalTables:originalLanding.entities.length,originalFields:originalLanding.coverage.totalFields,checks,totalMs:Date.now()-started},null,2));
