{
  "properties": {},
  "id": "a1000000000000000000000000000012",
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "13.数据源选项",
  "path": "/options/datasources",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "description": "POST /dwm/quality/options/datasources；只读；查询当前租户已登记且包含数据表的 JDBC 数据源；任务表单选项接口。",
  "createTime": 1785628800000,
  "updateTime": 1785628800000,
  "script": null
}
================================
// 接口：13.数据源选项
// 业务说明：POST /dwm/quality/options/datasources；只读；查询当前租户已登记且包含数据表的 JDBC 数据源；任务表单选项接口。

import tenantRuntime
import dataQuality

// 业务租户始终来自当前登录会话。
var tenantId = tenantRuntime.id()
// 只列出当前租户具备质检能力的数据源。
return dataQuality.datasources()
