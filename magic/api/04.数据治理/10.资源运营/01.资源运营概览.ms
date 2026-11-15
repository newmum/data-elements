{
  "properties": {},
  "id": "rc9c090d43fa64a71bf2f2a6d10a0001",
  "script": null,
  "groupId": "89db9624d0e543cd9cb97631c19be661",
  "name": "01.资源运营概览",
  "createTime": 1788453656475,
  "updateTime": null,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/overview",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "资源中心运营概览。仅统计当前租户的逻辑模型、物化计划、物化执行和目录发布申请。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime

// GET /dwm/ops/overview 资源运营概览
// 资源中心运营概览。仅统计当前租户的逻辑模型、物化计划、物化执行和目录发布申请。

var tenantId = tenantRuntime.id()
var countOf = (tableName) => {
  var rows = db.select('SELECT COUNT(1) AS total FROM ' + tableName + ' WHERE tenant_id = #{tenantId} AND is_del = 0', {tenantId: tenantId})
  return rows == null || rows.isEmpty() ? 0 : (rows[0].total == null ? 0 : rows[0].total)
}
return {
  tenantId: tenantId,
  logicalModelCount: countOf('res_logical_model'),
  logicalModelFieldCount: countOf('res_logical_model_field'),
  materializationPlanCount: countOf('res_materialization_plan'),
  materializationExecutionCount: countOf('res_materialization_execution'),
  catalogPublishRequestCount: countOf('res_catalog_publish_request')
}
