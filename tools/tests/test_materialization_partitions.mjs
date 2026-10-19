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
const names = ['defaultRegionCodes','activePartition','activePartitionChoice','activeListPartitionText','partitionPayload'];
const selected = names.map(name => {
  const node = ast.program.body.find(n => n.type === 'VariableDeclaration' && n.declarations.some(d => d.id?.name === name));
  assert.ok(node,name);return script.slice(node.start,node.end);
});
const columns = Array.from({length:100},(_,i)=>({columnName:i?'f'+i:'DSBM',dataType:'varchar',columnType:'varchar(6)'}));
const plans = {value:{target:{enabled:true,field:'DSBM',strategy:'MONTH'}}};
const context = vm.createContext({
  computed: definition => ({get value(){return typeof definition==='function'?definition():definition.get();},set value(value){definition.set(value);}}),
  activeDdlKey:{value:'target'},partitionPlans:plans,tableRef:{value:{getData:()=>columns}},templateTableItems:{value:columns},
  partitionInputError: (_key,message)=>{throw new Error(message);},
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
console.log('Partition controls: region defaults, independent enum drafts, inherited groups/default and unchecked generation verified.');
