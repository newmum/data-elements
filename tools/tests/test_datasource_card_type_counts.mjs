import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../../', import.meta.url);
const source = await fs.readFile(new URL('lowcode/01.系统门户/01.数据源管理/03.数据源管理列表(department-datasource).vue', root), 'utf8');
const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
const ast = babel.Babel.transform(script, {ast:true,code:false,configFile:false,babelrc:false,
  parserOpts:{sourceType:'module',plugins:['typescript']}}).ast;
const names = ['normalizeNumber','firstNumber','firstText','isTableAnnotated','isTableRegistered',
  'tableHasIssue','tableChangeStatus','tableArrayStats','normalizeItem','datasourceUpdatedAt',
  'compareDatasourceUpdated','loadSourceOverview'];
const executable = 'let loadGeneration=1;\n'+names.map(name => {
  const node=ast.program.body.find(n=>n.type==='VariableDeclaration' && n.declarations.some(d=>d.id.name===name));
  assert.ok(node,name);return script.slice(node.start,node.end);
}).join('\n')+'\nglobalThis.actual={tableArrayStats,normalizeItem,loadSourceOverview};';
const tables=[];
for(const businessType of ['business','业务表','dict','dictionary','字典表','log','日志表']) {
  for(const asset_status of [0,1,2,-1,null,undefined]) tables.push({businessType,asset_status,annotated:1});
}
tables.push({businessType:'过程表',assetStatus:2,annotated:1});
for(const size of [20,100]) {
  const calls=[],state={};
  const context=vm.createContext({console,state,SOURCE_BATCH_SIZE:20,userOrgName:{value:'部门'},
    $common:{post:async(route,body)=>{
      calls.push({route,body});return {list:Array.from({length:size},(_,i)=>({tid:`source-${i}`,tables}))};
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
  assert.equal(calls[0].route,'/dst/database/departmentDataSources');assert.equal(calls[0].body.mode,'summary');
  for(const row of state.list) assert.equal(row.businessTableNum,4);
  assert.equal(state.loading,false);assert.equal(state.listError,'');
  console.log(`Cards=${size}: one overview request; positive status fallback and authoritative zero passed`);
}
