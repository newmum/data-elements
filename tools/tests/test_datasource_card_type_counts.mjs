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
const names = ['normalizeNumber','firstNumber','firstText','normalizeItem','filterOptions','statusText',
  'isSystemAdministratorScope','currentScopeLabel'];
const routeFilter = ast.program.body.find(node => node.type === 'ExpressionStatement' &&
  node.expression.callee?.name === 'watch' && script.slice(node.start, node.end).includes('route.query.filter'));
assert.ok(routeFilter);
const executable = names.map(name => {
  const node=ast.program.body.find(n=>n.type==='VariableDeclaration' && n.declarations.some(d=>d.id.name===name));
  assert.ok(node,name);return script.slice(node.start,node.end);
}).join('\n')+'\n'+script.slice(routeFilter.start,routeFilter.end)+
  '\nglobalThis.actual={normalizeItem,filterOptions,statusText,isSystemAdministratorScope,currentScopeLabel};';
for(const policy of ['ALL','ORG','OWNER']) {
  const state={dataScope:policy};
  const route={path:'/datasource-manage/index',query:{filter:'incomplete',metric:'tableIssues'}};
  let applyRouteFilter;
  const context=vm.createContext({state,route,userOrgName:{value:'部门'},computed:fn=>({get value(){return fn();}}),
    watch:(_source,apply,{immediate})=>{applyRouteFilter=apply;if(immediate)apply();}});
  vm.runInContext(executable,context);
  const normalized=context.actual.normalizeItem({tableNum:8,annotatedTableNum:6,registeredTableNum:4,
    businessTableNum:4,dictionaryTableNum:6,logTableNum:4});
  assert.equal(normalized.businessTableNum,4);assert.equal(normalized.dictionaryTableNum,6);
  assert.equal(normalized.logTableNum,4);assert.equal(normalized.unannotatedTableNum,2);
  const zero=context.actual.normalizeItem({businessTableNum:0,dictionaryTableNum:0,logTableNum:0,unannotatedTableNum:0});
  for(const key of ['businessTableNum','dictionaryTableNum','logTableNum']) assert.equal(zero[key],0,'Server zero must override fallback tables');
  const scopeLabel={ALL:'全部部门',ORG:'本部门数据',OWNER:'本人登记数据'}[policy];
  assert.equal(context.actual.currentScopeLabel.value,scopeLabel);
  assert.equal(context.actual.isSystemAdministratorScope.value,policy==='ALL','Department count must not determine authority');
  assert.deepEqual(Array.from(context.actual.filterOptions, option=>option.label),['全部','待标注','未完成','已完成','有变更']);
  assert.deepEqual([0,1,2].map(assetStatus=>context.actual.statusText({assetStatus,connectionStatus:'failed'})),['待登记','未完成','已完成']);
  assert.equal(state.filter,'incomplete','Legacy workbench links still open the incomplete list');
  route.query={filter:'unannotated'};
  applyRouteFilter();
  assert.equal(state.filter,'unannotated');
  route.query={filter:'invalid',tenantId:'other'};
  applyRouteFilter();
  assert.equal(state.filter,'all');
  assert.equal(state.dataScope,policy);
}
