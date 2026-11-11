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
  "description": "数据源组织业务系统统计（POST /dst/database/treeStatistics）。mode=facets 为本页全量聚合，参数 keyword、filter、orgIds、appId；当前租户及 ALL/ORG/OWNER 先于聚合，返回 complete、typeCounts、组织及应用计数，不受概要或ES候选数量限制。变更按500来源游标页读取并批量核验。省略mode保留其他页面原候选树契约；只读。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantEs
import tenantRuntime
import dataScope
import tableRead
import java.util.HashSet
import java.util.List
import '@/common/configData' as configData

/**
 * 数据源组织与业务系统统计（POST /dst/database/treeStatistics）。
 * mode=facets：按 keyword、filter、orgIds 和 appId 筛选，返回组织、应用、类型和登记状态的全量分面。
 * filter=changed 时，按 ES 游标分批取得变更候选，并在主表核验后聚合。
 * 省略 mode：按 keyword、dbType、registrationState 和 orgIds 筛选，返回带数据源节点的旧版树。
 * 两种模式都使用当前会话的租户和角色范围，只读且不返回连接配置。
 */

var text = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || text(value).trim() == ''

// 租户和数据范围来自当前会话，不能由请求参数指定。
var tenantId = tenantRuntime.id()
if (blank(tenantId)) {
    exit 401, '无法识别当前租户'
}
var scope = dataScope.resource('MENU:2081000000000000002')
var scopeKind = text(scope?.scope)
var scopeUserId = text(scope?.userId)
var scopeOrgIds = scope?.organizationIds == null ? [] : scope.organizationIds
if (!['ALL', 'ORG', 'OWNER'].contains(scopeKind)) {
    exit 403, '数据源数据范围无效'
}
if (scopeKind == 'OWNER' && blank(scopeUserId)) {
    exit 403, '无法识别当前登记人员'
}

// 请求组织只能缩小当前账号的角色范围。空组织范围仍然返回空结果。
var requestedOrgs = body?.orgIds == null ? [] : body.orgIds
if (!(requestedOrgs instanceof List) || requestedOrgs.size() > 1000) {
    exit 400, '组织筛选最多1000项'
}
var orgIds = []
for (id in requestedOrgs) {
    if (blank(id) || text(id).length() > 64) {
        exit 400, '组织标识不合法'
    }
    if (!orgIds.contains(text(id))) {
        orgIds.add(text(id))
    }
}

