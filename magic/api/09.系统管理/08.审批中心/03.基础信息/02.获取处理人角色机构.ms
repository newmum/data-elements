{
  "properties": {},
  "id": "52baada3264d4eb28ff0fc7bbc43c960",
  "script": null,
  "groupId": "d243e142e85d4a1ca63381f359831b8c",
  "name": "02.获取处理人角色机构",
  "createTime": 1782955344593,
  "updateTime": 1784446270721,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/getAssignment",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按登录人员与请求中的应用标识查询审批所需的角色、机构和权限键；应用未指定时采用会话应用。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：获取当前人员的处理权限资料
// 业务说明：按登录人员与请求中的应用标识查询审批所需的角色、机构和权限键；应用未指定时采用会话应用。
import cn.dev33.satoken.stp.StpUtil
import approvalRuntime

if (StpUtil.getLoginIdDefaultNull() == null) { exit -1, '账号未登录或登录已过期' }

var appId = body?.appId == null ? null : '' + body.appId
return approvalRuntime.principalForApp(appId)
