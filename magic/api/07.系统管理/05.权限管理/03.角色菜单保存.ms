{
  "properties": {
    "gaLocalRoleAdapter": "v1"
  },
  "id": "e4445190b9a543c89d23b3a6adacffc5",
  "script": null,
  "groupId": "882093f918054984b1504ea77e58d3af",
  "name": "03.角色菜单保存",
  "createTime": 1782660716468,
  "updateTime": 1784437771404,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/saveRoleResource",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "替换指定角色的菜单关联。本地角色仅接受同租户、同应用内的菜单，统一校验后在事务内替换并审计，中央拥有角色由平台维护。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import '@/common/audit-operation' as auditOperation
import "@post:/sym/user/me" as me
import cn.hutool.core.util.IdUtil
import db
import identityRuntime
import tenantAccounts
import tenantRuntime

// POST /sym/auth/saveRoleResource 按 roleId 和 resourceIds 替换当前租户的角色菜单。
// 本地角色在写入前批量校验菜单归属。替换关联和写入审计使用同一租户事务。

var tenantId = tenantRuntime.id()
if (tenantAccounts.isLocal(tenantId)) {
    var input = body || {}
    var roleId = (input.roleId || '') + ''
    var role = db.table('rm_role_t').columns('id', 'app_id').where()
        .eq('tenant_id', tenantId).eq('id', roleId).eq('deleted', 0).selectOne()

    // 中央拥有角色的菜单由平台维护，普通授权请求不可覆盖。
    if (identityRuntime.hasTable('idaas_receiver_object_t') && db.table('idaas_receiver_object_t').column('local_id')
        .where().eq('local_id', roleId).eq('object_type', 'ROLE').eq('management_state', 'MANAGED').selectOne() != null) {
        exit 409, '中央拥有角色的资源请在平台维护或先脱管'
    }
    if (role == null) {
        exit 404, '角色不存在或不属于当前租户'
    }
    var ids = []
    for (resourceId in (input.resourceIds || [])) {
        var id = resourceId + ''
        if (!ids.contains(id)) {
            ids.add(id)
        }
    }
    if (ids.size() > 500) {
        exit 400, '一次最多分配500个资源'
    }
    if (!ids.isEmpty()) {
        var appId = role.appId == null ? role.app_id : role.appId
        var menus = db.table('rm_menu_t').column('id').where()
            .eq('tenant_id', tenantId).eq('app_id', appId)
            .in('id', ids).eq('deleted', 0).select()
        if (menus.size() != ids.size()) {
            exit 400, '所选菜单不存在或不属于当前应用'
        }
    }
    return db.transaction(() => {
        db.table('rm_role_menu_rela_t').where().eq('tenant_id', tenantId).eq('role_id', roleId).delete()
        if (!ids.isEmpty()) {
            var rows = ids.map(id => ({
                tenant_id: tenantId, id: IdUtil.getSnowflakeNextIdStr(), role_id: roleId,
                app_resource_id: id, extend_id: ''
            }))
            db.table('rm_role_menu_rela_t').batchInsert(rows)
        }
        auditOperation('ROLE_RESOURCES_UPDATED', 'ROLE', roleId, null, {id: roleId, targetIds: ids, count: ids.size()})
        return {roleId: roleId, count: ids.size()}
    })
}

var now = new Date();

// 获取入参
var roleId      = body?.roleId || '';
var resourceIds = body?.resourceIds;

// 参数校验
if (!roleId) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, '角色ID不能为空';
}

try {
    var updateId = me().createId;

    // 检查角色是否存在
    var role = db.table("rm_role_t")
        .where().eq("id", roleId).eq("deleted", 0).selectOne();

    if (role == null) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 404, '角色不存在: ' + roleId;
    }

    // 先删除该角色所有旧关联
    db.table("rm_role_menu_rela_t")
        .where().eq("role_id", roleId)
        .delete();

    // 再批量插入新关联
    var count = 0;
    if (resourceIds && resourceIds.size() > 0) {
        var insertList = [];
        for (resourceId in resourceIds) {
            insertList.push({
                id: IdUtil.getSnowflakeNextIdStr(),
                roleId: roleId,
                appResourceId: resourceId,
                extendId: ''
            });
            count = count + 1;
        }
        // 从 MySQL 业务表读取当前有效数据。
        db.table("rm_role_menu_rela_t").batchInsert(insertList);
    }

    // 返回当前业务分支的标准结果。
    return { roleId: roleId, count: count };
} catch(e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '保存角色菜单关联失败: ' + e.message;
}
