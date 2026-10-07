{
  "properties": {},
  "id": "b78cf957a41645ccbea4fda1f6c6b9e9",
  "script": null,
  "groupId": "dataservice_resource_group_01",
  "name": "03.收藏接口",
  "createTime": 1782955344081,
  "updateTime": 1787153757069,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/collect",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"catalogId\": \"11\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"id\": \"1995681734511165441\",\n        \"realName\": \"叶华\",\n        \"userName\": \"zn\",\n        \"phone\": \"13222222222\",\n        \"gender\": \"2\",\n        \"orgId\": \"2026503859392086017\",\n        \"orgName\": \"福建省公安\",\n        \"orgRootId\": \"2026503859392086017\",\n        \"serialNumber\": \"0006\",\n        \"orgPath\": \"福建省公安\",\n        \"roles\": [\n            \"admin\"\n        ],\n        \"perms\": [\n            \"sym:asset:add\"\n        ]\n    }\n}",
  "description": "按当前登录人员和目录标识检查已有收藏，再新增或取消收藏记录并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.收藏接口
// 路由：POST /dws/market/collect
// 业务说明：按当前登录人员和目录标识检查已有收藏，再新增或取消收藏记录并返回处理结果。
import cn.hutool.core.util.IdUtil
import log
import "@post:/sym/user/me" as me

// ========== 获取请求参数 ==========
var catalogId      = body?.catalogId

var userInfo = me()

var applyUserId    = userInfo.id
var applyUserName  = userInfo.userName
var applyOrgId  = userInfo.orgId

// ========== 参数校验 ==========
if (catalogId == null || catalogId == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'catalog_id不能为空'
}

// ========== 查询是否已收藏 ==========
var existing = db.table('data_market_collect_t')
    .where()
    .eq('catalog_id', catalogId)
    .eq('apply_org_id', applyOrgId)
    .selectOne()

if (existing != null) {
    // 已收藏 -> 取消收藏（物理删除）
    db.table('data_market_collect_t')
        .where()
        .eq('tid', existing.tid)
        .delete()
    log.info('取消收藏成功, catalog_id: {}, apply_org_id: {}', catalogId, applyOrgId)
    // 返回当前业务分支的标准结果。
    return false
} else {
    // 未收藏 -> 新增收藏
    var tid = IdUtil.getSnowflakeNextIdStr()
    // 将校验后的业务数据写入 MySQL 主表。
    db.table('data_market_collect_t').primary('tid').insert({
        tid: tid,
        catalog_id: catalogId,
        apply_user_id: applyUserId,
        apply_user_name: applyUserName,
        apply_org_id: applyOrgId,
        org_tree_path: userInfo.serialNumber
    })
    log.info('收藏成功, tid: {}, catalog_id: {}, apply_user_id: {}', tid, catalogId, applyUserId)
    // 返回当前业务分支的标准结果。
    return true
}
