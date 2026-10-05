import type { BatchJob, Bill, Cluster, Flow, InventoryStrategy, Row, Run, TableMapping, Task, Workspace } from './types';
import { billCanClose, clone, compareRows, definition, nextDaily, project, rowSignature, sha1, uid, validateTask } from './rules';
export type Action =
 | {type:'SAVE_TASK'; task:Task; expected?:number}
 | {type:'LINK_NIFI_PIPELINE'; id:string; pipelineId:string}
 | {type:'PUBLISH_TASK'|'COPY_TASK'|'ARCHIVE_TASK'|'PAUSE_TASK'|'RUN_TASK'; id:string}
 | {type:'CANCEL_RUN'|'RETRY_RUN'|'RETRY_BATCH'|'CANCEL_BATCH'; id:string}
 | {type:'REGISTER'; task:Task; flowId:string}
 | {type:'CREATE_BATCH'; name:string; clusterId:string; mappings:TableMapping[]; phases:number[]}
 | {type:'SAVE_CLUSTER'; cluster:Cluster}
 | {type:'SAVE_STRATEGY'; strategy:InventoryStrategy}
 | {type:'RUN_INVENTORY'; id:string}
 | {type:'WRITEOFF'; id:string; reason:string; by:string}
 | {type:'TICK'};
