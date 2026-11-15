{
  "properties": {},
  "id": "57d488fe51404e37afe8528c348c87ee",
  "script": null,
  "groupId": "b3f641e93f574cc5a88009f84e695ab1",
  "name": "01.菜单保存修改",
  "createTime": 1782660725684,
  "updateTime": 1791088093611,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"resourceType\": 0,\n    \"urlType\": 0,\n    \"openType\": 0\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "菜单保存修改（POST /sym/menu/save）。支持新增、编辑和菜单层级移动；移动时校验循环引用并同步整棵子树的 top_id；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；涉及 rm_menu_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.core.util.IdUtil
import db
import tableWrite
import tenantAccounts
import tenantRuntime

// 在当前租户的应用内新增或更新菜单。校验父链后批量更新受移动影响的后代。
var input = body || {}
var id = (input.id || '') + ''
var label = input.name || ''
if (!label) { exit 400, '菜单名称不能为空' }
var tenant = tenantRuntime.id()
if (!tenant) { exit 401, '当前租户无效' }
var sessionApp = tenantAccounts.session().get('appId')
var actor = StpUtil.getLoginId() + ''
var now = new Date()
var integer = (value, fallback) => value == null || (value + '').trim() == '' ? fallback : value.asInt()
var parentId = (input.parentId || 'ROOT') + ''
var resourceType = integer(input.resourceType, 0)
var sortNum = integer(input.sortNum, null)

return db.transaction(() => {
    var old = id == '' ? null : db.table('rm_menu_t').columns('app_id').where()
        .eq('tenant_id', tenant).eq('id', id).eq('deleted', 0).selectOne()
    if (id != '' && old == null) { exit 404, '记录不存在: ' + id }
    var appId = old == null ? (input.appId || sessionApp) : old.appId
    if (!appId) { exit 400, '未获取到菜单所属应用' }
    if (old != null && input.appId && input.appId != appId) {
        exit 400, '菜单不能移动到其他应用'
    }

    var fields = ['id', 'parent_id', 'top_id', 'resource_type', 'sort_num']
    var scope = {app_id: appId, deleted: 0}
    // 先按统一顺序取得应用内的行锁，再读取锁范围内可能已提交的新记录。
    tableWrite.lockByFilter('rm_menu_t', tenant, 'id', fields, scope, 1000)
    var rows = tableWrite.lockByFilter('rm_menu_t', tenant, 'id', fields, scope, 1000)
    var byId = {}
    var children = {}
    for (row in rows) {
        byId[row.id] = row
        var key = row.parent_id || 'ROOT'
        if (children[key] == null) { children[key] = [] }
        children[key].add(row)
    }
    var existing = byId[id]
    if (id != '' && existing == null) { exit 404, '记录不存在: ' + id }
    if (id != '' && parentId == id) { exit 400, '菜单不能设置自身为父级' }

    var menuId = id == '' ? IdUtil.getSnowflakeNextIdStr() : id
    var topId = menuId
    if (parentId != 'ROOT') {
        var parent = byId[parentId]
        if (parent == null) { exit 400, '目标父级菜单不存在' }
        if (parent.resource_type == 1) { exit 400, '按钮节点不能作为父级菜单' }
        var seenParents = {}
        var cursor = parentId
        while (cursor && cursor != 'ROOT') {
            if (cursor == menuId) { exit 400, '不能将菜单移动到自己的下级菜单中' }
            if (seenParents[cursor] || byId[cursor] == null) {
                exit 400, '目标父级菜单层级无效'
            }
            seenParents[cursor] = true
            cursor = byId[cursor].parent_id
        }
        topId = parent.top_id || parent.id
    }
    var data = {
        name: label, parentId: parentId, resourceType: resourceType, sno: input.sno || '',
        url: input.url || '', urlType: integer(input.urlType, 0), staticIcon: input.staticIcon || '',
        dynamicImg: input.dynamicImg || '', openType: integer(input.openType, 0), code: input.code || '',
        dataScopeEnabled: resourceType == 0 && integer(input.dataScopeEnabled, 0) == 1 ? 1 : 0,
        appId: appId, topId: topId, updateId: actor, updateTime: now
    }
    if (existing == null) {
        if (!sortNum) {
            sortNum = 1
            for (sibling in (children[parentId] || [])) {
                sortNum = Math.max(sortNum, (sibling.sort_num || 0) + 1)
            }
        }
        data.id = menuId
        data.tenantId = tenant
        data.sortNum = sortNum
        data.createId = actor
        data.createTime = now
        data.deleted = 0
        data.status = integer(input.status, 0)
        db.table('rm_menu_t').insert(data)
        return {id: menuId, parentId: parentId, topId: topId}
    }

    if (sortNum != null) { data.sortNum = sortNum }
    var moved = parentId != existing.parent_id
    var descendants = []
    if (moved || topId != existing.top_id) {
        var seen = {}
        seen[id] = true
        var queue = [id]
        var index = 0
        while (index < queue.size()) {
            for (child in (children[queue[index]] || [])) {
                if (seen[child.id]) { exit 400, '现有菜单层级存在循环' }
                seen[child.id] = true
                descendants.add(child.id)
                queue.add(child.id)
            }
            index = index + 1
        }
    }
    db.table('rm_menu_t').withBlank().where().eq('tenant_id', tenant)
        .eq('app_id', appId).eq('deleted', 0).eq('id', id).update(data)
    if (!descendants.isEmpty()) {
        db.table('rm_menu_t').where().eq('tenant_id', tenant).eq('app_id', appId)
            .eq('deleted', 0).in('id', descendants)
            .update({topId: topId, updateId: actor, updateTime: now})
    }
    return {id: id, parentId: parentId, topId: topId, moved: moved}
})
