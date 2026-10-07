{
  "properties": {},
  "id": "2e20af8a3d664efe8830927b3edc7ccc",
  "script": null,
  "groupId": "d243e142e85d4a1ca63381f359831b8c",
  "name": "01.根据userId获取流程权限",
  "createTime": 1782955344583,
  "updateTime": 1784446270720,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/getPremissionById",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按会话中的人员和当前应用读取审批角色、机构、可处理权限；传入 appId 时改为查询该应用。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：查询当前人员的流程权限
// 业务说明：按会话中的人员和当前应用读取审批角色、机构、可处理权限；传入 appId 时改为查询该应用。
// 响应继续使用旧页面依赖的 id、roles、perms、orgId、orgIds 字段。
import cn.dev33.satoken.stp.StpUtil
import approvalRuntime

var userId = StpUtil.getLoginIdDefaultNull()
if (userId == null) { exit -1, '账号未登录或登录已过期' }

// 角色、机构和委托授权由同一个租户隔离的权限解析器计算，避免多处 SQL 算出不同结果。
var appId = body?.appId == null ? null : '' + body.appId
var principal = approvalRuntime.principalForApp(appId)
var organizationIds = principal.organizationIds == null ? [] : principal.organizationIds

return {
    id: '' + userId,
    roles: principal.roleCodes,
    perms: principal.permissionKeys,
    orgId: organizationIds.size() == 0 ? null : organizationIds[0],
    orgIds: organizationIds
}
