{
  "properties": {},
  "id": "cdba14f35b564766aac8555b06c77417",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "20.工作台数据范围",
  "path": "/workbenchData",
  "createTime": 1792844788329,
  "updateTime": null,
  "parameters": [
    {
      "name": "payload",
      "value": null,
      "description": "当前视图、列表类型及分页条件；不接受前端租户或用户标识",
      "required": true,
      "dataType": "Object",
      "type": "java.util.Map",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "options": [],
  "description": "解析当前认证租户、有效应用角色及工作台责任范围，系统管理员可查询全局；警种管理员与登记员按已有ORG或OWNER范围查询；返回复用的授权SQL，不读取来源业务库。"
}
================================
// 采集作业与历史资产ID可能使用不同MySQL排序规则，来源关联键按同一连接字符类型归一，不修改原表结构。
// 工作台共用范围：只有当前应用的有效 admin 角色拥有全局视图。
// 责任范围沿用角色配置；ALL 用户的“我负责的”进一步限制为本人创建的数据源。
import tenantRuntime
import tenantAccounts
import dataScope
var tenantId = tenantRuntime.id()
if (tenantId == null || (tenantId + '').trim() == '') { exit 401, '请重新登录后查看工作台' }
var decision = dataScope.resource('MENU:2079477673186942976')
var userId = decision.userId
var roleIds = decision.roleIds
var scopeOrgIds = decision.organizationIds
var appId = tenantAccounts.session().get('appId')
if (userId == null || appId == null || roleIds == null || roleIds.size() == 0) { exit 403, '当前账号没有有效的工作台角色' }
var roles = db.select("""
    SELECT r.code_num FROM rm_role_t r
     WHERE r.tenant_id=#{tenantId} AND r.app_id=#{appId}
       AND r.id IN (#{roleIds}) AND r.deleted=0 AND r.status=0
""")
if (roles.size() == 0) { exit 403, '当前账号没有当前应用的有效工作台角色' }
var canViewAll = roles.filter(r => r.codeNum == 'admin').size() > 0
var mode = payload?.scope == null ? 'auto' : payload.scope + ''
if (!['auto','all','mine'].contains(mode)) { exit 400, '工作台视图参数无效' }
if (mode == 'auto') { mode = canViewAll ? 'all' : 'mine' }
if (mode == 'all' && !canViewAll) { exit 403, '仅系统管理员可以查看全局视图' }
var effectiveScope = mode == 'all' ? 'ALL' : decision.scope
var sourceWhere = ' WHERE d.tenant_id=#{tenantId} AND d.is_del=0 '
if (effectiveScope == 'OWNER') { sourceWhere = sourceWhere + ' AND d.created_by=#{userId} ' }
else if (effectiveScope == 'ORG') {
    sourceWhere = sourceWhere + (scopeOrgIds.size() == 0 ? ' AND 1=0 ' : ' AND d.org_id IN (#{scopeOrgIds}) ')
} else if (effectiveScope != 'ALL') { exit 403, '工作台数据范围无效' }
var responsibilityWhere = mode == 'mine' && effectiveScope == 'ALL' ? ' WHERE d.created_by=#{userId} ' : ''
var cte = """
WITH accessible_sources AS (
 SELECT d.tid datasource_id,d.db_name,d.db_type,d.database_type,d.org_id,d.created_by,
        d.asset_status,d.connection_status,d.show_connect,d.reg_time,d.created_time,d.updated_time
 FROM db_datasource_t d
""" + sourceWhere + """
) , source_scope AS (SELECT d.*,CAST(d.datasource_id AS CHAR(64)) source_key FROM accessible_sources d
""" + responsibilityWhere + """
), table_ranked AS (
 SELECT t.tid,t.datasource_id,t.table_name,t.table_name_cn,t.table_comment,t.table_type,
        t.business_type,t.business_type_reason,t.annotated,t.asset_status,t.field_count,t.timestamp_field,
        t.created_time,t.updated_time,
        CASE WHEN t.asset_status=2 OR
          (t.business_type='字典表' AND INSTR(REPLACE(COALESCE(t.field_governance_config,''),' ',''),'"registrationSavedPhase":"dictionary"')>0) OR
          (t.business_type IN ('业务表','日志表') AND INSTR(REPLACE(COALESCE(t.field_governance_config,''),' ',''),'"registrationSavedPhase":"business"')>0)
          THEN 1 ELSE 0 END registration_saved,
        ROW_NUMBER() OVER(PARTITION BY t.datasource_id,LOWER(t.table_name)
          ORDER BY COALESCE(t.annotated,0) DESC,COALESCE(t.field_count,0) DESC,t.updated_time DESC,t.tid DESC) name_rank
 FROM db_table_t t JOIN source_scope s ON s.datasource_id=t.datasource_id
 WHERE t.tenant_id=#{tenantId} AND t.is_del=0
), table_scope AS (SELECT * FROM table_ranked WHERE name_rank=1),
source_stats AS (
 SELECT datasource_id,COUNT(*) table_count,SUM(CASE WHEN annotated=1 THEN 1 ELSE 0 END) marked_count,
        SUM(CASE WHEN asset_status=2 THEN 1 ELSE 0 END) completed_count,
        SUM(CASE WHEN business_type='字典表' THEN 1 ELSE 0 END) dictionary_count,
        SUM(CASE WHEN business_type='字典表' AND registration_saved=1 THEN 1 ELSE 0 END) dictionary_saved,
        SUM(CASE WHEN business_type IN ('业务表','日志表') THEN 1 ELSE 0 END) business_count,
        SUM(CASE WHEN business_type IN ('业务表','日志表') AND registration_saved=1 THEN 1 ELSE 0 END) business_saved
 FROM table_scope GROUP BY datasource_id
), job_ranked AS (
 SELECT CAST(j.datasource_id AS CHAR(64)) datasource_id,j.status,j.added_count,j.deleted_count,j.total_count,j.finished_time,
        ROW_NUMBER() OVER(PARTITION BY j.datasource_id ORDER BY j.created_time DESC,j.tid DESC) job_rank
 FROM metadata_table_collection_job_t j JOIN source_scope s ON s.source_key=CAST(j.datasource_id AS CHAR(64))
 WHERE j.tenant_id=#{tenantId} AND j.is_del=0
), latest_job AS (SELECT * FROM job_ranked WHERE job_rank=1),
ods_sources AS (
 SELECT DISTINCT binding.datasource_id
 FROM dwm_center_layer_source_t binding
 JOIN sym_dict_t node ON node.tid=binding.target_id AND node.tenant_id=#{tenantId} AND node.is_del=0
 JOIN sym_dict_t ods ON ods.tenant_id=#{tenantId} AND ods.is_del=0 AND LOWER(ods.dict_code)='ods'
   AND ((binding.target_type='layer' AND node.tid=ods.tid) OR (binding.target_type='domain' AND node.parent_id=ods.tid))
 JOIN sym_dict_t root ON root.tid=ods.parent_id AND root.tenant_id=#{tenantId}
   AND root.is_del=0 AND root.parent_id='0' AND root.dict_code='dataSourceType'
 WHERE binding.tenant_id=#{tenantId} AND binding.is_del=0
), task_scope AS (
 SELECT k.tid,k.task_name,k.source_table_id,k.source_db_id,t.datasource_id,t.table_name,s.db_name,
        k.updated_time,k.created_time,k.monitor_time,k.monitor_status,k.monitor_msg,k.delay_ms_estimate,k.schedule_running_start,
        k.queued_count,k.flow_files_in,k.flow_files_out,k.active_thread_count,k.last_running,k.end_running,
        CASE WHEN p.id IS NOT NULL THEN
               CASE WHEN p.status IN ('DEPLOY_FAILED','RUN_ERROR') THEN 'error'
                    WHEN p.status='RUNNING' THEN 'healthy'
                    WHEN p.status IN ('DEPLOYING','STOPPING') THEN 'warning' ELSE 'idle' END
             WHEN NULLIF(k.pipeline_id,'') IS NOT NULL THEN 'unknown'
             WHEN k.task_status=2 THEN 'error'
             WHEN k.task_status=1 THEN 'healthy' ELSE 'idle' END runtime_state
 FROM data_access_agg_task_t k
 JOIN table_scope t ON t.tid=k.source_table_id
 JOIN source_scope s ON s.datasource_id=t.datasource_id AND s.asset_status>0
 JOIN db_table_t target ON target.tid=k.target_table_id AND target.tenant_id=#{tenantId} AND target.is_del=0
 JOIN accessible_sources target_source ON target_source.datasource_id=target.datasource_id
 LEFT JOIN nifi_pipeline_t p ON p.id=k.pipeline_id AND p.tenant_id=#{tenantId} AND p.is_del=0
 WHERE k.tenant_id=#{tenantId} AND k.is_del=0 AND t.asset_status>0
   AND t.business_type IN ('业务表','日志表','字典表')
   AND (NULLIF(k.source_db_id,'') IS NULL OR k.source_db_id=t.datasource_id)
   AND (NULLIF(k.target_db_id,'') IS NULL OR k.target_db_id=target.datasource_id)
   AND NOT EXISTS(SELECT 1 FROM ods_sources o WHERE o.datasource_id=t.datasource_id)
)
"""
// 非登记统计不需要解析每张表的治理配置；保留完全相同的租户、角色和快照去重条件。
var lightCte=cte.replace('t.field_governance_config',"''")
return {tenantId:tenantId,userId:userId,scopeOrgIds:scopeOrgIds,scope:effectiveScope,
    mode:mode,canViewAll:canViewAll,cte:cte,lightCte:lightCte}
