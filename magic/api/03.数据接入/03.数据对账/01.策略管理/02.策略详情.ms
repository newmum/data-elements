{
  "properties": {},
  "id": "19657ee2c5e541d9b2f881f6920cf55d",
  "groupId": "ccb8f94e621a4feaa18362a325890270",
  "name": "02.策略详情",
  "path": "/detail",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"policyId\":\"策略ID\"}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/reconciliation/policy/detail；只读；读取当前租户策略、接入任务、源目标表和字段规则；策略详情主接口。",
  "createTime": 1785340800000,
  "updateTime": 1785340800000,
  "script": null
}
================================
// 接口：02.策略详情
// 路由：POST /detail（完整路由由目录分组组合）
// 业务说明：POST /ods/reconciliation/policy/detail；只读；读取当前租户策略、接入任务、源目标表和字段规则；策略详情主接口。

import tenantRuntime
import reconcile

// 策略标识为必填；具体记录还必须在当前租户范围内存在。
var tenantId = tenantRuntime.id()
var policyId = body?.policyId == null ? '' : ('' + body.policyId).trim()
if (policyId == '') { exit 400, '策略ID不能为空' }
return reconcile.policyDetail(policyId)
