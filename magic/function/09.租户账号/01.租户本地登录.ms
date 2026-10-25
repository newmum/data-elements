{
  "id": "ga_local_login_01",
  "groupId": "ga_local_accounts_functions_01",
  "name": "01.租户本地登录",
  "path": "/login",
  "parameters": [
    {
      "name": "payload",
      "dataType": "Object",
      "type": null,
      "required": false,
      "value": null,
      "description": "该动作所需的账号、角色或资源资料"
    }
  ],
  "script": null,
  "description": "在当前租户库核验本地账号、密码和状态，返回租户本地登录结果；不接受统一身份平台账号冒充本地账号。"
}
================================
// 共享函数：01.租户本地登录
// 函数路径：/tenant-account/login
// 业务说明：在当前租户库核验本地账号、密码和状态，返回租户本地登录结果；不接受统一身份平台账号冒充本地账号。
// 函数输入：payload：该动作所需的账号、角色或资源资料。
import db;
import auditEvents;
import identityRuntime;
import tenantAccounts;
import cn.hutool.core.util.IdUtil;
import com.linewell.dataelement.utils.PwdRuleUtil;
import '@/idaas/receiver-account-check' as sourceCheck;
// 公安本地登录：仅校验显式绑定业务库的账号，不查询控制库用户或成员关系。
var p=payload||{};
if(!(p instanceof Map)
    || p.account!=null && !(p.account instanceof String) && !(p.account instanceof Number)
    || p.password!=null && !(p.password instanceof String)) {exit 400,'请输入有效的账号和密码';}
var tenantId=(p.tenantId||'')+'';
// 兼容旧登录页将纯数字账号从浏览器缓存解析为 Number；账号始终按文本匹配。
var account=p.account==null?'':(p.account+'').trim();
// 此处仅规范化前端传来的 SM3 摘要，不改变原始密码中的空格。
var password=p.password==null?'':p.password.trim().toUpperCase();
if(tenantId=='' || account=='' || account.length()>50 || !password.matches('[0-9A-F]{64}')) {exit 400,'请输入有效的账号和密码';}
if(!tenantAccounts.isLocal(tenantId)) {exit 403,'当前租户未启用本地账号';}
var sql=db.normal();
var user=identityRuntime.inTenant(tenantId,()=>sql.selectOne("""
select id,password as "password",status
from rm_user_t
where tenant_id=#{tenantId}
    and user_name=#{account}
    and deleted=0
"""));
if(user==null || user.status!=0 || user.password==null || !PwdRuleUtil.checkPwdBySalt(password,user.password)) {auditEvents.authentication(tenantId,null,'AUTH_LOGIN_FAILED','FAILED','INVALID_CREDENTIALS');exit -1,'用户名或密码错误';}
if(!identityRuntime.inTenant(tenantId,()=>sourceCheck(user.id)).allowed) {auditEvents.authentication(tenantId,null,'AUTH_LOGIN_FAILED','DENIED','SOURCE_DISABLED');exit -1,'此账号的统一管理来源已停用，请联系管理员或办理脱管';}
var token=tenantAccounts.loginLocal(tenantId,user.id,(p.appId||'')+'',account);
try {
    auditEvents.authentication(tenantId,user.id,'AUTH_LOGIN_SUCCEEDED','SUCCESS',null);
} catch(error) {
    auditEvents.compensateLogin();
    throw error;
}
return {token:token,url:'/home',authRealm:'tenant-local-v1',tenantId:tenantId};
