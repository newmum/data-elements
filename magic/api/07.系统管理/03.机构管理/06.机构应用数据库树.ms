{
  "properties": {},
  "id": "9caa9255f24d4bd08ca8de3ab9eb7de5",
  "script": null,
  "groupId": "c1736799c6af43359a75e6764601931f",
  "name": "06.机构应用数据库树",
  "createTime": 1782660716227,
  "updateTime": 1784437771357,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/assets",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"parentId\": \"2057646041423282177\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "从当前租户的物理索引按角色可见范围读取机构、应用与数据库树。默认使用租户配置的组织根。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import '@/common/configData' as configData
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import dataScope
import db
import jsons
import tenantRuntime

// 按当前租户和配置根读取机构，查询角色可见的索引资产并核对应用与数据库的机构关联。
var tenantId = tenantRuntime.id()
if (!tenantId) { exit 401, '当前租户无效' }
var config = configData()
var rootId = config.orgRootId
var parent = db.table('rm_org_t').where().eq('tenant_id', tenantId)
    .eq('id', rootId).eq('deleted', 0).selectOne()
if (parent == null || !parent.serialNumber) { return [] }

var page = db.table('rm_org_t')
    .excludes('shortName','creditCode','standard','createId','updateId','createTime','updateTime','deleted')
    .where().eq('tenant_id', tenantId).eq('deleted', 0)
    .like('serial_number', parent.serialNumber + '%')
    .orderBy('sort_num').orderBy('id', 'asc').page(10001, 0)
if (page.total > 10000) { exit 413, '机构树超过10000项' }
var rows = page.list
var orgs = {}
var roots = []
for (node in rows) {
    node.label = node.name
    node.value = node.id
    node.children = []
    orgs[node.id] = node
}
for (node in rows) {
    if (node.parentId == rootId) {
        roots.add(node)
    } else if (orgs[node.parentId] != null && node.id != rootId) {
        orgs[node.parentId].children.add(node)
    }
}

var scope = dataScope.resource('COMPONENT:DST-DETAIL')
if (!['ALL', 'ORG', 'OWNER'].contains(scope?.scope) || !scope?.userId) {
    exit 403, '无法确定当前数据范围'
}
var orgIds = rows.map(node => node.id)
if (scope.scope == 'ORG') {
    orgIds = orgIds.filter(id => scope.organizationIds.contains(id))
}
if (orgIds.isEmpty() || !esCommonService.indexExists(config.es_dataassets)) { return roots }

var filters = [{terms: {orgId: orgIds}}, {terms: {assetType: ['app', 'db']}}]
if (scope.scope == 'OWNER') { filters.add({term: {createdBy: scope.userId}}) }
// 索引服务按请求上下文选择物理租户索引，保持历史索引资产 ID 与前端计数的对应。
var matches = esCommonService.searchByJsonQuery(config.es_dataassets, jsons.toJSONStr({bool: {filter: filters}}),
    Map.class, 1, 2000)
if (matches.total > 2000) { exit 413, '当前机构资产树超过2000项' }

var visible = asset => {
    if (!asset?.id || !asset?.orgId || asset.orgId == rootId || orgs[asset.orgId] == null) { return false }
    if (asset.tenantId && asset.tenantId != tenantId) { return false }
    if (scope.scope == 'ORG') { return scope.organizationIds.contains(asset.orgId) }
    return scope.scope == 'ALL' || asset.createdBy == scope.userId
}
var applications = {}
for (asset in matches.list) {
    if (asset.assetType != 'app' || !visible(asset) || applications[asset.id] != null) { continue }
    var app = {tid: asset.id, id: asset.id, parentId: asset.orgId, name: asset.appName, type: 'app',
        label: asset.appName, value: asset.id, children: []}
    applications[asset.id] = app
    orgs[asset.orgId].children.add(app)
}
var databases = {}
for (asset in matches.list) {
    if (asset.assetType != 'db' || !visible(asset) || databases[asset.id] != null) { continue }
    var app = applications[asset.appId]
    if (app == null || app.parentId != asset.orgId) { continue }
    databases[asset.id] = true
    app.children.add({tid: asset.id, id: asset.id, parentId: app.id, name: asset.dbName, type: 'db',
        dbType: asset.dbType, label: asset.dbName, value: asset.id, children: []})
}
return roots
