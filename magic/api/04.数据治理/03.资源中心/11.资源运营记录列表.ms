{
  "properties": {},
  "id": "rc9c090d43fa64a71bf2f2a6d10a0002",
  "script": null,
  "groupId": "46fe687ba7304a608b5e2865725ad21c",
  "name": "11.资源运营记录列表",
  "createTime": 1788453732846,
  "updateTime": null,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/operations/records",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "读取当前租户资源中心运营记录。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：11.资源运营记录列表
// 路由：GET /dwm/center/operations/records
// 业务说明：读取当前租户资源中心运营记录。
import tenantRuntime

var tenantId = tenantRuntime.id()
var rowsOf = (tableName, orderBy) => db.select('SELECT * FROM ' + tableName + ' WHERE tenant_id = #{tenantId} AND is_del = 0 ORDER BY ' + orderBy, {tenantId: tenantId})
return {
  tenantId: tenantId,
  logicalModels: rowsOf('res_logical_model', 'updated_time DESC, tid DESC'),
  logicalModelFields: rowsOf('res_logical_model_field', 'model_id, sort_no, tid'),
  materializationPlans: rowsOf('res_materialization_plan', 'updated_time DESC, tid DESC'),
  materializationExecutions: rowsOf('res_materialization_execution', 'created_time DESC, tid DESC'),
  catalogPublishRequests: rowsOf('res_catalog_publish_request', 'created_time DESC, tid DESC')
}
