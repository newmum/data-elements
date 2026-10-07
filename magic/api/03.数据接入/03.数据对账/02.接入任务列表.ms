{
  "properties": {},
  "id": "1d5c267fce16413889ed0e8e40bb80a5",
  "groupId": "56755e012bc14bec93d431716f86b595",
  "name": "02.接入任务列表",
  "path": "/access-tasks",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/reconciliation/access-tasks；只读；读取当前租户具备完整源端和目标端配置的数据接入任务；策略表单专用接口。",
  "createTime": 1785340800000,
  "updateTime": 1785340800000,
  "script": null
}
================================
// 接口：02.接入任务列表
// 路由：POST /access-tasks（完整路由由目录分组组合）
// 业务说明：POST /ods/reconciliation/access-tasks；只读；读取当前租户具备完整源端和目标端配置的数据接入任务；策略表单专用接口。

import tenantRuntime
import reconcile

// 策略只能绑定当前租户中已配置源端和目标端的接入任务。
var tenantId = tenantRuntime.id()
return reconcile.accessTasks()
