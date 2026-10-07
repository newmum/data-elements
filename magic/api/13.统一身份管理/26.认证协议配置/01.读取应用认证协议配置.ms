{
  "properties": {},
  "id": "f4c91e5297f253d7a83356038bfbf6cd",
  "script": null,
  "groupId": "1e4c952752445b3b8a68635e161010ed",
  "name": "01.读取应用认证协议配置",
  "createTime": 1790635007936,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/read",
  "description": "读取指定应用的统一认证协议开通状态、客户端及回调配置，对密钥脱敏后供管理界面展示。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：01.读取应用认证协议配置
// 路由：GET /idaas/protocol-config/read
// 业务说明：读取指定应用的统一认证协议开通状态、客户端及回调配置，对密钥脱敏后供管理界面展示。
import db;
import identityProtocol;
import identityTransport;
import java.net.URI;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import '@/idaas/guard' as guard;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import identityRuntime;
import request;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    var payload={};
    for(key in ['id','appId','domain']) {
        var value=request.getValues(key);
        if(value!=null && value.size()>0) {
            payload.put(key,value[0]);
        }
    }
    // 请求参数与本接口业务处理。
    var operation = 'read';

    // 显式启用客户端和可选统一认证。登记、协议开通、应用认证模式分别维护。
    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
    var client=sql.selectOne("""
select *,tid as id
from iam_auth_client_t
where tid=#{p.id}
    and is_del=0
""");
    if(client==null) {
        exit 404,'请先登记客户端';
    }
    var domain=p.domain||'workforce';
    // 先校验当前会话的功能权限、身份域和应用管理范围。
    var ctx=guard(operation=='read'?'clients:read':'clients:write',client.app_id,domain);
    if(operation=='read') {
        return {
            id:client.id,version:client.version,protocol:client.protocol,status:client.status,clientType:client.client_type,grantTypes:client.grant_types==null?[]:JSONUtil.parseArray(client.grant_types),scopes:client.scopes==null?[]:JSONUtil.parseArray(client.scopes),redirects:sql.select("""
select uri,uri_type type
from iam_auth_redirect_t
where client_id=#{client.id}
"""),hasSecret:sql.selectOne("""
select tid as id
from iam_credential_t
where owner_kind='CLIENT'
    and owner_id=#{client.id}
    and credential_type='CLIENT_SECRET'
    and status='ACTIVE'
""")!=null
        };
    }
    // 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
    return mutate(ctx,'protocol:'+operation,p,()=>{
        var c=sql.selectOne("""
select *,tid as id
from iam_auth_client_t
where tid=#{p.id}
    for update
""");
        if(c.version!=p.version) {
            exit 409,'客户端配置已变更';
        }
        if(operation=='federation') {
            guard('apps:write',c.app_id,domain);
            var app=sql.selectOne("""
select *,tid as id
from iam_application_t
where tid=#{c.app_id}
    and kind='BUSINESS'
    and is_del=0
    for update
""");
            if(app==null || app.version!=p.appVersion) {
                exit 409,'应用版本已变更';
            }
            if(p.enabled==true && sql.selectOne("""
select tid as id
from iam_auth_client_t
where app_id=#{c.app_id}
    and protocol='OIDC'
    and status='ACTIVE'
    and is_del=0
limit 1
""")==null) {
                exit 409,'应用尚未配置活动认证客户端';
            }
            var mode=p.enabled==true?'FEDERATED':'LOCAL';
            sql.update("""
update iam_application_t
set auth_mode=#{mode},version=version+1,updated_time=utc_timestamp(6)
where tid=#{c.app_id}
""");
            // 记录操作主体、目标及结果，供审计与故障追踪。
            audit(ctx,'SUCCESS','protocol:federation',c.app_id,null,{
                id:c.app_id,mode:mode
            });
            return {
                id:c.app_id,mode:mode
            };
        }
        if(operation=='disable') {
            sql.update("""
update iam_auth_client_t
set status='DISABLED',version=version+1
where tid=#{c.id}
""");
            sql.update("""
update iam_auth_session_t
set revoked_at=utc_timestamp(6),revoke_reason='CLIENT_DISABLED'
where client_id=#{c.id}
    and revoked_at is null
""");
            audit(ctx,'SUCCESS','protocol:disable',c.id,null,{
                id:c.id
            });
            return {
                id:c.id
            };
        }
        if(operation!='configure' && operation!='rotate') {
            exit 400,'认证接入操作不正确';
        }
        var grants=c.client_type=='SERVICE'?['client_credentials']:c.client_type=='PUBLIC'?['authorization_code']:['authorization_code','refresh_token'];
        var scopes=p.scopes||[];
        var allowed=['openid','profile','email','api'];
        if(operation=='configure' && (scopes.size()==0 || scopes.filter(s=>!allowed.contains(s)).size()>0 || c.client_type=='SERVICE' && scopes.filter(s=>s!='api').size()>0)) {
            exit 400,'认证范围只能选择已支持的范围';
        }
        if(operation=='configure') {
            var redirects=p.redirects||[];
            if(c.client_type!='SERVICE' && redirects.size()==0 || redirects.size()>30) {
                exit 400,'交互客户端须登记精确回调地址';
            }
            var seen=[];
            for(item in redirects) {
                if(!['LOGIN','LOGOUT'].contains(item.type)) {
                    exit 400,'回调类型不正确';
                }
                var uri=new URI(item.uri);
                if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null || !(uri.getScheme()=='https' || uri.getScheme()=='http' && uri.getHost()=='localhost') || item.uri.length()>2000 || item.uri.contains('*')) {
                    exit 400,'回调地址须为精确HTTPS地址，开发仅接受localhost HTTP';
                }
                var mark=item.type+':'+item.uri;
                if(seen.contains(mark)) {
                    exit 400,'回调地址重复';
                }
                seen.add(mark);
            }
            sql.update("""
delete
from iam_auth_redirect_t
where client_id=#{c.id}
""");
            for(item in redirects) {
                var rid=IdUtil.fastSimpleUUID();
                var hash=DigestUtil.sha256Hex(item.uri);
                sql.update("""
insert into iam_auth_redirect_t(tid, client_id, uri_type, uri, uri_hash)
values(#{rid},#{c.id},#{item.type},#{item.uri},#{hash})
""");
            }
            var encodedGrants=JSONUtil.toJsonStr(grants);
            var encodedScopes=JSONUtil.toJsonStr(scopes);
            sql.update("""
update iam_auth_client_t
set protocol='OIDC',grant_types=#{encodedGrants},scopes=#{encodedScopes},status='ACTIVE',require_pkce=1,
    version=version+1,updated_time=utc_timestamp(6)
where tid=#{c.id}
""");
        }
        if(c.client_type!='PUBLIC') {
            var old=sql.selectOne("""
select *,tid as id
from iam_credential_t
where owner_kind='CLIENT'
    and owner_id=#{c.id}
    and credential_type='CLIENT_SECRET'
    and status='ACTIVE'
    for update
""");
            if(p.secret==null || p.secret=='') {
                if(old==null || operation=='rotate') {
                    exit 400,'请提供独立客户端密钥';
                }
            }
            else {
                var hash=identityProtocol.hashClientSecret(p.secret);
                if(old!=null) {
                    sql.update("""
update iam_credential_t
set status='RETIRED'
where tid=#{old.id}
""");
                }
                var rid=IdUtil.fastSimpleUUID();
                var revision=old==null?1:old.revision+1;
                sql.update("""
insert into iam_credential_t(tid,
                owner_kind, owner_id, credential_type, revision, algorithm, secret_hash, status)
values(#{rid},'CLIENT',#{c.id},'CLIENT_SECRET',#{revision},'BCRYPT',#{hash},'ACTIVE')
""");
            }
        }
        if(operation=='rotate') {
            sql.update("""
update iam_auth_client_t
set version=version+1,updated_time=utc_timestamp(6)
where tid=#{c.id}
""");
        }
        sql.update("""
update iam_auth_session_t
set revoked_at=utc_timestamp(6),revoke_reason='CLIENT_CONFIG_CHANGED'
where client_id=#{c.id}
    and revoked_at is null
""");
        audit(ctx,'SUCCESS','protocol:'+operation,c.id,null,{
            id:c.id
        });
        return {
            id:c.id
        };
    });

}));
