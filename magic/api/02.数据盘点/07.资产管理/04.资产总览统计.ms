{
  "properties": {},
  "id": "a55e7c1d2f4b4a6e9c3d8b1f5a7e2c43",
  "script": null,
  "groupId": "datapd_asset_group_01",
  "name": "04.资产总览统计",
  "createTime": 1791030855466,
  "updateTime": 1791030855466,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/stats",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": "{\"modules\":{},\"catalogs\":{},\"bindings\":{},\"standards\":{}}",
  "description": "当前租户资产模块与目录/挂接/标准覆盖聚合统计；固定查询次数，不按记录逐条补查。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime

// POST /dst/asset/stats 资产总览统计
// 业务说明：当前租户资产模块与目录/挂接/标准覆盖聚合统计；固定查询次数，不按记录逐条补查。

var tenantId = tenantRuntime.id()
var modules = {
    plan: { table: 'asset_plan_t', status: 'status' },
    discovery: { table: 'asset_discovery_t', status: 'status' },
    disposal: { table: 'asset_disposal_t', status: 'approval_status' },
    security: { table: 'asset_security_t', status: 'policy_status' },
    operation: { table: 'asset_operation_t', status: 'health_status' },
    evaluation: { table: 'asset_evaluation_t', status: 'grade' },
    inventory: { table: 'asset_inventory_t', status: 'status' }
}
var result = {}
var totalCount = 0
var moduleNames = ['plan', 'discovery', 'disposal', 'security', 'operation', 'evaluation', 'inventory']
for (name in moduleNames) {
    var config = modules[name]
    var byStatus = {}
    var statusRows = db.select("SELECT " + config.status + " AS status, COUNT(1) AS n FROM " + config.table + " WHERE tenant_id = #{tenantId} AND is_del = 0 GROUP BY " + config.status)
    var total = 0
    for (row in statusRows) {
        var key = row.status == null ? 'UNKNOWN' : (row.status + '')
        var count = row.n == null ? 0 : row.n::long
        byStatus[key] = count
        total = total + count
    }
    result[name] = { total: total, byStatus: byStatus }
    totalCount = totalCount + total
}
var catalogTotal = 0
var catalogApproved = 0
var catalogPending = 0
var catalogRows = db.select("""
    SELECT COUNT(1) AS total,
           COALESCE(SUM(CASE WHEN asset_status = 2 AND flow_status = 2 THEN 1 ELSE 0 END), 0) AS approved,
           COALESCE(SUM(CASE WHEN flow_status = 1 THEN 1 ELSE 0 END), 0) AS pending
      FROM da_catalog_t WHERE tenant_id = #{tenantId} AND is_del = 0
""")
if (catalogRows != null && catalogRows.size() > 0) {
    catalogTotal = catalogRows[0].total == null ? 0 : catalogRows[0].total::long
    catalogApproved = catalogRows[0].approved == null ? 0 : catalogRows[0].approved::long
    catalogPending = catalogRows[0].pending == null ? 0 : catalogRows[0].pending::long
}
var bindingRows = db.select("SELECT COUNT(1) AS n FROM da_catalog_resource_binding_t WHERE tenant_id = #{tenantId} AND status = 'ACTIVE'")
var bindingActive = bindingRows == null || bindingRows.size() == 0 || bindingRows[0].n == null ? 0 : bindingRows[0].n::long
var standardRows = db.select("""
    SELECT COUNT(1) AS total,
           COALESCE(SUM(CASE WHEN data_standard_id IS NOT NULL AND TRIM(data_standard_id) <> '' THEN 1 ELSE 0 END), 0) AS covered
      FROM db_table_column_t WHERE tenant_id = #{tenantId} AND is_del = 0
""")
var columnTotal = 0
var columnCovered = 0
if (standardRows != null && standardRows.size() > 0) {
    columnTotal = standardRows[0].total == null ? 0 : standardRows[0].total::long
    columnCovered = standardRows[0].covered == null ? 0 : standardRows[0].covered::long
}
var coverage = columnTotal == 0 ? 0 : Math.round(columnCovered::double * 10000.0 / columnTotal::double) / 100.0
return {
    modules: result,
    moduleTotal: totalCount,
    catalogs: { total: catalogTotal, approved: catalogApproved, pending: catalogPending },
    bindings: { active: bindingActive },
    standards: { columns: columnTotal, covered: columnCovered, coveragePercent: coverage }
}
