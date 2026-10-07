{
  "properties": {
    "gaLocalRoleAdapter": "v1"
  },
  "id": "user_role_options_01",
  "script": null,
  "groupId": "1ac60b4ccbc142e89dc1bf575e674d20",
  "name": "10.用户角色选项",
  "path": "/role-options",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"list\": []\n}",
  "description": "用户角色选项：POST /sym/user/role-options。返回当前租户、当前应用下全部未删除角色，用于用户角色维护；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；使用请求参数 appId；涉及 rm_role_t 等业务表。"
}
================================
// 接口：10.用户角色选项
// 路由：POST /sym/user/role-options
// 业务说明：用户角色选项：POST /sym/user/role-options。返回当前租户、当前应用下全部未删除角色，用于用户角色维护；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；使用请求参数 appId；涉及 rm_role_t 等业务表。
import tenantAccounts
import tenantRuntime
import cn.dev33.satoken.stp.StpUtil



try {
    tenantAccounts.session()
} catch (e) {
    exit 401, '账号未登录或登录已过期'
}

var appId = body?.appId || tenantAccounts.session().get('appId')
if (appId == null || (appId + '').trim() == '') {
    exit 400, '未获取到当前应用'
}

// 租户数据源会自动限定当前租户；不引入平台或租户管理员角色判断。
var list = db.table('rm_role_t').where()
    .eq('app_id', appId)
    .eq('deleted', 0)
    .orderBy('sort_num')
    .select()

if(tenantAccounts.isLocal(tenantRuntime.id())) {list=(list||[]).filter(r=>r.status==0);}
return {list:list||[],total:list==null?0:list.size()};
