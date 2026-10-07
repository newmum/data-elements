{
  "properties": {
    "gaLocalRoleAdapter": "v1"
  },
  "id": "b3519d0888524b2d990c3e09822f6e73",
  "script": null,
  "groupId": "af8d73aa8a2244a88474123d966e8ae5",
  "name": "01.角色分页",
  "createTime": 1782660726713,
  "updateTime": 1784437771459,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": 1,\n    \"pageSize\": 20\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "角色分页查询（含关联用户数、权限数、用户ID列表）；使用请求参数 appId、pageNum、pageSize、name、codeNum、ids；涉及 rm_role_t、rm_user_role_rela_t、rm_user_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.角色分页
// 路由：POST /sym/role/page
// 业务说明：角色分页查询（含关联用户数、权限数、用户ID列表）；使用请求参数 appId、pageNum、pageSize、name、codeNum、ids；涉及 rm_role_t、rm_user_role_rela_t、rm_user_t 等业务表。
import tenantAccounts
import tenantRuntime
import cn.hutool.db.PageResult
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.NumberUtil;


// 角色分页查询（含关联用户数、权限数、用户ID列表）
// 路径: /sym/role/page
// 方法: POST
// 参数（均为 body 字段）:
//   appId     - 应用ID（可选，默认取session中的appId）
//   pageNum   - 页码（默认1）
//   pageSize  - 每页条数（默认10）
//   name      - 角色名称模糊查询（可选）
//   codeNum   - 角色编码模糊查询（可选）
//   ids       - 角色ID列表，逗号分隔（可选）


try {
  tenantAccounts.session();
} catch (e) {
  // 参数或业务状态不满足要求时立即终止请求。
  exit -1, "账号未登入或登入已过期";
}

// ==================== 从 body 获取查询参数 ====================
var appId    = body?.appId || tenantAccounts.session().get("appId");
var pageNum  = (body?.pageNum || 1).asInt();
var pageSize = (body?.pageSize || 10).asInt();
var name     = body?.name;      // POST body 字段需通过 body?. 获取
var codeNum  = body?.codeNum;
var ids      = body?.ids;

// 将页码转换为偏移量
var offset = (pageNum - 1) * pageSize;
var limit = pageSize;

// ==================== 构建查询条件 ====================
var result = db.table("rm_role_t").where().eq("deleted", 0).eq("app_id", appId);

// 根据ID列表查询
if (ids != null && ids != "") {
    var idList = ids.split(",");
    result = result.in("id", idList);
}

// 角色编码模糊查询
if (codeNum != null && codeNum != "") {
    result = result.like("code_num", "%" + codeNum + "%");
}

// 角色名称模糊查询
if (name != null && name != "") {
    result = result.like("name", "%" + name + "%");
}

// ==================== 分页查询 ====================
var roleList = result.orderBy("sort_num")
                     .page(limit, offset);

// ==================== 收集角色ID，初始化默认值 ====================
var roleIds = [];
var list = roleList.list;
if (list != null) {
    for (role in list) {
        roleIds.push(role.id);
        role.userCount = 0;
        role.permCount = 0;
        role.userIds = "";              // 关联用户ID，逗号分割
    }
}

// ==================== 查询关联用户数、权限数、用户ID列表 ====================
if (roleIds.size() > 0) {

    // --- 查询角色关联用户记录 ---
    // .lower() 确保返回字段名为 snake_case，与 bracket 访问保持一致
    var userRecords = db.lower().table("rm_user_role_rela_t")
        .where()
        .in("role_id", roleIds)
        .select();

    if(tenantAccounts.isLocal(tenantRuntime.id())) {
      var localTenantId=tenantRuntime.id();
      userRecords=db.lower().select("""
select rel.*
from rm_user_role_rela_t rel
join rm_user_t u on u.id=rel.user_id
and u.tenant_id=#{localTenantId}
where rel.tenant_id=#{localTenantId}
    and rel.role_id in (#{roleIds})
    and u.deleted=0
""");
      var seenLocalUsers={};userRecords=userRecords.filter(r=>{
        var key=r['role_id']+':'+r['user_id'];if(seenLocalUsers[key]==true) {return false;} seenLocalUsers[key]=true;return true;
      });
    }
    var userCountMap = {};
    var userIdsMap = {};             // roleId → [userId1, userId2, ...]
    for (ur in userRecords) {
        var rid = ur["role_id"];
        var uid = ur["user_id"];
        if (rid != null && uid != null && uid != "") {
            // 计数
            if (userCountMap[rid] == null) {
                userCountMap[rid] = 0;
            }
            userCountMap[rid] = userCountMap[rid] + 1;

            // 收集用户ID
            if (userIdsMap[rid] == null) {
                userIdsMap[rid] = [];
            }
            userIdsMap[rid].push(uid);
        }
    }

    // --- 查询角色关联权限数 ---
    var permRecords = db.lower().table("rm_role_menu_rela_t")
        .where()
        .in("role_id", roleIds)
        .select();

    var permCountMap = {};
    for (pr in permRecords) {
        var rid = pr["role_id"];
        if (rid != null) {
            if (permCountMap[rid] == null) {
                permCountMap[rid] = 0;
            }
            permCountMap[rid] = permCountMap[rid] + 1;
        }
    }

    // --- 填充到角色列表 ---
    for (role in roleList.list) {
        // 用户数
        if (userCountMap[role.id] != null) {
            role.userCount = userCountMap[role.id];
        }
        // 权限数
        if (permCountMap[role.id] != null) {
            role.permCount = permCountMap[role.id];
        }
        // 关联用户ID列表（逗号分割）
        if (userIdsMap[role.id] != null) {
            role.userIds = userIdsMap[role.id].join(",");
        }
    }
}

// ==================== 返回结果 ====================
if (roleList.list == null) {
    roleList.list = [];
}
// 返回当前业务分支的标准结果。
return roleList;
