{
  "properties": {},
  "id": "8f4c71e2ae344e69a59682e86aed2661",
  "script": null,
  "groupId": "b2bc4c16954544a69d4bcb4700dadce1",
  "name": "05.办理审批任务",
  "createTime": 1782955344620,
  "updateTime": 1784446270723,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/handle",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"taskId\": \"2066790044328628225\",\n    \"businessId\": \"order_000000000058\",\n    \"handleType\": \"PASS\",\n    \"message\": \"同意\",\n    \"nodeCode\": \"测试\",\n    \"approveType\": \"checkIn\",\n    \"suggestionId\": \"838059686ff448d9afca95e9ad7b5906\",\n    \"storageSuggestion\": \"1\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "校验审批动作并通过 Warm-Flow 办理或协作处理任务。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import approvalFlow

// 校验审批动作并通过 Warm-Flow 办理或协作处理任务。
if (body?.taskId == null || ('' + body.taskId).trim() == '') { exit -1, 'taskId不能为空' }
var action = body?.handleType == null ? '' : ('' + body.handleType).trim()
var allowed = ['PASS','UNPASS','REJECT','REVOKE','TRANSFER','DEPUTE',
               'ADD_SIGNATURE','REDUCTION_SIGNATURE','TAKE_BACK','PENDING']
if (!allowed.contains(action)) { exit -1, '不支持的审批操作: ' + action }

// 操作类型决定流程节点、回退状态和资产副作用，必须由引擎事务统一提交。
return approvalFlow.handle(body)
