{
  "properties": {},
  "id": "007841d72ac04f8589d817ad04436d7c",
  "script": null,
  "groupId": "1ac60b4ccbc142e89dc1bf575e674d20",
  "name": "07.用户兼容分页",
  "createTime": null,
  "updateTime": 1787153759480,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/user/compat-page",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "兼容用户查询接口；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；涉及 rm_user_org_rela_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：07.用户兼容分页
// 路由：GET /sym/user/user/compat-page
// 业务说明：兼容用户查询接口；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；涉及 rm_user_org_rela_t 等业务表。
import tenantAccounts
import cn.dev33.satoken.stp.StpUtil
import tenantRuntime


try { tenantAccounts.session() } catch (e) { exit -1, '账号未登录或登录已过期' }

var tenantId = tenantRuntime.id()
var pageSize = (limit || 10).asInt()
var offsetValue = (offset || 0).asInt()
var filters = { pageNum: (offsetValue / pageSize).asInt() + 1, pageSize: pageSize }
if (ids != null && ids != '') { filters.ids = ids.split(',') }
if (userName != null && userName != '') { filters.userName = userName }
if (realName != null && realName != '') { filters.realName = realName }

// Organization links are tenant-owned; account lookup is delegated to tenantAccounts.
if (orgId != null && orgId != '') {
    var relations = db.table('rm_user_org_rela_t').where().eq('org_id', orgId).select()
    filters.ids = relations == null ? [] : relations.map(item => item.userId)
}
var result = tenantAccounts.page(tenantId, filters.pageNum, filters.pageSize, filters.ids,
    filters.userName, filters.realName)
return result.list || []
