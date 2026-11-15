{
  "properties": {},
  "id": "a55e7c1d2f4b4a6e9c3d8b1f5a7e2c45",
  "script": null,
  "groupId": "datapd_asset_group_01",
  "name": "06.资产评估执行",
  "createTime": 1791030855468,
  "updateTime": 1791030855468,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/evaluation/calculate",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"period\":\"YYYY-MM\",\"orgId\":\"\",\"dryRun\":false}",
  "headers": [],
  "paths": [],
  "responseBody": "{\"period\":\"YYYY-MM\",\"rows\":[]}",
  "description": "按周期汇总目录质量、使用、共享与安全指标，生成部门资产评估得分与等级；固定聚合查询，支持 dryRun。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.资产评估执行
// 路由：POST /dst/asset/evaluation/calculate
// 业务说明：按周期汇总目录质量、使用、共享与安全指标，生成部门资产评估得分与等级；固定聚合查询，支持 dryRun。
import cn.hutool.core.util.IdUtil
import jsons
import tenantRuntime
import "@post:/sym/user/me" as me

var tenantId = tenantRuntime.id()
var user = me()
var userId = user == null ? '' : (user.id == null ? '' : user.id + '')
var text = (value) => value == null ? '' : value + ''
var num = (value) => value == null ? 0 : value::long
var dec = (value) => value == null ? 0 : value::double
var first = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        if (row[key] != null && text(row[key]).trim() != '') { return row[key] }
    }
    return null
}
var period = text(body?.period).trim()
if (!period.matches('^[0-9]{4}-(0[1-9]|1[0-2])$')) { exit 400, '评估周期格式必须为 YYYY-MM' }
var orgIdValue = text(body?.orgId).trim()
if (orgIdValue != '') {
    var orgRows = db.select("SELECT id FROM rm_org_t WHERE tenant_id = #{tenantId} AND id = #{orgIdValue} AND deleted = 0")
    if (orgRows == null || orgRows.size() == 0) { exit 404, '部门不存在或不在当前租户' }
}
var dryRun = body?.dryRun == true
var now = new Date()

