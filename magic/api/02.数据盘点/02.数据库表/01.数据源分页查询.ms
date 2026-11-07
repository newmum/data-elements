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
  "description": "数据源分页查询（POST /dst/database/page）。在当前租户和角色范围内从主表筛选、汇总采集量并分页，概要及分布保留全授权范围。使用请求参数 pageNum、pageSize、conditions、sortField、sortDir。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime
import dataScope
import tableRead
import tableSort
import cn.hutool.json.JSONUtil
import java.math.BigDecimal
import java.sql.Timestamp
import java.util.List
import java.util.LinkedHashMap

/**
 * 数据源分页查询。POST /dst/database/page
 * 接收分页、筛选条件和排序参数，返回数据源列表、总数及完整授权范围内的概要统计。
 * 查询限定在当前会话租户和角色授权范围内，不连接外部来源库，也不写入数据。
 */

var text = value => value == null ? '' : (value + '').trim()
var tenantId = text(tenantRuntime.id())
if (tenantId == '') {
    exit 401, '请先登录'
}
var scope = dataScope.resource('MENU:2081000000000000002')
if (!['ALL', 'ORG', 'OWNER'].contains(scope.scope)) {
    exit 403, '数据源数据范围无效'
}
var pageNum = Math.max(1, body?.pageNum == null ? 1 : body.pageNum::int)
var pageSize = Math.min(100, Math.max(1, body?.pageSize == null ? 20 : body.pageSize::int))

var column = (alias, name) => tableRead.column(alias, name, 'TEXT')
var number = (alias, name) => tableRead.column(alias, name, 'NUMBER')
var date = (alias, name) => tableRead.column(alias, name, 'TIME')
var zero = value => tableRead.coalesce([value, 0])
var active = alias => tableRead.equal(zero(number(alias, 'is_del')), 0)
var master = name => column('d', name)
var projection = {
    tid: master('tid'),
    dbName: master('db_name'),
    dbType: master('db_type'),
    appId: master('app_id'),
    orgId: master('org_id'),
    assetStatus: number('d', 'asset_status'),
    flowStatus: number('d', 'flow_status'),
    createdTime: date('d', 'created_time'),
    updatedTime: date('d', 'updated_time'),
    regTime: date('d', 'reg_time'),
    tableNum: number('d', 'table_num'),
    dataCatalogNum: number('d', 'data_catalog_num'),
    remark: tableRead.column('d', 'remark', 'LOB'),
    isEnable: number('d', 'is_enable'),
    connectionStatus: master('connection_status'),
    databaseType: master('database_type'),
    storageDomain: master('storage_domain'),
    nodeId: master('node_id'),
    dbVersion: master('db_version'),
    appName: column('a', 'app_name'),
    lastCollectedAt: date('jobs', 'finished_time'),
    collectedTableCount: zero(number('stats', 'table_count')),
    fieldCount: zero(number('stats', 'field_count')),
    registerTableNum: zero(number('stats', 'registered_count'))
}

// 请求只能使用明确列出的筛选、排序字段。指标别名复用同一结构化表达式。
var fields = {
    dbName: projection.dbName,
    appName: projection.appName,
    dbType: projection.dbType,
    appId: projection.appId,
    orgId: projection.orgId,
    createdTime: projection.createdTime,
    updatedTime: projection.updatedTime,
    regTime: projection.regTime,
    tableNum: zero(projection.tableNum),
    name: projection.dbName,
    entityCount: projection.collectedTableCount,
    lastCollectedAt: projection.lastCollectedAt,
    catalogNum: zero(projection.dataCatalogNum),
    collectedTableCount: projection.collectedTableCount,
    fieldCount: projection.fieldCount,
    collectedFieldCount: projection.fieldCount,
    registerTableNum: projection.registerTableNum,
    assetStatus: projection.assetStatus,
    flowStatus: projection.flowStatus,
    tid: projection.tid,
    storageDomain: projection.storageDomain,
    isEnable: projection.isEnable,
    connectionStatus: projection.connectionStatus
}
var sortFields = new LinkedHashMap()
for (field in fields.keySet()) {
    sortFields[field] = field
}
var sort = fields[tableSort.field(body, sortFields, 'updatedTime')]
var direction = tableSort.direction(body)

// 用对应的数值和时间类型绑定参数，避免依赖不同数据库的隐式转换。
var typed = (field, value) => {
    var normalized = text(value)
    try {
        if ([
            'tableNum', 'entityCount', 'catalogNum', 'collectedTableCount', 'fieldCount',
            'collectedFieldCount', 'registerTableNum', 'assetStatus', 'flowStatus', 'isEnable'
        ].contains(field)) {
            return new BigDecimal(normalized)
        }
        if (['createdTime', 'updatedTime', 'regTime', 'lastCollectedAt'].contains(field)) {
            if (normalized.length() == 10) {
                normalized = normalized + ' 00:00:00'
            }
            return Timestamp.valueOf(normalized.replace('T', ' '))
        }
        return normalized
    } catch (e) {
        return null
    }
}

