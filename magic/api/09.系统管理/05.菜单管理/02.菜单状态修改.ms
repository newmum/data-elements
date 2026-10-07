{
  "properties" : { },
  "id" : "ad79907ebe4c4acca241d7e740e9e91b",
  "script" : null,
  "groupId" : "b3f641e93f574cc5a88009f84e695ab1",
  "name" : "02.菜单状态修改",
  "createTime" : 1782660726068,
  "updateTime" : 1791088093673,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/toggleStatus",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\n    \"status\": 2\n}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "菜单资源启用/禁用接口（级联）；使用请求参数 id、status、deep；涉及 rm_menu_t 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
// 接口：02.菜单状态修改
// 路由：POST /sym/menu/toggleStatus
// 业务说明：菜单资源启用/禁用接口（级联）；使用请求参数 id、status、deep；涉及 rm_menu_t 等业务表；执行数据库写入并返回处理结果。
// 菜单资源启用/禁用接口（级联）
// 路径: /sym/menu/toggleStatus
// 方法: POST
// 数据源: sso (192.168.175.86:3306)
// 参数: id (必填,菜单ID), status (必填,0启用/1禁用)
// 说明: 级联更新该节点的status (或及所有子孙菜单)

import "@post:/sym/user/me" as me

var now = new Date();

// 获取入参
var id     = body?.id || '';
var status = body?.status ? body.status : 0;
var deep   = body?.deep // 是否操作其下所有子孙菜单

// 参数校验
if (!id) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, '菜单ID不能为空';
}

if (status != 0 && status != 1) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, 'status值无效，0=启用 1=禁用';
}

try {
    var updateId = me().createId;

    // 校验菜单是否存在
    var exist = db.table("rm_menu_t")
        .where().eq("id", id).eq("deleted", 0).selectOne();

    if (exist == null) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 404, '菜单不存在: ' + id;
    }

    // BFS 收集所有后代ID（自身+子孙）
    var allIds = [id];
    var queue = [id];

    if (deep){
        while (queue.size() > 0) {
            var currentId = queue.remove(0);
            // 从 MySQL 业务表读取当前有效数据。
            var children = db.table("rm_menu_t")
                .where().eq("parent_id", currentId).eq("deleted", 0)
                .select();

            for (child in children) {
                allIds.add(child.id);
                queue.add(child.id);
            }
        }
    }

    // 批量更新自身及所有子孙status
    db.table("rm_menu_t")
        .where().in("id", allIds)
        .update({ status: status, updateId: updateId, updateTime: now });

    // 返回当前业务分支的标准结果。
    return { id: id, status: status, affectedCount: allIds.size() };
} catch(e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '操作失败: ' + e.message;
}