export function newTask(name:string,clusterId:string,mappings:TableMapping[],flow:Flow,scenario:Task['scenario']='接入'):Task {
 const id=uid('task');return {id,code:`HT-${id.slice(-6).toUpperCase()}`,name,scenario,owner:'集成工程师',clusterId,mappings,flow,revision:1,paused:false,archived:false,schedule:{kind:'MANUAL',phaseMinute:120,timezone:'Asia/Shanghai'},createdAt:new Date().toISOString(),updatedAt:new Date().toISOString(),fault:'NONE'};
}
function fail(message:string):never{throw new Error(message);}
function taskAt(s:Workspace,id:string){return s.tasks.find(t=>t.id===id)||fail('任务不存在或已删除');}
function insertRun(s:Workspace,task:Task,now:number,retryOf?:string){
 if(task.archived||task.paused)fail('任务已归档或暂停，不能执行');
 if(!task.published)fail('请先校验并发布任务版本');
 if(s.runs.some(r=>r.taskId===task.id&&['QUEUED','RUNNING'].includes(r.status)))fail('该任务已有待完成运行，请勿重复触发');
 const checks=validateTask(task.published,s);if(checks.some(c=>c.level==='error'))fail(checks.map(c=>c.message).join('；'));
 const cluster=s.clusters.find(c=>c.id===task.published!.clusterId);if(!cluster||cluster.state!=='HEALTHY'||!cluster.nodes.some(n=>n.state==='ONLINE'))fail('运行集群暂不可用');
 const def=clone(task.published);const iso=new Date(now).toISOString();
 const items=def.mappings.map(mapping=>{const table=s.tables.find(t=>t.id===mapping.sourceTableId)!;const sourceRows=clone(table.rows.slice(0,1000));const expectedRows=sourceRows.map(r=>project(r,mapping.fields));return {id:uid('ri'),mapping:clone(mapping),sourceRows,expectedRows,targetRows:[] as Row[],status:'PENDING' as const,input:sourceRows.length,expected:expectedRows.length,written:0,rejected:0};});
 const run:Run={id:uid('run'),taskId:task.id,task:def,status:'QUEUED',stage:0,startedAt:iso,items,logs:[{at:iso,level:'INFO',message:`已冻结任务 v${def.revision} 与本地来源样例；等待执行`}],retryOf,nextTick:now+500};s.runs.unshift(run);return run;
}
function finishItem(s:Workspace,run:Run,index:number,now:number){
 const item=run.items[index]; if(item.status!=='PENDING')return;
 const target=s.tables.find(t=>t.id===item.mapping.targetTableId);const iso=new Date(now).toISOString();
 if(!target){item.status='FAILED';item.error='目标表快照不存在';item.rejected=item.expected;return;}
 let received=clone(item.expectedRows);const fault=run.task.fault;
 if(fault==='TRANSPORT'){received=[];item.status='FAILED';item.error='本地传输场景：等待接收方确认超时';}
 else {if(fault==='MISSING')received=received.slice(0,-1);if(fault==='CONTENT'&&received.length){const field=item.mapping.fields.find(f=>!target.fields.find(t=>t.name===f.targetField)?.primary)?.targetField;if(field)received[0][field]='本地差异值';}item.status='SUCCEEDED';}
 const keys=target.fields.filter(f=>f.primary).map(f=>f.name);const fields=item.mapping.fields.map(f=>f.targetField);
 // Validate nonnullable fields before committing. Fail a table atomically rather than silently writing partial invalid rows.
 const invalid=received.filter(r=>target.fields.some(f=>!f.nullable&&(r[f.name]===null||r[f.name]===undefined)));
 if(invalid.length){item.status='FAILED';item.error='目标必填字段含空值';received=[];}
 if(item.status==='SUCCEEDED'){
  if(item.mapping.writeMode==='UPSERT'){const map=new Map(target.rows.map(r=>[rowSignature(r,keys),r]));for(const row of received)map.set(rowSignature(row,keys),row);target.rows=[...map.values()];}
  else {const existing=new Set(target.rows.map(r=>rowSignature(r,keys))); if(keys.length&&received.some(r=>existing.has(rowSignature(r,keys)))) {item.status='FAILED';item.error='追加模式发生主键冲突，目标未写入';received=[];}else target.rows.push(...received);}
 }
 item.targetRows=clone(received);item.written=received.length;item.rejected=item.expected-item.written;
 const diffs=keys.length?compareRows(item.expectedRows,received,keys,fields):[];
 const bill:Bill={id:uid('bill'),taskId:run.taskId,runId:run.id,mappingId:item.mapping.id,sourceTableId:item.mapping.sourceTableId,targetTableId:item.mapping.targetTableId,createdAt:iso,input:item.input,expected:item.expected,received:item.written,status:item.status==='FAILED'?'INCOMPLETE':item.expected===item.written?'MATCH':'DIFFERENT',differences:diffs, evidenceHash:sha1(JSON.stringify(item.expectedRows)+JSON.stringify(item.mapping.fields))};
 // Instant statements use batch counts; content differences are reserved for inventory comparison.
 s.bills.unshift(bill);run.logs.push({at:iso,level:item.status==='FAILED'?'ERROR':bill.status==='MATCH'?'INFO':'WARN',message:`${target.label}：查询 ${item.input} / 预期 ${item.expected} / 已确认 ${item.written} 条${item.error?'；'+item.error:''}`,objectId:item.id});
}
function tick(s:Workspace,now:number){
 const iso=new Date(now).toISOString();
 for(const t of s.tasks){if(t.schedule.kind==='DAILY'&&!t.paused&&!t.archived&&t.published&&t.schedule.nextAt&&Date.parse(t.schedule.nextAt)<=now){
  if(!s.runs.some(r=>r.taskId===t.id&&['QUEUED','RUNNING'].includes(r.status))){try{insertRun(s,t,now);}catch(error){s.logs.unshift({at:iso,level:'WARN',message:`${t.name} 调度未运行：${String(error)}`});}}
  t.schedule.nextAt=nextDaily(t.schedule.phaseMinute,now);
 }}
 for(const run of s.runs.filter(r=>['QUEUED','RUNNING'].includes(r.status))){
  if(run.nextTick>now)continue;
  if(run.cancelRequested){run.status='CANCELLED';run.finishedAt=iso;run.logs.push({at:iso,level:'WARN',message:'已取消未执行阶段；已经提交的表级结果保留'});continue;}
  if(run.status==='QUEUED'){const c=s.clusters.find(c=>c.id===run.task.clusterId); if(!c||c.state!=='HEALTHY'||!c.nodes.some(n=>n.state==='ONLINE'))continue;
   if(s.runs.filter(r=>r.task.clusterId===c.id&&r.status==='RUNNING').length>=c.concurrency)continue;
   run.status='RUNNING';run.stage=1;run.logs.push({at:iso,level:'INFO',message:'读取已冻结的本地记录快照'});
  }else if(run.stage<3){run.stage++;run.logs.push({at:iso,level:'INFO',message:run.stage===2?'应用已确认的字段映射和白名单转换':'进入表级写入；等待持久化确认'});}
  else if(run.stage===3){const i=run.items.findIndex(i=>i.status==='PENDING');if(i>=0)finishItem(s,run,i,now);if(run.items.every(i=>i.status!=='PENDING'))run.stage=4;}
  else{run.status=run.items.some(i=>i.status==='FAILED')?'FAILED':'SUCCEEDED';run.finishedAt=iso;run.logs.push({at:iso,level:run.status==='FAILED'?'ERROR':'INFO',message:'运行结束；运行状态与对账结果分别记录'});}
  run.nextTick=now+700;
 }
 for(const batch of s.batches.filter(b=>b.status==='RUNNING'&&b.nextTick<=now)){
  const item=batch.items.find(i=>i.status==='PENDING');if(item){try{
    const source=s.tables.find(t=>t.id===item.mapping.sourceTableId)||fail('来源已删除');
    const name=`${source.label} · 批量同步`;const t=newTask(name,batch.clusterId,[clone(item.mapping)],flowForMap(),'同步');
    const checks=validateTask(t,s);if(checks.some(c=>c.level==='error'))fail(checks.map(c=>c.message).join('；'));
    t.schedule={kind:'DAILY',phaseMinute:item.phaseMinute,timezone:'Asia/Shanghai',nextAt:nextDaily(item.phaseMinute,now)};
    // Created tasks stay drafts. No unapproved daily executions.
    s.tasks.unshift(t);item.status='SUCCEEDED';item.taskId=t.id;batch.logs.push({at:iso,level:'INFO',message:`已创建 ${t.name}，每日相位 ${item.phaseMinute} 分钟；待发布`});
   }catch(e){item.status='FAILED';item.error=String(e);batch.logs.push({at:iso,level:'ERROR',message:String(e)});}
  }
  if(batch.items.every(i=>i.status!=='PENDING'))batch.status=batch.items.some(i=>i.status==='FAILED')?'PARTIAL':'SUCCEEDED';batch.nextTick=now+450;
 }
}
function flowForMap():Flow {const a=uid('n'),b=uid('n'),c=uid('n');return {nodes:[{id:a,kind:'read',name:'来源查询',x:80,y:180,config:{}},{id:b,kind:'transform',name:'字段转换',x:350,y:180,config:{}},{id:c,kind:'write',name:'目标写入',x:620,y:180,config:{}}],edges:[{id:uid('e'),source:a,target:b,outlet:'success'},{id:uid('e'),source:b,target:c,outlet:'success'}]};}
export function execute(state:Workspace,action:Action,now=Date.now()):Workspace {
 const s=clone(state),iso=new Date(now).toISOString();
 switch(action.type){
 case 'SAVE_TASK':{const t=clone(action.task),old=s.tasks.find(x=>x.id===t.id);
   if(!t.name.trim()||t.name.length>80)fail('任务名称需要 1–80 个字符');if(old?.archived)fail('已归档任务不可编辑');if(!/^[A-Za-z][A-Za-z0-9_-]{1,39}$/.test(t.code))fail('任务代码需为字母开头的 2–40 位字母、数字、下划线或短横线');if(t.schedule.kind==='DAILY'&&(!Number.isInteger(t.schedule.phaseMinute)||t.schedule.phaseMinute<0||t.schedule.phaseMinute>1439))fail('每日相位必须是 0–1439 分钟');if(s.tasks.some(x=>x.id!==t.id&&x.code.toLowerCase()===t.code.toLowerCase()))fail('任务代码重复');
   if(old&&old.revision!==action.expected)fail('任务已在其他页面更新，请重新打开后修改');
   t.revision=old?old.revision+1:1;t.updatedAt=iso;t.published=old?.published;t.publishedRevision=old?.publishedRevision;t.nifiPipelineId=old?.nifiPipelineId;
   if(t.schedule.kind==='DAILY')t.schedule.nextAt=nextDaily(t.schedule.phaseMinute,now);
   if(old)s.tasks[s.tasks.indexOf(old)]=t;else s.tasks.unshift(t);break;}
 case 'LINK_NIFI_PIPELINE':{const t=taskAt(s,action.id);const pipelineId=action.pipelineId.trim();
   if(!pipelineId||!/^[A-Za-z0-9_-]{1,100}$/.test(pipelineId))fail('NiFi 流程 ID 无效');
   if(t.archived)fail('已归档任务不可关联 NiFi 流程');
   if(t.nifiPipelineId&&t.nifiPipelineId!==pipelineId)fail('任务已经关联其他 NiFi 流程');
   if(!t.nifiPipelineId){t.nifiPipelineId=pipelineId;t.revision++;t.updatedAt=iso;}
   break;}
 case 'PUBLISH_TASK':{const t=taskAt(s,action.id);if(t.archived)fail('已归档任务不可发布');const errors=validateTask(t,s).filter(c=>c.level==='error');if(errors.length)fail(errors.map(e=>e.message).join('；'));t.published=definition(t);t.publishedRevision=t.revision;if(t.schedule.kind==='DAILY')t.schedule.nextAt=nextDaily(t.schedule.phaseMinute,now);break;}
 case 'COPY_TASK':{const from=taskAt(s,action.id),t=clone(from);t.id=uid('task');t.code=`HT-${t.id.slice(-6).toUpperCase()}`;t.name+=' · 副本';t.revision=1;delete t.published;delete t.publishedRevision;delete t.nifiPipelineId;t.schedule={...t.schedule,kind:'MANUAL',nextAt:undefined};t.paused=false;t.archived=false;t.createdAt=iso;t.updatedAt=iso;s.tasks.unshift(t);break;}
 case 'ARCHIVE_TASK':{const t=taskAt(s,action.id);if(s.runs.some(r=>r.taskId===t.id&&['QUEUED','RUNNING'].includes(r.status)))fail('请先停止当前运行再归档');t.archived=true;t.paused=true;break;}
 case 'PAUSE_TASK':{const t=taskAt(s,action.id);t.paused=!t.paused;break;}
 case 'RUN_TASK':insertRun(s,taskAt(s,action.id),now);break;
 case 'CANCEL_RUN':{const r=s.runs.find(r=>r.id===action.id)||fail('运行不存在');if(!['QUEUED','RUNNING'].includes(r.status))fail('运行已结束');r.cancelRequested=true;break;}
 case 'RETRY_RUN':{const r=s.runs.find(r=>r.id===action.id)||fail('运行不存在');if(['QUEUED','RUNNING'].includes(r.status))fail('运行尚未结束');insertRun(s,taskAt(s,r.taskId),now,r.id);break;}
 case 'REGISTER':{if(!action.flowId.trim())fail('缺少流程 ID');if(s.registrations.some(r=>r.flowId===action.flowId))fail('此流程已登记，请编辑已有任务，不能重复创建');const t=clone(action.task);const errors=validateTask(t,s).filter(e=>e.level==='error');if(errors.length)fail(errors.map(e=>e.message).join('；'));if(s.tasks.some(x=>x.id===t.id))fail('任务身份已存在');s.tasks.unshift(t);s.registrations.unshift({id:uid('reg'),flowId:action.flowId,taskId:t.id,at:iso,revision:1});break;}
 case 'CREATE_BATCH':{if(!action.mappings.length)fail('至少选择一组匹配表');if(!s.clusters.some(c=>c.id===action.clusterId))fail('请选择集群');const batch:BatchJob={id:uid('batch'),name:action.name,clusterId:action.clusterId,createdAt:iso,status:'RUNNING',items:action.mappings.map((mapping,i)=>({mapping:clone(mapping),phaseMinute:action.phases[i]??120,status:'PENDING'})),logs:[],nextTick:now};s.batches.unshift(batch);break;}
 case 'CANCEL_BATCH':{const b=s.batches.find(b=>b.id===action.id)||fail('作业不存在');if(b.status!=='RUNNING')fail('作业已结束');b.status='CANCELLED';break;}
 case 'RETRY_BATCH':{const b=s.batches.find(b=>b.id===action.id)||fail('作业不存在');if(b.status==='RUNNING')fail('作业正在运行');b.items.filter(i=>i.status==='FAILED').forEach(i=>{i.status='PENDING';delete i.error;});if(!b.items.some(i=>i.status==='PENDING'))fail('没有可重试项');b.status='RUNNING';break;}
 case 'SAVE_CLUSTER':{const c=clone(action.cluster);if(!c.name.trim()||c.concurrency<1||c.concurrency>32)fail('请填写集群名称和 1–32 并发数');const i=s.clusters.findIndex(x=>x.id===c.id);if(i<0)s.clusters.push(c);else s.clusters[i]=c;break;}
 case 'SAVE_STRATEGY':{const st=clone(action.strategy);if(!st.name.trim()||!st.keys.length||!st.fields.length)fail('请填写策略名、主键和比较字段');if(!s.tables.find(t=>t.id===st.sourceTableId)||!s.tables.find(t=>t.id===st.targetTableId))fail('请选择有效来源与目标');if(st.start&&(!Number.isFinite(Date.parse(st.start))||!Number.isFinite(Date.parse(st.end))||Date.parse(st.start)>=Date.parse(st.end)))fail('结束时间必须晚于开始时间');if(st.start&&!st.timeField)fail('时间区间需要创建时间字段');const i=s.strategies.findIndex(x=>x.id===st.id);st.revision=i<0?1:s.strategies[i].revision+1;if(i<0)s.strategies.unshift(st);else s.strategies[i]=st;break;}
 case 'RUN_INVENTORY':{const st=s.strategies.find(x=>x.id===action.id)||fail('策略不存在');if(!st.enabled)fail('策略已停用');const a=s.tables.find(t=>t.id===st.sourceTableId)||fail('来源不存在'),b=s.tables.find(t=>t.id===st.targetTableId)||fail('目标不存在');
  const filter=(rows:Row[],field:string)=>st.start?rows.filter(r=>{const t=Date.parse(String(r[field]));return t>=Date.parse(st.start)&&t<Date.parse(st.end);}):rows;
  const targetTime=st.fields.find(m=>m.sourceField===st.timeField)?.targetField||st.timeField;
  const left=filter(a.rows,st.timeField).map(r=>project(r,st.fields)),right=filter(b.rows,targetTime);const targetKeys=st.keys.map(k=>st.fields.find(m=>m.sourceField===k)?.targetField||'');
  const reason=targetKeys.some(k=>!k)||st.keys.some(k=>!a.fields.some(f=>f.name===k))?'主键字段未完整映射':undefined;
  const allDiff=reason?[]:compareRows(left,right,targetKeys,st.mode==='SHA1'?st.fields.map(m=>m.targetField):[]);const blocked=!!reason||allDiff.some(d=>d.kind==='DUPLICATE'||d.kind==='NULL_KEY');
  const differences=st.mode==='COUNT'&&!blocked?(left.length===right.length?[]:[{key:'总行数',kind:'MISSING' as const,expected:String(left.length),actual:String(right.length)}]):allDiff;
  s.statements.unshift({id:uid('inv'),strategyId:st.id,strategy:clone(st),createdAt:iso,status:blocked?'BLOCKED':differences.length?'DIFFERENT':'MATCH',sourceCount:left.length,targetCount:right.length,differences,hashAlgorithm:'SHA-1',reason:reason||(blocked?'主键重复或为空，无法可靠配对':undefined)});break;}
 case 'WRITEOFF':{const b=s.bills.find(b=>b.id===action.id)||fail('账单不存在');if(b.writeoff)fail('账单已核销');if(action.reason.trim().length<4)fail('请填写至少 4 个字符的核销说明');if(!billCanClose(b,s.bills))fail('差异尚未复核：需同任务、同映射及相同预期数据的新一致账单作为证据');b.writeoff={at:iso,by:action.by,reason:action.reason.trim(),evidenceId:s.bills.find(x=>x.id!==b.id&&x.status==='MATCH'&&x.createdAt>b.createdAt&&x.taskId===b.taskId&&x.mappingId===b.mappingId&&x.evidenceHash===b.evidenceHash)?.id};break;}
 case 'TICK':tick(s,now);break;
 }
 if(action.type!=='TICK')s.logs.unshift({at:iso,level:'INFO',message:action.type,objectId:'id' in action?action.id:undefined});s.logs=s.logs.slice(0,1000);s.revision++;return s;
}
