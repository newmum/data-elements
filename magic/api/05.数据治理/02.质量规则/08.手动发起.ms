{
  "properties": {},
  "id": "a1000000000000000000000000000007",
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "08.手动发起",
  "path": "/run/manual",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"taskId\":\"dq_police_population_demo\",\"requestId\":\"page-generated-id\"}",
  "headers": [],
  "paths": [],
  "description": "POST /dwm/quality/run/manual；写入 dq_quality_run_t；只投递幂等实例，由 Worker 异步扫描源库；手工执行主接口。",
  "createTime": 1785628800000,
  "updateTime": 1785628800000,
  "script": null
}
================================
// 接口：08.手动发起
// 业务说明：POST /dwm/quality/run/manual；写入 dq_quality_run_t；只投递幂等实例，由 Worker 异步扫描源库；手工执行主接口。

import tenantRuntime
import dataQuality
import java.util.UUID

// 业务租户始终来自当前登录会话。
var tenantId = tenantRuntime.id()
// 只创建幂等实例，耗时的分片扫描由 Worker 执行。
var taskId = body?.taskId == null ? '' : ('' + body.taskId).trim()
if (taskId == '') { exit 400, '任务ID不能为空' }
var requestId = body?.requestId == null || ('' + body.requestId).trim() == '' ? UUID.randomUUID().toString() : '' + body.requestId
var runId = dataQuality.createRun(taskId, requestId)
return dataQuality.runDetail(runId)
