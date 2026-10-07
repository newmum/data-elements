{
  "properties": {},
  "id": "user_role_save_01",
  "script": null,
  "groupId": "1ac60b4ccbc142e89dc1bf575e674d20",
  "name": "08.用户角色保存",
  "path": "/saveRoles",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"userId\": \"用户ID\",\n  \"roleIds\": [\"角色ID\"]\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"roleIds\": []\n}",
  "description": "用户角色保存：POST /sym/user/saveRoles。替换当前租户内用户的角色关联；只允许选择当前应用存在的角色；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；使用请求参数 userId、appId、roleIds；涉及 rm_role_t、rm_user_role_rela_t 等业务表。"
}
================================
// 接口：08.用户角色保存
// 路由：POST /sym/user/saveRoles
// 业务说明：用户角色保存：POST /sym/user/saveRoles。替换当前租户内用户的角色关联；只允许选择当前应用存在的角色；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；使用请求参数 userId、appId、roleIds；涉及 rm_role_t、rm_user_role_rela_t 等业务表。
import '@/tenant-account/roles' as localRoles
import tenantAccounts
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.core.util.IdUtil
import tenantRuntime
if(tenantAccounts.isLocal(tenantRuntime.id())) {return localRoles(body||{});}


try {
    tenantAccounts.session()
} catch (e) {
    exit 401, '账号未登录或登录已过期'
}

var userId = body?.userId == null ? '' : (body.userId + '').trim()
if (userId == '') {
    exit 400, '用户ID不能为空'
}
if (tenantAccounts.find(tenantRuntime.id(), userId) == null) {
    exit 404, '用户不存在或不属于当前租户'
}

var appId = body?.appId || tenantAccounts.session().get('appId')
if (appId == null || (appId + '').trim() == '') {
    exit 400, '未获取到当前应用'
}

var availableRoles = db.table('rm_role_t').where()
    .eq('app_id', appId)
    .eq('deleted', 0)
    .select()
var availableRoleIds = {}
for (role in (availableRoles || [])) {
    availableRoleIds[role.id + ''] = true
}

var roleIds = []
for (roleId in (body?.roleIds || [])) {
    var normalizedRoleId = roleId == null ? '' : (roleId + '').trim()
    if (normalizedRoleId == '') {
        continue
    }
    if (availableRoleIds[normalizedRoleId] != true) {
        exit 400, '所选角色不存在或不属于当前应用'
    }
    if (!roleIds.contains(normalizedRoleId)) {
        roleIds.add(normalizedRoleId)
    }
}

// 用户角色是当前租户业务关系；全量替换使取消勾选立即生效。
db.table('rm_user_role_rela_t').where().eq('user_id', userId).delete()
var now = new Date()
for (roleId in roleIds) {
    db.table('rm_user_role_rela_t').insert({
        id: IdUtil.getSnowflakeNextIdStr(),
        role_id: roleId,
        user_id: userId,
        auth_from: 0,
        edit_time: now,
        is_temporary: 0
    })
}
return { userId: userId, roleIds: roleIds }
