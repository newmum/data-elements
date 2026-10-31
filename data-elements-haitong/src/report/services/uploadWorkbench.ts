import type { DataType, Resource, SourceBook,TimeFormat } from '../core/model';
import { clone } from '../core/model';
import { resourceFromRegion } from '../core/import';
import { prepareValue } from '../core/engine';
import { platformApi, platformSessionToken } from './platformApi';
import { getSessionRevision } from '../../nifi/api/bridgeSession';
import type { ReportContext } from './reportApi';

export interface UploadField { columnName: string; columnComment: string; columnType: string; columnLength?: number; primaryKey: boolean; nullable: boolean; isTimestamp: boolean; standardField?: ''|'DATE'|'DATETIME'; dictionaryRole?: ''|'code'|'label';timeFormat?:TimeFormat;timezone?:string }
export interface UploadTable {
  key: string; tid?: string; fileId: string; fileName: string; sheetName?: string;
  tableName: string; tableNameCn: string; fieldCount: number; recordCount: number;
  fields: UploadField[]; status: 'success' | 'failed'; error?: string;
  startRow?: number; endRow?: number; startColumn?: number; endColumn?: number; legacy?: boolean;
  headerMode?: 'single'|'bilingual'|'none'; dataStartRow?: number;
  parseEncoding?:string; parseDelimiter?:string;
  businessType?: string; normalized?: boolean; normalizationVersion?: number;
}
export interface ReportReceipt { id: string; fileId: string; tableId: string; tableName: string; fileName: string; status: string; rowCount: number; reportedAt: string; message?: string }
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
export const parseUploadedFiles=(fileIds:string[])=>platformApi<{files:Array<{fileId:string;fileName:string;tables:UploadTable[]}>}>(endpoint,{method:'POST',body:{action:'parse',fileIds}});
export const loadReportReceipts=(dbId:string,pageNo=1,pageSize=10,sortField='reportedAt',sortDir='desc')=>platformApi<{list:ReportReceipt[];total:number}>(endpoint,{method:'POST',body:{action:'receipts',dbId,pageNo,pageSize,sortField,sortDir}});
export async function uploadOriginal(file:File):Promise<string>{
  const form=new FormData();form.append('file',file);
  const result=await platformApi<{tid:string}>('/data/file?action=upload',{method:'POST',body:form});
  if(!result.tid)throw new Error('上传未返回文件编号');return result.tid;
}
export const saveUploadManifest=(dbId:string,manifest:UploadManifest,removedTableIds:string[]=[],normalizedTableKeys:string[]=[])=>platformApi<UploadManifest>(endpoint,{method:'POST',body:{...manifest,action:'save',dbId,removedTableIds,workflowVersion:2,normalizedTableKeys}});
export async function readUploadedOriginal(fileId:string,fileName:string):Promise<File>{
  const token=await platformSessionToken(),revision=getSessionRevision();
  const base=import.meta.env.VITE_DATA_ELEMENTS_API_BASE_URL||(import.meta.env.DEV?'/dev-api':'/prod-api');
  const response=await fetch(`${base}/data/download?fileId=${encodeURIComponent(fileId)}`,{headers:{token},signal:AbortSignal.timeout(30000)});
  if(revision!==getSessionRevision())throw new Error('登录身份已变化，请重新打开工作台');
  if(!response.ok)throw new Error('读取已上传原文件失败，请重新上传该文件');
  const contents=await response.blob();
  if(revision!==getSessionRevision())throw new Error('登录身份已变化，请重新打开工作台');
  return new File([contents],fileName);
}
export function tableResource(book:SourceBook,table:UploadTable):Resource{
  const sheet=book.sheets.findIndex(item=>item.name===table.sheetName);
  if(sheet<0||!table.startRow||!table.endRow)throw new Error('该历史记录没有数据区域，请重新上传原文件');
  const resource=resourceFromRegion(book,{id:table.key,sheet,kind:'business',startRow:table.startRow,endRow:table.endRow,
    startCol:table.startColumn||1,endCol:table.endColumn||table.fields.length,header:table.headerMode||'single',dataStart:table.dataStartRow??table.startRow+(table.headerMode==='none'?0:table.headerMode==='bilingual'?2:1),
    name:table.tableNameCn,codeCol:1,labelCol:2,hiddenConfirmed:true});
  resource.id=table.key;resource.source.fileId=table.fileId;resource.schema.name=table.tableNameCn;resource.schema.englishName=table.tableName;
  return applyTableSchema(resource,table);
}
export function applyTableSchema(original:Resource,table:UploadTable):Resource{
  const resource=clone(original);resource.schema.name=table.tableNameCn;resource.schema.englishName=table.tableName;
  if(resource.schema.fields.length!==table.fields.length)throw new Error('数据区域与字段数量不一致，请重新设置解析');
  resource.schema.fields=resource.schema.fields.map((field,index)=>({...field,en:table.fields[index].columnName,zh:table.fields[index].columnComment,
    type:table.fields[index].standardField==='DATE'?'date':table.fields[index].standardField==='DATETIME'?'datetime':fieldType(table.fields[index].columnType),
    nullable:table.fields[index].nullable?'allow':'forbid',pk:table.fields[index].primaryKey,confirmed:true,standardField:table.fields[index].standardField,
    time:{format:table.fields[index].timeFormat||(table.fields[index].standardField==='DATE'||fieldType(table.fields[index].columnType)==='date'?'ymd':'iso'),timezone:table.fields[index].timezone||'Asia/Shanghai'}}));
  resource.schema.timestamp=resource.schema.fields[table.fields.findIndex(field=>field.isTimestamp)]?.id||'';
  resource.savedSchema=clone(resource.schema);return resource;
}
export function fieldType(type:string):DataType{
  if(/timestamp|datetime/.test(type))return 'datetime';if(/^date$/.test(type))return 'date';
  if(/int/.test(type))return 'integer';if(/decimal|float|double|numeric/.test(type))return 'decimal';if(/bool/.test(type))return 'boolean';return 'text';
}
export function normalizedReportFile(resource:Resource):File{
  const quote=(value:unknown)=>'"'+String(value??'').replace(/"/g,'""')+'"';
  const fields=resource.schema.fields;
  const keys=new Set<string>();const primary=fields.flatMap((field,index)=>field.pk?[index]:[]);
  const rows=resource.rows.map(row=>{
    const values=fields.map(field=>{try{
      const value=prepareValue(row.values[field.id],field,resource.schema,[]);
      if(value===null&&(field.nullable==='forbid'||field.pk))throw new Error('不允许为空');return value;
    }catch(cause){throw new Error(`第${row.sourceRow}行 ${field.zh||field.en}：${(cause as Error).message}`);}});
    if(primary.length){const key=JSON.stringify(primary.map(index=>values[index]));if(keys.has(key))throw new Error(`第${row.sourceRow}行 主键重复，请修正数据或调整主键`);keys.add(key);}
    return values.map(quote).join(',');
  });
  if(!rows.length)throw new Error('当前数据表没有可上报的数据');
  const file=new File(['\uFEFF'+[fields.map(field=>quote(field.en)).join(','),...rows].join('\r\n')],resource.schema.englishName+'.csv',{type:'text/csv;charset=utf-8'});
  if(file.size>10*1024*1024)throw new Error('规范化CSV超过10 MB，请拆分数据后上报');
  return file;
}
export async function submitUploadTable(datasourceId:string,table:UploadTable,resource:Resource):Promise<ReportReceipt>{
  if(!table.tid)throw new Error('请先完成第二步并保存数据规范');
  const form=new FormData();form.append('datasourceId',datasourceId);form.append('tableId',table.tid);form.append('fileId',table.fileId);
  form.append('requestId',crypto.randomUUID());form.append('file',normalizedReportFile(resource));
  const result=await platformApi<ReportReceipt>('/dst/database/metadata/report-submit',{method:'POST',body:form});
  if(result.status!=='success')throw new Error(result.message||'上报尚未成功，请查看记录');return result;
}
