{
  "properties": {},
  "id": "ods_data_agg_tree_statistics_01",
  "script": null,
  "groupId": "1f4028f20aaa4f379b5e37a343b99fa9",
  "name": "07.数据汇聚数据表树统计",
  "createTime": 1785060000000,
  "updateTime": 1790155763671,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/dataAggTreeStatistics",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"keyword\": \"出入境\",\n  \"orgIds\": []\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"total\": 374,\n  \"orgCounts\": {\"1351480103971975170\": 23},\n  \"orgApps\": {\"1351480103971975170\": [{\"value\": \"1980105567668809728\", \"label\": \"福建省指挥中心警情受理处置应用系统\", \"count\": 3, \"datasources\": [{\"value\": \"4b5d9992d89744b9b514f8905dc27998\", \"label\": \"出入境人员动管系统数据库\", \"count\": 9}]}]}\n}",
  "description": "数据接入左侧树数据表统计（POST /ods/dataAggTreeStatistics）。只读接口；关系库限定已完成登记的数据源，Elasticsearch dataassets 投影提供层级信息并统计可接入数据表（业务表/日志表/字典表）；没有表的数据源统计为 0。与右侧列表使用相同的数据源资格边界；使用请求参数 keyword、orgIds；涉及 db_datasource_t、db_table_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：07.数据汇聚数据表树统计
// 路由：POST /ods/dataAggTreeStatistics
// 业务说明：数据接入左侧树数据表统计（POST /ods/dataAggTreeStatistics）。只读接口；关系库限定已完成登记的数据源，Elasticsearch dataassets 投影提供层级信息并统计可接入数据表（业务表/日志表/字典表）；没有表的数据源统计为 0。与右侧列表使用相同的数据源资格边界；使用请求参数 keyword、orgIds；涉及 db_datasource_t、db_table_t 等业务表。
import tenantEs
import tenantRuntime
import '@/common/configData' as configData

/*
 * 数据接入左树统计接口（POST /ods/dataAggTreeStatistics）。
 *
 * 1. 先从关系库取得已完成登记的数据源，再读取对应 ES 数据源投影；
 * 2. 读取同一数据源范围内的 ES table 投影，统计业务表、日志表、字典表数量；
 * 3. 只返回统计与层级结构，不写入 MySQL / ES，右侧列表仍由 /ods/dataAggPage 按相同表口径分页查询。
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

// 统计必须和 /ods/dataAggPage 一样，仅以当前登录租户作为业务数据边界。
var tenantId = text(tenantRuntime.id()).trim()
if (blank(tenantId)) {
    exit 401, '登录状态已失效，请重新登录'
}

var keyword = text(body?.keyword).trim()
var orgIds = body?.orgIds

// 数据源是否已登记以关系库主表为准；ES 仅用于补充检索投影和统计数据表。
// 树保留已完成登记的数据源节点，即使其中暂时没有右表可展示的接入候选表。
// 实际计数另以 /ods/dataAggPage 的已登记资产状态作为准入条件。
var registeredDatasourceRows = db.table('db_datasource_t').where()
    .eq('tenant_id', tenantId).eq('is_del', 0)
    .eq('asset_status', 2).select()
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
                { wildcard: { dbName: 'codex_*' } },
                { wildcard: { database: 'codex_*' } }
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
    doc.tid = datasourceId
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

// 左树数量必须与 /ods/dataAggPage 的 table 分支完全同口径：
// 仅计入当前租户、已完成登记的表资产，以及可创建接入任务的三类业务表。
// 不能以 ES/物理库的全量表投影计数，否则未登记表会让左树与右表失真。
var tableCountsByDatasource = {}
var total = 0
if (datasourceIds.size() > 0) {
    var registeredTableRows = db.table('db_table_t')
        .columns('tid', 'datasource_id', 'business_type')
        .where()
        .in('datasource_id', datasourceIds)
        .eq('tenant_id', tenantId)
        .eq('is_del', 0)
        .eq('asset_status', 2)
        .in('business_type', ['业务表', '日志表', '字典表'])
        .select()
    for (registeredTableRow in (registeredTableRows == null ? [] : registeredTableRows)) {
        var tableDatasourceId = text(first(registeredTableRow, ['datasourceId', 'datasource_id']))
        if (blank(tableDatasourceId) || datasourceMap[tableDatasourceId] == null) {
            continue
        }
        tableCountsByDatasource[tableDatasourceId] =
            (tableCountsByDatasource[tableDatasourceId] == null ? 0 : tableCountsByDatasource[tableDatasourceId]) + 1
        total = total + 1
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
