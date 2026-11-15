{
  "properties": {},
  "id": "a55e7c1d2f4b4a6e9c3d8b1f5a7e2c44",
  "script": null,
  "groupId": "datapd_asset_group_01",
  "name": "05.智能盘点执行",
  "createTime": 1791030855467,
  "updateTime": 1791030855467,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/scan",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"inventoryId\":\"\",\"inventoryName\":\"\",\"scopeType\":\"TENANT|ORG|CATALOG\",\"scopeJson\":\"\",\"catalogIds\":[],\"orgId\":\"\",\"dryRun\":false}",
  "headers": [],
  "paths": [],
  "responseBody": "{\"inventoryId\":\"\",\"status\":\"COMPLETED\",\"totalCount\":0}",
  "description": "按租户/部门/目录范围执行智能盘点：批量读取目录、资源挂接与标准覆盖，识别缺失挂接、悬空资源与标准缺口；支持 dryRun。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.hutool.core.util.IdUtil
import jsons
import tenantRuntime
import "@post:/sym/user/me" as me

// POST /dst/asset/scan 智能盘点执行
// 业务说明：按租户/部门/目录范围执行智能盘点：批量读取目录、资源挂接与标准覆盖，识别缺失挂接、悬空资源与标准缺口；支持 dryRun。

var tenantId = tenantRuntime.id()
var user = me()
var userId = user == null ? '' : (user.id == null ? '' : user.id + '')
var text = (value) => value == null ? '' : value + ''
var num = (value) => value == null ? 0 : value::long
var first = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        if (row[key] != null && text(row[key]).trim() != '') { return row[key] }
    }
    return null
}
var scopeType = text(body?.scopeType).trim().toUpperCase()
if (scopeType == '') { scopeType = 'TENANT' }
if (!['TENANT', 'ORG', 'CATALOG'].contains(scopeType)) { exit 400, '盘点范围必须是 TENANT/ORG/CATALOG' }
var orgIdValue = text(body?.orgId).trim()
if (scopeType == 'ORG' && orgIdValue == '') { exit 400, '按部门盘点时必须指定 orgId' }
var catalogIds = []
if (body?.catalogIds instanceof List) {
    for (rawId in body.catalogIds) {
        var catalogId = text(rawId).trim()
        if (catalogId == '') { continue }
        if (!catalogId.matches('[A-Za-z0-9_-]+')) { exit 400, '目录标识格式不正确' }
        if (!catalogIds.contains(catalogId)) { catalogIds.add(catalogId) }
    }
}
if (scopeType == 'CATALOG' && catalogIds.size() == 0) { exit 400, '按目录盘点时必须提供 catalogIds' }
if (catalogIds.size() > 200) { exit 400, '单次最多盘点 200 个目录' }
var dryRun = body?.dryRun == true
var inventoryId = text(body?.inventoryId).trim()
var inventoryName = text(body?.inventoryName).trim()
if (inventoryId != '' && !inventoryId.matches('[A-Za-z0-9_-]+')) { exit 400, '盘点任务标识格式不正确' }
if (inventoryId != '') {
    var existingTask = db.select("SELECT tid FROM asset_inventory_t WHERE tenant_id = #{tenantId} AND tid = #{inventoryId} AND is_del = 0")
    if (existingTask == null || existingTask.size() == 0) { exit 404, '盘点任务不存在或不在当前租户' }
}

var catalogWhere = ' WHERE tenant_id = #{tenantId} AND is_del = 0'
if (scopeType == 'ORG') { catalogWhere = catalogWhere + ' AND org_id = #{orgIdValue}' }
if (scopeType == 'CATALOG') {
    catalogWhere = catalogWhere + " AND tid IN ('" + catalogIds.join("','") + "')"
}
var catalogCountRows = db.select("SELECT COUNT(1) AS n FROM da_catalog_t" + catalogWhere)
var catalogTotal = catalogCountRows[0].n == null ? 0 : catalogCountRows[0].n::long
if (catalogTotal > 2000) { exit 413, '盘点目录超过 2000 个，请缩小部门或目录范围' }
var catalogs = db.select("SELECT tid, catalog_name, catalog_name_en, org_id, source_table_id, asset_status, flow_status FROM da_catalog_t" + catalogWhere + ' ORDER BY tid')
var catalogIdList = []
for (row in catalogs) { catalogIdList.add(text(first(row, ['tid', 'TID']))) }
var scannedTotal = catalogIdList.size()
if (catalogIdList.size() == 0) { catalogIdList.add('') }
var catalogsIn = catalogIdList.join("','")

