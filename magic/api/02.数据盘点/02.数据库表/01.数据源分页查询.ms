{
  "properties": {},
  "id": "datapd_database_page_01",
  "script": null,
  "groupId": "datapd_database_group_01",
  "name": "01.数据源分页查询",
  "createTime": null,
  "updateTime": 1790467385060,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"pageNum\": 1,\n  \"pageSize\": 20,\n  \"conditions\": [\n    {\"field\": \"assetType\", \"type\": \"match\", \"value\": \"db\"},\n    {\"field\": \"dbType\", \"type\": \"match\", \"value\": \"mysql\"},\n    {\"field\": \"registrationState\", \"type\": \"match\", \"value\": \"pending\"}\n  ],\n  \"sortField\": \"updatedTime\",\n  \"sortDir\": \"desc\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\"pageNum\":1,\"pageSize\":20,\"total\":0,\"list\":[]}",
  "description": "数据源分页查询（POST /dst/database/page）。Elasticsearch 负责关键字和组合筛选，控制库 db_datasource_t 是数据源资产存在性、租户边界与状态的事实来源；每次结果采用一次批量回查校验，过滤 ES 中残留的失效投影；使用请求参数 includeMetadataCounts、pageNum、pageSize、conditions、sortField、sortDir。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// Authoritative, scoped and bounded data-source page. Aggregates are sorted before LIMIT.
import tenantRuntime
import tableSort
import cn.hutool.json.JSONUtil
import java.util.List
var tenantId=tenantRuntime.id()
if(tenantId==null || (tenantId+'').trim()=='') {exit 401,'请先登录';}
var text=v=>v==null?'':(v+'').trim()
var quote=v=>"'"+text(v).replace("'","''")+"'"
var pageNum=Math.max(1,body?.pageNum==null?1:body.pageNum::int)
var pageSize=Math.min(100,Math.max(1,body?.pageSize==null?20:body.pageSize::int))
var fields={dbName:'d.db_name',appName:'a.app_name',dbType:'d.db_type',appId:'d.app_id',orgId:'d.org_id',
    createdTime:'d.created_time',updatedTime:'d.updated_time',regTime:'d.reg_time',
    tableNum:'coalesce(d.table_num,0)',name:'d.db_name',entityCount:'coalesce(stats.table_count,0)',lastCollectedAt:'jobs.finished_time',catalogNum:'coalesce(d.data_catalog_num,0)',
    collectedTableCount:'coalesce(stats.table_count,0)',fieldCount:'coalesce(stats.field_count,0)',
    collectedFieldCount:'coalesce(stats.field_count,0)',registerTableNum:'coalesce(stats.registered_count,0)',
    assetStatus:'d.asset_status',flowStatus:'d.flow_status',tid:'d.tid',storageDomain:'d.storage_domain',isEnable:'d.is_enable',connectionStatus:'d.connection_status'}
var whereSql=' WHERE d.tenant_id=#{tenantId} AND ifnull(d.is_del,0)=0 '
// 数据源列表按当前行业库和 tenant_id 查询，不再叠加 OWNER/ORG 角色范围。
var authorizedWhere=whereSql;
for(condition in (body?.conditions==null?[]:body.conditions)) {
    if(condition==null || text(condition.value)=='') {continue;}
    var field=text(condition.field)
    var value=condition.value
    var type=text(condition.type)
    if(field=='assetType') {if(text(value)!='db') {whereSql=whereSql+' AND 1=0';} continue;}
    if(field=='keyword') {
        var pattern='%'+text(value).toLowerCase().replace('!','!!').replace('%','!%').replace('_','!_')+'%'
        whereSql=whereSql+" AND (LOWER(d.db_name) LIKE "+quote(pattern)+" ESCAPE '!' OR LOWER(a.app_name) LIKE "+quote(pattern)+" ESCAPE '!')"
        continue
    }
    if(field=='registrationState') {
        var state=text(value)=='completed'?2:text(value)=='incomplete'?1:text(value)=='pending'?0:-1
        whereSql=whereSql+' AND coalesce(d.asset_status,0)='+state
        continue
    }
    if(field=='collectionState') {whereSql=whereSql+(text(value)=='collected'?' AND coalesce(stats.table_count,0)>0':' AND coalesce(stats.table_count,0)=0');continue;}
    var expression=fields[field]
    if(expression==null) {exit 400,'不支持的数据源筛选字段：'+field;}
    if(type=='in') {
        if(!(value instanceof List) || value.size()>1000) {exit 400,'筛选数组最多 1000 项';}
        whereSql=whereSql+(value.size()==0?' AND 1=0':' AND '+expression+' IN ('+value.map(quote).join(',')+')')
    } else if(type=='notPrefix') {whereSql=whereSql+' AND '+expression+' NOT LIKE '+quote(text(value)+'%');}
    else if(type=='like') {whereSql=whereSql+' AND '+expression+' LIKE '+quote('%'+text(value)+'%');}
    else if(type=='' || type=='match' || type=='eq') {whereSql=whereSql+' AND '+expression+'='+quote(value);}
    else {exit 400,'不支持的数据源筛选方式';}
}
var baseFrom=' FROM db_datasource_t d LEFT JOIN sym_application_t a ON a.tid=d.app_id AND a.tenant_id=d.tenant_id AND ifnull(a.is_del,0)=0 '
var fromSql=baseFrom+"""
LEFT JOIN (
    SELECT t.datasource_id,COUNT(DISTINCT t.tid) table_count,COUNT(DISTINCT c.tid) field_count,
           COUNT(DISTINCT CASE WHEN t.asset_status>0 THEN t.tid END) registered_count
    FROM db_table_t t LEFT JOIN db_table_column_t c ON c.table_id=t.tid
         AND c.tenant_id=#{tenantId} AND ifnull(c.is_del,0)=0
    WHERE t.tenant_id=#{tenantId} AND ifnull(t.is_del,0)=0 GROUP BY t.datasource_id
) stats ON stats.datasource_id=d.tid
LEFT JOIN (SELECT datasource_id,MAX(finished_time) finished_time FROM metadata_table_collection_job_t WHERE tenant_id=#{tenantId} AND is_del=0 AND status='SUCCEEDED' GROUP BY datasource_id) jobs ON CAST(jobs.datasource_id AS CHAR)=CAST(d.tid AS CHAR)
"""
var total=db.selectOne('SELECT COUNT(*) total'+fromSql+whereSql).total
var summary=db.selectOne('SELECT COUNT(*) source_count,COALESCE(SUM(stats.table_count),0) table_count,COALESCE(SUM(stats.field_count),0) field_count,COUNT(CASE WHEN stats.table_count>0 THEN 1 END) collected_count,COUNT(CASE WHEN d.connection_status IN (\'failed\',\'error\') OR d.connection_status IS NULL THEN 1 END) attention_count'+fromSql+authorizedWhere);
var distributions=db.select('SELECT d.db_type, d.storage_domain,COUNT(*) source_count'+baseFrom+authorizedWhere+' GROUP BY d.db_type,d.storage_domain');
var sortColumn=tableSort.field(body,fields,'updatedTime')
var sortDirection=tableSort.direction(body)
var offset=(pageNum-1)*pageSize
var list=db.select("""
SELECT d.tid,d.db_name,d.db_type,d.app_id,d.org_id,d.asset_status,d.flow_status,
       d.created_time,d.updated_time,d.reg_time,d.table_num,d.data_catalog_num,d.pool_cfg,
       d.remark,d.is_enable,d.connection_status,d.database_type,d.storage_domain,d.node_id,d.db_version,
       a.app_name,jobs.finished_time lastCollectedAt,coalesce(stats.table_count,0) collectedTableCount,
       coalesce(stats.field_count,0) fieldCount,coalesce(stats.registered_count,0) registerTableNum
"""+fromSql+whereSql+' ORDER BY CASE WHEN '+sortColumn+' IS NULL THEN 1 ELSE 0 END ASC,'+sortColumn+' '+sortDirection+',d.tid ASC LIMIT #{pageSize} OFFSET #{offset}')
for(row in list) {
    var pool={}
    try {pool=JSONUtil.parseObj(row.poolCfg||'{}');} catch(ignored) {}
    row.database=pool.database||pool.schema||''
    row.catalogNum=row.dataCatalogNum||0
    row.tableNum=row.tableNum||0
    row.collectedFieldCount=row.fieldCount
    row.displayStatus=row.assetStatus||0
    row.id=row.tid
    row.assetType='db'
    row.remove('poolCfg');row.remove('pool_cfg')
}
return {pageNum:pageNum,pageSize:pageSize,total:total,list:list,summary:summary,distributions:distributions}
