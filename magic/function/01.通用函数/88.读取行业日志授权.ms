{
  "id": "log_tenant_grants_v1",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "88.读取行业日志授权",
  "path": "/loggrants",
  "parameters": [
    {"name": "actor", "required": true, "dataType": "String", "type": "java.lang.String"},
    {"name": "appId", "required": true, "dataType": "String", "type": "java.lang.String"},
    {"name": "capability", "required": false, "dataType": "String", "type": "java.lang.String"}
  ],
  "options": [],
  "description": "行业日志导航、下载和后台任务共用当前有效账号、角色、菜单授权及数据范围。调用方提供可信会话或任务主体和应用。"
}
================================
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap
import identityRuntime
import tableRead
import tenantRuntime

// 导航读取全部模块，下载和任务只重新核验所选模块。租户始终从已绑定的执行上下文取得。
var tenantId = tenantRuntime.id()
// 授权查询只访问显式绑定的行业库，不能使用路由数据源的默认库。
identityRuntime.requireTenantBinding(tenantId)
var supported = ['auth', 'operations', 'requests', 'security', 'exports', 'archives', 'policies']
if (capability != null && !supported.contains(capability)) {
    throw new IllegalArgumentException('日志模块无效')
}
var modules = capability == null ? supported : [capability]
var field = (alias, name) => tableRead.column(alias, name, 'TEXT')
var no = (alias, name) => tableRead.equal(tableRead.column(alias, name, 'NUMBER'), 0)

// 在同一个租户内关联有效账号、角色和授权菜单，范围只能来自该条授权所属角色。
var grants = tableRead.from('rm_user_role_rela_t', 'ur', tenantId)
grants.where(tableRead.equal(field('ur', 'user_id'), actor))
grants.leftJoin('rm_user_t', 'u', 'id', 'user_id', [no('u', 'status'), no('u', 'deleted')])
grants.where(tableRead.equal(field('u', 'id'), actor))
grants.leftJoin('rm_role_t', 'r', 'id', 'role_id', [
    tableRead.equal(field('r', 'app_id'), appId), no('r', 'status'), no('r', 'deleted')
])
grants.leftJoinFrom('r', 'rm_role_menu_rela_t', 'rm', 'role_id', 'id', [])
grants.leftJoinFrom('rm', 'rm_menu_t', 'm', 'id', 'app_resource_id', [
    tableRead.equal(field('m', 'app_id'), appId), no('m', 'status'), no('m', 'deleted')
])
var code = field('m', 'code')
var menuId = field('m', 'id')
var scope = field('r', 'data_scope')
grants.where(tableRead.in(code, modules.map(name => 'logs:' + name)))
var rows = grants.grouped({code: code, menuId: menuId, scope: scope}, [code, menuId, scope])
var modes = new LinkedHashMap()
if (rows.isEmpty()) {
    return {modes: modes, orgIds: []}
}

// 一次读取当前应用的菜单树。祖先校验在内存完成，拒绝缺失、停用、删除及循环。
var menus = tableRead.from('rm_menu_t', 'm', tenantId)
menus.where(tableRead.equal(field('m', 'app_id'), appId))
var nodes = new HashMap()
menus.forEachOrderedRow({id: menuId, parent: field('m', 'parent_id'),
    status: tableRead.column('m', 'status', 'NUMBER'), deleted: tableRead.column('m', 'deleted', 'NUMBER')},
    [menuId], ['asc'], true, node => nodes.put(node.id, node))
var active = id => {
    var seen = new HashSet()
    var current = id
    while (current != null && !current.isBlank() && current != 'ROOT' && current != '0') {
        var node = nodes.get(current)
        if (node == null || !seen.add(current) || seen.size() > 50
            || (node.status + '') != '0' || (node.deleted + '') != '0') {
            return false
        }
        current = node.parent
    }
    return true
}
for (row in rows) {
    if (active(row.menuId)) {
        var name = row.code.substring(5)
        var previous = modes.get(name)
        // 同一模块的有效范围按 ALL、ORG、OWNER 取最大，其他范围按 OWNER 处理。
        if (row.scope == 'ALL') {
            modes.put(name, 'ALL')
        } else if (row.scope == 'ORG' && previous != 'ALL') {
            modes.put(name, 'ORG')
        } else if (previous == null) {
            modes.put(name, 'OWNER')
        }
    }
}

var orgIds = []
if (capability != null && modes.get(capability) == 'ORG') {
    var membership = tableRead.from('rm_user_org_rela_t', 'uo', tenantId)
    membership.where(tableRead.equal(field('uo', 'user_id'), actor))
    membership.leftJoin('rm_org_t', 'o', 'id', 'org_id', [no('o', 'deleted')])
    var orgId = field('o', 'id')
    membership.where(tableRead.not(tableRead.isNull(orgId)))
    orgIds = membership.grouped({id: orgId}, [orgId]).map(row => row.id)
}
return {modes: modes, orgIds: orgIds}
