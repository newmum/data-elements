{
  "properties" : { },
  "id" : "db987ffe0e004d8dba6565f42dcf9458",
  "script" : null,
  "groupId" : "1f4028f20aaa4f379b5e37a343b99fa9",
  "name" : "21.提交登记表接入申请",
  "createTime" : 1792474543009,
  "updateTime" : null,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/registrationAccessApply",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "部门用户提交单张已登记表的接入申请，重复请求幂等；管理员创建任务即代表受理。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
/*
 * 功能：部门用户确认已保存的登记表后提交接入申请；任务创建即由管理员受理。
 * 输入：datasourceId、tableId必填；只接受标识，不接受客户端状态、租户或申请人。
 * 输出：最新表级摘要及datasourceAssetStatus；重复申请保留首次申请时间。
 * 权限：当前租户及来源数据源有效记录；核心表已保存登记即可，不扩大检查项。
 * 数据：db_table_t申请三字段；按既有规则汇总db_datasource_t，允许部分登记。
 * 事务：单表申请与登记汇总使用短事务；原子NOT EXISTS防止与任务创建竞态。
 * 异常：无权、来源不一致或尚未保存登记拒绝；不创建NiFi流程或访问来源库。
 */
import tenantRuntime
import metadataAsset
import cn.dev33.satoken.stp.StpUtil
import '@/common/registrationAccessSummary' as registrationAccessSummary
var tenantId = tenantRuntime.id()
var datasourceId = body?.datasourceId == null ? '' : body.datasourceId + ''
var tableId = body?.tableId == null ? '' : body.tableId + ''
if (datasourceId == '' || tableId == '') { exit 400, '请选择要申请接入的数据表' }
var sources = db.table('db_datasource_t').columns('tid','org_id','created_by','asset_status').where().eq('tenant_id',tenantId).eq('tid',datasourceId).eq('is_del',0).select()
if (sources.size() != 1) { exit 403, '数据源不存在或已删除' }
var table = db.table('db_table_t').columns('tid','datasource_id','business_type','asset_status').where().eq('tenant_id',tenantId).eq('datasource_id',datasourceId).eq('tid',tableId).eq('is_del',0).selectOne()
if (table == null) { exit 403, '数据表不属于当前数据源或已不可访问' }
if (!['业务表','字典表','日志表'].contains(table.businessType) || (table.assetStatus || 0)::int <= 0) {
    exit 409, '请先保存该业务表、字典表或日志表的登记信息'
}
if (body?.dryRun == true) { return {valid:true,tableId:tableId,datasourceId:datasourceId} }
var actor = StpUtil.getLoginIdAsString()
var now = new Date()
var completion = db.transaction(() => {
    // 原子更新既保证重复提交不改申请时间，也避免覆盖管理员已经创建的任务。
    db.update("""
        UPDATE db_table_t SET access_request_status=1,access_requested_by=#{actor},access_requested_at=#{now}
        WHERE tenant_id=#{tenantId} AND datasource_id=#{datasourceId} AND tid=#{tableId} AND is_del=0
          AND COALESCE(access_request_status,0)<>1
          AND NOT EXISTS (SELECT 1 FROM data_access_agg_task_t task
                          WHERE task.tenant_id=#{tenantId} AND task.source_table_id=#{tableId} AND task.is_del=0)
    """)
    return metadataAsset.refreshRegistrationStatus(datasourceId,true)
})
var result = registrationAccessSummary({datasourceId:datasourceId,tableIds:[tableId]})
result.datasourceAssetStatus = completion.assetStatus
return result
