import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../../', import.meta.url);
const source = await fs.readFile(new URL('lowcode/03.数据接入/2.3.1 数据汇聚(data-convergence).vue', root), 'utf8');
const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
const ast = babel.Babel.transform(script, {ast:true,code:false,configFile:false,babelrc:false,
  parserOpts:{sourceType:'module',plugins:['typescript']}}).ast;
const names = ['ensureAccessTask','handleApplySave','targetTablesOf','openTaskSetting'];
const executable = names.map(name => {
  const node=ast.program.body.find(node=>node.type==='VariableDeclaration' && node.declarations.some(d=>d.id.name===name));
  assert.ok(node,name); return script.slice(node.start,node.end);
}).join('\n')+'\nglobalThis.actual={'+names.join(',')+'};';
const plain=value=>JSON.parse(JSON.stringify(value));
function fixture(size) {
  const calls=[], opened=[], notices=[];
  const tasks=Array.from({length:size},(_,i)=>({tid:`task-${i}`, pipelineId:`pipeline-${i}`,
    sourceTableId:i===0?'source':`target-${i-1}`,targetTableId:`target-${i}`}));
  const ref=value=>({value});
  const context=vm.createContext({console,getSourceTableId:row=>row.sourceTableId||row.tid,
    applyVisible:ref(true),pendingTaskRow:ref({tid:'source',datasourceId:'source-db'}),expandedRows:ref([]),
    id:ref(''),accessTaskId:ref(''),scheduleVisible:ref(false),tableRef:ref({refresh:async()=>calls.push({route:'refresh'})}),
    openNifiDesigner:(...args)=>opened.push(args),ElMessageBox:{confirm:async()=>{}},
    $message:Object.fromEntries(['success','error','warning'].map(kind=>[kind,text=>notices.push({kind,text})])),
    $common:{post:async(route,body)=>{calls.push({route,body:plain(body)});
      if(route==='/ods/dataAggTaskEnsure')return {tasks,task:tasks[0],createdCount:size,repairRequired:false};return {};}}});
  vm.runInContext(executable,context);
  return {context,calls,opened,notices,tasks};
}
for(const size of [1,2,20,100]) {
  const f=fixture(size);
  await f.context.actual.handleApplySave({targetTables:Array.from({length:size},(_,i)=>({targetTableId:`target-${i}`}))});
  const ensure=f.calls.filter(call=>call.route==='/ods/dataAggTaskEnsure');
  assert.equal(ensure.length,1);
  assert.deepEqual(ensure[0].body.targetTableIds,Array.from({length:size},(_,i)=>`target-${i}`));
  assert.equal(f.calls.filter(call=>call.route==='/ods/api-pull/bind-task').length,1);
  assert.equal(f.calls.filter(call=>call.route==='refresh').length,1);
  assert.equal(f.opened.length,size===1?1:0);
  assert.deepEqual(plain(f.context.expandedRows.value),['source']);
  assert.ok(!f.notices.some(notice=>notice.kind==='error'));
  f.context.actual.openTaskSetting(f.tasks.at(-1),{tid:'source'});
  assert.equal(f.context.id.value,f.tasks.at(-1).sourceTableId);
  assert.equal(f.context.accessTaskId.value,f.tasks.at(-1).tid);
  assert.equal(f.calls.length,3,'Opening an existing task performs no create request');
  console.log(`Selected targets=${size}: one ensure request, one rule binding, one list refresh`);
}
const f=fixture(2), row={targetTables:[{targetTableId:'target-0'},{targetTableId:'target-1'}],targetTableId:'target-0'};
assert.deepEqual(plain(f.context.actual.targetTablesOf(row,{targetTableId:'target-1',targetTables:[{targetTableId:'target-1'}]})).map(t=>t.targetTableId),['target-1']);
assert.equal(f.context.actual.targetTablesOf(row).length,2);
console.log('Task-specific target drawer and actual-source navigation passed');
