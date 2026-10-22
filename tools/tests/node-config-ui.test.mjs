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
  const create = new Function('ref', 'reactive', 'computed', 'nextTick', 'onMounted', 'resolveComponent', '$common', 'ElMessage', 'ElMessageBox', `${script}\nreturn {loadNodes,syncNodes,pushNodes,networkOptions,openPushSync,closePushSync,previewPushSync,commitPushSync,pushSourceId,pushSyncPreview,pushSyncError,pushSources,pushPreviewLoading,pushSyncVisible,saveNode,formRef,dialogMode};`);
  const ui = create(ref, reactive, computed, nextTick, () => {}, name => name, {post}, {success: value => successes.push(value),error:()=>{}}, {});
  ui.networkOptions.value = [{value:'GAW',label:'公安网'}];
  return {ui,successes};
}
for (const count of [20,100]) test(`${count} nodes load in one request with separate extraction and push state`, async () => {
  const calls=[];const rows=Array.from({length:count},(_,i)=>({tid:`p${i}`,nodeName:`Node ${i}`,networkCode:'GAW',enabled:1}));
  const {ui}=fixture(async (url,body)=>{calls.push({url,body});return {list:rows};});
  await ui.loadNodes('push');assert.equal(calls.length,1);assert.equal(calls[0].body.nodeType,'push');assert.equal(ui.pushNodes.value.length,count);assert.equal(ui.syncNodes.value.length,0);
});
test('source chooser batches once, excludes unfinished sources, previews before confirmed synchronization', async () => {
  const calls=[];const {ui,successes}=fixture(async (url,body)=>{
    calls.push({url,body});
    if(url.includes('departmentDataSources'))return {list:[{tid:'source',dbName:'已登记',accessMode:'receive',assetStatus:2},{tid:'draft',accessMode:'receive',assetStatus:0},{tid:'upload',accessMode:'upload',assetStatus:2}]};
    if(url.includes('push/sync'))return {valid:true,dryRun:body.dryRun===true,synced:!body.dryRun,nodeId:body.nodeId,datasourceId:body.datasourceId,tableCount:20,columnCount:100};
    return {list:[]};
  });
  await ui.openPushSync({id:'node'});assert.deepEqual(ui.pushSources.value,[{id:'source',name:'已登记'}]);assert.equal(calls.length,1);
  ui.pushSourceId.value='source';await ui.previewPushSync();assert.equal(calls[1].body.dryRun,true);assert.equal(successes.length,0);
  await ui.commitPushSync();assert.deepEqual(calls[2].body,{nodeId:'node',datasourceId:'source'});assert.equal(successes.length,1);assert.equal(ui.pushSyncVisible.value,false);
});
test('stale preview cannot overwrite new selection and close clears loading state', async () => {
  const pending=[];const {ui}=fixture(async (url,body)=>url.includes('departmentDataSources')?{list:[]}:new Promise(resolve=>pending.push({body,resolve})));
  await ui.openPushSync({id:'node'});ui.pushSourceId.value='old';const old=ui.previewPushSync();ui.pushSourceId.value='new';const latest=ui.previewPushSync();
  pending[1].resolve({valid:true,dryRun:true,nodeId:'node',datasourceId:'new'});await latest;
  pending[0].resolve({valid:true,dryRun:true,nodeId:'node',datasourceId:'old'});await old;assert.equal(ui.pushSyncPreview.value.datasourceId,'new');
  ui.closePushSync();assert.equal(ui.pushSyncPreview.value,null);assert.equal(ui.pushPreviewLoading.value,false);assert.deepEqual(ui.pushSources.value,[]);
});
test('upstream failure or mismatched identity never yields success feedback', async () => {
  const {ui,successes}=fixture(async (url,body)=>url.includes('departmentDataSources')?{list:[]}:{valid:true,dryRun:!!body.dryRun,synced:true,nodeId:'wrong-node',datasourceId:'source'});
  await ui.openPushSync({id:'node'});ui.pushSourceId.value='source';await ui.previewPushSync();assert.equal(ui.pushSyncPreview.value,null);assert.ok(ui.pushSyncError.value);
  ui.pushSyncPreview.value={valid:true};await ui.commitPushSync();assert.equal(successes.length,0);assert.equal(ui.pushSyncVisible.value,true);
});
test('double click during asynchronous form validation submits only once', async () => {
  const calls=[];let resolveValidation;const {ui}=fixture(async (url,body)=>{calls.push({url,body});return {list:[]};});
  ui.dialogMode.value='push';ui.formRef.value={validate:()=>new Promise(resolve=>{resolveValidation=resolve;})};
  const first=ui.saveNode();const second=ui.saveNode();resolveValidation(true);await Promise.all([first,second]);
  assert.equal(calls.filter(call=>call.url.endsWith('/saveOrUpdate')).length,1);assert.equal(calls[0].body.nodeType,'push');
});
