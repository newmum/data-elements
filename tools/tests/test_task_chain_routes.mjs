import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../../', import.meta.url);
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
const plain = value => JSON.parse(JSON.stringify(value));
for (const file of ['2.3.1 数据汇聚(data-convergence).vue', '2.3.1 Hive元数据库(data-convergence-hive).vue']) {
  const source = await fs.readFile(new URL(`lowcode/03.数据接入/${file}`, root), 'utf8');
  const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
  const ast = babel.Babel.transform(script, { ast: true, code: false, configFile: false, babelrc: false,
    parserOpts: { sourceType: 'module', plugins: ['typescript'] } }).ast;
  const names = ['qualifiedTableName', 'taskSourceTableName', 'taskLinkTargets', 'taskLinkStatus', 'taskLinkRoutes',
    'targetTablesOf', 'targetTaskSectionsOf', 'currentTargetSection', 'deleteCurrentFlow', 'deleteSingleTarget',
    'deleteAllTargets', 'targetCleanupPlan', 'targetCleanupSummary', 'handleRowTaskMenu'];
  const code = names.map(name => {
    const node = ast.program.body.find(node => node.type === 'VariableDeclaration' && node.declarations.some(d => d.id.name === name));
    assert.ok(node, name); return script.slice(node.start, node.end);
  }).join('\n') + '\nglobalThis.routes=taskLinkRoutes;globalThis.status=taskLinkStatus;globalThis.sections=targetTaskSectionsOf;globalThis.plan=targetCleanupPlan;globalThis.rowMenu=handleRowTaskMenu;globalThis.actions={deleteCurrentFlow,deleteSingleTarget,deleteAllTargets};';
  const menuCalls = [], recoveryCalls = [], resetCalls = [], notices = [], confirmations = [];
  const ctx = vm.createContext({ console:{...console,warn:()=>{}}, targetDrawerContext:{value:null},targetDrawerSections:{value:[]},
    deletingAllTargets:{value:false},deleteAllTargetCount:{value:0},deleteAllTargetProgress:{value:0},
    targetDrawerVisible:{value:true},accessTaskId:{value:''},tableRef:{value:{refresh:async()=>{}}},
    ElMessageBox:{confirm:async(text,title)=>confirmations.push({text,title})},$message:{success:text=>notices.push({type:'success',text}),warning:text=>notices.push({type:'warning',text}),error:assert.fail},
    handleTaskRecovery:(task,row,mode,target)=>recoveryCalls.push({task,row,mode,target}),
    handleTaskMenu:(action,task,row)=>menuCalls.push({action,task,row}),getSourceTableId: row => row.sourceTableId || row.tid,
    // Rendering must work without an API client; every required field comes from the page batch.
    $common: new Proxy({}, { get() { throw new Error('Route rendering issued a read request'); } }) });
  vm.runInContext(code, ctx);
  const target = (id, db) => ({ targetTableId: id, targetDbName: db, targetTableName: 'same_name', targetSchemaName: 'public' });
  const task = (id, source, db, targets) => ({ tid: id, sourceTableId: source, sourceDbName: db,
    sourceTableName: 'same_name', sourceSchemaName: 'public', targetTables: targets });
  const row = { tid: 'business', tasks: [task('first', 'business', '业务库', [target('ods', '汇聚库')]),
    task('second', 'ods', '汇聚库', [target('raw', '原始库')])] };
  ctx.targetDrawerContext.value = row;
  ctx.targetDrawerSections.value = ctx.sections(row);
  assert.deepEqual(plain(ctx.targetDrawerSections.value).map(section=>section.targets.map(target=>target.targetTableId)), [['ods'],['raw']]);
  const downstream = ctx.targetDrawerSections.value[1];
  await ctx.actions.deleteCurrentFlow(downstream);
  await ctx.actions.deleteSingleTarget(downstream, downstream.targets[0]);
  assert.equal(recoveryCalls.length,2);
  assert.ok(recoveryCalls.every(call=>call.task.tid==='second' && call.task.sourceTableId==='ods'), 'Each visible group keeps its own task and real source');
  await ctx.actions.deleteSingleTarget(downstream,{targetTableId:'ods'});
  await ctx.actions.deleteCurrentFlow({key:'task:foreign-id'});
  assert.equal(recoveryCalls.length,2,'Unknown task and another group target cannot invoke cleanup');
  ctx.rowMenu({action:'tables',taskId:'unauthorized-task'},row);
  assert.equal(menuCalls.length,0, 'Unknown IDs cannot invoke task actions');
  ctx.rowMenu({action:'tables',taskId:'second'},row);
  assert.equal(menuCalls[0].task.sourceTableId,'ods', 'Task actions use the actual downstream source');
  row.tasks[0].currentStatus = 'RUNNING';
  row.tasks[1].currentStatus = 'EXECUTION_ERROR';
  let routes = plain(ctx.routes(row));
  assert.deepEqual(routes[0].links, [{text:'运行中',tone:'success'},{text:'执行异常',tone:'danger'}]);
  assert.deepEqual(plain(ctx.status({currentStatus:'STOPPED',taskStatus:'1'})), {text:'未启动',tone:'idle'}, 'Stopped runtime state overrides legacy enabled flag');
  assert.deepEqual(plain(ctx.status({taskStatusText:'执行成功'})), {text:'未启动',tone:'idle'}, 'Historical success is not active running');
  assert.deepEqual(plain(ctx.status({currentStatus:'RUNNING',monitorStatus:'SUCCESS',taskStatusText:'执行成功'})), {text:'运行中',tone:'success'}, 'A successful batch does not stop the running task');
  assert.deepEqual(plain(ctx.status({taskStatus:1,monitorStatus:'SUCCESS',taskStatusText:'执行成功'})), {text:'运行中',tone:'success'}, 'Legacy start flag remains active after a successful batch');
  assert.deepEqual(plain(ctx.status({currentStatus:'STOPPED',monitorStatus:'SUCCESS',taskStatus:1})), {text:'未启动',tone:'idle'}, 'Current stop overrides both a successful batch and a stale start flag');
  assert.deepEqual(plain(ctx.status({currentStatus:'DEPLOY_FAILED',monitorStatus:'SUCCESS',taskStatus:1})), {text:'执行异常',tone:'danger'}, 'A deployment failure cannot be hidden by historical success');
  assert.deepEqual(plain(ctx.status({taskStatus:2})), {text:'执行异常',tone:'danger'});
  assert.deepEqual(plain(ctx.status({})), {text:'未启动',tone:'idle'});
  assert.equal(routes.length, 1);
  assert.deepEqual(routes[0].stages.map(stage => stage.map(item => item.id)), [['business'], ['ods'], ['raw']]);
  assert.deepEqual(routes[0].tasks.map(item => item.tid), ['first', 'second']);
  assert.ok(routes[0].stages.flat().every(item => item.table === 'public.same_name'));
  const multi = { tid: 'business', tasks: [task('multi', 'business', '业务库', [target('ods', '汇聚库'), target('raw', '原始库')])] };
  assert.equal(ctx.sections(multi)[0].targets.length,2,'Parallel targets are both visible in one task section');
  const detached = {...row,targetTables:[target('orphan','历史目标库')]};
  assert.equal(ctx.sections(detached).at(-1).key,'unassigned','An unlinked materialized target remains visible');
  const emptyTask = {...row,targetTableId:'ods',tasks:[{tid:'empty',sourceTableId:'ods'}]};
  assert.equal(ctx.sections(emptyTask)[0].targets.length,0,'A task with no targets cannot borrow the root target');
  routes = plain(ctx.routes(multi));
  assert.deepEqual(routes[0].stages.map(stage => stage.map(item => item.id)), [['business'], ['ods', 'raw']]);
  const unrelated = { tid: 'business', tasks: [...multi.tasks, task('unrelated', 'other', '其他库', [target('different', '其他目标')])] };
  routes = plain(ctx.routes(unrelated));
  assert.deepEqual(routes[0].tasks.map(item => item.tid), ['multi'], 'Equal table names do not imply linkage');
  const branch = { tid: 'business', tasks: [...multi.tasks, row.tasks[1]] };
  routes = plain(ctx.routes(branch));
  assert.equal(routes.length, 2);
  assert.ok(routes.some(item => item.stages.length === 3));
  assert.ok(routes.some(item => item.stages.length === 2));
  const cycle = { tid: 'business', tasks: [row.tasks[0], task('loop', 'ods', '汇聚库', [target('business', '业务库')])] };
  routes = plain(ctx.routes(cycle));
  assert.equal(routes.length, 1); assert.match(routes[0].warning, /循环/);
  for (const count of [20, 100]) {
    const rows = Array.from({ length: count }, () => row);
    assert.ok(rows.every(item => {
      const [route] = ctx.routes(item);
      const sections = ctx.sections(item);
      return sections.length === 2 && sections[1].targets[0].targetTableId === 'raw'
        && route.stages.length === 3 && route.links[0].text === '运行中' && route.links[1].text === '执行异常';
    }));
    const manyTasks={tid:'business',tasks:Array.from({length:count},(_,i)=>task(`batch-${i}`,'business','业务库',[target(`target-${i}`,'目标库')]))};
    assert.equal(ctx.sections(manyTasks).length,count,'All authorized tasks remain visible');
    assert.equal(ctx.plan(ctx.sections(manyTasks),manyTasks).length,count,'Preparing 20/100 target operations uses only preloaded batch data');
    console.log(`${file}: ${count} rows/tasks rendered chains and all target sections with zero additional requests/SQL`);
  }
  ctx.$common={post:async(route,body)=>{assert.equal(route,'/ods/dataAggReset');resetCalls.push(plain(body));return {data:{}};}};
  await ctx.actions.deleteAllTargets(downstream);
  assert.deepEqual(resetCalls.map(call=>[call.sourceTableId,call.targetTableId]),[['ods','raw']],'Group cleanup uses the downstream source and only its targets');
  await ctx.actions.deleteAllTargets({key:'task:foreign-id'});
  assert.equal(resetCalls.length,1,'Unknown groups cannot issue reset requests');
  resetCalls.length=0; confirmations.length=0;
  await ctx.actions.deleteAllTargets();
  assert.equal(confirmations.length,1,'All groups need only one confirmation');
  assert.equal(confirmations[0].title,'清理全部目标和流程');
  assert.deepEqual(resetCalls.map(call=>[call.sourceTableId,call.targetTableId]),[['ods','raw'],['business','ods']], 'Delete downstream targets first and retain each real source');
  const withFlow={...row,tasks:[...row.tasks,task('empty-flow','raw','原始库',[])]};
  assert.deepEqual(plain(ctx.plan(ctx.sections(withFlow),withFlow)).map(item=>item.key),['flow:empty-flow','target:raw','target:ods'],'An unmaterialized downstream flow is cleaned before its source target');
  resetCalls.length=0;
  ctx.targetDrawerContext.value=withFlow;
  ctx.targetDrawerSections.value=ctx.sections(withFlow);
  await ctx.actions.deleteAllTargets();
  assert.deepEqual(resetCalls.map(call=>[call.sourceTableId,call.taskId||call.targetTableId,call.deleteTargetTable]),[['raw','empty-flow',false],['ods','raw',true],['business','ods',true]],'All cleanup also removes flows without targets using their own task ID');
  const duplicate={...row,tasks:[...row.tasks,row.tasks[0]]};
  assert.equal(ctx.plan(ctx.sections(duplicate),duplicate).length,2,'Repeated target IDs are not deleted twice');
  assert.throws(()=>ctx.plan(ctx.sections(cycle),cycle),/循环/,'Reject cycles before confirmation or writes');
  const differentOwner={tid:'business',tasks:[...row.tasks,task('foreign-owner','other','其他库',[target('raw','原始库')])]};
  assert.throws(()=>ctx.plan(ctx.sections(differentOwner),differentOwner),/不同来源/,'Conflicting source ownership is refused');
  const independent=task('independent','business','业务库',[target('independent-target','其他目标库')]);
  ctx.targetDrawerContext.value={...row,tasks:[...row.tasks,independent]};
  ctx.targetDrawerSections.value=ctx.sections(ctx.targetDrawerContext.value);
  for(const failure of ['pending','transport']){
    resetCalls.length=0; notices.length=0;
    ctx.$common={post:async(route,body)=>{resetCalls.push(plain(body));
      if(body.targetTableId==='raw'){
        if(failure==='transport')throw new Error('isolated transport failure');
        return {data:{targetCleanupPending:true}};
      }
      return {data:{targetHivePhysicalRetained:true}};
    }};
    await ctx.actions.deleteAllTargets();
    assert.deepEqual(resetCalls.map(call=>call.targetTableId),['raw','independent-target'],'Retain dependent upstream targets while continuing independent branches');
    assert.ok(notices.some(item=>item.type==='warning'&&/2 项待重试/.test(item.text)&&/Hive/.test(item.text)));
    assert.equal(ctx.deletingAllTargets.value,false,'Unlock after partial cleanup');
  }
  resetCalls.length=0;
  ctx.ElMessageBox.confirm=async()=>{throw 'cancel';};
  await ctx.actions.deleteAllTargets();
  assert.equal(resetCalls.length,0,'Cancel performs no business mutation');
  assert.equal(ctx.deletingAllTargets.value,false,'Cancel unlocks all controls');
  let rejectDecision, decisionCalls=0;
  ctx.ElMessageBox.confirm=()=>{decisionCalls++;return new Promise((resolve,reject)=>{rejectDecision=reject;});};
  const firstClick=ctx.actions.deleteAllTargets();
  await ctx.actions.deleteAllTargets();
  assert.equal(decisionCalls,1,'Repeated click while confirming cannot start a second cleanup');
  rejectDecision('cancel');await firstClick;
  assert.equal(ctx.deletingAllTargets.value,false);
  if (file.includes('Hive')) {
    assert.ok(source.includes('/ods/hiveTargetPage') && source.includes('/ods/hiveTargetTree'));
    assert.ok(!source.includes('__hive_stage__'), 'Distribution uses the actual warehouse source');
  }
}
console.log('Linear chains, parallel sinks, unrelated IDs, branching and cycles passed');
