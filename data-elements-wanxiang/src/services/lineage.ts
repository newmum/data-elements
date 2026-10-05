import { platformApi } from '../shared/platformApi';
import { changed,type Row } from './api';
/** NiFi's tenant-scoped repository is authoritative. No graph is inferred from an ER join. */
export async function lineageTasks(signal?:AbortSignal):Promise<Row[]>{const data=await platformApi<unknown>('/nifi/api/pipelines',undefined,{signal});if(!Array.isArray(data))throw new Error('接入任务返回格式不正确');return data.map(r=>({id:String(r.id),name:String(r.name||''),description:String(r.description||''),nodeCount:Number(r.nodeCount||0),updatedAt:r.updatedAt,nifiProcessGroupId:r.nifiProcessGroupId||null,status:r.nifiProcessGroupId?'DEPLOYED':'NOT_DEPLOYED'}));}
export async function lineageGraph(id:string):Promise<Row>{const row=await platformApi<Row>('/nifi/api/pipelines/'+encodeURIComponent(id));if(!Array.isArray(row.dsl?.nodes)||!Array.isArray(row.dsl?.edges))throw new Error('此任务没有可展示的已保存链路');return row;}
/** Save the original shared DSL; credentials/configuration are preserved in memory, never rendered or persisted in browser storage. */
export async function saveLineageGraph(row:Row){validateProcessingGraph(row.dsl);const result=await platformApi<Row>('/nifi/api/pipelines'+(row.id?'/'+encodeURIComponent(row.id)+'?expectedUpdatedAt='+encodeURIComponent(row.updatedAt):''),{name:row.name,description:row.description||'',dsl:row.dsl});changed();return result;}
export const processingNodeTemplate=(body:Row)=>platformApi<Row>('/nifi/api/pipelines/template/node',body);
export const normalizeMapping=(spec:unknown)=>platformApi<Row>('/nifi/api/field-mapping/migrate-legacy',{spec});
export const recommendFieldMapping=(sourceSchema:Row[],targetSchema:Row[])=>platformApi<Row>('/nifi/api/field-mapping/recommend',{sourceSchema,targetSchema});
export const validateFieldMapping=(spec:Row,sourceSchema:Row[],targetSchema:Row[])=>platformApi<Row>('/nifi/api/field-mapping/validate',{spec,sourceSchema,targetSchema});
export const compileFieldMapping=(spec:Row)=>platformApi<Row>('/nifi/api/field-mapping/compile',{spec});
export const dictionaryLookupTemplate=(body:Row)=>platformApi<Row>('/nifi/api/field-mapping/lookup-template',body);
export const previewFieldMapping=(spec:Row,sampleRows:Row[])=>platformApi<Row>('/nifi/api/field-mapping/preview',{spec,sampleRows});
export async function deleteLineageGraph(id:string){const result=await platformApi('/nifi/api/pipelines/delete/'+encodeURIComponent(id),{});changed();return result;}
export async function lineageLifecycle(id:string,action:'deploy'|'start'|'stop'|'undeploy'){if(!['deploy','start','stop','undeploy'].includes(action))throw new Error('加工流程动作无效');const result=await platformApi<Row>('/nifi/api/pipelines/'+encodeURIComponent(id)+'/'+action,{}, {timeoutMs:180000});changed();return result;}
export const lineageStatus=(id:string,signal?:AbortSignal)=>platformApi<Row>('/nifi/api/pipelines/'+encodeURIComponent(id)+'/status',undefined,{signal});
/** Component outlet name, not the native NiFi relationship (success). */
export function processingEdge(id:string,source:string,target:string){return {id,source,target,outlet:'default'};}
export function validateProcessingGraph(dsl:Row){if(!Array.isArray(dsl?.nodes)||!Array.isArray(dsl?.edges))throw new Error('加工图格式不正确');const ids=new Set<string>(),edges=new Set<string>(),adj=new Map<string,string[]>();for(const n of dsl.nodes){if(!n.id||ids.has(n.id)||!n.manifestKey)throw new Error('节点标识重复或类型缺失');ids.add(n.id);adj.set(n.id,[]);}for(const e of dsl.edges){if(!e.id||edges.has(e.id)||!ids.has(e.source)||!ids.has(e.target)||e.source===e.target)throw new Error('连线重复、悬空或自连接');edges.add(e.id);adj.get(e.source)!.push(e.target);}const visiting=new Set<string>(),done=new Set<string>();const walk=(id:string)=>{if(visiting.has(id))throw new Error('加工图不能包含循环连线');if(done.has(id))return;visiting.add(id);for(const next of adj.get(id)!)walk(next);visiting.delete(id);done.add(id);};ids.forEach(walk);}
/** Deterministic provenance from explicit field paths, not proof of actual execution. */
export function mappingDependencies(mapping:Row,sourceFields:string[]){
 if(mapping.from)return [String(mapping.from).replace(/^\//,'')];
 if(Array.isArray(mapping.fromList))return [...new Set<string>(mapping.fromList.map((p:unknown)=>String(p).replace(/^\//,'')))];
 // Ignore SQL literals/comments and function names. Explicit shared DSL references
 // remain authoritative; raw SQL identifiers are a best-effort design-time hint.
 const expression=String(mapping.expr||mapping.expression||'').replace(/'(?:''|[^'])*'|\/\*[\s\S]*?\*\/|--[^\n]*/g,' ');
 const explicit=[...expression.matchAll(/\$\{field:([^}]+)\}/g)].map(m=>m[1].replace(/^\//,''));
 const sql=expression.replace(/\$\{(?:field|target):[^}]+\}/g,' ');
 const inferred=sourceFields.filter(name=>new RegExp('(?:^|[^\\w])'+name.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')+'(?:$|[^\\w(]|\\s+(?!\\())','i').test(sql));
 return [...new Set([...explicit,...inferred])];
}
export async function runtimeLineage(taskId:string,nodeId:string):Promise<Row>{const r=await platformApi<Row>('/nifi/api/pipelines/'+encodeURIComponent(taskId)+'/nodes/'+encodeURIComponent(nodeId)+'/lineage?maxEvents=10',{});if(!Array.isArray(r.nodes)||!Array.isArray(r.links||r.edges))throw new Error('运行血缘返回格式不正确');return {anchorEventId:r.anchorEventId,message:r.message,nodes:r.nodes,edges:r.links||r.edges};}

export type LineageKind = 'TABLE' | 'FIELD' | 'MODEL';
export type LineageMode = 'FULL' | 'ATTRIBUTION' | 'IMPACT';
export type LineageScope = 'DESIGN' | 'DEPLOYED';
export interface LineageObject {
  id: string; key: string; kind: LineageKind; name: string; code: string;
  datasourceId: string; datasourceName: string; datasourceType: string;
  domainId: string; domainName?: string; tableId: string; tableName: string; updatedAt: string;
  dataType?: string; length?: number; nullable?: number; primaryKey?: number;
  objectType?: string; boundTableCount?: number; distance?: number;
}
export interface LineageRelation {
  id: string; source: string; target: string; pipelineId: string;
  evidenceKind: string; evidenceRefId: string; ruleSummary: string;
}
export interface LineageAnalysis {
  root: LineageObject; nodes: LineageObject[]; edges: LineageRelation[];
  mode: LineageMode; scope: LineageScope; depth: number; hasMore: boolean;
  note: string; coverage: { knownEdges: number; visibleNodes: number; visibleEdges: number };
}
export interface LineageCoverage {
  pipelines: number; tables: number; fields: number; designTableEdges: number;
  designFieldEdges: number; deployedEdges: number; deployedPipelines: number; indexedPipelines: number;
  datasources: { id: string; name: string; type: string }[];
}

const lineageBase = '/dwm/metadata-governance/lineage';
export async function searchLineageObjects(body: { keyword?: string; kind?: string; datasourceId?: string; linkedOnly?: boolean; pageNo?: number; pageSize?: number }, signal?: AbortSignal) {
  const result = await platformApi<{items: LineageObject[]; total: number; pageNo: number; pageSize: number}>(`${lineageBase}/objects`, body, {signal, timeoutMs: 30000});
  if (!result || !Array.isArray(result.items)) throw new Error('血缘对象列表返回格式不正确');
  return result;
}
export async function analyzeLineage(body: { kind: LineageKind; id: string; mode: LineageMode; scope: LineageScope; depth: number }, signal?: AbortSignal) {
  const result = await platformApi<LineageAnalysis>(`${lineageBase}/analyze`, body, {signal, timeoutMs: 45000});
  if (!result || !Array.isArray(result.nodes) || !Array.isArray(result.edges)) throw new Error('血缘分析返回格式不正确');
  return result;
}
export async function getLineageEvidence(edgeId: string, scope: LineageScope) {
  return platformApi<LineageRelation & {sourceObject: LineageObject; targetObject: LineageObject; explanation: string}>(`${lineageBase}/evidence`, {edgeId, scope});
}
export async function getLineageCoverage(signal?: AbortSignal) {
  return platformApi<LineageCoverage>(`${lineageBase}/coverage`, {}, {signal, timeoutMs: 45000});
}
