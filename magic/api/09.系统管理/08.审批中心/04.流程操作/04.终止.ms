{
  "properties": {},
  "id": "d1a2e76b55c84370ab153b304a97b82d",
  "script": null,
  "groupId": "26a3fab0ca6147c19c07cd82345cc1fb",
  "name": "04.终止",
  "createTime": 1782955344660,
  "updateTime": 1784446270726,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/termination",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"flow_order_id\": \"xxxx\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "核验流程实例标识，交由 Warm-Flow 终止实例并记录当前操作人和终止说明。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：终止审批流程
// 业务说明：核验流程实例标识，交由 Warm-Flow 终止实例并记录当前操作人和终止说明。
import approvalFlow

if (body?.instanceId == null || ('' + body.instanceId).trim() == '') {
    exit -1, 'instanceId不能为空'
}
return approvalFlow.termination(body)
