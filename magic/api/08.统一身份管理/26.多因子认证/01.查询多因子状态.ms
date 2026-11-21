{
  "properties": {},
  "id": "f1817a5e1f1a55e3a6b0c3ed41985043",
  "script": null,
  "groupId": "863c54676e095a4aa6daf16770e35259",
  "name": "01.查询多因子状态",
  "createTime": 1790635007936,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/status",
  "description": "校验当前认证账号，读取多因子绑定状态和可用挑战方式；不返回私密种子。",
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
import identityRuntime;
import '@/idaas/api-call' as apiCall;

// POST /idaas/mfa/status。已验证平台或统一认证账号会话后，只读取本人动态口令绑定状态。

return identityRuntime.control(()=>apiCall(()=>{
    var proof=iamMfa.verifiedSession();
    var kind=proof.kind;
    var id=proof.id;
    var sql=db.normal();
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
        return {bound:credential!=null};
    });
}));
