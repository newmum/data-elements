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
  "requestBody": "{\n    \"tid\": \"7d66f7d13848469c9964f975b884608e\",\n    \"addToCart\": true\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": true\n}",
  "description": "逻辑删除主表 data_apply_form_t、子表 da_prop_t、关联表 data_apply_form_catalog_rela；使用请求参数 tid、addToCart。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：11.资产申请单按id删除
// 路由：POST /dws/market/applyForm/deleteById
// 业务说明：逻辑删除主表 data_apply_form_t、子表 da_prop_t、关联表 data_apply_form_catalog_rela；使用请求参数 tid、addToCart。
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import log
import "@post:/dws/market/shoppingCard" as addCar;

/**
 * 删除申请表单
 * POST /api/apply/form/delete
 *
 * 逻辑删除主表 data_apply_form_t、子表 da_prop_t、关联表 data_apply_form_catalog_rela
 * 同步删除 ES 索引 dataassets_apply_form
 */

// ========== 获取请求参数 ==========
var tid = body?.tid
if (tid == null || tid == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'tid不能为空'
}

var addToCart = body?.addToCart == true  // 是否重新加购
var now = new Date()

// ========== 0. 若需加购，先查出关联目录 ID ==========
var toAddIds
if (addToCart) {
    // 从 MySQL 业务表读取当前有效数据。
    var applyData = db.table('data_apply_form_t')
        .where().eq('tid', tid).eq('is_del', 0)
        .selectOne()
    if (!applyData){
        // 参数或业务状态不满足要求时立即终止请求。
        exit 500, '申请单不存在'
    }
    var catalogIds = applyData?.catalogIds?.split(",")

    if (catalogIds) {
        // 查当前用户购物车中已存在的 catalog_id，避免重复加购
        var existingIds = db.table('data_market_shopping_cart_t')
            .where().eq('apply_org_id', applyData.applyOrgId)
            .eq('is_del', 0)
            .in('catalog_id', catalogIds)
            .select()
            .map(it => it.catalogId)

        toAddIds = catalogIds.filter(id => !existingIds.contains(id))
    }
}

// ========== 1. 逻辑删除关联表 data_apply_form_catalog_rela ==========
var relaResult = db.table('data_apply_form_catalog_rela')
    .where().eq('apply_form_id', tid)
    .eq('is_del', 0)
    .update({ is_del: 1, updated_time: now })
log.info('逻辑删除关联表 data_apply_form_catalog_rela, apply_form_id: {}, 影响行数: {}', tid, relaResult)

// ========== 2. 逻辑删除动态属性 da_prop_t ==========
var propResult = db.table('da_prop_t')
    .where().eq('parent_id', tid).eq('is_del', 0)
    .update({ is_del: 1, updated_time: now })
log.info('逻辑删除子表 da_prop_t, parent_id: {}, 影响行数: {}', tid, propResult)

// ========== 3. 逻辑删除主表 data_apply_form_t ==========
var mainResult = db.table('data_apply_form_t')
    .where().eq('tid', tid).eq('is_del', 0)
    .update({ is_del: 1, updated_time: now })

if (mainResult == 0) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '申请表单不存在或已删除'
}
log.info('逻辑删除主表 data_apply_form_t 成功, tid: {}', tid)

// ========== 4. 删除 ES 索引 ==========
esCommonService.deleteById('dataassets_apply_form', tid)
log.info('删除ES索引成功, tid: {}', tid)

if (addToCart && toAddIds){
    for (id in toAddIds) {
        body = {catalogId: id}
        addCar();
    }
    log.info('重新加购目录到购物车, apply_form_id: {}, 加购数量: {}', tid, toAddIds.size())
}

// 返回当前业务分支的标准结果。
return true