var bindingRows = db.select("SELECT tid, catalog_id, resource_type, resource_id, binding_role " +
    "FROM da_catalog_resource_binding_t " +
    "WHERE tenant_id = #{tenantId} AND status = 'ACTIVE' " +
    "AND catalog_id IN ('" + catalogsIn + "')")
var bindingsByCatalog = {}
var tableBindingIds = []
for (row in bindingRows) {
    var catalogKey = text(first(row, ['catalog_id', 'catalogId', 'CATALOG_ID']))
    var resourceType = text(first(row, ['resource_type', 'resourceType', 'RESOURCE_TYPE'])).toUpperCase()
    var resourceId = text(first(row, ['resource_id', 'resourceId', 'RESOURCE_ID']))
    if (bindingsByCatalog[catalogKey] == null) { bindingsByCatalog[catalogKey] = [] }
    bindingsByCatalog[catalogKey].add({ resourceType: resourceType, resourceId: resourceId })
    if (resourceType == 'TABLE' && resourceId != '' && !tableBindingIds.contains(resourceId)) { tableBindingIds.add(resourceId) }
}
var existingTableIds = {}
if (tableBindingIds.size() > 0) {
    var tableRows = db.select("SELECT tid, table_name FROM db_table_t WHERE tenant_id = #{tenantId} AND is_del = 0 AND tid IN ('" + tableBindingIds.join("','") + "')")
    for (row in tableRows) { existingTableIds[text(first(row, ['tid', 'TID']))] = true }
}
var coverageByCatalog = {}
var coverageRows = db.select("SELECT b.catalog_id AS catalogId, " +
    "COUNT(c.tid) AS columnCount, " +
    "COALESCE(SUM(CASE WHEN c.data_standard_id IS NOT NULL AND m.tid IS NOT NULL THEN 1 ELSE 0 END), 0) AS coveredCount " +
    "FROM da_catalog_resource_binding_t b " +
    "JOIN db_table_t t ON t.tid = b.resource_id AND t.tenant_id = b.tenant_id AND t.is_del = 0 " +
    "JOIN db_table_column_t c ON c.table_id = t.tid AND c.tenant_id = t.tenant_id AND c.is_del = 0 " +
    "LEFT JOIN da_metadata_t m ON m.tid = c.data_standard_id AND m.tenant_id = c.tenant_id AND m.is_del = 0 AND m.publish_status = 1 " +
    "WHERE b.tenant_id = #{tenantId} AND b.status = 'ACTIVE' AND b.resource_type = 'TABLE' " +
    "AND b.catalog_id IN ('" + catalogsIn + "') " +
    "GROUP BY b.catalog_id, t.tid")
