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
const names=new Set(['normalizeNumber','parsePoolCfg','loadSourcePage','collectTreeNodeOrgIds',
  'sourcePageGeneration','sourceCursors','sourceScopePayload','sourcePageFilters','sourcePageFilterKey','sourcePagePayload',
  'resetSourcePage','changeSourcePage','changeSourcePageSize',
  'supportedDatasourceTypes','normalizeDatasourceType','normalizeAccessMode','isDataReportSource','isDataPushSource',
  'isProxyDatasource','sourceTypeIcon','sourceTypeName','sourceTypeColor','datasourceTypeKey','datasourceTypeCatalog',
  'datasourceTypeCounts','datasourceTypeOptions','typeStatisticsGeneration',
  'typeStatisticsTimer','typeStatisticsKey','typeStatisticsPayload','applyTypeStatistics','loadTypeStatistics',
  'scheduleTypeStatistics']);
const nodes=ast.program.body.filter(n=>n.type==='VariableDeclaration'&&n.declarations.some(d=>names.has(d.id?.name)));
assert.equal(nodes.length,names.size);
const statisticsWatch=ast.program.body.find(n=>n.type==='ExpressionStatement'&&script.slice(n.start,n.end).startsWith('watch(() => JSON.stringify(typeStatisticsPayload())'));
const cardsWatch=ast.program.body.find(n=>n.type==='ExpressionStatement'&&script.slice(n.start,n.end).startsWith('watch(sourcePageFilterKey'));
assert.ok(statisticsWatch);assert.ok(cardsWatch);
const executable='let loadGeneration=1;\n'+nodes.map(n=>script.slice(n.start,n.end)).join('\n')
  +'\nglobalThis.actual={loadSourcePage,loadTypeStatistics,typeStatisticsPayload,changeSourcePage,changeSourcePageSize,datasourceTypeOptions,sourceTypeColor};';
const full=()=>({complete:true,total:10001,typeCounts:{mysql:6001,oracle:4000},dbTypes:{mysql:6001,oracle:4000}});
const page=(body,count,total=10001)=>({dataScope:'OWNER',total,pageNum:body.pageNum,pageSize:body.pageSize,
  list:Array.from({length:count},(_,i)=>({tid:`source-${(body.pageNum-1)*body.pageSize+i}`,dbType:body.typeKey||'mysql',assetStatus:2}))});
