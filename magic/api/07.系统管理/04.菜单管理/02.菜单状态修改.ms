{
  "properties": {},
  "id": "ad79907ebe4c4acca241d7e740e9e91b",
  "script": null,
  "groupId": "b3f641e93f574cc5a88009f84e695ab1",
  "name": "02.菜单状态修改",
  "createTime": 1782660726068,
  "updateTime": 1791088093673,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/state",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"status\": 2\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "菜单资源启用/禁用接口（级联）；使用请求参数 id、status、deep；涉及 rm_menu_t 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.dev33.satoken.stp.StpUtil
import db
import tableWrite
import tenantRuntime

// 按菜单的现有应用范围一次锁定层级，校验并批量启停当前节点及选定后代。
var id = (body?.id || '') + ''
var status = body?.status || 0
if (id == '') { exit 400, '菜单ID不能为空' }
if (status != 0 && status != 1) { exit 400, 'status值无效，0=启用 1=禁用' }
var tenant = tenantRuntime.id()
if (!tenant) { exit 401, '当前租户无效' }
var actor = StpUtil.getLoginId() + ''
var now = new Date()

return db.transaction(() => {
    var previous = db.table('rm_menu_t').columns('app_id').where()
        .eq('tenant_id', tenant).eq('id', id).eq('deleted', 0).selectOne()
    if (previous == null || !previous.appId) { exit 404, '菜单不存在: ' + id }
    var appId = previous.appId
    var ids = [id]
    if (body?.deep) {
        var scope = {app_id: appId, deleted: 0}
        var projection = ['id', 'parent_id']
        tableWrite.lockByFilter('rm_menu_t', tenant, 'id', projection, scope, 1000)
        var rows = tableWrite.lockByFilter('rm_menu_t', tenant, 'id', projection, scope, 1000)
        var children = {}
        var found = false
        for (row in rows) {
            if (row.id == id) { found = true }
            var parentId = row.parent_id || 'ROOT'
            if (children[parentId] == null) { children[parentId] = [] }
            children[parentId].add(row.id)
        }
        if (!found) { exit 404, '菜单不存在: ' + id }
        var seen = {}
        seen[id] = true
        var index = 0
        while (index < ids.size()) {
            for (childId in (children[ids[index]] || [])) {
                if (seen[childId]) { exit 400, '现有菜单层级存在循环' }
                seen[childId] = true
                ids.add(childId)
            }
            index = index + 1
        }
    } else if (tableWrite.lockRow('rm_menu_t', tenant, 'id', id,
        ['id'], {app_id: appId, deleted: 0}) == null) {
        exit 404, '菜单不存在: ' + id
    }
    db.table('rm_menu_t').where().eq('tenant_id', tenant).eq('app_id', appId)
        .eq('deleted', 0).in('id', ids)
        .update({status: status, updateId: actor, updateTime: now})
    return {id: id, status: status, affectedCount: ids.size()}
})
