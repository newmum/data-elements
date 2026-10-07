{
  "properties": {},
  "id": "16fe18be7f815f8a90784acaa3a32b12",
  "script": null,
  "groupId": "039920aa11d55b4faed04ad7b00bbf94",
  "name": "01.提交密码找回申请",
  "createTime": 1791193400395,
  "updateTime": 1791193400395,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/request",
  "description": "公开提交管理员核验申请，统一回执防止账号枚举；按来源和账号限频并记录脱敏审计。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：提交管理员核验的密码找回申请；统一回执不暴露账号是否存在。
// 路由：POST /idaas/password-recovery/request
import db;
import identityRuntime;
import request;
import '@/idaas/api-call' as apiCall;
import '@/idaas/audit' as audit;

return identityRuntime.control(()=>apiCall(()=>{
    var p=body||{};
    var realm=p.realm||'platform';
    var domain=realm=='platform'?'workforce':p.domain||'public';
    var username=(p.username||'').trim().toLowerCase();
    if(!['platform','auth-account'].contains(realm) || !['workforce','public'].contains(domain)
        || username.length()<3 || username.length()>100
        || !username.matches('[A-Za-z][A-Za-z0-9_.-]{2,99}')) {
        exit 400,'请输入有效账号';
    }
    var sql=db.normal();
    var ip=request.getClientIP();
    var generic={accepted:true,message:'申请已提交。如账号存在，请联系对应管理员完成身份核验和重置。'};
    var recent=sql.selectOne("""
select count(*) n from iam_audit_event_t
where action='password:recovery-request' and ip=#{ip}
  and event_time>date_sub(utc_timestamp(6),interval 1 hour)
""");
    if(recent.n>=20) {
        return generic;
    }
    var principal=realm=='platform'?sql.selectOne("""
select tid as id from iam_operator_t
where active_username=#{username} and status='ACTIVE' and is_del=0
"""):sql.selectOne("""
select tid as id from iam_auth_account_t
where active_username=#{username} and identity_domain=#{domain}
  and status='ACTIVE' and is_del=0
""");
    var kind=realm=='platform'?'OPERATOR':'AUTH_ACCOUNT';
    var principalId=principal==null?'__unknown__':principal.id;
    var duplicate=sql.selectOne("""
select count(*) n from iam_audit_event_t
where action='password:recovery-request' and object_id=#{principalId}
  and reason_code=#{kind}
  and event_time>date_sub(utc_timestamp(6),interval 1 hour)
""");
    if(principal==null) {
        audit({userId:null,actorKind:'ANONYMOUS',requiredDomain:domain},'DENIED',
            'password:recovery-request',null,'UNKNOWN',{channel:'ADMIN_VERIFICATION'});
        return generic;
    }
    if(duplicate.n>=1) {
        return generic;
    }
    audit({userId:null,actorKind:'ANONYMOUS',requiredDomain:domain},'SUCCESS',
        'password:recovery-request',principal.id,kind,{channel:'ADMIN_VERIFICATION'});
    return generic;
}));
