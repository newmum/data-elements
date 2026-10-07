{
  "properties": {},
  "id": "fe55dadd11564ad4876470dc8f661041",
  "script": null,
  "groupId": "7c271b88983b467282fd9d498d0f8d66",
  "name": "02.按tid查询大屏",
  "createTime": 1782955345214,
  "updateTime": 1784437771874,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/getById",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"1\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"tid\": \"1\",\n        \"tenantId\": \"1\",\n        \"revision\": \"1\",\n        \"createdBy\": \"2\",\n        \"createdTime\": \"2026-04-15 15:14:45\",\n        \"updatedBy\": null,\n        \"updatedTime\": null,\n        \"name\": \"2\",\n        \"nameCn\": \"2\",\n        \"sourceCode\": \"1\",\n        \"thumbnail\": \"11\"\n    }\n}",
  "description": "按大屏主键读取应用工作坊的大屏配置与展示资料，返回单条记录。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.按tid查询大屏
// 路由：POST /das/repository/bigScreen/getById
// 业务说明：按大屏主键读取应用工作坊的大屏配置与展示资料，返回单条记录。
// 解析请求体并为可选参数设置默认值。
var tid = body?.tid

// 从 MySQL 业务表读取当前有效数据。
var data = db.table("app_workshop_big_screen").where().eq("tid", tid).selectOne();

// 返回当前业务分支的标准结果。
return data
