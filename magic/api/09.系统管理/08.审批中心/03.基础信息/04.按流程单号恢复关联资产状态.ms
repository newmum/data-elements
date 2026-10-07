{
  "properties": {},
  "id": "approval_restore_asset_status_01",
  "script": null,
  "groupId": "d243e142e85d4a1ca63381f359831b8c",
  "name": "04.按流程单号恢复关联资产状态",
  "path": "/restoreAssetStatusByFlowOrderId",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"flowOrderId\": \"流程单号\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "定位流程单关联资产，将登记或更新前的资产状态恢复并同步检索索引；只在明确调用时执行。"
}
================================
// 接口：按流程单号恢复资产状态
// 业务说明：定位流程单关联资产，将登记或更新前的资产状态恢复并同步检索索引；只在明确调用时执行。
import approvalFlow

if (body?.flowOrderId == null || ('' + body.flowOrderId).trim() == '') {
    exit -1, 'flowOrderId不能为空'
}

// 资产持久化和 Elasticsearch 更新需要跨系统的补偿/事务逻辑，由框架服务执行。
return approvalFlow.restoreAssetStatusByFlowOrderId(body)
