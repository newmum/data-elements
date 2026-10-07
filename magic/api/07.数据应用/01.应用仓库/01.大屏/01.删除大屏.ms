{
  "properties": {},
  "id": "acb1c21957514ca29bd54acd3fbc2d0a",
  "script": null,
  "groupId": "7c271b88983b467282fd9d498d0f8d66",
  "name": "01.删除大屏",
  "createTime": 1782955345206,
  "updateTime": 1784437771874,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/deleteById",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"1\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": \"success\"\n}",
  "description": "按目标标识和当前状态执行删除大屏，返回处理结果；使用请求参数 tid；涉及 app_workshop_big_screen 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.删除大屏
// 路由：POST /das/repository/bigScreen/deleteById
// 业务说明：按目标标识和当前状态执行删除大屏，返回处理结果；使用请求参数 tid；涉及 app_workshop_big_screen 等业务表。
// 解析请求体并为可选参数设置默认值。
var tid = body?.tid

// 将校验后的业务数据写入 MySQL 主表。
var data = db.table("app_workshop_big_screen").where().eq("tid", tid).delete();

// 返回当前业务分支的标准结果。
return "success"
