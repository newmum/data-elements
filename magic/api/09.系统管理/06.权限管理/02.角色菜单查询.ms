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
  "path": "/getRoleResource",
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
// 接口：02.角色菜单查询
// 路由：POST /sym/auth/getRoleResource
// 业务说明：返回: 该角色已关联的菜单资源ID列表；使用请求参数 roleId；涉及 rm_role_menu_rela_t 等业务表。
// 获取角色菜单关联关系接口
// 路径: /sym/auth/getRoleResource
// 方法: POST
// 数据源: sso (192.168.175.86:3306)
// 参数: roleId (必填,角色ID)
// 返回: 该角色已关联的菜单资源ID列表

var roleId = body?.roleId || '';

if (!roleId) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, '角色ID不能为空';
}

try {
    // 从 MySQL 业务表读取当前有效数据。
    var list = db.table("rm_role_menu_rela_t")
        .where().eq("role_id", roleId)
        .select();

    var resourceIds = [];
    for (row in list) {
        resourceIds.add(row.appResourceId);
    }

    // 返回当前业务分支的标准结果。
    return { roleId: roleId, resourceIds: resourceIds };
} catch(e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '查询角色菜单关联失败: ' + e.message;
}
