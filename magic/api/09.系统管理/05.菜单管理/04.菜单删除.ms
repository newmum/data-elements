{
  "properties" : { },
  "id" : "c1e127f305fd44ac827bab7bb2c65550",
  "script" : null,
  "groupId" : "b3f641e93f574cc5a88009f84e695ab1",
  "name" : "04.菜单删除",
  "createTime" : 1782660725883,
  "updateTime" : 1791088093824,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/delete",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\n}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "说明: 软删除，deleted=1；存在子菜单时拒绝删除；使用请求参数 id；涉及 rm_menu_t 等业务表。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
// 接口：04.菜单删除
// 路由：POST /sym/menu/delete
// 业务说明：说明: 软删除，deleted=1；存在子菜单时拒绝删除；使用请求参数 id；涉及 rm_menu_t 等业务表。
// 菜单资源删除接口（软删除）
// 路径: /sym/menu/delete
// 方法: POST
// 数据源: sso (192.168.175.86:3306)
// 参数: id (必填,菜单ID)
// 说明: 软删除，deleted=1；存在子菜单时拒绝删除

import "@post:/sym/user/me" as me

var now = new Date();

// 获取入参
var id = body?.id || '';

// 参数校验
if (!id) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, '菜单ID不能为空';
}

try {
    // 检查记录是否存在且未删除
    var exist = db.table("rm_menu_t")
        .where().eq("id", id).eq("deleted", 0).selectOne();

    if (exist == null) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 404, '菜单不存在或已删除: ' + id;
    }

    // 检查是否存在未删除的子菜单
    var children = db.table("rm_menu_t")
        .where().eq("parent_id", id).eq("deleted", 0).select();

    if (children.size() > 0) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 400, '该菜单下存在' + children.size() + '个子菜单，请先删除子菜单';
    }

    // 执行软删除
    var updateId = me().createId;
    // 从 MySQL 业务表读取当前有效数据。
    db.table("rm_menu_t")
        .where().eq("id", id)
        .update({ deleted: 1, updateId: updateId, updateTime: now });

    // 返回当前业务分支的标准结果。
    return { id: id, deleted: true };
} catch(e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '删除失败: ' + e.message;
}
