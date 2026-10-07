{
  "properties": {},
  "id": "c30f3718868148ce84e65217f49a7720",
  "script": null,
  "groupId": "9f9e437fd903445f804353ea851c2fe5",
  "name": "01.回调处理(节点处理结束回调)",
  "createTime": 1782955344570,
  "updateTime": 1784446270719,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/finshHandle",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "节点完成时按办理结果更新业务单与关联资产状态，并通知外部检索索引；回调可能由 Warm-Flow 内部触发。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：审批节点完成回调
// 业务说明：节点完成时按办理结果更新业务单与关联资产状态，并通知外部检索索引；回调可能由 Warm-Flow 内部触发。
import approvalFlow

if (body?.businessId == null || ('' + body.businessId).trim() == '') {
    exit 500, 'businessId不能为空'
}

// 业务状态和索引更新与引擎回调耦合，继续由 Java 事务/外部客户端完成。
return approvalFlow.finshHandle(body)
