{
  "properties": {},
  "id": "9451d67137ed4cec98caa1a91039e086",
  "groupId": "986c0e9209674f818072e3044d98013c",
  "name": "02.接入完成触发",
  "path": "/access-completed",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"accessTaskId\":\"接入任务ID\",\"accessRunId\":\"NiFi执行实例ID\",\"lowerWatermark\":\"2026-07-29 00:00:00\",\"upperWatermark\":\"2026-07-30 00:00:00\"}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/reconciliation/run/access-completed；写入；接收 NiFi 接入完成事件并为已启用 ACCESS_EVENT 策略创建对账实例；NiFi 回调接口。",
  "createTime": 1785340800000,
  "updateTime": 1785340800000,
  "script": null
}
================================
// 接口：02.接入完成触发
// 路由：POST /access-completed（完整路由由目录分组组合）
// 业务说明：POST /ods/reconciliation/run/access-completed；写入；接收 NiFi 接入完成事件并为已启用 ACCESS_EVENT 策略创建对账实例；NiFi 回调接口。

import tenantRuntime
import reconcile

// 接入完成事件只触发当前租户已启用的 ACCESS_EVENT 策略。
var tenantId = tenantRuntime.id()
var accessTaskId = body?.accessTaskId == null ? '' : ('' + body.accessTaskId).trim()
var accessRunId = body?.accessRunId == null ? '' : ('' + body.accessRunId).trim()
if (accessTaskId == '') { exit 400, '接入任务ID不能为空' }
if (accessRunId == '') { exit 400, '接入实例ID不能为空' }
var lower = body?.lowerWatermark == null ? null : '' + body.lowerWatermark
var upper = body?.upperWatermark == null ? null : '' + body.upperWatermark
var runs = []

// 同一接入实例重复通知时复用仓储的幂等键，不生成重复对账。
for (policyId in reconcile.accessEventPolicyIds(accessTaskId)) {
    var runId = reconcile.createRun(policyId, 'ACCESS_EVENT', accessRunId, lower, upper, accessRunId)
    runs.add(reconcile.runDetail(runId))
}
return runs
