import { platformApi } from '../shared/platformApi';
import { changed, getSession, type Row } from './api';

export const qualityTypes = ['NOT_NULL', 'NOT_EMPTY', 'UNIQUE', 'RANGE', 'ENUM', 'LENGTH', 'REGEX', 'TIMELINESS', 'REFERENCE', 'PROFILE'];
export const qualityFormatPresets = [
 {label:'电子邮箱',pattern:'^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$'},
 {label:'电话号码',pattern:'^\\+?[\\d\\s-]{7,20}$'},
 {label:'字母数字',pattern:'^[A-Za-z0-9]+$'},
];
export const runStates = ['PENDING', 'INITIALIZING', 'RUNNING', 'COMPLETED', 'COMPLETED_WITH_ISSUES', 'FAILED', 'CANCELLED'];
export const orderStates = ['OPEN', 'IN_PROGRESS', 'PENDING_RECHECK', 'CLOSED'];
export const qualityRunning = (status: unknown) => ['PENDING', 'INITIALIZING', 'RUNNING'].includes(String(status ?? ''));
export const canMaintainQuality = () => !!getSession()?.authenticated;
/** A legacy stored score is not evidence of a check when its checked count is zero. */
export function qualityScore(value:unknown,checkedCount?:unknown):string {
 if(value==null||String(value).trim()===''||!Number.isFinite(Number(value)))return '未评价';
 if(checkedCount!==undefined&&(checkedCount==null||!Number.isFinite(Number(checkedCount))||Number(checkedCount)<=0))return '未评价';
 return Number(value).toFixed(2);
}
export const qualityStatus = (status: unknown) => ({ PENDING:'等待执行', INITIALIZING:'准备分片', RUNNING:'执行中', COMPLETED:'检查完成', COMPLETED_WITH_ISSUES:'发现异常', FAILED:'执行失败', CANCELLED:'已取消', OPEN:'待分派', IN_PROGRESS:'整改中', PENDING_RECHECK:'待复检', CLOSED:'已关闭', ASSIGNED:'已关联工单' } as Row)[String(status)] || String(status || '未执行');
export const dimensionLabel = (value: unknown) => ({ COMPLETENESS:'完整性', UNIQUENESS:'唯一性', VALIDITY:'规范性', ACCURACY:'准确性',TIMELINESS:'时效性',CONSISTENCY:'引用完整性',PROFILE:'字段探查' } as Row)[String(value)] || String(value || '—');
export const severityLabel = (value: unknown) => ({ LOW:'一般', MEDIUM:'中等', HIGH:'严重', CRITICAL:'致命' } as Row)[String(value)] || String(value || '—');
/** Original Java / Magic responses may use either naming convention. Preserve zero and false. */
export function qualityRecord(value: unknown): Row {
 if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('质量接口没有返回有效业务对象');
 const row: Row = {...value};
 for (const [key,item] of Object.entries(row)) {
  const snake=key.replace(/[A-Z]/g, c=>'_'+c.toLowerCase()), camel=key.replace(/_([a-z])/g,(_m,c:string)=>c.toUpperCase());
  if (!(snake in row)) row[snake]=item;
  if (!(camel in row)) row[camel]=item;
 }
 row.id=String(row.tid??row.id??'');
 if (row.task_name!=null && row.task_id==null) row.enabled=row.enabled==null?Number(row.status)===1:[true,1,'1','true'].includes(row.enabled);
 for (const key of ['rules','metrics','shards']) if (Array.isArray(row[key])) row[key]=row[key].map(qualityRecord);
 if (Array.isArray(row.rules)) row.rule_count=row.rules.length;
 row.score??=row.quality_score; row.violation_total??=row.violation_count;
 row.started_time??=row.started_at; row.finished_time??=row.finished_at;
 return row;
}
export type QualityPage = {list:Row[];total:number};
export type QualityQuery = {page:number;size:number;keyword:string;status:string;kind?:'PROFILE'|'QUALITY'};
export function qualityPage(value:unknown):QualityPage {
 if (!value || typeof value!=='object' || Array.isArray(value)) throw new Error('质量列表返回格式不正确');
 const outer=value as Row, row=Array.isArray(outer.list)?outer:outer.list&&typeof outer.list==='object'?outer.list:outer;
 const rawTotal=outer.total??row.total;
 // Magic db.page returns list:null for a real empty result. Accept only an explicit zero total.
 const list=row.list===null&&(rawTotal===0||rawTotal==='0')?[]:row.list;
 if(!Array.isArray(list)) throw new Error('质量列表没有返回有效记录集合');
 if(rawTotal!=null&&(typeof rawTotal!=='number'&&typeof rawTotal!=='string'||String(rawTotal).trim()===''))throw new Error('质量列表总数不正确');
 const total=Number(rawTotal??list.length);
 if(!Number.isInteger(total)||total<0) throw new Error('质量列表总数不正确');
 return {list:list.map(qualityRecord),total};
}
export type QualityRule = {ruleName:string;ruleType:string;dimension:string;columnName:string;severity:string;weight:number;parameters:Row;relatedColumn?:string};
export type QualityDraft = {tid?:string;taskName:string;taskCode:string;datasourceId:string;tableId:string;shardKey:string;shardCount:number;sampleLimit:number;timeoutMinutes:number;resourceGroup:string;triggerMode:string;cronExpression:string;description:string;rules:QualityRule[]};
export const emptyQualityDraft = ():QualityDraft => ({taskName:'',taskCode:'',datasourceId:'',tableId:'',shardKey:'',shardCount:1,sampleLimit:200,timeoutMinutes:120,resourceGroup:'DEFAULT',triggerMode:'MANUAL',cronExpression:'',description:'',rules:[]});
export function qualityRule(value:unknown):QualityRule {
 const r=qualityRecord(value); let parameters=r.parameters;
 if(parameters==null && r.parameters_json!=null) {try {parameters=JSON.parse(r.parameters_json);}catch {throw new Error('规则参数格式损坏，不能用空参数覆盖');}}
 if(parameters!=null && (typeof parameters!=='object'||Array.isArray(parameters))) throw new Error('规则参数必须是对象');
 return {ruleName:r.rule_name||'',ruleType:r.rule_type||'NOT_NULL',dimension:r.dimension||r.quality_dimension||'COMPLETENESS',columnName:r.column_name||'',severity:r.severity||'MEDIUM',weight:Number(r.weight??r.weight_value??10),parameters:parameters||{},...(r.related_column?{relatedColumn:r.related_column}:{})};
}
export function qualityDraft(value:unknown):QualityDraft {
 const row=qualityRecord(value), result=emptyQualityDraft();
 for(const key of Object.keys(result) as Array<keyof QualityDraft>) if(row[key]!=null) (result as Row)[key]=row[key];
 result.tid=row.id||undefined; result.rules=(row.rules||[]).map(qualityRule); return result;
}
function bounded(value:unknown,max:number,label:string,required=false) {const text=String(value??'').trim();if(required&&!text)throw new Error(`请填写${label}`);if(text.length>max)throw new Error(`${label}不能超过 ${max} 个字符`);return text;}
function integer(value:unknown,min:number,max:number,label:string){const n=Number(value);if(value==null||value===''||!Number.isInteger(n)||n<min||n>max)throw new Error(`${label}须为 ${min}–${max} 的整数`);return n;}
/** Only the existing shared task DTO crosses the boundary; preview revisions, tenant and budgets do not. */
export function qualityBody(input:QualityDraft):QualityDraft {
 const out=emptyQualityDraft();
 if(input.tid)out.tid=bounded(input.tid,64,'任务标识',true);
 out.taskName=bounded(input.taskName,128,'任务名称',true);out.taskCode=bounded(input.taskCode,64,'任务编码',true);
 out.datasourceId=bounded(input.datasourceId,64,'数据源',true);out.tableId=bounded(input.tableId,64,'数据表',true);
 out.shardKey=bounded(input.shardKey,128,'分片字段');out.shardCount=integer(input.shardCount,1,128,'分片数');
 if(out.shardCount>1&&!out.shardKey)throw new Error('多分片执行必须选择数值分片字段');
 out.sampleLimit=integer(input.sampleLimit,0,2000,'异常样例上限');out.timeoutMinutes=integer(input.timeoutMinutes,1,1440,'超时分钟');
 out.resourceGroup=bounded(input.resourceGroup||'DEFAULT',64,'资源组',true);out.description=bounded(input.description,1000,'任务说明');
 if(!['MANUAL','CRON'].includes(input.triggerMode))throw new Error('请选择手动或定时触发');
 out.triggerMode=input.triggerMode;out.cronExpression=input.triggerMode==='CRON'?bounded(input.cronExpression,128,'Cron 表达式',true):'';
 if(out.triggerMode==='CRON'&&out.cronExpression.split(/\s+/).length!==6)throw new Error('Cron 表达式需包含六段；具体语法由共享后端校验');
 if(!Array.isArray(input.rules)||!input.rules.length)throw new Error('请至少配置一条质量规则');
 if(input.rules.length>64)throw new Error('一次最多配置 64 条规则或探查字段');
 const profile=input.rules.some(r=>r.ruleType==='PROFILE');
 if(profile&&(out.shardCount!==1||input.rules.some(r=>r.ruleType!=='PROFILE')))throw new Error('字段探查不能混入评分规则或使用多分片');
 out.rules=input.rules.map(r=>{
  if(!qualityTypes.includes(r.ruleType))throw new Error('共享后端不支持此规则模板');
  if(!['LOW','MEDIUM','HIGH','CRITICAL'].includes(r.severity))throw new Error('严重程度不正确');
  const p={...(r.parameters||{})};
  delete p.resolvedTargetTable;delete p.evaluationTime;
  const fields=(key:string)=>{if(!Array.isArray(p[key])||!p[key].length||p[key].length>16||p[key].some((v:unknown)=>typeof v!=='string'||!v.trim())||new Set(p[key]).size!==p[key].length)throw new Error('联合字段须为 1–16 个不重复字段');return p[key];};
  if(r.ruleType==='UNIQUE'&&p.columns){fields('columns');if(!p.columns.includes(r.columnName))throw new Error('主要字段必须在联合字段中');}
  if(r.ruleType==='REFERENCE'){p.targetTableId=bounded(p.targetTableId,64,'引用目标表',true);if(fields('sourceColumns').length!==fields('targetColumns').length)throw new Error('引用映射两侧字段数量不一致');if(!p.sourceColumns.includes(r.columnName))throw new Error('主要字段必须在引用来源字段中');p.allowNull=p.allowNull===true;}
  if(r.ruleType==='TIMELINESS'){p.maxAgeMinutes=integer(p.maxAgeMinutes,1,5256000,'最大允许时差（分钟）');p.futureToleranceMinutes=integer(p.futureToleranceMinutes??0,0,5256000,'未来时间容差（分钟）');}
  if(r.ruleType==='PROFILE'){if(!['SAMPLE','FULL'].includes(p.scanMode))throw new Error('请选择抽样或全量探查');if(p.scanMode==='SAMPLE')p.scanLimit=integer(p.scanLimit,1,10000,'探查扫描预算');else p.scanLimit=0;}
  const present=(value:unknown)=>value!=null&&String(value).trim()!=='';
  if(r.ruleType==='RANGE'){
   for(const key of ['min','max']){if(!present(p[key]))delete p[key];else if(!Number.isFinite(Number(p[key])))throw new Error('请填写有效的数值边界');}
   if(!present(p.min)&&!present(p.max))throw new Error('范围规则至少填写一个数值边界');
   if(present(p.min)&&present(p.max)&&Number(p.min)>Number(p.max))throw new Error('最小值不能大于最大值');
  }
  if(r.ruleType==='LENGTH'){
   for(const key of ['minLength','maxLength']){if(!present(p[key]))delete p[key];else p[key]=integer(p[key],0,65535,key==='minLength'?'最小长度':'最大长度');}
   if(!present(p.minLength)&&!present(p.maxLength))throw new Error('长度规则至少填写一个长度边界');
   if(present(p.minLength)&&present(p.maxLength)&&p.minLength>p.maxLength)throw new Error('最小长度不能大于最大长度');
  }
  if(r.ruleType==='ENUM'&&(!Array.isArray(p.values)||!p.values.length||p.values.some((v:unknown)=>typeof v!=='string'&&typeof v!=='number')))throw new Error('请填写至少一个有效枚举值');
  if(r.ruleType==='REGEX'){p.pattern=bounded(p.pattern,1000,'正则表达式',true);try{new RegExp(p.pattern);}catch{throw new Error('正则表达式格式不正确');}}
  return {ruleName:bounded(r.ruleName,128,'规则名称',true),ruleType:r.ruleType,dimension:bounded(r.dimension,32,'质量维度',true),columnName:bounded(r.columnName,128,'校验字段',true),severity:r.severity,weight:integer(r.weight,1,100,'评分权重'),parameters:p,...(r.relatedColumn?{relatedColumn:bounded(r.relatedColumn,128,'关联字段')}:{})};
 });
 return out;
}
const call=<T=unknown>(path:string,body:unknown={},signal?:AbortSignal,timeoutMs=20000)=>platformApi<T>('/dwm/quality/'+path,body,{signal,timeoutMs});
async function records(path:string,body:unknown={},signal?:AbortSignal){const value=await call(path,body,signal);if(!Array.isArray(value))throw new Error('质量选项返回格式不正确');return value.map(qualityRecord);}
async function mutate<T>(path:string,body:unknown):Promise<T>{const result=await call<T>(path,body);if(result===false||result==null)throw new Error('后端未确认操作成功');changed();return result;}
const id=(value:unknown)=>bounded(value,64,'业务标识',true);
export const qualityApi={
 summary:async(signal?:AbortSignal)=>qualityRecord(await call('summary',{},signal)),
 taskPage:async(query:Partial<QualityQuery>={},signal?:AbortSignal)=>qualityPage(await call('task/page',{page:query.page||1,size:query.size||15,keyword:query.keyword||'',status:query.status||'',...(query.kind?{kind:query.kind}:{})},signal)),
 taskDetail:async(taskId:string,signal?:AbortSignal)=>qualityRecord(await call('task/detail',{taskId:id(taskId)},signal)),
 saveTask:async(draft:QualityDraft)=>qualityRecord(await mutate('task/save',qualityBody(draft))),
 precheck:async(taskId:string)=>{const result=await call<Row>('task/precheck',{taskId:id(taskId)},undefined,120000);if(typeof result?.success!=='boolean')throw new Error('预检接口未返回有效结果');changed();return result;},
 enable:(taskId:string,enabled:boolean)=>mutate('task/enable',{taskId:id(taskId),enabled}),
 deleteTask:(taskId:string)=>mutate('task/delete',{taskId:id(taskId)}),
 manualRun:async(taskId:string,requestId=crypto.randomUUID())=>qualityRecord(await mutate('run/manual',{taskId:id(taskId),requestId})),
 runPage:async(query:Partial<QualityQuery>={},signal?:AbortSignal)=>qualityPage(await call('run/page',{page:query.page||1,size:query.size||15,keyword:query.keyword||'',status:query.status||'',...(query.kind?{kind:query.kind}:{})},signal)),
 runDetail:async(runId:string,signal?:AbortSignal)=>qualityRecord(await call('run/detail',{runId:id(runId)},signal)),
 runIssues:async(runId:string,page=1,size=20,severity='',status='',signal?:AbortSignal)=>qualityPage(await call('run/issues',{runId:id(runId),page,size,severity,status},signal)),
 cancelRun:(runId:string)=>mutate('run/cancel',{runId:id(runId)}),
 datasources:(signal?:AbortSignal)=>records('options/datasources',{},signal),tables:(datasourceId:string,signal?:AbortSignal)=>records('options/tables',{datasourceId:id(datasourceId)},signal),columns:(tableId:string,signal?:AbortSignal)=>records('options/columns',{tableId:id(tableId)},signal),templates:(signal?:AbortSignal)=>records('options/templates',{},signal),organizations:(signal?:AbortSignal)=>records('options/organizations',{},signal),
 workOrderPage:async(query:Partial<QualityQuery>={},signal?:AbortSignal):Promise<QualityPage>=>{const [result,orgs]=await Promise.all([call('workorders/page',{page:query.page||1,size:query.size||15,status:query.status||''},signal),records('options/organizations',{},signal)]);const page=qualityPage(result);return {...page,list:page.list.map(r=>({...r,assigneeOrgName:orgs.find(o=>o.id===String(r.assigneeOrgId))?.name||''}))};},
 createWorkOrder:async(issueId:string,assigneeOrgId?:string,remark?:string)=>qualityRecord(await mutate('workorders/create',{issueId:id(issueId),...(assigneeOrgId?{assigneeOrgId:id(assigneeOrgId)}:{}),...(remark?{remark:bounded(remark,1000,'工单说明')}:{})})),
 transitionWorkOrder:(orderId:string,action:string,opinion='',assigneeOrgId?:string)=>{if(!['START','SUBMIT_RECHECK','CLOSE'].includes(action))throw new Error('工单动作不合法');if(action==='START'&&!assigneeOrgId)throw new Error('请选择责任组织');if(action!=='START'&&!opinion.trim())throw new Error('请填写整改或复检说明');return mutate('workorders/transition',{orderId:id(orderId),action,opinion:bounded(opinion,1000,'整改说明'),...(assigneeOrgId?{assigneeOrgId:id(assigneeOrgId)}:{})});},
};
/** Export every matched server page, not just the visible page. Fail on repeated/malformed pages. */
export async function qualityExport(kind:'tasks'|'runs'|'orders',query:Partial<QualityQuery>):Promise<Row[]> {
 const loader=kind==='tasks'?qualityApi.taskPage:kind==='runs'?qualityApi.runPage:qualityApi.workOrderPage;
 const rows:Row[]=[], seen=new Set<string>();
 for(let page=1;page<=100;page++) {const result=await loader({...query,page,size:200});if(result.total>20000)throw new Error('导出超过 20000 条，请缩小筛选范围');let added=0;for(const row of result.list){if(!row.id)throw new Error('记录缺少业务标识');if(!seen.has(row.id)){seen.add(row.id);rows.push(row);added++;}}if(rows.length>=result.total)return rows;if(!added)throw new Error('后端分页没有推进，已停止导出');}
 throw new Error('导出超过安全范围，请缩小筛选条件');
}
