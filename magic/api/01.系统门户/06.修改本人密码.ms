{
  "properties": {},
  "id": "5e5d48c9fced4fc9a18b8308d102936f",
  "script": null,
  "groupId": "90ef9a3f03244c9b97aa655b8c59b0b6",
  "name": "06.修改本人密码",
  "createTime": null,
  "updateTime": null,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/account/password",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"oldPassword\":\"SM3摘要\",\"newPassword\":\"SM3摘要\"}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "核验当前用户原密码，修改本人密码并注销旧会话。"
}
================================
// 接口：修改当前登录用户密码
// 路由：POST /portal/account/password
// 请求：{ oldPassword: SM3(当前密码), newPassword: SM3(新密码) }
// 仅使用服务端会话确定租户与用户；不得接受客户端传入的用户 ID 或租户 ID。
import db;
import tenantRuntime;
import tenantAccounts;
import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.utils.PwdRuleUtil;

try {
    tenantAccounts.session();
} catch (e) {
    exit 401, '登录已过期，请重新登录';
}

var tenantId = (tenantRuntime.id() || '') + '';
if (tenantId == '' || !tenantAccounts.isLocal(tenantId)) {
    exit 403, '当前账号不支持此密码修改方式';
}
var userId = StpUtil.getLoginId() + '';
var oldDigest = ((body?.oldPassword || '') + '').trim().toUpperCase();
var newDigest = ((body?.newPassword || '') + '').trim().toUpperCase();
if (!oldDigest.matches('[0-9A-F]{64}') || !newDigest.matches('[0-9A-F]{64}')) {
    exit 400, '密码参数不正确';
}
if (oldDigest == newDigest) {
    exit 400, '新密码不能与当前密码相同';
}

var sql = db.normal();
sql.transaction(() => {
    var user = sql.selectOne(`
select password as "password"
  from rm_user_t
 where tenant_id=#{tenantId} and id=#{userId} and deleted=0 and status=0
 for update`);
    if (user == null || user.password == null || !PwdRuleUtil.checkPwdBySalt(oldDigest, user.password)) {
        exit 400, '当前密码不正确';
    }
    var encoded = PwdRuleUtil.encryptPwdBySalt(newDigest);
    sql.update(`
update rm_user_t
   set password=#{encoded}, update_id=#{userId}, update_time=current_timestamp
 where tenant_id=#{tenantId} and id=#{userId} and deleted=0 and status=0`);
    return true;
});
tenantAccounts.revoke(userId);
return { relogin: true };
