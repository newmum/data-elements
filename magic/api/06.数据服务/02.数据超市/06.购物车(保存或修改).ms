{
  "properties": {},
  "id": "624a0191565640a18c81c131ab0b4228",
  "script": null,
  "groupId": "dataservice_resource_group_01",
  "name": "06.购物车(保存或修改)",
  "createTime": 1782955344110,
  "updateTime": 1787153757107,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/shoppingCard",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"catalogId\": \"validation-catalog\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": \"validation-cart-item\"\n}",
  "description": "将已登记、已审批且已上架的可申请目录加入当前部门购物车；提供 tid 时修改本部门已有记录。同一目录沿用已有记录校验。正式操作返回购物车记录 ID，dryRun 只执行校验。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.hutool.core.util.IdUtil
import org.redisson.api.RedissonClient as redissonClient
import "@post:/sym/user/me" as me
import '@/common/market-catalog-eligibility' as marketCatalogEligibility
import log
import tableRead
import tenantRuntime

// POST /dws/market/shoppingCard：新增当前部门购物车记录，或按 tid 修改该部门已有记录。
// 新增或更换目录时，另外校验租户发布索引。正式操作返回购物车记录 ID，dryRun 只校验。
var submittedCatalog = body?.catalogId
var submittedId = body?.tid
if (!(submittedCatalog instanceof String) && !(submittedCatalog instanceof Number)) {
    exit 400, '目录标识无效'
}
if (submittedId != null && !(submittedId instanceof String) && !(submittedId instanceof Number)) {
    exit 400, '购物车记录标识无效'
}
var catalogId = (submittedCatalog + '').trim()
var tid = submittedId == null ? '' : (submittedId + '').trim()
if (!catalogId.matches('[^\\s,]{1,32}')) {
    exit 400, '目录标识无效'
}
if (tid.length() > 32) {
    exit 400, '购物车记录标识无效'
}
if (body.containsKey('dryRun') && !(body.dryRun instanceof Boolean)) {
    exit 400, 'dryRun 必须为布尔值'
}

var tenantId = tenantRuntime.id()
if (tenantId == null || (tenantId + '').trim() == '') {
    exit 401, '当前租户会话无效'
}
// 用户接口从会话选择当前应用，购物车参数不能改变身份解析。
var submitted = body
var user = null
try {
    body = {}
    user = me()
} finally {
    body = submitted
}
var actor = user?.id
if (actor == null || (actor + '').trim() == '') {
    exit 401, '当前用户会话无效'
}
var department = user?.orgId
if (department == null || (department + '').trim() == '') {
    exit 409, '当前账号未关联部门，无法加入购物车'
}

var scopedCart = () => db.table('data_market_shopping_cart_t').where()
    .eq('tenant_id', tenantId).eq('apply_org_id', department).eq('is_del', 0)
var text = (alias, name) => tableRead.column(alias, name, 'TEXT')
var number = (alias, name) => tableRead.column(alias, name, 'NUMBER')

var validateCart = () => {
    var existing = tid == '' ? null : scopedCart().eq('tid', tid).selectOne()
    if (tid != '' && existing == null) {
        exit 404, '购物车记录不存在或无权修改'
    }
    var catalog = db.table('da_catalog_t').columns('tid', 'target_table_id').where().eq('tid', catalogId)
        .eq('tenant_id', tenantId).eq('is_del', 0).eq('asset_status', 2).eq('flow_status', 2).selectOne()
    if (catalog == null) {
        exit 404, '该目录不存在或暂不可申请'
    }

    // 部门共享购物车，修改记录时仅从查重条件中排除其自身。
    var duplicate = tableRead.from('data_market_shopping_cart_t', 's', tenantId)
    var cartId = text('s', 'tid')
    duplicate.where(tableRead.equal(text('s', 'apply_org_id'), department))
    duplicate.where(tableRead.equal(number('s', 'is_del'), 0))
    duplicate.where(tableRead.equal(text('s', 'catalog_id'), catalogId))
    if (tid != '') {
        duplicate.where(tableRead.not(tableRead.equal(cartId, tid)))
    }
    if (duplicate.rows({tid: cartId}, cartId, 'asc', 1).size() > 0) {
        exit 409, '该资产已在购物车中'
    }

    // 历史申请可能没有目录关系行。按逗号边界匹配完整编号，转义 LIKE 通配符。
    var forms = tableRead.from('data_apply_form_t', 'a', tenantId)
    forms.where(tableRead.equal(text('a', 'apply_org_id'), department))
    forms.where(tableRead.equal(number('a', 'is_del'), 0))
    forms.where(tableRead.in(text('a', 'type'), ['catalogSubscribe', 'CATALOG_SUBSCRIPTION']))
    forms.where(tableRead.in(number('a', 'flow_status'), [0, 1, 2, 4]))
    var catalogList = tableRead.replace(text('a', 'catalog_ids'), ' ', '')
    var token = catalogId
    if (!token.matches('[A-Za-z0-9.-]+')) {
        for (escape in [['~', '~0'], ['%', '~1'], ['_', '~2'], ['\\', '~3']]) {
            catalogList = tableRead.replace(catalogList, escape[0], escape[1])
            token = token.replace(escape[0], escape[1])
        }
    }
    forms.where(tableRead.any([
        tableRead.equal(catalogList, token),
        tableRead.like(catalogList, token + ',%'),
        tableRead.like(catalogList, '%,' + token),
        tableRead.like(catalogList, '%,' + token + ',%')
    ]))
    var formId = text('a', 'tid')
    if (forms.rows({tid: formId}, formId, 'asc', 1).size() > 0) {
        exit 409, '该目录已申请，不予加购'
    }

    // 已存在的同目录记录可按原规则维护；新增或替换才要求目录当前已上架并可申请。
    if (existing != null && existing.catalogId == catalogId) { return }
    var eligibility = marketCatalogEligibility([{tid: catalogId, targetTableId: catalog.targetTableId}])
    if (!eligibility.eligibleIds.contains(catalogId)) {
        exit 404, '该目录不存在或暂不可申请'
    }
}

if (body.dryRun == true) {
    validateCart()
    return {dryRun: true, valid: true}
}

// 与删除草稿后回填购物车共用部门锁，避免并发请求在查重与写入之间插入相同目录。
var lock = null
var acquired = false
try {
    lock = redissonClient.getLock('data-elements:market:cart:' + tenantId + ':' + department)
    acquired = lock.tryLock()
} catch (error) {
    exit 503, '购物车暂时无法获取操作锁，请稍后重试'
}
if (!acquired) {
    exit 409, '当前部门购物车正在更新，请稍后重试'
}

try {
    validateCart()
    var now = new Date()
    var values = {
        catalog_id: catalogId,
        apply_user_id: actor,
        apply_user_name: user.userName,
        updated_by: actor,
        updated_time: now
    }
    if (tid != '') {
        if (scopedCart().eq('tid', tid).update(values) != 1) {
            exit 409, '购物车记录已变化，请刷新后重试'
        }
        return tid
    }

    var createdId = IdUtil.getSnowflakeNextIdStr()
    db.table('data_market_shopping_cart_t').insert({
        ...values,
        tid: createdId,
        tenant_id: tenantId,
        apply_org_id: department,
        created_by: actor,
        created_time: now,
        is_del: 0
    })
    return createdId
} finally {
    try {
        lock.unlock()
    } catch (error) {
        log.warn('部门购物车操作锁释放失败')
    }
}
