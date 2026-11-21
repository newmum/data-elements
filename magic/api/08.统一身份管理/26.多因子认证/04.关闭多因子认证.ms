{
  "properties": {},
  "id": "ba8ca2ef66bd58489112eda1c6cbf90a",
  "script": null,
  "groupId": "863c54676e095a4aa6daf16770e35259",
  "name": "04.关闭多因子认证",
  "createTime": 1790635007936,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/disable",
  "description": "核验当前账号及必要的二次认证后关闭多因子绑定，提升安全版本并记录审计。",
  "parameters": [],
  "method": "POST",
  "options": [
    {
      "name": "access",
      "value": "MFA_SESSION"
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
import '@/idaas/audit' as audit;
import identityRuntime;
import '@/idaas/api-call' as apiCall;

// POST /idaas/mfa/disable。验证已绑定口令及平台策略后关闭本人多因子认证，并撤销旧会话。

return identityRuntime.control(()=>apiCall(()=>{
    var proof=iamMfa.verifiedSession();
    var kind=proof.kind;
    var id=proof.id;
    var sql=db.normal();
    var ctx=proof.context;
    var p=body||{};
    var result=sql.transaction(()=>{
        var sec=sql.selectOne("""
select *,tid as id
from iam_account_security_t
where principal_kind=#{kind}
    and principal_id=#{id}
    for update
""");
        if(sec==null) {
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
        var step=iamMfa.matchingStep(secret,p.code);
        var metadata=credential.public_data==null?{}:JSONUtil.parseObj(credential.public_data);
        if(step<0 || metadata.lastStep!=null && step<=metadata.lastStep) {
            exit 401,'动态口令不正确或已被使用';
        }
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
        audit(ctx,'SUCCESS','auth:mfa-disable',id,null,{
            id:id
        });
        return {
            relogin:true,recoveryCodes:[]
        };
    });
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
