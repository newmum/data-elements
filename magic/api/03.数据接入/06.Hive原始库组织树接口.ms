{
  "properties": {},
  "id": "2b8dd89d130a4738a8aa1d0c8584e401",
  "script": null,
  "groupId": "1f4028f20aaa4f379b5e37a343b99fa9",
  "name": "06.Hive原始库组织树接口",
  "path": "/hiveTargetTree",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "分发任务组织树（保留 POST /ods/hiveTargetTree 路由）。只读；按当前租户资源中心 ODS 层及分域绑定的来源库聚合已登记三类表，来源库及表均须 asset_status>0，保留当前租户的有效记录边界；树与分发分页口径一致。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantEs
import tenantRuntime
import tableRead
import '@/common/configData' as configData
import '@/common/ods-source-classifier' as odsSourceClassifier

/**
 * POST /ods/hiveTargetTree 读取分发任务左侧组织树。
 * 输入 keyword、orgIds，返回 {total, orgCounts, orgApps}。未登记数据源及数据表不计入结果。
 * 来源资格与 /ods/hiveTargetPage 一致，按当前租户选取 ODS 层及直属分域绑定的数据源。
 * 关系库按来源库统计业务表、日志表和字典表，ES 投影仅补充展示信息。无写入。
 */
var text = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || text(value).trim() == ''
var first = (row, keys) => {
    if (row == null) {
        return null
    }
    for (key in keys) {
        if (row[key] != null && text(row[key]).trim() != '') {
            return row[key]
        }
    }
    return null
}
var normalize = (value) => text(value).toLowerCase().replace('_', '').replace('-', '').replace(' ', '')
var safeAssetSearch = (query) => {
    try {
        return tenantEs.index(configData().es_dataassets).search(query)
    } catch (e) {
        return { hits: { hits: [] } }
    }
}
var isAccessTableType = (value) => {
    var v = normalize(value)
    return v == '业务表' || v == '日志表' || v == '字典表' ||
        v == 'business' || v == 'businesstable' || v == 'log' || v == 'logtable' ||
        v == 'dict' || v == 'dictionary' || v == 'dictionarytable'
}
var addUnique = (list, value) => {
    var v = text(value)
    if (!blank(v) && !list.contains(v)) {
        list.add(v)
    }
}
var safeName = (value) => {
    var v = text(value).trim()
    if (blank(v) || v.startsWith('未匹配业务系统')) {
        return ''
    }
    return v
}

// 统计与 /ods/hiveTargetPage 一样，仅使用当前登录租户的数据。
var tenantId = text(tenantRuntime.id()).trim()
if (blank(tenantId)) {
    exit 401, '登录状态已失效，请重新登录'
}

var distributionView = true
// 接入和分发共享真实 ODS 字典与绑定口径。
var odsSources = odsSourceClassifier()
var warehouseBindings = odsSources.query.grouped(odsSources.projection, odsSources.groups)
var warehouseDatasourceIds = []
for (binding in warehouseBindings) {
    var bindingDatasourceId = text(binding.source_id)
    if (!blank(bindingDatasourceId) && !warehouseDatasourceIds.contains(bindingDatasourceId)) {
        warehouseDatasourceIds.add(bindingDatasourceId)
    }
}
var matchesTaskCategory = (datasource) => distributionView == warehouseDatasourceIds.contains(text(first(datasource, ['tid', 'id'])))

var keyword = text(body?.keyword).trim()
var orgIds = body?.orgIds

// 数据源是否已登记以关系库主表为准；ES 仅用于补充检索投影和统计数据表。
// 树保留登记状态大于 0 的来源库，表数量按分发分页的登记资格统计。
var registeredDatasourceRows = db.table('db_datasource_t').where()
    .eq('tenant_id', tenantId).eq('is_del', 0)
    .gt('asset_status', 0).select()
registeredDatasourceRows = registeredDatasourceRows.filter(matchesTaskCategory)
var registeredDatasourceIds = []
var registeredDatasourceMap = {}
for (registeredDatasourceRow in (registeredDatasourceRows == null ? [] : registeredDatasourceRows)) {
    var registeredDatasourceId = text(first(registeredDatasourceRow, ['tid']))
    if (blank(registeredDatasourceId)) {
        continue
    }
    registeredDatasourceMap[registeredDatasourceId] = registeredDatasourceRow
    addUnique(registeredDatasourceIds, registeredDatasourceId)
}
if (registeredDatasourceIds.size() == 0) {
    return { total: 0, orgCounts: {}, orgApps: {} }
}

// 数据源结构优先读取 ES db 投影；没有投影时使用已登记主表兜底，避免树和列表范围不一致。
var dbFilters = [
    { term: { assetType: 'db' } },
    { terms: { tid: registeredDatasourceIds } }
]
if (orgIds != null && orgIds.size() > 0) {
    dbFilters.add({ terms: { orgId: orgIds } })
}
if (!blank(keyword)) {
    dbFilters.add({
        bool: {
            should: [
                { wildcard: { dbName: '*' + keyword + '*' } },
                { wildcard: { datasourceName: '*' + keyword + '*' } },
                { wildcard: { database: '*' + keyword + '*' } },
                { wildcard: { schema: '*' + keyword + '*' } },
                { wildcard: { appName: '*' + keyword + '*' } },
                { wildcard: { applicationName: '*' + keyword + '*' } },
                { wildcard: { systemName: '*' + keyword + '*' } }
            ],
            minimum_should_match: 1
        }
    })
}

var dbSearch = safeAssetSearch({
    from: 0,
    size: 10000,
    track_total_hits: true,
    query: {
        bool: {
            filter: dbFilters,
            must_not: [
                { wildcard: { dbName: 'validation_*' } },
                { wildcard: { database: 'validation_*' } }
            ]
        }
    },
    _source: [
        'tid', 'id',
        'orgId', 'org_id',
        'appId', 'app_id', 'appName', 'app_name', 'applicationName', 'application_name', 'systemName',
        'datasourceName', 'datasource_name', 'dbName', 'db_name', 'database', 'schema', 'dbType',
        'connectionStatus', 'connection_status', 'showConnect', 'show_connect', 'assetStatus', 'asset_status'
    ]
})

var datasourceMap = {}
var datasourceIds = []
for (hit in (dbSearch?.hits?.hits == null ? [] : dbSearch.hits.hits)) {
    var doc = hit?._source
    if (doc == null) {
        continue
    }
    var datasourceId = text(first(doc, ['tid', 'id']) || hit._id)
    if (blank(datasourceId)) {
        continue
    }
    if (registeredDatasourceMap[datasourceId] == null) { continue }
    doc.tid = datasourceId
    doc.assetStatus = first(registeredDatasourceMap[datasourceId], ['assetStatus', 'asset_status'])
    datasourceMap[datasourceId] = doc
    addUnique(datasourceIds, datasourceId)
}
for (registeredDatasourceId in registeredDatasourceIds) {
    if (datasourceMap[registeredDatasourceId] != null) {
        continue
    }
    var registeredDatasource = registeredDatasourceMap[registeredDatasourceId]
    registeredDatasource.tid = registeredDatasourceId
    datasourceMap[registeredDatasourceId] = registeredDatasource
    addUnique(datasourceIds, registeredDatasourceId)
}

// 一次按来源库汇总当前租户已登记的业务表、日志表和字典表。
var tableCountsByDatasource = {}
var total = 0
if (datasourceIds.size() > 0) {
    var tables = tableRead.from('db_table_t', 't', tenantId)
    var datasource = tableRead.column('t', 'datasource_id', 'TEXT')
    tables.where(tableRead.all([
        tableRead.equal(tableRead.column('t', 'is_del', 'NUMBER'), 0),
        tableRead.greaterThan(tableRead.column('t', 'asset_status', 'NUMBER'), 0),
        tableRead.in(datasource, datasourceIds),
        tableRead.in(tableRead.column('t', 'business_type', 'TEXT'), ['业务表', '日志表', '字典表'])
    ]))
    var registeredTableRows = tables.grouped({ source_id: datasource, table_count: tableRead.count() }, [datasource])
    for (registeredTableRow in registeredTableRows) {
        var tableDatasourceId = text(registeredTableRow.source_id)
        if (blank(tableDatasourceId) || datasourceMap[tableDatasourceId] == null) {
            continue
        }
        var tableCount = registeredTableRow.table_count::int
        tableCountsByDatasource[tableDatasourceId] = tableCount
        total = total + tableCount
    }
}

var orgCounts = {}
var appMap = {}
var datasourceMapForApp = {}

// 按“部门 -> 应用系统 -> 数据源”组装树扩展节点；0 表数据源 count=0 仍保留。
for (datasourceId in datasourceIds) {
    var datasourceDoc = datasourceMap[datasourceId]
    var orgId = text(first(datasourceDoc, ['orgId', 'org_id']))
    var appId = text(first(datasourceDoc, ['appId', 'app_id']))
    var appName = safeName(first(datasourceDoc, ['appName', 'app_name', 'applicationName', 'application_name', 'systemName']))
    var datasourceName = text(first(datasourceDoc, ['datasourceName', 'datasource_name', 'dbName', 'db_name', 'database', 'schema']))
    var datasourceCount = tableCountsByDatasource[datasourceId] == null ? 0 : tableCountsByDatasource[datasourceId]

    if (blank(orgId) || blank(datasourceName)) {
        continue
    }
    if (blank(appId)) {
        appId = 'unbound-' + orgId
    }
    if (blank(appName)) {
        appName = '未绑定业务系统'
    }

    orgCounts[orgId] = (orgCounts[orgId] == null ? 0 : orgCounts[orgId]) + datasourceCount

    var appKey = orgId + '::' + appId
    if (appMap[appKey] == null) {
        appMap[appKey] = {
            orgId: orgId,
            value: appId,
            label: appName,
            count: 0,
            type: 'app',
            children: [],
            datasources: []
        }
        datasourceMapForApp[appKey] = {}
    }
    appMap[appKey].count = appMap[appKey].count + datasourceCount

    datasourceMapForApp[appKey][datasourceId] = {
        value: datasourceId,
        label: datasourceName,
        count: datasourceCount,
        type: 'datasource',
        dbType: text(first(datasourceDoc, ['dbType'])),
        connectionStatus: first(datasourceDoc, ['connectionStatus', 'connection_status']),
        showConnect: first(datasourceDoc, ['showConnect', 'show_connect']),
        assetStatus: first(datasourceDoc, ['assetStatus', 'asset_status']),
        children: []
    }
}

var orgApps = {}
for (appKey, app in appMap) {
    var datasources = []
    for (datasourceKey, datasource in datasourceMapForApp[appKey]) {
        datasources.add(datasource)
    }
    app.datasources = datasources
    app.children = datasources
    if (orgApps[app.orgId] == null) {
        orgApps[app.orgId] = []
    }
    orgApps[app.orgId].add(app)
}

return {
    total: total,
    orgCounts: orgCounts,
    orgApps: orgApps
}
