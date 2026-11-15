{
  "properties": {},
  "id": "a1000000000000000000000000000006",
  "groupId": "ec0b1d9bf20c4b26813f0f04f82fdcd2",
  "name": "05.任务启停",
  "path": "/enable",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"taskId\":\"dq_police_population_demo\",\"enabled\":true}",
  "headers": [],
  "paths": [],
  "description": "POST /dwm/qtask/enable；写入 dq_quality_task_t；仅通过预检的任务可以启用；任务状态主接口。",
  "createTime": 1785628800000,
  "updateTime": 1785628800000,
  "script": null
}
================================
import tenantRuntime
import dataQuality
// POST /dwm/qtask/enable 任务启停
// POST /dwm/qtask/enable；写入 dq_quality_task_t；仅通过预检的任务可以启用；任务状态主接口。
// 业务租户始终来自当前登录会话。

var tenantId = tenantRuntime.id()
// 启用前仓储仍会强制核验预检通过和任务归属。
var taskId = body?.taskId == null ? '' : ('' + body.taskId).trim()
if (taskId == '') { exit 400, '任务ID不能为空' }
var enabled = body?.enabled == true || body?.enabled == 1 || body?.enabled == '1' || ('' + body?.enabled).toLowerCase() == 'true'
return dataQuality.setEnabled(taskId, enabled)
