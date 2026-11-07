{
  "properties": {},
  "id": "1c10f955048340db9b921cb342b5e8f5",
  "script": null,
  "groupId": "dataservice_resource_group_01",
  "name": "11.资产申请单按id删除",
  "createTime": 1782955344158,
  "updateTime": 1787153757239,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/applyForm/deleteById",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"validation-market-form\",\n    \"addToCart\": true\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": true\n}",
  "description": "按 tid 删除本人在当前租户的超市订阅草稿。addToCart 为 true 时，将仍可申请的目录重新加入当前部门购物车，和主记录、目录关联、扩展属性一起提交。正式删除返回 true；dryRun 只验证并返回可回填数量。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.hutool.core.util.IdUtil
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import org.redisson.api.RedissonClient as redissonClient
import "@post:/sym/user/me" as me
import '@/common/market-catalog-eligibility' as marketCatalogEligibility
import log
import tableRead
import tenantRuntime

// POST /dws/market/applyForm/deleteById：按 tid 删除当前租户内本人创建的超市订阅草稿。
// addToCart 先排除已在购物车或已有有效申请的目录，再校验剩余目录，回填和草稿删除共用事务。
// 正式删除返回 true，并在提交后清理检索索引；dryRun 只校验并返回可回填数量。
var submittedId = body?.tid
if (!(submittedId instanceof String) && !(submittedId instanceof Number)) {
    exit 400, '申请单标识无效'
}
var tid = (submittedId + '').trim()
if (tid == '' || tid.length() > 32) { exit 400, '申请单标识无效' }
if (body.containsKey('dryRun') && !(body.dryRun instanceof Boolean)) {
    exit 400, 'dryRun 必须为布尔值'
}

var tenantId = tenantRuntime.id()
if (tenantId == null || (tenantId + '').trim() == '') { exit 401, '当前租户会话无效' }
// 用户权限按当前会话应用解析，提交参数不参与身份校验。
var submitted = body
var user = null
try {
    body = {}
    user = me()
} finally {
    body = submitted
}
var actor = user?.id
if (actor == null || (actor + '').trim() == '') { exit 401, '当前用户会话无效' }
var department = user?.orgId
var marketType = 'catalogSubscribe'
var scoped = table => db.table(table).where().eq('tenant_id', tenantId).eq('is_del', 0)
var ownedMarketDraft = table => table.where()
    .eq('tenant_id', tenantId)
    .eq('is_del', 0)
    .eq('tid', tid)
    .eq('type', marketType)
    .and(owner => owner.eq('apply_user_id', actor).or()
        .and(legacy => legacy.eq('created_by', actor)
            .and(missing => missing.isNull('apply_user_id').or().eq('apply_user_id', ''))))

// 只有当前申请人可删除草稿；早期申请人未填写时按创建人校验，与提交接口一致。
var form = ownedMarketDraft(db.table('data_apply_form_t').columns('type', 'flow_status', 'catalog_ids')).selectOne()
if (form == null || !marketType.equals(form.type)) { exit 404, '申请单不存在或无权删除' }
if (form.flowStatus::int(-1) != 0) { exit 409, '只有待提交的申请可以删除' }

var catalogIds = []
if (body.addToCart == true) {
    if (department == null || (department + '').trim() == '') {
        exit 409, '当前账号未关联部门，请取消重新加购后再删除'
    }
    for (catalogId in (form.catalogIds || '').split(',')) {
        var id = catalogId.trim()
        if (id.length() > 32) { exit 409, '关联目录标识无效，请取消重新加购后再删除' }
        if (id != '' && !catalogIds.contains(id)) { catalogIds.add(id) }
    }
    if (catalogIds.size() > 100) { exit 409, '关联目录超过可回填范围，请取消重新加购后再删除' }
}

