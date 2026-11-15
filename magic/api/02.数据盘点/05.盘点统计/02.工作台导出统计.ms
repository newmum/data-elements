{
  "properties": {},
  "id": "46051d94b97c46b6947516d70d0c7906",
  "script": null,
  "groupId": "9ef163a809ec40b59d35eb27d2f584a5",
  "name": "02.工作台导出统计",
  "createTime": 1791879307961,
  "updateTime": 1791879307961,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/management-workbench",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "requestBody": "{\"pageNum\":1,\"pageSize\":20,\"keyword\":\"\",\"registrationState\":\"\",\"sortField\":\"orgName\",\"sortDir\":\"asc\"}",
  "description": "原工作台导出所用分页指标，保留已有统计路由与资源ID；严格按认证租户及角色数据范围查询，ownerOnly只能进一步缩小至当前创建者。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 原工作台导出沿用 POST /dst/statistics/management-workbench；按已授权数据源分页返回真实统计。
// 联系人为数据源创建者；表标注与登记完成独立统计；只显示持久化任务状态。
// 固定3次业务查询（总数、分页、汇总），20/100行不逐项读；全部关联带当前租户。
import tenantRuntime
import dataScope
import tableSort
import cn.hutool.json.JSONUtil
var tenantId=tenantRuntime.id()
if(tenantId==null || (tenantId+'').trim()=='') {exit 401,'请先登录';}
var text=v=>v==null?'':(v+'').trim()
var pageNum=Math.max(1,body?.pageNum==null?1:body.pageNum::int)
var pageSize=Math.min(100,Math.max(1,body?.pageSize==null?20:body.pageSize::int))
var offset=(pageNum-1)*pageSize
var endRow=offset+pageSize
var keyword=text(body?.keyword)
if(keyword.length()>100) {exit 400,'搜索关键字最多100个字符';}
var keywordLike='%'+keyword+'%'
if(body?.ownerOnly!=null && body.ownerOnly!=true && body.ownerOnly!=false) {exit 400,'ownerOnly必须为布尔值';}
var ownerOnly=body?.ownerOnly==true
var state=text(body?.registrationState)
if(state!='' && !['0','1','2'].contains(state)) {exit 400,'登记状态无效';}
var registrationState=state==''?null:state::int
var fields={orgName:'q.org_name',appName:'q.app_name',dbName:'q.db_name',dbType:'q.db_type',
    contactName:'q.contact_name',directoryCount:'q.directory_count',registrationStartTime:'q.registration_start_time',
    annotatedCount:'q.annotated_count',dictionaryCount:'q.dictionary_count',businessCount:'q.business_count',logCount:'q.log_count',
    registrationRate:'q.registration_rate',sourceAssetStatus:'q.source_asset_status',accessedTableCount:'q.accessed_table_count',
    accessStartTime:'q.access_start_time',taskCount:'q.task_count'}
var sortColumn=tableSort.field(body,fields,'orgName')
var sortDirection=tableSort.direction(body)
// 保留当前角色已有数据范围，不按角色名称引入新管理员门槛。
var scope=dataScope.resource('MENU:2079477673186942976')
var scopeOrgIds=scope.organizationIds
var scopeUserId=scope.userId
var whereSql=' WHERE d.tenant_id=#{tenantId} AND d.is_del=0 '
if(scope.scope=='OWNER') {whereSql=whereSql+' AND d.created_by=#{scopeUserId}';}
else if(scope.scope=='ORG') {
    whereSql=whereSql+(scopeOrgIds.size()==0?' AND 1=0':' AND d.org_id IN (#{scopeOrgIds})')
} else if(scope.scope!='ALL') {exit 403,'数据范围无效';}
if(ownerOnly && scope.scope!='OWNER') {whereSql=whereSql+' AND d.created_by=#{scopeUserId}';}
if(keyword!='') {whereSql=whereSql+' AND (d.db_name LIKE #{keywordLike} OR a.app_name LIKE #{keywordLike} OR o.name LIKE #{keywordLike} OR o.short_name LIKE #{keywordLike} OR u.real_name LIKE #{keywordLike})';}
if(state!='') {whereSql=whereSql+' AND coalesce(d.asset_status,0)=#{registrationState}';}
var sourceCte="""
WITH source_scope AS (
 SELECT d.tid datasource_id,d.db_name,d.db_type,d.database_type,d.pool_cfg,
        d.reg_time registration_start_time,d.asset_status source_asset_status,d.table_num physical_table_count,
        a.app_name,coalesce(nullif(o.short_name,''),o.name) org_name,
        coalesce(nullif(u.real_name,''),u.user_name) contact_name
 FROM db_datasource_t d
 LEFT JOIN sym_application_t a ON a.tid=d.app_id AND a.tenant_id=d.tenant_id AND a.is_del=0
 LEFT JOIN rm_org_t o ON o.id=coalesce(nullif(d.org_id,''),a.org_id) AND o.tenant_id=d.tenant_id AND o.deleted=0
 LEFT JOIN rm_user_t u ON u.id=d.created_by AND u.tenant_id=d.tenant_id AND u.deleted=0
"""+whereSql+') '
var total=db.selectOne(sourceCte+' SELECT count(*) total FROM source_scope').total
var metricsCte=sourceCte+"""
, table_stats AS (
 SELECT t.datasource_id,count(*) directory_count,
        sum(case when t.annotated=1 then 1 else 0 end) annotated_count,
        sum(case when t.asset_status=2 then 1 else 0 end) completed_count,
        sum(case when t.asset_status=2 and t.business_type='字典表' then 1 else 0 end) dictionary_count,
        sum(case when t.asset_status=2 and t.business_type='业务表' then 1 else 0 end) business_count,
        sum(case when t.asset_status=2 and t.business_type='日志表' then 1 else 0 end) log_count,
        sum(case when s.source_asset_status>0 and t.asset_status>0 and t.business_type in ('字典表','业务表','日志表') then 1 else 0 end) access_candidate_count
 FROM db_table_t t JOIN source_scope s ON s.datasource_id=t.datasource_id
 WHERE t.tenant_id=#{tenantId} AND t.is_del=0 GROUP BY t.datasource_id
), task_links AS (
 SELECT st.datasource_id,k.source_table_id,k.tid task_id,nullif(k.schedule_running_start,'') access_start_time,
        case when p.status in ('DEPLOY_FAILED','RUN_ERROR') or k.task_status=2 then 2
             when p.status='RUNNING' then 1
             when p.status in ('DEPLOYING','STOPPING') then 3
             when p.id is not null then 0
             when nullif(k.pipeline_id,'') is not null then -1
             when k.task_status in (0,1,2) then k.task_status else -1 end effective_status
 FROM data_access_agg_task_t k
 JOIN db_table_t st ON st.tid=k.source_table_id AND st.tenant_id=k.tenant_id AND st.is_del=0
 JOIN source_scope s ON s.datasource_id=st.datasource_id
 JOIN db_table_t tt ON tt.tid=k.target_table_id AND tt.tenant_id=k.tenant_id AND tt.is_del=0
 JOIN db_datasource_t target_source ON target_source.tid=tt.datasource_id AND target_source.tenant_id=k.tenant_id AND target_source.is_del=0
 LEFT JOIN nifi_pipeline_t p ON p.id=k.pipeline_id AND p.tenant_id=k.tenant_id AND p.is_del=0
 WHERE k.tenant_id=#{tenantId} AND k.is_del=0
   AND (nullif(k.source_db_id,'') is null OR k.source_db_id=st.datasource_id)
   AND (nullif(k.target_db_id,'') is null OR k.target_db_id=tt.datasource_id)
), task_stats AS (
 SELECT datasource_id,count(distinct source_table_id) accessed_table_count,count(*) task_count,
        sum(case when effective_status=1 then 1 else 0 end) running_task_count,
        sum(case when effective_status=2 then 1 else 0 end) error_task_count,
        sum(case when effective_status=0 then 1 else 0 end) idle_task_count,
        sum(case when effective_status=3 then 1 else 0 end) changing_task_count,
        sum(case when effective_status=-1 then 1 else 0 end) missing_pipeline_count,
        min(access_start_time) access_start_time
 FROM task_links GROUP BY datasource_id
), metrics AS (
 SELECT s.*,coalesce(t.directory_count,0) directory_count,coalesce(t.annotated_count,0) annotated_count,
        coalesce(t.completed_count,0) completed_count,coalesce(t.dictionary_count,0) dictionary_count,
        coalesce(t.business_count,0) business_count,coalesce(t.log_count,0) log_count,
        coalesce(t.access_candidate_count,0) access_candidate_count,
        case when t.directory_count>0 then round(100.0*t.completed_count/t.directory_count,2) else 0 end registration_rate,
        coalesce(k.accessed_table_count,0) accessed_table_count,coalesce(k.task_count,0) task_count,
        coalesce(k.running_task_count,0) running_task_count,coalesce(k.error_task_count,0) error_task_count,
        coalesce(k.idle_task_count,0) idle_task_count,coalesce(k.changing_task_count,0) changing_task_count,
        coalesce(k.missing_pipeline_count,0) missing_pipeline_count,k.access_start_time
 FROM source_scope s LEFT JOIN table_stats t ON t.datasource_id=s.datasource_id
 LEFT JOIN task_stats k ON k.datasource_id=s.datasource_id
)
"""
// ROW_NUMBER范围分页兼容MySQL 8与达梦；先对完整授权结果排序，后分页。
var orderSql='CASE WHEN '+sortColumn+' IS NULL THEN 1 ELSE 0 END ASC,'+sortColumn+' '+sortDirection+',q.datasource_id ASC'
var list=db.select(metricsCte+', ranked AS (SELECT q.*,row_number() over(order by '+orderSql+') row_no FROM metrics q) SELECT * FROM ranked WHERE row_no>#{offset} AND row_no<=#{endRow} ORDER BY row_no')
var summary=db.selectOne(metricsCte+"""
 SELECT count(*) datasource_count,coalesce(sum(directory_count),0) directory_count,
        coalesce(sum(annotated_count),0) annotated_count,coalesce(sum(completed_count),0) completed_count,
        coalesce(sum(dictionary_count),0) dictionary_count,coalesce(sum(business_count),0) business_count,
        coalesce(sum(log_count),0) log_count,coalesce(sum(accessed_table_count),0) accessed_table_count,
        coalesce(sum(running_task_count),0) running_task_count,coalesce(sum(error_task_count),0) error_task_count
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
return {pageNum:pageNum,pageSize:pageSize,total:total,list:list,summary:summary,scope:scope.scope,
    units:{directoryCount:'张',accessedTableCount:'张'},timeBasis:{registrationStartTime:'数据源首次保存时间',accessStartTime:'现存任务最近一次启动时间中的最早值',registrationEndTime:'尚未记录'}}
