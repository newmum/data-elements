import { PlatformApiError, platformApi, platformApiGet } from '../shared/platformApi';
import { dataPlatformHomeUrl } from '../shared/platformSession';
import { getSession, type Row } from './api';
import { datasourceRegistrationApi, draftFromDetail, makeJdbcUrl, type SourceChoices, type SourceDraft } from './datasourceRegistrationApi';
export const engineToBackend:Record<string,string>={DM8:'dameng',MYSQL:'mysql',POSTGRESQL:'postgres',ORACLE:'oracle',SQLSERVER:'sqlserver'};
const engineFromBackend:Record<string,string>={dameng:'DM8',mysql:'MYSQL',postgres:'POSTGRESQL',postgresql:'POSTGRESQL',oracle:'ORACLE',sqlserver:'SQLSERVER'};
export const rowsOf=(v:unknown):Row[]=>Array.isArray(v)?v:v&&typeof v==='object'?rowsOf((v as Row).list??(v as Row).rows??(v as Row).items??(v as Row).data):[];
/** Translate driver/transport failures for the connection-test UI without exposing raw JDBC diagnostics. */
export function connectionFailureMessage(value:unknown):string {
 if(value instanceof PlatformApiError&&value.timedOut)return '连接测试请求超时，后端尚未返回结果，请检查服务状态和数据库响应时间。';
 const raw=value instanceof Error?value.message:typeof value==='string'?value:value&&typeof value==='object'?String((value as Row).error||(value as Row).message||''):'';
 const detail=raw.trim();
 if(/尚未登录|登录已失效|登录态|认证失败|无权限|没有权限/.test(detail))return detail;
 if(/request timed out|read timed out|connect timed out|connection timed out|timeout|timed out|请求超时/i.test(detail))return '连接测试超时，请检查数据库地址、端口、网络和数据库响应时间。';
 if(/access denied|authentication failed|password authentication failed|invalid username|invalid password|login failed|ora-01017|用户名或密码|账号或密码|密码错误/i.test(detail))return '数据库账号或密码不正确，请核对连接凭据。';
 if(/unknown database|database .* does not exist|database .* not found|ora-12514|ora-12505|service name.*unknown|数据库不存在|服务名不存在/i.test(detail))return '数据库或服务名不存在，请核对数据库名称、实例名和服务名。';
 if(/no suitable driver|classnotfoundexception|driver.*not found|驱动.*缺失/i.test(detail))return '当前服务缺少对应数据库驱动，请联系管理员检查驱动配置。';
 if(/ssl|tls|certificate|证书/i.test(detail))return '安全连接校验失败，请检查 TLS 设置和证书配置。';
 if(/permission denied|insufficient privileges|ora-01031|权限不足/i.test(detail))return '数据库账号缺少连接或读取权限，请检查授权。';
 if(/failed to fetch|networkerror|无法连接数据中台|后端服务无法访问/i.test(detail))return '后端服务暂时无法访问，请检查服务状态后重试。';
 if(/connection refused|communications link failure|could not connect|unable to connect|no route to host|network is unreachable|connection reset|ora-12541|连接被拒绝|数据库连接失败/i.test(detail))return '数据库连接失败，请检查地址、端口、防火墙、网络和数据库监听服务。';
 if(/[\u3400-\u9fff]/.test(detail)&&!/[A-Za-z]{3,}/.test(detail))return detail;
 return '连接测试未通过，请检查连接配置；如仍失败，请联系管理员查看服务端日志。';
}
/** Explicit presentation projection: never put credentials in table search/export rows. */
export function sourceRow(r:Row):Row {
 const id=String(r.tid||r.id||'');const disabled=String(r.isEnable)==='0';
 const connected=String(r.connectionStatus||'').toLowerCase();
 return {id,name:String(r.dbName||r.db_name||''),code:id,engine:engineFromBackend[String(r.dbType||r.db_type||'').toLowerCase()]||(String(r.dbType||r.db_type||'').toUpperCase()||'OTHER'),backendType:String(r.dbType||r.db_type||''),typeDescription:({ftp:'文件服务',sftp:'文件服务',api:'接口数据源',kafka:'消息流',minio:'对象存储',mongodb:'文档数据库',elasticsearch:'搜索与分析引擎'} as Record<string,string>)[String(r.dbType||r.db_type||'').toLowerCase()]||((r.dbType||r.db_type)?'关系型数据库':'接口未提供类型'),status:disabled?'DISABLED':connected==='success'?'CONNECTED':connected==='failed'||connected==='error'?'ERROR':'REGISTERED',connection:{host:r.host||'',port:r.port||'',database:r.database||'',schema:r.schema||''},domain:'',owner:r.contactName||r.orgName||'',orgId:r.orgId||r.org_id||'',orgName:r.orgName||r.org_name||'',appId:r.appId||r.app_id||'',businessSystem:r.appName||r.app_name||'',network:r.storageDomain||r.SSWL||'',description:r.assetDesc||'',entityCount:Number(r.collectedTableCount??r.tableNum??0),fieldCount:Number(r.fieldCount??r.collectedFieldCount??0),catalogCount:Number(r.dataCatalogNum??r.catalogNum??0),lastCollectedAt:r.lastCollectedAt||r.metadataCollectedAt||null,updatedAt:r.updatedTime||null,last_test_at:r.lastTestAt||null};
}
/** Permission presentation is based on the platform account, never the local preview identity. */
export function canManageSources() {
 const user=getSession()?.user;
 return Array.isArray(user?.roles)&&user.roles.includes('admin') || Array.isArray(user?.perms)&&user.perms.some((p:unknown)=>/dst[:/]database.*(save|edit|manage)/i.test(String(p)));
}
let choicesCache:{tenant:string;promise:Promise<SourceChoices>}|undefined;
const flattenOrganizations=(options:SourceChoices['organizations']):SourceChoices['organizations']=>options.flatMap(o=>[{...o,children:undefined},...flattenOrganizations(o.children||[])]);
export function sourceChoices():Promise<SourceChoices> {
 const tenant=String(getSession()?.tenant?.tid||getSession()?.tenant?.tenantId||'');
 if(!choicesCache||choicesCache.tenant!==tenant){const promise=datasourceRegistrationApi.choices().then(c=>({...c,organizations:flattenOrganizations(c.organizations)}));choicesCache={tenant,promise};promise.catch(()=>{if(choicesCache?.promise===promise)choicesCache=undefined;});}
 return choicesCache.promise;
}
export async function listSources(signal?:AbortSignal):Promise<Row[]> {
 const result:Row[]=[];const seen=new Set<string>();
 for(let pageNum=1;pageNum<=1000;pageNum++){
  const data=await platformApi<Row>('/dst/database/page',{pageNum,pageSize:1000,conditions:[],sortField:'updatedTime',sortDir:'desc',includeMetadataCounts:true},{signal});
  const page=rowsOf(data);for(const raw of page){const row=sourceRow(raw);if(row.id&&!seen.has(row.id)){seen.add(row.id);result.push(row);}}
  if(page.length<1000 || (Number(data.total)>0&&result.length>=Number(data.total)))return result;
 }
 throw new Error('数据源列表超出读取范围，请缩小查询条件');
}
export function formFromDraft(draft:SourceDraft,id?:string):Row {
 return {id,name:draft.dbName,code:id||'',engine:engineFromBackend[draft.dbType.toLowerCase()]||draft.dbType.toUpperCase(),backendType:draft.dbType,appId:draft.appId,orgId:draft.orgId,storageDomain:draft.storageDomain,owner:draft.contactName||'',description:draft.assetDesc||'',connection:{host:draft.host||'',port:draft.port||undefined,database:draft.database||'',schema:draft.schema||'',username:draft.username||'',password:'',jdbcURL:draft.jdbcURL||'',jdbcType:draft.jdbcType||'serviceName',serviceName:draft.serviceName||''},storedSecretFields:draft.storedSecretFields||[]};
}
export function draftFromForm(v:Row,existing?:Row|null):SourceDraft {
 const dbType=existing?.backendType||engineToBackend[v.engine];
 if(!dbType)throw new Error('此数据库类型尚未接入共享后端，不能保存为已连接数据源');
 const c=v.connection||{};
 const draft:SourceDraft={dbName:String(v.name||''),dbType,appId:v.appId,orgId:v.orgId,storageDomain:v.storageDomain,contactName:v.owner||'',assetDesc:v.description||'',host:c.host,port:c.port,database:c.database,schema:c.schema,username:c.username,password:c.password,jdbcType:c.jdbcType||'serviceName',serviceName:c.serviceName};
 const generated=makeJdbcUrl(draft),before=existing?.connection;
 const changedAddress=before&&['host','port','database','jdbcType','serviceName'].some(key=>String(before[key]||'')!==String(c[key]||''));
 const previousGenerated=before?makeJdbcUrl({...draft,...before}):'';
 if(changedAddress&&c.jdbcURL&&c.jdbcURL===before.jdbcURL&&before.jdbcURL!==previousGenerated)throw new Error('地址已修改，请同步更新自定义 JDBC 地址，或清空 JDBC 地址以自动生成');
 draft.jdbcURL=changedAddress&&c.jdbcURL===previousGenerated?generated:c.jdbcURL||generated;
 if(!draft.jdbcURL)throw new Error('请补全数据库地址、端口和数据库名称');
 return draft;
}
export async function editSource(id:string):Promise<Row> {
 return formFromDraft(draftFromDetail(await datasourceRegistrationApi.detail(id)),id);
}
export async function readSource(id:string):Promise<Row> {
 return sourceRow(await datasourceRegistrationApi.detail(id));
}
export function matchesSourceProjection(projected:Row,confirmed:Row) {
 return ['id','name','orgId','appId','network','description'].every(key=>String(projected[key]||'')===String(confirmed[key]||''));
}
export const saveSource=(v:Row,existing?:Row|null)=>datasourceRegistrationApi.save(draftFromForm(v,existing),existing?.id);
export async function testSource(id:string):Promise<void> {
 try {
  const result=await datasourceRegistrationApi.testSaved(id);
  if(result.connected!==true)throw new Error(connectionFailureMessage(result.error||result.message));
 } catch(error) { throw new Error(connectionFailureMessage(error)); }
}
export interface SourceTablePage {rows:Row[];total:number;page:number;size:number}
const tableRow=(t:Row,id:string):Row=>({id:String(t.tid||t.tableId||''),sourceId:id,name:String(t.tableNameEn||t.table_name_en||t.tableName||t.table_name||''),displayName:String(t.tableNameCn||t.table_name_cn||t.tableComment||t.tableName||''),kind:/view/i.test(String(t.tableType||t.table_type||''))?'view':'table',fieldCount:Number(t.fieldCount??t.field_count??0)});
const asTablePage=(raw:Row,id:string,page:number,size:number):SourceTablePage=>({rows:rowsOf(raw).map(t=>tableRow(t,id)).filter(t=>t.id),total:Number(raw.total??0),page:Number(raw.pageNo??page),size:Number(raw.pageSize??size)});
export async function sourceTablesPage(id:string,page=1,size=15,keyword=''):Promise<SourceTablePage> {
 const raw=await platformApiGet<Row>('/dst/database/metadata/tables/saved',{dbId:id,pageNo:page,pageSize:size,...(keyword?{keyword}:{})});
 return asTablePage(raw,id,page,size);
}
export async function addedSourceTablesPage(id:string,page=1,size=15):Promise<SourceTablePage> {
 const raw=await platformApiGet<Row>('/dst/database/metadata/tables/collection/additions',{dbId:id,pageNo:page,pageSize:size});
 return asTablePage(raw,id,page,size);
}
export async function missingSourceTablesPage(id:string,page=1,size=15):Promise<SourceTablePage> {
 const raw=await platformApiGet<Row>('/dst/database/metadata/tables/collection/changes',{dbId:id,pageNo:page,pageSize:size});
 return asTablePage(raw,id,page,size);
}
export interface CollectionJob {exists?:boolean;jobId?:string;datasourceId?:string;status:string;phase?:string;progressPercent?:number;totalCount?:number;persistedCount?:number;addedCount?:number;unchangedCount?:number;deletedCount?:number;startedAt?:string;finishedAt?:string;previousSuccessfulAt?:string;savedBeforeCount?:number;error?:string}
export const collectionStatus=(id:string,jobId?:string)=>platformApiGet<CollectionJob>('/dst/database/metadata/tables/collection/status',{dbId:id,jobId});
/** Fetch list-row statuses in bounded batches; the single-source route remains for detail polling. */
export async function collectionStatuses(ids:string[],signal?:AbortSignal):Promise<CollectionJob[]> {
 const unique=[...new Set(ids.filter(Boolean))];
 const jobs:CollectionJob[]=[];
 for(let start=0;start<unique.length;start+=500){
  const batch=unique.slice(start,start+500);
  const result=await platformApi<{items:CollectionJob[]}>('/dst/database/metadata/tables/collection/status/batch',{dbIds:batch},{signal});
  if(!Array.isArray(result?.items))throw new Error('采集任务批量状态返回格式不正确');
  const bySource=new Map(result.items.map(job=>[String(job.datasourceId||''),job]));
  if(batch.some(id=>!bySource.has(id)))throw new Error('采集任务批量状态缺少数据源，请刷新后重试');
  jobs.push(...batch.map(id=>bySource.get(id)!));
 }
 return jobs;
}
export const startCollection=(id:string,refresh:boolean)=>platformApi<CollectionJob>('/dst/database/metadata/tables/collection/start',{dbId:id,mode:refresh?'REFRESH':'INITIAL'});
export const collectTableFields=(id:string,force=false)=>platformApi<unknown>('/dst/database/metadata/collectColumns',{tableId:id,...(force?{force:true}:{})},{timeoutMs:120000});
export const sharedSourceApi=datasourceRegistrationApi;
export function openPlatformCatalog() {const url=new URL(dataPlatformHomeUrl(),location.href);url.hash='/register/register-sjml/list';location.assign(url.href);}
