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
const names=['normalizeNumber','loadSourceOverview','loadOrganizationTree','loadData','getTreeNodeCount',
  'filterZeroNodes','appendApplicationNodes','applyDefaultTreeState','rebuildTree','isSystemAdministratorScope'];
const executable='let loadGeneration=0;\n'+names.map(name=>{
  const node=ast.program.body.find(n=>n.type==='VariableDeclaration'&&n.declarations.some(d=>d.id?.name===name));
  assert.ok(node,name);return script.slice(node.start,node.end);
}).join('\n')+'\nglobalThis.actual={loadData};';
function deferred(){let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b;});return {promise,resolve,reject};}
function fixture(size){
  const calls=[],nodes=new Map();
  const departments=Array.from({length:size},(_,i)=>({value:`department-${i}`,label:`部门${i}`,children:[]}));
  const scope={dataScope:'',tableLoads:{},list:[]};
  const treeData={value:[{value:'',type:'org',label:'全部部门',children:[]}]};
  const tree={getNode:id=>{if(!nodes.has(id))nodes.set(id,{expanded:false});return nodes.get(id);},setCurrentNode:node=>{tree.current=node;}};
  const context=vm.createContext({state:scope,treeData,treeRef:{value:{getTree:()=>tree}},
    orgTreeSource:{value:[]},treeStatistics:{value:{}},countMap:{value:{}},SOURCE_BATCH_SIZE:24,
    computed:fn=>({get value(){return fn();}}),nextTick:callback=>Promise.resolve().then(callback),
    normalizeItem:row=>row,compareDatasourceUpdated:()=>0,$user:{orgRootId:'root'},console:{error:()=>{}},
    $common:{post:(route,body)=>{const request=deferred();calls.push({route,body,...request});return request.promise;}},
  });
  vm.runInContext(executable,context);
  const overview=policy=>({dataScope:policy,list:departments.map(d=>({tid:d.value}))});
  const statistics={total:size,orgCounts:Object.fromEntries(departments.map(d=>[d.value,1])),
    orgApps:Object.fromEntries(departments.map((d,i)=>[d.value,[{value:`system-${i}`,label:`系统${i}`,count:1}]]))};
  const respondTree=offset=>{
    calls[offset+1].resolve([{value:'authority',label:'机构',children:departments}]);
    calls[offset+2].resolve(statistics);
  };
  return {scope,treeData,tree,nodes,calls,overview,respondTree,load:context.actual.loadData};
}
for(const size of [20,100])for(const policy of ['ALL','ORG','OWNER'])for(const order of ['scope-first','tree-first']){
  const f=fixture(size),loading=f.load();
  assert.equal(f.calls.length,3);
  if(order==='scope-first'){f.calls[0].resolve(f.overview(policy));await Promise.resolve();f.respondTree(0);}
  else{f.respondTree(0);await new Promise(resolve=>setImmediate(resolve));
    assert.ok([...f.nodes.values()].every(node=>!node.expanded),'Do not expand systems before the scope is known');
    f.calls[0].resolve(f.overview(policy));}
  await loading;
  assert.equal(f.scope.dataScope,policy);
  assert.equal(f.nodes.get('').expanded,true);assert.equal(f.nodes.get('authority').expanded,true);
  for(let i=0;i<size;i++)assert.equal(f.nodes.get(`department-${i}`).expanded,policy!=='ALL');
  assert.equal(f.calls.length,3,'Default expansion must not issue node reads');
  // Department contents remain available for a deliberate user expansion.
  assert.equal(f.treeData.value[0].children[0].children[0].children[0].type,'app');
  f.nodes.get('department-0').expanded=true;
  assert.equal(f.calls.length,3);
  console.log(`${policy} ${size} departments, ${order}: root/organizations expanded; department expansion=${policy!=='ALL'}; requests=3`);
}
{
  const f=fixture(20),older=f.load(),current=f.load();
  f.calls[3].resolve(f.overview('OWNER'));f.respondTree(3);await current;
  f.respondTree(0);f.calls[0].resolve(f.overview('ALL'));await older;
  assert.equal(f.scope.dataScope,'OWNER');assert.equal(f.nodes.get('department-0').expanded,true);
}
{
  const f=fixture(20),loading=f.load();f.respondTree(0);f.calls[0].reject(new Error('overview unavailable'));await loading;
  assert.equal(f.scope.dataScope,'');assert.equal(f.nodes.get('department-0').expanded,false);
  assert.ok(f.scope.listError);assert.equal(f.calls.length,3);
}
console.log('Stale refresh and failed scope response cannot expand an administrator tree to business systems.');
