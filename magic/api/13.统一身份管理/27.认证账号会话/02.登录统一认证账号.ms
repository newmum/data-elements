{
  "properties": {},
  "id": "e3a6dbd17bac5e63b83ff8c1bd7cd0d2",
  "script": null,
  "groupId": "0d24c0ffb0bf58e59cf30e12786d94fe",
  "name": "02.登录统一认证账号",
  "createTime": 1790635007936,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/login",
  "description": "校验账号状态、密码、锁定和多因子策略，签发统一认证会话并记录认证事件；该会话不能登录管理控制台。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：02.登录统一认证账号
// 路由：POST /idaas/auth-account/login
// 业务说明：校验账号状态、密码、锁定和多因子策略，签发统一认证会话并记录认证事件；该会话不能登录管理控制台。
import identityTransport;
import db;
import authSession;
import iamSecurity;
import iamMfa;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import '@/idaas/auth-account-context' as context;
import '@/idaas/password-policy' as passwordPolicy;
import '@/idaas/audit' as audit;
import '@/idaas/auth-session-policy' as sessionPolicy;
import identityRuntime;
import request;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    var payload=body;
    // 请求参数与本接口业务处理。
    var operation = 'login';

    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
    var domain=p.domain||'public';
    var toProfile=(a)=>{
        var profile=sql.selectOne("""
select p.verification_status, v.expires_at>utc_timestamp(6) current_verification
from iam_public_profile_t p
left join iam_verification_t v on v.tid=p.verification_id
and v.status='VERIFIED'
where p.subject_id=#{a.subject_id}
    and p.is_del=0
""");
        var status=profile==null?'UNVERIFIED':profile.verification_status;
        if(status=='VERIFIED' && profile.current_verification!=1) {
            status='EXPIRED';
        }
        return {
            id:a.id,subjectId:a.subject_id,username:a.username,name:a.name,domain:a.identity_domain,realm:'auth-account',mustChangePassword:a.must_change_password==1,verificationStatus:status,legalEntityId:a.legal_entity_id,version:a.version
        };
    };
    if(operation=='login') {
        var username=(p.username||'').trim().toLowerCase();
        if(!['workforce','public'].contains(domain) || username=='' || username.length()>100 || p.password==null || p.password.length()>256) {
            exit 400,'请输入有效认证账号与密码';
        }
        var ip=request.getClientIP();
        var fingerprint=DigestUtil.sha256Hex(domain+':'+username);
        if(sql.selectOne("""
select count(*) n
from iam_audit_event_t
where event_type='AUTH'
    and result='FAILED'
    and (account_fingerprint=#{fingerprint}
         or ip=#{ip})
    and event_time>date_sub(utc_timestamp(6),interval 10 minute)
""").n>=30) {
            exit 429,'认证尝试过多';
        }
        // 在事务内完成校验与写入；重复请求及并发版本冲突由共享事务规则处理。
        var outcome=sql.transaction(()=>{
            var a=sql.selectOne("""
select a.*,a.tid as id
from iam_auth_account_t a
join iam_subject_t s on s.tid=a.subject_id
and s.identity_domain=a.identity_domain
and s.status='ACTIVE'
and s.is_del=0
where a.identity_domain=#{domain}
    and a.active_username=#{username}
    and a.status='ACTIVE'
    for update
""");
            if(a==null) {
                return {
                    error:'INVALID'
                };
            }
            var sec=sql.selectOne("""
select *,tid as id,locked_until>utc_timestamp(6) locked
from iam_account_security_t
where principal_kind='AUTH_ACCOUNT'
    and principal_id=#{a.id}
    for update
""");
            if(sec==null || sec.locked==1) {
                return {
                    error:'LOCKED'
                };
            }
            var c=sql.selectOne("""
select *,tid as id
from iam_credential_t
where owner_kind='AUTH_ACCOUNT'
    and owner_id=#{a.id}
    and credential_type='PASSWORD'
    and status='ACTIVE'
""");
            var policy=sql.selectOne("""
select value_json
from iam_setting_t
where category='SECURITY'
    and config_key='password'
    and scope_key='global'
    and is_del=0
""");
            var config=policy==null?{}:JSONUtil.parseObj(policy.value_json);
            var threshold=config.lockThreshold||5;
            var minutes=config.lockMinutes||15;
            // 核对凭据时兼容已登记的摘要算法，失败只更新安全状态而不创建会话。
            if(c==null || !iamSecurity.verify(p.password,c.algorithm,c.secret_hash)) {
                sql.update("""
update iam_account_security_t
set failed_count=failed_count+1,locked_until=case
        when failed_count+1>=#{threshold} then date_add(utc_timestamp(6),interval #{minutes} minute) else null
        end
where tid=#{sec.id}
""");
                return {
                    error:'INVALID'
                };
            }
            sql.update("""
update iam_account_security_t
set failed_count=0,locked_until=null
where tid=#{sec.id}
""");
            return {
                id:a.id,version:sec.security_version
            };
        });
        // 记录操作主体、目标及结果，供审计与故障追踪。
        var trace=audit(outcome.id==null?null:{
            userId:outcome.id,actorKind:'AUTH_ACCOUNT',requiredDomain:domain
        },outcome.error==null?'SUCCESS':'FAILED','auth:account-login',outcome.id,outcome.error,null);
        sql.update("""
update iam_audit_event_t
set account_fingerprint=#{fingerprint}
where tid=#{trace}
""");
        if(outcome.error!=null) {
            exit 401,'认证账号或密码不正确';
        }
        var mfa=sql.selectOne("""
select tid as id
from iam_credential_t
where owner_kind='AUTH_ACCOUNT'
    and owner_id=#{outcome.id}
    and credential_type='TOTP'
    and status='ACTIVE'
""");
        if(mfa!=null) {
            return {
                mfaRequired:true,realm:'auth-account',challenge:iamMfa.challenge('AUTH_ACCOUNT',outcome.id,outcome.version,'LOGIN')
            };
        }
        var policy=sessionPolicy('issue',outcome.id);
        authSession.login(outcome.id,outcome.version,policy.idleSeconds,policy.allowConcurrent);
        return {
            token:authSession.token(),profile:toProfile(context().account)
        };
    }
    var ctx=context();
    var a=ctx.account;
    var id=a.id;
    if(operation!='password') {
        exit 400,'认证账号操作不正确';
    }
    passwordPolicy(p.password);
    var result=sql.transaction(()=>{
        var old=sql.selectOne("""
select *,tid as id
from iam_credential_t
where owner_kind='AUTH_ACCOUNT'
    and owner_id=#{id}
    and credential_type='PASSWORD'
    and status='ACTIVE'
    for update
""");
        if(old==null || !iamSecurity.verify(p.oldPassword,old.algorithm,old.secret_hash) || p.oldPassword==p.password) {
            exit 400,'原密码不正确或新密码没有改变';
        }
        var hash=iamSecurity.hash(p.password);
        var cid=IdUtil.fastSimpleUUID();
        var revision=old.revision+1;
        sql.update("""
update iam_credential_t
set status='RETIRED'
where tid=#{old.id}
""");
        sql.update("""
insert into iam_credential_t(tid,
                owner_kind, owner_id, credential_type, revision, algorithm, secret_hash, status)
values(#{cid},'AUTH_ACCOUNT',#{id},'PASSWORD',#{revision},'PBKDF2_SHA256',#{hash},'ACTIVE')
""");
        sql.update("""
update iam_auth_account_t
set must_change_password=0,version=version+1
where tid=#{id}
""");
        sql.update("""
update iam_account_security_t
set security_version=security_version+1,password_changed_time=utc_timestamp(6)
where principal_kind='AUTH_ACCOUNT'
    and principal_id=#{id}
""");
        sql.update("""
update iam_auth_session_t
set revoked_at=utc_timestamp(6),revoke_reason='PASSWORD_CHANGED'
where principal_kind='AUTH_ACCOUNT'
    and principal_id=#{id}
    and revoked_at is null
""");
        audit(ctx,'SUCCESS','auth:account-password',id,null,{
            id:id
        });
        return {
            relogin:true
        };
    });
    authSession.revoke(id);
    return result;

}));
