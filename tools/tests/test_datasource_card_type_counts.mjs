import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../../', import.meta.url);
const source = await fs.readFile(new URL('lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource).vue', root), 'utf8');
const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
const ast = babel.Babel.transform(script, {ast:true,code:false,configFile:false,babelrc:false,
  parserOpts:{sourceType:'module',plugins:['typescript']}}).ast;
const names = ['normalizeNumber','firstNumber','firstText','isTableAnnotated','isTableRegistered',
  'tableHasIssue','tableChangeStatus','tableArrayStats','normalizeItem','datasourceUpdatedAt',
  'compareDatasourceUpdated','loadSourceOverview','filterOptions','hasCompletedRegistrationStatus',
  'hasIncompleteRegistrationStatus','isRegistrationComplete','statusText','matchedFilter',
  'isMetricSelected','applyMetricFilter','applyIncompleteFilter','clearMetricFilter',
  'isSystemAdministratorScope','currentScopeLabel'];
const executable = 'let loadGeneration=1;\n'+names.map(name => {
  const node=ast.program.body.find(n=>n.type==='VariableDeclaration' && n.declarations.some(d=>d.id.name===name));
  assert.ok(node,name);return script.slice(node.start,node.end);
}).join('\n')+'\nglobalThis.actual={tableArrayStats,normalizeItem,loadSourceOverview,filterOptions,statusText,matchedFilter,hasIncompleteRegistrationStatus,isMetricSelected,applyMetricFilter,applyIncompleteFilter,clearMetricFilter,isSystemAdministratorScope,currentScopeLabel};';
const tables=[];
for(const businessType of ['business','业务表','dict','dictionary','字典表','log','日志表']) {
  for(const asset_status of [0,1,2,-1,null,undefined]) tables.push({businessType,asset_status,annotated:1});
}
tables.push({businessType:'过程表',assetStatus:2,annotated:1});
for(const policy of ['ALL','ORG','OWNER']) for(const size of [20,100]) {
  const calls=[],state={orgCounts:policy==='ALL'?{'dept-a':size}:{'dept-a':size,'dept-b':size}};
  const treeData={value:[{label:'旧范围'}]};
  const context=vm.createContext({console,state,SOURCE_BATCH_SIZE:20,userOrgName:{value:'部门'},
    treeData,computed:fn=>({get value(){return fn();}}),
    $common:{post:async(route,body)=>{
      calls.push({route,body});return {dataScope:policy,list:Array.from({length:size},(_,i)=>({tid:`source-${i}`,tables}))};
    }}});
  vm.runInContext(executable,context);
  const stats=context.actual.tableArrayStats(tables);
  assert.equal(stats.businessTableNum,4);assert.equal(stats.dictionaryTableNum,6);assert.equal(stats.logTableNum,4);
  assert.equal(stats.tableNum,tables.length);assert.equal(stats.annotatedTableNum,tables.length);
  // Status 1 counts towards the type metric, but only 2 counts towards full registration.
  assert.equal(stats.registeredTableNum,8);
  const zero=context.actual.normalizeItem({tables,businessTableNum:0,dictionaryTableNum:0,logTableNum:0});
  for(const key of ['businessTableNum','dictionaryTableNum','logTableNum']) assert.equal(zero[key],0,'Server zero must override fallback tables');
  await context.actual.loadSourceOverview(1);
  assert.equal(state.list.length,size);assert.equal(calls.length,1);
  const scopeLabel={ALL:'全部部门',ORG:'本部门数据',OWNER:'本人登记数据'}[policy];
  assert.equal(state.dataScope,policy);assert.equal(treeData.value[0].label,scopeLabel);
  assert.equal(context.actual.currentScopeLabel.value,scopeLabel);
  assert.equal(context.actual.isSystemAdministratorScope.value,policy==='ALL','Department count must not determine authority');
  assert.equal(calls[0].route,'/dst/database/departmentDataSources');assert.equal(calls[0].body.mode,'summary');
  for(const row of state.list) assert.equal(row.businessTableNum,4);
  assert.equal(state.loading,false);assert.equal(state.listError,'');
  assert.deepEqual(Array.from(context.actual.filterOptions, option=>option.label),['全部','待标注','未完成','已完成','有变更']);
  const sources=Array.from({length:size},(_,i)=>({tid:`source-${i}`,assetStatus:i%3,connectionStatus:i%2?'success':'failed'}));
  const incomplete=sources.filter(row=>row.assetStatus===1);
  state.filter='unannotated';state.metricFilter='tableIssues';
  context.actual.applyIncompleteFilter();
  assert.equal(state.filter,'incomplete');assert.equal(state.metricFilter,'sourceIssues');
  const selected=sources.filter(context.actual.matchedFilter);
  assert.deepEqual(selected,incomplete);
  assert.ok(selected.every(row=>context.actual.statusText(row)==='未完成'));
  assert.equal(sources.filter(context.actual.hasIncompleteRegistrationStatus).length,selected.length);
  assert.ok(context.actual.hasIncompleteRegistrationStatus({asset_status:1}));
  assert.ok(!context.actual.hasIncompleteRegistrationStatus({asset_status:0,connectionStatus:'failed'}));
  assert.ok(!context.actual.hasIncompleteRegistrationStatus({assetStatus:2,connectionStatus:'failed'}));
  context.actual.applyIncompleteFilter();assert.equal(state.filter,'all');
  state.filter='incomplete';state.metricFilter='changed';context.actual.clearMetricFilter();
  assert.equal(state.filter,'incomplete');assert.equal(state.metricFilter,'');
  assert.deepEqual(sources.filter(context.actual.matchedFilter),incomplete);
  const metricSources=sources.map((row,i)=>({...row,unannotatedTableNum:i%2,changedTableNum:i%5===0?1:0,problemTableNum:0}));
  for (const [metric,filter,expected] of [
    ['unannotated','unannotated',metricSources.filter(row=>row.unannotatedTableNum>0)],
    ['tableIssues','incomplete',metricSources.filter(row=>row.assetStatus===1)],
    ['changed','changed',metricSources.filter(row=>row.changedTableNum>0)],
  ]) {
    state.filter='all';state.metricFilter='';
    context.actual.applyMetricFilter(metric);
    assert.equal(state.filter,filter);assert.ok(context.actual.isMetricSelected(metric));
    assert.deepEqual(metricSources.filter(context.actual.matchedFilter),expected);
    if (metric==='tableIssues') assert.ok(!context.actual.isMetricSelected('sourceIssues'));
    context.actual.applyMetricFilter(metric);assert.equal(state.filter,'all');assert.equal(state.metricFilter,'');
  }
  state.filter='unannotated';state.metricFilter='';
  assert.ok(context.actual.isMetricSelected('unannotated'));
  context.actual.applyMetricFilter('unannotated');assert.equal(state.filter,'all');
  context.actual.applyIncompleteFilter();context.actual.applyMetricFilter('tableIssues');
  assert.equal(state.filter,'incomplete');assert.ok(context.actual.isMetricSelected('tableIssues'));
  assert.ok(!context.actual.isMetricSelected('sourceIssues'));
  context.actual.clearMetricFilter();assert.ok(context.actual.isMetricSelected('sourceIssues'));
  assert.equal(calls.length,1,'Card and segment filtering must not issue per-source reads');
  console.log(`Cards=${size}: one overview request; positive status fallback and authoritative zero passed`);
  console.log(`Cards=${size}: incomplete metric, badge and card/segment filtering agree; draft and connection-only failures excluded`);
  console.log(`Cards=${size}: annotation/registration/change cards select their segment; repeated clicks reset all; one registration card highlighted at a time`);
}