// 全量分面直接聚合主表，不受概要或 ES 命中窗口限制。
if (text(body?.mode) == 'facets') {
    var facetKeyword = text(body?.keyword).trim()
    var facetFilter = blank(body?.filter) ? 'all' : text(body.filter)
    if (facetKeyword.length() > 256
            || !['all', 'unannotated', 'incomplete', 'completed', 'changed'].contains(facetFilter)) {
        exit 400, '统计筛选条件不合法'
    }
    var facetAppId = text(body?.appId).trim()
    if (facetAppId.length() > 64) {
        exit 400, '业务系统标识不合法'
    }

    var stringColumn = (alias, name) => tableRead.column(alias, name, 'TEXT')
    var numberColumn = (alias, name) => tableRead.column(alias, name, 'NUMBER')
    var zeroIfNull = (value) => tableRead.coalesce([value, 0])
    var active = (alias) => tableRead.equal(zeroIfNull(numberColumn(alias, 'is_del')), 0)
    var orgColumn = stringColumn('d', 'org_id')
    var appColumn = stringColumn('d', 'app_id')
    var appName = stringColumn('a', 'app_name')
    var dbName = stringColumn('d', 'db_name')
    var dbTypeColumn = stringColumn('d', 'db_type')
    var assetStatus = numberColumn('d', 'asset_status')
    var showConnect = numberColumn('d', 'show_connect')
    var poolConfig = tableRead.column('d', 'pool_cfg', 'LOB')
    var accessMode = tableRead.coalesce([
        tableRead.blankAsNull(tableRead.jsonText(poolConfig, 'accessMode')),
        tableRead.blankAsNull(tableRead.jsonText(poolConfig, 'dataAccessMode'))
    ])
    var facetRows = (candidateIds) => {
        var source = tableRead.from('db_datasource_t', 'd', tenantId)
        source.leftJoin('sym_application_t', 'a', 'tid', 'app_id', [active('a')])
        source.where(active('d'))

        // 保留旧校验数据命名过滤。空名称用空格参与 LIKE，兼容将空字符串当 NULL 的数据库。
        source.where(tableRead.not(tableRead.like(tableRead.coalesce([dbName, ' ']), 'validation_%')))

        // 先限定角色数据范围，再叠加请求筛选和变更候选。
        if (scopeKind == 'OWNER') {
            source.where(tableRead.equal(stringColumn('d', 'created_by'), scopeUserId))
        }
        if (scopeKind == 'ORG') {
            source.where(tableRead.in(orgColumn, scopeOrgIds))
        }
        if (orgIds.size() > 0) {
            source.where(tableRead.in(orgColumn, orgIds))
        }
        if (!blank(facetAppId)) {
            source.where(tableRead.equal(appColumn, facetAppId))
        }
        if (candidateIds != null) {
            source.where(tableRead.in(stringColumn('d', 'tid'), candidateIds))
        }
        if (!blank(facetKeyword)) {
            var fields = [
                dbName,
                appName,
                dbTypeColumn,
                tableRead.jsonText(poolConfig, 'database'),
                tableRead.jsonText(poolConfig, 'schema')
            ]
            source.where(tableRead.any(fields.map(field => tableRead.contains(field, facetKeyword))))
        }
        if (facetFilter == 'incomplete') {
            source.where(tableRead.equal(assetStatus, 1))
        }
        if (facetFilter == 'completed') {
            source.where(tableRead.equal(assetStatus, 2))
        }
        if (facetFilter == 'unannotated') {
            // 以已探查数和有效表数的较大值为基数，再扣除已标注表。
            var sourceId = stringColumn('t', 'datasource_id')
            var tables = tableRead.from('db_table_t', 't', tenantId)
            tables.where(active('t'))
            source.leftJoinGrouped(tables, 'ts', 'datasource_id', 'tid', {
                datasource_id: sourceId,
                table_count: tableRead.count(),
                annotated_count: tableRead.countIf(tableRead.equal(numberColumn('t', 'annotated'), 1))
            }, [sourceId])
            var exploredCount = zeroIfNull(numberColumn('d', 'table_num'))
            var actualCount = zeroIfNull(numberColumn('ts', 'table_count'))
            var annotatedCount = zeroIfNull(numberColumn('ts', 'annotated_count'))
            source.where(tableRead.greaterThan(tableRead.greatest([exploredCount, actualCount]), annotatedCount))
        }
        return source.grouped({
            orgId: orgColumn,
            appId: appColumn,
            appName: appName,
            dbType: dbTypeColumn,
            accessMode: accessMode,
            showConnect: showConnect,
            assetStatus: assetStatus,
            sourceCount: tableRead.count()
        }, [orgColumn, appColumn, appName, dbTypeColumn, accessMode, showConnect, assetStatus])
    }

    var answer = {
        total: 0,
        assignedTotal: 0,
        unassignedTotal: 0,
        orgCounts: {},
        orgApps: {},
        dbTypes: {},
        typeCounts: {},
        assetStatuses: { completed: 0, incomplete: 0, pending: 0 },
        complete: true
    }
    var facetApps = {}
    var aliases = {
        pg: 'postgresql',
        postgres: 'postgresql',
        mssql: 'sqlserver',
        dm: 'dameng',
        kingbase: 'kingbase8',
        kingbasees: 'kingbase8',
        es: 'elasticsearch',
        elastic: 'elasticsearch'
    }
    var accumulate = (rows) => {
        // 仅遍历聚合桶，无逐项 SQL、ES 或权限补查。
        for (row in rows) {
            var count = (row.sourceCount || row.sourcecount || 0)::long
            var rawType = text(row.dbType || row.dbtype).trim()
            var typeKey = rawType.toLowerCase().replaceAll('[\\s_-]', '')
            typeKey = aliases[typeKey] || (blank(typeKey) ? 'other' : typeKey)
            var mode = text(row.accessMode || row.accessmode).trim().toLowerCase()
            if (mode == 'proxy') {
                typeKey = 'mode:proxy'
            } else if (['upload', 'report'].contains(mode)
                    || (typeKey == 'api' && !['capture', 'pull'].contains(mode)
                    && text(row.showConnect != null ? row.showConnect : row.showconnect) == '0')) {
                typeKey = 'mode:upload'
            } else if (['receive', 'push'].contains(mode)) {
                typeKey = 'mode:receive'
            }
            answer.total += count
            answer.typeCounts[typeKey] = (answer.typeCounts[typeKey] || 0) + count
            if (!blank(rawType)) {
                answer.dbTypes[rawType] = (answer.dbTypes[rawType] || 0) + count
            }
            var status = text(row.assetStatus || row.assetstatus)
            var statusKey = status == '2' ? 'completed' : status == '1' ? 'incomplete' : 'pending'
            answer.assetStatuses[statusKey] += count
            var org = text(row.orgId || row.orgid).trim()
            if (!blank(org)) {
                answer.orgCounts[org] = (answer.orgCounts[org] || 0) + count;
                answer.assignedTotal += count
            }
            var appId = text(row.appId || row.appid).trim()
            var appName = text(row.appName || row.appname).trim()
            if (!blank(org) && !blank(appId) && !blank(appName) && !appName.startsWith('未匹配业务系统')) {
                var appKey = org + '::' + appId
                if (facetApps[appKey] == null) {
                    var app = { value: appId, label: appName, count: 0, type: 'app' }
                    facetApps[appKey] = app
                    if (answer.orgApps[org] == null) {
                        answer.orgApps[org] = []
                    }
                    answer.orgApps[org].add(app)
                }
                facetApps[appKey].count += count
            }
        }
    }
    if (facetFilter != 'changed') {
        accumulate(facetRows(null))
    } else {
        // 变更只存在于 ES 表投影。每页最多 500 个源，按游标遍历并用同一授权范围核验。
        var sourceKeyScript = "if (doc.containsKey('datasourceId') && doc['datasourceId'].size()!=0) "
            + "return doc['datasourceId'].value; "
            + "if (doc.containsKey('dbId') && doc['dbId'].size()!=0) return doc['dbId'].value; return null;"
        var afterKey = null
        while (true) {
            var composite = {
                size: 500,
                sources: [{ sourceId: { terms: { script: { lang: 'painless', source: sourceKeyScript } } } }]
            }
            if (afterKey != null) {
                composite.after = afterKey
            }
            var changedPage = tenantEs.index(configData().es_dataassets).search({
                size: 0,
                timeout: '5s',
                query: {
                    bool: {
                        filter: [{ term: { assetType: 'table' } }],
                        should: [
                            { terms: { changeStatus: ['added', 'modified', 'deleted'] } },
                            { terms: { change_status: ['added', 'modified', 'deleted'] } }
                        ],
                        minimum_should_match: 1
                    }
                },
                aggs: { changedSources: { composite: composite } }
            })
            if (changedPage?.timed_out == true || (changedPage?._shards?.failed || 0) > 0) {
                exit 503, '变更统计未完成，请重试'
            }
            var buckets = changedPage?.aggregations?.changedSources?.buckets
            if (!(buckets instanceof List) || buckets.size() > 500) {
                exit 503, '变更统计结果无效'
            }
            if (buckets.size() == 0) {
                break
            }
            var changedIds = []
            for (bucket in buckets) {
                var id = text(bucket?.key?.sourceId)
                if (blank(id) || id.length() > 64) {
                    exit 503, '变更统计数据源标识无效'
                }
                if (!changedIds.contains(id)) {
                    changedIds.add(id)
                }
            }
            accumulate(facetRows(changedIds))
            var nextKey = changedPage.aggregations.changedSources.after_key
            if (nextKey == null || buckets.size() < 500) {
                break
            }
            if (afterKey != null && text(nextKey) == text(afterKey)) {
                exit 503, '变更统计游标无效'
            }
            afterKey = nextKey
        }
    }
    answer.unassignedTotal = answer.total - answer.assignedTotal
    return answer
}

