import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';
import test from 'node:test';
const source=await fs.readFile(new URL('../../lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/04.登记内容审查(register-confirm).vue',import.meta.url),'utf8');
const block=source.slice(source.indexOf('// 动态任务状态只读取本页ID'),source.indexOf('// 普通数据源按数据库类型'));
function setup(count=20) {
 const calls=[],messages=[],pending=[];
const context={ref:value=>({value}),computed:getter=>({get value(){return getter();}}),ElMessageBox:{confirm:async()=>{}},
 sourceTid:{value:'source'},sourceDetail:{value:{assetStatus:0}},visibleTables:{value:Array.from({length:count},(_,i)=>({tid:String(i)}))},
 firstNonEmpty:(...values)=>values.find(v=>v!=null&&v!==''),requestWithTimeout:executor=>executor(),
 $common:{post:(url,body)=>{calls.push({url,body});return new Promise((resolve,reject)=>pending.push({resolve,reject}));}},
 $message:{success:message=>messages.push(message),error:message=>messages.push(message)},
 onMounted(){},onUnmounted(){},onActivated(){},onDeactivated(){},setTimeout,clearTimeout,document:{hidden:false},
 findTableAccessTaskRow:table=>context.api?.rows.value.find(row=>row.tableId===String(table?.tid)),
 };
 vm.createContext(context);vm.runInContext(block+`;globalThis.api={refresh:refreshAccessSummary,rows:accessTaskRows,totals:accessTotals,loaded:accessTaskLoaded,error:accessTaskError,loading:accessTaskLoading,dialogTable:accessDialogTable,submit:submitRegistrationAccess,submitting:accessSubmitting,control:controlRegistrationTask,command:accessCommand,phase:accessCommandPhase,commandError:accessCommandError,title:registrationTaskTitle};`,context);
 return {context,api:context.api,calls,pending,messages};
}
function response(ids,total=999){return {list:ids.map(tableId=>({tableId,canApply:true,accessStatus:'0',tasks:[],taskCount:0})),totals:{running:total},runtimeFresh:true,runtimeRevision:'r'};}
test('20/100 rows use one bounded request, full totals are server facts',async()=>{
 for(const size of [20,100]){const s=setup(size),done=s.api.refresh();assert.equal(s.calls.length,1);assert.equal(s.calls[0].body.tableIds.length,size);s.pending.shift().resolve(response(s.calls[0].body.tableIds));await done;assert.equal(s.api.rows.value.length,size);assert.equal(s.api.totals.value.running,999);assert.equal(s.api.loading.value,false);}
});
test('late response from old source cannot replace current source or current page',async()=>{
 const s=setup(1),old=s.api.refresh();s.context.sourceTid.value='new-source';s.context.visibleTables.value=[{tid:'new'}];const fresh=s.api.refresh();s.pending[1].resolve(response(['new']));await fresh;s.pending[0].resolve(response(['0']));await old;assert.equal(s.api.rows.value[0].tableId,'new');
});
test('foreign, missing and duplicate response IDs preserve known rows and expose retry error',async()=>{
 for(const ids of [['bad'],[],['0','0']]){const s=setup(1);s.api.rows.value=[{tableId:'known'}];const done=s.api.refresh();s.pending[0].resolve(response(ids));await done;assert.equal(s.api.rows.value[0].tableId,'known');assert.match(s.api.error.value,/不完整/);assert.equal(s.api.loading.value,false);}
});
test('failed refresh retains rows and never shows false success',async()=>{
 const s=setup(1);s.api.rows.value=[{tableId:'known'}];const done=s.api.refresh();s.pending[0].reject(new Error('offline'));await done;assert.equal(s.api.rows.value[0].tableId,'known');assert.equal(s.api.error.value,'offline');assert.equal(s.messages.length,0);
});
test('double submit sends one command and refreshes entire page once',async()=>{
 const s=setup(20);s.api.rows.value=response(s.context.visibleTables.value.map(row=>row.tid)).list;s.api.dialogTable.value={tid:'0'};const done=s.api.submit();await s.api.submit();assert.equal(s.calls.length,1);assert.match(s.calls[0].url,/registrationAccessApply$/);s.pending[0].resolve({datasourceAssetStatus:1});await new Promise(setImmediate);assert.equal(s.calls.length,2);assert.equal(s.calls[1].body.tableIds.length,20);s.pending[1].resolve(response(s.calls[1].body.tableIds));await done;assert.equal(s.api.submitting.value,false);assert.equal(s.context.sourceDetail.value.assetStatus,1);assert.equal(s.messages.length,1);
});

test('undeployed task starts once, blocks duplicate confirmation and refreshes page once',async()=>{
 const s=setup(20),task={tid:'task',canStart:true,requiresDeployment:true,taskName:'业务接入 → 实验库 / ods_table',targetDatasourceName:'实验库',targetTableName:'ods_table'};
 s.api.dialogTable.value={tid:'0'};s.api.rows.value=response(s.context.visibleTables.value.map(row=>row.tid)).list;
 let confirm; s.context.ElMessageBox.confirm=()=>new Promise(resolve=>{confirm=resolve;});
 const done=s.api.control(task,'start');await s.api.control(task,'start');assert.equal(s.calls.length,0);assert.equal(s.api.command.value,'task');
 confirm();await new Promise(setImmediate);assert.equal(s.calls.length,1);assert.deepEqual(JSON.parse(JSON.stringify(s.calls[0].body)),{datasourceId:'source',tableId:'0',taskId:'task',action:'start'});
 s.pending[0].resolve({accepted:true,deploymentPerformed:true});await new Promise(setImmediate);assert.equal(s.calls.length,2);assert.equal(s.calls[1].body.tableIds.length,20);
 s.pending[1].resolve(response(s.calls[1].body.tableIds));await done;assert.equal(s.api.command.value,'');assert.match(s.messages[0],/部署完成/);assert.equal(s.api.title(task),'业务接入');
});

test('deployment failure stays on its card, refreshes state and never reports success',async()=>{
 const s=setup(1),task={tid:'task',canStart:true,requiresDeployment:true};s.api.dialogTable.value={tid:'0'};
 const done=s.api.control(task,'start');await new Promise(setImmediate);s.pending[0].reject(new Error('部署未完成：来源连接不可用'));await done;
 assert.equal(s.api.command.value,'');assert.equal(s.api.commandError.value.taskId,'task');assert.match(s.api.commandError.value.message,/来源连接不可用/);assert.equal(s.messages.length,1);assert.equal(s.calls.length,2);
 s.pending[1].resolve(response(['0']));await new Promise(setImmediate);
});

test('source change while confirming cancels the command and task title keeps unrelated arrows',async()=>{
 const s=setup(1);s.api.dialogTable.value={tid:'0'};
 s.context.ElMessageBox.confirm=async()=>{s.context.sourceTid.value='another';};
 await s.api.control({tid:'task',canStart:true},'start');assert.equal(s.calls.length,0);assert.equal(s.api.command.value,'');
 assert.equal(s.api.title({taskName:'业务 A → 业务 B',targetDatasourceName:'实验库',targetTableName:'ods'}),'业务 A → 业务 B');
});
