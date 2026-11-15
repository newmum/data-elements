{
  "properties": {},
  "id": "a1000000000000000000000000000005",
  "groupId": "ec0b1d9bf20c4b26813f0f04f82fdcd2",
  "name": "04.任务预检",
  "path": "/check",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"taskId\":\"dq_police_population_demo\"}",
  "headers": [],
  "paths": [],
  "description": "POST /dwm/qtask/check；读写；验证注册数据源连接、物理表、字段、分片键和规则参数并保存根因；任务启用前主接口。",
  "createTime": 1785628800000,
  "updateTime": 1785628800000,
  "script": null
}
================================
import tenantRuntime
import dataQuality
// POST /dwm/qtask/check 任务预检
// POST /dwm/qtask/check；读写；验证注册数据源连接、物理表、字段、分片键和规则参数并保存根因；任务启用前主接口。
// 业务租户始终来自当前登录会话。

var tenantId = tenantRuntime.id()
// 预检连接和字段能力，再回写预检结果供任务启用时校验。
var taskId = body?.taskId == null ? '' : ('' + body.taskId).trim()
if (taskId == '') { exit 400, '任务ID不能为空' }
var result = dataQuality.inspectTask(taskId)
dataQuality.persistPrecheck(taskId, result)
return result
