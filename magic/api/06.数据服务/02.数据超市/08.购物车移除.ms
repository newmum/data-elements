{
  "properties": {},
  "id": "8cc51c590a964ad2b9a5b76656c5b3ce",
  "script": null,
  "groupId": "dataservice_resource_group_01",
  "name": "08.购物车移除",
  "createTime": 1782955344127,
  "updateTime": 1784437771765,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/shoppingCardRemove",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"validation-cart-item\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": true\n}",
  "description": "按 tid 移除当前租户当前部门的有效购物车记录，保留历史记录和操作者。正式移除返回 true；dryRun 只校验记录范围。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import org.redisson.api.RedissonClient as redissonClient
import "@post:/sym/user/me" as me
import log
import tenantRuntime

// POST /dws/market/shoppingCardRemove：按 tid 移除当前租户当前部门的购物车记录。
// 使用部门共享购物车的操作锁；正式移除只更新逻辑删除状态并返回 true，dryRun 只校验。
var submittedId = body?.tid
if (!(submittedId instanceof String) && !(submittedId instanceof Number)) {
    exit 400, '购物车记录标识无效'
}
var tid = (submittedId + '').trim()
if (tid == '' || tid.length() > 32) {
    exit 400, '购物车记录标识无效'
}
if (body.containsKey('dryRun') && !(body.dryRun instanceof Boolean)) {
    exit 400, 'dryRun 必须为布尔值'
}

var tenantId = tenantRuntime.id()
if (tenantId == null || (tenantId + '').trim() == '') {
    exit 401, '当前租户会话无效'
}
var user = me()
var actor = user?.id
if (actor == null || (actor + '').trim() == '') {
    exit 401, '当前用户会话无效'
}
var department = user?.orgId
if (department == null || (department + '').trim() == '') {
    exit 409, '当前账号未关联部门，无法移除购物车记录'
}

// 购物车由部门共享。可移除本部门其他成员添加的记录，不能用请求参数指定部门。
var scopedCart = () => db.table('data_market_shopping_cart_t').columns('tid').where()
    .eq('tenant_id', tenantId).eq('apply_org_id', department).eq('tid', tid).eq('is_del', 0)
if (body.dryRun == true) {
    if (scopedCart().selectOne() == null) {
        exit 404, '购物车记录不存在或无权移除'
    }
    return {dryRun: true, valid: true}
}

// 与加购及草稿回填串行，确保回填看到的部门购物车状态没有同时被移除。
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
    var changed = scopedCart().update({is_del: 1, updated_by: actor, updated_time: new Date()})
    if (changed != 1) {
        exit 404, '购物车记录不存在或无权移除'
    }
    return true
} finally {
    try {
        lock.unlock()
    } catch (error) {
        log.warn('部门购物车操作锁释放失败')
    }
}
