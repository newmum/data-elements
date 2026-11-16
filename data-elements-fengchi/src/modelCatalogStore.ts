import {scheduleConfig,scheduleError,type ModelSchedule} from './modelSchedule';
import type {ModelNode} from './modeling';
import {nodeLabels} from './modeling';
import {normalizeExecutionSettings,type ExecutionSettings} from './modelingExecution';
import type {TableDef} from './data';
export const MODEL_CATALOG_SCHEMA_VERSION=1 as const;
export const MODEL_DOCUMENT_MAX_CHARACTERS=2_000_000;
const PREFIX='fengchi:model-catalog:v1:';
export type ModelDocument={schemaVersion:1;startPosition?:{x:number;y:number};sourceId?:string;sourceLabel?:string;id:string;name:string;category:string;nodes:ModelNode[];executionSettings:ExecutionSettings;importedTables:Record<string,TableDef>;revision:number;createdAt:string;updatedAt:string};
export type ModelDraft=Pick<ModelDocument,'startPosition'|'id'|'name'|'category'|'sourceId'|'sourceLabel'|'nodes'|'executionSettings'|'importedTables'>;
export type ModelStorage=Pick<Storage,'length'|'key'|'getItem'|'setItem'>;
const fail=(message:string):never=>{throw new Error(message)};
const record=(value:unknown):value is Record<string,unknown>=>Boolean(value)&&typeof value==='object'&&!Array.isArray(value);
function text(value:unknown,max:number,label:string){if(typeof value!=='string'||!value.trim()||value.length>max)fail(`${label}为空或过长`);return value as string}
function check(value:unknown):asserts value is ModelDocument {
 if(!record(value)||value.schemaVersion!==1)fail('模型版本不兼容，未覆盖原记录');
 const d=value as Record<string,unknown>;if(d.startPosition!==undefined){if(!record(d.startPosition)||typeof d.startPosition.x!=='number'||typeof d.startPosition.y!=='number'||!Number.isFinite(d.startPosition.x)||!Number.isFinite(d.startPosition.y)||d.startPosition.x<12||d.startPosition.x>3200||d.startPosition.y<12||d.startPosition.y>2000)fail('开始节点位置无效')} text(d.id,100,'模型 ID');text(d.name,80,'模型名称');text(d.category,80,'业务分类');
 if(!Number.isSafeInteger(d.revision)||Number(d.revision)<1)fail('模型修订号无效');
 for(const key of ['createdAt','updatedAt'])if(typeof d[key]!=='string'||!Number.isFinite(Date.parse(d[key] as string)))fail('模型保存时间无效');
 if(!record(d.executionSettings)||!['flink','spark'].includes(String(d.executionSettings.engine))||!['batch','stream'].includes(String(d.executionSettings.mode)))fail('模型执行设置无效');
 const options=d.executionSettings as Record<string,unknown>;if(options.schedule!==undefined){if(!record(options.schedule))fail('调度配置无效');const issue=scheduleError(scheduleConfig(options.schedule as Partial<ModelSchedule>),Number(options.intervalMinutes??60));if(issue)fail(issue)}
 for(const field of ['scheduleEnabled','runImmediately'])if(options[field]!==undefined&&typeof options[field]!=='boolean')fail('调度开关须为布尔值');
 if(options.intervalMinutes!==undefined&&(!Number.isSafeInteger(options.intervalMinutes)||Number(options.intervalMinutes)<1||Number(options.intervalMinutes)>10080))fail('调度间隔须为 1–10080 分钟整数');
 if(!Array.isArray(d.nodes)||d.nodes.length>30)fail('模型节点最多 30 个');const ids=new Set<string>();
 for(const n of d.nodes as unknown[]){if(!record(n))fail('模型节点无效');const node=n as Record<string,unknown>;const id=text(node.id,100,'节点 ID');if(ids.has(id))fail('模型节点 ID 重复');ids.add(id);text(node.name,100,'节点名称');if(typeof node.kind!=='string'||!Object.hasOwn(nodeLabels,node.kind))fail('不支持的模型节点');for(const k of ['x','y'])if(typeof node[k]!=='number'||!Number.isFinite(node[k]))fail('节点位置无效');for(const k of ['width','height'])if(node[k]!==undefined&&(typeof node[k]!=='number'||!Number.isFinite(node[k])||Number(node[k])<=0))fail('节点尺寸无效');if(!Array.isArray(node.inputs)||node.inputs.length>30||node.inputs.some(x=>typeof x!=='string'))fail('节点连接无效');if(!record(node.config)||Object.keys(node.config).length>20||Object.values(node.config).some(x=>typeof x!=='string'||x.length>10000))fail('节点配置超出限制');}
 for(const n of d.nodes as ModelNode[])if(n.inputs.some(id=>!ids.has(id)))fail('节点连接引用不存在');
 if(!record(d.importedTables)||Object.keys(d.importedTables).length>30)fail('本地文件表超出限制');
 for(const t of Object.values(d.importedTables as Record<string,unknown>)){if(!record(t)||!Array.isArray(t.columns)||!Array.isArray(t.rows)||t.columns.length>200||t.rows.length>1000)fail('本地文件表结构超出限制');const table=t as unknown as TableDef;text(table.name,200,'文件表名称');const names=new Set<string>();for(const c of table.columns){if(!record(c))fail('文件字段无效');const name=text(c.name,1000,'文件字段');if(names.has(name))fail('文件字段重复');names.add(name)}for(const row of table.rows){if(!record(row))fail('文件记录无效');for(const v of Object.values(row))if(v!==null&&!(typeof v==='string'&&v.length<=100000)&&!(typeof v==='number'&&Number.isFinite(v)))fail('文件记录仅支持有限数字、文本或 NULL');}}
}
function storage(provided?:ModelStorage):ModelStorage{if(provided)return provided;try{return window.localStorage}catch{throw new Error('浏览器本地存储不可用，模型尚未保存')}}
function key(id:string){return PREFIX+encodeURIComponent(text(id,100,'模型 ID'))}
function parse(raw:string,id?:string):ModelDocument{if(raw.length>MODEL_DOCUMENT_MAX_CHARACTERS)fail('模型记录超过 200 万字符');let d:unknown;try{d=JSON.parse(raw)}catch{throw new Error('模型记录损坏，未覆盖原记录')}check(d);d.executionSettings=normalizeExecutionSettings(d.executionSettings);if(id!==undefined&&d.id!==id)fail('模型 ID 与存储记录不匹配');return d}
export function loadModelDocument(id:string,provided?:ModelStorage):ModelDocument|null{const raw=storage(provided).getItem(key(id));return raw===null?null:parse(raw,id)}
export function listModelDocuments(provided?:ModelStorage):ModelDocument[]{const s=storage(provided),docs:ModelDocument[]=[];for(let i=0;i<s.length;i++){const k=s.key(i);if(!k?.startsWith(PREFIX))continue;const raw=s.getItem(k);if(raw===null)continue;const d=parse(raw);if(k!==key(d.id))fail('模型目录记录不匹配');docs.push(d)}return docs.sort((a,b)=>b.updatedAt.localeCompare(a.updatedAt))}
/** Explicit user save only. Revision checks detect stale tabs; never auto-overwrite. */
export function saveModelDocument(draft:ModelDraft,expectedRevision:number|null,provided?:ModelStorage):ModelDocument{const s=storage(provided),old=loadModelDocument(draft.id,s);if((old?.revision??null)!==expectedRevision)fail('模型已被其他窗口修改，请重新加载或另存为');const now=new Date().toISOString();const d:ModelDocument={...draft,schemaVersion:1,revision:(old?.revision??0)+1,createdAt:old?.createdAt??now,updatedAt:now};check(d);const raw=JSON.stringify(d);if(raw.length>MODEL_DOCUMENT_MAX_CHARACTERS)fail('模型及本地文件数据超过 200 万字符，请减少导入数据');try{s.setItem(key(d.id),raw)}catch{throw new Error('本地存储写入失败或空间不足，模型尚未保存')}return parse(raw,d.id)}
export function changeModelCategory(id:string,category:string,expectedRevision:number,provided?:ModelStorage):ModelDocument{const d=loadModelDocument(id,provided);if(!d)fail('模型不存在');return saveModelDocument({...d!,category},expectedRevision,provided)}

export function renameModelDocument(id:string,name:string,expectedRevision:number,provided?:ModelStorage):ModelDocument{const d=loadModelDocument(id,provided);if(!d)fail('模型不存在');return saveModelDocument({...d!,name:name.trim()},expectedRevision,provided)}
