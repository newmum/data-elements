import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../../', import.meta.url);
const source = await fs.readFile(new URL('lowcode/03.数据接入/2.3.1 数据汇聚(data-convergence)/接入申请(access-apply).vue', root), 'utf8');
const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
const ast = babel.Babel.transform(script, { ast:true, code:false, configFile:false, babelrc:false,
  parserOpts:{sourceType:'module',plugins:['typescript']} }).ast;
const names = ['defaultRegionCodes','activePartition','activePartitionChoice','activeListPartitionText','partitionPayload',
  'partitionProbeIdentity','probePartitions','togglePartition'];
const selected = names.map(name => {
  const node = ast.program.body.find(n => n.type === 'VariableDeclaration' && n.declarations.some(d => d.id?.name === name));
  assert.ok(node,name);return script.slice(node.start,node.end);
});
const columns = Array.from({length:100},(_,i)=>({columnName:i?'f'+i:'DSBM',dataType:'varchar',columnType:'varchar(6)'}));
const plans = {value:{target:{enabled:true,field:'DSBM',strategy:'MONTH'}}};
let probeResult, readRequests=0, regenerationRequests=0;
const context = vm.createContext({
  computed: definition => ({get value(){return typeof definition==='function'?definition():definition.get();},set value(value){definition.set(value);}}),
  activeDdlKey:{value:'target'},partitionPlans:plans,tableRef:{value:{getData:()=>columns}},templateTableItems:{value:columns},
  partitionInputError: (_key,message)=>{throw new Error(message);},
  props:{id:'source'},formData:{value:{sourceTableId:'source'}},partitionSupport:{value:{}},
  sourceConstraints:{value:[]},sourceDefinitionMessage:{value:''},partitionProbeMessage:{value:''},sourcePartitionDetected:{value:false},
  ddlWasEdited:{value:false},updatePartitionDdl:async()=>{regenerationRequests++;},
  $common:{post:async(_route,body)=>{readRequests++;assert.equal(body.partitionProbe,true);return {data:probeResult};}},
});
const executable = babel.Babel.transform(selected.join('\n'),{filename:'partitions.ts',presets:['typescript'],configFile:false,babelrc:false}).code;
vm.runInContext(executable+'\nglobalThis.actual={'+names.join(',')+'};',context);
const actual=context.actual;
const preset=['350100','350200','350300','350400','350500','350600','350700','350800','350900','351000'];
actual.activePartitionChoice.value='REGION';
assert.equal(actual.activePartitionChoice.value,'REGION');
assert.equal(actual.activeListPartitionText.value,preset.join(','));
assert.equal(actual.partitionPayload('target').strategy,'LIST');
assert.deepEqual(JSON.parse(JSON.stringify(actual.partitionPayload('target').partitions.map(p=>p.values[0]))),preset);
actual.activeListPartitionText.value='350100,350200';
actual.activePartitionChoice.value='LIST';
assert.equal(actual.activeListPartitionText.value,'','Manual enumeration must not inherit region defaults');
assert.throws(()=>actual.partitionPayload('target'),/请填写至少一个枚举值/);
actual.activeListPartitionText.value='0,1,2';
actual.activePartitionChoice.value='REGION';
assert.equal(actual.activeListPartitionText.value,'350100,350200');
actual.activePartitionChoice.value='LIST';
assert.equal(actual.activeListPartitionText.value,'0,1,2','Each type retains its own edited values');
plans.value.target={enabled:true,field:'DSBM',strategy:'LIST',displayStrategy:'REGION',sourceBoundaries:true,
  partitions:[{name:'p_group',values:['350100','350200']},{name:'p_default',default:true}]};
assert.equal(actual.activePartitionChoice.value,'REGION');
assert.equal(actual.activeListPartitionText.value,'350100,350200');
assert.equal(actual.partitionPayload('target').partitions.length,2,'Rendering inherited text must not split source groups or drop DEFAULT');
assert.equal(actual.partitionPayload('target').partitions[1].default,true);
plans.value.target.enabled=false;
assert.equal(actual.partitionPayload('target').enabled,false);
for(const count of [20,100]) {
  const sourcePlan={enabled:true,sourceBoundaries:true,strategy:'LIST',field:'DSBM',partitions:
    Array.from({length:count},(_,i)=>({name:'p'+i,values:['v'+i]}))};
  const targets=Array.from({length:20},(_,i)=>({key:'t'+i,dbId:'t'+i}));
  probeResult={source:{status:'detected',plan:sourcePlan},targetSupport:Object.fromEntries(targets.map(t=>[t.key,true]))};
  plans.value={};readRequests=0;await actual.probePartitions(targets);
  assert.equal(readRequests,1,'One probe covers all targets and source boundaries');
  assert.equal(context.sourcePartitionDetected.value,true,'Show the source-partition hint only on a positive detection');
  for(const target of targets) {
    assert.equal(plans.value[target.key].enabled,false,'Detected source partitions must never enable a new target automatically');
    assert.equal(plans.value[target.key].partitions.length,count,'Keep the detected boundaries available for explicit opt-in');
    assert.equal(actual.partitionPayload(target.key).enabled,false,'Unchecked preview and submit must receive an ordinary-table plan');
  }
  assert.equal(sourcePlan.enabled,true,'Do not mutate the source probe result');
  context.activeDdlKey.value='t0';regenerationRequests=0;
  await actual.togglePartition(true);
  assert.equal(actual.partitionPayload('t0').partitions.length,count);
  assert.equal(actual.partitionPayload('t0').enabled,true);
  await actual.probePartitions(targets.slice(0,2));
  assert.equal(plans.value.t0.enabled,true,'Refreshing target support must preserve the user choice');
  assert.equal(plans.value.t1.enabled,false);
  await actual.togglePartition(false);
  assert.equal(actual.partitionPayload('t0').enabled,false);
  assert.equal(regenerationRequests,2,'Only explicit checkbox changes regenerate the preview');
  console.log(`Source partitions=${count}, targets=20: one probe, all default off; manual opt-in/out verified.`);
}
console.log('Partition controls: region defaults, independent enum drafts, inherited groups/default and unchecked generation verified.');

for (const status of ['unknown','unsupported','not_partitioned']) {
  probeResult={source:{status},targetSupport:{target:true}};
  plans.value={};
  await actual.probePartitions([{key:'target',dbId:status}]);
  assert.equal(context.sourcePartitionDetected.value,false,'Do not claim the source is partitioned after an inconclusive probe');
  assert.equal(plans.value.target.enabled,false);
}
console.log('Detected-only partition hint and inconclusive-probe fallback verified.');
