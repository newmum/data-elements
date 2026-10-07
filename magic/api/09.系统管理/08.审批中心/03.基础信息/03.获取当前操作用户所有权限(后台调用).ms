{
  "properties": {},
  "id": "b1dde9e625ac4f388dbd905b1aecbee2",
  "script": null,
  "groupId": "d243e142e85d4a1ca63381f359831b8c",
  "name": "03.获取当前操作用户所有权限(后台调用)",
  "createTime": 1782955344612,
  "updateTime": 1784446270722,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "permissions",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "返回当前应用下本人、所属角色/机构及有效委托的权限键；未登录时返回空列表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：查询当前人员的流程权限键
// 业务说明：返回当前应用下本人、所属角色/机构及有效委托的权限键；未登录时返回空列表。
import cn.dev33.satoken.stp.StpUtil
import approvalRuntime

if (StpUtil.getLoginIdDefaultNull() == null) { return [] }
return approvalRuntime.permissions()
