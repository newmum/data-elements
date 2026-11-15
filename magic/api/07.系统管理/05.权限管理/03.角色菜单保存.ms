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
  "path": "/save",
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
import cn.hutool.core.util.IdUtil
import db
import identityRuntime
import tableWrite
import tenantRuntime

// 锁定当前租户角色，批量验证应用内菜单，再在同一事务内替换权限和记录审计。
var tenant = tenantRuntime.id()
if (!tenant) { exit 401, '当前租户无效' }
var roleId = (body?.roleId || '') + ''
if (roleId == '') { exit 404, '角色不存在或不属于当前租户' }
var input = body?.resourceIds || []
if (!(input instanceof List) || input.size() > 500) { exit 400, '一次最多分配500个资源' }
var ids = input.map(id => (id + '').trim()).distinct()
if (ids.contains('')) { exit 400, '菜单ID不能为空' }

return db.transaction(() => {
    var role = tableWrite.lockRow('rm_role_t', tenant, 'id', roleId,
        ['id', 'app_id'], {deleted: 0})
    if (role == null) { exit 404, '角色不存在或不属于当前租户' }
    // 中央拥有的角色仍由平台配置，行业应用不能覆盖其菜单。
    if (identityRuntime.hasTable('idaas_receiver_object_t') && db.table('idaas_receiver_object_t').column('local_id')
        .where().eq('local_id', roleId).eq('object_type', 'ROLE').eq('management_state', 'MANAGED').selectOne() != null) {
        exit 409, '中央拥有角色的资源请在平台维护或先脱管'
    }
    if (!ids.isEmpty()) {
        var menus = tableWrite.lockRows('rm_menu_t', tenant, 'id', ids, ['id'],
            {app_id: role.app_id, deleted: 0})
        if (menus.size() != ids.size()) { exit 400, '所选菜单不存在或不属于当前应用' }
    }
    db.table('rm_role_menu_rela_t').where().eq('tenant_id', tenant).eq('role_id', roleId).delete()
    if (!ids.isEmpty()) {
        db.table('rm_role_menu_rela_t').batchInsert(ids.map(id => ({
            tenant_id: tenant, id: IdUtil.getSnowflakeNextIdStr(), role_id: roleId,
            app_resource_id: id, extend_id: ''
        })))
    }
    auditOperation('ROLE_RESOURCES_UPDATED', 'ROLE', roleId, null, {id: roleId, targetIds: ids, count: ids.size()})
    return {roleId: roleId, count: ids.size()}
})