var orgFilter = ''
if (orgIdValue != '') { orgFilter = ' AND c.org_id = #{orgIdValue}' }
var baseRows = db.select("""
    SELECT c.org_id AS orgId,
           COUNT(1) AS catalogCount,
           COALESCE(SUM(CASE WHEN c.asset_status = 2 AND c.flow_status = 2 THEN 1 ELSE 0 END), 0) AS approvedCount,
           COALESCE(SUM(COALESCE(c.market_view_count, 0)), 0) AS viewTotal
      FROM da_catalog_t c
     WHERE c.tenant_id = #{tenantId} AND c.is_del = 0""" + orgFilter + """
     GROUP BY c.org_id
""")
var qualityRows = db.select("""
    SELECT c.org_id AS orgId,
           COUNT(col.tid) AS columnCount,
           COALESCE(SUM(CASE WHEN col.data_standard_id IS NOT NULL AND m.tid IS NOT NULL THEN 1 ELSE 0 END), 0) AS coveredCount
      FROM da_catalog_t c
      JOIN da_catalog_resource_binding_t b
        ON b.tenant_id = c.tenant_id AND b.catalog_id = c.tid AND b.status = 'ACTIVE' AND b.resource_type = 'TABLE'
      JOIN db_table_column_t col
        ON col.tenant_id = c.tenant_id AND col.table_id = b.resource_id AND col.is_del = 0
      LEFT JOIN da_metadata_t m
        ON m.tenant_id = col.tenant_id AND m.tid = col.data_standard_id AND m.is_del = 0 AND m.publish_status = 1
     WHERE c.tenant_id = #{tenantId} AND c.is_del = 0""" + orgFilter + """
     GROUP BY c.org_id
""")
var sharingRows = db.select("""
    SELECT c.org_id AS orgId, COUNT(DISTINCT c.tid) AS sharedCount
      FROM da_catalog_t c
      JOIN da_catalog_resource_binding_t b
        ON b.tenant_id = c.tenant_id AND b.catalog_id = c.tid AND b.status = 'ACTIVE' AND b.binding_role = 'DELIVERY'
     WHERE c.tenant_id = #{tenantId} AND c.is_del = 0""" + orgFilter + """
     GROUP BY c.org_id
""")
var securityRows = db.select("""
    SELECT c.org_id AS orgId, COUNT(DISTINCT s.catalog_id) AS securedCount
      FROM da_catalog_t c
      LEFT JOIN asset_security_t s
        ON s.tenant_id = c.tenant_id AND s.catalog_id = c.tid AND s.is_del = 0
     WHERE c.tenant_id = #{tenantId} AND c.is_del = 0""" + orgFilter + """
     GROUP BY c.org_id
""")
var state = {}
var orgKeys = []
var ensure = (orgKey) => {
    if (state[orgKey] == null) {
        state[orgKey] = { catalogCount: 0, approvedCount: 0, viewTotal: 0, columnCount: 0, coveredCount: 0, sharedCount: 0, securedCount: 0 }
        orgKeys.add(orgKey)
    }
    return state[orgKey]
}
for (row in baseRows) {
    var item = ensure(text(first(row, ['orgId', 'orgid', 'ORGID'])))
    item.catalogCount = num(first(row, ['catalogCount', 'catalogcount', 'CATALOGCOUNT']))
    item.approvedCount = num(first(row, ['approvedCount', 'approvedcount', 'APPROVEDCOUNT']))
    item.viewTotal = num(first(row, ['viewTotal', 'viewtotal', 'VIEWTOTAL']))
}
for (row in qualityRows) {
    var item = ensure(text(first(row, ['orgId', 'orgid', 'ORGID'])))
    item.columnCount = num(first(row, ['columnCount', 'columncount', 'COLUMNCOUNT']))
    item.coveredCount = num(first(row, ['coveredCount', 'coveredcount', 'COVEREDCOUNT']))
}
for (row in sharingRows) {
    var item = ensure(text(first(row, ['orgId', 'orgid', 'ORGID'])))
    item.sharedCount = num(first(row, ['sharedCount', 'sharedcount', 'SHAREDCOUNT']))
}
for (row in securityRows) {
    var item = ensure(text(first(row, ['orgId', 'orgid', 'ORGID'])))
    item.securedCount = num(first(row, ['securedCount', 'securedcount', 'SECUREDCOUNT']))
}
var rows = []
for (orgKey in orgKeys) {
    var item = state[orgKey]
    var quality = item.columnCount == 0 ? 0.0 : item.coveredCount::double * 100.0 / item.columnCount::double
    var avgViews = item.catalogCount == 0 ? 0.0 : item.viewTotal::double / item.catalogCount::double
    var usage = Math.min(100.0, avgViews * 10.0)
    var sharing = item.approvedCount == 0 ? 0.0 : item.sharedCount::double * 100.0 / item.approvedCount::double
    var security = item.catalogCount == 0 ? 0.0 : item.securedCount::double * 100.0 / item.catalogCount::double
    var total = Math.round((quality * 0.35 + usage * 0.20 + sharing * 0.20 + security * 0.25) * 100.0) / 100.0
    var grade = total >= 90.0 ? 'A' : total >= 75.0 ? 'B' : total >= 60.0 ? 'C' : 'D'
    rows.add({ orgId: orgKey, catalogCount: item.catalogCount, approvedCount: item.approvedCount,
        columnCount: item.columnCount, coveredCount: item.coveredCount, sharedCount: item.sharedCount,
        securedCount: item.securedCount, avgViews: Math.round(avgViews * 100.0) / 100.0,
        qualityScore: Math.round(quality * 100.0) / 100.0, usageScore: Math.round(usage * 100.0) / 100.0,
        sharingScore: Math.round(sharing * 100.0) / 100.0, securityScore: Math.round(security * 100.0) / 100.0,
        totalScore: total, grade: grade })
}
for (row in rows) {
    var rank = 1
    for (other in rows) { if (other.totalScore > row.totalScore) { rank = rank + 1 } }
    row.rankNo = rank
}
if (dryRun) { return { dryRun: true, period: period, rows: rows } }
var written = 0
for (row in rows) {
    var orgKey = row.orgId
    var orgBind = orgKey == '' ? null : orgKey
    var evaluationCode = 'EVAL-' + period + '-' + (orgKey == '' ? 'ALL' : orgKey)
    if (evaluationCode.length() > 64) { evaluationCode = 'EVAL-' + period + '-' + IdUtil.simpleUUID() }
    var existingSql = "SELECT tid FROM asset_evaluation_t WHERE tenant_id = #{tenantId} AND period = #{period} AND is_del = 0"
    if (orgKey == '') { existingSql = existingSql + " AND (org_id IS NULL OR org_id = '')" }
    else { existingSql = existingSql + ' AND org_id = #{orgBind}' }
    var existing = db.select(existingSql)
    var detailJson = jsons.toJSONStr({ columnCount: row.columnCount, coveredCount: row.coveredCount,
        sharedCount: row.sharedCount, securedCount: row.securedCount, avgViews: row.avgViews,
        weights: { quality: 0.35, usage: 0.20, sharing: 0.20, security: 0.25 } })
    if (existing == null || existing.size() == 0) {
        db.update("""
            INSERT INTO asset_evaluation_t
              (tid, tenant_id, evaluation_code, evaluation_name, period, org_id, catalog_count,
               quality_score, usage_score, sharing_score, security_score, total_score, grade, rank_no,
               detail_json, evaluated_time, created_by, updated_by, created_time, updated_time, is_del)
            VALUES
              (#{evaluationId}, #{tenantId}, #{evaluationCode}, #{evaluationName}, #{period}, #{orgBind}, #{catalogCount},
               #{qualityScore}, #{usageScore}, #{sharingScore}, #{securityScore}, #{totalScore}, #{grade}, #{rankNo},
               #{detailJson}, #{now}, #{userId}, #{userId}, #{now}, #{now}, 0)
        """, { evaluationId: IdUtil.getSnowflakeNextIdStr(), tenantId: tenantId, evaluationCode: evaluationCode,
            evaluationName: '资产评估-' + period, period: period, orgBind: orgBind, catalogCount: row.catalogCount,
            qualityScore: row.qualityScore, usageScore: row.usageScore, sharingScore: row.sharingScore,
            securityScore: row.securityScore, totalScore: row.totalScore, grade: row.grade, rankNo: row.rankNo,
            detailJson: detailJson, now: now, userId: userId })
    } else {
        var evaluationId = text(first(existing[0], ['tid', 'TID']))
        db.update("""
            UPDATE asset_evaluation_t
               SET evaluation_code = #{evaluationCode}, catalog_count = #{catalogCount},
                   quality_score = #{qualityScore}, usage_score = #{usageScore}, sharing_score = #{sharingScore},
                   security_score = #{securityScore}, total_score = #{totalScore}, grade = #{grade}, rank_no = #{rankNo},
                   detail_json = #{detailJson}, evaluated_time = #{now}, updated_by = #{userId}, updated_time = #{now}
             WHERE tenant_id = #{tenantId} AND tid = #{evaluationId} AND is_del = 0
        """, { evaluationCode: evaluationCode, catalogCount: row.catalogCount, qualityScore: row.qualityScore,
            usageScore: row.usageScore, sharingScore: row.sharingScore, securityScore: row.securityScore,
            totalScore: row.totalScore, grade: row.grade, rankNo: row.rankNo, detailJson: detailJson,
            now: now, userId: userId, tenantId: tenantId, evaluationId: evaluationId })
    }
    written = written + 1
}
return { period: period, rows: rows, written: written }
