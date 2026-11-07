import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const root=new URL('../../',import.meta.url);
const source=await fs.readFile(new URL('lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource).vue',root),'utf8');
const script=source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
const babel={};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js',root),'utf8'),babel);
const ast=babel.Babel.transform(script,{ast:true,code:false,configFile:false,babelrc:false,
  parserOpts:{sourceType:'module',plugins:['typescript']}}).ast;
const names=['normalizeNumber','loadSourcePage','loadOrganizationTree','loadData','getTreeNodeCount',
  'filterZeroNodes','appendApplicationNodes','applyDefaultTreeState','rebuildTree','isSystemAdministratorScope',
  'collectTreeNodeOrgIds','findSelectedTreeNode','sourceScopePayload','sourcePageFilters','sourcePageFilterKey','sourcePagePayload'];
const executable='let loadGeneration=0;let sourcePageGeneration=0;\n'+names.map(name=>{
  const node=ast.program.body.find(n=>n.type==='VariableDeclaration'&&n.declarations.some(d=>d.id?.name===name));
  assert.ok(node,name);return script.slice(node.start,node.end);
}).join('\n')+'\nglobalThis.actual={loadData};';
function deferred(){let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b;});return {promise,resolve,reject};}
const readsPerRefresh = 3;
function fixture(size){
  const calls=[],nodes=new Map();
  const departments=Array.from({length:size},(_,i)=>({value:`department-${i}`,label:`部门${i}`,children:[]}));
  const scope={dataScope:'',tableLoads:{},list:[],keyword:'',filter:'all',sourcePage:1,sourcePageSize:24,sourceTotal:0};
  const treeData={value:[{value:'',type:'org',label:'全部部门',children:[]}]};
  const tree={getNode:id=>{if(!nodes.has(id))nodes.set(id,{expanded:false});return nodes.get(id);},setCurrentNode:node=>{tree.current=node;}};
  const context=vm.createContext({state:scope,treeData,treeRef:{value:{getTree:()=>tree}},
    orgTreeSource:{value:[]},treeStatistics:{value:{}},countMap:{value:{}},sourceCursors:new Map(),
    computed:fn=>({get value(){return fn();}}),nextTick:callback=>Promise.resolve().then(callback),
    normalizeItem:row=>row,$user:{orgRootId:'root'},console:{error:()=>{}},
    typeStatisticsPayload:()=>({mode:'facets'}),applyTypeStatistics:()=>{},scheduleTypeStatistics:()=>{},
    $common:{post:(route,body)=>{const request=deferred();calls.push({route,body,...request});return request.promise;}},
  });
  vm.runInContext(executable,context);
  const group = offset => calls.slice(offset,offset+readsPerRefresh);
  const request = (offset,mode) => group(offset).find(call=>call.body.mode===mode);
  const respondPage = (offset,policy) => {
    const page = request(offset,'cards');
    const start = (page.body.pageNum - 1) * page.body.pageSize;
    page.resolve({dataScope:policy, total:size, pageNum:page.body.pageNum,pageSize:page.body.pageSize,
      list:departments.slice(start,start+page.body.pageSize).map(d=>({tid:d.value}))});
  };
  const statistics={total:size,orgCounts:Object.fromEntries(departments.map(d=>[d.value,1])),
    orgApps:Object.fromEntries(departments.map((d,i)=>[d.value,[{value:`system-${i}`,label:`系统${i}`,count:1}]]))};
  const respondTree=offset=>{
    group(offset).find(call=>call.route==='/sym/org/getOrgTree')
      .resolve([{value:'authority',label:'机构',children:departments}]);
    request(offset,'facets').resolve(statistics);
  };
  return {scope,treeData,tree,nodes,calls,request,respondPage,respondTree,load:context.actual.loadData};
}
for(const size of [20,100])for(const policy of ['ALL','ORG','OWNER'])for(const order of ['scope-first','tree-first']){
  const f=fixture(size),loading=f.load();
  assert.equal(f.calls.length,readsPerRefresh);
  if(order==='scope-first'){f.respondPage(0,policy);await Promise.resolve();f.respondTree(0);}
  else{f.respondTree(0);await new Promise(resolve=>setImmediate(resolve));
    assert.ok([...f.nodes.values()].every(node=>!node.expanded),'Do not expand systems before the scope is known');
    f.respondPage(0,policy);}
  await loading;
  assert.equal(f.scope.dataScope,policy);
  assert.equal(f.nodes.get('').expanded,true);assert.equal(f.nodes.get('authority').expanded,true);
  for(let i=0;i<size;i++)assert.equal(f.nodes.get(`department-${i}`).expanded,policy!=='ALL');
  assert.equal(f.calls.length,readsPerRefresh,'Default expansion must not issue node reads');
  // Department contents remain available for a deliberate user expansion.
  assert.equal(f.treeData.value[0].children[0].children[0].children[0].type,'app');
  f.nodes.get('department-0').expanded=true;
  assert.equal(f.calls.length,readsPerRefresh);
  console.log(`${policy} ${size} departments, ${order}: root/organizations expanded; department expansion=${policy!=='ALL'}; requests=${readsPerRefresh}`);
}
{
  const f=fixture(20),older=f.load(),current=f.load();
  f.respondPage(readsPerRefresh,'OWNER');f.respondTree(readsPerRefresh);await current;
  f.respondTree(0);f.respondPage(0,'ALL');await older;
  assert.equal(f.scope.dataScope,'OWNER');assert.equal(f.nodes.get('department-0').expanded,true);
}
{
  const f=fixture(20),loading=f.load();f.respondTree(0);
  f.request(0,'cards').reject(new Error('cards unavailable'));await loading;
  assert.equal(f.scope.dataScope,'');assert.equal(f.nodes.get('department-0').expanded,false);
  assert.ok(f.scope.listError);assert.equal(f.calls.length,readsPerRefresh);
}
{
  const f=fixture(100),initial=f.load();f.respondTree(0);f.respondPage(0,'ALL');await initial;
  const selected=f.treeData.value[0].children[0].children[0].children[0];
  f.scope.leftSelectNode=selected;f.scope.sourcePage=2;
  const refreshed=f.load();
  assert.equal(f.request(readsPerRefresh,'cards').body.appId,'system-0');
  assert.equal(f.request(readsPerRefresh,'cards').body.pageNum,2);
  f.respondTree(readsPerRefresh);f.respondPage(readsPerRefresh,'ALL');await refreshed;
  assert.equal(f.scope.leftSelectNode.value,selected.value);
  assert.notEqual(f.scope.leftSelectNode,selected,'Selection is rebound to the new tree data');
  assert.equal(f.tree.current,f.scope.leftSelectNode);
  assert.equal(f.calls.length,readsPerRefresh*2,'Refreshing a surviving selection makes only the three normal requests');
}
console.log('Refresh preserves valid selection; stale or failed scope responses cannot expand unrelated systems.');
