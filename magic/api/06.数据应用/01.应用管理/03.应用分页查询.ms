{
  "properties": {},
  "id": "6d310836db5f463a8942fa81958c35ec",
  "script": null,
  "groupId": "37c35deab6bc4e678c8955cc951f2ffd",
  "name": "03.应用分页查询",
  "createTime": 1782955345170,
  "updateTime": 1784437771871,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": \"1\",\n    \"pageSize\": \"20\",\n    \"appName\": \"测试业务系统\",\n    \"publishStatus\": 2\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"total\": 1,\n        \"list\": [\n            {\n                \"tid\": \"1\",\n                \"tenantId\": \"1\",\n                \"revision\": \"1\",\n                \"createdBy\": \"2\",\n                \"createdTime\": \"2026-04-22 11:16:17\",\n                \"isDel\": 0,\n                \"updatedBy\": null,\n                \"updatedTime\": null,\n                \"appName\": null,\n                \"appType\": null,\n                \"appIcon\": null,\n                \"description\": null,\n                \"publishStatus\": null,\n                \"publishUrl\": null,\n                \"publishId\": null\n            }\n        ]\n    }\n}",
  "description": "按应用名称、类型、发布状态及分页参数查询应用仓库，返回应用列表和总数。按当前认证行业租户处理。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime

// POST /das/app/page 应用分页查询。按应用名称、类型、发布状态及分页参数查询应用仓库，返回应用列表和总数。
var tenantId = tenantRuntime.id()

// 应用列表在当前行业库内做服务端分页，计数和数据页使用同一租户条件。
var pageNum = Math.max(1, body?.pageNum::int(1))
var pageSize = Math.max(1, Math.min(100, body?.pageSize::int(20)))
var appName = body?.appName
var publishStatus = body?.publishStatus

var query = db.table('app_workshop_repository').where()
    .eq('tenant_id', tenantId)
    .eq('is_del', 0)
if (appName != null && appName != '') {
    query.like('app_name', '%' + appName + '%')
}
if (publishStatus != null) {
    query.eq('publish_status', publishStatus)
}
return query.orderBy('created_time', 'desc').page(pageSize, (pageNum - 1L) * pageSize)
