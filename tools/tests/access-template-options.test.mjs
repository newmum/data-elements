import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';
import test from 'node:test';

const root=new URL('../../',import.meta.url);
const source=await fs.readFile(new URL('lowcode/03.数据接入/2.3.1 数据汇聚(data-convergence)/接入申请(access-apply).vue',root),'utf8');
const babel={};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js',root),'utf8'),babel);
const block=source.slice(source.indexOf('type AccessTemplateOption ='),source.indexOf('const openTemplateManagement ='));
const code=babel.Babel.transform(block,{filename:'access-options.ts',plugins:['transform-typescript']}).code;
const ref=value=>({value});
function setup(count){
  const calls=[],timers=new Map();let sequence=0;
  const options=Array.from({length:count},(_,i)=>({tid:'validation-'+i,name:'模板'+i,version:1,
    selectionReady:true,available:true,spec:{targets:['MYSQL'],targetDatasourceIds:['db-'+i]},
    targets:[{tid:'db-'+i,label:'库'+i,dbType:'mysql'}]}));
  const context={ref,computed:fn=>({get value(){return fn();}}),open:ref(true),editable:ref(true),
    formData:ref({}),completedMaterializations:new Set(),materializationResults:ref([]),
    datasourceMap:ref({}),datasourcePathMap:ref({}),datasourceLeafIds:ref([]),
    jsonFormRef:ref({setValue(){},updateFieldOptions(){}}),syncTargetDatasourceSelection(){},
    setTimeout(fn){const id=++sequence;timers.set(id,fn);return id;},clearTimeout(id){timers.delete(id);},
    $common:{async post(route,body){calls.push({route,body});return {data:{list:options,total:count,
      defaultVersionId:options[0].tid,defaultTemplate:options[0]}};}}};
  vm.runInNewContext(code+'\nglobalThis.api={loadAccessTemplates,chooseAccessTemplate,openAccessTemplates,searchAccessTemplates,accessTemplateId,accessTemplatesError};',context);
  return {context,api:context.api,calls,options,timers};
}

test('20/100 hydrated options and repeated dropdown opening use one candidate request without per-row details',async()=>{
  for(const count of [20,100]){
    const fixture=setup(count);await fixture.api.loadAccessTemplates();
    for(const option of fixture.options)await fixture.api.chooseAccessTemplate(option.tid);
    fixture.api.openAccessTemplates(true);fixture.api.searchAccessTemplates('');
    assert.equal(fixture.calls.length,1);assert.equal(fixture.timers.size,0);
    assert.equal(fixture.calls[0].route,'/nifi/canvas/templates');assert.equal(fixture.calls[0].body.includeTargets,true);
    assert.equal(fixture.api.accessTemplateId.value,'validation-'+(count-1));
    assert.equal(fixture.context.formData.value.accessTemplateSelection,'validation-'+(count-1));
    assert.equal(Object.keys(fixture.context.datasourceMap.value).length,count);
  }
});

test('parallel initialization response is reused and an unavailable binding cannot populate target metadata',async()=>{
  const fixture=setup(20);
  await fixture.api.loadAccessTemplates(1,{status:'fulfilled',value:{data:{list:fixture.options,total:20,defaultVersionId:'validation-0'}}});
  assert.equal(fixture.calls.length,0);
  fixture.options[1].available=false;fixture.options[1].targets=[];
  await fixture.api.chooseAccessTemplate('validation-1');
  assert.match(fixture.api.accessTemplatesError.value,/目标库不可用或超出当前权限/);
  assert.equal(fixture.calls.length,0);assert.equal(fixture.context.datasourceMap.value['db-1'],undefined);
});

test('keyword input coalesces intermediate keystrokes into one bounded page request',async()=>{
  const fixture=setup(20);await fixture.api.loadAccessTemplates();
  fixture.api.searchAccessTemplates('a');fixture.api.searchAccessTemplates('ab');fixture.api.searchAccessTemplates('abc');
  assert.equal(fixture.timers.size,1);
  const callback=[...fixture.timers.values()][0];callback();await new Promise(setImmediate);
  assert.equal(fixture.calls.length,2);assert.equal(fixture.calls[1].body.keyword,'abc');
  assert.equal(fixture.calls[1].body.pageSize,20);
});
