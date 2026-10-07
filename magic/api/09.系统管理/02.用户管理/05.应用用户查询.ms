{
  "properties": {},
  "id": "438022734caa445288081ecc7bcf247b",
  "script": null,
  "groupId": "1ac60b4ccbc142e89dc1bf575e674d20",
  "name": "05.应用用户查询",
  "path": "/user/application-list",
  "method": "GET",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "兼容用户查询接口；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.应用用户查询
// 路由：GET /sym/user/user/application-list
// 业务说明：兼容用户查询接口；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制。
import tenantAccounts
import cn.dev33.satoken.stp.StpUtil
import tenantRuntime


try { tenantAccounts.session() } catch (e) { exit -1, '账号未登录或登录已过期' }
return tenantAccounts.list(tenantRuntime.id(), null)
