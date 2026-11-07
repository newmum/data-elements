{
  "properties": {},
  "id": "48f4dd83b6a943fbba6b252921c730f8",
  "script": null,
  "groupId": "46fe687ba7304a608b5e2865725ad21c",
  "name": "06.DST层总览",
  "createTime": 1782955345100,
  "updateTime": 1787153757484,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/dst-info",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "资源中心 06：按当前组织树汇总 DST 层应用系统、数据源和数据表；涉及 rm_org_t、db_datasource_t、db_table_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import "@post:/sym/user/me" as me
import tenantRuntime
import dataScope
import tableRead

/**
 * DST 层总览。GET /dwm/center/dst-info
 * 按当前租户、数据源角色范围及账号组织树，汇总各组织的应用、数据源和已登记数据表。
 * 不接收业务筛选参数。返回 dstTableCount、dbCount 及按组织排序的 dst，接口只读。
 */

var textOf = value => value == null ? '' : value + ''
var tenantId = textOf(tenantRuntime.id())
if (tenantId == '') {
    exit 401, '当前租户无效'
}
var scope = dataScope.dataSource()
var scopeMode = textOf(scope?.scope)
var scopeUserId = textOf(scope?.userId)
var scopeOrgIds = scope?.organizationIds == null ? [] : scope.organizationIds
if (!['ALL', 'ORG', 'OWNER'].contains(scopeMode) || scopeUserId == '') {
    exit 403, '无法确定当前数据范围'
}
var column = (alias, name) => tableRead.column(alias, name, 'TEXT')
var numberColumn = (alias, name) => tableRead.column(alias, name, 'NUMBER')
var active = alias => tableRead.equal(numberColumn(alias, 'is_del'), 0)
var sourceOrg = column('d', 'org_id')
var sources = tableRead.from('db_datasource_t', 'd', tenantId)
// 表的登记和删除条件留在关联内，保证没有已登记表的数据源也计入。
sources.leftJoin('db_table_t', 't', 'datasource_id', 'tid', [active('t'), tableRead.equal(numberColumn('t', 'asset_status'), 2)])
sources.where(active('d'))
sources.where(tableRead.in(numberColumn('d', 'asset_status'), [1, 2]))
if (scopeMode == 'OWNER') {
    sources.where(tableRead.equal(column('d', 'created_by'), scopeUserId))
} else if (scopeMode == 'ORG') {
    sources.where(tableRead.in(sourceOrg, scopeOrgIds))
}
var statsRows = sources.grouped({
    orgId: sourceOrg,
    appCount: tableRead.countDistinct(column('d', 'app_id')),
    dbCount: tableRead.countDistinct(column('d', 'tid')),
    tableCount: tableRead.countDistinct(column('t', 'tid'))
}, [sourceOrg])

var statsByOrg = {}
var orgIds = []
for (row in statsRows) {
    var orgId = textOf(row.orgId)
    if (orgId != '' && (row.appCount > 0 || row.dbCount > 0 || row.tableCount > 0)) {
        statsByOrg[orgId] = row
        orgIds.add(orgId)
    }
}

var currentUser = me()
var rootSerial = textOf(currentUser?.orgRootSerialNumber == null ? currentUser?.org_root_serial_number : currentUser.orgRootSerialNumber)
if (orgIds.size() == 0) {
    return {dstTableCount: 0, dbCount: 0, dst: []}
}
var organizations = db.camel().table('rm_org_t').columns('id', 'name').where()
    .eq('tenant_id', tenantId).eq('deleted', 0).orderBy('sort_num', 'asc')
if (rootSerial != '') {
    organizations.like('serial_number', rootSerial + '%')
}

var result = {dstTableCount: 0, dbCount: 0, dst: []}
var addOrganizations = rows => {
    for (org in (rows == null ? [] : rows)) {
        var orgId = textOf(org.id)
        var stats = statsByOrg[orgId]
        if (stats != null) {
            result.dst.add({
                orgId: orgId, name: org.name, appCount: stats.appCount,
                dbCount: stats.dbCount, tableCount: stats.tableCount
            })
            result.dstTableCount = result.dstTableCount + stats.tableCount
            result.dbCount = result.dbCount + stats.dbCount
        }
    }
}

var orgWindow = 1000
if (orgIds.size() <= orgWindow) {
    // 常规规模只读取有统计值的组织，并沿用数据库的组织排序。
    addOrganizations(organizations.in('id', orgIds).select())
} else {
    // 大组织树按有界窗口读取。相同排序值按 ID 区分，保持跨窗口稳定且不丢失组织。
    organizations.orderBy('id', 'asc')
    var first = organizations.page(orgWindow, 0)
    addOrganizations(first.list)
    var offset = orgWindow
    while (offset < first.total) {
        var page = organizations.page(orgWindow, offset)
        if (page.list == null || page.list.size() == 0) {
            break
        }
        addOrganizations(page.list)
        offset = offset + orgWindow
    }
}
return result
