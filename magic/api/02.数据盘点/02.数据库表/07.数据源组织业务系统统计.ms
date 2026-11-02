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
// 接口：07.数据源组织业务系统统计
// 路由：POST /dst/database/treeStatistics
// 业务说明：从 ES 筛选候选数据源，一次批量查询当前租户主表，按 ALL/ORG/OWNER 校验后统计组织、业务系统、数据源、类型及状态；不写入业务数据。
import tenantEs
import tenantRuntime
import dataScope
import java.util.List
var tenantId = tenantRuntime.id()
import '@/common/configData' as configData

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

// 本页全量统计不经过概要 LIMIT 或 ES 候选 hits；只返回聚合，不返回数据源/连接配置。
if (text(body?.mode) == 'facets') {
    if (blank(tenantId)) { exit 401, '无法识别当前租户' }
    var scope = dataScope.resource('MENU:2081000000000000002')
    var scopeUserId = text(scope.userId)
    var scopeOrgIds = scope.organizationIds == null ? [] : scope.organizationIds
    var facetWhere = ' WHERE d.tenant_id=#{tenantId} AND COALESCE(d.is_del,0)=0 '
        + " AND COALESCE(d.db_name,'') NOT LIKE 'validation_%' "
    if (scope.scope == 'OWNER') {
        if (blank(scopeUserId)) { exit 403, '无法识别当前登记人员' }
        facetWhere += ' AND d.created_by=#{scopeUserId} '
    } else if (scope.scope == 'ORG') {
        facetWhere = facetWhere + (scopeOrgIds.size() == 0 ? ' AND 1=0 ' : ' AND d.org_id IN (#{scopeOrgIds}) ')
    } else if (scope.scope != 'ALL') { exit 403, '数据源数据范围无效' }
    var facetKeyword = text(body?.keyword).trim().toLowerCase()
    var facetFilter = blank(body?.filter) ? 'all' : text(body.filter)
    if (facetKeyword.length() > 256 || !['all','unannotated','incomplete','completed','changed'].contains(facetFilter)) {
        exit 400, '统计筛选条件不合法'
    }
    var requestedOrgs = body?.orgIds == null ? [] : body.orgIds
    if (!(requestedOrgs instanceof List) || requestedOrgs.size() > 1000) { exit 400, '组织筛选最多1000项' }
    var facetOrgIds = []
    for (id in requestedOrgs) {
        if (blank(id) || text(id).length() > 64) { exit 400, '组织标识不合法' }
        if (!facetOrgIds.contains(text(id))) { facetOrgIds.add(text(id)) }
    }
    if (facetOrgIds.size() > 0) { facetWhere += ' AND d.org_id IN (#{facetOrgIds}) ' }
    var facetAppId = text(body?.appId).trim()
    if (facetAppId.length() > 64) { exit 400, '业务系统标识不合法' }
    if (!blank(facetAppId)) { facetWhere += ' AND d.app_id=#{facetAppId} ' }
    var keywordPattern = '%' + facetKeyword.replace('!','!!').replace('%','!%').replace('_','!_') + '%'
    var validPool = "CASE WHEN JSON_VALID(d.pool_cfg) THEN d.pool_cfg ELSE '{}' END"
    if (!blank(facetKeyword)) {
        facetWhere += " AND (LOWER(d.db_name) LIKE #{keywordPattern} ESCAPE '!' OR LOWER(a.app_name) LIKE #{keywordPattern} ESCAPE '!'"
            + " OR LOWER(d.db_type) LIKE #{keywordPattern} ESCAPE '!' OR LOWER(JSON_UNQUOTE(JSON_EXTRACT(" + validPool + ", '$.database'))) LIKE #{keywordPattern} ESCAPE '!'"
            + " OR LOWER(JSON_UNQUOTE(JSON_EXTRACT(" + validPool + ", '$.schema'))) LIKE #{keywordPattern} ESCAPE '!') "
    }
    if (facetFilter == 'incomplete') { facetWhere += ' AND COALESCE(d.asset_status,0)=1 ' }
    if (facetFilter == 'completed') { facetWhere += ' AND d.asset_status=2 ' }
    var facetFrom = ' FROM db_datasource_t d LEFT JOIN sym_application_t a ON a.tid=d.app_id AND a.tenant_id=d.tenant_id AND COALESCE(a.is_del,0)=0 '
    if (facetFilter == 'unannotated') {
        facetFrom += """
            LEFT JOIN (SELECT datasource_id,COUNT(*) table_count,
                SUM(CASE WHEN annotated=1 THEN 1 ELSE 0 END) annotated_count
                FROM db_table_t WHERE tenant_id=#{tenantId} AND COALESCE(is_del,0)=0
                GROUP BY datasource_id) ts ON ts.datasource_id=d.tid
        """
        facetWhere += ' AND GREATEST(COALESCE(d.table_num,0),COALESCE(ts.table_count,0))-COALESCE(ts.annotated_count,0)>0 '
    }
    var modeExpression = 'COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(' + validPool + ", '$.accessMode')),''),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(" + validPool + ", '$.dataAccessMode')),''),'')"
    var groupedSql = 'SELECT d.org_id org_id,d.app_id app_id,a.app_name app_name,d.db_type db_type,'
        + modeExpression + ' access_mode,d.show_connect show_connect,d.asset_status asset_status,COUNT(*) source_count'
        + facetFrom + facetWhere
    var grouping = ' GROUP BY d.org_id,d.app_id,a.app_name,d.db_type,' + modeExpression + ',d.show_connect,d.asset_status '
    var parameters = {tenantId:tenantId,scopeUserId:scopeUserId,scopeOrgIds:scopeOrgIds,
        facetOrgIds:facetOrgIds,facetAppId:facetAppId,keywordPattern:keywordPattern}
    var answer = {total:0,assignedTotal:0,unassignedTotal:0,orgCounts:{},orgApps:{},dbTypes:{},typeCounts:{},
        assetStatuses:{completed:0,incomplete:0,pending:0},complete:true}
    var facetApps = {}
    var aliases = {pg:'postgresql',postgres:'postgresql',mssql:'sqlserver',dm:'dameng',
        kingbase:'kingbase8',kingbasees:'kingbase8',es:'elasticsearch',elastic:'elasticsearch'}
    var accumulate = (rows) => {
        // 仅遍历聚合桶，无逐项 SQL、ES 或权限补查。
        for (row in rows) {
            var count = (row.sourceCount || row.sourcecount || 0)::long
            var rawType = text(row.dbType || row.dbtype).trim()
            var typeKey = rawType.toLowerCase().replaceAll('[\\s_-]','')
            typeKey = aliases[typeKey] || (blank(typeKey) ? 'other' : typeKey)
            var mode = text(row.accessMode || row.accessmode).trim().toLowerCase()
            if (mode == 'proxy') { typeKey = 'mode:proxy' }
            else if (['upload','report'].contains(mode) || (typeKey=='api' && text(row.showConnect != null ? row.showConnect : row.showconnect)=='0')) { typeKey = 'mode:upload' }
            else if (['receive','push'].contains(mode)) { typeKey = 'mode:receive' }
            answer.total += count
            answer.typeCounts[typeKey] = (answer.typeCounts[typeKey] || 0) + count
            if (!blank(rawType)) { answer.dbTypes[rawType] = (answer.dbTypes[rawType] || 0) + count }
            var status = text(row.assetStatus || row.assetstatus)
            var statusKey = status == '2' ? 'completed' : status == '1' ? 'incomplete' : 'pending'
            answer.assetStatuses[statusKey] += count
            var org = text(row.orgId || row.orgid).trim()
            if (!blank(org)) { answer.orgCounts[org] = (answer.orgCounts[org] || 0) + count; answer.assignedTotal += count }
            var appId = text(row.appId || row.appid).trim()
            var appName = text(row.appName || row.appname).trim()
            if (!blank(org) && !blank(appId) && !blank(appName) && !appName.startsWith('未匹配业务系统')) {
                var appKey = org + '::' + appId
                if (facetApps[appKey] == null) {
                    var app = {value:appId,label:appName,count:0,type:'app'}
                    facetApps[appKey] = app
                    if (answer.orgApps[org] == null) { answer.orgApps[org] = [] }
                    answer.orgApps[org].add(app)
                }
                facetApps[appKey].count += count
            }
        }
    }
    if (facetFilter != 'changed') {
        accumulate(db.select(groupedSql + grouping, parameters))
    } else {
        // 变更只存在于ES表投影。按500个源一页遍历全部桶，再在数据库同一授权范围聚合。
        var afterKey = null
        while (true) {
            var composite = {size:500,sources:[{sourceId:{terms:{script:{lang:'painless',source:
                "if (doc.containsKey('datasourceId') && doc['datasourceId'].size()!=0) return doc['datasourceId'].value; if (doc.containsKey('dbId') && doc['dbId'].size()!=0) return doc['dbId'].value; return null;"}}}}]}
            if (afterKey != null) { composite.after = afterKey }
            var changedPage = tenantEs.index(configData().es_dataassets).search({size:0,timeout:'5s',
                query:{bool:{filter:[{term:{assetType:'table'}}],should:[
                    {terms:{changeStatus:['added','modified','deleted']}},{terms:{change_status:['added','modified','deleted']}}],minimum_should_match:1}},
                aggs:{changedSources:{composite:composite}}})
            if (changedPage?.timed_out == true || (changedPage?._shards?.failed || 0) > 0) { exit 503, '变更统计未完成，请重试' }
            var buckets = changedPage?.aggregations?.changedSources?.buckets
            if (buckets == null) { exit 503, '变更统计结果无效' }
            if (buckets.size() == 0) { break }
            var changedIds = buckets.map(bucket => text(bucket.key.sourceId))
            parameters.changedIds = changedIds
            accumulate(db.select(groupedSql + ' AND d.tid IN (#{changedIds}) ' + grouping, parameters))
            var nextKey = changedPage.aggregations.changedSources.after_key
            if (nextKey == null || buckets.size() < 500) { break }
            if (afterKey != null && text(nextKey) == text(afterKey)) { exit 503, '变更统计游标无效' }
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
                { wildcard: { dbName: 'validation_*' } },
                { wildcard: { database: 'validation_*' } }
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
        .in('tid', candidateIds).eq('tenant_id', tenantId).eq('is_del', 0).select()
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
