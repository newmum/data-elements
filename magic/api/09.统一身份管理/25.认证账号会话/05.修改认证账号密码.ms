{
  "properties": {},
  "id": "3532f978386e5de2ac75a50c0aee3fbd",
  "script": null,
  "groupId": "0d24c0ffb0bf58e59cf30e12786d94fe",
  "name": "05.修改认证账号密码",
  "createTime": 1790635007936,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/password",
  "description": "核验当前统一认证会话与原密码，执行密码策略并更新安全版本，使旧会话失效。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：05.修改认证账号密码
// 路由：POST /idaas/auth-account/password
// 业务说明：核验当前统一认证会话与原密码，执行密码策略并更新安全版本，使旧会话失效。
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
    var operation = 'password';

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
    var ctx=context();
    var a=ctx.account;
    var id=a.id;
    if(operation!='password') {
        exit 400,'认证账号操作不正确';
    }
    passwordPolicy(p.password);
    // 在事务内完成校验与写入；重复请求及并发版本冲突由共享事务规则处理。
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
        // 核对凭据时兼容已登记的摘要算法，失败只更新安全状态而不创建会话。
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
        // 记录操作主体、目标及结果，供审计与故障追踪。
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
