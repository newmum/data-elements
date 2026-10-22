{
  "properties" : { },
  "id" : "quality_sql_template_delete_v1",
  "script" : null,
  "groupId" : "d5ce0d4911e94d7bb3c22a9f67547406",
  "name" : "24.删除SQL模板",
  "createTime" : 1792504668868,
  "updateTime" : null,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/templates/delete",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : null,
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "按当前租户软删除SQL模板，已有任务及报告保留其独立快照。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
/* 功能：软删除单个当前租户SQL模板；输入tid必填，返回true。
 * 权限及数据：data_gov_quality_rule按当前租户和模板scope限定；不改来源数据或历史任务。
 * 重复删除或越权返回403；仅影响后续模板选择，已有任务继续使用保存的快照。
 */
import tenantRuntime
var tenantId=tenantRuntime.id()
if(body?.tid == null || body.tid=='') {exit 400,'请选择模板'}
var count=db.table('data_gov_quality_rule').where().eq('tid',body.tid).eq('tenant_id',tenantId)
    .eq('scope_of_application','QUALITY_SQL_TEMPLATE_V1').eq('is_del',0).update({is_del:1,updated_time:new Date()})
if(count!=1) {exit 403,'模板不存在或不在当前租户范围内'}
return true
