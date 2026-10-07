{
  "properties": {},
  "id": "b8e9f119f73a5c5d80fef296a2d55079",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "23.协议访问授权校验",
  "createTime": 1790635007936,
  "updateTime": 1790949884838,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/protocol-access",
  "description": "验证统一认证令牌的应用受众、客户端状态和实时有效授权，拒绝已撤销访问。",
  "parameters": [
    {
      "name": "payload",
      "value": null,
      "description": "请求资料或筛选条件",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "returnType": null,
  "mappingPath": null
}
================================
// 共享函数：23.协议访问授权校验
// 业务说明：验证统一认证令牌的应用受众、客户端状态和实时有效授权，拒绝已撤销访问。
// 函数输入：payload：请求资料或筛选条件。
// 返回与副作用：验证统一认证令牌的应用受众、客户端状态和实时有效授权，拒绝已撤销访问。
import db;
import identityProtocol;
import authSession;
import '@/idaas/effective-access' as effective;
import '@/idaas/resource-enabled' as enabled;
import '@/idaas/auth-session-policy' as sessionPolicy;

// 只有框架协议桥接可调用此函数，普通 Magic 请求不能伪造认证主体。
if(!identityProtocol.isTrusted()) {
    exit 403,'认证主体判断只允许框架调用';
}
var sql=db.normal();
var p=payload||{};
var account=(id)=>sql.selectOne("""
select a.*,a.tid as id,s.status subject_status,s.is_del subject_deleted,sec.security_version
from iam_auth_account_t a
join iam_subject_t s on s.tid=a.subject_id
and s.identity_domain=a.identity_domain
join iam_account_security_t sec on sec.principal_kind='AUTH_ACCOUNT'
and sec.principal_id=a.tid
where a.tid=#{id}
    and a.status='ACTIVE'
    and a.is_del=0
""");
// 实时检查账号、人员及法人办理资格，不以令牌中的旧权限快照替代当前状态。
var eligible=(a)=>a!=null && a.subject_status=='ACTIVE' && a.subject_deleted==0 && a.must_change_password==0 && (a.legal_entity_id==null || sql.selectOne("""
select m.tid as id
from iam_legal_member_t m
join iam_legal_entity_t e on e.tid=m.entity_id
and e.status='ACTIVE'
and e.is_del=0
and e.verification_status='VERIFIED'
and exists
    (select 1
     from iam_verification_t v
     where v.target_type='LEGAL_ENTITY'
         and v.target_id=e.tid
         and v.status='VERIFIED'
         and v.expires_at>utc_timestamp(6)
         and v.tid=
             (select x.tid as id
              from iam_verification_t x
              where x.target_type='LEGAL_ENTITY'
                  and x.target_id=e.tid
              order by x.requested_time desc, x.tid desc
              limit 1))
where m.entity_id=#{a.legal_entity_id}
    and m.subject_id=#{a.subject_id}
    and (m.auth_account_id is null
         or m.auth_account_id=#{a.id})
    and m.status='ACTIVE'
    and m.is_del=0
    and m.valid_from<=utc_timestamp(6)
    and (m.valid_until is null
         or m.valid_until>utc_timestamp(6))
limit 1
""")!=null);
// 主体解析先核验账号和会话，再把可用的认证身份返回协议桥接层。
if(p.operation=='principal') {
    var policy=sessionPolicy('read',null);
    var principal=authSession.principal(p.token,policy.idleSeconds);
    if(principal.id==null) {
        return {
            allowed:false
        };
    }
    var a=account(principal.id);
    return eligible(a) && principal.securityVersion==a.security_version?{
        allowed:true,accountId:a.id,subjectId:a.subject_id,domain:a.identity_domain
    }
    :{
        allowed:false
    };
}
var client=sql.selectOne("""
select c.*,c.tid as id
from iam_auth_client_t c
join iam_application_t app on app.tid=c.app_id
and app.kind='BUSINESS'
and app.auth_mode='FEDERATED'
and app.status='ACTIVE'
and app.is_del=0
where c.tid=#{p.clientId}
    and c.status='ACTIVE'
    and c.protocol='OIDC'
    and c.is_del=0
""");
if(client==null) {
    return {
        allowed:false
    };
}
var finish=(result)=>{
    if(p.operation!='api') {
        return result;
    }
    if(!result.allowed || p.appId!=result.appId) {
        return {
            allowed:false
        };
    }
    var matched=sql.select("""
select tid as id
from iam_resource_t
where app_id=#{result.appId}
    and identity_domain=#{result.domain}
    and resource_type='API'
    and path=#{p.path}
    and http_method=#{p.method}
    and status='ACTIVE'
    and is_del=0
""").filter(r=>result.resourceIds.contains(r.id));
    if(matched.size()==0) {
        return {
            allowed:false
        };
    }
    return {
        allowed:true,appId:result.appId,domain:result.domain,principalId:result.principalId,principalKind:p.machine==true?'CLIENT':'AUTH_ACCOUNT',resourceIds:matched.map(r=>r.id)
    };
};
if(p.machine==true) {
    if(client.client_type!='SERVICE') {
        return {
            allowed:false
        };
    }
    var grants=sql.select("""
select tid as id,identity_domain
from iam_access_grant_t
where principal_type='CLIENT'
    and client_id=#{client.id}
    and app_id=#{client.app_id}
    and status='ACTIVE'
    and is_del=0
    and valid_from<=utc_timestamp(6)
    and (valid_until is null
         or valid_until>utc_timestamp(6))
""");
    if(grants.size()==0) {
        return {
            allowed:false
        };
    }
    var domains=[];
    for(g in grants) {
        if(!domains.contains(g.identity_domain)) {
            domains.add(g.identity_domain);
        }
    }
    if(domains.size()!=1) {
        return {
            allowed:false
        };
    }
    var domain=domains[0];
    var roles=sql.select("""
select distinct r.tid as id
from iam_grant_role_rela_t rr
join iam_access_grant_t g on g.tid=rr.grant_id
and g.principal_type='CLIENT'
and g.client_id=#{client.id}
and g.app_id=#{client.app_id}
and g.status='ACTIVE'
and g.is_del=0
and g.valid_from<=utc_timestamp(6)
and (g.valid_until is null
     or g.valid_until>utc_timestamp(6))
join iam_role_t r on r.tid=rr.role_id
and r.app_id=g.app_id
and r.status='ACTIVE'
and r.is_del=0
""").map(r=>r.id);
    var resources=sql.select("""
select distinct res.tid as id
from iam_resource_t res
join iam_role_resource_rela_t rr on rr.resource_id=res.tid
and rr.app_id=res.app_id
join iam_role_t r on r.tid=rr.role_id
and r.status='ACTIVE'
and r.is_del=0
join iam_grant_role_rela_t gr on gr.role_id=r.tid
join iam_access_grant_t g on g.tid=gr.grant_id
and g.client_id=#{client.id}
and g.principal_type='CLIENT'
and g.app_id=#{client.app_id}
and g.status='ACTIVE'
and g.is_del=0
and g.valid_from<=utc_timestamp(6)
and (g.valid_until is null
     or g.valid_until>utc_timestamp(6))
where res.app_id=#{client.app_id}
    and res.resource_type='API'
    and res.status='ACTIVE'
    and res.is_del=0
""").map(r=>r.id);
    resources=enabled(client.app_id,domain,resources);
    return finish({
        allowed:true,appId:client.app_id,domain:domain,roleIds:roles,resourceIds:resources,principalId:client.id,securityVersion:client.version
    });
}
var a=account(p.principalName);
if(!eligible(a)) {
    return {
        allowed:false
    };
}
// 访问令牌最终权限以当前应用的实时有效授权为准，撤销后立即失效。
var access=effective(a.subject_id,client.app_id,a.identity_domain);
return finish({
    allowed:access.hasAccess,appId:client.app_id,accountId:a.id,subjectId:a.subject_id,domain:a.identity_domain,roleIds:access.roleIds,resourceIds:access.resourceIds,principalId:a.id,securityVersion:a.security_version
});
