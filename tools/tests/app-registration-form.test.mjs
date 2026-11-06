import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../../',import.meta.url));
const require=createRequire(path.join(root,'data-elements-chengtian/package.json'));
const ts=require('typescript');
const source=fs.readFileSync(path.join(root,'lowcode/02.数据盘点/02.登记流程组件/登记组件(register-modal)/1. 登记系统(register-app).vue'),'utf8');
const script=source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1].replace(/^import .*;\s*$/gm,'');
const js=ts.transpileModule(script,{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText;
const settle=()=>new Promise(resolve=>setImmediate(resolve));
function fixture(loadDefinition,initial={}){
  const calls=[],values=[];let validations=0;
  const form={api:{},setValue:v=>values.push(v),validate:async()=>{validations++},getSaveData:()=>({appName:'validation-app',orgId:'validation-org'})};
  let first=true;
  const store={data:{app:initial},state:{loadStatus:{}}};
  const get=(o,k,f)=>o[k]??f,set=(o,k,v)=>o[k]=v;
  const common={post:async(url,body)=>{calls.push({url,body});return url.endsWith('/detail')?{tid:initial.tid,appName:'validation-existing'}:{tid:'validation-id'}}};
  const api=new Function('ref','computed','nextTick','get','set','useRegisterStore','$form','$common','$user','defineExpose',js+'\nreturn {loadForm,save,finish,formRules,formLoading,formLoadError,showAppSync};')(
    v=>{if(first){first=false;return {value:form}}return {value:v}},fn=>({get value(){return typeof fn==='function'?fn():fn.get()},set value(v){fn.set(v)}}),async()=>{},get,set,()=>store,{loadDefinition},common,{orgId:'validation-org'},()=>{});
  return {api,calls,values,validations:()=>validations};
}
test('missing definition surfaces exact error and prevents writes',async()=>{
  const f=fixture(async()=>{throw new Error('未找到【登记系统】配置')});await settle();
  assert.equal(f.api.formLoading.value,false);assert.equal(f.api.formLoadError.value,'未找到【登记系统】配置');
  await assert.rejects(f.api.finish(),/未找到/);assert.equal(f.calls.length,0);
});
test('empty definition cannot save a blank registration',async()=>{
  const f=fixture(async()=>({rules:[]}));await settle();await assert.rejects(f.api.save(),/配置为空/);assert.equal(f.calls.length,0);
});
test('retry obtains a fresh definition and populates current organization',async()=>{
  let reads=0;const f=fixture(async()=>{if(++reads===1)throw new Error('暂不可用');return {rules:[{type:'input',field:'appName'}]}});
  await settle();await f.api.loadForm();assert.equal(reads,2);assert.equal(f.api.formLoadError.value,'');assert.deepEqual(f.values,[{orgId:'validation-org'}]);assert.equal(f.api.showAppSync.value,false);
});
test('edit loads only selected application and restores its values',async()=>{
  const f=fixture(async()=>({rules:[{type:'input',field:'appName'}]}),{tid:'validation-selected'});await settle();
  assert.deepEqual(f.calls,[{url:'/dst/application/detail',body:{tid:'validation-selected'}}]);assert.equal(f.values[0].appName,'validation-existing');
});
test('completion validates and persists once before reporting success',async()=>{
  const f=fixture(async()=>({rules:[{type:'input',field:'appName'}]}));await settle();
  const out=await f.api.finish();assert.equal(f.validations(),1);assert.equal(f.calls.length,1);assert.equal(f.calls[0].url,'/dst/application/save');assert.equal(out[0].tid,'validation-id');
});
test('optional sync slot uses definition props without outer slot-scope access',async()=>{
  const f=fixture(async()=>({rules:[{type:'div',children:[{type:'input',field:'appName',props:{showSync:true}}]}]}));await settle();
  assert.equal(f.api.showAppSync.value,true);assert.match(source,/v-if="showAppSync" #field-appName="scope"/);assert.doesNotMatch(source,/v-if="scope\?/);
});

