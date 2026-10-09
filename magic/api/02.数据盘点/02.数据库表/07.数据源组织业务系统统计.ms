{
  "properties": {},
  "id": "datapd_database_tree_statistics_01",
  "script": null,
  "groupId": "datapd_database_group_01",
  "name": "07.数据源组织业务系统统计",
  "createTime": 1784814000000,
  "updateTime": 1784814000000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/treeStatistics",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"keyword\": \"出入境\",\n  \"dbType\": \"mysql\",\n  \"registrationState\": \"pending\",\n  \"orgIds\": []\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"total\": 1,\n  \"assignedTotal\": 1,\n  \"unassignedTotal\": 0,\n  \"orgCounts\": {\"1351480103971975170\": 1},\n  \"orgApps\": {\"1351480103971975170\": [{\"value\": \"1980105567668809728\", \"label\": \"福建公安出入境人员动态管控系统\", \"count\": 1, \"type\": \"app\", \"datasources\": [{\"value\": \"4b5d9992d89744b9b514f8905dc27998\", \"label\": \"出入境人员动管系统数据库\", \"count\": 1, \"dbType\": \"mysql\", \"type\": \"datasource\"}]}]},\n  \"dbTypes\": {\"mysql\": 1},\n  \"assetStatuses\": {\"completed\": 0,\"pending\": 1}\n}",
  "description": "数据源组织业务系统统计（POST /dst/database/treeStatistics）。只读页面专用接口；完全从 Elasticsearch dataassets 索引按关键字、数据库类型和登记状态筛选，通过 orgId→appId 嵌套聚合返回组织机构、业务系统及数据源数量，并同时返回数据库类型和登记状态分面；不查询或写入数据库；使用请求参数 keyword、dbType、registrationState、orgIds；涉及 db_datasource_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：07.数据源组织业务系统统计
// 路由：POST /dst/database/treeStatistics
// 业务说明：数据源组织业务系统统计（POST /dst/database/treeStatistics）。只读页面专用接口；完全从 Elasticsearch dataassets 索引按关键字、数据库类型和登记状态筛选，通过 orgId→appId 嵌套聚合返回组织机构、业务系统及数据源数量，并同时返回数据库类型和登记状态分面；不查询或写入数据库；使用请求参数 keyword、dbType、registrationState、orgIds；涉及 db_datasource_t 等业务表。
import tenantEs
import tenantRuntime
var tenantId = tenantRuntime.id()
import '@/common/configData' as configData
import dataScope

/*
 * 数据源组织树统计。
 * 数据读取：Elasticsearch dataassets 索引负责筛选，db_datasource_t 负责当前租户
 * 有效数据源准入校验。必须与 /dst/database/page 保持相同口径，避免 ES 删除残留
 * 进入左树统计而右侧列表不可见。
 * 数据写入：无。
 * 核心逻辑：使用 orgId terms + appId + tid 子聚合，一次请求构建“部门→业务系统→数据源”统计树。
 * 数据源列表、数据目录和数据接入页面共用该接口，所有左树节点与数据源范围保持一致。
 */
var text = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || text(value).trim() == ''

var filters = [ { term: { assetType: 'db' } } ]
var keyword = text(body?.keyword).trim()
var dbType = text(body?.dbType).trim()
var registrationState = text(body?.registrationState).trim()
var orgIds = body?.orgIds

// 统计接口与分页接口保持完全相同的筛选口径。
if (!blank(keyword)) {
    filters.add({
        bool: {
            should: [
                { wildcard: { dbName: '*' + keyword + '*' } },
                { wildcard: { appName: '*' + keyword + '*' } },
                { wildcard: { database: '*' + keyword + '*' } }
            ],
            minimum_should_match: 1
        }
    })
}
if (!blank(dbType)) {
    filters.add({ term: { dbType: dbType } })
}
if (orgIds != null && orgIds.size() > 0) {
    filters.add({ terms: { orgId: orgIds } })
}

// 先取得与分页接口完全相同的 ES 候选集，再一次性回查当前租户未删除的数据源。
// 数据源数量远小于表资产数量，10000 的上限与分页接口一致。
var result = tenantEs.index(configData().es_dataassets).search({
    from: 0,
    size: 10000,
    track_total_hits: true,
    query: {
        bool: {
            filter: filters,
            must_not: [
                { wildcard: { dbName: 'codex_*' } },
                { wildcard: { database: 'codex_*' } }
            ]
        }
    }
})

var hits = result?.hits?.hits == null ? [] : result.hits.hits
var candidateIds = []
for (hit in hits) {
    var candidateId = hit?._source?.tid || hit?._id
    if (!blank(candidateId)) {
        candidateIds.add(candidateId + '')
    }
}

var datasourceById = {}
if (candidateIds.size() > 0) {
    var datasourceRows = db.table('db_datasource_t').where()
        .in('tid', candidateIds).eq('is_del', 0).select()
    datasourceRows = dataScope.visibleDataSourcesFor('MENU:2081000000000000002', datasourceRows)
    for (datasource in datasourceRows) {
        var datasourceId = datasource?.tid
        if (!blank(datasourceId)) {
            datasourceById[datasourceId + ''] = datasource
        }
    }
}

var validItems = []
for (hit in hits) {
    var indexItem = hit?._source
    if (indexItem == null) {
        continue
    }
    var datasourceId = indexItem.tid || hit._id
    var datasource = blank(datasourceId) ? null : datasourceById[datasourceId + '']
    if (datasource == null) {
        continue
    }
    var item = {}
    item.putAll(indexItem)
    item.putAll(datasource)
    item.tid = datasourceId
    var registered = text(datasource.asset_status == null ? datasource.assetStatus : datasource.asset_status) == '2'
    if (blank(registrationState) || registrationState == (registered ? 'completed' : text(datasource.asset_status == null ? datasource.assetStatus : datasource.asset_status) == '1' ? 'incomplete' : 'pending')) {
        item.assetStatus = datasource.asset_status == null ? datasource.assetStatus : datasource.asset_status
        validItems.add(item)
    }
}

var orgCounts = {}
var orgApps = {}
var dbTypes = {}
var assetStatuses = { completed: 0, incomplete: 0, pending: 0 }
var appAccumulators = {}
for (item in validItems) {
    var orgId = text(item?.orgId).trim()
    var appId = text(item?.appId).trim()
    var appName = text(item?.appName).trim()
    var dbName = text(item?.dbName).trim()
    var dbTypeValue = text(item?.dbType).trim()
    var assetStatusValue = text(item?.assetStatus) == '2' ? 'completed' : text(item?.assetStatus) == '1' ? 'incomplete' : 'pending'

    if (!blank(orgId)) {
        orgCounts[orgId] = (orgCounts[orgId] == null ? 0 : orgCounts[orgId]) + 1
    }
    if (!blank(dbTypeValue)) {
        dbTypes[dbTypeValue] = (dbTypes[dbTypeValue] == null ? 0 : dbTypes[dbTypeValue]) + 1
    }
    assetStatuses[assetStatusValue] =
        (assetStatuses[assetStatusValue] == null ? 0 : assetStatuses[assetStatusValue]) + 1

    // 历史“未匹配业务系统”占位项不进入树，部门总数仍保留有效数据源。
    if (blank(orgId) || blank(appId) || blank(appName)
            || text(appName).startsWith('未匹配业务系统')) {
        continue
    }
    var key = orgId + '::' + appId
    var app = appAccumulators[key]
    if (app == null) {
        app = {
            value: appId,
            label: appName,
            count: 0,
            type: 'app',
            datasources: [],
            datasourceIds: {}
        }
        appAccumulators[key] = app
        if (orgApps[orgId] == null) {
            orgApps[orgId] = []
        }
        orgApps[orgId].add(app)
    }
    app.count = app.count + 1
    var datasourceId = text(item?.tid)
    if (!blank(datasourceId) && blank(app.datasourceIds[datasourceId]) && !blank(dbName)) {
        app.datasourceIds[datasourceId] = true
        app.datasources.add({
            value: datasourceId,
            label: dbName,
            count: 1,
            dbType: dbTypeValue,
            type: 'datasource'
        })
    }
}

var assigned = 0
for (key, value in orgCounts) {
    assigned = assigned + value
}

for (orgId, apps in orgApps) {
    for (app in apps) {
        app.remove('datasourceIds')
    }
}

return {
    total: validItems.size(),
    assignedTotal: assigned,
    unassignedTotal: validItems.size() - assigned,
    orgCounts: orgCounts,
    orgApps: orgApps,
    dbTypes: dbTypes,
    assetStatuses: assetStatuses
}
