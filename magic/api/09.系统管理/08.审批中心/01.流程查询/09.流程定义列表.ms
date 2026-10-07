{
  "properties": {},
  "id": "6c3ab97a62ce4aec8b9fb91d17703726",
  "script": null,
  "groupId": "208d0e4705354e9eba03d8d531396f82",
  "name": "09.流程定义列表",
  "createTime": 1782955344534,
  "updateTime": 1784446270714,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/definitionList",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "读取审批中心可用的流程定义，返回发起流程时可选择的定义清单。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：09.流程定义列表
// 路由：GET /sym/approval/query/definitionList
// 业务说明：读取审批中心可用的流程定义，返回发起流程时可选择的定义清单。
/*
 * 审批中心 - 流程定义列表
 * 本接口属于 /sym/approval 下的独立 Warm-Flow 基础能力。
 * 数据资产登记步骤不会自动调用或自动提交本接口。
 */
import log

// 从 MySQL 业务表读取当前有效数据。
var list = db.table("flow_definition").where().eq("is_publish", "1").select()

// 返回当前业务分支的标准结果。
return list.map(el => ({
    ...el,
    id: el.id + ""
}))
