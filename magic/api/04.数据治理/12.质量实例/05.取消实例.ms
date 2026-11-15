{
  "properties": {},
  "id": "a1000000000000000000000000000011",
  "groupId": "7391edbbd74b46969f5a885e04cd5ee7",
  "name": "05.取消实例",
  "path": "/cancel",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"runId\":\"质检实例ID\"}",
  "headers": [],
  "paths": [],
  "description": "POST /dwm/qrun/cancel；写入 dq_quality_run_t；请求 Worker 停止未执行分片；实例取消主接口。",
  "createTime": 1785628800000,
  "updateTime": 1785628800000,
  "script": null
}
================================
import tenantRuntime
import dataQuality
// POST /dwm/qrun/cancel 取消实例
// POST /dwm/qrun/cancel；写入 dq_quality_run_t；请求 Worker 停止未执行分片；实例取消主接口。
// 业务租户始终来自当前登录会话。

var tenantId = tenantRuntime.id()
// 仅标记取消；Worker 在安全检查点结束未完成分片。
var runId = body?.runId == null ? '' : ('' + body.runId).trim()
if (runId == '') { exit 400, '实例ID不能为空' }
return dataQuality.cancel(runId)
