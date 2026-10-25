import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../../', import.meta.url);
const page = await fs.readFile(new URL('lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/05.数据源详情库表信息(db-table).vue', root), 'utf8');
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
const script = babel.Babel.transform(page.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1].replace(/^import[\s\S]*?;\r?\n/gm, ''), {filename:'snapshot.ts', presets:['typescript']}).code;

function fixture(count) {
  const calls = [], notices = [], timers = new Map(), watchers = [], registrations = [];
  let unmount, confirm = true, refreshes = 0, status = 'NOT_COLLECTED', nextTimer = 0;
  const source = {tid:'source', dbType:'mysql', accessMode:'extract'};
  const rows = Array.from({length:count}, (_, i) => ({tid:`table-${i}`, tableName:`table_${i}`, annotated:i % 2, fieldCount:2}));
  const fields = [{columnName:'id', columnType:'bigint', primaryKey:1, nullable:0}];
  const ref = value => ({value});
  const registrationOpen = ref(false);
  const context = vm.createContext({
    defineProps: () => ({dbId:'source'}), ref, reactive:v => v,
    computed: fn => ({get value(){return fn();}}),
    watch:(source, callback, options) => {watchers.push({source,callback});if(options?.immediate)callback(source());},
    onBeforeUnmount:fn => {unmount=fn;},
    useRegisterModal:() => ({open:registrationOpen, openRegisterModal: v => registrations.push(v)}),
    ElMessageBox:{confirm: async() => {if(!confirm)throw new Error('cancel');}},
    $message:Object.fromEntries(['success','info','error'].map(key => [key, message => notices.push({key,message})])),
    setTimeout:fn => {const id=++nextTimer;timers.set(id,fn);return id;}, clearTimeout:id => timers.delete(id),
    $common:{get:async(route,body)=>{calls.push({route,body});if(route.endsWith('/collection/status'))return {status,progressPercent:45};if(route.endsWith('/tables'))return {rows,total:count,snapshotTotal:count,businessStats:{业务表:count}};if(route.endsWith('/columns'))return fields;throw new Error(route);},
      post:async(route,body)=>{calls.push({route,body});if(route.endsWith('/detail'))return source;if(route.endsWith('/collection/start')){status='RUNNING';return {status,jobId:'job'};}if(route.endsWith('/sample-data'))return {columnNames:['id'],columnTypes:['bigint'],data:[{id:12}]};if(route.endsWith('/deleteAsset'))return {deleted:true};throw new Error(route);}},
  });
  vm.runInContext(`${script}\nthis.api={state,fetchData,tableRef,startCollection,openDbTableRegistration,openTablePreview,changePreviewMode,deleteTableSnapshot,initialize,stopPolling,readCollectionStatus};`, context);
  context.api.tableRef.value={refresh:()=>{refreshes++;}};
  return {context,calls,notices,timers,watchers,registrations,source,rows,
    ready:async()=>{await new Promise(resolve=>setImmediate(resolve));},
    cancel:()=>{confirm=false;}, confirm:()=>{confirm=true;}, unmount:()=>unmount(),
    succeed:()=>{status='SUCCEEDED';}, get refreshes(){return refreshes;}};
}

for(const count of [20,100]) {
  const f=fixture(count); await f.ready();
  const result=await f.context.api.fetchData({pageNo:1,pageSize:count});
  assert.equal(result.list.length,count); assert.equal(f.calls.length,3,'one source, one job status and one paged snapshot request');
  assert.equal(f.calls.filter(c=>c.route.endsWith('/detail')).length,1);
  assert.ok(f.calls.every(c=>!c.route.includes('reExplore')&&!c.route.includes('nifi')&&!c.route.endsWith('/collection/start')));
  const pageRead=f.calls.find(c=>c.route.endsWith('/tables'));
  assert.equal(pageRead.body.allSnapshots,true);assert.equal(pageRead.body.includeGovernance,false);
  await f.context.api.fetchData({pageNo:2,pageSize:20});assert.equal(f.calls.length,4,'paging reads only the current snapshot page');
  await f.context.api.openDbTableRegistration();
  assert.equal(f.registrations[0].registerData.currentStep,1);assert.equal(f.registrations[0].registerData.db[0].tid,'source');
  assert.equal(f.calls.length,4,'opening registration reuses source context');
  await f.context.api.openTablePreview(f.rows[0],'fields');assert.equal(f.calls.length,5,'only explicitly selected fields are read');
  assert.equal(f.context.api.state.fieldRows[0].primaryKey,true);
  await f.context.api.changePreviewMode('rows');assert.equal(f.calls.length,6);assert.equal(f.context.api.state.previewRows[0].id,12);
  await f.context.api.changePreviewMode('fields');await f.context.api.changePreviewMode('rows');assert.equal(f.calls.length,6,'tab changes retain the selected sample');
  f.cancel();await f.context.api.deleteTableSnapshot(f.rows[0]);assert.equal(f.calls.length,6,'cancelled deletion does not submit');
  f.confirm();await f.context.api.deleteTableSnapshot(f.rows[0]);assert.equal(f.calls.length,7);assert.equal(f.calls.at(-1).body.tableId,'table-0');
  await f.context.api.startCollection();
  assert.equal(f.calls.filter(c=>c.route.endsWith('/collection/start')).length,1);assert.equal(f.timers.size,1);
  await f.context.api.startCollection();assert.equal(f.calls.filter(c=>c.route.endsWith('/collection/start')).length,1,'running collection suppresses repeated clicks');
  const timer=[...f.timers.values()][0];f.timers.clear();f.succeed();await timer();await f.ready();
  assert.equal(f.context.api.state.collectionStatus,'SUCCEEDED');assert.equal(f.refreshes,2,'delete and completed collection each refresh once');
  f.unmount();assert.equal(f.timers.size,0);
  console.log(`PASS ${count} tables: 3 initial reads, paged snapshots, no automatic probe, step 2 entry, on-demand preview, cancel/delete and manual collection`);
}

{
  const f=fixture(20);await f.ready();
  f.context.$common.get=async()=>{throw new Error('快照服务暂不可用');};
  const failed=await f.context.api.fetchData();assert.equal(failed.total,0);assert.equal(f.context.api.state.loading,false);assert.match(f.context.api.state.listError,/快照服务/);
  let finish;
  f.context.$common.get=async()=>new Promise(resolve=>{finish=resolve;});
  const pending=f.context.api.fetchData();f.unmount();finish({rows:f.rows,total:20});await pending;
  assert.equal(f.context.api.state.rows.length,0,'unmounted result must not overwrite state');
  console.log('PASS failed snapshot retry state and stale request disposal');
}
