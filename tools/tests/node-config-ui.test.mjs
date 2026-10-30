import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createRequire } from 'node:module';
import test from 'node:test';
const require = createRequire(new URL('../../data-elements-chengtian/package.json', import.meta.url));
const { ref, reactive, computed, nextTick } = require('vue');
const source = fs.readFileSync(new URL('../../lowcode/09.系统管理/02.用户与权限/NiFi 节点配置(nifi-node-config).vue', import.meta.url), 'utf8');
const script = source.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*;\r?\n/gm, '');
function fixture(post) {
  const successes = [];
  const create = new Function('ref', 'reactive', 'computed', 'nextTick', 'onMounted', 'resolveComponent', '$common', 'ElMessage', 'ElMessageBox', `${script}\nreturn {loadNodes,syncNodes,pushNodes,networkOptions,openPushSync,closePushSync,previewPushSync,commitPushSync,pushSyncNode,pushSyncPage,pushSyncRows,pushSyncPageRows,pushCanSync,pushSyncStage,pushSyncPreview,pushSyncError,pushPreviewLoading,pushSyncVisible,saveNode,formRef,dialogMode};`);
  const ui = create(ref, reactive, computed, nextTick, () => {}, name => name, {post}, {success: value => successes.push(value),error:()=>{}}, {});
  ui.networkOptions.value = [{value:'GAW',label:'公安网'}];
  return {ui,successes};
}
for (const count of [20,100]) test(`${count} nodes load in one request with separate extraction and push state`, async () => {
  const calls=[];const rows=Array.from({length:count},(_,i)=>({tid:`p${i}`,nodeName:`Node ${i}`,networkCode:'GAW',enabled:1}));
  const {ui}=fixture(async (url,body)=>{calls.push({url,body});return {list:rows};});
  await ui.loadNodes('push');assert.equal(calls.length,1);assert.equal(calls[0].body.nodeType,'push');assert.equal(ui.pushNodes.value.length,count);assert.equal(ui.syncNodes.value.length,0);
});
const plan = (count=20,nodeId='node') => ({valid:true,dryRun:true,nodeId,planHash:'a'.repeat(64),datasourceCount:1,tableCount:count,columnCount:count,rows:Array.from({length:count},(_,i)=>({datasourceId:'source',datasourceName:'示例源',tableId:`t${i}`,tableName:`table_${i}`,syncStatus:'NEW'}))});
for(const count of [20,100])test(`${count} tables: one preview request, exact plan commit and fixed node refresh`,async()=>{
  const calls=[];const {ui,successes}=fixture(async(url,body)=>{calls.push({url,body});return url.endsWith('/page')?{list:[]}:body.preview?plan(count):{...plan(count),dryRun:false,synced:true};});
  await ui.openPushSync({id:'node',name:'节点',enabled:true});assert.equal(calls.length,1);assert.deepEqual(calls[0].body,{all:true,nodeId:'node',preview:true});
  assert.equal(ui.pushSyncRows.value.length,count);assert.equal(ui.pushSyncPageRows.value.length,20);
  ui.pushSyncPage.value=2;assert.equal(calls.length,1);assert.equal(ui.pushSyncPageRows.value.length,count===20?0:20);
  await Promise.all([ui.commitPushSync(),ui.commitPushSync()]);assert.equal(calls.length,3);assert.deepEqual(calls[1].body,{all:true,nodeId:'node',planHash:'a'.repeat(64)});
  assert.equal(successes.length,1);assert.equal(ui.pushSyncVisible.value,true);assert.equal(ui.pushSyncStage.value,'success');assert.ok(ui.pushSyncRows.value.every(row=>row.syncStatus==='SUCCESS'));
});
test('stale responses cannot replace the current node or reopen a closed preview',async()=>{
  const pending={};const {ui}=fixture((url,body)=>new Promise(resolve=>{pending[body.nodeId]=resolve;}));
  const first=ui.openPushSync({id:'old',enabled:true});const second=ui.openPushSync({id:'new',enabled:true});pending.new(plan(20,'new'));await second;pending.old(plan(20,'old'));await first;
  assert.equal(ui.pushSyncPreview.value.nodeId,'new');const third=ui.previewPushSync();ui.closePushSync();pending.new(plan(20,'new'));await third;
  assert.equal(ui.pushSyncVisible.value,false);assert.equal(ui.pushSyncPreview.value,null);assert.equal(ui.pushPreviewLoading.value,false);
});
test('mismatched receipt keeps the list open and requires a new preview',async()=>{
  const {ui,successes}=fixture(async(url,body)=>body.preview?plan():{...plan(),dryRun:false,synced:true,rows:plan().rows.map(row=>({...row,tableId:'unexpected'}))});
  await ui.openPushSync({id:'node',enabled:true});await ui.commitPushSync();assert.equal(successes.length,0);assert.equal(ui.pushSyncStage.value,'uncertain');assert.equal(ui.pushCanSync.value,false);assert.ok(ui.pushSyncError.value);
});
test('disabled node cannot start preview',async()=>{
  let calls=0;const {ui}=fixture(async()=>{calls++;});await ui.openPushSync({id:'node',enabled:false});assert.equal(calls,0);
});
test('double click during asynchronous form validation submits only once',async()=>{
  const calls=[];let resolveValidation;const {ui}=fixture(async(url,body)=>{calls.push({url,body});return {list:[]};});ui.dialogMode.value='push';ui.formRef.value={validate:()=>new Promise(resolve=>{resolveValidation=resolve;})};
  const first=ui.saveNode();const second=ui.saveNode();resolveValidation(true);await Promise.all([first,second]);assert.equal(calls.filter(call=>call.url.endsWith('/saveOrUpdate')).length,1);
});