var filters = [ { term: { assetType: 'db' } } ]
var keyword = text(body?.keyword).trim()
var dbType = text(body?.dbType).trim()
var registrationState = text(body?.registrationState).trim()
if (keyword.length() > 256 || dbType.length() > 64) {
    exit 400, '统计筛选条件不合法'
}

// 兼容旧版页面的 ES 候选筛选，归属和有效状态始终以主表为准。
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
if (orgIds.size() > 0) {
    filters.add({ terms: { orgId: orgIds } })
}

// 保持旧版候选窗口，按固定大小核验每批，避免在达梦等方言构造超长 IN。
var candidateLimit = 10000
var batchSize = 500
var result = tenantEs.index(configData().es_dataassets).search({
    from: 0,
    size: candidateLimit,
    timeout: '5s',
    _source: ['tid', 'appId', 'appName'],
    track_total_hits: true,
    query: {
        bool: {
            filter: filters,
            must_not: [
                { wildcard: { dbName: 'validation_*' } },
                { wildcard: { database: 'validation_*' } }
            ]
        }
    }
})

if (result?.timed_out == true || (result?._shards?.failed || 0) > 0) {
    exit 503, '数据源候选查询未完成，请重试'
}
var hits = result?.hits?.hits == null ? [] : result.hits.hits
if (!(hits instanceof List) || hits.size() > candidateLimit) {
    exit 503, '数据源候选结果无效'
}
var candidateIds = []
var seen = new HashSet()
var indexedRows = []
for (hit in hits) {
    var indexed = hit?._source
    var id = text(indexed?.tid || hit?._id)
    if (indexed != null && !blank(id) && id.length() <= 64 && seen.add(id)) {
        candidateIds.add(id)
        indexedRows.add({ id: id, appId: indexed.appId, appName: indexed.appName })
    }
}

