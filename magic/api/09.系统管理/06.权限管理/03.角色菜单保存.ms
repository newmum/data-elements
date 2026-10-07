{
  "properties": {
    "gaLocalRoleAdapter": "v1"
  },
  "id": "e4445190b9a543c89d23b3a6adacffc5",
  "script": null,
  "groupId": "882093f918054984b1504ea77e58d3af",
  "name": "03.角色菜单保存",
  "createTime": 1782660716468,
  "updateTime": 1784437771404,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/saveRoleResource",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "说明: 先删后增——删除该角色所有旧关联，再批量插入新关联；使用请求参数 roleId、resourceIds；涉及 rm_role_t、rm_role_menu_rela_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.角色菜单保存
// 路由：POST /sym/auth/saveRoleResource
// 业务说明：说明: 先删后增——删除该角色所有旧关联，再批量插入新关联；使用请求参数 roleId、resourceIds；涉及 rm_role_t、rm_role_menu_rela_t 等业务表。
import tenantAccounts
import tenantRuntime
import '@/tenant-account/resources' as localResources
import cn.hutool.core.util.IdUtil
import "@post:/sym/user/me" as me
if(tenantAccounts.isLocal(tenantRuntime.id())) {return localResources(body||{});}

// 角色菜单关联关系保存接口
// 路径: /sym/sso/roleResource
// 方法: POST
// 数据源: sso (192.168.175.86:3306)
// 参数: roleId (必填,角色ID), resourceIds (可选,菜单资源ID数组,传空数组表示清空所有关联)
// 说明: 先删后增——删除该角色所有旧关联，再批量插入新关联


var now = new Date();

// 获取入参
var roleId      = body?.roleId || '';
var resourceIds = body?.resourceIds;

// 参数校验
if (!roleId) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, '角色ID不能为空';
}

try {
    var updateId = me().createId;

    // 检查角色是否存在
    var role = db.table("rm_role_t")
        .where().eq("id", roleId).eq("deleted", 0).selectOne();

    if (role == null) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 404, '角色不存在: ' + roleId;
    }

    // 先删除该角色所有旧关联
    db.table("rm_role_menu_rela_t")
        .where().eq("role_id", roleId)
        .delete();

    // 再批量插入新关联
    var count = 0;
    if (resourceIds && resourceIds.size() > 0) {
        var insertList = [];
        for (resourceId in resourceIds) {
            insertList.push({
                id: IdUtil.getSnowflakeNextIdStr(),
                roleId: roleId,
                appResourceId: resourceId,
                extendId: ''
            });
            count = count + 1;
        }
        // 从 MySQL 业务表读取当前有效数据。
        db.table("rm_role_menu_rela_t").batchInsert(insertList);
    }

    // 返回当前业务分支的标准结果。
    return { roleId: roleId, count: count };
} catch(e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '保存角色菜单关联失败: ' + e.message;
}
