{
  "properties": {},
  "id": "a55e7c1d2f4b4a6e9c3d8b1f5a7e2c40",
  "script": null,
  "groupId": "datapd_asset_group_01",
  "name": "01.资产模块分页",
  "createTime": 1791030855463,
  "updateTime": 1791030855463,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/module/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"module\":\"plan|discovery|disposal|security|operation|evaluation|inventory\",\"pageNo\":1,\"pageSize\":20,\"keyword\":\"\",\"status\":\"\",\"orgId\":\"\"}",
  "headers": [],
  "paths": [],
  "responseBody": "{\"list\":[],\"total\":0,\"page\":1,\"size\":20}",
  "description": "当前租户资产规划/发现/处置/安全/运维/评估/盘点记录分页查询；一次计数、一次分页，不按行补查。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.资产模块分页
// 路由：POST /dst/asset/module/page
// 业务说明：按当前租户分页查询资产规划/发现/处置/安全/运维/评估/盘点记录；一次计数、一次分页，不按行补查。
import tenantRuntime

var tenantId = tenantRuntime.id()
var text = (value) => value == null ? '' : value + ''
var modules = {
    plan: { table: 'asset_plan_t', code: 'plan_code', name: 'plan_name', status: 'status', org: 'org_id' },
    discovery: { table: 'asset_discovery_t', code: 'discovery_code', name: 'discovery_name', status: 'status', org: 'org_id' },
    disposal: { table: 'asset_disposal_t', code: 'disposal_code', name: 'asset_name', status: 'approval_status', org: '' },
    security: { table: 'asset_security_t', code: 'catalog_id', name: 'classification_name', status: 'policy_status', org: '' },
    operation: { table: 'asset_operation_t', code: 'catalog_id', name: 'operation_name', status: 'health_status', org: '' },
    evaluation: { table: 'asset_evaluation_t', code: 'evaluation_code', name: 'evaluation_name', status: 'grade', org: 'org_id' },
    inventory: { table: 'asset_inventory_t', code: 'inventory_code', name: 'inventory_name', status: 'status', org: '' }
}
var module = text(body?.module).trim().toLowerCase()
if (modules[module] == null) { exit 400, '资产模块无效' }
var config = modules[module]
var pageNo = body?.pageNo == null ? 1 : (body.pageNo + '')::int(1)
var pageSize = body?.pageSize == null ? 20 : (body.pageSize + '')::int(1)
if (pageNo < 1) { pageNo = 1 }
if (pageSize < 1) { pageSize = 20 }
if (pageSize > 100) { pageSize = 100 }
var keyword = text(body?.keyword).trim()
if (keyword.length() > 200) { exit 400, '检索关键词过长' }
var keywordPattern = '%' + keyword + '%'
var statusValue = text(body?.status).trim().toUpperCase()
var orgIdValue = text(body?.orgId).trim()
var sql = 'SELECT * FROM ' + config.table + ' WHERE tenant_id = #{tenantId} AND is_del = 0'
if (keyword != '') {
    sql = sql + ' AND (' + config.code + " LIKE #{keywordPattern} OR COALESCE(" + config.name + ", '') LIKE #{keywordPattern})"
}
if (statusValue != '') {
    sql = sql + ' AND ' + config.status + ' = #{statusValue}'
}
if (orgIdValue != '' && config.org != '') {
    sql = sql + ' AND ' + config.org + ' = #{orgIdValue}'
}
sql = sql + ' ORDER BY updated_time DESC, tid ASC'
var page = db.page(sql, pageSize, (pageNo - 1) * pageSize)
var pageList = page.list == null ? [] : page.list
var rows = []
var first = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        if (row[key] != null) { return row[key] }
    }
    return null
}
for (row in pageList) {
    var idValue = first(row, ['tid', 'TID', 'Tid'])
    if (idValue != null) { row['id'] = idValue }
    rows.add(row)
}
return { list: rows, total: page.total, page: pageNo, size: pageSize, module: module }
