{
  "properties": {
    "localAccountRetirement": "v1"
  },
  "id": "f75db35437b546f8a8c8e4eda6740e33",
  "script": null,
  "groupId": "1ac60b4ccbc142e89dc1bf575e674d20",
  "name": "02.用户分页",
  "createTime": 1782660716553,
  "updateTime": 1787153759518,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"pageNum\": 1,\n  \"pageSize\": 20\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"list\": [],\n  \"total\": 0\n}",
  "description": "应用独立本地账号接口；业务规则由租户账号 Magic 函数执行。共享控制库账号及成员关系分支已退役；使用请求参数 orgId；涉及 rm_user_org_rela_t、rm_user_role_rela_t、rm_role_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.用户分页
// 路由：POST /sym/user/page
// 业务说明：应用独立本地账号接口；业务规则由租户账号 Magic 函数执行。共享控制库账号及成员关系分支已退役；使用请求参数 orgId；涉及 rm_user_org_rela_t、rm_user_role_rela_t、rm_role_t 等业务表。

import '@/tenant-account/users' as localUsers
import tenantAccounts
import cn.dev33.satoken.stp.StpUtil
import tenantRuntime
try {
    tenantAccounts.session()
} catch (e) {
    exit -1, "账号未登录或登录已过期"
}

var filters = body || {}

// 组织关系属于租户库，先换算成允许查询的本地用户ID。
if (body?.orgId != null && (body.orgId + '').trim() != '') {
    var orgUsers = db.table("rm_user_org_rela_t").where()
        .eq("org_id", body.orgId)
        .select()
    filters.ids = orgUsers == null ? [] : orgUsers.map(item => item.userId)
}

var userPage = localUsers('page',filters)
if (userPage.list == null || userPage.list.size() == 0) {
    return userPage
}

var userIds = userPage.list.map(item => item.id)
var roleRelations = db.table("rm_user_role_rela_t").where()
    .in("user_id", userIds)
    .select()
var roleNames = {}
if (roleRelations != null && roleRelations.size() > 0) {
    var roleIds = roleRelations.map(item => item.roleId)
    var roles = db.table("rm_role_t").where()
        .in("id", roleIds)
        .eq("deleted", 0)
        .select()
    var namesById = {}
    for (role in roles) {
        namesById[role.id] = role.name
    }
    for (relation in roleRelations) {
        var roleName = namesById[relation.roleId]
        if (roleName != null && roleName != '') {
            roleNames[relation.userId] = roleNames[relation.userId] == null
                ? roleName
                : roleNames[relation.userId] + ',' + roleName
        }
    }
}

var orgRelations = db.table("rm_user_org_rela_t").where()
    .in("user_id", userIds)
    .select()
var orgIds = {}
if (orgRelations != null) {
    for (relation in orgRelations) {
        orgIds[relation.userId] = relation.orgId
    }
}

for (user in userPage.list) {
    user.roleNames = roleNames[user.id] || ''
    user.orgId = orgIds[user.id] || ''
}
return userPage
