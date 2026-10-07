{
  "properties" : { },
  "id" : "rc9c090d43fa64a71bf2f2a6d10a0003",
  "script" : null,
  "groupId" : "46fe687ba7304a608b5e2865725ad21c",
  "name" : "12.资源运营记录保存",
  "createTime" : 1788453732929,
  "updateTime" : 1790982799099,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/operations/record/save",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : "",
  "description" : "新建或更新当前租户资源中心运营记录；字段和表名均由服务端白名单控制；使用请求参数 recordType、fields、tid。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
// 旧路由兼容保护：POST /dwm/center/operations/record/save。
// 通用记录编辑不能绕过模型冻结、目录审批或真实物化执行；查询路由继续保留。
import tenantRuntime
var tenantId = tenantRuntime.id()
var kind = body == null || body.recordType == null ? '' : (body.recordType + '').trim()
if (kind == '') { exit 400, '缺少记录类型' }
if (['logicalModel','logicalModelField','materializationPlan','materializationExecution','catalogPublishRequest'].contains(kind)) {
    exit 409, '此记录已由资源中心业务流程管理，请进入对应模型、物化或目录页面操作；通用编辑不能直接修改审批及执行状态'
}
exit 400, '不支持的记录类型'