var textColumn = (alias, column) => tableRead.column(alias, column, 'TEXT')
var numberColumn = (alias, column) => tableRead.column(alias, column, 'NUMBER')
var catalogsToRestore = () => {
    if (catalogIds.size() == 0) { return [] }

    // 当前部门购物车共享，先去掉已有目录。历史失效目录若已存在，也无需再次加购。
    var cart = tableRead.from('data_market_shopping_cart_t', 'line', tenantId)
    var cartCatalogId = textColumn('line', 'catalog_id')
    cart.where(tableRead.equal(textColumn('line', 'apply_org_id'), department))
    cart.where(tableRead.equal(numberColumn('line', 'is_del'), 0))
    cart.where(tableRead.in(cartCatalogId, catalogIds))
    var inCart = cart.grouped({catalogId: cartCatalogId}, [cartCatalogId]).map(row => row.catalogId)
    var available = catalogIds.filter(id => !inCart.contains(id))
    if (available.size() == 0) { return [] }

    // 旧申请可能缺少目录关系行。按目录编号的逗号边界匹配，固定一次聚合返回仍有申请的目录。
    var applications = tableRead.from('data_apply_form_t', 'a', tenantId)
    applications.where(tableRead.equal(textColumn('a', 'apply_org_id'), department))
    applications.where(tableRead.equal(numberColumn('a', 'is_del'), 0))
    applications.where(tableRead.in(textColumn('a', 'type'), [marketType, 'CATALOG_SUBSCRIPTION']))
    applications.where(tableRead.in(numberColumn('a', 'flow_status'), [0, 1, 2, 4]))
    applications.where(tableRead.not(tableRead.equal(textColumn('a', 'tid'), tid)))

    var catalogList = tableRead.replace(textColumn('a', 'catalog_ids'), ' ', '')
    // 转义历史编号中可能出现的 LIKE 通配符，不依赖数据库各自的默认转义规则。
    var escapes = [['~', '~0'], ['%', '~1'], ['_', '~2'], ['\\', '~3']]
    var encodedList = catalogList
    for (escape in escapes) { encodedList = tableRead.replace(encodedList, escape[0], escape[1]) }
    var projection = {}
    for (index, candidate in available) {
        var id = candidate
        var column = catalogList
        if (!id.matches('[A-Za-z0-9.-]+')) {
            column = encodedList
            for (escape in escapes) { id = id.replace(escape[0], escape[1]) }
        }
        projection['catalog' + index] = tableRead.countIf(tableRead.any([
            tableRead.equal(column, id),
            tableRead.like(column, id + ',%'),
            tableRead.like(column, '%,' + id),
            tableRead.like(column, '%,' + id + ',%')
        ]))
    }
    var counts = applications.grouped(projection, [])[0]
    var result = []
    for (index, candidate in available) {
        if (counts['catalog' + index]::long == 0) { result.add(candidate) }
    }
    if (result.size() == 0) { return [] }

    // 只核验本次确实会新增的目录，关系库审批和上架资格缺一不可。
    var candidates = tableRead.from('da_catalog_t', 'c', tenantId)
    var catalogIdColumn = textColumn('c', 'tid')
    candidates.where(tableRead.in(catalogIdColumn, result))
    candidates.where(tableRead.equal(numberColumn('c', 'is_del'), 0))
    candidates.where(tableRead.equal(numberColumn('c', 'asset_status'), 2))
    candidates.where(tableRead.equal(numberColumn('c', 'flow_status'), 2))
    var catalogs = candidates.rows({tid: catalogIdColumn, targetTableId: textColumn('c', 'target_table_id')},
        catalogIdColumn, 'asc', result.size())
    if (catalogs.size() != result.size()) {
        exit 409, '部分目录已不可申请，请取消重新加购后再删除'
    }
    var eligible = marketCatalogEligibility(catalogs)?.eligibleIds
    if (!(eligible instanceof List)) { exit 502, '目录资格暂时无法核验，请稍后重试' }
    for (id in result) {
        if (!eligible.contains(id)) { exit 409, '部分目录已不可申请，请取消重新加购后再删除' }
    }
    return result
}

if (body.dryRun == true) {
    return {dryRun: true, valid: true, tid: tid, readdCount: catalogsToRestore().size()}
}

var now = new Date()
var deleteValues = {is_del: 1, updated_by: actor, updated_time: now}
var deleteDraft = () => db.transaction(() => {
    var restored = catalogsToRestore()

    // 主记录的条件更新先取得写锁。若已被修改或提交，子记录和购物车均不写入。
    var draftVersion = ownedMarketDraft(db.table('data_apply_form_t')).eq('flow_status', 0)
    if (form.catalogIds == null) {
        draftVersion.isNull('catalog_ids')
    } else {
        draftVersion.eq('catalog_ids', form.catalogIds)
    }
    var main = draftVersion.update(deleteValues)
    if (main != 1) { exit 409, '申请单已变化，请刷新后重试' }

    scoped('da_catalog_apply_form_rela').eq('apply_form_id', tid).update(deleteValues)
    scoped('da_prop_t').eq('parent_id', tid).eq('data_type', 'apply_form').update(deleteValues)

    if (restored.size() > 0) {
        var rows = restored.map(id => {
            return {tid: IdUtil.getSnowflakeNextIdStr(), catalog_id: id, tenant_id: tenantId,
                apply_user_id: actor, apply_user_name: user.userName, apply_org_id: department,
                created_by: actor, created_time: now, updated_by: actor, updated_time: now, is_del: 0}
        })
        db.table('data_market_shopping_cart_t').batchInsert(rows)
    }
})

if (catalogIds.size() > 0) {
    // 同一部门并发回填时串行核对并写入购物车，防止重复加购。
    var lock = redissonClient.getLock('data-elements:market:cart:' + tenantId + ':' + department)
    if (!lock.tryLock()) { exit 409, '当前部门购物车正在更新，请稍后重试' }
    try {
        deleteDraft()
    } finally {
        lock.unlock()
    }
} else {
    deleteDraft()
}

try {
    esCommonService.deleteById('dataassets_apply_form', tid)
} catch (error) {
    // 索引故障不会改变已提交的删除结果；列表和详情以关系库为准。
    log.warn('超市申请草稿已删除，检索索引清理失败')
}

return true
