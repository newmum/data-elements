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
var pageNum  = Math.max(1,(body?.pageNum || 1).asInt());
var pageSize = Math.min(100,Math.max(1,(body?.pageSize || 10).asInt()));
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

// Count aggregates participate in ORDER BY before the page is selected.
import tableSort
var tenantId = tenantRuntime.id()
var sortColumn = tableSort.field(body, {
    name:'r.name', createTime:'r.create_time', userCount:'coalesce(uc.total,0)',
    permCount:'coalesce(pc.total,0)', sortNum:'r.sort_num'
}, 'sortNum')
var sortDirection = body?.sortField == null ? 'asc' : tableSort.direction(body)
var filterSql = ' WHERE r.tenant_id=#{tenantId} AND r.deleted=0 AND r.app_id=#{appId}'
if (ids != null && ids != '') { filterSql = filterSql + ' AND r.id IN (#{idList})' }
if (name != null && name != '') { var roleNamePattern='%'+name+'%'; filterSql=filterSql+' AND r.name LIKE #{roleNamePattern}' }
if (codeNum != null && codeNum != '') { var roleCodePattern='%'+codeNum+'%'; filterSql=filterSql+' AND r.code_num LIKE #{roleCodePattern}' }
var roleList = db.page("""
SELECT r.*,COALESCE(uc.total,0) AS userCount,COALESCE(pc.total,0) AS permCount
FROM rm_role_t r
LEFT JOIN (SELECT rel.role_id,COUNT(DISTINCT rel.user_id) total
           FROM rm_user_role_rela_t rel JOIN rm_user_t u ON u.id=rel.user_id
           AND u.tenant_id=#{tenantId} AND u.deleted=0
           WHERE rel.tenant_id=#{tenantId} GROUP BY rel.role_id) uc ON uc.role_id=r.id
LEFT JOIN (SELECT role_id,COUNT(DISTINCT app_resource_id) total FROM rm_role_menu_rela_t
           WHERE tenant_id=#{tenantId} GROUP BY role_id) pc ON pc.role_id=r.id
""" + filterSql + ' ORDER BY ' + sortColumn + ' ' + sortDirection + ',r.id ASC', Math.min(limit,100), offset)

// One tenant-scoped batch enriches user IDs; count metrics already come from the page SQL.
var roleIds=roleList.list.map(role=>role.id)
var userIdsByRole={}
if(roleIds.size()>0) {
    var records=db.lower().select("""
        SELECT DISTINCT rel.role_id,rel.user_id FROM rm_user_role_rela_t rel
        JOIN rm_user_t u ON u.id=rel.user_id AND u.tenant_id=rel.tenant_id AND u.deleted=0
        WHERE rel.tenant_id=#{tenantId} AND rel.role_id IN (#{roleIds})
    """)
    for(row in records) {var id=row.role_id;if(userIdsByRole[id]==null){userIdsByRole[id]=[]};userIdsByRole[id].add(row.user_id)}
}
for(role in roleList.list) {role.userIds=(userIdsByRole[role.id] || []).join(',')}

// ==================== 返回结果 ====================
if (roleList.list == null) {
    roleList.list = [];
}
// 返回当前业务分支的标准结果。
return roleList;
