{
  "properties": {},
  "id": "9b5e2f59c4f04b8c900f81f178c27458",
  "groupId": "ccb8f94e621a4feaa18362a325890270",
  "name": "04.策略预检",
  "path": "/precheck",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"policyId\":\"策略ID\"}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/reconciliation/policy/precheck；读写；检查源目标连接、表字段和唯一索引并回写预检结果；策略启用前置接口。",
  "createTime": 1785340800000,
  "updateTime": 1785340800000,
  "script": null
}
================================
// 接口：04.策略预检
// 路由：POST /precheck（完整路由由目录分组组合）
// 业务说明：POST /ods/reconciliation/policy/precheck；读写；检查源目标连接、表字段和唯一索引并回写预检结果；策略启用前置接口。

import tenantRuntime
import reconcile

// 预检跨源、目标两个数据库，只检查连接、主键、字段及能力，不扫描全表。
var tenantId = tenantRuntime.id()
var policyId = body?.policyId == null ? '' : ('' + body.policyId).trim()
if (policyId == '') { exit 400, '策略ID不能为空' }
var result = reconcile.inspectPolicy(policyId)

// 把原始预检结果保存到策略，启用接口再据此判定是否允许启用。
reconcile.persistPrecheck(policyId, result)
return result
