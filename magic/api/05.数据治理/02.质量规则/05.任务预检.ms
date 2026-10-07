{
  "properties": {},
  "id": "a1000000000000000000000000000005",
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "05.任务预检",
  "path": "/task/precheck",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"taskId\":\"dq_police_population_demo\"}",
  "headers": [],
  "paths": [],
  "description": "POST /dwm/quality/task/precheck；读写；验证注册数据源连接、物理表、字段、分片键和规则参数并保存根因；任务启用前主接口。",
  "createTime": 1785628800000,
  "updateTime": 1785628800000,
  "script": null
}
================================
// 接口：05.任务预检
// 业务说明：POST /dwm/quality/task/precheck；读写；验证注册数据源连接、物理表、字段、分片键和规则参数并保存根因；任务启用前主接口。

import tenantRuntime
import dataQuality

// 业务租户始终来自当前登录会话。
var tenantId = tenantRuntime.id()
// 预检连接和字段能力，再回写预检结果供任务启用时校验。
var taskId = body?.taskId == null ? '' : ('' + body.taskId).trim()
if (taskId == '') { exit 400, '任务ID不能为空' }
var result = dataQuality.inspectTask(taskId)
dataQuality.persistPrecheck(taskId, result)
return result
