{
  "properties": {},
  "id": "bf5e4120008242979d347ccd67de8604",
  "groupId": "986c0e9209674f818072e3044d98013c",
  "name": "04.实例详情",
  "path": "/detail",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"runId\":\"执行实例ID\"}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/reconciliation/run/detail；只读；读取实例、分桶进度、统计和差异分类；执行详情主接口。",
  "createTime": 1785340800000,
  "updateTime": 1785340800000,
  "script": null
}
================================
// 接口：04.实例详情
// 路由：POST /detail（完整路由由目录分组组合）
// 业务说明：POST /ods/reconciliation/run/detail；只读；读取实例、分桶进度、统计和差异分类；执行详情主接口。

import tenantRuntime
import reconcile

// 读取实例、分桶进度和差异分类；仓储强制当前租户归属。
var tenantId = tenantRuntime.id()
var runId = body?.runId == null ? '' : ('' + body.runId).trim()
if (runId == '') { exit 400, '对账实例ID不能为空' }
return reconcile.runDetail(runId)
