{
  "properties": {},
  "id": "user_role_assignments_01",
  "script": null,
  "groupId": "1ac60b4ccbc142e89dc1bf575e674d20",
  "name": "09.用户角色查询",
  "path": "/roles",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"userId\": \"用户ID\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"roleIds\": []\n}",
  "description": "用户角色查询：POST /sym/user/roles。返回当前租户内指定用户的已关联角色 ID，用于自动勾选；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；使用请求参数 userId；涉及 rm_user_role_rela_t 等业务表。"
}
================================
// 接口：09.用户角色查询
// 路由：POST /sym/user/roles
// 业务说明：用户角色查询：POST /sym/user/roles。返回当前租户内指定用户的已关联角色 ID，用于自动勾选；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；使用请求参数 userId；涉及 rm_user_role_rela_t 等业务表。
import tenantAccounts
import cn.dev33.satoken.stp.StpUtil
import tenantRuntime


try {
    tenantAccounts.session()
} catch (e) {
    exit 401, '账号未登录或登录已过期'
}

// 读取请求中的业务标识与筛选条件，供下面的校验和查询使用。
var userId = body?.userId == null ? '' : (body.userId + '').trim()
if (userId == '') {
    exit 400, '用户ID不能为空'
}
// 业务租户从当前会话读取，不采用请求体中的租户标识。
if (tenantAccounts.find(tenantRuntime.id(), userId) == null) {
    exit 404, '用户不存在或不属于当前租户'
}

// 以下从 rm_user_role_rela_t 业务表读取或维护当前操作涉及的记录。
var relations = db.table('rm_user_role_rela_t').where().eq('user_id', userId).select()
var roleIds = []
for (relation in (relations || [])) {
    var roleId = relation.roleId == null ? relation['role_id'] : relation.roleId
    if (roleId != null && (roleId + '').trim() != '') {
        roleIds.add(roleId + '')
    }
}
return { roleIds: roleIds }
