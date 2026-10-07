{
  "properties": {},
  "id": "f919bae96dc45a398e97ad8bfbdf2dfc",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "22.协议客户端共用规则",
  "createTime": 1790635007936,
  "updateTime": 1790949884838,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/protocol-client",
  "description": "校验协议客户端、回调和凭据轮换规则，供客户端配置及认证接口复用。",
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
// 共享函数：22.协议客户端共用规则
// 业务说明：校验协议客户端、回调和凭据轮换规则，供客户端配置及认证接口复用。
// 函数输入：payload：请求资料或筛选条件。
// 返回与副作用：校验协议客户端、回调和凭据轮换规则，供客户端配置及认证接口复用。
import db;
import identityProtocol;
import cn.hutool.json.JSONUtil;
import java.lang.Math;
import '@/idaas/auth-session-policy' as sessionPolicy;

// 协议配置仅供可信框架读取，避免普通接口获取客户端密钥摘要。
if(!identityProtocol.isTrusted()) {
    exit 403,'协议配置只允许框架读取';
}
var p=payload||{};
var sql=db.normal();
// 仅选择已启用业务应用下的已启用 OIDC 客户端。
var c=sql.selectOne("""
select c.*,c.tid as id
from iam_auth_client_t c
join iam_application_t a on a.tid=c.app_id
and a.kind='BUSINESS'
and a.auth_mode='FEDERATED'
and a.status='ACTIVE'
and a.is_del=0
where c.is_del=0
    and c.status='ACTIVE'
    and c.protocol='OIDC'
    and (c.tid=#{p.id}
         or c.client_key=#{p.clientKey})
""");
if(c==null) {
    return {
        found:false
    };
}
// 机密客户端必须存在有效凭据；公有客户端无需客户端密钥。
var key=sql.selectOne("""
select secret_hash
from iam_credential_t
where owner_kind='CLIENT'
    and owner_id=#{c.id}
    and credential_type='CLIENT_SECRET'
    and status='ACTIVE'
    and (expires_at is null
         or expires_at>utc_timestamp(6))
""");
if(c.client_type!='PUBLIC' && key==null) {
    return {
        found:false
    };
}
var redirects=sql.select("""
select uri,uri_type
from iam_auth_redirect_t
where client_id=#{c.id}
""");
var policy=sessionPolicy('read',null);
return {
    found:true,id:c.id,appId:c.app_id,clientKey:c.client_key,name:c.name,clientType:c.client_type,secretHash:key==null?null:key.secret_hash,grants:JSONUtil.parseArray(c.grant_types),scopes:JSONUtil.parseArray(c.scopes),redirects:redirects.filter(r=>r.uri_type=='LOGIN').map(r=>r.uri),logoutRedirects:redirects.filter(r=>r.uri_type=='LOGOUT').map(r=>r.uri),accessTtl:Math.min(c.access_ttl_seconds,policy.accessTtl),refreshTtl:Math.min(c.refresh_ttl_seconds,policy.refreshTtl)
};