for (row in coverageRows) {
    var catalogKey = text(first(row, ['catalogId', 'catalogid', 'CATALOGID']))
    var columnCount = num(first(row, ['columnCount', 'columncount', 'COLUMNCOUNT']))
    var coveredCount = num(first(row, ['coveredCount', 'coveredcount', 'COVEREDCOUNT']))
    var state = coverageByCatalog[catalogKey]
    if (state == null) { coverageByCatalog[catalogKey] = { columns: columnCount, covered: coveredCount, gap: coveredCount < columnCount } }
    else {
        state.columns = state.columns + columnCount
        state.covered = state.covered + coveredCount
        if (coveredCount < columnCount) { state.gap = true }
    }
}
var issues = []
var matchedCount = 0
var missingCount = 0
var mismatchCount = 0
for (row in catalogs) {
    var catalogKey = text(first(row, ['tid', 'TID']))
    var catalogName = text(first(row, ['catalog_name', 'catalogName', 'CATALOG_NAME']))
    var catalogBindings = bindingsByCatalog[catalogKey]
    var catalogIssues = []
    if (catalogBindings == null || catalogBindings.size() == 0) {
        catalogIssues.add({ code: 'MISSING_BINDING', message: '目录未挂接任何共享资源' })
    } else {
        for (binding in catalogBindings) {
            if (binding.resourceType == 'TABLE' && existingTableIds[binding.resourceId] != true) {
                catalogIssues.add({ code: 'DANGLING_RESOURCE', message: '挂接的数据表已不存在：' + binding.resourceId })
            }
        }
        var coverage = coverageByCatalog[catalogKey]
        if (coverage != null && coverage.columns > 0 && coverage.gap == true) {
            catalogIssues.add({ code: 'STANDARD_GAP', message: '挂接表存在未绑定已发布数据元的字段（覆盖 ' + coverage.covered + '/' + coverage.columns + '）' })
        }
    }
    if (catalogIssues.size() == 0) { matchedCount = matchedCount + 1 }
    else {
        if (catalogBindings == null || catalogBindings.size() == 0) { missingCount = missingCount + 1 }
        else { mismatchCount = mismatchCount + 1 }
        if (issues.size() < 200) {
            issues.add({ catalogId: catalogKey, catalogName: catalogName, issues: catalogIssues })
        }
    }
}
var summary = '盘点 ' + scannedTotal + ' 个目录：一致 ' + matchedCount + '，缺失挂接 ' + missingCount + '，不一致 ' + mismatchCount
var resultPayload = { scopeType: scopeType, orgId: orgIdValue, catalogIds: catalogIds, issues: issues }
var resultJson = jsons.toJSONStr(resultPayload)
if (dryRun) {
    return { dryRun: true, totalCount: scannedTotal, matchedCount: matchedCount,
        missingCount: missingCount, mismatchCount: mismatchCount, issues: issues, summary: summary }
}
var now = new Date()
if (inventoryId == '') {
    inventoryId = IdUtil.getSnowflakeNextIdStr()
    if (inventoryName == '') { inventoryName = '智能盘点-' + scopeType }
    db.update("""
        INSERT INTO asset_inventory_t
          (tid, tenant_id, inventory_code, inventory_name, scope_type, scope_json, status, started_time, finished_time,
           total_count, matched_count, missing_count, mismatch_count, result_json, result_summary,
           created_by, updated_by, created_time, updated_time, is_del)
        VALUES
          (#{inventoryId}, #{tenantId}, #{inventoryCode}, #{inventoryName}, #{scopeType}, #{scopeJson}, 'COMPLETED', #{now}, #{now},
           #{totalCount}, #{matchedCount}, #{missingCount}, #{mismatchCount}, #{resultJson}, #{summary},
           #{userId}, #{userId}, #{now}, #{now}, 0)
    """, { inventoryId: inventoryId, tenantId: tenantId, inventoryCode: 'INV-' + inventoryId, inventoryName: inventoryName,
        scopeType: scopeType, scopeJson: jsons.toJSONStr({ scopeType: scopeType, orgId: orgIdValue, catalogIds: catalogIds }),
        now: now, totalCount: scannedTotal, matchedCount: matchedCount, missingCount: missingCount,
        mismatchCount: mismatchCount, resultJson: resultJson, summary: summary, userId: userId })
} else {
    db.update("""
        UPDATE asset_inventory_t
           SET status = 'COMPLETED', started_time = #{now}, finished_time = #{now},
               total_count = #{totalCount}, matched_count = #{matchedCount}, missing_count = #{missingCount},
               mismatch_count = #{mismatchCount}, result_json = #{resultJson}, result_summary = #{summary},
               updated_by = #{userId}, updated_time = #{now}
         WHERE tenant_id = #{tenantId} AND tid = #{inventoryId} AND is_del = 0
    """, { now: now, totalCount: scannedTotal, matchedCount: matchedCount, missingCount: missingCount,
        mismatchCount: mismatchCount, resultJson: resultJson, summary: summary, userId: userId,
        tenantId: tenantId, inventoryId: inventoryId })
}
return { inventoryId: inventoryId, status: 'COMPLETED', totalCount: scannedTotal,
    matchedCount: matchedCount, missingCount: missingCount, mismatchCount: mismatchCount,
    issues: issues, summary: summary }
