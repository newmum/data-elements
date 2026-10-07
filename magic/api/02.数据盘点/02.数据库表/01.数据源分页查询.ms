{
  "properties": {},
  "id": "datapd_database_page_01",
  "script": null,
  "groupId": "datapd_database_group_01",
  "name": "01.数据源分页查询",
  "createTime": null,
  "updateTime": 1790467385060,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"pageNum\": 1,\n  \"pageSize\": 20,\n  \"conditions\": [\n    {\"field\": \"assetType\", \"type\": \"match\", \"value\": \"db\"},\n    {\"field\": \"dbType\", \"type\": \"match\", \"value\": \"mysql\"},\n    {\"field\": \"registrationState\", \"type\": \"match\", \"value\": \"pending\"}\n  ],\n  \"sortField\": \"updatedTime\",\n  \"sortDir\": \"desc\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\"pageNum\":1,\"pageSize\":20,\"total\":0,\"list\":[]}",
  "description": "数据源分页查询（POST /dst/database/page）。Elasticsearch 负责关键字和组合筛选，控制库 db_datasource_t 是数据源资产存在性、租户边界与状态的事实来源；每次结果采用一次批量回查校验，过滤 ES 中残留的失效投影；使用请求参数 includeMetadataCounts、pageNum、pageSize、conditions、sortField、sortDir。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.数据源分页查询
// 路由：POST /dst/database/page
// 业务说明：数据源分页查询（POST /dst/database/page）。Elasticsearch 负责关键字和组合筛选，控制库 db_datasource_t 是数据源资产存在性、租户边界与状态的事实来源；每次结果采用一次批量回查校验，过滤 ES 中残留的失效投影；使用请求参数 includeMetadataCounts、pageNum、pageSize、conditions、sortField、sortDir。
import tenantEs
import tenantRuntime
var tenantId = tenantRuntime.id()
import '@/common/configData' as configData
import log
import dataScope

/*
 * 数据源分页查询。
 * 数据读取：Elasticsearch dataassets 索引负责检索，db_datasource_t 负责资产准入校验。
 * 数据写入：无。
 * 核心逻辑：一次 ES 检索后批量回查当前租户的有效数据源，避免 ES 残留投影进入登记流程。
 */
var text = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || text(value).trim() == ''


/*
 * Optional governance summary: one grouped query over persisted snapshots for
 * the already-authorized page only. No external connection or per-source reads.
 */
var withMetadataCounts = rows => {
    if (body?.includeMetadataCounts != true || rows.size() == 0) { return rows }
    var quotedIds = []
    for (row in rows) { quotedIds.add("'" + text(row.tid).replace("'", "''") + "'") }
    var counts = db.select("""
        select t.datasource_id as sourceId,
               count(distinct t.tid) as collectedTableCount,
               count(c.tid) as collectedFieldCount
          from db_table_t t
          left join db_table_column_t c
            on c.table_id = t.tid and c.tenant_id = #{tenantId} and ifnull(c.is_del, 0) = 0
         where t.tenant_id = #{tenantId} and ifnull(t.is_del, 0) = 0
           and t.datasource_id in (${quotedIds.join(',')})
         group by t.datasource_id
    """)
    var bySource = {}
    for (count in counts) { bySource[text(count.sourceid)] = count }
    for (row in rows) {
        var count = bySource[text(row.tid)]
        row.collectedTableCount = count == null ? 0 : count.collectedtablecount
        row.fieldCount = count == null ? 0 : count.collectedfieldcount
    }
    return rows
}

var addCondition = (must, mustNot, condition) => {
    if (condition == null || blank(condition.value)) {
        return
    }
    var field = condition.field
    var value = condition.value
    var type = blank(condition.type) ? 'match' : condition.type

    // 登记状态在回表后以 db_datasource_t.asset_status 为准，避免索引延迟误判。
    if (field == 'registrationState') {
        return
    } else if (field == 'keyword') {
        must.add({
            bool: {
                should: [
                    { wildcard: { dbName: '*' + value + '*' } },
                    { wildcard: { appName: '*' + value + '*' } },
                    { wildcard: { database: '*' + value + '*' } }
                ],
                minimum_should_match: 1
            }
        })
    } else if (type == 'in') {
        if (value == null || value.size() == 0) {
            return
        }
        var terms = {}
        terms[field] = value
        must.add({ terms: terms })
    } else if (type == 'like') {
        var wildcard = {}
        wildcard[field] = '*' + value + '*'
        must.add({ wildcard: wildcard })
    } else if (type == 'notPrefix') {
        var excluded = {}
        excluded[field] = value + '*'
        mustNot.add({ wildcard: excluded })
    } else {
        var term = {}
        term[field] = value
        must.add({ term: term })
    }
}

var pageNum = body?.pageNum == null ? 1 : body.pageNum::int
var pageSize = body?.pageSize == null ? 20 : body.pageSize::int
var requestConditions = body?.conditions == null ? [] : body.conditions
var must = [ { term: { assetType: 'db' } } ]
var mustNot = [
    { wildcard: { dbName: 'codex_*' } },
    { wildcard: { database: 'codex_*' } }
]
for (condition in requestConditions) {
    addCondition(must, mustNot, condition)
}

var sortField = blank(body?.sortField) ? 'updatedTime' : body.sortField
var sortDir = text(body?.sortDir).toLowerCase() == 'asc' ? 'asc' : 'desc'
var sort = {}
sort[sortField] = { order: sortDir, unmapped_type: 'keyword' }

// 数据源数量通常远小于数据表数量。先取回匹配的索引候选，再一次性以控制库校验，
// 这样既保留 ES 的检索能力，也不会让删除后的索引残留成为可操作数据源。
var result = null
var esAvailable = true
try {
    result = tenantEs.index(configData().es_dataassets).search({
        from: 0,
        size: 10000,
        track_total_hits: true,
        query: { bool: { must: must, must_not: mustNot } },
        sort: [sort]
    })
} catch (e) {
    // ES is a search projection. A temporary transport failure must not make
    // the authoritative datasource list unavailable.
    esAvailable = false
    log.warn('数据源 ES 查询失败，改用 MySQL 兜底: {}', e?.message)
}

var hits = result?.hits?.hits == null ? [] : result.hits.hits
if (!esAvailable) {
    var fallbackRows = db.table('db_datasource_t').where()
        .eq('is_del', 0).orderBy('updated_time', sortDir).select()
    fallbackRows = dataScope.visibleDataSourcesFor('MENU:2081000000000000002', fallbackRows)
    var fallbackItems = []
    for (source in fallbackRows) {
        var item = {}
        item.putAll(source)
        item.dbName = blank(item.dbName) ? item.db_name : item.dbName
        item.appName = blank(item.appName) ? item.app_name : item.appName
        item.updatedTime = blank(item.updatedTime) ? item.updated_time : item.updatedTime
        item.createdTime = blank(item.createdTime) ? item.created_time : item.createdTime
        item.assetStatus = item.assetStatus == null ? item.asset_status : item.assetStatus
        item.flowStatus = item.flowStatus == null ? item.flow_status : item.flowStatus
        item.displayStatus = text(item.assetStatus) == '2' ? 2 : 0
        item.display_status = item.displayStatus
        item.asset_status = item.displayStatus
        item.tableNum = item.tableNum == null ? 0 : item.tableNum
        item.table_num = item.tableNum

        var matched = true
        for (condition in requestConditions) {
            if (condition == null || blank(condition.value) || !matched) {
                continue
            }
            var field = condition.field
            var value = condition.value
            if (field == 'assetType') {
                matched = text(value) == 'db'
            } else if (field == 'registrationState') {
                matched = (text(item.assetStatus) == '2' ? 'completed' : 'pending') == text(value)
            } else if (field == 'keyword') {
                var keyword = text(value).toLowerCase()
                matched = text(item.dbName).toLowerCase().indexOf(keyword) >= 0
                    || text(item.appName).toLowerCase().indexOf(keyword) >= 0
                    || text(item.database).toLowerCase().indexOf(keyword) >= 0
            } else if (condition.type == 'in') {
                matched = value.contains(item[field])
            } else {
                matched = text(item[field]) == text(value)
            }
        }
        if (matched) {
            fallbackItems.add(item)
        }
    }
    var fallbackTotal = fallbackItems.size()
    var fallbackStart = (pageNum - 1) * pageSize
    if (fallbackStart < 0) {
        fallbackStart = 0
    }
    var fallbackEnd = fallbackStart + pageSize
    fallbackEnd = fallbackEnd > fallbackTotal ? fallbackTotal : fallbackEnd
    var fallbackList = fallbackStart >= fallbackTotal ? [] : fallbackItems.subList(fallbackStart::int, fallbackEnd::int)
    return { pageNum: pageNum, pageSize: pageSize, total: fallbackTotal, list: withMetadataCounts(fallbackList) }
}

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
    if (hit?._source == null) {
        continue
    }
    var indexItem = hit._source
    var tid = indexItem.tid || hit._id
    var datasource = blank(tid) ? null : datasourceById[tid + '']
    // The named-table tenant interceptor applies the current tenant condition here.
    // An ES document without a matching active control-table row is stale and must not
    // be shown as an editable data source.
    if (datasource == null) {
        continue
    }

    var item = {}
    item.putAll(indexItem)
    item.putAll(datasource)

    var assetStatus = datasource.asset_status == null ? datasource.assetStatus : datasource.asset_status
    var flowStatus = item.flowStatus
    var displayStatus = text(assetStatus) == '2' ? 2 : 0
    item.tid = tid
    item.assetStatus = assetStatus
    item.asset_status = assetStatus
    item.flowStatus = flowStatus
    item.flow_status = flowStatus
    item.displayStatus = displayStatus
    item.display_status = displayStatus
    item.tableNum = item.tableNum == null ? 0 : item.tableNum
    item.table_num = item.tableNum
    // 列表仅用于展示与定位，连接凭据必须留在服务端主表。
    item.remove('password')
    item.remove('username')
    item.remove('jdbcUrl')
    item.remove('jdbc_url')
    item.remove('poolCfg')
    item.remove('pool_cfg')
    var requestedRegistrationState = null
    for (condition in requestConditions) {
        if (condition?.field == 'registrationState' && !blank(condition.value)) {
            requestedRegistrationState = text(condition.value)
        }
    }
    if (blank(requestedRegistrationState) || requestedRegistrationState == (displayStatus == 2 ? 'completed' : 'pending')) {
        validItems.add(item)
    }
}

var total = validItems.size()
var start = (pageNum - 1) * pageSize
if (start < 0) {
    start = 0
}
var end = start + pageSize
end = end > total ? total : end
var list = start >= total ? [] : validItems.subList(start::int, end::int)
return { pageNum: pageNum, pageSize: pageSize, total: total, list: withMetadataCounts(list) }
