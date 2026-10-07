{
  "properties": {},
  "id": "a1000000000000000000000000000011",
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "12.取消实例",
  "path": "/run/cancel",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"runId\":\"质检实例ID\"}",
  "headers": [],
  "paths": [],
  "description": "POST /dwm/quality/run/cancel；写入 dq_quality_run_t；请求 Worker 停止未执行分片；实例取消主接口。",
  "createTime": 1785628800000,
  "updateTime": 1785628800000,
  "script": null
}
================================
// 接口：12.取消实例
// 业务说明：POST /dwm/quality/run/cancel；写入 dq_quality_run_t；请求 Worker 停止未执行分片；实例取消主接口。

import tenantRuntime
import dataQuality

// 业务租户始终来自当前登录会话。
var tenantId = tenantRuntime.id()
// 仅标记取消；Worker 在安全检查点结束未完成分片。
var runId = body?.runId == null ? '' : ('' + body.runId).trim()
if (runId == '') { exit 400, '实例ID不能为空' }
return dataQuality.cancel(runId)
