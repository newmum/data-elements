{
  "properties" : { },
  "id" : "datapd_dept_datasources_01",
  "script" : null,
  "groupId" : "datapd_database_group_01",
  "name" : "06.本部门数据源清单",
  "createTime" : null,
  "updateTime" : 1791109965040,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/departmentDataSources",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\"mode\":\"summary\"}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : "",
  "description" : "本部门数据源管理：cards 服务端分页，cardMetrics 全授权范围汇总，choices 分页读取登记候选；summary、tables、reportTables 及默认模式兼容已有调用方。所有读取使用当前租户与菜单角色范围。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
import "@post:/sym/user/me" as me
import tableSort
import tenantRuntime
import tenantEs
import tenantAccounts
import dataScope
import tableRead
import cn.hutool.json.JSONUtil
import java.util.ArrayList
import java.util.Date
import java.util.LinkedHashMap
import java.util.List
import '@/common/configData' as configData

/**
 * 本部门数据源清单。POST /dst/database/departmentDataSources
 * cards 分页读取卡片，cardMetrics 汇总全授权范围，choices 分页读取登记候选。
 * summary 保留历史概要。tables 按 datasourceId 返回已登记表分页。
 * reportTables 按 datasourceIds 返回已授权上报源的业务表和日志表分页。
 * 省略 mode 时返回兼容清单。按当前会话租户和菜单角色范围过滤，使用有效主表核验 ES 投影。
 * 接口只读，查询平台登记数据与索引，不连接外部来源库。
 */

var text = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || text(value).trim() == ''
var numberValue = (value) => value == null ? 0 : value::int

// ACTIVE_DATASOURCE_GUARD: 当前租户的有效主表记录是 ES 候选的准入条件。
var tenantId = text(tenantRuntime.id()).trim()
if (blank(tenantId)) {
    exit 401, '无法识别当前租户'
}

// 内部接口共用请求体。这里的 appId 是业务系统筛选，角色查询应使用登录会话的应用。
var filtersBody = body
var userInfo = null
try {
    body = {}
    userInfo = me()
} finally {
    body = filtersBody
}
var orgId = userInfo?.orgId
var scopeResource = 'MENU:2081000000000000002'
// 每次请求只解析一次服务端角色范围；在分页、统计及批量明细读取之前应用。
var scopeDecision = dataScope.resource(scopeResource)
var scopeKind = text(scopeDecision?.scope).trim().toUpperCase()
var scopeUserId = text(scopeDecision?.userId).trim()
var scopeOrgIds = scopeDecision?.organizationIds == null ? [] : scopeDecision.organizationIds
if (scopeKind == 'OWNER') {
    if (blank(scopeUserId)) {
        exit 403, '无法识别当前登记人员'
    }
} else if (scopeKind != 'ORG' && scopeKind != 'ALL') {
    exit 403, '数据源数据范围无效'
}

// JSON 只在租户、角色范围和数量上限通过后解读，响应只包含登记模式等展示信息。
var poolInfo = value => {
    if (blank(value)) {
        return {}
    }
    try {
        return JSONUtil.parseObj(text(value))
    } catch (error) {
        return {}
    }
}
var accessModeOf = pool => {
    var accessMode = text(pool.accessMode)
    return accessMode == '' ? text(pool.dataAccessMode) : accessMode
}
var visibleSources = (ids, readPool) => {
    if (scopeKind == 'ORG' && scopeOrgIds.size() == 0) {
        return []
    }
    var source = db.lower().table('db_datasource_t')
    if (readPool) {
        source.columns('tid', 'pool_cfg')
    } else {
        source.columns('tid')
    }
    var query = source.where().eq('tenant_id', tenantId).eq('is_del', 0).in('tid', ids)
    if (scopeKind == 'OWNER') {
        query.eq('created_by', scopeUserId)
    }
    if (scopeKind == 'ORG') {
        query.in('org_id', scopeOrgIds)
    }
    return query.select()
}
// 查询构建器只负责生成当前方言的语句，字段、口径和角色范围由本脚本指定。
var column = (alias, name) => tableRead.column(alias, name, 'TEXT')
var numberColumn = (alias, name) => tableRead.column(alias, name, 'NUMBER')
var timeColumn = (alias, name) => tableRead.column(alias, name, 'TIME')
var lobColumn = (alias, name) => tableRead.column(alias, name, 'LOB')
var zero = field => tableRead.coalesce([field, 0])
var active = (alias, field) => tableRead.equal(zero(numberColumn(alias, field)), 0)
var applyScope = (query, alias, kind, userId, organizations) => {
    if (kind == 'OWNER') {
        query.where(tableRead.equal(column(alias, 'created_by'), userId))
    }
    if (kind == 'ORG') {
        query.where(tableRead.in(column(alias, 'org_id'), organizations))
    }
}

var requestMode = text(body?.mode).trim()
if (!blank(requestMode) && !['cards', 'cardMetrics', 'choices', 'summary', 'tables', 'reportTables'].contains(requestMode)) {
    exit 400, '不支持的清单查询模式'
}
if (['cards', 'cardMetrics', 'choices'].contains(requestMode)) {
    var metricsOnly = requestMode == 'cardMetrics'
    var choicesOnly = requestMode == 'choices'
    var pageNum = Math.max(1, body?.pageNum == null ? 1 : body.pageNum::int(1))
    var pageSize = Math.min(100, Math.max(1, body?.pageSize == null ? 24 : body.pageSize::int(24)))
    var keyword = metricsOnly ? '' : text(body?.keyword).trim()
    var filter = metricsOnly || blank(body?.filter) ? 'all' : text(body.filter)
    var appId = metricsOnly ? '' : text(body?.appId).trim()
    var typeKey = metricsOnly ? '' : text(body?.typeKey).trim()
    var orgInput = metricsOnly || body?.orgIds == null ? [] : body.orgIds
    if (keyword.length() > 256 || appId.length() > 64 || typeKey.length() > 64
            || !['all', 'unannotated', 'incomplete', 'completed', 'changed'].contains(filter)
            || !(orgInput instanceof List) || orgInput.size() > 1000) {
        exit 400, '数据源筛选条件不合法'
    }
    if (choicesOnly && filter == 'changed') {
        exit 400, '登记候选不支持按变更筛选'
    }
    var orgIds = []
    for (rawId in orgInput) {
        var id = text(rawId).trim()
        if (blank(id) || id.length() > 64) {
            exit 400, '组织标识不合法'
        }
        if (!orgIds.contains(id)) {
            orgIds.add(id)
        }
    }

    var cardId = column('d', 'tid')
    var cardName = column('d', 'db_name')
    var cardOrg = column('d', 'org_id')
    var cardApp = column('d', 'app_id')
    var cardAppName = column('a', 'app_name')
    var cardType = column('d', 'db_type')
    var cardAssetStatus = numberColumn('d', 'asset_status')
    var cardShowConnect = numberColumn('d', 'show_connect')
    var cardPool = lobColumn('d', 'pool_cfg')
    var cardAccessMode = tableRead.coalesce([
        tableRead.blankAsNull(tableRead.jsonText(cardPool, 'accessMode')),
        tableRead.blankAsNull(tableRead.jsonText(cardPool, 'dataAccessMode'))
    ])
    var cardUpdated = tableRead.coalesce([timeColumn('d', 'updated_time'), timeColumn('d', 'created_time')])
    var cardConnection = column('d', 'connection_status')
    var exploredCount = zero(numberColumn('d', 'table_num'))
    var actualCount = zero(numberColumn('ts', 'tableNum'))
    var annotatedCount = zero(numberColumn('ts', 'annotatedTableNum'))
    var registeredCount = zero(numberColumn('ts', 'registeredTableNum'))
    var displayTableCount = tableRead.greatest([exploredCount, actualCount])

    // 数据源主表决定可见性。请求条件和 ES 候选均只能缩小这一范围。
    var cardSource = () => {
        var query = tableRead.from('db_datasource_t', 'd', tenantId)
        query.leftJoin('sym_application_t', 'a', 'tid', 'app_id', [active('a', 'is_del')])
        query.where(active('d', 'is_del'))
        query.where(tableRead.not(tableRead.like(tableRead.coalesce([cardName, ' ']), 'validation_%')))
        applyScope(query, 'd', scopeKind, scopeUserId, scopeOrgIds)
        if (orgIds.size() > 0) {
            query.where(tableRead.in(cardOrg, orgIds))
        }
        if (!blank(appId)) {
            query.where(tableRead.equal(cardApp, appId))
        }
        if (!blank(keyword)) {
            query.where(tableRead.any([
                cardName, cardAppName, cardType, tableRead.jsonText(cardPool, 'database'),
                tableRead.jsonText(cardPool, 'schema')
            ].map(field => tableRead.contains(field, keyword))))
        }
        if (filter == 'incomplete') {
            query.where(tableRead.equal(cardAssetStatus, 1))
        }
        if (filter == 'completed') {
            query.where(tableRead.equal(cardAssetStatus, 2))
        }
        return query
    }

    var tableSourceId = column('t', 'datasource_id')
    var tableStatus = zero(numberColumn('t', 'asset_status'))
    var businessType = tableRead.lower(column('t', 'business_type'))
    var registered = tableRead.equal(tableStatus, 2)
    var classified = tableRead.greaterThan(tableStatus, 0)
    var business = tableRead.in(businessType, ['business', '业务表'])
    var dictionary = tableRead.in(businessType, ['dict', 'dictionary', '字典表'])
    var logTable = tableRead.in(businessType, ['log', '日志表'])
    var core = tableRead.any([business, dictionary, logTable])
    var baseStats = {
        datasourceId: tableSourceId, tableNum: tableRead.count(),
        annotatedTableNum: tableRead.countIf(tableRead.equal(numberColumn('t', 'annotated'), 1)),
        registeredTableNum: tableRead.countIf(registered)
    }
    var cardStats = new LinkedHashMap(baseStats)
    cardStats.businessTableNum = tableRead.countIf(tableRead.all([classified, business]))
    cardStats.dictionaryTableNum = tableRead.countIf(tableRead.all([classified, dictionary]))
    cardStats.logTableNum = tableRead.countIf(tableRead.all([classified, logTable]))
    cardStats.coreTableNum = tableRead.countIf(core)
    cardStats.registeredCoreTableNum = tableRead.countIf(tableRead.all([core, registered]))
    cardStats.deferredTableNum = tableRead.countIf(tableRead.in(businessType, ['暂不处理', '暂不登记', '不确定']))
    var statSource = ids => {
        var query = tableRead.from('db_table_t', 't', tenantId)
        query.where(active('t', 'is_del'))
        if (ids != null) {
            query.where(tableRead.in(tableSourceId, ids))
        }
        return query
    }

    // 变更来源沿用树分面的 ES 合成桶；只把当前租户的有效主表授权结果传给调用者。
    var changeStates = ['added', 'modified', 'deleted']
    var changeFilters = {}
    for (status in changeStates) {
        changeFilters[status] = { bool: { should: [
            { term: { changeStatus: status } }, { term: { change_status: status } }
        ], minimum_should_match: 1 } }
    }
    var zeroChanges = { addedTableNum: 0, modifiedTableNum: 0, deletedTableNum: 0, changedTableNum: 0 }
    var changeCounts = bucket => {
        var counts = bucket?.changes?.buckets
        if (counts?.added?.doc_count == null || counts?.modified?.doc_count == null || counts?.deleted?.doc_count == null) {
            throw new IllegalStateException('变更统计分类不完整')
        }
        var added = counts.added.doc_count::long
        var modified = counts.modified.doc_count::long
        var deleted = counts.deleted.doc_count::long
        return { addedTableNum: added, modifiedTableNum: modified, deletedTableNum: deleted,
            changedTableNum: added + modified + deleted }
    }
    var index = () => tenantEs.index(configData().es_dataassets)
    var validSearch = result => result != null && result?.timed_out != true && (result?._shards?.failed || 0) == 0
    var scanChanges = consume => {
        var sourceKeyScript = "if (doc.containsKey('datasourceId') && doc['datasourceId'].size()!=0) "
            + "return doc['datasourceId'].value; "
            + "if (doc.containsKey('dbId') && doc['dbId'].size()!=0) return doc['dbId'].value; return null;"
        var started = new Date()
        var deadline = started.getTime() + 20000
        var assetIndex = index()
        var afterKey = null
        while (true) {
            var now = new Date()
            if (now.getTime() > deadline) {
                throw new IllegalStateException('变更统计超过查询时限')
            }
            var composite = { size: 500,
                sources: [{ sourceId: { terms: { script: { lang: 'painless', source: sourceKeyScript } } } }] }
            if (afterKey != null) {
                composite.after = afterKey
            }
            var result = assetIndex.search({
                size: 0, timeout: '5s',
                query: { bool: { filter: [{ term: { assetType: 'table' } }],
                    should: [{ terms: { changeStatus: changeStates } }, { terms: { change_status: changeStates } }],
                    minimum_should_match: 1 } },
                aggs: { changedSources: { composite: composite, aggs: { changes: { filters: { filters: changeFilters } } } } }
            })
            var grouping = result?.aggregations?.changedSources
            var buckets = grouping?.buckets
            if (!validSearch(result) || !(buckets instanceof List) || buckets.size() > 500) {
                throw new IllegalStateException('变更统计结果未完整读取')
            }
            if (buckets.size() == 0) {
                break
            }
            var changesById = {}
            for (bucket in buckets) {
                var id = text(bucket?.key?.sourceId)
                if (blank(id) || id.length() > 64) {
                    throw new IllegalStateException('变更统计结果无效')
                }
                changesById[id] = changeCounts(bucket)
            }
            consume(new ArrayList(changesById.keySet()), changesById)
            var nextKey = grouping.after_key
            if (nextKey == null || buckets.size() < 500) {
                break
            }
            if (afterKey != null && text(nextKey) == text(afterKey)) {
                throw new IllegalStateException('变更统计游标未推进')
            }
            afterKey = nextKey
        }
    }

    if (metricsOnly) {
        var query = cardSource()
        query.leftJoinGrouped(statSource(null), 'ts', 'datasourceId', 'tid', baseStats, [tableSourceId])
        var applicationKey = tableRead.coalesce([tableRead.blankAsNull(cardApp), tableRead.blankAsNull(cardAppName)])
        var hasApplication = tableRead.not(tableRead.isNull(applicationKey))
        var completed = tableRead.equal(cardAssetStatus, 2)
        var incomplete = tableRead.equal(cardAssetStatus, 1)
        var connected = tableRead.all([
            tableRead.not(tableRead.equal(tableRead.coalesce([cardShowConnect, 1]), 0)),
            tableRead.in(tableRead.coalesce([tableRead.lower(cardConnection), ' ']),
                ['success', 'connected', 'ok', 'true', '1'])
        ])
        var notProxy = tableRead.not(tableRead.equal(tableRead.coalesce([tableRead.lower(cardAccessMode), ' ']), 'proxy'))
        var metrics = query.grouped({
            appSystemTotal: tableRead.countDistinct(applicationKey),
            appDatasourceTotal: tableRead.countIf(hasApplication),
            datasourceTotal: tableRead.count(),
            completedTotal: tableRead.countIf(completed),
            incompleteDatasourceTotal: tableRead.countIf(incomplete),
            unreachableDatasourceTotal: tableRead.countIf(tableRead.all([notProxy, tableRead.not(connected)])),
            tableTotal: zero(tableRead.sum(displayTableCount)),
            annotatedTableTotal: zero(tableRead.sum(annotatedCount)),
            registeredTableTotal: zero(tableRead.sum(registeredCount)),
            persistedTableTotal: zero(tableRead.sum(actualCount))
        }, [])[0]
        metrics.unboundAppDatasourceTotal = metrics.datasourceTotal - metrics.appDatasourceTotal
        metrics.pendingDatasourceTotal = metrics.datasourceTotal - metrics.completedTotal
        metrics.draftDatasourceTotal = metrics.pendingDatasourceTotal - metrics.incompleteDatasourceTotal
        metrics.unannotatedTotal = metrics.tableTotal - metrics.annotatedTableTotal
        metrics.problemTableTotal = metrics.persistedTableTotal - metrics.registeredTableTotal
        metrics.remove('persistedTableTotal')
        var changesAvailable = true
        var totals = { added: 0, modified: 0, deleted: 0 }
        try {
            if (metrics.datasourceTotal > 0) {
                scanChanges((ids, changes) => {
                    var authorized = cardSource()
                    authorized.where(tableRead.in(cardId, ids))
                    for (row in authorized.rows({tid: cardId}, cardId, 'asc', ids.size())) {
                        var counts = changes[text(row.tid)]
                        totals.added += counts.addedTableNum
                        totals.modified += counts.modifiedTableNum
                        totals.deleted += counts.deletedTableNum
                    }
                })
            }
        } catch (changeError) {
            changesAvailable = false
        }
        metrics.addedTableTotal = changesAvailable ? totals.added : null
        metrics.modifiedTableTotal = changesAvailable ? totals.modified : null
        metrics.deletedTableTotal = changesAvailable ? totals.deleted : null
        metrics.changedTotal = changesAvailable ? totals.added + totals.modified + totals.deleted : null
        return {metrics: metrics, dataScope: scopeKind, changesAvailable: changesAvailable}
    }

    // 先查有限类型桶并归一化原值，再把所选桶作为参数条件交给数据库分页。
    var typeCondition = null
    if (!blank(typeKey)) {
        var groups = cardSource().grouped(
            { dbType: cardType, accessMode: cardAccessMode, showConnect: cardShowConnect },
            [cardType, cardAccessMode, cardShowConnect]
        )
        var aliases = {pg:'postgresql', postgres:'postgresql', mssql:'sqlserver', dm:'dameng',
            kingbase:'kingbase8', kingbasees:'kingbase8', es:'elasticsearch', elastic:'elasticsearch'}
        var conditions = []
        for (group in groups) {
            var key = text(group.dbType).trim().toLowerCase().replaceAll('[\\s_-]', '')
            key = aliases[key] || (blank(key) ? 'other' : key)
            var mode = text(group.accessMode).trim().toLowerCase()
            if (mode == 'proxy') {
                key = 'mode:proxy'
            } else if (['upload', 'report'].contains(mode) || (key == 'api' && text(group.showConnect) == '0')) {
                key = 'mode:upload'
            } else if (['receive', 'push'].contains(mode)) {
                key = 'mode:receive'
            }
            if (key == typeKey) {
                conditions.add(tableRead.all([tableRead.equal(cardType, group.dbType),
                    tableRead.equal(cardAccessMode, group.accessMode), tableRead.equal(cardShowConnect, group.showConnect)]))
            }
        }
        if (conditions.size() > 256) {
            exit 503, '数据源类型别名过多，请缩小筛选范围'
        }
        typeCondition = tableRead.any(conditions)
    }

    var filteredSource = () => {
        var query = cardSource()
        if (typeCondition != null) {
            query.where(typeCondition)
        }
        if (filter == 'unannotated') {
            query.leftJoinGrouped(statSource(null), 'ts', 'datasourceId', 'tid', cardStats, [tableSourceId])
            query.where(tableRead.greaterThan(displayTableCount, annotatedCount))
        }
        return query
    }
    var projection = {
        tid: cardId, dbName: cardName, dbType: cardType, databaseType: column('d', 'database_type'),
        orgId: cardOrg, orgName: column('o', 'name'), appId: cardApp, appName: cardAppName,
        assetStatus: cardAssetStatus, accessMode: cardAccessMode, showConnect: cardShowConnect,
        sortKey: cardUpdated
    }
    if (!choicesOnly) {
        projection.createdBy = column('d', 'created_by')
        projection.description = lobColumn('d', 'remark')
        projection.orgPath = column('o', 'org_path')
        projection.poolCfg = cardPool
        projection.exploredTableNum = exploredCount
        projection.connectionStatus = cardConnection
        projection.updatedTime = timeColumn('d', 'updated_time')
        projection.createdTime = timeColumn('d', 'created_time')
        projection.storageDomain = column('d', 'storage_domain')
        projection.nodeId = column('d', 'node_id')
        if (filter == 'unannotated') {
            for (key in cardStats.keySet()) {
                if (key != 'datasourceId') {
                    projection[key + 'Stat'] = zero(numberColumn('ts', key))
                }
            }
        }
    }
    var rowSource = () => {
        var query = filteredSource()
        query.leftJoin('rm_org_t', 'o', 'id', 'org_id', [active('o', 'deleted')])
        return query
    }

    var rows = []
    var total = 0
    var nextCursor = null
    var changesById = {}
    var changesAvailable = true
    if (filter == 'changed') {
        var cursor = body?.cursor
        var cursorTime = text(cursor?.sortKey)
        var cursorId = text(cursor?.tid)
        if ((pageNum > 1 && cursor == null)
                || (cursor != null && (blank(cursorId) || cursorId.length() > 64 || cursorTime.length() > 64))) {
            exit 400, '变更列表游标无效，请从第一页重新查询'
        }
        // ES 以来源 ID 排序。每批仅回表核验窄投影，并保留主表时间顺序中的当前页。
        var ordered = { rows: [], total: 0 }
        var compareCards = (left, right) => {
            var leftTime = text(left.sortKey)
            var rightTime = text(right.sortKey)
            if (leftTime != rightTime) {
                return leftTime > rightTime ? -1 : 1
            }
            return text(left.tid).compareTo(text(right.tid))
        }
        try {
            scanChanges((ids, batchChanges) => {
                var candidates = filteredSource()
                candidates.where(tableRead.in(cardId, ids))
                var batch = candidates.rows({tid: cardId, sortKey: cardUpdated}, cardUpdated, 'desc', ids.size())
                ordered.total += batch.size()
                for (row in batch) {
                    if (cursor == null || compareCards(row, cursor) > 0) {
                        row.changes = batchChanges[text(row.tid)]
                        ordered.rows.add(row)
                    }
                }
                ordered.rows = new ArrayList(ordered.rows.sort(compareCards).subList(0, Math.min(pageSize + 1, ordered.rows.size())))
            })
        } catch (changeError) {
            exit 503, '变更列表未完整读取，请重试'
        }
        total = ordered.total
        if (ordered.rows.size() > pageSize) {
            var last = ordered.rows[pageSize - 1]
            nextCursor = { sortKey: text(last.sortKey), tid: text(last.tid) }
            ordered.rows = new ArrayList(ordered.rows.subList(0, pageSize))
        }
        if (ordered.rows.size() > 0) {
            var ids = ordered.rows.map(row => text(row.tid))
            var query = rowSource()
            query.where(tableRead.in(cardId, ids))
            var byId = {}
            for (row in query.rows(projection, cardUpdated, 'desc', ids.size())) {
                byId[text(row.tid)] = row
            }
            for (entry in ordered.rows) {
                var id = text(entry.tid)
                if (byId[id] != null) {
                    rows.add(byId[id])
                    changesById[id] = entry.changes
                }
            }
        }
    } else {
        var page = rowSource().pageClamped(projection, cardUpdated, 'desc', 'tid', 'asc', pageNum, pageSize)
        rows = page.rows
        total = page.total
        pageNum = page.pageNum
    }

    var response = { list: rows, total: total, pageNum: pageNum, pageSize: pageSize,
        dataScope: scopeKind, orgId: orgId, orgName: userInfo?.orgName, changesAvailable: changesAvailable,
        nextCursor: nextCursor }
    if (choicesOnly || rows.size() == 0) {
        for (row in rows) {
            row.id = row.tid
            row.orgName = row.orgName || userInfo?.orgName
            row.remove('sortKey')
        }
        return response
    }

    // 只为当前页的已授权来源读取计数和作者，不向前端发送连接配置。
    var pageIds = rows.map(row => text(row.tid))
    var bySource = {}
    if (filter != 'unannotated') {
        for (stat in statSource(pageIds).grouped(cardStats, [tableSourceId])) {
            bySource[text(stat.datasourceId)] = stat
        }
    }
    var creatorIds = rows.map(row => text(row.createdBy)).filter(id => !blank(id)).distinct()
    var creatorNames = creatorIds.size() == 0 ? {} : tenantAccounts.names(creatorIds)
    var proxyIds = rows.filter(row => text(row.accessMode).trim().toLowerCase() == 'proxy').map(row => text(row.tid))
    var serviceCounts = {}
    if (proxyIds.size() > 0) {
        var serviceScope = dataScope.dataSource()
        var serviceUser = text(serviceScope?.userId).trim()
        if (!['ALL', 'ORG', 'OWNER'].contains(serviceScope?.scope)
                || (serviceScope.scope == 'OWNER' && blank(serviceUser))) {
            exit 403, '数据服务范围无效'
        }
        var services = tableRead.from('api_info_t', 's', tenantId)
        var serviceSource = column('s', 'datasource_id')
        services.where(tableRead.equal(numberColumn('s', 'is_del'), 0))
        services.where(tableRead.in(serviceSource, proxyIds))
        applyScope(services, 's', serviceScope.scope, serviceUser, serviceScope?.organizationIds || [])
        for (stat in services.grouped({datasourceId: serviceSource, serviceCount: tableRead.count()}, [serviceSource])) {
            serviceCounts[text(stat.datasourceId)] = numberValue(stat.serviceCount)
        }
    }
    if (filter != 'changed') {
        try {
            var sourceFilters = {}
            for (id in pageIds) {
                sourceFilters[id] = { bool: { should: [{ term: { datasourceId: id } }, { term: { dbId: id } }],
                    minimum_should_match: 1 } }
            }
            var result = index().search({ size: 0, timeout: '5s', query: { term: { assetType: 'table' } },
                aggs: { bySource: { filters: { filters: sourceFilters },
                    aggs: { changes: { filters: { filters: changeFilters } } } } } })
            var buckets = result?.aggregations?.bySource?.buckets
            if (!validSearch(result) || buckets == null) {
                throw new IllegalStateException('卡片变更统计未完整读取')
            }
            for (id in pageIds) {
                changesById[id] = changeCounts(buckets[id])
            }
        } catch (changeError) {
            response.changesAvailable = false
        }
    }
    for (row in rows) {
        var id = text(row.tid)
        row.id = row.tid
        row.remove('sortKey')
        var pool = poolInfo(row.remove('poolCfg'))
        row.database = text(pool.database)
        row.schema = text(pool.schema)
        row.orgName = row.orgName || userInfo?.orgName
        row.createdByName = creatorNames[text(row.createdBy)] || ''
        row.pushDataType = blank(pool.pushDataType) ? 'STRUCTURED' : text(pool.pushDataType).toUpperCase()
        var files = pool.reportRegistration?.files
        row.reportFileCount = files instanceof List ? files.size() : 0
        row.serviceCount = numberValue(serviceCounts[id])
        var stats = bySource[id] || {}
        for (key in cardStats.keySet()) {
            if (key != 'datasourceId') {
                row[key] = numberValue(filter == 'unannotated' ? row.remove(key + 'Stat') : stats[key])
            }
        }
        var storedTables = row.tableNum
        row.tableNum = Math.max(numberValue(row.exploredTableNum), storedTables)
        row.unannotatedTableNum = Math.max(0, row.tableNum - row.annotatedTableNum)
        row.unregisteredTableNum = Math.max(0, row.tableNum - row.registeredTableNum)
        row.problemTableNum = storedTables - row.registeredTableNum
        var changes = changesById[id] || zeroChanges
        for (key in zeroChanges.keySet()) {
            row[key] = response.changesAvailable ? changes[key] : null
        }
    }
    return response
}
if (requestMode == 'reportTables') {
    // 一页报告的表跨数据源批量读取；前端不得对每个数据源再发一次表查询。
    var submittedIds = body?.datasourceIds
    if (!(submittedIds instanceof List) || submittedIds.size() == 0 || submittedIds.size() > 200) {
        exit 400, '数据源标识必须是 1 至 200 项的数组'
    }
    var requestedIds = []
    for (rawId in submittedIds) {
        var sourceId = text(rawId).trim()
        if (blank(sourceId) || sourceId.length() > 32) {
            exit 400, '数据源标识不合法'
        }
        if (!requestedIds.contains(sourceId)) {
            requestedIds.add(sourceId)
        }
    }
    // 按当前租户批量读取主表，任一 ID 不存在时整批失败。
    var allowedSources = visibleSources(requestedIds, true)
    if (allowedSources == null || allowedSources.size() != requestedIds.size()) {
        exit 403, '数据源不存在或无权访问'
    }
    var allowedIds = []
    for (source in allowedSources) {
        var accessMode = accessModeOf(poolInfo(source.pool_cfg)).trim().toLowerCase()
        if (accessMode != 'upload') {
            exit 403, '数据源不是数据上报方式'
        }
        allowedIds.add(text(source.tid))
    }
    var reportPageNum = body?.pageNum == null ? 1 : body.pageNum::int
    var reportPageSize = body?.pageSize == null ? 100 : body.pageSize::int
    if (reportPageNum < 1) {
        reportPageNum = 1
    }
    if (reportPageSize < 1) {
        reportPageSize = 100
    }
    if (reportPageSize > 100) {
        reportPageSize = 100
    }
    // 与单源元数据表接口保持相同去重优先级：同名先取已标注，再取最近更新。
    // 过滤业务类型置于去重之后，不能让较旧的同名业务表重新进入结果。
    var reportSource = tableRead.from('db_table_t', 't', tenantId)
    reportSource.where(tableRead.equal(numberColumn('t', 'is_del'), 0))
    reportSource.where(tableRead.in(column('t', 'datasource_id'), allowedIds))
    var ranked = reportSource.ranked('r', {
        tid: column('t', 'tid'), datasource_id: column('t', 'datasource_id'),
        table_name: column('t', 'table_name'), table_name_cn: column('t', 'table_name_cn'),
        table_comment: lobColumn('t', 'table_comment'), table_type: column('t', 'table_type'),
        business_type: column('t', 'business_type'), annotated: numberColumn('t', 'annotated'),
        field_count: numberColumn('t', 'field_count'), record_count: numberColumn('t', 'record_count'),
        timestamp_field: column('t', 'timestamp_field'), updated_time: timeColumn('t', 'updated_time')
    }, [column('t', 'datasource_id'), tableRead.lower(column('t', 'table_name'))],
       [zero(numberColumn('t', 'annotated')), timeColumn('t', 'updated_time'), column('t', 'tid')],
       ['desc', 'desc', 'desc'])
    ranked.where(tableRead.equal(numberColumn('r', 'annotated'), 1))
    ranked.where(tableRead.in(column('r', 'business_type'), ['业务表', '日志表']))
    var reportPage = ranked.page({
        tid: column('r', 'tid'), datasourceId: column('r', 'datasource_id'),
        tableName: column('r', 'table_name'), tableNameCn: column('r', 'table_name_cn'),
        tableComment: lobColumn('r', 'table_comment'), tableType: column('r', 'table_type'),
        businessType: column('r', 'business_type'), fieldCount: numberColumn('r', 'field_count'),
        recordCount: numberColumn('r', 'record_count'), timestampField: column('r', 'timestamp_field'),
        updatedTime: timeColumn('r', 'updated_time')
    }, timeColumn('r', 'updated_time'), 'desc', 'tid', 'desc', reportPageNum, reportPageSize)
    var reportRows = []
    for (row in reportPage.rows) {
        reportRows.add({
            tid: row.tid, datasourceId: row.datasourceId,
            tableName: row.tableName, tableNameCn: row.tableNameCn,
            tableComment: row.tableComment, tableType: row.tableType,
            businessType: row.businessType, fieldCount: numberValue(row.fieldCount),
            recordCount: numberValue(row.recordCount), timestampField: row.timestampField,
            businessTimeField: '', updatedTime: row.updatedTime
        })
    }
    return { pageNum: reportPageNum, pageSize: reportPageSize,
        total: numberValue(reportPage.total), list: reportRows }
}
if (requestMode == 'tables') {
    var datasourceId = text(body?.datasourceId).trim()
    if (blank(datasourceId)) {
        exit 400, '缺少数据源标识'
    }
    var sourceRows = visibleSources([datasourceId], false)
    if (sourceRows == null || sourceRows.size() == 0) {
        exit 404, '数据源不存在或无权访问'
    }
    var pageNum = body?.pageNum == null ? 1 : body.pageNum::int
    var pageSize = body?.pageSize == null ? 10 : body.pageSize::int
    if (pageNum < 1) {
        pageNum = 1
    }
    if (pageSize < 1) {
        pageSize = 10
    }
    if (pageSize > 100) {
        pageSize = 100
    }
    // 仅返回当前页的展示字段，不读取连接信息、字段快照或治理 JSON。
    var tables = tableRead.from('db_table_t', 't', tenantId)
    tables.where(tableRead.equal(column('t', 'datasource_id'), datasourceId))
    tables.where(active('t', 'is_del'))
    tables.where(tableRead.equal(numberColumn('t', 'asset_status'), 2))
    var businessType = tableRead.lower(column('t', 'business_type'))
    tables.where(tableRead.any([tableRead.isNull(businessType),
        tableRead.not(tableRead.in(businessType, ['暂不处理', '暂不登记', '不确定', 'deferred']))]))
    var sorts = {
        tableName: tableRead.coalesce([tableRead.blankAsNull(column('t', 'table_name_cn')),
            tableRead.blankAsNull(tableRead.boundedText(lobColumn('t', 'table_comment'))), column('t', 'table_name')]),
        fieldCount: numberColumn('t', 'field_count'), createdTime: timeColumn('t', 'created_time'),
        updatedTime: timeColumn('t', 'updated_time')
    }
    var sortField = tableSort.field(body, {tableName: 'tableName', fieldCount: 'fieldCount',
        createdTime: 'createdTime', updatedTime: 'updatedTime'}, 'createdTime')
    var page = tables.pageClamped({
        tid: column('t', 'tid'), datasourceid: column('t', 'datasource_id'),
        tablename: column('t', 'table_name'), tablenameen: column('t', 'table_name_en'),
        tablenamecn: column('t', 'table_name_cn'), tablecomment: lobColumn('t', 'table_comment'),
        tabletype: column('t', 'table_type'), businesstype: column('t', 'business_type'),
        fieldcount: numberColumn('t', 'field_count'), assetstatus: numberColumn('t', 'asset_status'),
        createdtime: timeColumn('t', 'created_time'), updatedtime: timeColumn('t', 'updated_time')
    }, sorts[sortField], tableSort.direction(body), 'tid', 'asc', pageNum, pageSize)
    pageNum = page.pageNum
    var total = page.total
    var pageList = []
    for (row in page.rows) {
        pageList.add({ tid: row.tid, id: row.tid, datasourceId: row.datasourceid,
            dbId: row.datasourceid, tableName: row.tablename || row.tablenameen,
            tableNameCn: row.tablenamecn || row.tablecomment, tableComment: row.tablecomment,
            tableType: row.tabletype, businessType: row.businesstype, fieldCount: row.fieldcount,
            assetStatus: row.assetstatus, createdTime: row.createdtime, updatedTime: row.updatedtime })
    }
    // 只为当前页补齐显式变更标签，禁止下载数据源下所有表文档。
    var pageIds = []
    for (item in pageList) {
        pageIds.add(text(item.tid))
    }
    if (pageIds.size() > 0) {
        var pageChanges = tenantEs.index(configData().es_dataassets).search({
            size: pageSize, _source: ['tid', 'changeStatus', 'change_status'],
            query: { bool: { must: [
                { term: { assetType: 'table' } }, { ids: { values: pageIds } }
            ] } }
        })
        var changeById = {}
        for (hit in (pageChanges?.hits?.hits == null ? [] : pageChanges.hits.hits)) {
            changeById[text(hit?._source?.tid || hit._id)] = text(hit?._source?.changeStatus || hit?._source?.change_status).toLowerCase()
        }
        for (item in pageList) {
            item.changeStatus = changeById[text(item.tid)] || 'unchanged'
        }
    }
    return { datasourceId: datasourceId, pageNum: pageNum, pageSize: pageSize, total: total, list: pageList }
}
var summaryOnly = requestMode == 'summary'

// 页面关键字只能缩小结果；角色范围始终由服务端会话确定。
var keywordText = text(body?.keyword).trim()
var datasourceMust = [
    { term: { assetType: 'db' } }
]
if (!blank(keywordText)) {
    // 关键字也在 ES 过滤，前端不能借搜索绕过部门权限。
    datasourceMust.add({
        bool: {
            should: [
                { wildcard: { dbName: '*' + keywordText + '*' } },
                { wildcard: { appName: '*' + keywordText + '*' } },
                { wildcard: { database: '*' + keywordText + '*' } },
                { wildcard: { dbType: '*' + keywordText + '*' } }
            ],
            minimum_should_match: 1
        }
    })
}

// ES 仅作为候选投影；全部 / 本部门 / 本人范围在主表过滤阶段统一裁剪。
var datasourceSearch = null
try {
    datasourceSearch = tenantEs.index(configData().es_dataassets).search({
        _source: summaryOnly ? ['tid', 'id', 'dbName', 'appId', 'appName', 'dbType', 'databaseType',
            'database', 'schema', 'host', 'port', 'accessMode', 'dataAccessMode', 'orgId', 'orgName',
            'tableNum', 'assetStatus', 'showConnect', 'connectionStatus', 'description',
            'createdTime', 'updatedTime', 'createdBy', 'storageDomain', 'nodeId'] : true,
        size: 1000,
        track_total_hits: true,
        query: {
            bool: {
                must: datasourceMust,
                must_not: [
                    { wildcard: { dbName: 'validation_*' } },
                    { wildcard: { database: 'validation_*' } }
                ]
            }
        },
        sort: [ { updatedTime: { order: 'desc', unmapped_type: 'keyword' } } ]
    })
} catch (datasourceSearchError) {
    // 候选投影不可用时仍从当前租户的主表读取数据源。
}

var result = []
var resultById = {}
var datasourceIds = []
for (hit in (datasourceSearch?.hits?.hits == null ? [] : datasourceSearch.hits.hits)) {
    if (hit?._source == null) {
        continue
    }
    var item = hit._source
    item.tid = item.tid || hit._id
    item.id = item.id || item.tid
    item.orgName = userInfo?.orgName
    // 数据源 ES 投影里可能已经有最近一次探查得到的物理总表数。
    // 这个总数和 db_table_t 已保存行数不是同一个口径，不能在后续统计时被清零丢掉。
    item.exploredTableNum = numberValue(item.tableNum || item.table_num || item.tableCount || item.table_count)
    item.tableNum = 0
    item.annotatedTableNum = 0
    item.unannotatedTableNum = 0
    item.registeredTableNum = 0
    item.unregisteredTableNum = 0
    item.businessTableNum = 0
    item.dictionaryTableNum = 0
    item.logTableNum = 0
    item.coreTableNum = 0
    item.registeredCoreTableNum = 0
    item.deferredTableNum = 0
    item.issueTableNum = 0
    item.problemTableNum = 0
    item.changedTableNum = 0
    item.addedTableNum = 0
    item.modifiedTableNum = 0
    item.deletedTableNum = 0
    item.tables = []
    datasourceIds.add(item.tid)
    resultById[text(item.tid)] = item
    result.add(item)
}

// ES 数据源主投影可能存在刷新延迟。按当前租户批量回表补齐主卡片，
// 并按当前租户过滤，避免前端参数切换到其他行业数据。
var sources = tableRead.from('db_datasource_t', 'd', tenantId)
sources.leftJoin('sym_application_t', 'a', 'tid', 'app_id', [active('a', 'is_del')])
sources.leftJoin('rm_org_t', 'o', 'id', 'org_id', [active('o', 'deleted')])
sources.where(active('d', 'is_del'))
// 排除验证数据源。空名称保留，与旧接口的回退口径一致。
var sourceName = column('d', 'db_name')
sources.where(tableRead.any([tableRead.isNull(sourceName),
    tableRead.not(tableRead.like(sourceName, 'validation_%'))]))
applyScope(sources, 'd', scopeKind, scopeUserId, scopeOrgIds)
if (!blank(keywordText)) {
    sources.where(tableRead.any([tableRead.contains(sourceName, keywordText),
        tableRead.contains(column('a', 'app_name'), keywordText),
        tableRead.contains(column('d', 'db_type'), keywordText)]))
}
var activeDatasourceById = {}
var storedTotalsById = {}
var datasourceRows = sources.rows({
    tid: column('d', 'tid'), dbname: sourceName, description: lobColumn('d', 'remark'),
    appid: column('d', 'app_id'), appname: column('a', 'app_name'),
    dbtype: column('d', 'db_type'), databasetype: column('d', 'database_type'), poolcfg: lobColumn('d', 'pool_cfg'),
    orgid: column('d', 'org_id'), createdby: column('d', 'created_by'),
    orgname: column('o', 'name'), orgpath: column('o', 'org_path'),
    exploredtablenum: numberColumn('d', 'table_num'), assetstatus: numberColumn('d', 'asset_status'),
    showconnect: numberColumn('d', 'show_connect'), connectionstatus: column('d', 'connection_status'),
    updatedtime: timeColumn('d', 'updated_time'), createdtime: timeColumn('d', 'created_time'),
    storagedomain: column('d', 'storage_domain'), nodeid: column('d', 'node_id')
}, timeColumn('d', 'updated_time'), 'desc', 1000)
// 在读取窗口之前应用菜单范围。ES 投影只允许补充有效卡片的展示信息。
// 创建人主数据位于控制库；批量解析可见数据源的显示名称，避免逐卡片查询。
var creatorIds = []
for (row in datasourceRows) {
    var creatorId = text(row.createdby)
    if (!blank(creatorId) && !creatorIds.contains(creatorId)) {
        creatorIds.add(creatorId)
    }
}
var creatorNames = tenantAccounts.names(creatorIds)
for (row in datasourceRows) {
    var rowTid = text(row.tid)
    if (blank(rowTid)) {
        continue
    }
    activeDatasourceById[rowTid] = true
    storedTotalsById[rowTid] = numberValue(row.exploredtablenum)
    var pool = poolInfo(row.poolcfg)
    var accessMode = accessModeOf(pool)
    var pushDataType = blank(pool.pushDataType) ? 'STRUCTURED' : text(pool.pushDataType).toUpperCase()
    var reportFiles = pool.reportRegistration?.files
    var reportFileCount = reportFiles instanceof List ? reportFiles.size() : 0
    var existingItem = resultById[rowTid]
    if (existingItem != null) {
        // ES 投影可能是旧版本；部门树以业务库中的组织信息为准，补全/覆盖现有投影。
        existingItem.orgId = row.orgid
        existingItem.orgName = row.orgname || userInfo?.orgName
        existingItem.orgPath = row.orgpath
        existingItem.appId = row.appid || existingItem.appId
        existingItem.appName = row.appname || existingItem.appName
        existingItem.dbType = row.dbtype || existingItem.dbType
        existingItem.databaseType = row.databasetype || existingItem.databaseType
        existingItem.accessMode = accessMode || existingItem.accessMode
        existingItem.pushDataType = pushDataType
        existingItem.reportFileCount = reportFileCount
        existingItem.assetStatus = row.assetstatus
        existingItem.showConnect = row.showconnect
        existingItem.connectionStatus = row.connectionstatus || existingItem.connectionStatus
        existingItem.createdBy = row.createdby
        existingItem.createdByName = creatorNames[text(row.createdby)] || ''
        existingItem.createdTime = row.createdtime || existingItem.createdTime
        existingItem.updatedTime = row.updatedtime || existingItem.updatedTime
        if (!blank(row.description)) {
            existingItem.description = row.description
        }
        continue
    }
    var item = {
        tid: rowTid,
        id: rowTid,
        dbName: row.dbname,
        description: row.description,
        appId: row.appid,
        appName: row.appname,
        dbType: row.dbtype,
        databaseType: row.databasetype,
        accessMode: accessMode,
        pushDataType: pushDataType,
        reportFileCount: reportFileCount,
        orgId: row.orgid,
        createdBy: row.createdby,
        createdByName: creatorNames[text(row.createdby)] || '',
        orgName: row.orgname || userInfo?.orgName,
        orgPath: row.orgpath,
        exploredTableNum: numberValue(row.exploredtablenum),
        tableNum: 0,
        annotatedTableNum: 0,
        unannotatedTableNum: 0,
        registeredTableNum: 0,
        unregisteredTableNum: 0,
        businessTableNum: 0,
        dictionaryTableNum: 0,
        logTableNum: 0,
        coreTableNum: 0,
        registeredCoreTableNum: 0,
        deferredTableNum: 0,
        issueTableNum: 0,
        problemTableNum: 0,
        changedTableNum: 0,
        addedTableNum: 0,
        modifiedTableNum: 0,
        deletedTableNum: 0,
        assetStatus: row.assetstatus,
        showConnect: row.showconnect,
        connectionStatus: row.connectionstatus,
        updatedTime: row.updatedtime,
        createdTime: row.createdtime,
        storageDomain: row.storagedomain,
        nodeId: row.nodeid,
        tables: []
    }
    datasourceIds.add(rowTid)
    resultById[rowTid] = item
    result.add(item)
}

// ES 只作为加速投影；已在业务库软删除或不属于当前租户的主记录不得返回。
var activeResult = []
var activeDatasourceIds = []
for (item in result) {
    var itemTid = text(item.tid)
    if (activeDatasourceById[itemTid] == true) {
        activeResult.add(item)
        activeDatasourceIds.add(itemTid)
    }
}
result = activeResult
datasourceIds = activeDatasourceIds

// 代理访问服务数量一次聚合；父数据源已逐 ID 通过当前租户及菜单范围过滤。
// 主表窗口最多 1000 项，同时限定本次服务聚合范围。
var proxyDatasourceIds = []
for (item in result) {
    item.serviceCount = 0
    if (text(item.accessMode).trim().toLowerCase() == 'proxy') {
        proxyDatasourceIds.add(text(item.tid))
    }
}
if (proxyDatasourceIds.size() > 0) {
    // 服务使用与服务列表相同的 DATA_SOURCE 角色范围，不能继承更宽的菜单范围。
    var cardServiceScope = dataScope.dataSource()
    var cardServiceUserId = text(cardServiceScope?.userId).trim()
    var cardServiceOrgIds = cardServiceScope?.organizationIds == null ? [] : cardServiceScope.organizationIds
    if (cardServiceScope.scope == 'OWNER') {
        if (blank(cardServiceUserId)) {
            exit 403, '无法识别当前服务登记人员'
        }
    } else if (cardServiceScope.scope != 'ORG' && cardServiceScope.scope != 'ALL') {
        exit 403, '数据服务范围无效'
    }
    var services = tableRead.from('api_info_t', 's', tenantId)
    var serviceSource = column('s', 'datasource_id')
    services.where(tableRead.equal(numberColumn('s', 'is_del'), 0))
    services.where(tableRead.in(serviceSource, proxyDatasourceIds))
    applyScope(services, 's', cardServiceScope.scope, cardServiceUserId, cardServiceOrgIds)
    var serviceCounts = services.grouped({datasourceId: serviceSource, serviceCount: tableRead.count()}, [serviceSource])
    var serviceCountBySource = {}
    for (serviceCount in serviceCounts) {
        serviceCountBySource[text(serviceCount.datasourceId)] = numberValue(serviceCount.serviceCount)
    }
    for (item in result) {
        item.serviceCount = numberValue(serviceCountBySource[text(item.tid)])
    }
}

// 兼容旧调用方的一次完整读取，业务库回退用最多五个固定窗口限制总量。
var legacyTables = (ids, fields) => {
    var query = db.lower().table('db_table_t').columns(...fields).where()
        .eq('tenant_id', tenantId).in('datasource_id', ids)
    query.and(condition => condition.eq('is_del', 0).or().isNull('is_del'))
    // 同名表使用 ID 稳定排序，避免窗口交界处漏读或重复读取。
    query.orderBy('table_name', 'asc').orderBy('tid', 'asc')
    var windowSize = 10000
    var firstPage = query.page(windowSize, 0)
    if (firstPage.total > 50000) {
        exit 400, '表清单超过50000项，请使用数据源表分页'
    }
    var rows = []
    rows.addAll(firstPage.list || [])
    var offset = windowSize
    while (offset < firstPage.total) {
        var nextPage = query.page(windowSize, offset)
        rows.addAll(nextPage.list || [])
        offset = offset + windowSize
    }
    return rows
}

// 管理首屏跳过兼容路径的表投影读取。
if (!summaryOnly) {
    var tableGroups = {}
    if (datasourceIds.size() > 0) {
        // 第二次 ES 查询：批量读取当前结果集下的表，避免每个数据源分别查询数据库。
        var tableSearch = null
        try {
            tableSearch = tenantEs.index(configData().es_dataassets).search({
                size: 10000,
                query: {
                    bool: {
                        must: [
                            { term: { assetType: 'table' } },
                            {
                                bool: {
                                    should: [
                                        { terms: { datasourceId: datasourceIds } },
                                        { terms: { dbId: datasourceIds } }
                                    ],
                                    minimum_should_match: 1
                                }
                            }
                        ]
                    }
                },
                sort: [
                    { updatedTime: { order: 'desc', unmapped_type: 'keyword' } },
                    { tableName: { order: 'asc', unmapped_type: 'keyword' } }
                ]
            })
        } catch (tableSearchError) {
            // 表索引超时不能阻断数据源管理；下方按 datasource_id 批量回表补齐。
            tableSearch = null
        }

        for (hit in (tableSearch?.hits?.hits == null ? [] : tableSearch.hits.hits)) {
            if (hit?._source == null) {
                continue
            }
            var table = hit._source
            table.tid = table.tid || hit._id
            table.id = table.id || table.tid
            var datasourceId = text(table.datasourceId || table.dbId)
            if (blank(datasourceId) || activeDatasourceById[datasourceId] != true) {
                continue
            }
            var rawChangeStatus = text(table.changeStatus || table.change_status).toLowerCase()

            // 变更类型只使用显式探查结果，不再从登记工作流字段猜测。
            if (blank(rawChangeStatus)) {
                rawChangeStatus = 'unchanged'
            }
            table.changeStatus = rawChangeStatus
            table.hasIssue = text(table.assetStatus) != '2'
            if (tableGroups[datasourceId] == null) {
                tableGroups[datasourceId] = []
            }
            tableGroups[datasourceId].add(table)
        }

        // 只回填当前 ES 窗口中的候选表。大数据源无需为了补两个时间字段读取全部登记表。
        var indexedTables = []
        for (sourceId in datasourceIds) {
            indexedTables.addAll(tableGroups[text(sourceId)] || [])
        }
        var timeProjection = alias => ({
            tid: column(alias, 'tid'),
            datasource_id: column(alias, 'datasource_id'),
            table_name: column(alias, 'table_name'),
            created_time: timeColumn(alias, 'created_time'),
            updated_time: timeColumn(alias, 'updated_time'),
            asset_status: numberColumn(alias, 'asset_status')
        })
        var timeQuery = () => {
            var query = tableRead.from('db_table_t', 't', tenantId)
            query.where(active('t', 'is_del'))
            return query
        }
        var tableNameOf = table => text(table.tableName || table.table_name || table.sourceTableName || table.tableNameEn).toLowerCase()
        var tableSourceOf = table => text(table.datasourceId || table.dbId)
        var timeNameKey = (source, name) => source + '::' + name
        var batchStart = 0
        while (batchStart < indexedTables.size()) {
            var batchEnd = Math.min(indexedTables.size(), batchStart + 500)
            var batch = indexedTables.subList(batchStart, batchEnd)
            batchStart = batchEnd
            var tableIds = batch.map(table => text(table.tid || table.id)).distinct()
            var sourcesInBatch = batch.map(tableSourceOf).distinct()
            var byId = timeQuery()
            byId.where(tableRead.in(column('t', 'tid'), tableIds))
            byId.where(tableRead.in(column('t', 'datasource_id'), sourcesInBatch))
            var rowsById = {}
            for (row in byId.rows(timeProjection('t'), column('t', 'tid'), 'asc', tableIds.size())) {
                rowsById[text(row.tid)] = row
            }

            // 旧投影的表 ID 可能已过期。按同一可见来源及表名找最新有效快照。
            var missingNames = {}
            var tableSource = column('t', 'datasource_id')
            var lowerName = tableRead.lower(column('t', 'table_name'))
            for (table in batch) {
                var source = tableSourceOf(table)
                var row = rowsById[text(table.tid || table.id)]
                var name = tableNameOf(table)
                if ((row == null || text(row.datasource_id) != source) && !blank(name)) {
                    missingNames[timeNameKey(source, name)] = tableRead.all([
                        tableRead.equal(tableSource, source), tableRead.equal(lowerName, name)
                    ])
                }
            }
            var rowsByName = {}
            if (missingNames.size() > 0) {
                var byName = timeQuery()
                byName.where(tableRead.any(new ArrayList(missingNames.values())))
                var ranked = byName.ranked('r', timeProjection('t'), [tableSource, lowerName],
                    [timeColumn('t', 'updated_time'), column('t', 'tid')], ['desc', 'desc'])
                for (row in ranked.rows(timeProjection('r'), column('r', 'tid'), 'asc', missingNames.size())) {
                    rowsByName[timeNameKey(text(row.datasource_id), text(row.table_name).toLowerCase())] = row
                }
            }
            for (table in batch) {
                var source = tableSourceOf(table)
                var row = rowsById[text(table.tid || table.id)]
                if (row == null || text(row.datasource_id) != source) {
                    row = rowsByName[timeNameKey(source, tableNameOf(table))]
                }
                if (row != null) {
                    table.createdTime = row.created_time
                    table.updatedTime = row.updated_time
                    table.assetStatus = row.asset_status
                    table.hasIssue = text(row.asset_status) != '2'
                }
            }
        }

        var missingDatasourceIds = []
        for (datasourceId in datasourceIds) {
            var group = tableGroups[text(datasourceId)]
            if (group == null || group.size() == 0) {
                missingDatasourceIds.add(datasourceId)
            }
        }

        // ES 表投影未刷新或缺失时，按 datasource_id 批量从 db_table_t 补齐统计和展开清单；
        // 仍然避免逐数据源回表查询，保证本部门页面不会因为单行循环拖慢。
        if (missingDatasourceIds.size() > 0) {
            var dbTableRows = legacyTables(missingDatasourceIds,
                ['tid', 'datasource_id', 'table_name', 'table_name_en', 'table_name_cn', 'table_comment',
                 'table_type', 'business_type', 'field_count', 'annotated', 'asset_status', 'updated_time', 'created_time'])
            for (row in dbTableRows) {
                var datasourceId = text(row.datasource_id)
                if (blank(datasourceId)) {
                    continue
                }
                var table = {
                    tid: row.tid,
                    id: row.tid,
                    datasourceId: datasourceId,
                    dbId: datasourceId,
                    tableName: row.table_name || row.table_name_en,
                    tableNameCn: row.table_name_cn || row.table_comment,
                    tableComment: row.table_comment,
                    tableType: row.table_type,
                    businessType: row.business_type,
                    fieldCount: row.field_count,
                    annotated: row.annotated,
                    assetStatus: row.asset_status,
                    changeStatus: 'unchanged',
                    updatedTime: row.updated_time,
                    createdTime: row.created_time
                }
                table.hasIssue = text(row.asset_status) != '2'
                if (tableGroups[datasourceId] == null) {
                    tableGroups[datasourceId] = []
                }
                tableGroups[datasourceId].add(table)
            }
        }
    }

    // 在内存中组装表级统计，确保卡片指标与展开清单来自同一批 ES 文档。
    for (item in result) {
        var tables = tableGroups[text(item.tid)]
        item.tables = tables == null ? [] : tables
        item.tableNum = item.tables.size()
        for (table in item.tables) {
            if (numberValue(table.annotated) == 1) {
                item.annotatedTableNum = item.annotatedTableNum + 1
            } else {
                item.unannotatedTableNum = item.unannotatedTableNum + 1
            }

            if (text(table.assetStatus) == '2') {
                item.registeredTableNum = item.registeredTableNum + 1
            } else {
                item.unregisteredTableNum = item.unregisteredTableNum + 1
            }
            var businessType = blank(table.businessType) ? '' : text(table.businessType).toLowerCase()
            var coreTable = ['business', '业务表', 'dict', 'dictionary', '字典表', 'log', '日志表'].contains(businessType)
            var deferredTable = ['暂不处理', '暂不登记', '不确定'].contains(businessType)
            if (coreTable) {
                item.coreTableNum = item.coreTableNum + 1
                if (text(table.assetStatus) == '2') {
                    item.registeredCoreTableNum = item.registeredCoreTableNum + 1
                }
            }
            if (deferredTable) {
                item.deferredTableNum = item.deferredTableNum + 1
            }
            if (numberValue(table.assetStatus) > 0 && (businessType == 'business' || businessType == '业务表')) {
                item.businessTableNum = item.businessTableNum + 1
            } else if (numberValue(table.assetStatus) > 0 && (businessType == 'dict' || businessType == 'dictionary' || businessType == '字典表')) {
                item.dictionaryTableNum = item.dictionaryTableNum + 1
            } else if (numberValue(table.assetStatus) > 0 && (businessType == 'log' || businessType == '日志表')) {
                item.logTableNum = item.logTableNum + 1
            }
            if (table.hasIssue) {
                item.issueTableNum = item.issueTableNum + 1
            }
            // 未登记且有问题的同一张表只计一次。
            if (text(table.assetStatus) != '2' || table.hasIssue) {
                item.problemTableNum = item.problemTableNum + 1
            }

            if (table.changeStatus == 'added') {
                item.addedTableNum = item.addedTableNum + 1
                item.changedTableNum = item.changedTableNum + 1
            } else if (table.changeStatus == 'deleted') {
                item.deletedTableNum = item.deletedTableNum + 1
                item.changedTableNum = item.changedTableNum + 1
            } else if (table.changeStatus == 'modified') {
                item.modifiedTableNum = item.modifiedTableNum + 1
                item.changedTableNum = item.changedTableNum + 1
            }
        }
    }
}
var tableStatsBySource = {}
if (datasourceIds.size() > 0) {
    // 复用主卡片已读取的探查总数和描述，表统计只查本批已授权的数据源。
    var statistics = tableRead.from('db_table_t', 't', tenantId)
    var sourceId = column('t', 'datasource_id')
    statistics.where(active('t', 'is_del'))
    statistics.where(tableRead.in(sourceId, datasourceIds))
    var type = tableRead.lower(column('t', 'business_type'))
    var status = zero(numberColumn('t', 'asset_status'))
    var registered = tableRead.equal(status, 2)
    var activeStatus = tableRead.greaterThan(status, 0)
    var coreType = tableRead.in(type, ['business', '业务表', 'dict', 'dictionary', '字典表', 'log', '日志表'])
    var statRows = statistics.grouped({
        datasourceId: sourceId, tableNum: tableRead.count(),
        annotatedTableNum: tableRead.countIf(tableRead.equal(numberColumn('t', 'annotated'), 1)),
        registeredTableNum: tableRead.countIf(registered),
        businessTableNum: tableRead.countIf(tableRead.all([activeStatus, tableRead.in(type, ['business', '业务表'])])),
        dictionaryTableNum: tableRead.countIf(tableRead.all([activeStatus, tableRead.in(type, ['dict', 'dictionary', '字典表'])])),
        logTableNum: tableRead.countIf(tableRead.all([activeStatus, tableRead.in(type, ['log', '日志表'])])),
        coreTableNum: tableRead.countIf(coreType),
        registeredCoreTableNum: tableRead.countIf(tableRead.all([coreType, registered])),
        deferredTableNum: tableRead.countIf(tableRead.in(type, ['暂不处理', '暂不登记', '不确定']))
    }, [sourceId])
    for (stat in statRows) {
        tableStatsBySource[text(stat.datasourceId)] = stat
    }
    for (item in result) {
        var stats = tableStatsBySource[text(item.tid)]
        var exploredTotal = Math.max(numberValue(item.exploredTableNum), numberValue(storedTotalsById[text(item.tid)]))
        // 类型数量只认可当前租户主表的有效登记记录；无主表记录时不沿用 ES 旧投影。
        item.businessTableNum = 0
        item.dictionaryTableNum = 0
        item.logTableNum = 0
        if (stats != null) {
            var displayTableNum = numberValue(stats.tableNum)
            if (exploredTotal > displayTableNum) {
                displayTableNum = exploredTotal
            }
            item.tableNum = displayTableNum
            item.annotatedTableNum = numberValue(stats.annotatedTableNum)
            item.unannotatedTableNum = displayTableNum - item.annotatedTableNum
            item.registeredTableNum = numberValue(stats.registeredTableNum)
            item.unregisteredTableNum = displayTableNum - item.registeredTableNum
            item.businessTableNum = numberValue(stats.businessTableNum)
            item.dictionaryTableNum = numberValue(stats.dictionaryTableNum)
            item.logTableNum = numberValue(stats.logTableNum)
            item.coreTableNum = numberValue(stats.coreTableNum)
            item.registeredCoreTableNum = numberValue(stats.registeredCoreTableNum)
            item.deferredTableNum = numberValue(stats.deferredTableNum)
            item.problemTableNum = numberValue(stats.tableNum) - item.registeredTableNum
        } else if (exploredTotal > item.tableNum) {
            item.tableNum = exploredTotal
            item.unannotatedTableNum = exploredTotal - item.annotatedTableNum
            item.unregisteredTableNum = exploredTotal - item.registeredTableNum
        }
        if (item.unannotatedTableNum < 0) {
            item.unannotatedTableNum = 0
        }
        if (item.unregisteredTableNum < 0) {
            item.unregisteredTableNum = 0
        }
    }
}

if (summaryOnly && datasourceIds.size() > 0) {
    var sourceFilters = {}
    for (sourceId in datasourceIds) {
        sourceFilters[text(sourceId)] = { bool: { should: [
            { term: { datasourceId: sourceId } }, { term: { dbId: sourceId } }
        ], minimum_should_match: 1 } }
    }
    var changeResult = tenantEs.index(configData().es_dataassets).search({
        size: 0,
        query: { term: { assetType: 'table' } },
        aggs: { bySource: {
            filters: { filters: sourceFilters },
            aggs: { changes: { filters: { filters: {
                added: { bool: { should: [{term: {changeStatus: 'added'}}, {term: {change_status: 'added'}}], minimum_should_match: 1 } },
                modified: { bool: { should: [{term: {changeStatus: 'modified'}}, {term: {change_status: 'modified'}}], minimum_should_match: 1 } },
                deleted: { bool: { should: [{term: {changeStatus: 'deleted'}}, {term: {change_status: 'deleted'}}], minimum_should_match: 1 } }
            } } } }
        } }
    })
    for (item in result) {
        var counts = changeResult?.aggregations?.bySource?.buckets[text(item.tid)]?.changes?.buckets
        item.addedTableNum = numberValue(counts?.added?.doc_count)
        item.modifiedTableNum = numberValue(counts?.modified?.doc_count)
        item.deletedTableNum = numberValue(counts?.deleted?.doc_count)
        item.changedTableNum = item.addedTableNum + item.modifiedTableNum + item.deletedTableNum
        item.remove('tables')
        // 概要必须为轻量投影：不携带凭据、配置、列数组或历史治理 JSON。
        for (key in ['password', 'username', 'jdbcUrl', 'jdbc_url', 'poolCfg', 'pool_cfg', 'columns',
                     'fieldGovernanceConfig', 'field_governance_config', 'dictionaryProfiles', 'dictionaryProfile']) {
            item.remove(key)
        }
    }
}

var sourceSortKey = (item) => {
    var value = item?.updatedTime || item?.updated_time || item?.createdTime || item?.created_time
    return blank(value) ? '' : text(value).replace('T', ' ').replace('Z', '')
}

// 合并索引和业务库回退数据后再排序，让刚保存而未进入索引的卡片也参与排序。
result = result.sort((left, right) => {
    var leftKey = sourceSortKey(left)
    var rightKey = sourceSortKey(right)
    if (rightKey > leftKey) {
        return 1
    }
    if (rightKey < leftKey) {
        return -1
    }
    return 0
})

var total = result.size()
return { orgId: orgId, orgName: userInfo?.orgName, dataScope: scopeKind, total: total, list: result }
