import type { DataTable, Task, Workspace } from './types';
import { defaultFlow, matchingMapping, definition, clone } from './rules';
import { execute, newTask } from './engine';
export function createSeed(now=Date.now()):Workspace {
 const sources=[{id:'s-record',name:'节目备案业务库',engine:'Oracle',namespace:'program_record',zone:'业务网'},{id:'s-license',name:'播出许可业务库',engine:'MySQL',namespace:'license_biz',zone:'业务网'},{id:'s-warehouse',name:'广电主题数据仓库',engine:'DM8',namespace:'dw_broadcast',zone:'数据网'},{id:'s-service',name:'共享服务资源库',engine:'PostgreSQL',namespace:'svc_resource',zone:'交换网'}];
 const entities=[['program','节目备案','节目'],['organization','播出机构','机构'],['license','许可证信息','许可'],['schedule','节目排期','排期']];
 const tables:DataTable[]=sources.flatMap((s,si)=>entities.map(([name,label,prefix],ei)=>({id:`${s.id}-${name}`,sourceId:s.id,name:si===2?`dwd_${name}`:si===3?`svc_${name}`:name,label,revision:1,fields:[
  {id:`${s.id}-${name}-id`,name:'id',label:`${prefix}编号`,type:'String' as const,nullable:false,primary:true},
  {id:`${s.id}-${name}-name`,name:'name',label:`${prefix}名称`,type:'String' as const,nullable:false},
  {id:`${s.id}-${name}-region`,name:'region_code',label:'地区编码',type:'String' as const,nullable:true},
  {id:`${s.id}-${name}-status`,name:'status',label:'业务状态',type:'String' as const,nullable:false},
  {id:`${s.id}-${name}-time`,name:'created_at',label:'创建时间',type:'Timestamp' as const,nullable:false}
 ],rows:Array.from({length:12+ei*4},(_,i)=>({id:`${name.toUpperCase()}-${String(i+1).padStart(4,'0')}`,name:`${prefix}资源 ${String(i+1).padStart(2,'0')}`,region_code:['350100','350200','350300'][i%3],status:i%4?'有效':'待复核',created_at:new Date(now-86400000*(i%6)-3600000).toISOString()}))})));
 // Explicit initial local discrepancies, not network observations.
 tables.find(t=>t.id==='s-service-license')!.rows.pop();tables.find(t=>t.id==='s-service-program')!.rows[0].name='待核对的节目名称';
 const clusters=[{id:'c-ing',name:'接入计算集群',zone:'业务网 → 数据网',state:'HEALTHY' as const,nodes:[{id:'node1',name:'ingest-01',cpu:32,memory:48,state:'ONLINE' as const},{id:'node2',name:'ingest-02',cpu:26,memory:41,state:'ONLINE' as const}],bulletins:['当前无阻断事项','资源指标为本地样例快照'],concurrency:2},
 {id:'c-sync',name:'主题同步集群',zone:'数据网',state:'HEALTHY' as const,nodes:[{id:'node3',name:'sync-01',cpu:41,memory:55,state:'ONLINE' as const},{id:'node4',name:'sync-02',cpu:29,memory:46,state:'ONLINE' as const}],bulletins:['同步队列按本地作业实时统计'],concurrency:2},
 {id:'c-cross',name:'跨网交换集群',zone:'数据网 → 交换网',state:'HEALTHY' as const,nodes:[{id:'node5',name:'bridge-01',cpu:38,memory:61,state:'ONLINE' as const}],bulletins:['跨网执行为本地状态模拟；未连接真实网闸'],concurrency:1}];
 const names=['节目备案增量接入','机构主体信息同步','播出许可目录分发','节目排期跨网传输','节目与机构多表同步','公共服务目录推送','许可变更每日接入','播出业务主题同步','地区节目资源分发','节目日排期汇聚','跨网目录回执核查','许可证内容校对'];
 let s:Workspace={schemaVersion:2,revision:0,sources,tables,tasks:[],runs:[],bills:[],clusters,strategies:[],statements:[],batches:[],registrations:[],logs:[]};
 const scenarios:Task['scenario'][]=['接入','同步','分发','跨网','多表同步','分发','接入','同步','分发','接入','跨网','同步'];
 names.forEach((name,i)=>{const ei=i%4,src=i%3===0?0:i%3===1?1:2,tgt=src<2?2:3;const a=tables[src*4+ei],b=tables[tgt*4+ei];
  const maps=[matchingMapping(a,b)];if(scenarios[i]==='多表同步')maps.push(matchingMapping(tables[src*4+(ei+1)%4],tables[tgt*4+(ei+1)%4]));
  const task=newTask(name,clusters[i%3].id,maps,defaultFlow(scenarios[i]==='跨网'),scenarios[i]);task.id=`task-${i+1}`;task.code=`HT-${String(i+1).padStart(4,'0')}`;task.owner=['林澄','周宁','陈知远'][i%3];task.createdAt=new Date(now-(12-i)*86400000).toISOString();task.updatedAt=task.createdAt;
  task.fault=i===3?'TRANSPORT':i===2?'MISSING':'NONE';if(i!==11){task.published=definition(task);task.publishedRevision=1;}s.tasks.push(task);
 });
 for(let i=0;i<8;i++){const t=now-(8-i)*3600000;s=execute(s,{type:'RUN_TASK',id:s.tasks[i].id},t);for(let step=1;step<9;step++)s=execute(s,{type:'TICK'},t+step*800);}
 const a=tables.find(t=>t.id==='s-license-license')!,b=tables.find(t=>t.id==='s-service-license')!;
 s.strategies=[{id:'strategy-1',name:'播出许可完整性盘点',sourceTableId:a.id,targetTableId:b.id,fields:matchingMapping(a,b).fields,keys:['id'],timeField:'created_at',start:'',end:'',frequency:'WEEKLY',enabled:true,revision:1,mode:'SHA1'},
 {id:'strategy-2',name:'节目服务内容比对',sourceTableId:'s-record-program',targetTableId:'s-service-program',fields:matchingMapping(tables[0],tables[12]).fields,keys:['id'],timeField:'created_at',start:'',end:'',frequency:'ONCE',enabled:true,revision:1,mode:'SHA1'}];
 s=execute(s,{type:'RUN_INVENTORY',id:'strategy-1'},now-400000);s=execute(s,{type:'RUN_INVENTORY',id:'strategy-2'},now-300000);
 s.logs=[];return clone(s);
}
