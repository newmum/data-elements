{
  "properties": {},
  "id": "53ef5b91495854feaf2420d17dfa1677",
  "script": null,
  "groupId": "863c54676e095a4aa6daf16770e35259",
  "name": "05.验证登录二次口令",
  "createTime": 1790635007936,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/verify-login",
  "description": "校验登录挑战、时间窗内的一次性口令和重放状态，成功后完成认证会话签发。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：05.验证登录二次口令
// 路由：POST /idaas/mfa/verify-login
// 业务说明：校验登录挑战、时间窗内的一次性口令和重放状态，成功后完成认证会话签发。
import db;
import platformSession;
import authSession;
import identityTransport;
import iamSecurity;
import iamMfa;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.crypto.digest.DigestUtil;
import '@/idaas/context' as platformContext;
import '@/idaas/auth-account-context' as accountContext;
import '@/idaas/session' as sessionInfo;
import '@/idaas/audit' as audit;
import '@/idaas/auth-session-policy' as sessionPolicy;
import identityRuntime;
import request;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    var payload=body;
    // 请求参数与本接口业务处理。
    var operation = 'verify-login';

    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
    var ticket=null;
    var ctx=null;
    var kind=null;
    var id=null;
    if(operation=='verify-login') {
        ticket=iamMfa.attempt(p.challenge,'LOGIN');
        if(ticket.id==null) {
            exit 401,'认证挑战已失效，请重新登录';
        }
        kind=ticket.kind;
        id=ticket.id;
    }
    else {
        ctx=p.realm=='auth-account'?accountContext():platformContext();
        kind=p.realm=='auth-account'?'AUTH_ACCOUNT':'OPERATOR';
        id=ctx.userId;
    }
    // 把读取校验和状态写入放在同一事务，防止并发覆盖。
    var result=sql.transaction(()=>{
        var sec=sql.selectOne("""
select *,tid as id
from iam_account_security_t
where principal_kind=#{kind}
    and principal_id=#{id}
    for update
""");
        if(sec==null || ticket!=null && sec.security_version!=ticket.version) {
            exit 401,'账号安全状态已变更';
        }
        var credential=sql.selectOne("""
select *,tid as id
from iam_credential_t
where owner_kind=#{kind}
    and owner_id=#{id}
    and credential_type='TOTP'
    and status='ACTIVE'
    for update
""");
        if(operation=='status') {
            return {
                bound:credential!=null
            };
        }
        if(operation=='setup') {
            var password=sql.selectOne("""
select *,tid as id
from iam_credential_t
where owner_kind=#{kind}
    and owner_id=#{id}
    and credential_type='PASSWORD'
    and status='ACTIVE'
""");
            // 核对凭据时兼容已登记的摘要算法，失败只更新安全状态而不创建会话。
            if(password==null || !iamSecurity.verify(p.password,password.algorithm,password.secret_hash) || credential!=null) {
                exit 400,'密码不正确或已绑定动态口令';
            }
            var secret=iamMfa.secret();
            var cipher=identityTransport.protectValue(secret,'MFA:'+kind+':'+id);
            sql.update("""
update iam_credential_t
set status='RETIRED'
where owner_kind=#{kind}
    and owner_id=#{id}
    and credential_type='TOTP'
    and status='PENDING'
""");
            var cid=IdUtil.fastSimpleUUID();
            var revision=sql.selectOne("""
select coalesce(max(revision), 0)+1 revision
from iam_credential_t
where owner_kind=#{kind}
    and owner_id=#{id}
    and credential_type='TOTP'
""").revision;
            sql.update("""
insert into iam_credential_t(tid,
                owner_kind, owner_id, credential_type, slot, revision, algorithm, secret_cipher, status,
                expires_at)
values(#{cid},#{kind},#{id},'TOTP','pending',#{revision},'RFC6238_SHA1',#{cipher},'PENDING',date_add(utc_timestamp(6),interval 5 minute))
""");
            return {
                id:cid,secret:secret,uri:'otpauth://totp/%E7%BB%9F%E4%B8%80%E8%BA%AB%E4%BB%BD%E7%AE%A1%E7%90%86%E5%B9%B3%E5%8F%B0:'+id+'?secret='+secret+'&issuer=%E7%BB%9F%E4%B8%80%E8%BA%AB%E4%BB%BD%E7%AE%A1%E7%90%86%E5%B9%B3%E5%8F%B0&digits=6&period=30'
            };
        }
        if(operation=='bind') {
            credential=sql.selectOne("""
select *,tid as id
from iam_credential_t
where owner_kind=#{kind}
    and owner_id=#{id}
    and credential_type='TOTP'
    and status='PENDING'
    and expires_at>utc_timestamp(6)
    for update
""");
        }
        if(credential==null) {
            exit 400,'未设置有效动态口令';
        }
        var secret=identityTransport.unprotectValue(credential.secret_cipher,'MFA:'+kind+':'+id);
        var recovery=null;
        if(operation=='verify-login' && p.code!=null && p.code.matches('[a-f0-9]{32}')) {
            var hash=DigestUtil.sha256Hex(p.code);
            recovery=sql.selectOne("""
select *,tid as id
from iam_credential_t
where owner_kind=#{kind}
    and owner_id=#{id}
    and credential_type='RECOVERY_CODE'
    and secret_hash=#{hash}
    and status='ACTIVE'
    for update
""");
        }
        var step=iamMfa.matchingStep(secret,p.code);
        var metadata=credential.public_data==null?{}:JSONUtil.parseObj(credential.public_data);
        if(recovery==null && (step<0 || metadata.lastStep!=null && step<=metadata.lastStep)) {
            exit 401,'动态口令不正确或已被使用';
        }
        if(operation=='verify-login') {
            if(!iamMfa.consume(p.challenge)) {
                exit 401,'认证挑战已消费';
            }
            if(recovery!=null) {
                sql.update("""
update iam_credential_t
set status='REVOKED',updated_time=utc_timestamp(6),version=version+1
where tid=#{recovery.id}
""");
            }
            else {
                var encoded=JSONUtil.toJsonStr({
                    lastStep:step
                });
                sql.update("""
update iam_credential_t
set public_data=#{encoded},updated_time=utc_timestamp(6),version=version+1
where tid=#{credential.id}
""");
            }
            // 记录操作主体、目标及结果，供审计与故障追踪。
            audit({
                userId:id,actorKind:kind=='OPERATOR'?'OPERATOR':'AUTH_ACCOUNT'
            },'SUCCESS','auth:mfa-login',id,null,{
                id:id
            });
            return {
                login:true,version:sec.security_version
            };
        }
        if(operation=='disable') {
            var policy=sql.selectOne("""
select value_json
from iam_setting_t
where category='SECURITY'
    and config_key='password'
    and scope_key='global'
    and is_del=0
""");
            if(policy!=null && JSONUtil.parseObj(policy.value_json).mfaRequired==true) {
                exit 409,'平台强制动态口令策略已启用，不能解绑';
            }
            sql.update("""
update iam_credential_t
set status='RETIRED'
where owner_kind=#{kind}
    and owner_id=#{id}
    and credential_type in ('TOTP','RECOVERY_CODE')
    and status='ACTIVE'
""");
        }
        else if(operation=='bind') {
            var encoded=JSONUtil.toJsonStr({
                lastStep:step
            });
            sql.update("""
update iam_credential_t
set status='ACTIVE',slot='primary',expires_at=null,public_data=#{encoded}
where tid=#{credential.id}
""");
        }
        else {
            exit 400,'动态口令操作不正确';
        }
        var codes=[];
        if(operation=='bind') {
            sql.update("""
update iam_credential_t
set status='RETIRED'
where owner_kind=#{kind}
    and owner_id=#{id}
    and credential_type='RECOVERY_CODE'
    and status='ACTIVE'
""");
            for(n in [1,2,3,4,5,6,7,8,9,10]) {
                var raw=IdUtil.fastSimpleUUID();
                codes.add(raw);
                var hash=DigestUtil.sha256Hex(raw);
                var cid=IdUtil.fastSimpleUUID();
                sql.update("""
insert into iam_credential_t(tid,
                owner_kind, owner_id, credential_type, slot, algorithm, secret_hash, status)
values(#{cid},#{kind},#{id},'RECOVERY_CODE',#{cid},'SHA256_RANDOM128',#{hash},'ACTIVE')
""");
            }
        }
        sql.update("""
update iam_account_security_t
set security_version=security_version+1
where tid=#{sec.id}
""");
        sql.update("""
update iam_auth_session_t
set revoked_at=utc_timestamp(6),revoke_reason='MFA_CHANGED'
where principal_kind=#{kind}
    and principal_id=#{id}
    and revoked_at is null
""");
        audit(ctx,'SUCCESS','auth:mfa-'+operation,id,null,{
            id:id
        });
        return {
            relogin:true,recoveryCodes:codes
        };
    });
    if(result.login==true) {
        if(kind=='OPERATOR') {
            // 密码及附加挑战通过后才签发独立的平台会话。
            platformSession.login(id,1800);
            platformSession.session().set('securityVersion',result.version);
            return {
                token:platformSession.token(),session:sessionInfo()
            };
        }
        var policy=sessionPolicy('issue',id);
        authSession.login(id,result.version,policy.idleSeconds,policy.allowConcurrent);
        return {
            token:authSession.token(),realm:'auth-account'
        };
    }
    if(result.relogin==true) {
        if(kind=='OPERATOR') {
            platformSession.revoke(id);
        }
        else {
            authSession.revoke(id);
        }
    }
    return result;

}));
