{
  "properties": {},
  "id": "a1000000000000000000000000000013",
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "14.数据表选项",
  "path": "/options/tables",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"datasourceId\":\"dq_demo_source_20260802\"}",
  "headers": [],
  "paths": [],
  "description": "POST /dwm/quality/options/tables；只读；查询指定数据源已登记数据表；任务表单选项接口。",
  "createTime": 1785628800000,
  "updateTime": 1785628800000,
  "script": null
}
================================
// 接口：14.数据表选项
// 业务说明：POST /dwm/quality/options/tables；只读；查询指定数据源已登记数据表；任务表单选项接口。

import tenantRuntime
import dataQuality

// 业务租户始终来自当前登录会话。
var tenantId = tenantRuntime.id()
// 根据已登记数据源读取其可质检表。
var datasourceId = body?.datasourceId == null ? '' : ('' + body.datasourceId).trim()
if (datasourceId == '') { exit 400, '数据源ID不能为空' }
return dataQuality.tables(datasourceId)
