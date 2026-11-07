{
  "properties": {},
  "id": "bc7ab46f70354dab95ca7deb785d72fd",
  "script": null,
  "groupId": "dataservice_resource_group_01",
  "name": "15.超市详情",
  "createTime": 1782955344192,
  "updateTime": 1784521647429,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/detail",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"tid\":\"validation-catalog\"}",
  "headers": [],
  "paths": [],
  "responseBody": "{\"code\":0,\"data\":{\"tid\":\"validation-catalog\",\"applicationState\":\"available\",\"canApply\":true}}",
  "description": "读取当前租户已上架且已登记审批通过的超市目录，浏览量从同次目录主表读取取得，不改变计数。结合当前部门的草稿、审批和购物车状态返回申请入口，未上架或不存在时返回 404。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import "@post:/sym/user/me" as me
import '@/common/configData' as configData
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import elasticsearch
import tableRead
import tenantRuntime

// POST /dws/market/detail 读取一个已上架目录及当前部门的申请状态，不改变浏览量。
var tenantId = tenantRuntime.id()
if (tenantId == null || (tenantId + '').trim() == '') { exit 401, '请先登录' }
var submitted = body
var actor = null
try {
    // 当前用户只由会话决定，详情参数不参与身份解析。
    body = {}
    actor = me()
} finally {
    body = submitted
}
if (actor?.id == null) { exit 401, '请先登录' }
var tid = body?.tid == null ? '' : (body.tid + '').trim()
if (!tid.matches('[A-Za-z0-9_-]{1,32}')) {
    exit 400, '数据目录标识无效'
}

var text = (alias, name) => tableRead.column(alias, name, 'TEXT')
var number = (alias, name) => tableRead.column(alias, name, 'NUMBER')
var catalog = tableRead.from('da_catalog_t', 'c', tenantId)
catalog.where(tableRead.equal(text('c', 'tid'), tid))
catalog.where(tableRead.equal(number('c', 'is_del'), 0))
catalog.where(tableRead.equal(number('c', 'asset_status'), 2))
catalog.where(tableRead.equal(number('c', 'flow_status'), 2))
var catalogRows = catalog.rows({
    tid: text('c', 'tid'),
    catalogName: text('c', 'catalog_name'),
    orgId: text('c', 'org_id'),
    dataSourceType: text('c', 'data_source_type'),
    targetTableId: text('c', 'target_table_id'),
    viewCount: number('c', 'market_view_count')
}, text('c', 'tid'), 'asc', 1)
if (catalogRows.size() == 0) {
    exit 404, '该数据目录不存在或暂未上架'
}

// 超市沿用租户索引中的上架标志；审批通过的关系库记录并不等同于已经上架。
var indexName = esCommonService.resolveTenantIndexName(configData().es_dataassets)
var search = null
try {
    search = elasticsearch.index(indexName).search({
        size: 1,
        track_total_hits: false,
        query: {bool: {filter: [
            {term: {tid: tid}},
            {term: {assetType: 'catalog'}},
            {term: {publishStatus: 1}},
            {bool: {should: [{term: {tenantId: tenantId}}, {bool: {must_not: [{exists: {field: 'tenantId'}}]}}],
                minimum_should_match: 1}}
        ]}}
    })
} catch (error) {
    var message = error?.message == null ? '' : error.message + ''
    if (message.contains('index_not_found_exception')) {
        exit 404, '该数据目录不存在或暂未上架'
    }
    throw error
}
var hits = search?.hits?.hits
if (search?.timed_out == true || (search?._shards?.failed || 0) > 0 || !(hits instanceof List)) {
    exit 502, '目录状态暂时无法确认，请稍后重试'
}
var projected = hits.size() > 0 ? hits[0]?._source : null
if (projected?.tid != tid || projected.assetType != 'catalog' || (projected.publishStatus + '') != '1'
    || (projected.tenantId != null && (projected.tenantId + '') != (tenantId + ''))) {
    exit 404, '该数据目录不存在或暂未上架'
}

var primary = catalogRows[0]
var resourceTypes = []
if (primary.targetTableId || projected.targetTableId) { resourceTypes.add('table') }
if (projected.apiId) { resourceTypes.add('api') }
if (projected.fileId) { resourceTypes.add('file') }
if (projected.dirId) { resourceTypes.add('dir') }

var department = actor.orgId
var canCollect = department != null && (department + '').trim() != ''
var collected = false
var applyStatus = null
var applicationState = canCollect ? 'available' : 'no-department'
if (canCollect) {
    var favorites = tableRead.from('data_market_collect_t', 'f', tenantId)
    favorites.where(tableRead.equal(text('f', 'apply_org_id'), department))
    favorites.where(tableRead.equal(text('f', 'catalog_id'), tid))
    collected = favorites.rows({tid: text('f', 'tid')}, text('f', 'tid'), 'asc', 1).size() > 0

    // 历史申请可能只保存逗号分隔目录。按完整编号匹配，避免相似目录互相阻塞。
    var normalizedIds = tableRead.replace(text('a', 'catalog_ids'), ' ', '')
    var token = tid
    if (tid.contains('_')) {
        normalizedIds = tableRead.replace(tableRead.replace(normalizedIds, '~', '~0'), '_', '~2')
        token = tid.replace('_', '~2')
    }
    var forms = tableRead.from('data_apply_form_t', 'a', tenantId)
    var status = number('a', 'flow_status')
    forms.where(tableRead.equal(number('a', 'is_del'), 0))
    forms.where(tableRead.equal(text('a', 'apply_org_id'), department))
    forms.where(tableRead.in(text('a', 'type'), ['catalogSubscribe', 'CATALOG_SUBSCRIPTION']))
    forms.where(tableRead.in(status, [0, 1, 2, 4]))
    forms.where(tableRead.any([
        tableRead.equal(normalizedIds, token),
        tableRead.like(normalizedIds, token + ',%'),
        tableRead.like(normalizedIds, '%,' + token),
        tableRead.like(normalizedIds, '%,' + token + ',%')
    ]))
    var statuses = forms.grouped({status: status}, [status]).map(row => row.status::int)
    if (statuses.contains(2)) {
        applyStatus = 2
        applicationState = 'approved'
    } else if (statuses.contains(1)) {
        applyStatus = 1
        applicationState = 'pending'
    } else if (statuses.contains(0)) {
        applyStatus = 0
        applicationState = 'draft'
    } else if (statuses.contains(4)) {
        applyStatus = 1
        applicationState = 'withdrawn'
    } else if ((projected.shareType + '') == '3') {
        applicationState = 'restricted'
    } else if (resourceTypes.size() == 0) {
        applicationState = 'no-resources'
    } else {
        var cart = tableRead.from('data_market_shopping_cart_t', 's', tenantId)
        cart.where(tableRead.equal(number('s', 'is_del'), 0))
        cart.where(tableRead.equal(text('s', 'apply_org_id'), department))
        cart.where(tableRead.equal(text('s', 'catalog_id'), tid))
        if (cart.rows({tid: text('s', 'tid')}, text('s', 'tid'), 'asc', 1).size() > 0) {
            applyStatus = 'cart'
            applicationState = 'cart'
        }
    }
}

return {
    ...projected,
    tid: tid,
    // 已有主表读取同时返回权威浏览量，不增加详情读取，也不使用索引旧值兜底。
    viewCount: primary.viewCount == null ? 0 : primary.viewCount,
    catalogName: primary.catalogName || projected.catalogName,
    orgId: primary.orgId || projected.orgId,
    dataSourceType: primary.dataSourceType || projected.dataSourceType,
    resType: resourceTypes,
    collected: collected,
    canCollect: canCollect,
    applyStatus: applyStatus,
    applicationState: applicationState,
    canApply: applicationState == 'available' || applicationState == 'cart'
}
