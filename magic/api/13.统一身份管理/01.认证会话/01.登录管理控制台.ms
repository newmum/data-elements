{
  "properties": {},
  "id": "b04b913d36315a339c11a2a5c462fca6",
  "script": null,
  "groupId": "846e66f7a9365dc4a13942533f262c0a",
  "name": "01.登录管理控制台",
  "createTime": 1790612165860,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/login",
  "description": "根据平台操作账号读取独立凭据与安全状态，检查失败锁定、密码和多因子挑战，成功后签发平台会话并记录登录审计；租户本地账号不能在此登录。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：01.登录管理控制台
// 路由：POST /idaas/auth/login
// 业务说明：根据平台操作账号读取独立凭据与安全状态，检查失败锁定、密码和多因子挑战，成功后签发平台会话并记录登录审计；租户本地账号不能在此登录。
import db;
import request;
import platformSession;
import iamSecurity;
import iamMfa;
import '@/idaas/session' as sessionInfo;
import '@/idaas/audit' as audit;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import identityRuntime;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    // 请求参数与本接口业务处理。
    var payload = body||{};

    // 平台账号与租户本地账号分离；失败次数限制、密码核验和多因子挑战均在本接口完成。
    // 独立operator登录；保留旧摘要兼容，新增/改密使用框架PBKDF2，不依赖rm账号。
    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
    var username=(p.username||'').trim().toLowerCase();
    var password=p.password||'';
    if(p.domain!='workforce' || username=='' || username.length()>50 || password=='' || password.length()>256) {
        exit 400,'请输入有效的平台账号与密码';
    }
    var fingerprint=DigestUtil.sha256Hex(username);
    var ip=request.getClientIP();
    var limited=sql.selectOne("""
select count(*) as total
from iam_audit_event_t
where event_type='AUTH'
    and result!='SUCCESS'
    and (account_fingerprint=#{fingerprint}
         or ip=#{ip})
    and event_time>date_sub(utc_timestamp(6),interval 10 minute)
""");
    if(limited.total>=30) {
        exit 429,'尝试次数过多，请稍后重试';
    }
    // 把读取校验和状态写入放在同一事务，防止并发覆盖。
    var outcome=sql.transaction(()=>{
        var u=sql.selectOne("""
select *,tid as id
from iam_operator_t
where active_username=#{username}
    and status='ACTIVE'
    for update
""");
        if(u==null) {
            return {
                error:'INVALID'
            };
        }
        var s=sql.selectOne("""
select *,tid as id,case when locked_until>utc_timestamp(6) then 1 else 0
                   end as locked
from iam_account_security_t
where principal_kind='OPERATOR'
    and principal_id=#{u.id}
    for update
""");
        if(s==null) {
            return {
                error:'INVALID'
            };
        }
        if(s.locked==1) {
            return {
                error:'LOCKED',userId:u.id
            };
        }
        var policy=sql.selectOne("""
select value_json
from iam_setting_t
where scope_kind='GLOBAL'
    and scope_key='global'
    and category='SECURITY'
    and config_key='password'
    and is_del=0
    and status='ACTIVE'
""");
        var config=policy==null?{}:policy.value_json;
        if(policy!=null) {
            config=JSONUtil.parseObj(policy.value_json);
        }
        var threshold=config.lockThreshold||5;
        var minutes=config.lockMinutes||15;
        var cred=sql.selectOne("""
select *,tid as id
from iam_credential_t
where owner_kind='OPERATOR'
    and owner_id=#{u.id}
    and credential_type='PASSWORD'
    and slot='primary'
    and status='ACTIVE'
    and (not_before is null
         or not_before<=utc_timestamp(6))
    and (expires_at is null
         or expires_at>utc_timestamp(6))
""");
        // 核对凭据时兼容已登记的摘要算法，失败只更新安全状态而不创建会话。
        if(cred==null || !iamSecurity.verify(password,cred.algorithm,cred.secret_hash)) {
            var failures=s.failed_count+1;
            sql.update("""
update iam_account_security_t
set failed_count=#{failures},locked_until=case
        when #{failures}>=#{threshold} then date_add(utc_timestamp(6),interval #{minutes} minute) else null
        end,updated_time=utc_timestamp(6)
where tid=#{s.id}
""");
            return {
                error:'INVALID',userId:u.id
            };
        }
        sql.update("""
update iam_account_security_t
set failed_count=0,locked_until=null,updated_time=utc_timestamp(6)
where tid=#{s.id}
""");
        sql.update("""
update iam_operator_t
set last_login_time=utc_timestamp(6)
where tid=#{u.id}
""");
        return {
            id:u.id,securityVersion:s.security_version
        };
    });
    // 记录操作主体、目标及结果，供审计与故障追踪。
    var trace=audit(outcome.id==null?null:{
        userId:outcome.id
    },outcome.error==null?'SUCCESS':'FAILED','auth:login',outcome.userId,outcome.error,null);
    sql.update("""
update iam_audit_event_t
set account_fingerprint=#{fingerprint}
where tid=#{trace}
""");
    if(outcome.error=='LOCKED') {
        exit 429,'账号暂时锁定，请稍后重试或申请解锁';
    }
    if(outcome.error!=null) {
        exit 401,'平台账号或密码不正确';
    }
    var mfa=sql.selectOne("""
select tid as id
from iam_credential_t
where owner_kind='OPERATOR'
    and owner_id=#{outcome.id}
    and credential_type='TOTP'
    and status='ACTIVE'
""");
    var mfaPolicy=sql.selectOne("""
select value_json
from iam_setting_t
where category='SECURITY'
    and config_key='password'
    and scope_key='global'
    and is_del=0
""");
    if(mfa!=null) {
        return {
            mfaRequired:true,realm:'platform',challenge:iamMfa.challenge('OPERATOR',outcome.id,outcome.securityVersion,'LOGIN')
        };
    }
    if(mfaPolicy!=null && JSONUtil.parseObj(mfaPolicy.value_json).mfaRequired==true) {
        exit 403,'账号尚未绑定动态口令，请联系管理员';
    }
    // 密码及附加挑战通过后才签发独立的平台会话。
    platformSession.login(outcome.id,1800);
    platformSession.session().set('securityVersion',outcome.securityVersion);
    try {
        return {
            token:platformSession.token(),session:sessionInfo()
        };
    }
    catch(e) {
        platformSession.logout();
        throw e;
    }

}));
