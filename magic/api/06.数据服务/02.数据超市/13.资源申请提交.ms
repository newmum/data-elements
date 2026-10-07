{
  "properties": {},
  "id": "241456b9741748059d853cdbb4e12461",
  "script": null,
  "groupId": "dataservice_resource_group_01",
  "name": "13.资源申请提交",
  "createTime": 1782955344174,
  "updateTime": 1784437771774,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/submit",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"approveType\": \"catalogSubscribe\",\n    \"flowOrderId\": \"30253579e54d4061bd95c7af602e17e7\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {}\n}",
  "description": "按请求目标与业务状态执行资源申请提交，返回本次操作结果；使用请求参数 flowOrderId；涉及 data_apply_form_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：13.资源申请提交
// 路由：POST /dws/market/submit
// 业务说明：按请求目标与业务状态执行资源申请提交，返回本次操作结果；使用请求参数 flowOrderId；涉及 data_apply_form_t 等业务表。
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import jsons
import log

/*
 * 数据服务申请直接生效。
 *
 * 平台已取消审批流程，因此本接口不再创建流程实例，也不依赖审批中心。
 * 申请单直接更新为已通过，并同步申请单 ES 文档，保留原 /submit 路径供页面兼容调用。
 */
var flowOrderId = body?.flowOrderId
if (flowOrderId == null || (flowOrderId + '').trim() == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, '申请单号flowOrderId不能为空'
}

// 从 MySQL 业务表读取当前有效数据。
var form = db.table('data_apply_form_t').where()
    .eq('tid', flowOrderId)
    .eq('is_del', 0)
    .selectOne()
if (form == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 404, '申请单不存在'
}

// 将校验后的业务数据写入 MySQL 主表。
db.table('data_apply_form_t').where().eq('tid', flowOrderId).update({
    flow_status: 2,
    updated_time: new Date()
})

try {
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    var indexed = esCommonService.getDocument('dataassets_apply_form', flowOrderId)
    var document = indexed == null ? {} : indexed
    document.id = flowOrderId
    document.tid = flowOrderId
    document.flowStatus = 2
    document.updatedTime = new Date()
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    esCommonService.saveJsonAutoMapping(
        'dataassets_apply_form',
        flowOrderId,
        jsons.toJSONStr(document)
    )
} catch (e) {
    log.warn('同步数据服务申请单ES失败，flowOrderId: {}, error: {}', flowOrderId, e.message)
}

// 返回当前业务分支的标准结果。
return {
    flowOrderId: flowOrderId,
    flowStatus: 2,
    directGranted: true
}
