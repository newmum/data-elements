{
  "properties": {},
  "id": "901cb59ef73f4404bdee6049f20889ef",
  "groupId": "986c0e9209674f818072e3044d98013c",
  "name": "05.取消实例",
  "path": "/cancel",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"runId\":\"执行实例ID\"}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/reconciliation/run/cancel；写入；设置取消标记，由 Worker 在流式检查点安全停止；执行控制接口。",
  "createTime": 1785340800000,
  "updateTime": 1785340800000,
  "script": null
}
================================
// 接口：05.取消实例
// 路由：POST /cancel（完整路由由目录分组组合）
// 业务说明：POST /ods/reconciliation/run/cancel；写入；设置取消标记，由 Worker 在流式检查点安全停止；执行控制接口。

import tenantRuntime
import reconcile

// 只设置取消标记；Worker 在流式检查点安全结束，保留已完成进度。
var tenantId = tenantRuntime.id()
var runId = body?.runId == null ? '' : ('' + body.runId).trim()
if (runId == '') { exit 400, '对账实例ID不能为空' }
return reconcile.requestCancel(runId)
