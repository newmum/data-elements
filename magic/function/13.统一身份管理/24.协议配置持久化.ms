{
  "properties": {},
  "id": "3abcf6298afb5031ab99f4c9897f4809",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "24.协议配置持久化",
  "createTime": 1790635007936,
  "updateTime": 1790949884838,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/protocol-store",
  "description": "以控制库保存或读取协议客户端与联邦配置，保护敏感凭据和版本。",
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
// 共享函数：24.协议配置持久化
// 业务说明：以控制库保存或读取协议客户端与联邦配置，保护敏感凭据和版本。
// 函数输入：payload：请求资料或筛选条件。
// 返回与副作用：以控制库保存或读取协议客户端与联邦配置，保护敏感凭据和版本。
import db;
import identityProtocol;
import identityTransport;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import '@/idaas/protocol-access' as access;

// 框架状态、令牌摘要和轮换族均留在baseline；不向业务应用用户表写入凭据。
if(!identityProtocol.isTrusted()) {
    exit 403,'协议状态只允许框架调用';
}
var sql=db.normal();
var p=payload||{};
var op=p.operation;
var hash=(v)=>v==null?null:DigestUtil.sha256Hex(v);
if(op.endsWith('consent')) {
    var key='consent:'+p.clientId+':'+p.principalName;
    var fingerprint=hash(key);
    if(op=='find-consent') {
        var row=sql.selectOne("""
select *,tid as id
from iam_auth_session_t
where session_fingerprint=#{fingerprint}
    and revoked_at is null
    and expires_at>utc_timestamp(6)
""");
        return row==null?{}:identityTransport.parseJson(identityTransport.unprotectState(row.authorization_cipher,key));
    }
    // 把读取校验和状态写入放在同一事务，防止并发覆盖。
    return sql.transaction(()=>{
        if(op=='remove-consent') {
            sql.update("""
update iam_auth_session_t
set revoked_at=utc_timestamp(6),revoke_reason='CONSENT_REVOKED'
where session_fingerprint=#{fingerprint}
""");
            return {
                removed:true
            };
        }
        var id=IdUtil.fastSimpleUUID();
        var encoded=identityTransport.protectState(identityTransport.canonicalJson({
            authorities:p.authorities
        }),key);
        sql.update("""
insert into iam_auth_session_t(tid,
                principal_kind, principal_id, client_id, session_fingerprint, security_version, issued_at,
                expires_at, authorization_key, authorization_cipher)
values(#{id},'AUTH_ACCOUNT',#{p.principalName},#{p.clientId},#{fingerprint},1,utc_timestamp(6),date_add(utc_timestamp(6),interval 30 day),#{key},#{encoded}) on duplicate key
update authorization_cipher=#{encoded},revoked_at=null,expires_at=date_add(utc_timestamp(6),interval 30 day),version=version+1
""");
        return {
            saved:true
        };
    });
}
if(op=='remove') {
    sql.update("""
update iam_auth_session_t
set revoked_at=utc_timestamp(6),revoke_reason='PROTOCOL_REVOKED',version=version+1
where authorization_key=#{p.id}
""");
    return {
        removed:true
    };
}
if(op=='save' && p.revoked==true) {
    sql.update("""
update iam_auth_session_t
set revoked_at=utc_timestamp(6),revoke_reason='TOKEN_REVOKED',version=version+1
where authorization_key=#{p.id}
    and revoked_at is null
""");
    return {
        saved:true
    };
}
if(op=='find') {
    var tokenHash=hash(p.token);
    var row=p.id!=null?sql.selectOne("""
select *,tid as id
from iam_auth_session_t
where authorization_key=#{p.id}
"""):sql.selectOne("""
select *,tid as id
from iam_auth_session_t
where code_fingerprint=#{tokenHash}
    or access_fingerprint=#{tokenHash}
    or refresh_fingerprint=#{tokenHash}
    or state_fingerprint=#{tokenHash}
limit 1
""");
    if(row==null && p.token!=null) {
        var consumed=sql.selectOne("""
select result_json
from iam_request_receipt_t
where operation_code='PROTOCOL_REFRESH_CONSUMED'
    and request_id=#{tokenHash}
""");
        if(consumed!=null) {
            var family=identityTransport.parseJson(consumed.result_json).family;
            sql.update("""
update iam_auth_session_t
set revoked_at=utc_timestamp(6),revoke_reason='REFRESH_REPLAY',version=version+1
where family_id=#{family}
""");
        }
        return {};
    }
    if(row==null || row.revoked_at!=null || sql.selectOne("""
select tid as id
from iam_auth_session_t
where tid=#{row.id}
    and expires_at>utc_timestamp(6)
""")==null) {
        return {};
    }
    var ok=access({
        operation:'issue',clientId:row.client_id,principalName:row.principal_id,machine:row.principal_kind=='CLIENT'
    });
    if(!ok.allowed || ok.securityVersion!=row.security_version) {
        return {};
    }
    return {
        state:identityTransport.unprotectState(row.authorization_cipher,row.authorization_key)
    };
}
if(op!='save') {
    exit 400,'协议状态操作不正确';
}
return sql.transaction(()=>{
    var permission=access({
        operation:'issue',clientId:p.clientId,principalName:p.principalName,machine:p.grant=='client_credentials'
    });
    if(!permission.allowed) {
        exit 403,'认证资格已失效';
    }
    var old=sql.selectOne("""
select *,tid as id
from iam_auth_session_t
where authorization_key=#{p.id}
    for update
""");
    if(old!=null && old.revoked_at!=null) {
        exit 403,'认证会话已撤销';
    }
    var id=old==null?IdUtil.fastSimpleUUID():old.id;
    var principal=permission.principalId;
    var kind=p.grant=='client_credentials'?'CLIENT':'AUTH_ACCOUNT';
    var fingerprint=hash('authorization:'+p.id);
    var code=hash(p.code);
    var token=hash(p.access);
    var refresh=hash(p.refresh);
    var stateHash=hash(p.stateValue);
    var cipher=identityTransport.protectState(p.state,p.id);
    var family=old==null?IdUtil.fastSimpleUUID():old.family_id;
    var client=sql.selectOne("""
select access_ttl_seconds,refresh_ttl_seconds
from iam_auth_client_t
where tid=#{p.clientId}
""");
    var ttl=p.refresh==null?client.access_ttl_seconds:client.refresh_ttl_seconds;
    if(old!=null && old.refresh_fingerprint!=null && old.refresh_fingerprint!=refresh) {
        var rid=IdUtil.fastSimpleUUID();
        var result=identityTransport.canonicalJson({
            family:family
        });
        sql.update("""
insert into iam_request_receipt_t(tid,
                operator_id, operation_code, request_id, request_hash, status, result_json)
values(#{rid},#{principal},'PROTOCOL_REFRESH_CONSUMED',#{old.refresh_fingerprint},#{old.refresh_fingerprint},'SUCCEEDED',#{result}) on duplicate key
update tid=tid
""");
    }
    if(old==null) {
        sql.update("""
insert into iam_auth_session_t(tid,
                principal_kind, principal_id, client_id, app_id, session_fingerprint, family_id,
                security_version, issued_at, expires_at, authorization_key, authorization_cipher, code_fingerprint,
                access_fingerprint, refresh_fingerprint, state_fingerprint)
values(#{id},#{kind},#{principal},#{p.clientId},#{permission.appId},#{fingerprint},#{family},#{permission.securityVersion},utc_timestamp(6),date_add(utc_timestamp(6),interval #{ttl} second),#{p.id},#{cipher},#{code},#{token},#{refresh},#{stateHash})
""");
    }
    else {
        sql.update("""
update iam_auth_session_t
set authorization_cipher=#{cipher},code_fingerprint=#{code},access_fingerprint=#{token},
    previous_refresh_fingerprint=case
        when refresh_fingerprint<>#{refresh} then refresh_fingerprint else previous_refresh_fingerprint
        end,refresh_fingerprint=#{refresh},
        state_fingerprint=#{stateHash},
        expires_at=date_add(utc_timestamp(6),interval #{ttl} second),version=version+1,
        updated_time=utc_timestamp(6)
where tid=#{id}
""");
    }
    return {
        saved:true
    };
});
