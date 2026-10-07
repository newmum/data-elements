{
  "properties": {},
  "id": "6826075b84dd4f2fb768993514383911",
  "script": null,
  "groupId": "208d0e4705354e9eba03d8d531396f82",
  "name": "10.统计流程统计",
  "createTime": 1782955344545,
  "updateTime": 1784446270716,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/flowCount",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按流程实例状态统计审批流程数量，返回审批中心展示指标。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：10.统计流程统计
// 路由：GET /sym/approval/query/flowCount
// 业务说明：按流程实例状态统计审批流程数量，返回审批中心展示指标。
/*
 * 审批中心 - 统计流程统计
 * 本接口属于 /sym/approval 下的独立 Warm-Flow 基础能力。
 * 数据资产登记步骤不会自动调用或自动提交本接口。
 */
list = db.table("flow_instance").where().ne("flow_status",8).eq('business_id',"order_000000000055").select()

// 返回当前业务分支的标准结果。
return  list[0]
