{
  "properties": {},
  "id": "7cca7dbb12174db680af8e5b81aeca5d",
  "script": null,
  "groupId": "882093f918054984b1504ea77e58d3af",
  "name": "02.角色菜单查询",
  "createTime": 1782660716505,
  "updateTime": 1784437771408,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/detail",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "返回: 该角色已关联的菜单资源ID列表；使用请求参数 roleId；涉及 rm_role_menu_rela_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tableRead
import tenantRuntime

// 查询当前租户角色的有效菜单关联。按角色的应用核验每个菜单 ID。
var roleId = (body?.roleId || '') + ''
if (roleId == '') { exit 400, '角色ID不能为空' }
var tenant = tenantRuntime.id()
if (!tenant) { exit 401, '当前租户无效' }
var links = tableRead.from('rm_role_menu_rela_t', 'link', tenant)
var menuId = tableRead.column('menu', 'id', 'TEXT')
var roleApp = tableRead.column('role', 'app_id', 'TEXT')
links.where(tableRead.equal(tableRead.column('link', 'role_id', 'TEXT'), roleId))
links.leftJoin('rm_role_t', 'role', 'id', 'role_id', [
    tableRead.equal(tableRead.column('role', 'deleted', 'NUMBER'), 0)
])
links.leftJoin('rm_menu_t', 'menu', 'id', 'app_resource_id', [
    tableRead.equal(tableRead.column('menu', 'deleted', 'NUMBER'), 0),
    tableRead.equal(tableRead.column('menu', 'app_id', 'TEXT'), roleApp)
])
links.where(tableRead.not(tableRead.isNull(menuId)))
var rows = links.rows({id: menuId}, [menuId], ['asc'], 1001)
if (rows.size() > 1000) { exit 413, '角色菜单关联超过1000项' }
return {roleId: roleId, resourceIds: rows.map(row => row.id).distinct()}