function fixture(count,{autoWatch=false,watchCards=false,reply=()=>full(),replyCards=body=>page(body,count)}={}){
  const calls=[],state=reactive({list:[],keyword:'',datasourceType:'',filter:'all',treeLoading:false,
    leftSelectNode:{value:''},sourcePage:1,sourcePageSize:count,sourceTotal:0,expanded:[],tableLoads:{},typeStatistics:null,
    typeStatisticsLoading:true,typeStatisticsError:''});
  const post=async(url,body)=>{calls.push({url,body});
    if(body.mode==='cards')return replyCards(body);
    return reply(body);
  };
  const context=vm.createContext({state,computed,watch,nextTick,setTimeout,clearTimeout,console:{error:()=>{}},SOURCE_PAGE_SIZES:[24,48,100],
    treeData:{value:[{value:''}]},sourceListTop:{value:null},normalizeItem:x=>x,$common:{post,postSilently:post}});
  vm.runInContext(executable,context);
  if(autoWatch)vm.runInContext('globalThis.stopStatisticsWatch='+script.slice(statisticsWatch.start,statisticsWatch.end),context);
  if(watchCards)vm.runInContext('globalThis.stopCardsWatch='+script.slice(cardsWatch.start,cardsWatch.end),context);
  return {state,calls,...context.actual,dispose:()=>{context.stopStatisticsWatch?.();context.stopCardsWatch?.();}};
}
const option=(f,key)=>f.datasourceTypeOptions.value.find(x=>x.value===key);
for(const count of [20,100])test(`${count} card rows use one page read and never limit full totals`,async()=>{
  const f=fixture(count);await f.loadSourcePage();await f.loadTypeStatistics();
  assert.equal(f.state.list.length,count);assert.equal(f.state.sourceTotal,10001);
  assert.equal(f.state.typeStatistics.total,10001);
  assert.equal(option(f,'mysql').count,6001);assert.equal(option(f,'oracle').count,4000);
  assert.equal(option(f,'kingbase8').count,0);
  assert.deepEqual(f.calls.map(({body})=>body.mode),['cards','facets']);
  assert.ok(!('typeKey' in f.calls[1].body));
  assert.ok(!('tenantId' in f.calls[1].body));
  f.calls.length=0;
  await f.changeSourcePage(Math.floor(1000/count)+1);
  assert.equal(f.state.list[0].tid,'source-1000');
  assert.equal(f.calls.length,1);
});
test('Department descendants, application, keyword and status are sent together; only card pages include type',async()=>{
  const f=fixture(20);f.state.leftSelectNode={value:'dept-a',children:[{value:'dept-child'}]};
  f.state.keyword='Oracle';f.state.filter='unannotated';f.state.datasourceType='oracle';
  await f.loadTypeStatistics();
  assert.deepEqual(JSON.parse(JSON.stringify(f.calls[0].body)),{mode:'facets',keyword:'Oracle',filter:'unannotated',orgIds:['dept-a','dept-child'],appId:''});
  f.state.leftSelectNode={type:'app',value:'system-a'};await f.loadSourcePage();
  assert.deepEqual(JSON.parse(JSON.stringify(f.calls[1].body)),{mode:'cards',keyword:'Oracle',filter:'unannotated',orgIds:[],appId:'system-a',typeKey:'oracle',pageNum:1,pageSize:20});
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
test('Type and page-size changes reset the server page without recalculating type totals',async()=>{
  const f=fixture(24,{autoWatch:true,watchCards:true,replyCards:body=>page(body,body.pageSize)});
  await f.loadTypeStatistics();
  await f.loadSourcePage();
  await f.changeSourcePage(3);
  f.calls.length=0;
  f.state.datasourceType='oracle';
  await nextTick();
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(f.state.sourcePage,1);
  assert.equal(f.state.list[0].dbType,'oracle');
  assert.equal(option(f,'mysql').count,6001);
  await f.changeSourcePageSize(100);
  assert.equal(f.state.list.length,100);
  assert.deepEqual(f.calls.map(({body})=>[body.mode,body.typeKey,body.pageNum,body.pageSize]),[
    ['cards','oracle',1,24],['cards','oracle',1,100],
  ]);
  f.dispose();
});
test('The latest card request wins, and an invalid page is retryable',async()=>{
  const pending=[];
  const f=fixture(24,{replyCards:body=>new Promise(resolve=>pending.push({resolve,body}))});
  const older=f.loadSourcePage();
  f.state.keyword='当前';
  const current=f.loadSourcePage();
  pending[1].resolve({list:[{tid:'current'}],total:1,pageNum:1,pageSize:24,dataScope:'ORG'});
  await current;
  pending[0].resolve(page(pending[0].body,24));
  await older;
  assert.equal(f.state.list[0].tid,'current');
  assert.equal(f.state.sourceTotal,1);
  const invalid=f.loadSourcePage();
  pending[2].resolve({list:[]});
  await invalid;
  assert.ok(f.state.listError);
  const retry=f.loadSourcePage();
  assert.equal(pending[3].body.keyword,'当前');
  pending[3].resolve({list:[],total:0,pageNum:1,pageSize:24,dataScope:'ORG'});
  await retry;
  assert.equal(f.state.sourceTotal,0);
  assert.equal(f.state.listError,'');
});
test('Changed cards follow server cursors, while an unavailable index remains an error',async()=>{
  const cursor={sortKey:'2026-11-07 12:00:00',tid:'source-24'};
  const f=fixture(24,{replyCards:body=>({ ...page(body,24,48),
    ...(body.pageNum===1?{nextCursor:cursor}:{changesAvailable:true}) })});
  f.state.filter='changed';
  await f.loadSourcePage();
  await f.changeSourcePage(2);
  assert.equal(f.calls[1].body.cursor,cursor);
  assert.equal(f.state.sourcePage,2);
  await f.changeSourcePage(1);
  assert.ok(!('cursor' in f.calls[2].body));
  const unavailable=fixture(24,{replyCards:()=>({changesAvailable:false})});
  unavailable.state.filter='changed';
  await unavailable.loadSourcePage();
  assert.match(unavailable.state.listError,/变更暂不可用/);
});
test('Failed or incomplete statistics show an unavailable marker, never snapshot counts or false zeros',async()=>{
  for(const reply of [()=>{throw new Error('offline');},()=>({total:20,typeCounts:{mysql:20}})]){
    const f=fixture(20,{reply});await f.loadSourcePage();await f.loadTypeStatistics();
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
