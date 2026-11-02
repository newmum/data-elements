import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import { createRequire } from 'node:module';
import vm from 'node:vm';
import test from 'node:test';

const root=new URL('../../',import.meta.url);
const require=createRequire(new URL('data-elements-chengtian/package.json',root));
const {computed,reactive,watch,nextTick}=require('vue');
const source=await fs.readFile(new URL('lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource).vue',root),'utf8');
const script=source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
const babel={};vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js',root),'utf8'),babel);
const ast=babel.Babel.transform(script,{ast:true,code:false,configFile:false,babelrc:false,parserOpts:{sourceType:'module',plugins:['typescript']}}).ast;
// Runtime SFCs do not auto-import Vue lifecycle hooks; missing imports pass syntax compilation but blank the page.
const vueImports=new Set(ast.program.body.filter(n=>n.type==='ImportDeclaration'&&n.source.value==='vue').flatMap(n=>n.specifiers.map(s=>s.local.name)));
for(const hook of ['onMounted','onBeforeUnmount','watch','computed'])assert.ok(vueImports.has(hook),`${hook} must resolve in the released factory`);
const names=new Set(['normalizeNumber','parsePoolCfg','loadSourceOverview','matchedKeyword','matchedFilter',
  'filteredBeforeOrganization','collectTreeNodeOrgIds','matchesSelectedTreeNode','sourcesBeforeType','visibleList',
  'displayList','hasCompletedRegistrationStatus','hasIncompleteRegistrationStatus','isRegistrationComplete',
  'supportedDatasourceTypes','normalizeDatasourceType','normalizeAccessMode','isDataReportSource','isDataPushSource',
  'isProxyDatasource','sourceTypeIcon','sourceTypeName','sourceTypeColor','datasourceTypeKey','datasourceTypeCatalog',
  'datasourceTypeCounts','datasourceTypeOptions','typeStatisticsGeneration',
  'typeStatisticsTimer','typeStatisticsKey','typeStatisticsPayload','applyTypeStatistics','loadTypeStatistics',
  'scheduleTypeStatistics']);
const nodes=ast.program.body.filter(n=>n.type==='VariableDeclaration'&&n.declarations.some(d=>names.has(d.id?.name)));
assert.equal(nodes.length,names.size);
const statisticsWatch=ast.program.body.find(n=>n.type==='ExpressionStatement'&&script.slice(n.start,n.end).startsWith('watch(() => JSON.stringify(typeStatisticsPayload())'));
assert.ok(statisticsWatch);
const executable='let loadGeneration=1;\n'+nodes.map(n=>script.slice(n.start,n.end)).join('\n')
  +'\nglobalThis.actual={loadSourceOverview,loadTypeStatistics,typeStatisticsPayload,visibleList,displayList,datasourceTypeOptions,sourceTypeColor};';
