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
function fixture(size, runtimeCode=executable) {
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
  vm.runInContext(runtimeCode,context);
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
  assert.equal(f.opened.length,1,'Every successful create opens the first task, including a multi-hop result');
  assert.deepEqual(plain(f.opened[0]),['task-0','source']);
  assert.deepEqual(plain(f.context.expandedRows.value),['source']);
  assert.ok(!f.notices.some(notice=>notice.kind==='error'));
  f.context.actual.openTaskSetting(f.tasks.at(-1),{tid:'source'});
  assert.equal(f.context.id.value,f.tasks.at(-1).sourceTableId);
  assert.equal(f.context.accessTaskId.value,f.tasks.at(-1).tid);
  assert.equal(f.calls.length,3,'Opening an existing task performs no create request');
  console.log(`Selected targets=${size}: one ensure request, one rule binding, one list refresh`);
}
const delayedRefresh=fixture(2);
let completeRefresh;
delayedRefresh.context.tableRef.value.refresh=()=>new Promise(resolve=>{completeRefresh=resolve;});
await delayedRefresh.context.actual.handleApplySave({targetTables:[{targetTableId:'target-0'},{targetTableId:'target-1'}]});
assert.deepEqual(plain(delayedRefresh.opened),[['task-0','source']],'A slow page refresh cannot delay opening the canvas');
completeRefresh();
const shuffled=fixture(2);
shuffled.tasks.reverse();
await shuffled.context.actual.handleApplySave({targetTables:[{targetTableId:'target-0'},{targetTableId:'target-1'}]});
assert.deepEqual(plain(shuffled.opened),[['task-0','source']],'Select the actual entry hop even if the response order changes');
const hiveSource=await fs.readFile(new URL('lowcode/03.数据接入/2.3.1 Hive元数据库(data-convergence-hive).vue',root),'utf8');
const hiveScript=hiveSource.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
const hiveAst=babel.Babel.transform(hiveScript,{ast:true,code:false,configFile:false,babelrc:false,
  parserOpts:{sourceType:'module',plugins:['typescript']}}).ast;
const hiveCode=names.map(name=>{
  const node=hiveAst.program.body.find(node=>node.type==='VariableDeclaration'&&node.declarations.some(d=>d.id.name===name));
  assert.ok(node,name);return hiveScript.slice(node.start,node.end);
}).join('\n')+'\nglobalThis.actual={'+names.join(',')+'};';
for(const size of [1,2]){
  const f=fixture(size,hiveCode);
  await f.context.actual.handleApplySave({targetTables:Array.from({length:size},(_,i)=>({targetTableId:`target-${i}`}))});
  assert.deepEqual(plain(f.opened),[['task-0','source']],'Distribution also opens the first canvas immediately');
  assert.equal(f.calls.filter(call=>call.route==='/ods/dataAggTaskEnsure').length,1);
}
for (const runtimeCode of [executable,hiveCode]) {
  const failed=fixture(2,runtimeCode);
  failed.context.$common.post=async(route,body,options)=>{
    failed.calls.push({route,body:plain(body)});
    if(route==='/ods/dataAggTaskEnsure') {
      assert.equal(options?._hiddenErrorMsg,true);
      throw new Error('字段映射存储容量不足');
    }
    throw new Error(`Unexpected route ${route}`);
  };
  failed.context.console={error:()=>{}};
  await failed.context.actual.handleApplySave({targetTables:[{targetTableId:'target-0'},{targetTableId:'target-1'}]});
  assert.equal(failed.opened.length,0);
  assert.equal(failed.calls.filter(call=>call.route==='refresh').length,1);
  assert.ok(failed.notices.some(notice=>notice.kind==='error'&&notice.text.includes('目标表已保留')&&notice.text.includes('无需重复物化或删表')));
  assert.ok(!failed.notices.some(notice=>notice.kind==='success'));
}
const f=fixture(2), row={targetTables:[{targetTableId:'target-0'},{targetTableId:'target-1'}],targetTableId:'target-0'};
assert.deepEqual(plain(f.context.actual.targetTablesOf(row,{targetTableId:'target-1',targetTables:[{targetTableId:'target-1'}]})).map(t=>t.targetTableId),['target-1']);
assert.equal(f.context.actual.targetTablesOf(row).length,2);
console.log('Task-specific target drawer and actual-source navigation passed');

const pageNames = ['fetchSources', 'collectDatasourceIds', 'buildConditions', 'handleExpandChange',
  'qualifiedTableName', 'taskSourceTableName', 'taskLinkTargets'];
const pageExecutable = pageNames.map(name => {
  const node = ast.program.body.find(node => node.type === 'VariableDeclaration' && node.declarations.some(d => d.id.name === name));
  assert.ok(node, name);
  return script.slice(node.start, node.end);
}).join('\n') + '\nglobalThis.pageHandlers={' + pageNames.join(',') + '};';
for (const count of [20, 100]) {
  const calls = [], rows = Array.from({length:count}, (_,i) => ({tid:`source-${i}`, taskCount:1,
    tableName:`source_${i}`,tasks:[{tid:`task-${i}`,sourceTableId:`source-${i}`,sourceTableName:`source_${i}`,
    sourceSchemaName:'public',targetTables:[{targetTableId:`target-${i}`,targetDbName:'汇聚库',targetTableName:`ods_${i}`}]}]}));
  const ref = value => ({value});
  const ctx = vm.createContext({console,taskFetchSequence:0,state:{accessStatus:'all'},treeData:ref([{children:[]}]),expandedRows:ref([]),
    getSourceTableId:row=>row.tid,$message:{error:assert.fail},$common:{post:async(route,body)=>{
      calls.push({route,body:plain(body)});return {list:rows,total:count};
    }}});
  vm.runInContext(pageExecutable,ctx);
  const page = await ctx.pageHandlers.fetchSources({pageNo:1,pageSize:count});
  assert.equal(page.list.length,count);
  for (const row of page.list) {
    ctx.pageHandlers.handleExpandChange(row,page.list);
    assert.equal(ctx.pageHandlers.taskSourceTableName(row.tasks[0],row),`public.${row.tableName}`);
    assert.equal(ctx.pageHandlers.taskLinkTargets(row.tasks[0]).length,1);
  }
  assert.equal(calls.length,1,'Page and all expanded task links use one request');
  assert.equal(calls[0].route,'/ods/dataAggPage');
  assert.equal(ctx.pageHandlers.qualifiedTableName('public.orders','public'),'public.orders');
  assert.equal(ctx.pageHandlers.taskSourceTableName({sourceTableId:'other'},rows[0]),'来源表名称缺失',
    'Never label an unknown chained source with the root source');
  assert.equal(ctx.pageHandlers.taskLinkTargets({targetTables:[{targetTableName:'one'},{targetTableName:'two'}]}).length,2);
  console.log(`Page rows=${count}: one list request; expanding/rendering every task uses zero additional requests`);
}
