import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {createRequire} from 'node:module';
const {reactive}=createRequire(new URL('../package.json',import.meta.url))('vue');
const root=new URL('../../data-elements/db/migrations/resources/datasource-loading-20260926/',import.meta.url);
const vue=fs.readFileSync(new URL('pages/department-datasource.vue',root),'utf8');
const magic=fs.readFileSync(new URL('magic/departmentDataSources.ms',root),'utf8');
const loadCode=vue.slice(vue.indexOf('let loadGeneration = 0;'),vue.indexOf('const sourceTodoCount'));
const tableCode=vue.slice(vue.indexOf('const tablePageSize = 10;'),vue.indexOf('const appSystemKey'));
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {promise,resolve,reject};};
function harness(post){
 const state=reactive({list:[],tableLoads:{},expanded:[],loading:false});
 const box={value:{}};const treeData={value:[{}]};
 return new Function('state','$common','$user','orgTreeSource','treeStatistics','countMap','treeData','normalizeNumber','normalizeItem','compareDatasourceUpdated','rebuildTree','SOURCE_BATCH_SIZE','console',loadCode+tableCode+';return {state,loadData,loadTablePage,tableLoadState,tablePage,pagedTables,changeTablePage};')(
  state,{post},{orgRootId:'ROOT'},box,{value:{}},{value:{}},treeData,Number,x=>x,()=>0,()=>{},24,{error(){}}
 );
}
test('overview settles without waiting for organization tree',async()=>{
 const tree=deferred();const requests=[];
 const h=harness((url,body)=>{requests.push({url,body});return url.includes('getOrgTree')?tree.promise:Promise.resolve(url.includes('departmentDataSources')?{list:[{tid:'db1'}]}:{});});
 const loading=h.loadData();await new Promise(setImmediate);
 assert.equal(h.state.loading,false);assert.equal(h.state.treeLoading,true);assert.equal(h.state.list[0].tid,'db1');
 assert.equal(requests.find(x=>x.url.includes('departmentDataSources')).body.mode,'summary');
 assert.ok(!requests.some(x=>x.body.mode==='tables'));
 tree.resolve([]);await loading;
});
test('tree failure does not clear successful overview',async()=>{
 const h=harness(url=>url.includes('getOrgTree')?Promise.reject(Error('tree down')):Promise.resolve(url.includes('departmentDataSources')?{list:[{tid:'db1'}]}:{}));
 await h.loadData();assert.equal(h.state.list.length,1);assert.ok(h.state.treeError);assert.equal(h.state.loading,false);
});
test('failed refresh preserves prior list',async()=>{
 const h=harness(url=>url.includes('departmentDataSources')?Promise.reject(Error('source down')):Promise.resolve([]));h.state.list=[{tid:'old'}];
 await h.loadData();assert.equal(h.state.list[0].tid,'old');assert.ok(h.state.listError);
});
test('expanded table request is scoped, paged, and deduplicated',async()=>{
 const request=deferred();const calls=[];const h=harness((url,body)=>{calls.push(body);return request.promise;});
 const item={tid:'db1',registeredTableNum:25};const first=h.loadTablePage(item,1);await h.loadTablePage(item,1);
 assert.equal(calls.length,1);assert.deepEqual(calls[0],{mode:'tables',datasourceId:'db1',pageNum:1,pageSize:10});
 request.resolve({pageNum:1,total:25,list:[{tid:'table1'}]});await first;
 assert.equal(h.tablePage(item),1);assert.equal(h.pagedTables(item)[0].tid,'table1');
});
test('table failure is local and permits retry',async()=>{
 let fail=true;const h=harness(()=>fail?Promise.reject(Error('down')):Promise.resolve({pageNum:1,total:1,list:[{tid:'t1'}]}));
 const item={tid:'db1'};h.state.list=[item];await h.loadTablePage(item,1);
 assert.ok(h.tableLoadState(item).error);assert.equal(h.state.list.length,1);
 fail=false;await h.loadTablePage(item,1);assert.equal(h.tableLoadState(item).loaded,true);assert.equal(h.tableLoadState(item).error,'');
});
test('refresh invalidates in-flight table responses',async()=>{
 const pending=deferred();const h=harness((url,body)=>body?.mode==='tables'?pending.promise:Promise.resolve(body?.mode==='summary'?{list:[{tid:'db1'}]}:[]));
 const item={tid:'db1'};const table=h.loadTablePage(item,1);await h.loadData();pending.resolve({total:1,pageNum:1,list:[{tid:'stale'}]});await table;
 assert.deepEqual(h.state.tableLoads,{});
});
test('failed overview refresh also releases canceled table loading',async()=>{
 const pending=deferred();const h=harness((url,body)=>body?.mode==='tables'?pending.promise:body?.mode==='summary'?Promise.reject(Error('overview down')):Promise.resolve([]));
 const item={tid:'db1'};const table=h.loadTablePage(item,1);await h.loadData();
 assert.equal(h.tableLoadState(item).loading,false);assert.ok(h.tableLoadState(item).error);
 pending.resolve({total:1,pageNum:1,list:[{tid:'stale'}]});await table;
 assert.equal(h.pagedTables(item).length,0);
});
test('summary skips all legacy table scans and expansion applies tenant/menu boundaries',()=>{
 assert.match(magic,/if \(!summaryOnly\) \{[\s\S]*var tableGroups/);
 assert.match(magic,/size: 0,[\s\S]*aggs/);
 assert.match(magic,/eq\('tid', datasourceId\)\.eq\('tenant_id', tenantId\)/);
 assert.match(magic,/dataScope\.visibleDataSourcesFor\(scopeResource, sourceRows\)/);
 assert.match(magic,/limit \$\{offset\}, \$\{pageSize\}/);
 assert.match(magic,/pageSize > 100/);
 assert.match(magic,/asset_status = 2/);
 assert.doesNotMatch(magic,/body\.tenantId|param\.tenantId|principal\.tenantId/);
});