var datasourceById = {}
if (scopeKind != 'ORG' || scopeOrgIds.size() > 0) {
    var offset = 0
    while (offset < candidateIds.size()) {
        var end = Math.min(offset + batchSize, candidateIds.size())
        var batchIds = candidateIds.subList(offset::int, end::int)
        var candidateQuery = db.camel().table('db_datasource_t')
            .columns('tid', 'org_id', 'app_id', 'db_name', 'db_type', 'asset_status')
            .where()
            .eq('tenant_id', tenantId)
            .eq('is_del', 0)
            .in('tid', batchIds)

        // ES 只决定候选集。主表再次核验归属、角色范围及请求组织。
        if (scopeKind == 'OWNER') {
            candidateQuery.eq('created_by', scopeUserId)
        }
        if (scopeKind == 'ORG') {
            candidateQuery.in('org_id', scopeOrgIds)
        }
        if (orgIds.size() > 0) {
            candidateQuery.in('org_id', orgIds)
        }
        if (!blank(dbType)) {
            candidateQuery.eq('db_type', dbType)
        }
        for (datasource in candidateQuery.select()) {
            datasourceById[text(datasource.tid)] = datasource
        }
        offset = end
    }
}

var validItems = []
for (indexed in indexedRows) {
    var datasource = datasourceById[indexed.id]
    if (datasource == null) {
        continue
    }
    var status = text(datasource.assetStatus)
    var statusKey = status == '2' ? 'completed' : status == '1' ? 'incomplete' : 'pending'
    if (blank(registrationState) || registrationState == statusKey) {
        // 旧版树沿用 ES 显示名。主表已改挂的来源不能沿用旧系统名称。
        if (text(datasource.appId) == text(indexed.appId)) {
            datasource.appName = indexed.appName
        }
        validItems.add(datasource)
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
    var assetStatusValue = text(item?.assetStatus) == '2'
        ? 'completed'
        : text(item?.assetStatus) == '1' ? 'incomplete' : 'pending'

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