var conditions = body?.conditions == null ? [] : body.conditions
if (!(conditions instanceof List) || conditions.size() > 32) {
    exit 400, '数据源筛选条件最多 32 个'
}
var filters = []
for (condition in conditions) {
    if (condition == null || text(condition.value) == '') {
        continue
    }
    var field = text(condition.field)
    var value = condition.value
    var type = text(condition.type)
    if (field == 'assetType') {
        if (text(value) != 'db') {
            filters.add(tableRead.any([]))
        }
    } else if (field == 'keyword') {
        // 关键字同时匹配库名和应用名，通配符按字面值处理。
        filters.add(tableRead.any([
            tableRead.contains(fields.dbName, text(value)),
            tableRead.contains(fields.appName, text(value))
        ]))
    } else if (field == 'registrationState') {
        var state = {completed: 2, incomplete: 1, pending: 0}[text(value)]
        filters.add(state == null ? tableRead.any([]) : tableRead.equal(zero(fields.assetStatus), state))
    } else if (field == 'collectionState') {
        filters.add(text(value) == 'collected' ? tableRead.greaterThan(fields.collectedTableCount, 0)
            : tableRead.equal(fields.collectedTableCount, 0))
    } else {
        var expression = fields[field]
        if (expression == null) {
            exit 400, '不支持的数据源筛选字段：' + field
        }
        if (type == 'in') {
            if (!(value instanceof List) || value.size() > 1000) {
                exit 400, '筛选数组最多 1000 项'
            }
            var values = value.map(item => typed(field, item)).filter(item => item != null).distinct()
            filters.add(tableRead.in(expression, values))
        } else if (type == 'notPrefix') {
            filters.add(tableRead.not(tableRead.like(expression, text(value) + '%')))
        } else if (type == 'like') {
            filters.add(tableRead.like(expression, '%' + text(value) + '%'))
        } else if (type == '' || type == 'match' || type == 'eq') {
            var bound = typed(field, value)
            filters.add(bound == null ? tableRead.any([]) : tableRead.equal(expression, bound))
        } else {
            exit 400, '不支持的数据源筛选方式'
        }
    }
}

// 客户端条件只能进一步缩小可信角色范围。
var visibleSources = () => {
    var query = tableRead.from('db_datasource_t', 'd', tenantId)
    query.where(active('d'))
    if (scope.scope == 'OWNER') {
        query.where(text(scope.userId) == '' ? tableRead.any([]) : tableRead.equal(master('created_by'), scope.userId))
    } else if (scope.scope == 'ORG') {
        query.where(tableRead.in(projection.orgId, scope.organizationIds == null ? [] : scope.organizationIds))
    }
    return query
}

// 先在库内按数据源聚合有效表与字段，支持按统计值过滤或排序后再分页。
var tables = tableRead.from('db_table_t', 't', tenantId)
var tableDatasource = column('t', 'datasource_id')
var tableId = column('t', 'tid')
tables.where(active('t'))
tables.leftJoin('db_table_column_t', 'c', 'table_id', 'tid', [active('c')])
var stats = {
    datasource_id: tableDatasource,
    table_count: tableRead.countDistinct(tableId),
    field_count: tableRead.countDistinct(column('c', 'tid')),
    registered_count: tableRead.countDistinct(
        tableRead.when(tableRead.greaterThan(number('t', 'asset_status'), 0), tableId, null)
    )
}

// 概要和分布统计完整的授权范围。请求筛选只应用于后续的分页和总数。
var query = visibleSources()
query.leftJoinGrouped(tables, 'stats', 'datasource_id', 'tid', stats, [tableDatasource])
var collected = tableRead.greaterThan(projection.collectedTableCount, 0)
var attention = tableRead.any([
    tableRead.in(projection.connectionStatus, ['failed', 'error']),
    tableRead.isNull(projection.connectionStatus)
])
var summary = query.grouped({
    sourceCount: tableRead.count(),
    tableCount: zero(tableRead.sum(number('stats', 'table_count'))),
    fieldCount: zero(tableRead.sum(number('stats', 'field_count'))),
    collectedCount: tableRead.countIf(collected),
    attentionCount: tableRead.countIf(attention)
}, [])[0]
var distributions = visibleSources().grouped({
    dbType: projection.dbType,
    storageDomain: projection.storageDomain,
    sourceCount: tableRead.count()
}, [projection.dbType, projection.storageDomain])

// 每个数据源关联最近成功采集时间，失败和已删除的采集任务不参与统计。
query.leftJoin('sym_application_t', 'a', 'tid', 'app_id', [active('a')])
var jobs = tableRead.from('metadata_table_collection_job_t', 'j', tenantId)
var jobDatasource = column('j', 'datasource_id')
jobs.where(tableRead.equal(number('j', 'is_del'), 0))
jobs.where(tableRead.equal(column('j', 'status'), 'SUCCEEDED'))
query.leftJoinGrouped(jobs, 'jobs', 'datasource_id', 'tid', {
    datasource_id: jobDatasource,
    finished_time: tableRead.max(date('j', 'finished_time'))
}, [jobDatasource], true)
for (filter in filters) {
    query.where(filter)
}

var selected = new LinkedHashMap(projection)
selected.poolCfg = tableRead.column('d', 'pool_cfg', 'LOB')
var page = query.page(selected, sort, direction, 'tid', pageNum, pageSize)
var list = []
for (row in page.rows) {
    // 仅返回声明的字段。池配置用于提取库名，不把连接配置或方言技术列发送给调用方。
    var item = new LinkedHashMap()
    for (field in projection.keySet()) {
        item[field] = row[field]
    }
    var pool = {}
    try {
        pool = JSONUtil.parseObj(row.poolCfg || '{}')
    } catch (ignored) {}
    item.database = pool.database || pool.schema || ''
    item.catalogNum = item.dataCatalogNum || 0
    item.tableNum = item.tableNum || 0
    item.collectedFieldCount = item.fieldCount
    item.displayStatus = item.assetStatus || 0
    item.id = item.tid
    item.assetType = 'db'
    // 兼容旧查询经列名映射形成的小写指标键。
    item.lastcollectedat = item.lastCollectedAt
    item.collectedtablecount = item.collectedTableCount
    item.fieldcount = item.fieldCount
    item.registertablenum = item.registerTableNum
    list.add(item)
}
return {
    pageNum: pageNum,
    pageSize: pageSize,
    total: page.total,
    list: list,
    summary: summary,
    distributions: distributions
}
