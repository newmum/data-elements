{
  "properties": {},
  "id": "d56ef41bf6e655aaae8554c7de8399e3",
  "script": null,
  "groupId": "863c54676e095a4aa6daf16770e35259",
  "name": "02.发起多因子绑定",
  "createTime": 1790635007936,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/setup",
  "description": "为当前认证账号生成待确认的多因子种子和一次性绑定信息，保存待验证状态。",
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
import iamSecurity;
import cn.hutool.core.util.IdUtil;
import identityRuntime;
import '@/idaas/api-call' as apiCall;

// POST /idaas/mfa/setup。核对本人密码后生成五分钟内可用的绑定材料，并使上次未完成绑定失效。

return identityRuntime.control(()=>apiCall(()=>{
    var proof=iamMfa.verifiedSession();
    var kind=proof.kind;
    var id=proof.id;
    var sql=db.normal();
    var p=body||{};
    return sql.transaction(()=>{
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
    });
}));
