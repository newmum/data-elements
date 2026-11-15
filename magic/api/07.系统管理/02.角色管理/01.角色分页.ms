{
  "properties": {
    "gaLocalRoleAdapter": "v1"
  },
  "id": "b3519d0888524b2d990c3e09822f6e73",
  "script": null,
  "groupId": "af8d73aa8a2244a88474123d966e8ae5",
  "name": "01.角色分页",
  "createTime": 1782660726713,
  "updateTime": 1784437771459,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": 1,\n    \"pageSize\": 20\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "角色分页查询（含关联用户数、权限数、用户ID列表）；使用请求参数 appId、pageNum、pageSize、name、codeNum、ids；涉及 rm_role_t、rm_user_role_rela_t、rm_user_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import java.util.Collections
import tableRead
import tableSort
import tenantAccounts
import tenantRuntime

/**
 * POST /sym/role/page 分页读取当前租户应用的有效角色。
 * appId 为空时使用当前会话的应用，支持角色 ID、名称、编码和排序筛选。
 * 返回 total 与 list，角色包含有效关联用户数、权限数和逗号分隔的用户 ID。
 */
var session = null
try {
    session = tenantAccounts.session()
} catch (error) {
    exit -1, '账号未登入或登入已过期'
}

var tenantId = tenantRuntime.id()
var appId = body?.appId || session.get('appId')
var pageNum = Math.max(1, (body?.pageNum || 1).asInt())
var pageSize = Math.min(100, Math.max(1, (body?.pageSize || 10).asInt()))
var sortKey = tableSort.field(body, {
    name: 'name', createTime: 'createTime', userCount: 'userCount',
    permCount: 'permCount', sortNum: 'sortNum'
}, 'sortNum')
var direction = body?.sortField == null ? 'asc' : tableSort.direction(body)
if (appId == null) {
    return {total: 0, list: []}
}

// 租户取自可信运行上下文，应用参数只筛选该租户内的有效角色。
var roles = tableRead.from('rm_role_t', 'role_row', tenantId)
var roleColumn = (column, kind) => tableRead.column('role_row', column, kind)
roles.where(tableRead.all([
    tableRead.equal(roleColumn('deleted', 'NUMBER'), 0),
    tableRead.equal(roleColumn('app_id', 'TEXT'), appId)
]))
var ids = body?.ids
if (ids != null && ids != '') {
    var requestedRoleIds = []
    for (roleId in ids.split(',')) {
        requestedRoleIds.add(roleId)
    }
    roles.where(tableRead.in(roleColumn('id', 'TEXT'), requestedRoleIds))
}
var name = body?.name
if (name != null && name != '') {
    roles.where(tableRead.like(roleColumn('name', 'TEXT'), '%' + name + '%'))
}
var codeNum = body?.codeNum
if (codeNum != null && codeNum != '') {
    roles.where(tableRead.like(roleColumn('code_num', 'TEXT'), '%' + codeNum + '%'))
}

// 关联数在库内去重后参与排序。分页总数只依赖角色筛选，无需执行这两个聚合关联。
var userLinks = tableRead.from('rm_user_role_rela_t', 'user_link', tenantId)
var linkRoleId = tableRead.column('user_link', 'role_id', 'TEXT')
var linkUserId = tableRead.column('user_link', 'user_id', 'TEXT')
var userId = tableRead.column('active_user', 'id', 'TEXT')
userLinks.leftJoin('rm_user_t', 'active_user', 'id', 'user_id', [
    tableRead.equal(tableRead.column('active_user', 'deleted', 'NUMBER'), 0)
])
userLinks.where(tableRead.not(tableRead.isNull(userId)))
roles.leftJoinGroupedProjection(userLinks, 'user_counts', 'role_id', 'id', {
    role_id: linkRoleId,
    total: tableRead.countDistinct(linkUserId)
}, [linkRoleId])

var menuLinks = tableRead.from('rm_role_menu_rela_t', 'menu_link', tenantId)
var menuRoleId = tableRead.column('menu_link', 'role_id', 'TEXT')
var menuId = tableRead.column('menu_link', 'app_resource_id', 'TEXT')
roles.leftJoinGroupedProjection(menuLinks, 'menu_counts', 'role_id', 'id', {
    role_id: menuRoleId,
    total: tableRead.countDistinct(menuId)
}, [menuRoleId])

var fields = {
    tenantId: roleColumn('tenant_id', 'TEXT'),
    id: roleColumn('id', 'TEXT'),
    createId: roleColumn('create_id', 'TEXT'),
    updateId: roleColumn('update_id', 'TEXT'),
    deleted: roleColumn('deleted', 'NUMBER'),
    createTime: roleColumn('create_time', 'TIME'),
    updateTime: roleColumn('update_time', 'TIME'),
    status: roleColumn('status', 'NUMBER'),
    name: roleColumn('name', 'TEXT'),
    codeNum: roleColumn('code_num', 'TEXT'),
    description: roleColumn('description', 'TEXT'),
    appId: roleColumn('app_id', 'TEXT'),
    sortNum: roleColumn('sort_num', 'NUMBER'),
    dataScope: roleColumn('data_scope', 'TEXT'),
    userCount: tableRead.coalesce([tableRead.column('user_counts', 'total', 'NUMBER'), 0]),
    permCount: tableRead.coalesce([tableRead.column('menu_counts', 'total', 'NUMBER'), 0])
}
// 排序空值统一放在末尾，同值以角色 ID 升序稳定分页。
var page = roles.page(fields, fields[sortKey], direction, 'id', 'asc', pageNum, pageSize)
var rows = page.rows
if (rows.size() == 0) {
    return {total: page.total, list: []}
}

// 只对当前页角色批量取用户 ID。重新按有效角色和应用关联，使用主表 ID 兼容历史大小写差异。
var roleIds = rows.map(role => role.id)
var visibleRoleId = tableRead.column('page_role', 'id', 'TEXT')
userLinks.leftJoin('rm_role_t', 'page_role', 'id', 'role_id', [
    tableRead.equal(tableRead.column('page_role', 'deleted', 'NUMBER'), 0),
    tableRead.equal(tableRead.column('page_role', 'app_id', 'TEXT'), appId)
])
userLinks.where(tableRead.in(visibleRoleId, roleIds))
var members = userLinks.grouped({roleId: visibleRoleId, userId: userId}, [visibleRoleId, userId])
var userIdsByRole = {}
for (member in members) {
    if (userIdsByRole[member.roleId] == null) {
        userIdsByRole[member.roleId] = []
    }
    userIdsByRole[member.roleId].add(member.userId)
}
// 仅返回契约字段，分页适配器附加的列不进入响应。
var list = []
for (row in rows) {
    var role = {}
    for (key in fields.keySet()) {
        role[key] = row[key]
    }
    var userIds = userIdsByRole[role.id] || []
    Collections.sort(userIds)
    role.userIds = userIds.join(',')
    list.add(role)
}
return {total: page.total, list: list}
