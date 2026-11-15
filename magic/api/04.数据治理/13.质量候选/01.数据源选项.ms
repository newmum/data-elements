{
  "properties": {},
  "id": "a1000000000000000000000000000012",
  "groupId": "f5cea4fc64d44c7ebd024067f72f923d",
  "name": "01.数据源选项",
  "path": "/sources",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "description": "POST /dwm/qoption/sources；只读；查询当前租户已登记且包含数据表的 JDBC 数据源；任务表单选项接口。",
  "createTime": 1785628800000,
  "updateTime": 1785628800000,
  "script": null
}
================================
import tenantRuntime
import dataQuality
// POST /dwm/qoption/sources 数据源选项
// POST /dwm/qoption/sources；只读；查询当前租户已登记且包含数据表的 JDBC 数据源；任务表单选项接口。
// 业务租户始终来自当前登录会话。

var tenantId = tenantRuntime.id()
// 只列出当前租户具备质检能力的数据源。
return dataQuality.datasources()
