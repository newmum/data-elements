{
  "properties": {},
  "id": "aa7ece2e7d394aea8e6d84b8c5ab1fb6",
  "script": null,
  "groupId": "b2bc4c16954544a69d4bcb4700dadce1",
  "name": "06.查询可退回节点",
  "createTime": 1782955344669,
  "updateTime": 1784446270727,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/previous",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"definitionId\": \"2034456697161957377\",\n    \"nodeCode\": \"d1ded9a6-c783-42dc-b4b1-55e8344cbd93\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "读取 Warm-Flow 允许退回的前驱节点。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import approvalFlow

// 读取 Warm-Flow 允许退回的前驱节点。
if (body?.definitionId == null || body?.nodeCode == null) {
    exit -1, 'definitionId和nodeCode不能为空'
}
return approvalFlow.previousNodeList(body)
