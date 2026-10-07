{
  "properties": {},
  "id": "9ab9834851824ee4aedc909a53a47d56",
  "groupId": "986c0e9209674f818072e3044d98013c",
  "name": "01.手动发起",
  "path": "/manual",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"policyId\":\"策略ID\",\"lowerWatermark\":\"2026-07-29 00:00:00\",\"upperWatermark\":\"2026-07-30 00:00:00\",\"requestId\":\"页面生成的幂等ID\"}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/reconciliation/run/manual；写入；创建幂等对账实例并交由 Worker 异步执行；手动执行主接口。",
  "createTime": 1785340800000,
  "updateTime": 1785340800000,
  "script": null
}
================================
// 接口：01.手动发起
// 路由：POST /manual（完整路由由目录分组组合）
// 业务说明：POST /ods/reconciliation/run/manual；写入；创建幂等对账实例并交由 Worker 异步执行；手动执行主接口。

import tenantRuntime
import reconcile
import java.util.UUID

// 只投递执行实例，不在 HTTP 请求线程扫描源表和目标表。
var tenantId = tenantRuntime.id()
var policyId = body?.policyId == null ? '' : ('' + body.policyId).trim()
if (policyId == '') { exit 400, '策略ID不能为空' }
var requestId = body?.requestId == null || ('' + body.requestId).trim() == '' ? UUID.randomUUID().toString() : '' + body.requestId
var accessRunId = body?.accessRunId == null ? null : '' + body.accessRunId
var lower = body?.lowerWatermark == null ? null : '' + body.lowerWatermark
var upper = body?.upperWatermark == null ? null : '' + body.upperWatermark

// 仓储以 requestId 和水位生成幂等键；重复提交返回既有实例。
var runId = reconcile.createRun(policyId, 'MANUAL', accessRunId, lower, upper, requestId)
return reconcile.runDetail(runId)
