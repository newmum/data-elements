/** Live extension verification. All writes/DDL use new disposable fixtures.
 * Credentials only from environment; no keys, rows, connection details or tokens are logged. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {mkdir,writeFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {verificationLogin} from './shared-verification-auth.mjs';
const require=createRequire(import.meta.url),{sm3}=require('../../data-elements-front/node_modules/sm-crypto'),exec=promisify(execFile);
for(const env of ['METADATA_TEST_ACCOUNT','METADATA_TEST_PASSWORD','WX_META_JAVA','WX_META_DRIVER','WX_META_DB_HOST','WX_META_DB_USER','WX_META_DB_PASSWORD'])assert(process.env[env],env+' must be set');
assert.equal(process.env.WX_META_DATABASE,'baseline_ga_old','Resolve the actual active tenant before writes');
const key=Date.now().toString(36),prefix='WX_META_EXT_'+key,folder=join(tmpdir(),'wx-metadata-extensions-'+key);await mkdir(folder);
const base='http://localhost:3010/dev-api';
const login=await verificationLogin(base,process.env.METADATA_TEST_ACCOUNT,process.env.METADATA_TEST_PASSWORD);
const headers={'Content-Type':'application/json',token:login.data.token};
async function raw(path,body,side=3010){const response=await fetch(`http://localhost:${side}/dev-api${path}`,{method:body===undefined?'GET':'POST',headers,...(body===undefined?{}:{body:JSON.stringify(body)}),signal:AbortSignal.timeout(65000)});const text=await response.text();let json;try{json=text?JSON.parse(text):{};}catch{throw Error(path+' returned non-JSON HTTP '+response.status);}return {http:response.status,...json};}
const success=r=>r.http<400&&[0,200].includes(r.code??0);
const snake=row=>Object.fromEntries(Object.entries(row).flatMap(([k,v])=>[[k,v],[k.replace(/[A-Z]/g,c=>'_'+c.toLowerCase()),v]]));
async function call(path,body,side=3010){const r=await raw(path,body,side);assert(success(r),path+': '+String(r.message||r.msg||r.error||'HTTP '+r.http));const data='data'in r?r.data:r;if(path==='/dwm/metadata-governance/catalog')return Object.fromEntries(Object.entries(data).map(([k,v])=>[k,Array.isArray(v)?v.map(snake):v]));return path.includes('/relations/save')?snake(data):data;}
async function reject(name,path,body){assert(!success(await raw(path,body)),name+' must be rejected');pass(name);}
const checks=[],fixtures={relations:[]},counts=r=>['sources','tables','columns','relations'].map(k=>r[k].length),pass=name=>{checks.push(name);console.log('PASS '+name);};
const record=()=>writeFile(join(folder,'fixtures.json'),JSON.stringify(fixtures,null,2));
// Java source-file mode keeps this disposable helper outside the repository and
// removes the need for a manually installed temp .class when rerunning the test.
const fixtureSource=[
 "import java.sql.*;",
 "/** Disposable fixtures only. Never accepts arbitrary SQL or table names. */",
 "public class WxMetadataExtensionsFixture {",
 " public static void main(String[] args)throws Exception {",
 "  String database=System.getenv(\"WX_META_DATABASE\"),key=args[1];",
 "  if(!\"baseline_ga_old\".equals(database)||!key.matches(\"[a-z0-9]{8,16}\"))throw new IllegalArgumentException(\"Unexpected fixture scope\");",
 "  String p=\"wx_meta_ext_\"+key+\"_parent\",c=\"wx_meta_ext_\"+key+\"_child\",t=\"wx_meta_ext_\"+key+\"_sink\";",
 "  try(Connection db=DriverManager.getConnection(\"jdbc:mysql://\"+System.getenv(\"WX_META_DB_HOST\")+\":3306/\"+database+\"?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=15000\",System.getenv(\"WX_META_DB_USER\"),System.getenv(\"WX_META_DB_PASSWORD\"));Statement s=db.createStatement()) {",
 "   s.setQueryTimeout(15);",
 "   switch(args[0]) {",
 "    case \"setup\":",
 "     s.execute(\"CREATE TABLE `\"+p+\"` (a INT NOT NULL,b INT NOT NULL,label VARCHAR(50),active INT NOT NULL,PRIMARY KEY(a,b)) ENGINE=InnoDB\");",
 "     s.execute(\"CREATE TABLE `\"+c+\"` (id INT NOT NULL PRIMARY KEY,a INT,b INT,label VARCHAR(50),active INT NOT NULL) ENGINE=InnoDB\");",
 "     s.execute(\"CREATE TABLE `\"+t+\"` (id INT NOT NULL PRIMARY KEY,a INT,b INT,label VARCHAR(50),active INT NOT NULL) ENGINE=InnoDB\");",
 "     s.executeUpdate(\"INSERT INTO `\"+p+\"` VALUES(1,10,'alpha',1),(2,20,'beta',1),(3,30,'gamma',1)\");",
 "     s.executeUpdate(\"INSERT INTO `\"+c+\"` VALUES(1,1,10,'alpha',1),(2,1,99,'orphan',0),(3,2,20,'beta',1),(4,NULL,30,'null-key',1)\");break;",
 "    case \"repair\":",
 "     if(s.executeUpdate(\"DELETE FROM `\"+c+\"` WHERE id=2 AND a=1 AND b=99\")!=1)throw new AssertionError(\"Unexpected orphan fixture\");",
 "     if(s.executeUpdate(\"UPDATE `\"+c+\"` SET a=3 WHERE id=4 AND a IS NULL AND b=30\")!=1)throw new AssertionError(\"Unexpected null fixture\");break;",
 "    case \"assert-constraint\":",
 "     try{s.executeUpdate(\"INSERT INTO `\"+c+\"` VALUES(99,99,99,'must reject',1)\");throw new AssertionError(\"Physical FK was not enforced\");}catch(SQLException e){if(!e.getSQLState().startsWith(\"23\"))throw e;}break;",
 "    case \"assert-sink\":try(ResultSet r=s.executeQuery(\"SELECT id,label FROM `\"+t+\"` ORDER BY id\")){int n=0;while(r.next()){int id=r.getInt(1);String expected=id==1?\"ALPHA\":id==3?\"BETA\":id==4?\"NULL-KEY\":\"unexpected\";if(!expected.equals(r.getString(2)))throw new AssertionError(\"Unexpected fixture transformation\");n++;}if(n!=3)throw new AssertionError(\"Fixture pipeline has not written expected rows yet\");}break;",
 "    case \"drop\":s.execute(\"DROP TABLE IF EXISTS `\"+t+\"`\");s.execute(\"DROP TABLE IF EXISTS `\"+c+\"`\");s.execute(\"DROP TABLE IF EXISTS `\"+p+\"`\");break;",
 "    default:throw new IllegalArgumentException(\"Unknown fixture action\");",
 "   }",
 "  }",
 "  System.out.println(\"PASS isolated physical fixture: \"+args[0]);",
 " }",
 "}",
].join('\n');
const fixtureFile=join(folder,'WxMetadataExtensionsFixture.java');let fixtureWritten=false;
const physical=async action=>{if(!fixtureWritten){await writeFile(fixtureFile,fixtureSource);fixtureWritten=true;}const r=await exec(process.env.WX_META_JAVA,['-cp',process.env.WX_META_DRIVER,fixtureFile,action,key],{timeout:30000,windowsHide:true});assert(r.stdout.includes('PASS isolated physical fixture'));};
const tenant=await call('/sym/tenant/current');assert.equal(String(tenant.tid),'2084109831682699264');
const beforeCatalog=await call('/dwm/metadata-governance/catalog',{}),before=counts(beforeCatalog),user=await call('/sym/user/me',{});
await writeFile(join(folder,'before-counts.json'),JSON.stringify({tenant:tenant.tid,counts:before}));
try {
 fixtures.physical=true;await record();await physical('setup');pass('isolated-composite-key-physical-fixtures');
 const source=await call('/dst/database/saveOrUpdate',{tid:'',assetType:'db',propList:{dbName:prefix,dbType:'mysql',orgId:user.orgId,storageDomain:'政务外网',assetDesc:'Disposable extensions verification fixture',accessMode:'explore',dataAccessMode:'explore',showConnect:1,preserveSourceOrg:true,assetStatus:0,jdbcURL:`jdbc:mysql://${process.env.WX_META_DB_HOST}:3306/${process.env.WX_META_DATABASE}?useSSL=false&allowPublicKeyRetrieval=true`,host:process.env.WX_META_DB_HOST,port:'3306',database:process.env.WX_META_DATABASE,username:process.env.WX_META_DB_USER,password:process.env.WX_META_DB_PASSWORD}});fixtures.source=source.tid;assert(fixtures.source);await record();
 async function seed(kind){const name='wx_meta_ext_'+key+'_'+kind;const names=kind!=='parent'?['id','a','b','label','active']:['a','b','label','active'];await call('/data/save',{db_table_t:[{datasourceId:fixtures.source,tableName:name,tableNameEn:name,tableNameCn:name,tableComment:'临时扩展验证表',tableType:'数据表',businessType:'业务表',dataSourceType:'mysql',fieldCount:names.length,annotated:0,assetStatus:2,flowStatus:2}],db_table_column_t:names.map((field,i)=>({tableId:'#db_table_t.tid',columnName:field,columnComment:'测试字段',dataType:field==='label'?'varchar':'int',columnType:field==='label'?'varchar(50)':'int',nullable:kind==='child'&&['a','b'].includes(field)?1:0,primaryKey:kind==='parent'?['a','b'].includes(field)?1:0:field==='id'?1:0,ordinalPosition:i+1}))});const meta=await call('/dwm/metadata-governance/catalog',{}),t=meta.tables.find(t=>t.datasource_id===fixtures.source&&t.table_name===name);assert(t);const cols=Object.fromEntries(meta.columns.filter(c=>c.table_id===t.tid).map(c=>[c.column_name,c.tid]));assert.equal(Object.keys(cols).length,names.length);return {table:t.tid,columns:cols};}
 fixtures.child=await seed('child');fixtures.parent=await seed('parent');await record();
 const a=fixtures.child,b=fixtures.parent,definition={relationName:prefix,relationType:'REFERENCE',origin:'MANUAL',status:'CONFIRMED',sourceTableId:a.table,targetTableId:b.table,mappings:[{sourceColumnId:a.columns.a,targetColumnId:b.columns.a},{sourceColumnId:a.columns.b,targetColumnId:b.columns.b}],targetsPerSource:{min:'1',max:'1'},sourcesPerTarget:{min:'0',max:'many'},conditions:[],description:'Disposable composite-key validation fixture'};
 let relation=await call('/dwm/metadata-governance/relations/save',definition);fixtures.relations.push(relation.tid);await record();assert.equal(JSON.parse(relation.definition_json).mappings.length,2);pass('composite-mappings-and-cardinality-roundtrip');
 await reject('duplicate-mapping-rejected','/dwm/metadata-governance/relations/save',{...definition,mappings:[definition.mappings[0],definition.mappings[0]]});
 await reject('cross-table-field-rejected','/dwm/metadata-governance/relations/save',{...definition,mappings:[{sourceColumnId:b.columns.a,targetColumnId:b.columns.b}]});
 await reject('stale-relation-edit-rejected','/dwm/metadata-governance/relations/save',{...definition,tid:relation.tid,expectedVersion:0});
 const evidence=await call('/dwm/metadata-governance/relations/validate',{tid:relation.tid,sampleLimit:200});assert.equal(evidence.simulated,false);assert.equal(evidence.validationStatus,'violations_found');assert.equal(Number(evidence.metrics.orphanRows),1);assert.equal(Number(evidence.metrics.sourceNullRows),1);assert.equal(evidence.metrics.rowMatchRatio,2/3);pass('real-business-key-validation-aggregate-only');
 relation=await call('/dwm/metadata-governance/relations/save',{...definition,tid:relation.tid,targetsPerSource:{min:'0',max:'1'},conditions:[{side:'source',columnId:a.columns.active,operator:'eq',value:1}]});assert.equal(relation.validation_json,null);const conditional=await call('/dwm/metadata-governance/relations/validate',{tid:relation.tid});assert.equal(conditional.validationStatus,'full_supported');assert.equal(Number(conditional.metrics.orphanRows),0);pass('AND-condition-filter-and-evidence-invalidation');
 await reject('conditional-physical-foreign-key-rejected','/dwm/metadata-governance/relations/foreign-key/preview',{tid:relation.tid});
 relation=await call('/dwm/metadata-governance/relations/save',{...definition,tid:relation.tid});
 const plan=await call('/dwm/metadata-governance/relations/foreign-key/preview',{tid:relation.tid});assert(plan.ddl.includes('FOREIGN KEY'));assert.equal(plan.alreadyExists,false);assert(plan.rollbackSql);pass('readonly-composite-physical-key-preview');
 await reject('unconfirmed-DDL-rejected','/dwm/metadata-governance/relations/foreign-key/create',{tid:relation.tid,previewHash:plan.hash,confirmPhysicalChange:false});
 await reject('stale-preview-hash-rejected','/dwm/metadata-governance/relations/foreign-key/create',{tid:relation.tid,previewHash:'invalid-hash',confirmPhysicalChange:true});
 await reject('existing-orphans-block-DDL','/dwm/metadata-governance/relations/foreign-key/create',{tid:relation.tid,previewHash:plan.hash,confirmPhysicalChange:true});
 await physical('repair');const valid=await call('/dwm/metadata-governance/relations/validate',{tid:relation.tid});assert.equal(valid.validationStatus,'full_supported');assert.equal(valid.metrics.rowMatchRatio,1);
 const created=await call('/dwm/metadata-governance/relations/foreign-key/create',{tid:relation.tid,previewHash:plan.hash,confirmPhysicalChange:true});assert.equal(created.executed,true);await physical('assert-constraint');const retry=await call('/dwm/metadata-governance/relations/foreign-key/create',{tid:relation.tid,previewHash:plan.hash,confirmPhysicalChange:true});assert.equal(retry.alreadyExists,true);pass('explicit-physical-FK-enforced-and-idempotent');
 await reject('physical-receipt-blocks-mapping-rewrite','/dwm/metadata-governance/relations/save',{...definition,tid:relation.tid,conditions:[{side:'source',columnId:a.columns.active,operator:'eq',value:1}]});
 const draft={taskName:prefix,taskCode:prefix,datasourceId:fixtures.source,tableId:a.table,triggerMode:'MANUAL',shardCount:1,shardKey:'',sampleLimit:10,timeoutMinutes:5,resourceGroup:'DEFAULT',cronExpression:'',description:'Disposable binding fixture; never enabled or run',rules:[{ruleName:'fixture-not-null',ruleType:'NOT_NULL',dimension:'COMPLETENESS',columnName:'a',severity:'HIGH',weight:1,parameters:{}}]};
 const task=await call('/dwm/quality/task/save',draft);fixtures.task=task.tid||task.id;assert(fixtures.task);await record();const detail=await call('/dwm/quality/task/detail',{taskId:fixtures.task});const rule=detail.rules[0].tid;assert(rule);
 const binding={tableId:a.table,columnId:a.columns.a,mode:'QUALITY',targetId:rule,expectedTargetId:''};await call('/dwm/metadata-governance/bindings/save',{...binding,dryRun:true});await call('/dwm/metadata-governance/bindings/save',binding);
 await reject('stale-quality-binding-rejected','/dwm/metadata-governance/bindings/save',binding);
 await reject('quality-rule-for-other-field-rejected','/dwm/metadata-governance/bindings/save',{...binding,columnId:a.columns.b});
 let bindings=await call('/dwm/metadata-governance/bindings/list',{},3000);assert(bindings.items.some(i=>i.field_id===a.columns.a&&i.target_id===rule&&i.status==='CONFIRMED'));pass('existing-quality-rule-bound-shared-between-frontends');
 const ref={tableId:a.table,columnId:a.columns.a,mode:'REFERENCE',targetId:b.columns.a,expectedTargetId:''};await call('/dwm/metadata-governance/bindings/save',ref);
 await reject('reference-cycle-rejected','/dwm/metadata-governance/bindings/save',{tableId:b.table,columnId:b.columns.a,mode:'REFERENCE',targetId:a.columns.a,expectedTargetId:''});
 await reject('reference-self-rejected','/dwm/metadata-governance/bindings/save',{tableId:a.table,columnId:a.columns.b,mode:'REFERENCE',targetId:a.columns.b,expectedTargetId:''});
 await call('/dwm/metadata-governance/bindings/save',{...ref,targetId:null,expectedTargetId:b.columns.a});
 const concurrent=await Promise.all([raw('/dwm/metadata-governance/bindings/save',ref),raw('/dwm/metadata-governance/bindings/save',{tableId:b.table,columnId:b.columns.a,mode:'REFERENCE',targetId:a.columns.a,expectedTargetId:''})]);assert.equal(concurrent.filter(success).length,1);pass('concurrent-reference-edits-cannot-create-cycle');
 for(const [tableId,columnId,targetId]of[[a.table,a.columns.a,b.columns.a],[b.table,b.columns.a,a.columns.a]]){const list=await call('/dwm/metadata-governance/bindings/list',{});if(list.items.some(i=>i.field_id===columnId&&i.mapping_mode==='REFERENCE'))await call('/dwm/metadata-governance/bindings/save',{tableId,columnId,mode:'REFERENCE',targetId:null,expectedTargetId:targetId});}
 await call('/dwm/metadata-governance/bindings/save',{...binding,targetId:null,expectedTargetId:rule});pass('quality-and-field-bindings-can-be-cleared');
 const sink=await seed('sink');fixtures.sink=sink;await record();
 const sourceNode=await call('/nifi/api/pipelines/template/node',{key:'source.jdbc',tableId:a.table}),sinkNode=await call('/nifi/api/pipelines/template/node',{key:'sink.jdbc',tableId:sink.table}),transform=await call('/nifi/api/pipelines/template/node',{key:'transform.field-mapping',sourceTableId:a.table,targetTableId:sink.table});assert(sourceNode.manifestKey&&sinkNode.manifestKey&&transform.manifestKey);
 const schema=['id','a','b','label','active'].map(name=>({name,path:'/'+name,type:name==='label'?'varchar(50)':'int',nullable:false,isPk:['a','b'].includes(name)}));const recommended=await call('/nifi/api/field-mapping/recommend',{sourceSchema:schema,targetSchema:schema});assert(recommended.spec.mappings.length>=3);
 const spec={version:'1.0',passthroughUnmapped:false,passthroughCaseSensitive:true,onMissingSource:'NULL',onTypeMismatch:'CAST',mappings:schema.map(f=>f.name==='label'?{to:'/label',expression:'UPPER(${field:label})'}:{from:f.path,to:f.path})};const normalized=await call('/nifi/api/field-mapping/migrate-legacy',{spec:JSON.stringify(spec)});const validation=await call('/nifi/api/field-mapping/validate',{spec:normalized,sourceSchema:schema,targetSchema:schema});assert.equal(validation.valid,true,JSON.stringify(validation.errors));const compiled=await call('/nifi/api/field-mapping/compile',{spec:normalized});assert(compiled.query.includes('UPPER'));pass('shared-field-rule-recommend-normalize-validate-compile');
 const dsl={version:1,nodes:[{id:'s',manifestKey:sourceNode.manifestKey,category:'source',label:'Fixture source',x:80,y:100,config:sourceNode.config},{id:'m',manifestKey:transform.manifestKey,category:'transform',label:'Fixture mapping',x:400,y:100,config:{...transform.config,sourceTableId:a.table,targetTableId:sink.table,mappings:JSON.stringify(normalized)}},{id:'t',manifestKey:sinkNode.manifestKey,category:'sink',label:'Fixture sink',x:720,y:100,config:sinkNode.config}],edges:[{id:'sm',source:'s',target:'m',outlet:'success'},{id:'mt',source:'m',target:'t',outlet:'success'}]};let pipeline=await call('/nifi/api/pipelines',{name:prefix,description:'Disposable graph fixture; never deployed',dsl});fixtures.pipeline=pipeline.id;assert(fixtures.pipeline);await record();const firstTime=pipeline.updatedAt;
 pipeline=await call('/nifi/api/pipelines/'+pipeline.id+'?expectedUpdatedAt='+firstTime,{name:prefix,description:'Edited isolated graph',dsl:{...dsl,nodes:dsl.nodes.map(n=>n.id==='m'?{...n,x:470}:n)}});assert.equal(pipeline.dsl.nodes.find(n=>n.id==='m').x,470);assert(!pipeline.nifiProcessGroupId);await reject('stale-processing-save-rejected','/nifi/api/pipelines/'+pipeline.id+'?expectedUpdatedAt='+firstTime,{name:prefix,dsl});const other=await call('/nifi/api/pipelines/'+pipeline.id,undefined,3000);assert.equal(other.dsl.nodes.find(n=>n.id==='m').x,470);assert.equal(JSON.parse(other.dsl.nodes.find(n=>n.id==='m').config.mappings).mappings.find(m=>m.to==='/label').expression,'UPPER(${field:label})');pass('processing-graph-position-rules-roundtrip-shared-without-deploy');
 if(process.env.WX_VERIFY_LINEAGE_RUNTIME==='1'){
  const functionSpec={version:'1.0',functions:{normalizeCode:{expression:'UPPER(TRIM(${value}))'}},mappings:[{to:'/label',userFunction:{name:'normalizeCode',from:'/label',args:[]}}]};
  const preview=await call('/nifi/api/field-mapping/preview',{spec:functionSpec,sampleRows:[{label:" a'|b "}]});
  assert(JSON.stringify(preview).includes("A'|B"),'Shared preview must evaluate safe scalar functions, not return expression text');pass('real-expression-function-preview');
  const lookup=await call('/nifi/api/field-mapping/lookup-template',{datasourceId:fixtures.source,tableId:b.table,keyColumn:'a',valueColumn:'label'});assert(lookup.sql.includes('?'));assert.equal(lookup.dataSource.datasourceId,fixtures.source);assert(!lookup.connection);pass('owned-parameterized-dictionary-template');
  const deployed=await call('/nifi/api/pipelines/'+pipeline.id+'/deploy',{});assert(deployed.processGroupId);let runtime=await call('/nifi/api/pipelines/'+pipeline.id+'/status');assert.equal(runtime.deployed,true);assert.notEqual(runtime.status,'RUNNING');pass('explicit-real-NiFi-deploy-without-start');
  await call('/nifi/api/pipelines/'+pipeline.id+'/start',{});const deadline=Date.now()+90000;let written=false;while(Date.now()<deadline){try{await physical('assert-sink');written=true;break;}catch{await new Promise(resolve=>setTimeout(resolve,2000));}}assert(written,'NiFi did not produce expected transformed fixture rows');pass('real-NiFi-processed-only-isolated-fixture-tables');
  await call('/nifi/api/pipelines/'+pipeline.id+'/stop',{});runtime=await call('/nifi/api/pipelines/'+pipeline.id+'/status');assert.notEqual(runtime.status,'RUNNING');pass('explicit-real-NiFi-stop');
  // NiFi indexes provenance asynchronously; wait only on this isolated node,
  // never accept a graph from unrelated recent events as evidence.
  const provenanceWait=Math.min(900000,Math.max(30000,Number(process.env.WX_VERIFY_PROVENANCE_WAIT_MS)||30000));
  const provenanceStarted=Date.now(),lineageDeadline=provenanceStarted+provenanceWait;let lineage;
  do{lineage=await call('/nifi/api/pipelines/'+pipeline.id+'/nodes/m/lineage?maxEvents=10',{});if(lineage.anchorEventId&&lineage.nodes.length)break;await new Promise(resolve=>setTimeout(resolve,5000));}while(Date.now()<lineageDeadline);
  assert(lineage.anchorEventId&&lineage.nodes.length,'Isolated node has no actual provenance graph');assert(!(lineage.events||[]).some(e=>e.attributes||e.transitUri));await writeFile(join(folder,'runtime-evidence.json'),JSON.stringify({nodes:lineage.nodes.length,links:(lineage.links||lineage.edges).length,hasRealEvent:!!lineage.anchorEventId,indexWaitMs:Date.now()-provenanceStarted}));pass('real-NiFi-provenance-lineage-evidence');
  await call('/nifi/api/pipelines/'+pipeline.id+'/undeploy',{});assert.equal((await call('/nifi/api/pipelines/'+pipeline.id+'/status')).deployed,false);pass('explicit-real-NiFi-undeploy-preserves-saved-design');
 }
} catch(error) {
 await writeFile(join(folder,'failure.json'),JSON.stringify({message:String(error.message).slice(0,1000),checks},null,2));
 throw error;
} finally {
 const errors=[];
 if(fixtures.pipeline)try{await call('/nifi/api/pipelines/delete/'+fixtures.pipeline,{});}catch(e){errors.push('pipeline: '+e.message);}
 if(fixtures.task)try{await call('/dwm/quality/task/delete',{taskId:fixtures.task});}catch(e){errors.push('task: '+e.message);}
 for(const tid of fixtures.relations)try{await call('/dwm/metadata-governance/relations/delete',{tid});}catch(e){errors.push('relation: '+e.message);}
 if(fixtures.source)try{await call('/dst/database/delete',{tid:fixtures.source,dryRun:true});await call('/dst/database/delete',{tid:fixtures.source});}catch(e){errors.push('source: '+e.message);}
 if(fixtures.physical&&!errors.length)try{await physical('drop');}catch(e){errors.push('physical fixture: '+e.message);}
 await writeFile(join(folder,'cleanup.json'),JSON.stringify({errors}));if(errors.length)throw Error('Fixture cleanup needs attention: '+errors.join('; '));
 if(!errors.length)await rm(fixtureFile,{force:true});
}
const afterCatalog=await call('/dwm/metadata-governance/catalog',{}),after=counts(afterCatalog);
// A shared validation environment can receive legitimate new registrations while
// this test runs. Check identities rather than deleting additions to restore a
// global count. Every original object must remain and every own fixture must go.
for(const kind of ['sources','tables','columns','relations']){
 const afterIds=new Set(afterCatalog[kind].map(r=>String(r.tid)));
 assert(beforeCatalog[kind].every(r=>afterIds.has(String(r.tid))),kind+': an original record disappeared during verification');
}
assert(!afterCatalog.sources.some(s=>String(s.tid)===String(fixtures.source)));
const ownTables=[fixtures.child?.table,fixtures.parent?.table,fixtures.sink?.table].filter(Boolean);
const ownColumns=[fixtures.child,fixtures.parent,fixtures.sink].filter(Boolean).flatMap(t=>Object.values(t.columns));
assert(!afterCatalog.tables.some(t=>ownTables.includes(String(t.tid))));
assert(!afterCatalog.columns.some(c=>ownColumns.includes(String(c.tid))));
assert(!afterCatalog.relations.some(r=>fixtures.relations.includes(String(r.tid))));
pass('fixtures-cleaned-and-original-business-records-preserved');
const countsPreserved=JSON.stringify(before)===JSON.stringify(after);
if(!countsPreserved)console.log('NOTE shared business additions preserved; global counts changed from '+JSON.stringify(before)+' to '+JSON.stringify(after));
await writeFile(join(folder,'results.json'),JSON.stringify({tenant:tenant.name,database:process.env.WX_META_DATABASE,checks,countsPreserved,originalRecordsRetained:true,ownFixturesAbsent:true,beforeCounts:before,afterCounts:after},null,2));console.log(JSON.stringify({checks:checks.length,countsPreserved,originalRecordsRetained:true,ownFixturesAbsent:true,verificationRecords:folder}));
