{
  "properties": {
    "gaLocalRoleAdapter": "v1"
  },
  "id": "347601b7dc2b45c8a52dd14221e17518",
  "script": null,
  "groupId": "af8d73aa8a2244a88474123d966e8ae5",
  "name": "02.角色保存修改",
  "createTime": 1782660726505,
  "updateTime": 1784437771436,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/saveOrUpdate",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "角色新增/编辑 + 关联用户保存；使用请求参数 roleName、codeNum、description、status、sortNum、id、userIds、appId；涉及 rm_role_t、rm_user_role_rela_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.角色保存修改
// 路由：POST /sym/role/saveOrUpdate
// 业务说明：角色新增/编辑 + 关联用户保存；使用请求参数 roleName、codeNum、description、status、sortNum、id、userIds、appId；涉及 rm_role_t、rm_user_role_rela_t 等业务表。
import tenantAccounts
import tenantRuntime
import '@/tenant-account/role-admin' as localRoleAdmin
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.date.DateUtil;
if(tenantAccounts.isLocal(tenantRuntime.id())) {return localRoleAdmin('save',body||{});}


// 角色新增/编辑 + 关联用户保存
// 路径: /sym/role/saveOrUpdate
// 方法: POST
// 参数:
//   id          - 角色ID（有则编辑，无则新增）
//   appId       - 应用ID（可选，默认取session中的appId）
//   roleName    - 角色名称（必填，避免与magic-api内置变量name冲突）
//   codeNum     - 角色编码（必填）
//   description - 角色描述（可选）
//   status      - 启用状态（0启用/1禁用）
//   sortNum     - 排序号（可选，默认0）
//   userIds     - 关联用户ID，逗号分隔字符串（如 "id1,id2,id3"）
//
// 关联表:


try {
    tenantAccounts.session();
} catch (e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit -1, "账号未登入或登入已过期";
}

// ==================== 参数获取（POST body） ====================
var roleName = body?.roleName;
var codeNum = body?.codeNum;
var description = body?.description || "";
var status = body?.status;
var sortNum = body?.sortNum;
var id = body?.id;
var userIds = body?.userIds || "";
var appId = body?.appId || tenantAccounts.session().get("appId");

// ==================== 参数校验 ====================
var currentUserId = StpUtil.getLoginId();
var now = DateUtil.now();

if (roleName == null || roleName == "") {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, "角色名称不能为空";
}
if (codeNum == null || codeNum == "") {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, "角色编码不能为空";
}

// ==================== 保存角色信息 ====================
var isUpdate = (id != null && id != "");
var roleId;

if (isUpdate) {
    // --- 编辑角色 ---
    roleId = id;
    var updateMap = {};
    updateMap["name"] = roleName;
    updateMap["code_num"] = codeNum;
    updateMap["description"] = description;
    updateMap["status"] = status;
    if (sortNum != null) {
        updateMap["sort_num"] = sortNum;
    }
    updateMap["update_id"] = currentUserId;
    updateMap["update_time"] = now;

    // 将校验后的业务数据写入 MySQL 主表。
    db.table("rm_role_t").where().eq("id", roleId).update(updateMap);

} else {
    // --- 新增角色 ---
    roleId = IdUtil.getSnowflakeNextIdStr();
    var insertMap = {};
    insertMap["id"] = roleId;
    insertMap["app_id"] = appId;
    insertMap["name"] = roleName;
    insertMap["code_num"] = codeNum;
    insertMap["description"] = description;
    insertMap["status"] = status != null ? status : 0;
    insertMap["sort_num"] = sortNum != null ? sortNum : 0;
    insertMap["deleted"] = 0;
    insertMap["create_id"] = currentUserId;
    insertMap["update_id"] = currentUserId;
    insertMap["create_time"] = now;
    insertMap["update_time"] = now;

    // 将校验后的业务数据写入 MySQL 主表。
    db.table("rm_role_t").insert(insertMap);
}

// ==================== 保存角色用户关联 ====================
// 先删除该角色所有现有用户关联
db.table("rm_user_role_rela_t").where().eq("role_id", roleId).delete();

// 再插入新关联
if (userIds != null && userIds != "") {
    var userIdArray = userIds.split(",");
    for (uid in userIdArray) {
        uid = uid.trim();
        if (uid != "" && uid != null) {
            var relMap = {};
            relMap["id"] = IdUtil.getSnowflakeNextIdStr();
            relMap["role_id"] = roleId;
            relMap["user_id"] = uid;
            relMap["auth_from"] = 0;
            relMap["edit_time"] = now;
            relMap["is_temporary"] = 0;

            // 将校验后的业务数据写入 MySQL 主表。
            db.table("rm_user_role_rela_t").insert(relMap);
        }
    }
}

// ==================== 返回 ====================
return { success: true, id: roleId };
