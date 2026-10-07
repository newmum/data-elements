{
  "properties": {},
  "id": "ee2487476fc7433c86de3b1ef8bbdba5",
  "script": null,
  "groupId": "26a3fab0ca6147c19c07cd82345cc1fb",
  "name": "03.撤销",
  "createTime": 1782955344652,
  "updateTime": 1784446270725,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/revoke",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"3521505afe654916a146a1135a4a1203\",\n    \"flowOrderId\": \"order_000000000603\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "根据资产记录和流程单号找到未结束的审批实例及首任务，执行 REVOKE 并同步关联资产状态。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：撤销审批流程
// 业务说明：根据资产记录和流程单号找到未结束的审批实例及首任务，执行 REVOKE 并同步关联资产状态。
import approvalFlow

if (body?.tid == null || ('' + body.tid).trim() == '') { exit -1, 'tid不能为空' }
if (body?.flowOrderId == null || ('' + body.flowOrderId).trim() == '') {
    exit -1, 'flowOrderId不能为空'
}

// 关联资产、实例和任务读取及撤销动作需要保持在同一流程服务边界内。
return approvalFlow.revoke(body)
