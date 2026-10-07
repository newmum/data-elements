{
  "properties": {},
  "id": "8f4c71e2ae344e69a59682e86aed2661",
  "script": null,
  "groupId": "26a3fab0ca6147c19c07cd82345cc1fb",
  "name": "01.办理",
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
  "description": "校验任务标识和办理动作；普通动作完成任务，协作动作转办/委派/加签，由 Warm-Flow 原子写入节点、历史及资产状态。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：办理审批任务
// 业务说明：校验任务标识和办理动作；普通动作完成任务，协作动作转办/委派/加签，由 Warm-Flow 原子写入节点、历史及资产状态。
import approvalFlow

if (body?.taskId == null || ('' + body.taskId).trim() == '') { exit -1, 'taskId不能为空' }
var action = body?.handleType == null ? '' : ('' + body.handleType).trim()
var allowed = ['PASS','UNPASS','REJECT','REVOKE','TRANSFER','DEPUTE',
               'ADD_SIGNATURE','REDUCTION_SIGNATURE','TAKE_BACK','PENDING']
if (!allowed.contains(action)) { exit -1, '不支持的审批操作: ' + action }

// 操作类型决定流程节点、回退状态和资产副作用，必须由引擎事务统一提交。
return approvalFlow.handle(body)
