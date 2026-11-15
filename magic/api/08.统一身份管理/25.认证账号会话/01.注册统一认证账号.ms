{
  "properties": {},
  "id": "c1eeec0de90e5f5fac276c075e9bfae5",
  "script": null,
  "groupId": "0d24c0ffb0bf58e59cf30e12786d94fe",
  "name": "01.注册统一认证账号",
  "createTime": 1790635007936,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/register",
  "description": "校验注册策略、账号唯一性和密码强度，在独立认证账号表创建账号与安全记录；不创建平台操作账号或租户账号。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：01.注册统一认证账号
// 路由：POST /idaas/auth-account/register
// 业务说明：校验注册策略、账号唯一性和密码强度，在独立认证账号表创建账号与安全记录；不创建平台操作账号或租户账号。
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
    var operation = 'register';

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
    if(operation=='register') {
        if(domain!='public' || p.consent!=true || p.requestId==null || !p.requestId.matches('[A-Za-z0-9_-]{8,64}')) {
            exit 400,'请同意注册条款并提供有效申请号';
        }
        var username=(p.username||'').trim().toLowerCase();
        var name=(p.name||'').trim();
        if(!username.matches('[A-Za-z][A-Za-z0-9_.-]{2,99}') || name=='' || name.length()>100 || (p.phone||'').length()>128 || (p.email||'').length()>255) {
            exit 400,'注册资料不正确';
        }
        passwordPolicy(p.password);
        var ip=request.getClientIP();
        if(sql.selectOne("""
select count(*) n
from iam_audit_event_t
where action='auth:public-register'
    and ip=#{ip}
    and event_time>date_sub(utc_timestamp(6),interval 30 minute)
""").n>=20) {
            exit 429,'注册申请过于频繁';
        }
        // 把读取校验和状态写入放在同一事务，防止并发覆盖。
        var result=sql.transaction(()=>{
            sql.selectOne("""
select tid as id
from iam_application_t
where tid='idaas-platform-internal'
    for update
""");
            var principal=DigestUtil.sha256Hex('register:'+username).substring(0,32);
            var hash=identityTransport.protectedFingerprint(DigestUtil.sha256Hex(JSONUtil.toJsonStr(p)),'receipt:v2');
            var prior=sql.selectOne("""
select *,tid as id
from iam_request_receipt_t
where operator_id=#{principal}
    and operation_code='PUBLIC_REGISTER'
    and request_id=#{p.requestId}
    for update
""");
            if(prior!=null) {
                if(prior.request_hash!=hash) {
                    exit 409,'同一申请号资料不一致';
                }
                return JSONUtil.parseObj(prior.result_json);
            }
            if(sql.selectOne("""
select tid as id
from iam_auth_account_t
where identity_domain='public'
    and active_username=#{username}
""")!=null) {
                exit 409,'此认证账号已注册';
            }
            var sid=IdUtil.fastSimpleUUID();
            var aid=IdUtil.fastSimpleUUID();
            var code='PUBLIC_'+sid;
            sql.update("""
insert into iam_subject_t(tid,
                identity_domain, subject_code, account_hint, name, phone, email, status)
values(#{sid},'public',#{code},#{username},#{name},#{p.phone},#{p.email},'ACTIVE')
""");
            sql.update("""
insert into iam_auth_account_t(tid,
                subject_id, identity_domain, username, status)
values(#{aid},#{sid},'public',#{username},'ACTIVE')
""");
            var security=IdUtil.fastSimpleUUID();
            sql.update("""
insert into iam_account_security_t(tid, principal_kind, principal_id)
values(#{security},'AUTH_ACCOUNT',#{aid})
""");
            var cid=IdUtil.fastSimpleUUID();
            var digest=iamSecurity.hash(p.password);
            sql.update("""
insert into iam_credential_t(tid,
                owner_kind, owner_id, credential_type, algorithm, secret_hash, status)
values(#{cid},'AUTH_ACCOUNT',#{aid},'PASSWORD','PBKDF2_SHA256',#{digest},'ACTIVE')
""");
            var profile=IdUtil.fastSimpleUUID();
            sql.update("""
insert into iam_public_profile_t(tid, subject_id)
values(#{profile},#{sid})
""");
            var receipt=IdUtil.fastSimpleUUID();
            var encoded=JSONUtil.toJsonStr({
                id:aid,subjectId:sid,domain:'public',verificationStatus:'UNVERIFIED'
            });
            sql.update("""
insert into iam_request_receipt_t(tid,
                operator_id, operation_code, request_id, request_hash, status, result_json)
values(#{receipt},#{principal},'PUBLIC_REGISTER',#{p.requestId},#{hash},'SUCCEEDED',#{encoded})
""");
            // 记录操作主体、目标及结果，供审计与故障追踪。
            audit({
                userId:aid,actorKind:'AUTH_ACCOUNT',requiredDomain:'public'
            },'SUCCESS','auth:public-register',sid,null,{
                id:aid
            });
            return JSONUtil.parseObj(encoded);
        });
        return result;
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
