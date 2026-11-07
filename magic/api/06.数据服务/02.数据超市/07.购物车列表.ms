{
  "properties": {},
  "id": "9fc031d411354d8baff7006b11f5ad0a",
  "script": null,
  "groupId": "dataservice_resource_group_01",
  "name": "07.购物车列表",
  "createTime": 1782955344119,
  "updateTime": 1787153757165,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/shoppingCardList",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"mode\": \"page\",\n    \"pageNum\": 1,\n    \"pageSize\": 20,\n    \"keyword\": \"\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\"list\": [], \"total\": 0, \"cartTotal\": 0, \"pageNum\": 1, \"pageSize\": 20}\n}",
  "description": "读取当前会话租户和部门的购物车。count 模式只返回 total；page 模式按加入时间分页，支持目录、业务系统、数源单位搜索，每页最多 100 条。一次查询当前页候选的发布资格，区分可申请、不可申请及暂时无法确认，保留原购物车记录以供移除。未指定模式时兼容返回最多 10 条的数组。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import "@post:/sym/user/me" as me
import '@/common/market-catalog-eligibility' as marketCatalogEligibility
import log
import tableRead
import tenantRuntime

// POST /dws/market/shoppingCardList：当前租户、当前部门共享的购物车，仅执行读取。
// mode=count 返回 total；mode=page 接收 pageNum、pageSize、keyword，返回 list 与分页、购物车总数。
// 原调用保留数组结果。当前页一次核对发布资格，失效或暂时无法确认的记录仍可移除。
var mode = body?.mode == null ? '' : (body.mode + '').trim()
if (mode != '' && mode != 'page' && mode != 'count') { exit 400, '购物车读取模式无效' }
var paged = mode == 'page'
var keyword = mode == 'count' || body?.keyword == null ? '' : (body.keyword + '').trim()
if (keyword.length() > 100) { exit 400, '搜索关键词最多 100 个字符' }
var positiveInteger = (value, fallback) => {
    if (value == null) { return fallback }
    var text = (value + '').trim()
    if (!text.matches('[1-9][0-9]{0,8}')) { exit 400, '购物车分页参数无效' }
    return text::int
}
var pageNum = paged ? positiveInteger(body.pageNum, 1) : 1
var pageSize = paged ? positiveInteger(body.pageSize, 20) : 10
if (pageSize > 100) { exit 400, '购物车单页最多 100 条' }

var tenantId = tenantRuntime.id()
if (tenantId == null || (tenantId + '').trim() == '') {
    exit 401, '请先登录'
}
// 用户接口从会话确定应用及权限，购物车参数不能参与身份解析。
var submitted = body
var actor = null
try {
    body = {}
    actor = me()
} finally {
    body = submitted
}
if (actor?.id == null || (actor.id + '').trim() == '') {
    exit 401, '请先登录'
}
var department = actor.orgId
if (department == null || (department + '').trim() == '') {
    if (mode == 'count') { return {total: 0} }
    return paged ? {list: [], total: 0, cartTotal: 0, pageNum: 1, pageSize: pageSize} : []
}

var textColumn = (alias, name) => tableRead.column(alias, name, 'TEXT')
var numberColumn = (alias, name) => tableRead.column(alias, name, 'NUMBER')
var timeColumn = (alias, name) => tableRead.column(alias, name, 'TIME')
var scopedCart = () => {
    var query = tableRead.from('data_market_shopping_cart_t', 's', tenantId)
    query.where(tableRead.equal(textColumn('s', 'apply_org_id'), department))
    query.where(tableRead.equal(numberColumn('s', 'is_del'), 0))
    return query
}
var countCart = () => scopedCart().grouped({total: tableRead.count()}, [])[0].total
if (mode == 'count') { return {total: countCart()} }

// 购物车记录是分页主表，目录和机构均按同一租户关联。只投影当前有效、已登记且审批通过的目录。
var cart = scopedCart()
cart.leftJoin('da_catalog_t', 'c', 'tid', 'catalog_id', [
    tableRead.equal(numberColumn('c', 'is_del'), 0),
    tableRead.equal(numberColumn('c', 'asset_status'), 2),
    tableRead.equal(numberColumn('c', 'flow_status'), 2)
])
cart.leftJoin('rm_org_t', 'o', 'tenant_id', 'tenant_id', [
    tableRead.equal(textColumn('o', 'id'), textColumn('c', 'org_id')),
    tableRead.equal(tableRead.coalesce([numberColumn('o', 'deleted'), 0]), 0)
])
var catalogId = textColumn('s', 'catalog_id')
var catalogName = textColumn('c', 'catalog_name')
var catalogNameEn = textColumn('c', 'catalog_name_en')
var appName = textColumn('c', 'app_name')
var orgName = textColumn('o', 'name')
if (keyword != '') {
    cart.where(tableRead.any([
        tableRead.contains(catalogName, keyword),
        tableRead.contains(catalogNameEn, keyword),
        tableRead.contains(appName, keyword),
        tableRead.contains(orgName, keyword),
        tableRead.contains(catalogId, keyword)
    ]))
}
var addedAt = timeColumn('s', 'created_time')
var page = cart.pageClamped({
    tid: catalogId,
    shoppingCarId: textColumn('s', 'tid'),
    addTime: addedAt,
    availableCatalogId: textColumn('c', 'tid'),
    catalogName: catalogName,
    catalogNameEn: catalogNameEn,
    orgId: textColumn('c', 'org_id'),
    orgName: orgName,
    appId: textColumn('c', 'app_id'),
    appName: appName,
    targetTableId: textColumn('c', 'target_table_id')
}, addedAt, 'desc', 'tid', 'desc', pageNum, pageSize)

// 目录扩展属性只补当前页，遇到历史重复属性时保留最近更新的有效值。
var catalogIds = []
for (row in page.rows) {
    if (row.availableCatalogId != null && !catalogIds.contains(row.availableCatalogId)) {
        catalogIds.add(row.availableCatalogId)
    }
}
var propertiesById = {}
if (catalogIds.size() > 0) {
    var propertyNames = ['apiId', 'fileId', 'dirId', 'shareType', 'updateCycle', 'belongField']
    var properties = tableRead.from('da_prop_t', 'p', tenantId)
    var parentId = textColumn('p', 'parent_id')
    var propertyName = textColumn('p', 'prop_name')
    var propertyId = textColumn('p', 'tid')
    properties.where(tableRead.equal(numberColumn('p', 'is_del'), 0))
    properties.where(tableRead.equal(textColumn('p', 'data_type'), 'catalog'))
    properties.where(tableRead.in(parentId, catalogIds))
    properties.where(tableRead.in(propertyName, propertyNames))
    var latest = properties.ranked('latest', {
        tid: propertyId,
        parent_id: parentId,
        prop_name: propertyName,
        prop_value: tableRead.column('p', 'prop_value', 'LOB')
    }, [parentId, propertyName], [timeColumn('p', 'updated_time'), propertyId], ['desc', 'desc'])
    var metadata = latest.rows({
        parentId: textColumn('latest', 'parent_id'),
        name: textColumn('latest', 'prop_name'),
        value: tableRead.column('latest', 'prop_value', 'LOB')
    }, textColumn('latest', 'tid'), 'asc', catalogIds.size() * propertyNames.size())
    for (prop in metadata) {
        if (!propertiesById.containsKey(prop.parentId)) { propertiesById[prop.parentId] = {} }
        propertiesById[prop.parentId][prop.name] = prop.value
    }
}

var candidateIds = {}
var candidates = []
var list = page.rows.map(row => {
    var metadata = propertiesById[row.availableCatalogId] || {}
    var resType = []
    if (row.targetTableId) { resType.add('table') }
    if (metadata.apiId) { resType.add('api') }
    if (metadata.fileId) { resType.add('file') }
    if (metadata.dirId) { resType.add('dir') }
    var reason = null
    if (row.availableCatalogId == null) {
        reason = '目录不存在、已删除或未通过登记审批'
    } else if (!row.availableCatalogId.matches('[^\\s,]{1,32}')) {
        reason = '目录编号无效'
    } else if ((metadata.shareType + '').trim() == '3') {
        reason = '当前目录不予共享'
    } else if (resType.size() == 0) {
        reason = '当前目录未关联可申请资源'
    } else if (!candidateIds.containsKey(row.availableCatalogId)) {
        candidateIds[row.availableCatalogId] = true
        candidates.add({tid: row.availableCatalogId, targetTableId: row.targetTableId})
    }
    return {tid: row.tid, shoppingCarId: row.shoppingCarId, addTime: row.addTime,
        catalogName: row.availableCatalogId == null ? '目录不可用' : row.catalogName,
        catalogNameEn: row.catalogNameEn, orgId: row.orgId, orgName: row.orgName,
        appId: row.appId, appName: row.appName, assetType: 'catalog', resType: resType,
        shareType: metadata.shareType, updateCycle: metadata.updateCycle, belongField: metadata.belongField,
        available: false, availabilityStatus: 'unavailable', availabilityReason: reason}
})

// 只核验当前页通过关系库范围和业务校验的目录。索引异常时保留分页和移除能力。
if (candidates.size() > 0) {
    var eligibleIds = null
    try {
        var eligibility = marketCatalogEligibility({candidates: candidates, allowUnconfirmed: true})
        eligibleIds = eligibility?.eligibleIds
    } catch (error) {
        // 索引读取异常时继续返回当前页，候选目录暂不允许申请。
        eligibleIds = null
    }
    if (!(eligibleIds instanceof List)) {
        log.warn('购物车目录发布资格查询失败，候选数：{}', candidates.size())
    }
    for (row in list) {
        if (row.availabilityReason != null) { continue }
        if (!(eligibleIds instanceof List)) {
            row.availabilityStatus = 'unconfirmed'
            row.availabilityReason = '暂时无法确认资格，请重试'
        } else if (eligibleIds.contains(row.tid)) {
            row.available = true
            row.availabilityStatus = 'available'
        } else {
            row.availabilityReason = '当前目录已下架或不再具备申请资格'
        }
    }
}
if (!paged) { return list }
return {list: list, total: page.total, cartTotal: keyword == '' ? page.total : countCart(),
    pageNum: page.pageNum, pageSize: pageSize}
