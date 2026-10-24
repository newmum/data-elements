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
  "description": "分发任务组织树（保留 POST /ods/hiveTargetTree 路由）。只读；按当前租户资源中心 ODS 层及分域绑定的来源库聚合已登记三类表，来源库及表均须 asset_status>0，保留租户和角色边界；树与分发分页口径一致。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.Hive原始库组织树接口（分发任务）
// 路由：POST /ods/hiveTargetTree
// 业务说明：数据接入左侧树数据表统计（POST /ods/dataAggTreeStatistics）。只读接口；关系库限定数据源与数据表登记状态均大于 0，按当前租户及角色范围一次分组统计业务表/日志表/字典表，Elasticsearch 投影仅辅助层级展示；没有表的数据源统计为 0。与右侧列表使用相同的数据源资格边界；使用请求参数 keyword、orgIds；涉及 db_datasource_t、db_table_t 等业务表。
import tenantEs
import tenantRuntime
import dataScope
import '@/common/configData' as configData

/*
 * 数据接入左树统计接口（POST /ods/dataAggTreeStatistics）。
 *
 * 1. 先从关系库取得登记状态大于 0 的数据源，再读取对应 ES 数据源投影；
 * 2. 在同一授权数据源范围内，以关系库登记状态一次分组统计业务表、日志表、字典表数量；
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

var distributionView = true
// 接入/分发依据当前租户资源中心 ODS 层及其分域绑定分类，不按库名、节点类型或数据库产品判断。
// 同时校验真实字典根、层/域和绑定的 tenant_id / is_del，旧解绑或其他租户配置不能改变当前候选范围。
var warehouseBindings = db.select("""
    SELECT DISTINCT binding.datasource_id AS datasourceId
      FROM dwm_center_layer_source_t binding
      JOIN sym_dict_t node ON node.tid=binding.target_id AND node.tenant_id=#{tenantId} AND node.is_del=0
      JOIN sym_dict_t ods ON ods.tenant_id=#{tenantId} AND ods.is_del=0 AND LOWER(ods.dict_code)='ods'
           AND ((binding.target_type='layer' AND node.tid=ods.tid)
                OR (binding.target_type='domain' AND node.parent_id=ods.tid))
      JOIN sym_dict_t root ON root.tid=ods.parent_id AND root.tenant_id=#{tenantId}
           AND root.is_del=0 AND root.parent_id='0' AND root.dict_code='dataSourceType'
     WHERE binding.tenant_id=#{tenantId} AND binding.is_del=0 AND binding.datasource_id IS NOT NULL
""")
var warehouseDatasourceIds = []
for (binding in (warehouseBindings == null ? [] : warehouseBindings)) {
    var bindingDatasourceId = text(first(binding, ['datasourceId', 'datasourceid', 'datasource_id']))
    if (!blank(bindingDatasourceId) && !warehouseDatasourceIds.contains(bindingDatasourceId)) {
        warehouseDatasourceIds.add(bindingDatasourceId)
    }
}
var matchesTaskCategory = (datasource) => distributionView == warehouseDatasourceIds.contains(text(first(datasource, ['tid', 'id'])))

var keyword = text(body?.keyword).trim()
var orgIds = body?.orgIds

// 数据源是否已登记以关系库主表为准；ES 仅用于补充检索投影和统计数据表。
// 树保留登记状态大于 0 的数据源节点，即使其中暂时没有右表可展示的接入候选表。
// 实际计数另以 /ods/dataAggPage 的已登记资产状态作为准入条件。
var registeredDatasourceRows = db.table('db_datasource_t').where()
    .eq('tenant_id', tenantId).eq('is_del', 0)
    .gt('asset_status', 0).select()
registeredDatasourceRows = dataScope.visibleDataSourcesFor('MENU:2081000000000000002', registeredDatasourceRows)
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

// 左树数量必须与 /ods/dataAggPage 的 table 分支完全同口径：
// 仅计入当前租户、登记状态大于 0 的表资产，以及可创建接入任务的三类业务表。
// 不能以 ES/物理库的全量表投影计数，否则未登记表会让左树与右表失真。
var tableCountsByDatasource = {}
var total = 0
if (datasourceIds.size() > 0) {
    var registeredTableRows = db.select("""
        SELECT datasource_id AS datasourceId, COUNT(*) AS tableCount
          FROM db_table_t
         WHERE tenant_id=#{tenantId} AND is_del=0 AND asset_status>0
           AND datasource_id IN <foreach collection="datasourceIds" item="sourceDb" open="(" close=")" separator=",">#{sourceDb}</foreach>
           AND business_type IN ('业务表','日志表','字典表')
         GROUP BY datasource_id
    """)
    for (registeredTableRow in (registeredTableRows == null ? [] : registeredTableRows)) {
        var tableDatasourceId = text(first(registeredTableRow, ['datasourceId', 'datasourceid', 'datasource_id']))
        if (blank(tableDatasourceId) || datasourceMap[tableDatasourceId] == null) {
            continue
        }
        var tableCount = first(registeredTableRow, ['tableCount', 'tablecount', 'table_count'])::int
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
