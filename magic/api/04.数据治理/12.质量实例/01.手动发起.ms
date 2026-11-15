{
  "properties": {},
  "id": "a1000000000000000000000000000007",
  "groupId": "7391edbbd74b46969f5a885e04cd5ee7",
  "name": "01.手动发起",
  "path": "/start",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"taskId\":\"dq_police_population_demo\",\"requestId\":\"page-generated-id\"}",
  "headers": [],
  "paths": [],
  "description": "POST /dwm/qrun/start；写入 dq_quality_run_t；只投递幂等实例，由 Worker 异步扫描源库；手工执行主接口。",
  "createTime": 1785628800000,
  "updateTime": 1785628800000,
  "script": null
}
================================
import tenantRuntime
import dataQuality
import java.util.UUID
// POST /dwm/qrun/start 手动发起
// POST /dwm/qrun/start；写入 dq_quality_run_t；只投递幂等实例，由 Worker 异步扫描源库；手工执行主接口。
// 业务租户始终来自当前登录会话。

var tenantId = tenantRuntime.id()
// 只创建幂等实例，耗时的分片扫描由 Worker 执行。
var taskId = body?.taskId == null ? '' : ('' + body.taskId).trim()
if (taskId == '') { exit 400, '任务ID不能为空' }
var requestId = body?.requestId == null || ('' + body.requestId).trim() == '' ? UUID.randomUUID().toString() : '' + body.requestId
var runId = dataQuality.createRun(taskId, requestId)
return dataQuality.runDetail(runId,body||{})
