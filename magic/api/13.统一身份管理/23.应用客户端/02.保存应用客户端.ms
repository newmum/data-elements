{
  "properties": {},
  "id": "3fbdbbf3b526546f9c9fbcb38a38784d",
  "script": null,
  "groupId": "22234a256a485757b6f81c2c1c3885ed",
  "name": "02.保存应用客户端",
  "createTime": 1790617158595,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/save",
  "description": "校验请求字段、所属范围和版本；新建或更新应用客户端及必要关联记录，写入审计并返回保存结果。维护应用接入客户端及其协议配置；客户端凭据不随普通列表接口明文返回。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：02.保存应用客户端
// 路由：POST /idaas/clients/save
// 业务说明：校验请求字段、所属范围和版本；新建或更新应用客户端及必要关联记录，写入审计并返回保存结果。维护应用接入客户端及其协议配置；客户端凭据不随普通列表接口明文返回。
import db;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import '@/idaas/guard' as guard;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import '@/idaas/app-list-context' as listContext;
import identityRuntime;
import request;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    var payload=body;
    // 请求参数与本接口业务处理。
    var operation = 'save';

    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
    var r=p.record||p;
    var domain=r.domain||p.domain||'workforce';
    var id=r.id||p.id;
    var old=id==null?null:sql.selectOne("""
select *,tid as id
from iam_auth_client_t
where tid=#{id}
    and is_del=0
""");
    var appId=old==null?r.appId||p.appId:old.app_id;
    var permission=operation=='list'?'clients:read':'clients:write';
    // 核验会话的功能权限及对象范围，超出范围的请求在读取或写入前拒绝。
    var ctx=operation=='list'?listContext(permission,appId,domain):guard(permission,appId,domain);
    // 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
    return mutate(ctx,'clients:'+operation,p,()=>{
        old=id==null?null:sql.selectOne("""
select *,tid as id
from iam_auth_client_t
where tid=#{id}
    and is_del=0
    for update
""");
        if(old==null && p.creating!=true) {
            exit 404,'客户端不存在';
        }
        // 以当前版本校验修改目标，阻止旧页面覆盖已经变更的记录。
        if(old!=null && (old.version!=(r.version||p.version) || r.appId!=null && r.appId!=old.app_id)) {
            exit 409,'客户端应用或版本不一致';
        }
        if(sql.selectOne("""
select tid as id
from iam_application_t
where tid=#{appId}
    and kind='BUSINESS'
    and is_del=0
""")==null) {
            exit 400,'客户端只能属于业务应用';
        }
        if(operation=='remove') {
            if(sql.selectOne("""
select count(*) n
from iam_access_grant_t
where client_id=#{id}
    and status='ACTIVE'
    and is_del=0
""").n>0) {
                exit 409,'机器身份仍有有效授权';
            }
            sql.update("""
update iam_auth_client_t
set status='DISABLED',is_del=1,version=version+1
where tid=#{id}
""");
            // 记录操作主体、目标及结果，供审计与故障追踪。
            audit(ctx,'SUCCESS','clients:remove',id,null,{
                id:id
            });
            return {
                id:id
            };
        }
        var name=(r.name||'').trim();
        var key=(r.clientKey||'').trim();
        var type=r.clientType||'PUBLIC';
        if(name=='' || name.length()>100 || !key.matches('[A-Za-z][A-Za-z0-9_.-]{2,99}') || !['PUBLIC','CONFIDENTIAL','SERVICE'].contains(type)) {
            exit 400,'客户端名称、编码或类型不正确';
        }
        if(old!=null && (key!=old.client_key || type!=old.client_type)) {
            exit 400,'客户端编码和类型保持稳定';
        }
        if(r.protocol!=null && r.protocol!='NONE' || r.status=='ACTIVE') {
            exit 400,'登记接口不开放认证协议，请在认证接入配置中启用';
        }
        var status=r.status=='DISABLED'?'DISABLED':'REGISTERED';
        var access=r.accessTtl||600;
        var refresh=r.refreshTtl||86400;
        if(access<60 || access>3600 || refresh<access || refresh>604800) {
            exit 400,'令牌有效期超限';
        }
        if(old==null) {
            if(sql.selectOne("""
select tid as id
from iam_auth_client_t
where client_key=#{key}
""")!=null) {
                exit 409,'客户端编码已经使用';
            }
            id=IdUtil.fastSimpleUUID();
            sql.update("""
insert into iam_auth_client_t(tid,
                app_id, client_key, name, client_type, token_auth_method, access_ttl_seconds,
                refresh_ttl_seconds, status)
values(#{id},#{appId},#{key},#{name},#{type},#{type=='PUBLIC'?'none':'client_secret_basic'},#{access},#{refresh},#{status})
""");
        }
        else {
            sql.update("""
update iam_auth_client_t
set name=#{name},status=#{status},access_ttl_seconds=#{access},
    refresh_ttl_seconds=#{refresh},version=version+1,updated_time=utc_timestamp(6)
where tid=#{id}
""");
        }
        audit(ctx,'SUCCESS','clients:save',id,null,{
            id:id,appId:appId
        });
        return {
            id:id
        };
    });

}));
