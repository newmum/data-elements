import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = new URL('../../', import.meta.url);
const file = 'lowcode/02.数据盘点/02.登记流程组件/详情页面(detail-page)/1. 资产【app%7Cdb%7Ctable%7Ccategory】详情(base-detail).vue';
const source = await fs.readFile(path.join(fileURLToPath(root), file), 'utf8');
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
const script = source.match(/<script[^>]*>([\s\S]*?)<\/script>/)[1].replace(/^import .*?;\r?\n/gm, '');
const code = babel.Babel.transform(script, { filename: 'detail.ts', presets: ['typescript'] }).code;
const deferred = () => { let resolve, reject; const promise = new Promise((a,b) => { resolve=a; reject=b; }); return { promise, resolve, reject }; };
function setup(type = 'table') {
  const calls=[], forms=[], records=[], values=[], unmount=[];
  const props={tid:'record-a'}, store={state:{detailClass:type},setData:data=>records.push(data)};
  const context=vm.createContext({
    Error,
    defineProps:()=>props, ref:value=>({value}), reactive:value=>value,
    computed:get=>({get value(){return get();}}), nextTick:async()=>{}, watch:()=>{}, onBeforeUnmount:fn=>unmount.push(fn),
    useDetailStore:()=>store, FormUtils:{transFormText:rules=>rules},
    $form:{get:name=>{ const d=deferred(); forms.push({name,...d}); return d.promise; }},
    $common:{post:(url,body)=>{ const d=deferred(); calls.push({url,body,...d}); return d.promise; }},
  });
  vm.runInContext(code+'\nthis.test={loadDetail,detailLoading,loadError,state,formRef,formRules};',context);
  context.test.formRef.value={setValue:data=>values.push(data)};
  return {...context.test,props,store,calls,forms,records,values,unmount};
}
for(const count of [20,100]) {
  const t=setup(); const done=t.loadDetail();
  assert.equal(t.detailLoading.value,true);
  assert.equal(t.calls.length,1); assert.equal(t.forms.length,1);
  assert.equal(t.calls[0].url,'/ods/dataAggPage');
  assert.equal('tenantId' in t.calls[0].body,false);
  t.calls[0].resolve({detail:{tid:'record-a',name:'ready'}}); await Promise.resolve();
  assert.equal(t.detailLoading.value,true,'data alone cannot reveal an unconfigured form');
  t.forms[0].resolve(Array.from({length:count},(_,i)=>({field:`f${i}`}))); await done;
  assert.equal(t.detailLoading.value,false); assert.equal(t.loadError.value,'');
  assert.equal(t.values[0].name,'ready'); assert.equal(t.records.length,1);
  assert.equal(t.calls.length+t.forms.length,2,`${count} fields still use one definition and one detail request`);
  console.log(`PASS ${count} fields: fixed two reads, skeleton until definition/data binding, existing tenant-scoped endpoint`);
}
{
  const t=setup(); const done=t.loadDetail(); t.forms[0].resolve([{field:'name'}]); await Promise.resolve();
  assert.equal(t.detailLoading.value,true,'configured form must wait for data');
  t.calls[0].reject(new Error('数据服务暂不可用')); await done;
  assert.equal(t.detailLoading.value,false); assert.equal(t.loadError.value,'数据服务暂不可用'); assert.equal(t.records.length,0);
  const retry=t.loadDetail(); assert.equal(t.loadError.value,''); assert.equal(t.detailLoading.value,true);
  t.forms[1].resolve([{field:'name'}]); t.calls[1].resolve({detail:{name:'retry success'}}); await retry;
  assert.equal(t.values[0].name,'retry success'); assert.equal(t.detailLoading.value,false);
  console.log('PASS request failure exits loading and supports successful retry');
}
{
  const t=setup(); const old=t.loadDetail(); t.props.tid='record-b'; const latest=t.loadDetail();
  t.forms[1].resolve([{field:'name'}]); t.calls[1].resolve({detail:{name:'latest'}}); await latest;
  t.forms[0].resolve([{field:'name'}]); t.calls[0].resolve({detail:{name:'stale'}}); await old;
  assert.equal(t.values.length,1); assert.equal(t.values[0].name,'latest'); assert.equal(t.records.length,1);
  console.log('PASS stale responses cannot overwrite a newly selected record or detail store');
}
{
  const t=setup(); const done=t.loadDetail(); t.unmount[0](); t.forms[0].resolve([{field:'name'}]); t.calls[0].resolve({detail:{name:'late'}}); await done;
  assert.equal(t.records.length,0); assert.equal(t.values.length,0);
  console.log('PASS unmounted detail ignores pending responses');
}
for(const type of ['app','db','catalog','fileCatalog']) {
  const t=setup(type); const done=t.loadDetail(); t.forms[0].resolve([{field:'name'}]); t.calls[0].resolve({name:type}); await done;
  assert.equal(t.detailLoading.value,false); assert.equal(t.values[0].name,type);
}
console.log('PASS all five configured asset detail types share the loading lifecycle');
{
  const t=setup(); const done=t.loadDetail(); t.forms[0].resolve([]); t.calls[0].resolve({detail:{}}); await done;
  assert.equal(t.detailLoading.value,false); assert.match(t.loadError.value,/表单配置为空/); assert.equal(t.records.length,0);
  console.log('PASS empty form definitions show a recoverable configuration error');
}
