import { platformApi, platformApiGet } from '../shared/platformApi';
import { getSession, changed, type Row } from './api';
import { listSources } from './datasources';
import { organizationOptions, sourceOptions, type SourceOption } from './datasourceRegistrationApi';
export type LiveMetadataSource = Row & {tid:string;db_name:string;db_type:string;org_id?:string;org_name?:string;app_id?:string;app_name?:string};
export type LiveMetadataTable = Row & {tid:string;datasource_id:string;table_name:string;table_comment?:string;table_name_cn?:string;table_type?:string;field_count?:number};
export type LiveMetadataCatalog = {sources:LiveMetadataSource[];tables:LiveMetadataTable[];columns:Row[];relations:Row[]};
const snake=(row:Row)=>Object.fromEntries(Object.entries(row).map(([key,value])=>[key.replace(/[A-Z]/g,c=>'_'+c.toLowerCase()),value]));
/** Reject malformed success payloads; never substitute preview metadata on a live failure. */
export function normalizeMetadata(value:unknown):LiveMetadataCatalog {
 if(!value||typeof value!=='object')throw new Error('元数据目录返回格式不正确');
 const v=value as Row;for(const key of ['sources','tables','columns','relations'])if(!Array.isArray(v[key]))throw new Error('元数据目录缺少 '+key+' 列表');
 const unique=(rows:Row[],key='tid')=>{const seen=new Set<string>();return rows.map(snake).filter(r=>{const id=String(r[key]??'');if(!id||seen.has(id))return false;seen.add(id);r[key]=id;return true;});};
 const sources=unique(v.sources).map(r=>({tid:r.tid,db_name:String(r.db_name||''),db_type:String(r.db_type||r.database_type||''),org_id:String(r.org_id||''),org_name:String(r.org_name||''),app_id:String(r.app_id||''),app_name:String(r.app_name||''),is_enable:r.is_enable,storage_domain:r.storage_domain,connection_status:r.connection_status,updated_time:r.updated_time}));
 const ids=new Set(sources.map(s=>s.tid));
 const tables:LiveMetadataTable[]=unique(v.tables).filter(t=>ids.has(String(t.datasource_id))).map(t=>({...t,tid:String(t.tid),datasource_id:String(t.datasource_id),table_name:String(t.table_name||t.table_name_en||''),field_count:Number(t.field_count||0)}));
 const tableIds=new Set(tables.map(t=>t.tid));
 const columns:Row[]=unique(v.columns).filter(c=>tableIds.has(String(c.table_id))).map(c=>({...c,tid:String(c.tid),table_id:String(c.table_id)}));
 const fieldTables=new Map(columns.map(c=>[c.tid,c.table_id]));
 const relations=unique(v.relations).filter(r=>tableIds.has(String(r.source_table_id))&&tableIds.has(String(r.target_table_id))&&fieldTables.get(String(r.source_column_id))===String(r.source_table_id)&&fieldTables.get(String(r.target_column_id))===String(r.target_table_id)).map(r=>{if(!r.definition_json)return r;const d=typeof r.definition_json==='string'?JSON.parse(r.definition_json):r.definition_json;if(!d||!Array.isArray(d.mappings)||!Array.isArray(d.conditions))throw new Error('关系定义不完整，未自动覆盖历史数据');return {...r,broken:d.mappings.some((m:Row)=>fieldTables.get(m.sourceColumnId)!==r.source_table_id||fieldTables.get(m.targetColumnId)!==r.target_table_id)||d.conditions.some((c:Row)=>fieldTables.get(c.columnId)!==(c.side==='source'?r.source_table_id:r.target_table_id))};});
 return {sources,tables,columns,relations};
}
let cache:{scope:string;at:number;value:LiveMetadataCatalog}|undefined;
let pending:{scope:string;generation:number;promise:Promise<LiveMetadataCatalog>}|undefined;let generation=0;
export const metadataScope=()=>{const s=getSession();const tenant=String(s?.tenant?.tid||s?.tenant?.tenantId||''),principal=String(s?.principalId||'');if(!s?.authenticated||!tenant||!principal)throw new Error('登录上下文尚未就绪');return tenant+':'+principal;};
export const metadataWritable=()=>Boolean(getSession()?.authenticated);
export function invalidateMetadata(){cache=undefined;generation++;changed();}
/** Coalesce large snapshot reads. Each consumer can abort without cancelling other views. */
export async function readMetadata(signal?:AbortSignal,force=false):Promise<LiveMetadataCatalog>{
 const scope=metadataScope();signal?.throwIfAborted();
 if(!force&&cache?.scope===scope&&Date.now()-cache.at<15000)return cache.value;
 if(force)generation++;
 const epoch=generation;
 if(!pending||pending.scope!==scope||pending.generation!==epoch){
  const request={scope,generation:epoch,promise:Promise.resolve(null as unknown as LiveMetadataCatalog)};
  request.promise=platformApi('/dwm/metadata-governance/catalog',{}, {timeoutMs:60000}).then(raw=>{const value=normalizeMetadata(raw);if(metadataScope()!==scope)throw new Error('登录上下文已切换，请重新加载元数据');if(generation===epoch)cache={scope,at:Date.now(),value};return value;}).finally(()=>{if(pending===request)pending=undefined;});pending=request;
 }
 const promise=pending.promise;if(!signal)return promise;
 return new Promise((resolve,reject)=>{const abort=()=>reject(signal.reason||new DOMException('Aborted','AbortError'));signal.addEventListener('abort',abort,{once:true});promise.then(resolve,reject).finally(()=>signal.removeEventListener('abort',abort));});
}
export async function readMetadataScope(signal?:AbortSignal):Promise<{organizations:SourceOption[];applications:SourceOption[];sources:LiveMetadataSource[]}>{
 const [catalog,orgs,apps,sourceRows]=await Promise.all([readMetadata(signal),platformApi('/sym/org/getOrgTree',{parentId:'ROOT'},{signal}),platformApi('/dst/application/list',{}, {signal}),listSources(signal)]);
 const byId=new Map(sourceRows.map(r=>[r.id,r]));const sources=catalog.sources.map(s=>{const r=byId.get(s.tid);return {...s,org_id:s.org_id||r?.orgId||'',org_name:r?.orgName||s.org_name||'',app_id:s.app_id||r?.appId||'',app_name:r?.businessSystem||s.app_name||''};});
 return {sources,organizations:organizationOptions(orgs),applications:sourceOptions(apps)};
}
/** Lightweight catalog scope: the table browser must not load the full ER/field snapshot. */
export async function readCatalogScope(signal?:AbortSignal):Promise<{organizations:SourceOption[];applications:SourceOption[];sources:LiveMetadataSource[]}>{
 const [orgs,apps,sourceRows]=await Promise.all([platformApi('/sym/org/getOrgTree',{parentId:'ROOT'},{signal}),platformApi('/dst/application/list',{}, {signal}),listSources(signal)]);
 const applications=sourceOptions(apps),appNames=new Map(applications.map(app=>[app.value,app.label]));
 return {sources:sourceRows.map(r=>({tid:String(r.id),db_name:String(r.name||''),db_type:String(r.backendType||r.engine||''),org_id:String(r.orgId||''),org_name:String(r.orgName||''),app_id:String(r.appId||''),app_name:String(r.businessSystem||appNames.get(String(r.appId||''))||'')})),organizations:organizationOptions(orgs),applications};
}
export type CatalogTablesPage = {rows:LiveMetadataTable[];catalogs:{tid:string;catalogName:string;catalogNameEn:string;assetDesc:string;sourceTableId:string;dbId:string}[];total:number;page:number;size:number};
export async function readCatalogTablesPage(sourceIds:string[]|undefined,kind:string|undefined,keyword:string,page:number,size:number,signal?:AbortSignal,tableId?:string):Promise<CatalogTablesPage>{
 const query={sourceIds:sourceIds?.join(','),kind,keyword,pageNo:page,pageSize:size,tableId};
 const raw=await platformApiGet<Row>('/dwm/metadata-governance/tables/page',query,{signal});
 signal?.throwIfAborted();
 if(!Array.isArray(raw?.rows)||!Array.isArray(raw?.catalogs)||!Number.isFinite(Number(raw.total)))throw new Error('数据表分页返回格式不正确');
 return {rows:raw.rows.map((r:Row)=>snake(r) as LiveMetadataTable),catalogs:raw.catalogs.map((r:Row)=>{const c=snake(r);return {tid:String(c.tid),catalogName:String(c.catalog_name||''),catalogNameEn:String(c.catalog_name_en||''),assetDesc:String(c.asset_desc||''),sourceTableId:String(c.source_table_id||''),dbId:String(c.db_id||'')};}),total:Number(raw.total),page:Number(raw.pageNo||page),size:Number(raw.pageSize||size)};
}
export async function catalogTableColumns(tableId:string):Promise<Row[]>{
 const raw=await platformApiGet<unknown>('/dwm/metadata-governance/tables/columns',{tableId});
 if(!Array.isArray(raw))throw new Error('数据表字段返回格式不正确');
 return raw.map((r:Row)=>snake(r));
}
export async function storedColumns(tableId:string):Promise<Row[]>{return (await readMetadata()).columns.filter(c=>c.table_id===tableId);}
export async function previewMetadataRecords(tableId:string):Promise<Row[]>{const result=await platformApi<Row>('/dst/database/metadata/table/sample-data',{tableId,sampleSize:10},{timeoutMs:30000});const rows=result?.data??result?.rows??result?.items;if(!Array.isArray(rows))throw new Error('数据预览未返回有效记录集合');return rows.slice(0,10);}
export async function saveMetadataRelation(body:Row):Promise<Row>{const {tenantId:_tenant,tenant_id:_snake,...safe}=body;const r=snake(await platformApi<Row>('/dwm/metadata-governance/relations/save',safe));invalidateMetadata();return r;}
export async function deleteMetadataRelation(tid:string){const result=await platformApi<boolean>('/dwm/metadata-governance/relations/delete',{tid});if(result!==true)throw new Error('关系不存在或无权删除');invalidateMetadata();}
export async function discoverMetadataRelations(tableIds:string[],signal?:AbortSignal){if(tableIds.length<2||tableIds.length>300)throw new Error('请选择 2 至 300 张表进行结构关系发现');const result=await platformApi<Row>('/dwm/metadata-governance/relations/discover',{tableIds},{signal,timeoutMs:60000});if(!Array.isArray(result.items))throw new Error('关系识别返回格式不正确');return result.items.map((r:Row)=>snake(r));}
export async function validateMetadataRelation(tid:string,sampleLimit=200){const result=await platformApi<Row>('/dwm/metadata-governance/relations/validate',{tid,sampleLimit},{timeoutMs:60000});invalidateMetadata();return result;}
export const previewPhysicalForeignKey=(tid:string)=>platformApi<Row>('/dwm/metadata-governance/relations/foreign-key/preview',{tid},{timeoutMs:30000});
export async function createPhysicalForeignKey(tid:string,previewHash:string){const result=await platformApi<Row>('/dwm/metadata-governance/relations/foreign-key/create',{tid,previewHash,confirmPhysicalChange:true},{timeoutMs:60000});invalidateMetadata();return result;}
