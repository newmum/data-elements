import {isConfigurationOnly,previewModel,type ModelNode} from './modeling';
import {modelInputCapacity,modelHasOutput} from './modelingInteractions';
import type {TableDef} from './data';
export type ExecutionSettings={engine:'flink'|'spark';mode:'batch'|'stream'};
export type ExecutionIssue={level:'error'|'warning';message:string;nodeId?:string};
const streaming=new Set(['kafkaInput','cdcInput','fileWatcher','slidingWindow','tumblingWindow','sessionWindow','eventTimeWindow']);
const bounded=new Set(['sort','rank','percentile','pivot','crossJoin','fuzzy','excelInput','excelOutput']);
export function validateExecution(nodes:ModelNode[],settings:ExecutionSettings):ExecutionIssue[]{
 const issues:ExecutionIssue[]=[];const error=(message:string,nodeId?:string)=>issues.push({level:'error',message,nodeId});
 if(nodes.length>30)return [{level:'error',message:'模型最多 30 个节点'}];
 const ids=new Set(nodes.map(n=>n.id));if(ids.size!==nodes.length)error('存在重复节点 ID');
 const sources=nodes.filter(n=>modelInputCapacity(n.kind)===0),outputs=nodes.filter(n=>!modelHasOutput(n.kind));
 if(!sources.length)error('缺少输入节点');if(!outputs.length)error('缺少输出节点');
 for(const n of nodes){const cap=modelInputCapacity(n.kind);if(n.inputs.length!==cap)error(`${n.name}需要 ${cap} 个输入，当前 ${n.inputs.length} 个`,n.id);if(new Set(n.inputs).size!==n.inputs.length)error(`${n.name}存在重复连线`,n.id);if(n.inputs.some(id=>!ids.has(id)))error(`${n.name}存在断开的前序节点`,n.id);
 if(n.inputs.some(id=>{const input=nodes.find(item=>item.id===id);return input&&!modelHasOutput(input.kind)}))error(`${n.name}不可将输出节点作为前序`,n.id);
 if(isConfigurationOnly(n.kind))error(`${n.name}仅配置，尚未接入执行器`,n.id);
 const optional=new Set(['mockSqlTable','manualSourceName','inputMode','value','replacement','separator','delimiter','prefix','defaultValue','group','responsePath','groupFields','keyFields','keyField']);
 if(n.kind==='source'&&n.config.inputMode==='sql'){optional.add('table');if(!n.config.database?.trim())error(`${n.name}请选择数据库`,n.id);if(!n.config.sqlText?.trim())error(`${n.name}请填写 SQL`,n.id);if(!n.config.mockSqlTable)error(`${n.name}自定义 SQL 未接入执行器`,n.id)}
 for(const [key,value] of Object.entries(n.config)){if(!optional.has(key)&&!value.trim()&&!(key==='field'&&n.kind==='aggregate'&&n.config.operation==='count'))error(`${n.name}缺少配置：${key}`,n.id)}
 if(settings.mode==='batch'&&streaming.has(n.kind))error(`${n.name}需要实时模式（本原型规则）`,n.id);
 if(settings.mode==='stream'&&bounded.has(n.kind))error(`${n.name}依赖有限数据集，实时模式未建模（本原型规则）`,n.id);
 }
 const visiting=new Set<string>(),done=new Set<string>();let cyclic=false;const visit=(id:string)=>{if(visiting.has(id)){cyclic=true;return}if(done.has(id))return;visiting.add(id);nodes.find(n=>n.id===id)?.inputs.forEach(visit);visiting.delete(id);done.add(id)};nodes.forEach(n=>visit(n.id));if(cyclic)error('存在循环依赖');
 const connected=new Set<string>();const collect=(id:string)=>{if(connected.has(id))return;connected.add(id);nodes.find(n=>n.id===id)?.inputs.forEach(collect)};outputs.forEach(n=>collect(n.id));nodes.filter(n=>!connected.has(n.id)).forEach(n=>error(`${n.name}未连接到输出`,n.id));
 if(settings.mode==='stream')issues.push({level:'warning',message:`${settings.engine==='spark'?'Spark 微批':'Flink 流式'}仅为目标配置；本页按有限快照模拟，不产生持续流、检查点或真实吞吐指标。`});
 issues.push({level:'warning',message:'兼容校验仅覆盖本原型规则，不代表已对接或完整支持真实引擎。'});return issues;
}
export type MockRun={state:'queued'|'running'|'succeeded'|'failed'|'cancelled';attempt:number;settings:ExecutionSettings;nodes:ModelNode[];log:string[];rows?:number;error?:string;backfill?:string};
export function createMockRun(nodes:ModelNode[],settings:ExecutionSettings,backfill?:string):MockRun{
 const errors=validateExecution(nodes,settings).filter(i=>i.level==='error');if(errors.length)throw new Error(errors.map(i=>i.message).join('；'));
 return {state:'queued',attempt:1,settings:{...settings},nodes:structuredClone(nodes),log:['模拟任务已创建，等待手动推进；未向引擎提交。'],backfill};
}
export function advanceMockRun(run:MockRun,tables:Record<string,TableDef>,fail=false):MockRun{
 if(run.state==='queued')return {...run,state:'running',log:[...run.log,'进入模拟运行阶段，尚未计算数据。']};if(run.state!=='running')return run;
 try{if(fail)throw new Error('用户触发的模拟故障');let rows=0;for(const n of run.nodes.filter(n=>!modelHasOutput(n.kind))){rows+=previewModel(run.nodes,n.id,tables).rows.length}return {...run,state:'succeeded',rows,error:undefined,log:[...run.log,`浏览器快照预览完成：输出合计 ${rows} 行；非引擎执行成功。`]}}
 catch(e){const error=e instanceof Error?e.message:'模拟失败';return {...run,state:'failed',error,log:[...run.log,`模拟失败：${error}`]}}
}
export function retryMockRun(run:MockRun):MockRun{if(run.state!=='failed')throw new Error('仅失败任务可重试');if(run.attempt>=3)throw new Error('最多尝试 3 次，请修复模型后新建模拟任务');return {...run,state:'queued',attempt:run.attempt+1,error:undefined,rows:undefined,log:[...run.log,'手动重试已排队；没有后台自动重试。']}}
export function cancelMockRun(run:MockRun):MockRun{if(!['queued','running'].includes(run.state))return run;return {...run,state:'cancelled',log:[...run.log,'模拟任务已取消。']}}
/** Strict ISO minute/second precision, explicit timezone, and actual calendar dates. */
export function validScheduleTime(value:string):boolean {
 const m=/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?(Z|([+-])(\d{2}):(\d{2}))$/.exec(value);
 if(!m)return false;const y=+m[1],month=+m[2],day=+m[3],hour=+m[4],minute=+m[5],second=+(m[6]||0),tzHour=+(m[9]||0),tzMin=+(m[10]||0);
 return y>=1000&&month>=1&&month<=12&&day>=1&&day<=new Date(Date.UTC(y,month,0)).getUTCDate()&&hour<24&&minute<60&&second<60&&tzHour<=14&&tzMin<60&&!(tzHour===14&&tzMin!==0)&&Number.isFinite(Date.parse(value));
}
export function validateSchedule(interval:string,start:string,end:string):string{
 if(!/^\d+$/.test(interval)||Number(interval)<1||Number(interval)>10080)return '间隔必须为 1–10080 分钟';
 if(Boolean(start)!==Boolean(end))return '补跑开始和结束时间需同时填写';
 if(start&&(!validScheduleTime(start)||!validScheduleTime(end)))return '请输入有效的带时区 ISO 时间，例如 2026-11-14T00:00+08:00';
 if(start&&Date.parse(start)>=Date.parse(end))return '补跑结束时间必须晚于开始时间';return '';
}
