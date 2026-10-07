{
  "properties": {},
  "id": "99884ef7c8074a85a77e9effbf69f5b8",
  "script": null,
  "groupId": "8f2a6564b6fd43c28ff7a5e35d4d7cd2",
  "name": "03.页签删除",
  "createTime": 1782955345284,
  "updateTime": 1784437771881,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/delete",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"xxxx\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "应用仓库页签删除（同步删除关联资源）；使用请求参数 tid；涉及 app_workshop_repository_tab、app_workshop_big_screen、sym_form 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.页签删除
// 路由：POST /das/repository/tab/delete
// 业务说明：应用仓库页签删除（同步删除关联资源）；使用请求参数 tid；涉及 app_workshop_repository_tab、app_workshop_big_screen、sym_form 等业务表。
import log
import uiComponentAdmin

/**
 * 应用仓库页签删除（同步删除关联资源）
 * DELETE /api/das/repository/tab/delete/{tid}
 */

var tid = body?.tid
if (tid == null || tid == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, "页签ID不能为空"
}

// 从 MySQL 业务表读取当前有效数据。
var tab = db.table('app_workshop_repository_tab')
    .where()
    .eq('tid', tid)
    .eq('is_del', 0)
    .selectOne()

if (tab == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, "页签不存在"
}

var tabType = tab?.tab_type::int
var relaId = tab?.rela_id

// 将校验后的业务数据写入 MySQL 主表。
db.table('app_workshop_repository_tab').logic().where().eq('tid', tid).delete()

var otherRefCount = 0
if (relaId != null && relaId != '') {
    otherRefCount = db.selectInt("""
        select count(1)
        from app_workshop_repository_tab
        where is_del = 0
        and tab_type = #{tabType}
        and rela_id = #{relaId}
        and tid != #{tid}
    """)
}

var deletedRela = false
if (otherRefCount == 0 && relaId != null && relaId != '') {
    if ([0,1].contains(tabType)) {
        uiComponentAdmin.delete(relaId)
        deletedRela = true
    } else if (tabType == 3) {
        // 将校验后的业务数据写入 MySQL 主表。
        db.table('app_workshop_big_screen').logic().where().eq('tid', relaId).delete()
        deletedRela = true
    } else if (tabType == 2) {
        // 将校验后的业务数据写入 MySQL 主表。
        db.table('sym_form').logic().where().eq('tid', relaId).delete()
        deletedRela = true
    }
}

log.info('删除应用仓库页签成功, tid: {}, tabType: {}, relaId: {}, deletedRela: {}', tid, tabType, relaId, deletedRela)

// 返回当前业务分支的标准结果。
return true
