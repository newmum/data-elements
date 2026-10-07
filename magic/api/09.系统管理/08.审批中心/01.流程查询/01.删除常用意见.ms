{
  "properties": {},
  "id": "88f84dd78cbb42899740f49437a41148",
  "script": null,
  "groupId": "208d0e4705354e9eba03d8d531396f82",
  "name": "01.删除常用意见",
  "createTime": 1782955344444,
  "updateTime": 1784446270672,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/deleteSuggestion",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"1\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按目标标识和当前状态执行删除常用意见，返回处理结果；使用请求参数 tid；涉及 flow_suggestion 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.删除常用意见
// 路由：POST /sym/approval/query/deleteSuggestion
// 业务说明：按目标标识和当前状态执行删除常用意见，返回处理结果；使用请求参数 tid；涉及 flow_suggestion 等业务表。
/*
 * 审批中心 - 删除常用意见
 * 本接口属于 /sym/approval 下的独立 Warm-Flow 基础能力。
 * 数据资产登记步骤不会自动调用或自动提交本接口。
 */
import cn.dev33.satoken.stp.StpUtil;

var userId = StpUtil.getLoginIdDefaultNull();
if (userId == null) {
    // 返回当前业务分支的标准结果。
    return []
}

// 解析请求体并为可选参数设置默认值。
db.table("flow_suggestion").where().eq("tid",body?.tid).update(
    {
        "is_del":"1"
    }
)
