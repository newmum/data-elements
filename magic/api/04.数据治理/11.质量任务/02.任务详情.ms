{
  "properties": {},
  "id": "a1000000000000000000000000000003",
  "groupId": "ec0b1d9bf20c4b26813f0f04f82fdcd2",
  "name": "02.任务详情",
  "path": "/detail",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"taskId\":\"dq_police_population_demo\"}",
  "headers": [],
  "paths": [],
  "description": "POST /dwm/qtask/detail；只读；查询质检任务及其版本化规则配置；任务编辑接口。",
  "createTime": 1785628800000,
  "updateTime": 1785628800000,
  "script": null
}
================================
import tenantRuntime
import dataQuality
// POST /dwm/qtask/detail 任务详情
// POST /dwm/qtask/detail；只读；查询质检任务及其版本化规则配置；任务编辑接口。
// 业务租户始终来自当前登录会话。

var tenantId = tenantRuntime.id()
// 按当前租户读取任务及规则，禁止跨租户读取。
var taskId = body?.taskId == null ? '' : ('' + body.taskId).trim()
if (taskId == '') { exit 400, '任务ID不能为空' }
return dataQuality.taskDetail(taskId)
