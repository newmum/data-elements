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
  "path": "/verify",
  "description": "校验登录挑战、时间窗内的一次性口令和重放状态，成功后完成认证会话签发。",
  "parameters": [],
  "method": "POST",
  "options": [
    {
      "name": "access",
      "value": "MFA_LOGIN_CHALLENGE"
    }
  ],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
import db;
import iamMfa;
import identityTransport;
import cn.hutool.json.JSONUtil;
import platformSession;
import authSession;
import cn.hutool.crypto.digest.DigestUtil;
import '@/idaas/audit' as audit;
import '@/idaas/session' as sessionInfo;
import '@/idaas/auth-session-policy' as sessionPolicy;
import identityRuntime;
import '@/idaas/api-call' as apiCall;

// POST /idaas/mfa/verify。使用已校验的登录挑战验证动态口令或恢复码，成功后签发对应身份域的会话。

return identityRuntime.control(()=>apiCall(()=>{
    var ticket=iamMfa.verifiedChallenge();
    var kind=ticket.kind;
    var id=ticket.id;
    var p=body||{};
    var sql=db.normal();
    var result=sql.transaction(()=>{
        var sec=sql.selectOne("""
select *,tid as id
from iam_account_security_t
where principal_kind=#{kind}
    and principal_id=#{id}
    for update
""");
        if(sec==null || sec.security_version!=ticket.version) {
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
        if(credential==null) {
            exit 400,'未设置有效动态口令';
        }
        var secret=identityTransport.unprotectValue(credential.secret_cipher,'MFA:'+kind+':'+id);
        var recovery=null;
        if(p.code!=null && p.code.matches('[a-f0-9]{32}')) {
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
        },'ACCEPTED','auth:mfa-verified',id,null,{
            id:id
        });
        return {
            login:true,version:sec.security_version
        };
    });
    if(result.login==true) {
        if(kind=='OPERATOR') {
            // 密码及附加挑战通过后才签发独立的平台会话。
            platformSession.login(id,1800);
            platformSession.session().set('securityVersion',result.version);
            try {
                audit({userId:id,actorKind:'OPERATOR'},'SUCCESS','auth:login',id,null,null);
                return {token:platformSession.token(),session:sessionInfo()};
            } catch(error) { platformSession.logout(); throw error; }
        }
        var policy=sessionPolicy('issue',id);
        authSession.login(id,result.version,policy.idleSeconds,policy.allowConcurrent);
        try {
            audit({userId:id,actorKind:'AUTH_ACCOUNT'},'SUCCESS','auth:account-login',id,null,null);
            return {token:authSession.token(),realm:'auth-account'};
        } catch(error) { authSession.logout(); throw error; }
    }
}));