const full=()=>({complete:true,total:10001,typeCounts:{mysql:6001,oracle:4000},dbTypes:{mysql:6001,oracle:4000}});
function fixture(count,{autoWatch=false,reply=()=>full()}={}){
  const calls=[],state=reactive({list:[],keyword:'',datasourceType:'',filter:'all',metricFilter:'',treeLoading:false,
    leftSelectNode:{value:''},sourceDisplayLimit:24,expanded:[],typeStatistics:null,typeStatisticsLoading:true,typeStatisticsError:''});
  const context=vm.createContext({state,computed,watch,nextTick,setTimeout,clearTimeout,console,SOURCE_BATCH_SIZE:24,
    treeData:{value:[{value:''}]},normalizeItem:x=>x,compareDatasourceUpdated:()=>0,
    $common:{post:async(url,body)=>{calls.push({url,body});return url.endsWith('departmentDataSources')
      ?{dataScope:'OWNER',list:Array.from({length:count},(_,i)=>({tid:`source-${i}`,dbType:'mysql',assetStatus:2}))}:reply(body);}}});
  vm.runInContext(executable,context);
  if(autoWatch)vm.runInContext('globalThis.stopWatch='+script.slice(statisticsWatch.start,statisticsWatch.end),context);
  return {state,calls,...context.actual,dispose:()=>context.stopWatch?.()};
}
const option=(f,key)=>f.datasourceTypeOptions.value.find(x=>x.value===key);
for(const count of [20,100])test(`${count} overview rows never limit full totals or trigger per-type reads`,async()=>{
  const f=fixture(count);await f.loadSourceOverview(1);await f.loadTypeStatistics();
  assert.equal(f.state.list.length,count);assert.equal(f.displayList.value.length,Math.min(count,24));
  assert.equal(f.state.typeStatistics.total,10001);
  assert.equal(option(f,'mysql').count,6001);assert.equal(option(f,'oracle').count,4000);
  assert.equal(option(f,'kingbase8').count,0);
  f.state.datasourceType='mysql';assert.equal(f.visibleList.value.length,count);
  assert.equal(option(f,'oracle').count,4000);
  assert.equal(f.calls.length,2,'One overview plus one aggregation, independent of row and type counts');
  assert.ok(!('datasourceType' in f.calls[1].body));
  assert.ok(!('tenantId' in f.calls[1].body));
});
test('Department descendants, application, keyword and status are sent together; type selection stays local',async()=>{
  const f=fixture(20);f.state.leftSelectNode={value:'dept-a',children:[{value:'dept-child'}]};
  f.state.keyword='Oracle';f.state.filter='unannotated';f.state.datasourceType='oracle';
  await f.loadTypeStatistics();
  assert.deepEqual(JSON.parse(JSON.stringify(f.calls[0].body)),{mode:'facets',keyword:'Oracle',filter:'unannotated',orgIds:['dept-a','dept-child'],appId:''});
  f.state.leftSelectNode={type:'app',value:'system-a'};await f.loadTypeStatistics();
  assert.equal(f.calls[1].body.appId,'system-a');assert.equal(f.calls[1].body.orgIds.length,0);
});
test('Fast filter changes merge into one request; the latest response wins',async()=>{
  const f=fixture(100,{autoWatch:true});f.state.filter='completed';f.state.keyword='日报';f.state.leftSelectNode={value:'dept-a'};
  await nextTick();await new Promise(resolve=>setTimeout(resolve,190));
  assert.equal(f.calls.length,1);f.dispose();
  const pending=[];const g=fixture(20,{reply:()=>new Promise(resolve=>pending.push(resolve))});
  const older=g.loadTypeStatistics();g.state.keyword='new';const newer=g.loadTypeStatistics();
  pending[1]({...full(),total:2,typeCounts:{mysql:2}});await newer;
  pending[0](full());await older;
  assert.equal(option(g,'mysql').count,2);assert.equal(g.state.typeStatistics.total,2);
});
test('Failed or incomplete statistics show an unavailable marker, never snapshot counts or false zeros',async()=>{
  for(const reply of [()=>{throw new Error('offline');},()=>({total:20,typeCounts:{mysql:20}})]){
    const f=fixture(20,{reply});await f.loadSourceOverview(1);await f.loadTypeStatistics();
    assert.ok(f.state.typeStatisticsError);assert.equal(f.state.typeStatistics,null);
    assert.equal(f.state.typeStatisticsLoading,false);
  }
  const f=fixture(0,{reply:()=>({complete:true,total:0,typeCounts:{},dbTypes:{}})});await f.loadTypeStatistics();
  assert.equal(f.state.typeStatistics.total,0);assert.ok(f.datasourceTypeOptions.value.every(x=>x.count===0));
});
test('Dropdown and selected icons share card colors, including proxy precedence and unknown server-only types',async()=>{
  const f=fixture(20,{reply:()=>({...full(),total:10005,typeCounts:{...full().typeCounts,experimentaldb:4},dbTypes:{...full().dbTypes,'Experimental DB':4}})});await f.loadTypeStatistics();
  for(const [key,color] of [['api','#1677ff'],['mode:proxy','#1677ff'],['mode:receive','#079669'],['mode:upload','#7c3aed'],['experimentaldb','#1677ff']]){
    assert.equal(option(f,key).color,color);f.state.datasourceType=key;assert.equal(option(f,f.state.datasourceType).color,color);
  }
  assert.equal(f.sourceTypeColor({dbType:'api',accessMode:'proxy',showConnect:0}),'#1677ff');
  assert.match(source,/:style="\{ color: sourceTypeColor\(item\) \}"/);
  const shared=await fs.readFile(new URL("data-elements-chengtian/src/components/base/datasource-type-filter.vue",root),"utf8");
  assert.match(source,/:options="datasourceTypeOptions"/);
  assert.match(shared,/:style="\{ color: option.color \}"/);
  assert.match(shared,/:style="\{ color: selected.color \}"/);
});
test('Raw receive markers already grouped as push do not create an extra zero-count English option',async()=>{
  const f=fixture(0,{reply:()=>({complete:true,total:4,typeCounts:{'mode:receive':4},dbTypes:{receive:2,api:2}})});
  f.state.list=[{tid:'structured',dbType:'receive',accessMode:'receive'},
    {tid:'unstructured',dbType:'receive',accessMode:'receive'}];
  await f.loadTypeStatistics();
  assert.equal(option(f,'receive'),undefined);
  assert.equal(option(f,'mode:receive').label,'数据推送方式');
  assert.equal(option(f,'mode:receive').count,4);
  assert.equal(option(f,'mode:receive').color,'#079669');
  assert.equal(f.datasourceTypeOptions.value.filter(x=>x.label==='数据推送方式').length,1);
  assert.equal(option(f,'oracle').count,0,'Configured zero-count database choices remain visible');
});
