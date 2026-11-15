{
  "properties": {
    "gaLocalRoleAdapter": "v1"
  },
  "id": "f0d7c425b9dc42619db0e70fa9bd546f",
  "script": null,
  "groupId": "af8d73aa8a2244a88474123d966e8ae5",
  "name": "03.角色删除",
  "createTime": 1782660726928,
  "updateTime": 1784437771461,
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
  "description": "按目标标识和当前状态执行角色删除，返回处理结果；使用请求参数 id；涉及 rm_role_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.角色删除
// 路由：POST /sym/role/delete
// 业务说明：按目标标识和当前状态执行角色删除，返回处理结果；使用请求参数 id；涉及 rm_role_t 等业务表。
import tenantAccounts
import tenantRuntime
import '@/tenant-account/role-admin' as localRoleAdmin
import cn.dev33.satoken.stp.StpUtil;
if(tenantAccounts.isLocal(tenantRuntime.id())) {return localRoleAdmin('delete',body||{});}


// 删除角色
// 路径: /sym/role/delete
// 方法: POST
// 参数（均为 body 字段）:
//   id       - 角色ID（必填）
// 说明:
//   角色主表 rm_role_t 执行软删除（deleted = 1）
//   关联表 rm_user_role_rela_t 删除 auth_from = 0 的直接授权记录
//   关联表 rm_role_menu_rela_t 删除角色对应的资源授权记录


try {
  tenantAccounts.session();
} catch (e) {
  // 参数或业务状态不满足要求时立即终止请求。
  exit -1, "账号未登入或登入已过期";
}

// ==================== 从 body 获取参数 ====================
var id = body?.id;

// ==================== 参数校验 ====================
if (id == null || id == "") {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, "角色ID不能为空";
}

var currentUserId = StpUtil.getLoginId();
var now = new Date();

// ==================== 1. 软删除角色主表 ====================
var updateMap = {};
updateMap["deleted"] = 1;
updateMap["update_id"] = currentUserId;
updateMap["update_time"] = now;

// 将校验后的业务数据写入 MySQL 主表。
db.table("rm_role_t").where().eq("id", id).update(updateMap);

// ==================== 2. 删除角色直接授权的用户关联 ====================
db.lower().table("rm_user_role_rela_t")
    .where()
    .eq("role_id", id)
    .eq("auth_from", 0)
    .delete();

// ==================== 3. 删除角色关联的资源权限 ====================
db.lower().table("rm_role_menu_rela_t")
    .where()
    .eq("role_id", id)
    .delete();

// ==================== 返回结果 ====================
return {
    success: true,
    msg: "删除成功"
};
