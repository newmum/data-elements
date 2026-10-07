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
  "requestBody": "{\n    \"catalogId\": \"0d50a16cf9b5458398a22f6fa158ea57\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 500,\n    \"msg\": \"该资产已在购物车中\",\n    \"success\": false,\n    \"data\": null\n}",
  "description": "校验请求资料后执行购物车(保存或修改)，返回保存或配置结果；使用请求参数 tid、catalogId；涉及 data_market_shopping_cart_t、data_apply_form_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.购物车(保存或修改)
// 路由：POST /dws/market/shoppingCard
// 业务说明：校验请求资料后执行购物车(保存或修改)，返回保存或配置结果；使用请求参数 tid、catalogId；涉及 data_market_shopping_cart_t、data_apply_form_t 等业务表。
// 加购条件：当前用户未申请 未加购的目录
import cn.hutool.core.util.IdUtil
import log
import "@post:/sym/user/me" as me
import tenantRuntime
// ========== 获取请求参数 ==========
var tid           = body?.tid
var catalogId     = body?.catalogId

var userInfo = me()
var applyUserId    = userInfo.id
var applyUserName  = userInfo.userName
var applyOrgId  = userInfo.orgId

var tenantId      = tenantRuntime.id()
var isUpdate      = tid != null && tid != ''
var now           = new Date()

// ========== 参数校验 ==========
if (catalogId == null || catalogId == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'catalog_id不能为空'
}


// ========== 业务逻辑 ==========
if (isUpdate) {
    // 将校验后的业务数据写入 MySQL 主表。
    db.table('data_market_shopping_cart_t').primary('tid').update({
        catalog_id:      catalogId,
        apply_user_id:   applyUserId,
        apply_org_id:    applyOrgId,
        apply_user_name: applyUserName,
        updated_by:      applyUserId,
        updated_time:    now
    })
    log.info('更新购物车成功, tid: {}', tid)
} else {
    // 同一部门对同一资产只允许加入一次
    var existing = db.table('data_market_shopping_cart_t')
        .where()
        .eq('catalog_id', catalogId)
        .eq('apply_org_id', applyOrgId)
        .eq('is_del', 0)
        .selectOne()
    if (existing != null) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 500, '该资产已在购物车中'
    }

     // 校验该目录是否已存在有效申请单（草稿/审批中/已通过）
    var appliedForm = db.table('data_apply_form_t')
                        .where()
                        .eq('apply_org_id', applyOrgId)
                        .like('catalog_ids', `%${catalogId}%`)
                        .in('flow_status', [0,1,2])
                        .eq('is_del', 0)
                        .select();
    if (appliedForm != null && appliedForm.size() > 0) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 500, '该目录已申请，不予加购'
    }

    tid = IdUtil.getSnowflakeNextIdStr()
    // 将校验后的业务数据写入 MySQL 主表。
    db.table('data_market_shopping_cart_t').primary('tid').insert({
        tid:             tid,
        catalog_id:      catalogId,
        apply_user_id:   applyUserId,
        apply_org_id:   applyOrgId,
        apply_user_name: applyUserName,
        tenant_id:       tenantId,
        created_by:      applyUserId,
        created_time:    now,
        updated_by:      applyUserId,
        updated_time:    now,
        is_del:          0
    })
    log.info('新增购物车成功, tid: {}', tid)
}

// 返回当前业务分支的标准结果。
return tid
