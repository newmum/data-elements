{
  "properties": {},
  "id": "c9342e84fe8a432082c56d6640e91049",
  "script": null,
  "groupId": "26a3fab0ca6147c19c07cd82345cc1fb",
  "name": "02.启动",
  "createTime": 1782955344640,
  "updateTime": 1784446270724,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/start",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"flowType\": \"catalogSubscribe\",\n    \"approveType\": \"catalogSubscribe\",\n    \"flowOrderId\": \"30253579e54d4061bd95c7af602e17e7\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "受理流程启动请求；资产登记/更新可按当前审批模式直接通过或跳过，其余请求进入已发布的 Warm-Flow 定义并建立首个任务。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：启动审批流程
// 业务说明：受理流程启动请求；资产登记/更新可按当前审批模式直接通过或跳过，其余请求进入已发布的 Warm-Flow 定义并建立首个任务。
import approvalFlow

if (body == null) { exit -1, '参数不能为空' }

// 流程引擎负责已发布定义校验、首节点入队以及当前人员和机构处理人解析。
// 资产审批的 AUTO/OFF 分支也在同一事务服务中处理，避免 Magic 和引擎各写一半状态。
return approvalFlow.start(body)
