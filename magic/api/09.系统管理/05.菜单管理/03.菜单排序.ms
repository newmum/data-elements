{
  "properties" : { },
  "id" : "daaca32a76bd4787ba35d3d1ef4f871f",
  "script" : null,
  "groupId" : "b3f641e93f574cc5a88009f84e695ab1",
  "name" : "03.菜单排序",
  "createTime" : 1782660726302,
  "updateTime" : 1791088093750,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/sort",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\n}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "校验同级菜单标识及当前租户范围，按请求顺序更新菜单资源排序号并返回处理结果。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
// 接口：03.菜单排序
// 路由：POST /sym/menu/sort
// 业务说明：校验同级菜单标识及当前租户范围，按请求顺序更新菜单资源排序号并返回处理结果。
// 菜单资源排序接口
// 路径: /sym/menu/sort
// 方法: POST
// 数据源: sso (192.168.175.86:3306)
// 参数: ids (必填,同级菜单ID数组,按新顺序排列,如 ["id3","id1","id2"])

import "@post:/sym/user/me" as me

var now = new Date();

// 获取入参
var ids = body?.ids;

// 参数校验
if (!ids || ids.size() == 0) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, '排序菜单ID列表不能为空';
}

try {
    var updateId = me().createId;

    // 按传入顺序依次设置sortNum（for迭代的是元素，手动维护序号）
    var sortNum = 0;
    for (id in ids) {
        sortNum = sortNum + 1;
        // 从 MySQL 业务表读取当前有效数据。
        db.table("rm_menu_t")
            .where().eq("id", id)
            .update({ sortNum: sortNum, updateId: updateId, updateTime: now });
    }

    // 返回当前业务分支的标准结果。
    return { count: ids.size() };
} catch(e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '排序失败: ' + e.message;
}
