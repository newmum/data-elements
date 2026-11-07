{
  "properties": {},
  "id": "64c0bb65fad54c538d5b80423623cbf7",
  "script": null,
  "groupId": "dataservice_resource_group_01",
  "name": "14.超市列表",
  "createTime": 1782955344184,
  "updateTime": 1787153757274,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": 1,\n    \"pageSize\": 20,\n    \"keyword\": \"\",\n    \"sortField\": \"createdTime\",\n    \"sortDir\": \"desc\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"list\": [],\n        \"total\": 0,\n        \"totalLimited\": false\n    }\n}",
  "description": "按关键字、组织、业务系统、共享类型、加工程度和发布时间查询当前租户已上架目录。每页最多 100 条，可浏览前 10000 条。一次批量读取本页有效且审批通过目录的权威浏览量，按当前部门补充收藏、加购和申请状态，返回 list、total 与 totalLimited。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.hutool.core.date.DateUtil
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import "@post:/sym/user/me" as me
import '@/common/configData' as configData
import elasticsearch
import tableRead
import tenantRuntime

// POST /dws/market/list：检索当前租户已上架的数据目录，按当前部门补充本页的收藏、加购和申请状态。
// pageNum、pageSize、keyword、orgPath、appId、shareType、dataProcessLevel、publishedWithin 和排序均可选。
// 保留旧页面使用的少量筛选结构，浏览量来自本页目录主表。返回 list、可浏览的 total 与 totalLimited，无业务写入。
var tenantId = tenantRuntime.id()
if (tenantId == null || (tenantId + '').trim() == '') { exit 401, '请先登录' }
// 用户身份按会话应用解析，目录筛选里的 appId 不传给身份接口。
var submitted = body
var actor = null
try {
    body = {}
    actor = me()
} finally {
    body = submitted
}
if (actor?.id == null || (actor.id + '').trim() == '') { exit 401, '请先登录' }
if (body?.indexType != null) { exit 400, '数据超市索引由当前租户确定' }

var readPageValue = (value, fallback) => {
    if (value == null) { return fallback }
    var text = (value + '').trim()
    if (!text.matches('[1-9][0-9]{0,8}')) { exit 400, '分页范围不合法' }
    return text::int
}
var pageNum = readPageValue(body?.pageNum, 1)
var pageSize = readPageValue(body?.pageSize, 20)
if (pageSize > 100) { exit 400, '单页最多100条' }
var browseLimit = 10000
var offset = (pageNum::long - 1) * pageSize
if (offset >= browseLimit) { exit 400, '最多浏览前10000条，请缩小搜索范围' }
var size = Math.min(pageSize, browseLimit - offset)
var sortField = body?.sortField == null || body.sortField == '' ? 'createdTime' : body.sortField
var sortDir = body?.sortDir == null || body.sortDir == '' ? 'desc' : body.sortDir
if (!['createdTime', 'updatedTime', 'recordCount'].contains(sortField) || !['asc', 'desc'].contains(sortDir)) {
    exit 400, '排序条件无效'
}

var searchFields = ['catalogName^3', 'catalogNameEn^2']
var termsFields = ['orgPath', 'appId', 'shareType', 'dataProcessLevel', 'policeType']
var valueByField = {}
var readFilter = (value, maxLength) => {
    if (!(value instanceof String) && !(value instanceof Number)) { exit 400, '目录筛选值无效' }
    var normalized = (value + '').trim()
    if (normalized == '' || normalized.length() > maxLength) { exit 400, '目录筛选值无效' }
    return normalized
}
var readKeyword = value => {
    if (!(value instanceof String)) { exit 400, '搜索关键词无效' }
    var text = value.trim()
    if (text.length() > 100) { exit 400, '搜索关键词最多100个字符' }
    return text
}
var keyword = body?.keyword == null ? '' : readKeyword(body.keyword)
var modernFilters = {orgPath: body?.orgPath, appId: body?.appId, shareType: body?.shareType,
    dataProcessLevel: body?.dataProcessLevel}
for (field, value in modernFilters) {
    if (value != null && value != '') {
        valueByField[field] = [readFilter(value, field == 'orgPath' ? 500 : 128)]
    }
}

var publishedWithin = body?.publishedWithin
var publishRange = null
if (publishedWithin != null && publishedWithin != '') {
    if (!['10d', '1M', '3M', '6M'].contains(publishedWithin)) { exit 400, '发布时间范围无效' }
    var now = new Date()
    var from = publishedWithin == '10d' ? DateUtil.offsetDay(now, -10)
        : DateUtil.offsetMonth(now, -(publishedWithin.substring(0, 1)::int))
    publishRange = {gte: DateUtil.formatDateTime(from), lte: DateUtil.formatDateTime(now)}
}

// 仅翻译旧页面实际提交的字段与操作符，客户端查询不能替换索引、上架条件或脚本。
var singleKey = mapping => {
    for (key, value in mapping) { return key }
}
var legacy = body?.query
if (legacy != null) {
    if (!(legacy instanceof List) || legacy.size() > 10) { exit 400, '旧版目录筛选条件无效' }
    for (clause in legacy) {
        if (!(clause instanceof Map) || clause.size() != 1) { exit 400, '旧版目录筛选条件无效' }
        if (clause.containsKey('multi_match')) {
            var search = clause.multi_match
            if (!(search instanceof Map) || search.size() != 2 || !searchFields.equals(search.fields) || keyword != '') {
                exit 400, '旧版关键词条件无效'
            }
            keyword = readKeyword(search.query)
            continue
        }
        var operator = singleKey(clause)
        var argument = clause[operator]
        if (!(argument instanceof Map) || argument.size() != 1) { exit 400, '旧版目录筛选条件无效' }
        var field = singleKey(argument)
        var value = argument[field]
        if (operator == 'term') {
            if (!(field == 'assetType' && value == 'catalog') && !(field == 'publishStatus' && (value + '') == '1')) {
                exit 400, '只允许查询已上架的数据目录'
            }
        } else if (operator == 'terms' && termsFields.contains(field)) {
            if (!(value instanceof List) || value.size() == 0 || value.size() > 100 || valueByField.containsKey(field)) {
                exit 400, '旧版目录筛选值无效'
            }
            valueByField[field] = value.map(item => readFilter(item, field == 'orgPath' ? 500 : 128))
        } else if (operator == 'range' && field == 'publishTime') {
            var datePattern = '[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}'
            if (publishRange != null || !(value instanceof Map) || value.size() != 2 ||
                !(value.gte instanceof String) || !(value.lte instanceof String) ||
                !value.gte.matches(datePattern) || !value.lte.matches(datePattern)) {
                exit 400, '旧版发布时间范围无效'
            }
            publishRange = {gte: value.gte, lte: value.lte}
        } else {
            exit 400, '不支持该目录筛选条件'
        }
    }
}

// 上架列表沿用已发布索引，详情再校验目录主记录。保留没有租户字段的历史投影，拒绝明确属于其他租户的文档。
var filters = [{term: {assetType: 'catalog'}}, {term: {publishStatus: 1}},
    {bool: {should: [{term: {tenantId: tenantId}}, {bool: {must_not: [{exists: {field: 'tenantId'}}]}}],
        minimum_should_match: 1}}]
for (field, values in valueByField) { filters.add({terms: {[field]: values}}) }
if (publishRange != null) {
    // 同一字段在历史索引中可能是 keyword，在现行索引中可能是 date；日期索引需要显式解析此格式。
    filters.add({range: {publishTime: {...publishRange, format: 'yyyy-MM-dd HH:mm:ss'}}})
}
var conditions = {filter: filters}
if (keyword != '') { conditions.must = [{multi_match: {query: keyword, fields: searchFields}}] }
var sourceFields = ['tid', 'assetType', 'catalogName', 'catalogNameEn', 'targetTableId', 'apiId', 'fileId', 'dirId',
    'orgId', 'orgName', 'shareType', 'updateCycle', 'appId', 'appName', 'createdTime', 'updatedTime',
    'publishTime', 'publishStatus', 'recordCount', 'dataProcessLevel', 'tenantId']
var query = {from: offset, size: size, track_total_hits: browseLimit, _source: sourceFields,
    query: {bool: conditions}, sort: [{[sortField]: {order: sortDir, unmapped_type: 'keyword', missing: '_last'}},
        {tid: {order: 'asc', unmapped_type: 'keyword'}}]}
var empty = {list: [], total: 0, totalLimited: false}
var search = null
try {
    // 仅解析受信索引名称，直接读取已存在的租户索引。
    var physicalIndex = esCommonService.resolveTenantIndexName(configData().es_dataassets)
    search = elasticsearch.index(physicalIndex).search(query)
} catch (error) {
    var message = error?.message == null ? '' : error.message + ''
    if (message.contains('index_not_found_exception')) { return empty }
    throw error
}
if (search?.timed_out == true || (search?._shards?.failed)::int(0) > 0 || !(search?.hits?.hits instanceof List)) {
    exit 502, '超市目录查询未完成，请稍后重试'
}

var totalHits = search?.hits?.total
var rawTotal = totalHits instanceof Map ? totalHits.value::long(0) : totalHits::long(0)
var totalLimited = rawTotal > browseLimit || (totalHits instanceof Map && totalHits.relation == 'gte')
var total = Math.min(rawTotal, browseLimit)
var documents = []
var catalogIds = []
for (hit in search?.hits?.hits || []) {
    var item = hit?._source
    if (item?.tid instanceof String && item.tid.matches('[A-Za-z0-9_-]{1,32}') &&
        item.assetType == 'catalog' && (item.publishStatus + '') == '1' &&
        (item.tenantId == null || (item.tenantId + '') == tenantId) && !catalogIds.contains(item.tid)) {
        catalogIds.add(item.tid)
        documents.add(item)
    }
}
if (documents.size() == 0) { return {list: [], total: total, totalLimited: totalLimited} }

// 浏览量只从当前页已授权目录主记录批量读取，不让索引重建或陈旧投影覆盖权威值。
// 保留上架索引的分页与顺序；失效主记录不补出计数，详情仍按原契约重新校验。
var counters = tableRead.from('da_catalog_t', 'c', tenantId)
var counterId = tableRead.column('c', 'tid', 'TEXT')
counters.where(tableRead.in(counterId, catalogIds))
counters.where(tableRead.equal(tableRead.column('c', 'is_del', 'NUMBER'), 0))
counters.where(tableRead.equal(tableRead.column('c', 'asset_status', 'NUMBER'), 2))
counters.where(tableRead.equal(tableRead.column('c', 'flow_status', 'NUMBER'), 2))
var viewCounts = {}
for (counter in counters.rows({tid: counterId, viewCount: tableRead.column('c', 'market_view_count', 'NUMBER')},
    counterId, 'asc', catalogIds.size())) {
    viewCounts[counter.tid] = counter.viewCount
}

var department = actor.orgId
var hasDepartment = department != null && (department + '').trim() != ''
var favorites = []
var inCart = []
var priorityById = {}
if (hasDepartment) {
    var textColumn = (alias, name) => tableRead.column(alias, name, 'TEXT')
    var numberColumn = (alias, name) => tableRead.column(alias, name, 'NUMBER')
    var departmentRows = (table, alias) => {
        var scoped = tableRead.from(table, alias, tenantId)
        scoped.where(tableRead.equal(textColumn(alias, 'apply_org_id'), department))
        return scoped
    }
    var favoriteRows = departmentRows('data_market_collect_t', 'f')
    var favoriteId = textColumn('f', 'catalog_id')
    favoriteRows.where(tableRead.in(favoriteId, catalogIds))
    favorites = favoriteRows.grouped({tid: favoriteId}, [favoriteId]).map(row => row.tid)

    var cartRows = departmentRows('data_market_shopping_cart_t', 's')
    var cartId = textColumn('s', 'catalog_id')
    cartRows.where(tableRead.equal(numberColumn('s', 'is_del'), 0))
    cartRows.where(tableRead.in(cartId, catalogIds))
    inCart = cartRows.grouped({tid: cartId}, [cartId]).map(row => row.tid)

    // 早期申请可能仅在主记录保存逗号分隔目录。只聚合本页编号，按精确目录边界比较。
    var forms = departmentRows('data_apply_form_t', 'a')
    var flow = numberColumn('a', 'flow_status')
    forms.where(tableRead.equal(numberColumn('a', 'is_del'), 0))
    forms.where(tableRead.in(textColumn('a', 'type'), ['catalogSubscribe', 'CATALOG_SUBSCRIPTION']))
    forms.where(tableRead.in(flow, [0, 1, 2, 4]))
    // 已通过、审批中、草稿、撤回依次优先；撤回保留旧列表显示值 1。
    var priority = tableRead.when(tableRead.equal(flow, 2), 4,
        tableRead.when(tableRead.equal(flow, 1), 3, tableRead.when(tableRead.equal(flow, 0), 2, 1)))
    var catalogList = tableRead.replace(textColumn('a', 'catalog_ids'), ' ', '')
    var escapedCatalogList = tableRead.replace(tableRead.replace(catalogList, '!', '!0'), '_', '!1')
    var projection = {}
    for (i, id in catalogIds) {
        var column = id.contains('_') ? escapedCatalogList : catalogList
        var token = id.replace('_', '!1')
        var matches = tableRead.any([
            tableRead.equal(column, token), tableRead.like(column, token + ',%'),
            tableRead.like(column, '%,' + token), tableRead.like(column, '%,' + token + ',%')
        ])
        projection['status' + i] = tableRead.max(tableRead.when(matches, priority, 0))
    }
    var priorities = forms.grouped(projection, [])[0]
    for (i, id in catalogIds) {
        priorityById[id] = priorities['status' + i]::int(0)
    }
}

var list = documents.map(document => {
    var row = {}
    for (field in sourceFields) {
        if (field != 'tenantId' && document.containsKey(field)) { row[field] = document[field] }
    }
    var resourceTypes = []
    if (row.targetTableId) { resourceTypes.add('table') }
    if (row.apiId) { resourceTypes.add('api') }
    if (row.fileId) { resourceTypes.add('file') }
    if (row.dirId) { resourceTypes.add('dir') }
    var priority = priorityById[row.tid]::int(0)
    var states = [null, 1, 0, 1, 2]
    var applyStatus = priority > 0 ? states[priority] : (inCart.contains(row.tid) ? 'cart' : null)
    row.resType = resourceTypes
    row.viewCount = viewCounts[row.tid] == null ? 0 : viewCounts[row.tid]
    row.collected = favorites.contains(row.tid)
    row.applyStatus = applyStatus
    row.canCollect = hasDepartment
    row.canApply = hasDepartment && applyStatus == null && resourceTypes.size() > 0 && (row.shareType + '') != '3'
    return row
})
return {list: list, total: total, totalLimited: totalLimited}
