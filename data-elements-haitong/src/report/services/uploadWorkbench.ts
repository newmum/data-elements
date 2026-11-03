import {reportRandomId} from '../core/browserId';
import type { DataType, Resource, SourceBook,TimeFormat,StandardField,TimeRole } from '../core/model';
import { clone } from '../core/model';
import { resourceFromRegion } from '../core/import';
import { prepareReportFile } from './reportCsv';
import type {PlatformUploadProgress} from '../../api/platformApi';
export {normalizedReportFile,prepareReportFile} from './reportCsv';
import { platformApi, platformSessionToken } from './platformApi';
import { getSessionRevision } from '../../nifi/api/bridgeSession';
import type { ReportContext } from './reportApi';
import {isReportReady,hasReportSuccess} from './reportSchema';
import {reconcilePlatformSessionToken} from '../../services/platformSession';

export interface UploadField { columnName: string; columnComment: string; columnType: string; columnLength?: number; primaryKey: boolean; nullable: boolean; isTimestamp: boolean; standardField?: StandardField; timeRoles?:TimeRole[]; dictionaryRole?: ''|'code'|'label';timeFormat?:TimeFormat;timezone?:string }
export interface UploadTable {
  key: string; tid?: string; fileId: string; fileName: string; sheetName?: string;
  tableName: string; tableNameCn: string; fieldCount: number; recordCount: number;
  fields: UploadField[]; status: 'success' | 'failed'; error?: string;
  startRow?: number; endRow?: number; startColumn?: number; endColumn?: number; legacy?: boolean;
  headerMode?: 'single'|'bilingual'|'none'; dataStartRow?: number;
  parseEncoding?:string; parseDelimiter?:string;
  businessType?: string; normalized?: boolean; normalizationVersion?: number;
  createdAt?: string; updatedAt?: string;
}
export interface ReportReceipt { id: string; fileId: string; tableId: string; tableName: string; fileName: string; status: string; rowCount: number; reportedAt: string; message?: string; registrationSnapshot?:UploadTable }
export interface UploadManifest { revision: string; files: Array<{fileId: string; fileName: string}>; tables: UploadTable[]; failedTables?: UploadTable[]; receipts?: ReportReceipt[] }
export interface UploadContext { context: ReportContext; sources: Array<{id: string; name: string}> }
const endpoint='/dst/database/metadata/report-registration';
export async function loadUploadContext(preferred=''): Promise<UploadContext> {
  await platformSessionToken();
  const [user,tenant,summary]=await Promise.all([
    platformApi<Record<string,string>>('/sym/user/me',{method:'POST',body:{}}),
    platformApi<Record<string,string>>('/sym/tenant/current'),
    platformApi<Array<Record<string,unknown>>|{list:Array<Record<string,unknown>>}>('/dst/database/departmentDataSources',{method:'POST',body:{mode:'summary'}}),
  ]);
  if(!(user.userId||user.tid||user.id)||!(tenant.tenantId||tenant.tid||tenant.id))throw new Error('当前登录身份或租户信息不完整');
  const sources=(Array.isArray(summary)?summary:summary.list).filter(row=>['upload','report'].includes(String(row.accessMode))).map(row=>({id:String(row.tid||row.id),name:String(row.dbName||'未命名数据源')}));
  const selected=preferred?sources.find(source=>source.id===preferred):sources[0];
  if(!selected)throw new Error(preferred?'当前数据源不存在或不是数据上报方式':'请先在源数据管理平台保存一个数据上报数据源');
  return {sources,context:{datasourceId:selected.id,datasourceName:selected.name,user:{...user,userId:user.userId||user.tid||user.id},
    tenant:{tenantId:tenant.tenantId||tenant.tid||tenant.id,tenantName:tenant.tenantName||tenant.name},tables:[],batchCount:0}};
}
export const loadUploadManifest=(dbId:string)=>platformApi<UploadManifest>(endpoint,{method:'POST',body:{action:'list',dbId}});
export interface RegistrationPageQuery { pageNo:number; pageSize:number; search:string; sortField:string; sortDir:'asc'|'desc' }
export interface RegistrationPage { list:Array<{key:string;updatedAt:string}>; total:number; revision:string }
export async function loadRegistrationPage(dbId:string,manifest:UploadManifest,query:RegistrationPageQuery):Promise<RegistrationPage>{
  const result=await platformApi<RegistrationPage>(endpoint,{method:'POST',body:{action:'table-page',dbId,revision:manifest.revision,...query}});
  const keys=new Set(manifest.tables.map(table=>table.key));
  if(result.revision!==manifest.revision)throw new Error('登记信息已更新，请重新打开工作台');
  if(result.list.some(row=>!keys.has(row.key))||new Set(result.list.map(row=>row.key)).size!==result.list.length)throw new Error('登记清单包含不属于当前数据源的数据表');
  return result;
}
export type UploadFile=UploadManifest['files'][number];
export interface RegistrationFileGroup {key:string;fileId:string;fileName:string;tableKeys:string[];failedKeys:string[];updatedAt:string;fieldCount:number;recordCount:number;readyCount:number;locked:boolean}
export interface RegistrationFilePage {revision:string;total:number;list:RegistrationFileGroup[]}
export interface ReportTablePage extends RegistrationPage {summary:{all:number;pending:number;reported:number}}
export async function loadRegistrationFilesPage(dbId:string,manifest:UploadManifest,query:RegistrationPageQuery):Promise<RegistrationFilePage>{
  const result=await platformApi<RegistrationFilePage>(endpoint,{method:'POST',body:{action:'file-page',dbId,revision:manifest.revision,...query,all:true}});
  if(result.revision!==manifest.revision)throw new Error('登记信息已更新，请重新打开工作台');
  if(result.list.length!==result.total||result.total>201)throw new Error('原始文件清单不完整，请重新加载');
  const files=new Set(manifest.files.map(file=>file.fileId)),tables=new Map(manifest.tables.map(table=>[table.key,table])),failed=new Map((manifest.failedTables||[]).map(table=>[table.key,table]));
  const groups=new Set<string>(),keys=new Set<string>();
  for(const group of result.list){
    if(groups.has(group.key)||group.fileId&&!files.has(group.fileId))throw new Error('文件清单包含不属于当前数据源的文件');groups.add(group.key);
    for(const key of group.tableKeys){const row=tables.get(key);if(!row||row.fileId!==group.fileId||keys.has(key))throw new Error('文件清单包含无效或跨文件的数据表');keys.add(key);}
    for(const key of group.failedKeys){const row=failed.get(key);if(!row||row.fileId!==group.fileId||keys.has(key))throw new Error('解析清单包含不属于当前文件的区域');keys.add(key);}
  }
  return result;
}
export async function loadReportTablesPage(dbId:string,manifest:UploadManifest,query:RegistrationPageQuery,reportStatus='all'):Promise<ReportTablePage>{
  const result=await platformApi<ReportTablePage>(endpoint,{method:'POST',body:{action:'report-page',dbId,revision:manifest.revision,...query,reportStatus}});
  if(result.revision!==manifest.revision)throw new Error('登记信息已更新，请重新打开工作台');
  const tables=new Map(manifest.tables.map(table=>[table.key,table])),seen=new Set<string>();
  for(const row of result.list){const table=tables.get(row.key);if(!table||seen.has(row.key)||!(isReportReady(table,manifest.tables)||hasReportSuccess(table,manifest.receipts)))throw new Error('上报清单包含未规范或无归属的数据表');seen.add(row.key);}
  return result;
}
// The server reads each owned object within its own 20s deadline. A batch must
// also allow for parsing, while keeping the same single ownership-checked API.
export const parseUploadedFiles=(fileIds:string[])=>platformApi<{files:Array<{fileId:string;fileName:string;tables:UploadTable[]}>}>(endpoint,{method:'POST',body:{action:'parse',fileIds},timeoutMs:30_000*Math.min(20,Math.max(1,fileIds.length))});
export const loadReportReceipts=(dbId:string,pageNo=1,pageSize=10,sortField='reportedAt',sortDir='desc')=>platformApi<{list:ReportReceipt[];total:number}>(endpoint,{method:'POST',body:{action:'receipts',dbId,pageNo,pageSize,sortField,sortDir}});
export async function uploadOriginal(file:File):Promise<string>{
  const form=new FormData();form.append('file',file);
  const result=await platformApi<{tid:string}>('/data/file?action=upload',{method:'POST',body:form});
  if(!result.tid)throw new Error('上传未返回文件编号');return result.tid;
}
export const saveUploadManifest=(dbId:string,manifest:UploadManifest,removedTableIds:string[]=[],normalizedTableKeys:string[]=[])=>platformApi<UploadManifest>(endpoint,{method:'POST',body:{...manifest,action:'save',dbId,removedTableIds,workflowVersion:2,normalizedTableKeys}});
export async function readUploadedOriginal(fileId:string,fileName:string):Promise<File>{
  const revision=getSessionRevision(),token=await platformSessionToken();
  if(!reconcilePlatformSessionToken(token,revision))throw new Error('登录会话已切换，请重新打开数据预览');
  const base=import.meta.env.VITE_DATA_ELEMENTS_API_BASE_URL||(import.meta.env.DEV?'/dev-api':'/prod-api');
  const response=await fetch(`${base}/data/download?fileId=${encodeURIComponent(fileId)}`,{headers:{token},signal:AbortSignal.timeout(30000)});
  if(revision!==getSessionRevision())throw new Error('登录身份已变化，请重新打开工作台');
  if(!response.ok)throw new Error('读取已上传原文件失败，请重新上传该文件');
  const contents=await response.blob();
  if(revision!==getSessionRevision())throw new Error('登录身份已变化，请重新打开工作台');
  return new File([contents],fileName);
}
export function tableResource(book:SourceBook,table:UploadTable):Resource{
  return resourceForTable(book,table);
}
export function tableSample(book:SourceBook,table:UploadTable,limit=10):Resource{
  if(!Number.isInteger(limit)||limit<1||limit>10)throw new Error('原始样例最多读取10条');
  return resourceForTable(book,table,limit);
}
function resourceForTable(book:SourceBook,table:UploadTable,rowLimit=Infinity):Resource{
  const sheet=book.sheets.findIndex(item=>item.name===table.sheetName);
  if(sheet<0||!table.startRow||!table.endRow)throw new Error('该历史记录没有数据区域，请重新上传原文件');
  const resource=resourceFromRegion(book,{id:table.key,sheet,kind:'business',startRow:table.startRow,endRow:table.endRow,
    startCol:table.startColumn||1,endCol:table.endColumn||table.fields.length,header:table.headerMode||'single',dataStart:table.dataStartRow??table.startRow+(table.headerMode==='none'?0:table.headerMode==='bilingual'?2:1),
    name:table.tableNameCn,codeCol:1,labelCol:2,hiddenConfirmed:true},false,rowLimit);
  resource.id=table.key;resource.source.fileId=table.fileId;resource.schema.name=table.tableNameCn;resource.schema.englishName=table.tableName;
  return applyTableSchema(resource,table);
}
export function applyTableSchema(original:Resource,table:UploadTable):Resource{
  const resource=clone(original);resource.schema.name=table.tableNameCn;resource.schema.englishName=table.tableName;
  if(resource.schema.fields.length!==table.fields.length)throw new Error('数据区域与字段数量不一致，请重新设置解析');
  resource.schema.fields=resource.schema.fields.map((field,index)=>({...field,en:table.fields[index].columnName,zh:table.fields[index].columnComment,
    type:table.fields[index].standardField==='DATE'?'date':table.fields[index].standardField==='DATETIME'?'datetime':['LXDH','SFZH'].includes(table.fields[index].standardField||'')?'text':fieldType(table.fields[index].columnType),
    nullable:table.fields[index].nullable?'allow':'forbid',pk:table.fields[index].primaryKey,confirmed:true,standardField:table.fields[index].standardField,
    time:{format:table.fields[index].timeFormat||(table.fields[index].standardField==='DATE'||fieldType(table.fields[index].columnType)==='date'?'ymd':'iso'),timezone:table.fields[index].timezone||'Asia/Shanghai'}}));
  resource.schema.timestamp=resource.schema.fields[table.fields.findIndex(field=>field.isTimestamp)]?.id||'';
  resource.savedSchema=clone(resource.schema);return resource;
}
export function fieldType(type:string):DataType{
  if(/timestamp|datetime/.test(type))return 'datetime';if(/^date$/.test(type))return 'date';
  if(/int/.test(type))return 'integer';if(/decimal|float|double|numeric/.test(type))return 'decimal';if(/bool/.test(type))return 'boolean';return 'text';
}
export async function submitUploadTable(datasourceId:string,table:UploadTable,resource:Resource,options:{file?:File;signal?:AbortSignal;onUploadProgress?:(progress:PlatformUploadProgress)=>void}={}):Promise<ReportReceipt>{
  if(!table.tid)throw new Error('请先完成第二步并保存数据规范');
  const form=new FormData();form.append('datasourceId',datasourceId);form.append('tableId',table.tid);form.append('fileId',table.fileId);
  form.append('requestId',reportRandomId());form.append('file',options.file||await prepareReportFile(resource,undefined,options.signal));
  const result=await platformApi<ReportReceipt>('/dst/database/metadata/report-submit',{method:'POST',body:form,timeoutMs:120_000,signal:options.signal,onUploadProgress:options.onUploadProgress});
  if(result.status!=='success')throw new Error(result.message||'上报尚未成功，请查看记录');return result;
}
