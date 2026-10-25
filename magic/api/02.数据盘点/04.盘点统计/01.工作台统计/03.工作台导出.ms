{
  "properties": {},
  "id": "1d54e87126614dd88063f247ab8f1176",
  "script": null,
  "groupId": "2cb941922e934cb0984438557189e854",
  "name": "03.工作台导出",
  "path": "/export",
  "createTime": 1792844788332,
  "updateTime": null,
  "parameters": [],
  "options": [],
  "description": "复用统一工作台授权、有效快照去重及接入范围，分页导出真实登记接入报表；只读取当前页接入配置，剔除连接凭据，保留原公共报表接口兼容性。",
  "method": "POST",
  "requestBody": "{\"scope\":\"auto\"}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
import '@/common/workbenchData' as workbenchData
import tableSort
import cn.hutool.json.JSONUtil
var context=workbenchData(body)
var tenantId=context.tenantId
var userId=context.userId
var scopeOrgIds=context.scopeOrgIds
var text=v=>v==null?'':(v+'').trim()
var pageNum=Math.max(1,body?.pageNum==null?1:body.pageNum::int)
var pageSize=Math.min(100,Math.max(1,body?.pageSize==null?20:body.pageSize::int))
var offset=(pageNum-1)*pageSize
var endRow=offset+pageSize
var fields={orgName:'q.org_name',appName:'q.app_name',dbName:'q.db_name',dbType:'q.db_type',
    contactName:'q.contact_name',directoryCount:'q.directory_count',registrationStartTime:'q.registration_start_time',
    annotatedCount:'q.annotated_count',dictionaryCount:'q.dictionary_count',businessCount:'q.business_count',logCount:'q.log_count',
    registrationRate:'q.registration_rate',sourceAssetStatus:'q.source_asset_status',accessedTableCount:'q.accessed_table_count',
    accessStartTime:'q.access_start_time',taskCount:'q.task_count'}
var sortColumn=tableSort.field(body,fields,'orgName')
var sortDirection=tableSort.direction(body)

// 与概览共用授权快照、去重规则和接入任务范围；仅当前页读取低频接入配置。
var cte=context.cte+"""
, type_stats AS (
 SELECT datasource_id,
 SUM(CASE WHEN asset_status=2 AND business_type='字典表' THEN 1 ELSE 0 END) dictionary_count,
 SUM(CASE WHEN asset_status=2 AND business_type='业务表' THEN 1 ELSE 0 END) business_count,
 SUM(CASE WHEN asset_status=2 AND business_type='日志表' THEN 1 ELSE 0 END) log_count,
 SUM(CASE WHEN asset_status>0 AND business_type IN ('字典表','业务表','日志表') THEN 1 ELSE 0 END) access_candidate_count
 FROM table_scope GROUP BY datasource_id
), task_stats AS (
 SELECT datasource_id,COUNT(*) task_count,COUNT(DISTINCT source_table_id) accessed_table_count,
 SUM(CASE WHEN runtime_state='healthy' THEN 1 ELSE 0 END) running_task_count,
 SUM(CASE WHEN runtime_state='error' THEN 1 ELSE 0 END) error_task_count,
 SUM(CASE WHEN runtime_state='idle' THEN 1 ELSE 0 END) idle_task_count,
 MIN(NULLIF(schedule_running_start,'')) access_start_time
 FROM task_scope GROUP BY datasource_id
), metrics AS (
 SELECT s.datasource_id,s.db_name,s.db_type,s.database_type,s.asset_status source_asset_status,
 s.reg_time registration_start_time,a.app_name,COALESCE(NULLIF(o.short_name,''),o.name) org_name,
 COALESCE(NULLIF(u.real_name,''),u.user_name) contact_name,
 COALESCE(ss.table_count,0) directory_count,COALESCE(ss.marked_count,0) annotated_count,
 COALESCE(ss.completed_count,0) completed_count,
 COALESCE(ts.dictionary_count,0) dictionary_count,COALESCE(ts.business_count,0) business_count,
 COALESCE(ts.log_count,0) log_count,
 CASE WHEN s.asset_status>0 THEN COALESCE(ts.access_candidate_count,0) ELSE 0 END access_candidate_count,
 CASE WHEN ss.table_count>0 THEN ROUND(100.0*ss.completed_count/ss.table_count,2) ELSE 0 END registration_rate,
 COALESCE(k.task_count,0) task_count,COALESCE(k.accessed_table_count,0) accessed_table_count,
 COALESCE(k.running_task_count,0) running_task_count,COALESCE(k.error_task_count,0) error_task_count,
 COALESCE(k.idle_task_count,0) idle_task_count,k.access_start_time
 FROM source_scope s
 JOIN db_datasource_t raw ON raw.tid=s.datasource_id AND raw.tenant_id=#{tenantId} AND raw.is_del=0
 LEFT JOIN sym_application_t a ON a.tid=raw.app_id AND a.tenant_id=#{tenantId} AND a.is_del=0
 LEFT JOIN rm_org_t o ON o.id=COALESCE(NULLIF(raw.org_id,''),a.org_id) AND o.tenant_id=#{tenantId} AND o.deleted=0
 LEFT JOIN rm_user_t u ON u.id=raw.created_by AND u.tenant_id=#{tenantId} AND u.deleted=0
 LEFT JOIN source_stats ss ON ss.datasource_id=s.datasource_id
 LEFT JOIN type_stats ts ON ts.datasource_id=s.datasource_id
 LEFT JOIN task_stats k ON k.datasource_id=s.datasource_id
)
"""
var total=db.selectOne(context.cte+' SELECT COUNT(*) total FROM source_scope').total
var order='CASE WHEN '+sortColumn+' IS NULL THEN 1 ELSE 0 END,'+sortColumn+' '+sortDirection+',q.datasource_id'
var list=db.select(cte+', ranked AS (SELECT q.*,ROW_NUMBER() OVER(ORDER BY '+order+') row_no FROM metrics q) SELECT q.*,raw.pool_cfg FROM ranked q JOIN db_datasource_t raw ON raw.tid=q.datasource_id AND raw.tenant_id=#{tenantId} AND raw.is_del=0 WHERE q.row_no>#{offset} AND q.row_no<=#{endRow} ORDER BY q.row_no')
var summary=db.selectOne(cte+"""
 SELECT COUNT(*) datasource_count,COALESCE(SUM(directory_count),0) directory_count,
 COALESCE(SUM(annotated_count),0) annotated_count,COALESCE(SUM(completed_count),0) completed_count,
 COALESCE(SUM(dictionary_count),0) dictionary_count,COALESCE(SUM(business_count),0) business_count,
 COALESCE(SUM(log_count),0) log_count,COALESCE(SUM(accessed_table_count),0) accessed_table_count,
 COALESCE(SUM(running_task_count),0) running_task_count,COALESCE(SUM(error_task_count),0) error_task_count
 FROM metrics
""")
// 只解析当前页低频配置；不补读任何一行，不把含连接凭据的pool_cfg返回浏览器。
var modes={explore:'数据抽取',extract:'数据抽取',extraction:'数据抽取',receive:'数据推送',push:'数据推送',capture:'数据抓取',pull:'数据抓取',upload:'数据上报',report:'数据上报'}
for(row in list) {
    var config={}
    try {config=JSONUtil.parseObj(row.poolCfg||'{}');} catch(ignored) {}
    var mode=text(config.accessMode||config.access_mode||config.dataAccessMode||config.data_access_mode||config.registrationAccessMode).toLowerCase()
    row.accessMode=modes[mode]
    row.registrationEndTime=null
    row.remove('poolCfg');row.remove('pool_cfg')
}

return {pageNum:pageNum,pageSize:pageSize,total:total,list:list,summary:summary,scope:context.scope,workbenchMode:context.mode,
 units:{directoryCount:'张',accessedTableCount:'张'},timeBasis:{registrationStartTime:'数据源首次保存时间',accessStartTime:'现存有效任务最近启动时间中的最早值',registrationEndTime:'尚未记录'}}
