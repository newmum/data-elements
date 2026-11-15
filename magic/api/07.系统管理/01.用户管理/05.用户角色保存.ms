{
  "properties": {},
  "id": "user_role_save_01",
  "script": null,
  "groupId": "1ac60b4ccbc142e89dc1bf575e674d20",
  "name": "05.用户角色保存",
  "path": "/assign",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"userId\": \"用户ID\",\n  \"roleIds\": [\"角色ID\"]\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"roleIds\": []\n}",
  "description": "替换当前应用的用户角色。本地账号校验用户及角色归属，保留中央授权和其他应用角色，事务内更新并记录审计。"
}
================================
import '@/common/audit-operation' as auditOperation
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.core.util.IdUtil
import cn.hutool.json.JSONUtil
import db
import identityRuntime
import tenantAccounts
import tenantRuntime

// POST /sym/user/assign 按 userId、appId 和 roleIds 替换当前应用的用户角色。
// 本地账号校验行业库、应用角色及中央授权，并在事务内替换本地关联和记录审计。

var tenantId = tenantRuntime.id()
if (tenantAccounts.isLocal(tenantId)) {
    var input = body || {}
    var userId = (input.userId || '') + ''
    var appId = (input.appId || tenantAccounts.session().get('appId') || '') + ''
    if (userId == '' || appId == '') {
        exit 400, '用户和应用不能为空'
    }
    if (tenantAccounts.find(tenantId, userId) == null) {
        exit 404, '用户不存在或不属于当前租户'
    }
    // 读取当前应用角色一次。旧的停用或删除角色也纳入替换范围，只有有效角色可新增。
    var applicationRoles = db.table('rm_role_t').columns('id', 'deleted', 'status').where()
        .eq('tenant_id', tenantId).eq('app_id', appId).select()
    var available = applicationRoles.filter(role => role.deleted == 0 && role.status == 0).map(role => role.id)
    var roleIds = []
    for (id in (input.roleIds || [])) {
        var roleId = (id + '').trim()
        if (!available.contains(roleId)) {
            exit 400, '所选角色不存在或不属于当前应用'
        }
        if (!roleIds.contains(roleId)) {
            roleIds.add(roleId)
        }
    }
    if (userId == StpUtil.getLoginId() + '' && roleIds.size() == 0) {
        exit 409, '不能清空当前登录账号的应用角色'
    }

    return db.transaction(() => {
        // 先核对中央拥有的关联，再删除本地关联，保留中央授予的角色与其他应用的角色。
        var managed = identityRuntime.hasTable('idaas_receiver_object_t')
            ? db.table('idaas_receiver_object_t').column('snapshot_json').where()
                .eq('object_type', 'SUBJECT').eq('local_id', userId).eq('management_state', 'MANAGED').selectOne()
            : null
        var snapshot = managed == null ? null : (managed.snapshotJson == null ? managed.snapshot_json : managed.snapshotJson)
        var owned = snapshot == null ? [] : JSONUtil.parseObj(snapshot).roleRelations || []
        var central = owned.size() == 0 ? [] : db.table('rm_user_role_rela_t').column('role_id').where()
            .eq('tenant_id', tenantId).in('id', owned).select().map(row => row.roleId == null ? row.role_id : row.roleId)
        if (central.filter(id => !roleIds.contains(id)).size() > 0) {
            exit 409, '不能移除中央来源角色，请在平台撤权或先脱管'
        }
        var appRoleIds = applicationRoles.map(role => role.id)
        if (!appRoleIds.isEmpty()) {
            db.table('rm_user_role_rela_t').where()
                .eq('tenant_id', tenantId).eq('user_id', userId).in('role_id', appRoleIds)
                .notIn(owned.size() > 0, 'id', owned).delete()
        }
        var now = new Date()
        var rows = roleIds.filter(id => !central.contains(id)).map(id => ({
            tenant_id: tenantId, id: IdUtil.getSnowflakeNextIdStr(), user_id: userId,
            role_id: id, auth_from: 0, edit_time: now, is_temporary: 0
        }))
        if (!rows.isEmpty()) {
            db.table('rm_user_role_rela_t').batchInsert(rows)
        }
        auditOperation('USER_ROLES_UPDATED', 'USER', userId, null, {id: userId, roleIds: roleIds})
        return {userId: userId, roleIds: roleIds}
    })
}

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
