import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../../', import.meta.url), babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
const folder = new URL('lowcode/03.数据接入/', root);
const source = await fs.readFile(new URL('2.3.1 数据汇聚(data-convergence).vue', folder), 'utf8');
const wrapper = await fs.readFile(new URL('2.3.1 Hive元数据库(data-convergence-hive).vue', folder), 'utf8');
assert.match(wrapper, /<data-convergence task-category="distribution"\s*\/>/);
assert.match(source, /<access-apply[\s\S]*?:task-category="isDistribution \? 'distribution' : undefined"/);
const names = ['props','isDistribution','taskPage','taskName','taskFetchSequence','handleSearch',
  'collectDatasourceIds','buildConditions','fetchSources','taskLinkStatus','tabs','accessStatusType',
  'openTableDetail','openDatasourceDetail','loadTree','statisticsPayload','getSourceTableId','handleRejectForModify'];
const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
const ast = babel.Babel.transform(script, {ast:true,code:false,parserOpts:{sourceType:'module'}}).ast;
const code = ast.program.body.filter(node => node.type === 'VariableDeclaration' && node.declarations.some(d => names.includes(d.id.name)))
  .map(node => script.slice(node.start,node.end)).join('\n');
const variants = [
  {page: '/ods/ingest/page', tree: '/ods/ingest/stats', route: '/jr/data-convergence', tabs: ['全部','待接入','待审批','待启动','已接入'], states: ['all','0','2','3','1']},
  {category: 'distribution', page: '/ods/dist/tables', tree: '/ods/dist/tree', route: '/jr/hive-metastore', tabs: ['全部','待分发','已分发'], states: ['all','0','1']},
];
for (const variant of variants) {
  for (const count of [20,100]) {
    let listRequests=0, refreshedPage;
    const refreshes=[], ref=value=>({value});
    const rows=Array.from({length:count},(_,i)=>({tid:`table-${i}`,accessStatus:String(i%4),accessStatusText:['待接入','已接入','待审批','待启动'][i%4],tasks:[
      {tid:`first-${i}`,runtimeState:'RUNNING',runtimeLabel:'运行中',runtimeFresh:true,taskStatus:2},
      {tid:`second-${i}`,runtimeState:'STOPPED',runtimeLabel:'已停止',runtimeFresh:true,taskStatus:2},
    ]}));
    const routeCalls = [], treeRequests = [], rejectRequests = [];
    const ctx=vm.createContext({ref,computed:get=>({get value(){return get();}}),console,AbortController,TextDecoder,setTimeout,clearTimeout,
      defineProps: defs => ({taskCategory: variant.category || defs.taskCategory.default}),
      document:{hidden:false},state:{loading:false,accessStatus:'all',datasourceType:'mysql',keyword:'physical',leftSelectNode:{type:'datasource',value:'source'}},treeData:ref([{children:[]}]),$message:{error:assert.fail},
      router:{push: route => routeCalls.push(route)}, $user:{isAdmin:false, orgId:'own-org', orgRootId:'root-org'},
      orgTreeSource:ref([]),treeStatistics:ref({}),countMap:ref({}),rebuildTree:()=>{},
      tableRef:ref({refresh:async reset=>{refreshes.push(reset);refreshedPage=await ctx.actual.fetchSources({pageSize:count});}}),
      $common:{post:async(url,body)=>{listRequests++;assert.equal(url,variant.page);assert.equal(body.pageSize,count);assert.equal(body.datasourceType,"mysql");assert.equal(body.keyword,"physical");assert.equal(body.includeDatasourceTypeStatistics,true);assert.equal(body.scopeId,"source");
        const updated=structuredClone(rows);if(listRequests>1)for(const row of updated)Object.assign(row.tasks[1],{runtimeState:'RUNNING',runtimeLabel:'运行中'});
        return {datasourceTypeStatistics:{complete:true,total:10001,typeCounts:{mysql:9999,hive:2}},list:updated,total:count,runtimeRevision:listRequests>1?'v2':'v1',runtimeFresh:true,runtimeObservedAt:listRequests>1?131:1};}},
      $request:{get:()=>assert.fail('Manual task list must not subscribe to runtime events')},
    });
    vm.runInContext(code+'\nglobalThis.actual={fetchSources,handleSearch,taskLinkStatus,tabs,accessStatusType,openTableDetail,openDatasourceDetail,loadTree,handleRejectForModify};',ctx);
    const initial=await ctx.actual.fetchSources({pageSize:count});assert.equal(ctx.actual.taskLinkStatus(initial.list[0].tasks[1]).text,'已停止');
    assert.deepEqual(Array.from(ctx.actual.tabs,tab=>tab.label),variant.tabs);
    assert.deepEqual(Array.from(ctx.actual.tabs,tab=>tab.value),variant.states);
    if(!variant.category) {
      assert.equal(initial.list[0].accessStatusText,'待接入');
      assert.equal(initial.list[2].accessStatusText,'待审批');
      assert.equal(initial.list[3].accessStatusText,'待启动');
      assert.equal(ctx.actual.accessStatusType(initial.list[1]),'success');
    }
    assert.equal(ctx.state.datasourceTypeStatistics.total,10001);assert.equal(ctx.state.datasourceTypeError,"");
    await new Promise(resolve=>setTimeout(resolve,190));
    assert.equal(listRequests,1);assert.deepEqual(refreshes,[]);
    assert.equal(ctx.actual.taskLinkStatus(initial.list[0].tasks[1]).text,'已停止');
    ctx.actual.handleSearch();
    await new Promise(resolve=>setTimeout(resolve,0));
    assert.equal(listRequests,2);assert.deepEqual(refreshes,[true]);
    assert.ok(refreshedPage.list.every(row=>row.tasks.every(task=>ctx.actual.taskLinkStatus(task).text==='运行中')));
    assert.equal(ctx.actual.taskLinkStatus({runtimeFresh:false,runtimeMessage:'NiFi 连接失败'}).text,'节点连不通');
    assert.equal(ctx.actual.taskLinkStatus({runtimeFresh:false,runtimeMessage:'NiFi 认证或授权失败'}).text,'认证或授权失败');
    assert.equal(ctx.actual.taskLinkStatus(refreshedPage.list[0].tasks[0]).text,'运行中');
    const pending=[];
    ctx.$common.post=()=>new Promise(resolve=>pending.push(resolve));
    const old=ctx.actual.fetchSources({pageSize:count}),latest=ctx.actual.fetchSources({pageSize:count});
    pending[1]({list:[],total:0,datasourceTypeStatistics:{complete:true,total:99,typeCounts:{hive:99}}});await latest;
    pending[0]({list:[],total:0,datasourceTypeStatistics:{complete:true,total:1,typeCounts:{mysql:1}}});await old;
    assert.equal(ctx.state.datasourceTypeStatistics.total,99);assert.equal(ctx.state.loading,false);
    ctx.$message.error=()=>{};ctx.console={...console,error(){}};
    ctx.$common.post=async()=>{throw new Error("offline")};await ctx.actual.fetchSources({pageSize:count});
    assert.equal(ctx.state.datasourceTypeStatistics,null);assert.ok(ctx.state.datasourceTypeError);assert.equal(ctx.state.loading,false);
    ctx.actual.openTableDetail({tid:'table-1',tableName:'source'});
    ctx.actual.openDatasourceDetail({datasourceId:'db-1',datasourceName:'data'});
    assert.deepEqual(routeCalls.map(route=>[route.path,route.query.from,route.query.type,route.query.id]), [
      [`${variant.route}/detail`,`${variant.route}/list`,'table','table-1'],
      [`${variant.route}/detail`,`${variant.route}/list`,'db','db-1'],
    ]);
    ctx.$common.post=async(url,body)=>{
      treeRequests.push([url,JSON.parse(JSON.stringify(body))]);
      return url===variant.tree ? {total:count} : [];
    };
    await ctx.actual.loadTree();
    assert.deepEqual(treeRequests,[
      ['/sym/org/tree',{parentId:'root-org'}],
      [variant.tree,{keyword:'',dbType:'',orgIds:['own-org']}],
    ]);
    ctx.$common.post=async(url,body)=>{rejectRequests.push([url,JSON.parse(JSON.stringify(body))]);};
    ctx.ElMessageBox={confirm:async()=>{throw 'cancel';}};
    await ctx.actual.handleRejectForModify({sourceTableId:'source',datasourceId:'db-1'});
    assert.equal(rejectRequests.length,0);
    let confirms=0;
    ctx.ElMessageBox.confirm=async()=>{confirms++;};
    ctx.tableRef.value={refresh:()=>{}};
    ctx.$message.success=()=>{};
    await ctx.actual.handleRejectForModify({sourceTableId:'source',datasourceId:'db-1'});
    assert.equal(confirms,variant.category ? 1 : 0);
    assert.deepEqual(rejectRequests.filter(([url])=>url==='/ods/ingest/reject'),variant.category ? [
      ['/ods/ingest/reject',{tableId:'source',datasourceId:'db-1'}],
    ] : []);
    console.log(`${variant.category || 'ingestion'}: rows=${count}; initial page=1, idle=0 reads, search=1 page read; unreachable state stays on its task`);
  }
  assert.doesNotMatch(script,/startRuntimeStream|visibilitychange|onActivated|setInterval|setTimeout|\/nifi\/run\/events/);
  assert.match(source,/accessStatus: \"all\"/);
}
