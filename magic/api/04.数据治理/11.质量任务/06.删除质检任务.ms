{
  "properties": {},
  "id": "a1000000000000000000000000000016",
  "script": null,
  "groupId": "ec0b1d9bf20c4b26813f0f04f82fdcd2",
  "name": "06.删除质检任务",
  "createTime": 1790495138754,
  "updateTime": 1790498525458,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/delete",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按目标标识和当前状态执行删除质检任务，返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime
import dataQuality
// POST /dwm/qtask/delete 删除质检任务
// 按目标标识和当前状态执行删除质检任务，返回处理结果。
// 业务租户始终来自当前登录会话。

var tenantId = tenantRuntime.id()
// 软删除任务并保留历史质检结果和审计记录。
var taskId = body?.taskId == null ? '' : ('' + body.taskId).trim()
if (taskId == '') { exit 400, '任务ID不能为空' }
return dataQuality.deleteTask(taskId)
