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
  "requestBody": "{\n    \"pageNum\": 1,\n    \"pageSize\": 20,\n    \"query\": [\n        {\n            \"term\": {\n                \"publishStatus\": 1\n            }\n        },\n        {\n            \"term\": {\n                \"assetType\": \"catalog\"\n            }\n        }\n    ],\n    \"sortField\": \"createdTime\",\n    \"sortDir\": \"desc\",\n    \"indexType\": \"app\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 500,\n    \"msg\": \"org.ssssssss.script.exception.MagicScriptException: method [POST], host [http://192.168.175.86:19200], URI [/elements_sea_fishery_dataassets/_search], status line [HTTP/1.1 404 Not Found]\\n{\\\"error\\\":{\\\"root_cause\\\":[{\\\"type\\\":\\\"index_not_found_exception\\\",\\\"reason\\\":\\\"no such index [elements_sea_fishery_dataassets]\\\",\\\"resource.type\\\":\\\"index_or_alias\\\",\\\"resource.id\\\":\\\"elements_sea_fishery_dataassets\\\",\\\"index_uuid\\\":\\\"_na_\\\",\\\"index\\\":\\\"elements_sea_fishery_dataassets\\\"}],\\\"type\\\":\\\"index_not_found_exception\\\",\\\"reason\\\":\\\"no such index [elements_sea_fishery_dataassets]\\\",\\\"resource.type\\\":\\\"index_or_alias\\\",\\\"resource.id\\\":\\\"elements_sea_fishery_dataassets\\\",\\\"index_uuid\\\":\\\"_na_\\\",\\\"index\\\":\\\"elements_sea_fishery_dataassets\\\"},\\\"status\\\":404} at Row:39~39,Col:49~59\\n\\nvar searchlist = tenantEs.index(indexType).search(dsl);\\r\\n                                                ^^^^^^^^^^^  \",\n    \"success\": false,\n    \"data\": null\n}",
  "description": "返回目录列表，及申请状态、收藏状态、关联资源类型；涉及 data_market_collect_t、data_market_shopping_cart_t、data_apply_form_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：14.超市列表
// 路由：POST /dws/market/list
// 业务说明：返回目录列表，及申请状态、收藏状态、关联资源类型；涉及 data_market_collect_t、data_market_shopping_cart_t、data_apply_form_t 等业务表。
// 返回目录列表，及申请状态、收藏状态、关联资源类型
import jsons
import tenantEs;
import tenantRuntime;
import log;
import "@post:/sym/user/me" as me
import '@/common/configData' as configData


// 解析请求体并为可选参数设置默认值。
var {pageNum, pageSize, query, indexType, sortField, sortDir } = body;
var tenantId = tenantRuntime.id();
//indexType = indexType || "elements_sea_fishery_dataassets"

indexType = indexType || configData().es_dataassets

var dsl = {
    query: {
        bool: {
            must: query
        }
    },
};

if (pageNum !== null && pageSize !== null){
    var requestedPage = pageNum::int;
    var requestedSize = pageSize::int;
    if (requestedPage < 1 || requestedSize < 1 || requestedSize > 100) {
        exit 400, '分页范围不合法，单页最多100条';
    }
    dsl["from"] = (requestedPage - 1) * requestedSize;
    dsl["size"] = requestedSize;
}

if (sortField !== null){
    dsl["sort"] = [
        {[sortField]: {order: sortDir}}
    ]
}

// 直接查询租户物理索引，避免额外的索引检查请求和另一套客户端连接。
// 新租户尚未建立索引时继续返回空列表，其余查询错误向上报告。
var searchlist = null;
try {
    searchlist = tenantEs.index(indexType).search(dsl);
} catch (error) {
    var errorMessage = error?.message == null ? '' : error.message + '';
    if (errorMessage.contains('index_not_found_exception')) {
        return { total: 0, list: [] };
    }
    throw error;
}
var list = searchlist.hits.hits.map(e=>e._source)


// ====== 获取当前用户信息 ======
var userInfo = me()
var applyUserId  = userInfo.id
var applyUserName  = userInfo.userName
var applyOrgId  = userInfo.orgId
var ids = list.map(item=>item.tid)

// ====== 获取收藏目录id ======
var collects = db.table('data_market_collect_t')
    .where()
    .eq('tenant_id', tenantId)
    .eq('apply_org_id', applyOrgId)
    .in('catalog_id', ids)
    .select()

var collectIds = collects.map(item=>item.catalogId)

// ====== 获取购物车目录id ======
var cartItems = db.table('data_market_shopping_cart_t')
    .where()
    .eq('tenant_id', tenantId)
    .eq('apply_org_id', applyOrgId)
    .eq('is_del', 0)
    .in('catalog_id', ids)
    .select()

var cartIds = cartItems.map(item=>item.catalogId)

// ====== 获取申请单状态（只取有效状态 0/1/2/5）======
var applyForms = db.table('data_apply_form_t')
    .where()
    .eq('tenant_id', tenantId)
    .eq('apply_org_id', applyOrgId)
    .in('flow_status', [0, 1, 2, 5, 4])
    .eq('is_del', 0)
    .select()

// 优先级： 2（已申请）> 1（申请中） > 5(已驳回) > 0（草稿/待申请）
var PRIORITY = {"2": 3, "1": 2, "5": 1, "0": 0}
var applyStatusMap = {}

applyForms.each(form => {
    var catalogIdList = form.catalogIds.split(',')
    catalogIdList.each(cid => {
        if (ids.contains(cid)) {
            var newStatus = form.flowStatus
            var existing = applyStatusMap[cid]
            applyStatusMap[cid] = newStatus
            // if (existing === undefined || PRIORITY[newStatus] > PRIORITY[existing]) {
            //     applyStatusMap[cid] = newStatus
            // }
        }
    })
})

list = list.map(item => {
    var obj = item
    // 设置目录的关联资源
    if (obj.assetType == "catalog"){
        var res = []
        !!obj.targetTableId && res.push("table")
        !!obj.apiId && res.push("api")
        !!obj.fileId && res.push("file")
        !!obj.dirId && res.push("dir")
        obj["resType"] = res
    }

    // 设置初始浏览量
    obj["viewCount"] = obj["viewCount"] || 0

    // 设置收藏状态
    obj["collected"] = collectIds.contains(obj.tid)

    // 设置申请状态
    var applyStatus = applyStatusMap[obj.tid]
    if (applyStatus !== undefined) {
        obj["applyStatus"] = applyStatus == 4 ? 1 : applyStatus       // 0=待提交, 1=审核中, 2=已申请
    } else if (cartIds.contains(obj.tid)) {
        obj["applyStatus"] = "cart"             // 已加购
    } else {
        obj["applyStatus"] = null               // 默认，可申请
    }
    // 返回当前业务分支的标准结果。
    return obj
})


// 返回当前业务分支的标准结果。
return {
    total: searchlist.hits.total.value,
    list
};
