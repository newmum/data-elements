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
import identityRuntime;
import tenantAccounts;
import cn.hutool.core.util.IdUtil;
import com.linewell.dataelement.utils.PwdRuleUtil;
import '@/idaas/receiver-account-check' as sourceCheck;
// 公安本地登录：仅校验显式绑定业务库的账号，不查询控制库用户或成员关系。
var p=payload||{};var tenantId=(p.tenantId||'')+'';
var account=(p.account||'').trim();var password=(p.password||'').trim().toUpperCase();
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
if(user==null || user.status!=0 || user.password==null || !PwdRuleUtil.checkPwdBySalt(password,user.password)) {exit -1,'用户名或密码错误';}
if(!identityRuntime.inTenant(tenantId,()=>sourceCheck(user.id)).allowed) {exit -1,'此账号的统一管理来源已停用，请联系管理员或办理脱管';}
var token=tenantAccounts.loginLocal(tenantId,user.id,(p.appId||'')+'',account);
var logId=IdUtil.getSnowflakeNextIdStr();
sql.update("""
insert into log_user_login_t(tid,
                account, is_success, token, created_by, updated_by, created_time, updated_time, is_del,
                tenant_id)
values(#{logId},#{account},1,#{token},#{user.id},#{user.id},current_timestamp,current_timestamp,0,#{tenantId})
""");
return {token:token,url:'/home',authRealm:'tenant-local-v1',tenantId:tenantId};
