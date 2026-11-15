{
  "properties": {},
  "id": "c1e127f305fd44ac827bab7bb2c65550",
  "script": null,
  "groupId": "b3f641e93f574cc5a88009f84e695ab1",
  "name": "04.菜单删除",
  "createTime": 1782660725883,
  "updateTime": 1791088093824,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/delete",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "说明: 软删除，deleted=1；存在子菜单时拒绝删除；使用请求参数 id；涉及 rm_menu_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.dev33.satoken.stp.StpUtil
import db
import tableWrite
import tenantRuntime

// 锁定当前租户菜单，再确认其应用内无子菜单后软删除。
var id = (body?.id || '') + ''
if (id == '') { exit 400, '菜单ID不能为空' }
var tenant = tenantRuntime.id()
if (!tenant) { exit 401, '当前租户无效' }
var actor = StpUtil.getLoginId() + ''
return db.transaction(() => {
    var menu = tableWrite.lockRow('rm_menu_t', tenant, 'id', id, ['id', 'app_id'], {deleted: 0})
    if (menu == null) { exit 404, '菜单不存在或已删除: ' + id }
    var children = db.table('rm_menu_t').where().eq('tenant_id', tenant).eq('app_id', menu.app_id)
        .eq('parent_id', id).eq('deleted', 0).count()
    if (children > 0) { exit 400, '该菜单下存在' + children + '个子菜单，请先删除子菜单' }
    db.table('rm_menu_t').where().eq('tenant_id', tenant).eq('id', id).eq('deleted', 0)
        .update({deleted: 1, updateId: actor, updateTime: new Date()})
    return {id: id, deleted: true}
})
