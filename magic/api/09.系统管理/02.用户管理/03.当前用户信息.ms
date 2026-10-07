{
  "properties": {
    "gaLocalRoleAdapter": "v1"
  },
  "id": "bced293de53e4b348940f99b1b5bdf9f",
  "script": null,
  "groupId": "1ac60b4ccbc142e89dc1bf575e674d20",
  "name": "03.当前用户信息",
  "createTime": 1782660715924,
  "updateTime": 1788947882331,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/me",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "当前用户信息（POST /sym/user/me）。读取当前会话用户、角色、权限和主属部门；组织配置缺失时返回明确业务提示，不暴露脚本异常；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；使用请求参数 appId；涉及 rm_user_role_rela_t、rm_role_t、rm_org_t、rm_role_menu_rela_t、rm_menu_t、rm_user_org_rela_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.当前用户信息
// 路由：POST /sym/user/me
// 业务说明：当前用户信息（POST /sym/user/me）。读取当前会话用户、角色、权限和主属部门；组织配置缺失时返回明确业务提示，不暴露脚本异常；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；使用请求参数 appId；涉及 rm_user_role_rela_t、rm_role_t、rm_org_t、rm_role_menu_rela_t、rm_menu_t、rm_user_org_rela_t 等业务表。
import tenantAccounts
import cn.dev33.satoken.stp.StpUtil;
import tenantRuntime



/*
 * 当前用户信息。
 *
 * 只读接口。组织归属是当前用户权限与数据范围的前提；所有组织查询都以服务端
 * 当前租户为准。缺少用户、角色、主属部门、组织编码或根部门时返回业务错误，
 * 避免出现空对象脚本异常和向浏览器暴露调用栈。
 */
var text = value => value == null ? '' : (value + '').trim()

try {
    tenantAccounts.session()
} catch (e) {
    exit 401, '账号未登录或登录已过期'
}

var tenantId = text(tenantRuntime.id())
if (tenantId == '') {
    exit 401, '当前登录租户无效，请重新登录'
}

var appId = body?.appId || tenantAccounts.session().get('appId')
var user = tenantAccounts.find(tenantId, StpUtil.getLoginId() + '')
if (user == null) {
    exit 401, '当前登录账号不存在或已失效，请重新登录'
}

var newUser = {
    id: user.id,
    realName: user.realName,
    userName: user.userName,
    phone: user.phone,
    gender: user.gender,
    createId: user.createId
}

var userRoleList = db.table('rm_user_role_rela_t').where()
    .eq('user_id', StpUtil.getLoginId())
    .select()
if (userRoleList == null || userRoleList.isEmpty()) {
    exit 400, '当前用户未配置应用角色，请联系管理员'
}

var roleIds = userRoleList.map(item => item.roleId)
var roleQuery=db.table('rm_role_t').where().eq('app_id',appId).in('id',roleIds);
if(tenantAccounts.isLocal(tenantId)) {roleQuery=roleQuery.eq('deleted',0).eq('status',0);}
var roleList=roleQuery.select();
if(tenantAccounts.isLocal(tenantId) && (roleList==null || roleList.size()==0)) {exit 400,'当前用户未配置有效的应用角色，请联系管理员';}

var roles = roleList == null ? [] : roleList.map(item => item.codeNum)

var permsSQL = """
    select distinct res.*
      from rm_role_t role
      join rm_role_menu_rela_t rr on rr.role_id = role.id and rr.tenant_id = #{tenantId}
      join rm_menu_t res on res.id = rr.app_resource_id and res.app_id = role.app_id and res.tenant_id = #{tenantId}
     where role.app_id = #{appId}
       and role.id in (#{roleIds})
       and role.deleted = 0
       and role.status = 0
       and res.deleted = 0
       and res.resource_type = 1
       and role.tenant_id = #{tenantId}
"""
var permsList = db.select(permsSQL)
var perms = permsList == null ? [] : permsList.map(item => item.sno)

var orgSQL = """
    select u.user_id, org.id, org.name, org.serial_number, org.org_path
      from rm_user_org_rela_t u
      join rm_org_t org on org.id = u.org_id and org.tenant_id = #{tenantId}
     where u.user_id = #{user.id}
       and u.job_type = 1
       and u.tenant_id = #{tenantId}
"""
var org = db.selectOne(orgSQL)
if (org == null) {
    exit 400, '当前用户未配置主属部门，请联系管理员'
}

var serialNumber = text(org.serialNumber)
if (serialNumber == '') {
    exit 400, '当前用户主属部门的组织编码不完整，请联系管理员'
}
var orgPath = text(org.orgPath)
if (orgPath == '') {
    exit 400, '当前用户主属部门的组织路径为空，请联系管理员'
}

var orgRootSerialNumber = serialNumber.substring(0, 4)
var orgRoot = db.table('rm_org_t').where()
    .eq('serialNumber', orgRootSerialNumber)
    .eq('parentId', 'ROOT')
    .eq('nature', '2')
    .selectOne()
if (orgRoot == null) {
    exit 400, '当前用户主属部门的组织编码未关联有效根部门，请联系管理员'
}

return {
    ...newUser,
    orgId: org.id,
    orgName: org.name,
    serialNumber: serialNumber,
    orgPath: orgPath,
    roles: roles,
    perms: perms,
    orgRootId: orgRoot.id,
    orgRootSerialNumber: orgRoot.serialNumber
}
