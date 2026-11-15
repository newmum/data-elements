{
  "properties": {},
  "id": "daaca32a76bd4787ba35d3d1ef4f871f",
  "script": null,
  "groupId": "b3f641e93f574cc5a88009f84e695ab1",
  "name": "03.菜单排序",
  "createTime": 1782660726302,
  "updateTime": 1791088093750,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/sort",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "校验同级菜单标识及当前租户范围，按请求顺序更新菜单资源排序号并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.dev33.satoken.stp.StpUtil
import db
import tableWrite
import tenantRuntime

// 一次锁定排序清单，确认全部菜单同属当前租户、同一应用和同一级，再批量更新。
var input = body?.ids
if (!(input instanceof List) || input.isEmpty() || input.size() > 1000) {
    exit 400, '排序菜单ID列表须包含1到1000项'
}
var ids = input.map(value => value == null ? '' : (value + '').trim())
if (ids.contains('') || ids.distinct().size() != ids.size()) { exit 400, '排序菜单ID不能为空或重复' }
var tenant = tenantRuntime.id()
if (!tenant) { exit 401, '当前租户无效' }
var actor = StpUtil.getLoginId() + ''
var now = new Date()
return db.transaction(() => {
    var menus = tableWrite.lockRows('rm_menu_t', tenant, 'id', ids,
        ['id', 'app_id', 'parent_id'], {deleted: 0})
    if (menus.size() != ids.size()) { exit 400, '所选菜单不存在或不属于当前租户' }
    var appId = menus[0].app_id
    var parentId = menus[0].parent_id
    if (!appId || menus.filter(row => row.app_id != appId || row.parent_id != parentId).size() > 0) {
        exit 400, '只能调整同一应用的同级菜单顺序'
    }
    var number = 0
    var updates = []
    for (id in ids) {
        number = number + 1
        updates.add({id: id, sort_num: number, update_id: actor, update_time: now})
    }
    var changed = tableWrite.batchUpdate('rm_menu_t', tenant,
        ['sort_num', 'update_id', 'update_time'], ['id'], {app_id: appId, deleted: 0}, updates)
    if (changed != ids.size()) { exit 409, '菜单已变化，请刷新后重试' }
    return {count: changed}
})
