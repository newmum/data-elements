import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';
const root = new URL('../../', import.meta.url);
const source = await fs.readFile(new URL('lowcode/01.系统门户/01.数据源管理/01.工作台(my-workbench).vue', root), 'utf8');
const compiler = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), compiler);
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
const compiled = compiler.Babel.transform(script, {filename:'workbench.ts', presets:['typescript'], plugins:[()=>({visitor:{ImportDeclaration(path){path.remove();}}})]}).code;
const exposed = 'loadOverview,loadSection,retrySection,sectionState,activeScope,canViewAll,scopeOptions,view,loadError,switchScope,list,openList,fetchDrawer,queueSelected,selectedPending,enterRegistration,percent,openStage,openType,selectMetric,sortPending,refreshing';
function fixture() {
  const requests=[], messages=[], registrations=[], navigations=[];
  const state={respond:async()=>({})};
  const service={post:async(route,body)=>{requests.push({route,body});const data=await state.respond(route,body);if(!route.endsWith('/overview'))return data;const section=body.section;return {...data,section,counts:Object.fromEntries(Object.entries(data.counts||{}).filter(([key])=>section==='registration'?!/^(access|added|changed|deleted)/.test(key):section==='tasks'?key.startsWith('access'):section==='changes'?/^(added|changed|deleted)/.test(key):false))};}};
  const ref=value=>({value});
  const api=new Function('ref','computed','reactive','watch','onMounted','onBeforeUnmount','useRouter','useRegisterModal','defineOptions','$common','$message','ExcelJS', compiled+`\nreturn {${exposed}};`)(ref, fn=>({get value(){return fn();}}), value=>value, ()=>{}, ()=>{}, ()=>{}, ()=>({push:value=>navigations.push(value)}), ()=>({open:ref(false),openRegisterModal:value=>registrations.push(value)}), ()=>{}, service, {success:m=>messages.push(m),error:m=>messages.push(m)}, {});
  return {api,state,requests,messages,registrations,navigations};
}
const overview=(count,scope='all',admin=true)=>({mode:scope,canViewAll:admin,counts:{tables:count,marked:0,registered:0},stages:[],types:[],pending:Array.from({length:Math.min(5,count)},(_,i)=>({id:String(i),datasourceId:'source',tableName:'表'+i})),health:[],tasks:[],changes:{available:true,list:[],counts:{added:0,modified:0,deleted:0}},governance:{available:true},unavailable:[]});
{
  const f=fixture();const waiting=[];
  f.state.respond=()=>new Promise(resolve=>{waiting.push(resolve);});
  const pending=f.api.loadOverview('all');
  assert.equal(f.api.refreshing.value,true);
  f.api.selectMetric({key:'markStatus'});assert.equal(f.navigations.length,0);
  waiting.forEach(finish=>finish(overview(20)));await pending;assert.equal(f.api.refreshing.value,false);
  f.api.selectMetric({key:'markStatus'});
  assert.deepEqual(f.navigations[0],{path:'/datasource-manage/index',query:{filter:'unannotated'}});
  f.api.selectMetric({key:'tableRegistration'});
  assert.deepEqual(f.navigations[1],{path:'/datasource-manage/index',query:{filter:'incomplete',metric:'tableIssues'}});
  assert.equal(f.api.list.visible,false);assert.equal(f.requests.length,5);
}
for(const count of [20,100]) {
  const f=fixture();f.state.respond=async()=>overview(count);
  await f.api.loadOverview('auto');assert.equal(f.requests.length,5);assert.equal(f.api.view.counts.tables,count);
  f.api.selectedPending.value=['0','1'];f.api.queueSelected();
  f.state.respond=async()=>({list:[{id:'0'},{id:'1'}],total:2});
  assert.equal((await f.api.fetchDrawer({pageNo:1,pageSize:count})).total,2);
  assert.equal(f.requests.length,6);assert.deepEqual(f.requests[5].body.ids,['0','1']);
  f.state.respond=async()=>overview(count/2,'mine',false);
  await f.api.loadOverview('mine');assert.deepEqual(f.api.scopeOptions.value.map(v=>v.value),['mine']);assert.equal(f.messages.length,0);
  f.state.respond=async()=>{throw new Error('暂时无法读取');};
  await f.api.loadOverview('mine');assert.match(f.api.loadError.value,/无法读取/);assert.ok(Object.values(f.api.sectionState).every(section=>section.error));
  console.log(`${count} rows: 5 fixed independent overview requests; 1 explicit bounded selection request; no row reads or scope toast`);
}
{
  const f=fixture();f.state.respond=async()=>overview(20);
  await f.api.loadOverview('all');f.requests.length=0;
  await f.api.sortPending('displayName');assert.equal(f.requests[0].body.pendingSortDir,'asc');
  await f.api.sortPending('displayName');assert.equal(f.requests[1].body.pendingSortDir,'desc');
  await f.api.sortPending('displayName');assert.equal(f.requests[2].body.pendingSortField,'');assert.equal(f.requests[2].body.pendingSortDir,'');
}
{
  const f=fixture();const data=overview(1);data.tasks=[{tid:'task',dbName:'来源库',runtimeState:'error'}];data.health=[{id:'source',showConnect:0,connectionStatus:'failed'}];
  f.state.respond=async()=>data;await f.api.loadOverview('all');assert.equal(f.api.view.tasks[0].source,'来源库');assert.equal(f.api.view.health[0].statusText,'未提供连接');
}
{
  const f=fixture();const waiting=[];
  f.state.respond=()=>new Promise(resolve=>waiting.push(resolve));
  const first=f.api.loadOverview('all'), second=f.api.loadOverview('mine');
  waiting.slice(5).forEach(finish=>finish(overview(7,'mine',false)));await second;waiting.slice(0,5).forEach(finish=>finish(overview(99,'all',true)));await first;
  assert.equal(f.api.view.counts.tables,7);assert.equal(f.api.activeScope.value,'mine');assert.equal(f.api.canViewAll.value,false);
}
{
  const f=fixture();const waiting=new Map();
  f.state.respond=(_route,body)=>new Promise((resolve,reject)=>waiting.set(body.section,{resolve,reject}));
  const pending=f.api.loadOverview('all');assert.equal(f.requests.length,5);
  waiting.get('registration').resolve(overview(100));await new Promise(resolve=>setImmediate(resolve));
  assert.equal(f.api.view.counts.tables,100);assert.equal(f.api.sectionState.registration.loading,false);
  assert.equal(f.api.sectionState.governance.loading,true);assert.equal(f.api.refreshing.value,true);
  f.api.selectMetric({key:'markStatus'});assert.equal(f.navigations.length,1,'ready cards work while another section is slow');
  waiting.get('tasks').resolve({...overview(0),counts:{accessTasks:3}});
  waiting.get('health').resolve(overview(0));waiting.get('changes').resolve(overview(0));
  waiting.get('governance').reject(new Error('治理读取超时'));await pending;
  assert.equal(f.api.view.counts.tables,100);assert.equal(f.api.view.counts.accessTasks,3);
  assert.match(f.api.sectionState.governance.error,/超时/);assert.equal(f.api.sectionState.registration.error,'');
  f.state.respond=async()=>({...overview(0),governance:{available:true,standardFields:9}});
  await f.api.retrySection('governance');assert.equal(f.requests.length,6);
  assert.equal(f.api.view.standardFields,9);assert.equal(f.api.view.counts.tables,100);
  assert.equal(f.api.sectionState.governance.error,'');
  f.requests.length=0;await f.api.sortPending('createdTime');
  assert.equal(f.requests.length,1);assert.equal(f.requests[0].body.section,'registration');
  console.log('Independent partial rendering, local retry, usable ready cards and bounded sorting passed.');
}
{
  const f=fixture();f.state.respond=async()=>({tid:'source'});
  f.api.openStage({name:'登记字典表',step:2});await f.api.enterRegistration({id:'source'});
  assert.equal(f.registrations[0].registerData.currentStep,2);
  assert.equal(f.requests.length,1);assert.equal(f.requests[0].route,'/dst/database/detail');
  f.api.openList('未登记表',{kind:'tables',step:3});
  await f.api.enterRegistration({datasourceId:'source',annotated:0,businessType:'业务表'});assert.equal(f.registrations[1].registerData.currentStep,1);
  await f.api.enterRegistration({datasourceId:'source',annotated:1,businessType:'字典表'});assert.equal(f.registrations[2].registerData.currentStep,2);
  await f.api.enterRegistration({datasourceId:'source',annotated:1,businessType:'业务表'});assert.equal(f.registrations[3].registerData.currentStep,3);
  assert.equal(f.api.percent(3,0),0);assert.equal(f.api.percent(120,100),100);
}
console.log('Scope visibility, stale responses, inline failures, explicit registration entry and zero/overflow chart values passed.');
