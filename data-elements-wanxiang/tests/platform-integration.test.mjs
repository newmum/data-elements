import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const ds=require('../.test-build/src/services/datasources.js');
const registration=require('../.test-build/src/services/datasourceRegistrationApi.js');
const auth=require('../.test-build/src/services/api.js');
const shared=require('../.test-build/src/shared/platformApi.js');
const source={tid:'canonical-source',dbName:'真实数据源',dbType:'mysql',orgId:'org-1',appId:'app-1',connectionStatus:'success',collectedTableCount:136,collectedFieldCount:2398,username:'must-not-leak',password:'must-not-leak',pool_cfg:{password:'must-not-leak'},tenantId:'never-send'};
test('shared rows keep canonical IDs and actual counts without credentials',()=>{const row=ds.sourceRow(source);assert.equal(row.id,source.tid);assert.equal(row.entityCount,136);assert.equal(row.fieldCount,2398);assert.equal(row.status,'CONNECTED');assert.doesNotMatch(JSON.stringify(row),/must-not-leak|pool_cfg|tenantId/);});
test('disabled and failed sources are not presented as connected',()=>{assert.equal(ds.sourceRow({...source,isEnable:0}).status,'DISABLED');assert.equal(ds.sourceRow({...source,connectionStatus:'failed'}).status,'ERROR');assert.equal(ds.sourceRow({...source,connectionStatus:undefined}).status,'REGISTERED');});
test('connection-test driver errors are actionable Chinese messages without raw JDBC diagnostics',()=>{
 assert.match(ds.connectionFailureMessage('Communications link failure: Connection refused'),/数据库连接失败/);
 assert.match(ds.connectionFailureMessage('Access denied for user reader@host'),/账号或密码/);
 assert.match(ds.connectionFailureMessage('ORA-12514: listener does not currently know of service requested'),/服务名/);
 assert.match(ds.connectionFailureMessage('connect timed out'),/连接测试超时/);
 assert.match(ds.connectionFailureMessage(new shared.PlatformApiError('/dst/database/metadata/test-connection','请求超时，请稍后重试',undefined,true)),/后端尚未返回结果/);
 assert.match(ds.connectionFailureMessage('Failed to fetch'),/后端服务暂时无法访问/);
 assert.equal(ds.connectionFailureMessage('数据库连接失败，请检查地址、端口、防火墙、网络和数据库监听服务。'),'数据库连接失败，请检查地址、端口、防火墙、网络和数据库监听服务。');
 assert.doesNotMatch(ds.connectionFailureMessage('org.postgresql.util.PSQLException: fatal unexpected server exception'),/org\.|PSQLException|fatal/i);
});
test('form mapping uses the original platform property contract',()=>{const v=ds.draftFromForm({name:'新数据源',engine:'POSTGRESQL',appId:'app-1',orgId:'org-1',domain:'not-persisted',environment:'not-persisted',code:'not-persisted',connection:{host:'db.local',port:5432,database:'business',username:'reader',schema:'public'}});assert.equal(v.dbType,'postgres');assert.equal(v.jdbcURL,'jdbc:postgresql://db.local:5432/business');assert.equal(v.appId,'app-1');assert.equal(v.orgId,'org-1');for(const key of ['domain','environment','code','tenantId'])assert.equal(key in v,false);});
test('unsupported connectors fail closed rather than save a fake connection',()=>{assert.throws(()=>ds.draftFromForm({name:'Mongo',engine:'MONGODB',connection:{host:'localhost'}}),/尚未接入/);assert.throws(()=>ds.draftFromForm({name:'MySQL',engine:'MYSQL',connection:{host:'localhost'}}),/补全/);});
test('address edits regenerate ordinary JDBC URLs and reject stale custom URLs',()=>{
 const existing={name:'地址测试',engine:'MYSQL',backendType:'mysql',connection:{host:'old.local',port:3306,database:'business',jdbcURL:'jdbc:mysql://old.local:3306/business',jdbcType:'serviceName'}};
 const changed={...existing,connection:{...existing.connection,host:'new.local'}};
 assert.equal(ds.draftFromForm(changed,existing).jdbcURL,'jdbc:mysql://new.local:3306/business');
 const custom={...existing,connection:{...existing.connection,jdbcURL:existing.connection.jdbcURL+'?useSSL=true'}};
 assert.throws(()=>ds.draftFromForm({...custom,connection:{...custom.connection,host:'new.local'}},custom),/同步更新自定义 JDBC/);
 assert.equal(ds.draftFromForm({...custom,connection:{...custom.connection,host:'new.local',jdbcURL:''}},custom).jdbcURL,'jdbc:mysql://new.local:3306/business');
});
test('pending index synchronization does not replace confirmed edits with a stale row',()=>{
 const confirmed=ds.sourceRow({...source,assetDesc:'saved description',storageDomain:'政务外网'});
 assert.equal(ds.matchesSourceProjection(ds.sourceRow(source),confirmed),false);
 assert.equal(ds.matchesSourceProjection({...confirmed,fieldCount:1},confirmed),true);
 const page=fs.readFileSync('src/features/platform/SourcesPage.tsx','utf8');
 assert.match(page,/confirmedSources/);assert.match(page,/readSource\(saved.tid\)/);assert.match(page,/syncingIndexes.current.has/);
});
test('drawer field remounts retain initial values without returning stored passwords',()=>{
 assert.match(fs.readFileSync('src/features/platform/common.tsx','utf8'),/<Form form=\{form\} initialValues=\{initial\}/);
 const form=ds.formFromDraft(registration.draftFromDetail({...source,password:'must-not-return',host:'db.local',port:3306,database:'business'}),source.tid);
 assert.equal(form.name,'真实数据源');assert.equal(form.connection.host,'db.local');assert.equal(form.connection.password,'');
});
test('existing passwords are neither returned in editor fields nor erased on empty edit',()=>{const draft=registration.draftFromDetail({...source,password:'existing-secret',host:'db.local',port:3306,database:'business'});assert.equal(draft.password,undefined);const form=ds.formFromDraft(draft,source.tid);assert.equal(form.connection.password,'');const props=registration.sourceProperties({...ds.draftFromForm(form,form),password:''});assert.equal('password' in props,false);assert.equal('tenantId' in props,false);});
test('backend auth and mutation errors do not fall back to local seeds',async()=>{
 const previous={fetch:globalThis.fetch,location:globalThis.location,localStorage:globalThis.localStorage,sessionStorage:globalThis.sessionStorage};
 const calls=[];globalThis.location={href:'http://localhost:3000/',origin:'http://localhost:3000',replace:()=>{}};
 globalThis.localStorage={getItem:()=>null,setItem:()=>{},removeItem:()=>{}};globalThis.sessionStorage={getItem:()=>JSON.stringify('test-only-token')};
 globalThis.fetch=async(url,init)=>{calls.push({url,init});const path=String(url);let data;
 if(path.endsWith('/sym/user/me'))data={id:'real-user',realName:'真实账号',orgName:'真实部门',roles:['admin']};
 else if(path.endsWith('/sym/tenant/current'))data={tid:'tenant-from-session',name:'真实租户'};
 else if(path.endsWith('/dst/database/page'))data={list:[source],total:1};
 else if(path.endsWith('/saveOrUpdate'))data={tid:'saved-source',indexRefreshPending:true};
 else if(path.endsWith('/test-connection'))data={connected:false,error:'连接被拒绝'};
 else throw new Error('offline');
 return new Response(JSON.stringify({code:0,data}));};
 try{
  assert.equal(ds.canManageSources(),false);const session=await auth.loadSession();assert.equal(session.name,'真实账号');assert.equal(ds.canManageSources(),true);
  assert.equal((await ds.listSources())[0].id,'canonical-source');assert.equal(calls.filter(c=>c.url.includes('/dst/database/')).length,1);
  await ds.saveSource({name:'保存测试',engine:'MYSQL',orgId:'org-1',connection:{host:'db.local',port:3306,database:'business',username:'reader'}});
  const saved=JSON.parse(calls.find(c=>c.url.endsWith('/saveOrUpdate')).init.body);assert.equal(saved.deferIndexRefresh,true);assert.equal(saved.assetType,'db');assert.equal(saved.propList.preserveSourceOrg,true);assert.equal('tenantId' in saved,false);
  assert.equal(calls.some(c=>c.url.includes('/maintenance/refresh')),false);
  await assert.rejects(ds.testSource('canonical-source'),/数据库连接失败，请检查地址/);
  await assert.rejects(shared.platformApi('/unavailable'),/无法连接数据中台/);
 }finally{Object.assign(globalThis,previous);}
});
test('framework gate has no local identity login bypass',()=>{const gate=fs.readFileSync('src/app/AuthGate.tsx','utf8');assert.doesNotMatch(gate,/LocalIdentityPicker|工作身份|login\(/);assert.match(gate,/redirectToPlatformLogin/);const app=fs.readFileSync('src/app/App.tsx','utf8');assert.doesNotMatch(app,/workspace-selector|LocalIdentityPicker/);assert.match(app,/CenterSwitcher/);assert.match(app,/SidebarTools/);assert.match(app,/PlatformAccount/);});
test('platform account or tenant changes discard prior page state',()=>{const gate=fs.readFileSync('src/app/AuthGate.tsx','utf8');assert.match(gate,/value.principalId,value.tenant\?\.tid/);assert.match(gate,/<Fragment key=\{contextKey\(session\)\}/);assert.match(gate,/context!==sessionContext.current\)clearWorkspaceView/);});
test('cross-origin bridge checks exact origin, source and nonce; never uses URL tokens',()=>{const bridge=fs.readFileSync('src/shared/platformSession.ts','utf8');assert.match(bridge,/event.origin !== platform.origin/);assert.match(bridge,/event.source !== frame.contentWindow/);assert.match(bridge,/event.data.nonce !== nonce/);assert.match(bridge,/data-elements:logout-request/);assert.doesNotMatch(bridge,/token=.*encodeURIComponent|postMessage\([^;]+,\s*['"]\*['"]/);});
test('source actions use shared data without loading every saved table into the browser',()=>{const page=fs.readFileSync('src/features/platform/SourcesPage.tsx','utf8');const drawer=fs.readFileSync('src/features/platform/SourceCollectionDrawer.tsx','utf8');assert.doesNotMatch(page,/(?<![.\w])(?:api|all)\(|JobDrawer|refreshStudio|local-db/);assert.match(page,/deletePreview/);assert.match(page,/saveMessage="数据源已保存到数据中台"/);assert.match(page,/serverPagination=/);assert.match(drawer,/sourceTablesPage\(/);assert.match(drawer,/serverPagination=/);assert.doesNotMatch(drawer,/不支持勾选部分表/);});
test('source menu has a working table view but no enable or disable action, and KPI names match the counts',()=>{
 const page=fs.readFileSync('src/features/platform/SourcesPage.tsx','utf8');
 assert.match(page,/key:'tables',label:'查看数据表'/);
 assert.match(page,/if\(key==='tables'\)viewTables\(r\)/);
 assert.doesNotMatch(page,/key:'status',label:|label:'停用来源'|label:'恢复来源'/);
 assert.match(page,/label:'已采集表',value:q\.data\?entityCount:'—',note:`字段 \$\{fieldCount\.toLocaleString\(\)\} 个`/);
 const styles=fs.readFileSync('src/styles/studio-polish.css','utf8');
 assert.match(styles,/\.wx-source-kpis:not\(\.wx-quality-kpis\) \.wx-source-kpi small\{grid-row:2;grid-column:1\/-1;white-space:nowrap/);
});
