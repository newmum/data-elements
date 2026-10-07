{
  "properties": {},
  "id": "aa7ece2e7d394aea8e6d84b8c5ab1fb6",
  "script": null,
  "groupId": "26a3fab0ca6147c19c07cd82345cc1fb",
  "name": "05.获取前驱节点(驳回使用)",
  "createTime": 1782955344669,
  "updateTime": 1784446270727,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "previousNodeList",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"definitionId\": \"2034456697161957377\",\n    \"nodeCode\": \"d1ded9a6-c783-42dc-b4b1-55e8344cbd93\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "根据流程定义与当前节点，读取 Warm-Flow 允许退回的节点集合；无可用节点时明确报错。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：查询可退回的前驱节点
// 业务说明：根据流程定义与当前节点，读取 Warm-Flow 允许退回的节点集合；无可用节点时明确报错。
import approvalFlow

if (body?.definitionId == null || body?.nodeCode == null) {
    exit -1, 'definitionId和nodeCode不能为空'
}
return approvalFlow.previousNodeList(body)
