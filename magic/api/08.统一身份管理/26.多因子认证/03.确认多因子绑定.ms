{
  "properties": {},
  "id": "716347fdd5f258c1b7176786483b6286",
  "script": null,
  "groupId": "863c54676e095a4aa6daf16770e35259",
  "name": "03.确认多因子绑定",
  "createTime": 1790635007936,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/bind",
  "description": "核验当前账号提交的一次性口令与待绑定种子，成功后启用多因子并记录安全事件。",
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
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.core.util.IdUtil;
import identityRuntime;
import '@/idaas/api-call' as apiCall;

// POST /idaas/mfa/bind。验证本人待绑定口令，保存绑定及恢复码，随后撤销旧会话。

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
    and status='PENDING'
    and expires_at>utc_timestamp(6)
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
        var encoded=JSONUtil.toJsonStr({
            lastStep:step
        });
        sql.update("""
update iam_credential_t
set status='ACTIVE',slot='primary',expires_at=null,public_data=#{encoded}
where tid=#{credential.id}
""");
        var codes=[];
        sql.update("""
update iam_credential_t
set status='RETIRED'
where owner_kind=#{kind}
    and owner_id=#{id}
    and credential_type='RECOVERY_CODE'
    and status='ACTIVE'
""");
        // 固定生成十个一次性恢复码，用绑定参数一次写入摘要。
        var recoveryValues=[];
        var recoveryParams={kind:kind,id:id};
        for(n in [1,2,3,4,5,6,7,8,9,10]) {
            var raw=IdUtil.fastSimpleUUID();
            codes.add(raw);
            var idKey='recoveryId'+n;
            var hashKey='recoveryHash'+n;
            recoveryParams[idKey]=IdUtil.fastSimpleUUID();
            recoveryParams[hashKey]=DigestUtil.sha256Hex(raw);
            recoveryValues.add("(#{"+idKey+"},#{kind},#{id},'RECOVERY_CODE',#{"+idKey
                +"},'SHA256_RANDOM128',#{"+hashKey+"},'ACTIVE')");
        }
        sql.update("""
insert into iam_credential_t(tid,
    owner_kind, owner_id, credential_type, slot, algorithm, secret_hash, status)
values """+recoveryValues.join(','),recoveryParams);
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
        audit(ctx,'SUCCESS','auth:mfa-bind',id,null,{
            id:id
        });
        return {
            relogin:true,recoveryCodes:codes
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
