{
  "properties" : { },
  "id" : "rc9c090d43fa64a71bf2f2a6d10a0004",
  "script" : null,
  "groupId" : "46fe687ba7304a608b5e2865725ad21c",
  "name" : "13.资源运营记录删除",
  "createTime" : 1788453733018,
  "updateTime" : 1790982799160,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/operations/record/delete",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : "",
  "description" : "逻辑删除当前租户资源中心运营记录；使用请求参数 recordType、tid。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
// 旧路由兼容保护：POST /dwm/center/operations/record/delete。
// 删除动作改由模型归档、物化取消、目录撤回/下线实现，保留版本和执行审计记录。
import tenantRuntime
var tenantId = tenantRuntime.id()
var kind = body == null || body.recordType == null ? '' : (body.recordType + '').trim()
if (kind == '') { exit 400, '缺少记录类型' }
if (['logicalModel','logicalModelField','materializationPlan','materializationExecution','catalogPublishRequest'].contains(kind)) {
    exit 409, '此记录已由资源中心业务流程管理，请使用归档、撤回或下线操作，不能通过通用删除移除版本和执行记录'
}
exit 400, '不支持的记录类型'
