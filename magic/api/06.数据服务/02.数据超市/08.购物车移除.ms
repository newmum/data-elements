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
  "requestBody": "{\n    \"tid\": \"6b6ba3bc8ef744d3a2be7213b2f47073\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按目标标识和当前状态执行购物车移除，返回处理结果；使用请求参数 tid；涉及 data_market_shopping_cart_t 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：08.购物车移除
// 路由：POST /dws/market/shoppingCardRemove
// 业务说明：按目标标识和当前状态执行购物车移除，返回处理结果；使用请求参数 tid；涉及 data_market_shopping_cart_t 等业务表；执行数据库写入并返回处理结果。
import log

// ========== 获取请求参数 ==========
var tid = body?.tid
if (tid == null || tid == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'tid不能为空'
}

// ========== 业务逻辑 ==========
var now    = new Date()
// 从 MySQL 业务表读取当前有效数据。
var result = db.table('data_market_shopping_cart_t')
    .where().eq('tid', tid).eq('is_del', 0)
    .update({ is_del: 1, updated_time: now })

if (result == 0) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '购物车数据不存在'
}

log.info('移除购物车成功, tid: {}', tid)
// 返回当前业务分支的标准结果。
return true
