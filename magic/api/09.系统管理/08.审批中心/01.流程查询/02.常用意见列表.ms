{
  "properties": {},
  "id": "f8a5b9d39f3a43dabb3ddcbd0c1a2aed",
  "script": null,
  "groupId": "208d0e4705354e9eba03d8d531396f82",
  "name": "02.常用意见列表",
  "createTime": 1782955344452,
  "updateTime": 1784446270673,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/suggestionList",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按当前登录人员读取审批中心常用意见，返回可复用的意见清单。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.常用意见列表
// 路由：GET /sym/approval/query/suggestionList
// 业务说明：按当前登录人员读取审批中心常用意见，返回可复用的意见清单。
/*
 * 审批中心 - 常用意见列表
 * 本接口属于 /sym/approval 下的独立 Warm-Flow 基础能力。
 * 数据资产登记步骤不会自动调用或自动提交本接口。
 */
import cn.dev33.satoken.stp.StpUtil;

var userId = StpUtil.getLoginId() + ""
if (userId == null) {
    // 返回当前业务分支的标准结果。
    return []
}

// 从 MySQL 业务表读取当前有效数据。
return db.table("flow_suggestion")
    .columns("tid", "content", "use_num", "create_time", "create_by", "update_time", "update_by", "tenant_id")
    .where()
    .eq("is_del", 0)
    .eq("create_by", userId)
    .orderBy("use_num", "desc")
    .select()
