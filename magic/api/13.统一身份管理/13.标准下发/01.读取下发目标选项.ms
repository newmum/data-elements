{
  "properties": {},
  "id": "696383013abe5008aae8fe5ed01ac834",
  "script": null,
  "groupId": "07899fe85f4d59fd90de0507c7f4d188",
  "name": "01.读取下发目标选项",
  "createTime": 1790612165860,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/options",
  "description": "按当前操作人的应用范围列出可配置下发接收端的应用、资料类型与可用目标，不发起下发。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：01.读取下发目标选项
// 路由：GET /idaas/provision/options
// 业务说明：按当前操作人的应用范围列出可配置下发接收端的应用、资料类型与可用目标，不发起下发。
import db;
import identityTransport;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import '@/idaas/guard' as guard;
import '@/idaas/app-list-context' as listContext;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import identityRuntime;
import request;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    var payload={};
    for(key in ['appId','configId','id']) {
        var input=request.getValues(key);
        if(input!=null && input.size()>0) {
            payload.put(key,input[0]);
        }
    }
    // 请求参数与本接口业务处理。
    var operation = 'options';

    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
    var id=p.configId||p.id;
    var old=id==null?null:sql.selectOne("""
select *,tid as id
from iam_sync_config_t
where tid=#{id}
    and is_del=0
""");
    var appId=old==null?p.appId||p.targetTenantId:old.app_id;
    var permission=operation=='configs' || operation=='options'?'sync:read':'sync:write';
    // 核验会话的功能权限及对象范围，超出范围的请求在读取或写入前拒绝。
    var ctx=operation=='configs' || operation=='options'?listContext(permission,appId,'workforce'):guard(permission,appId,'workforce');
    var visible=x=>ctx.pairs.filter(pair=>pair.codes.contains(permission) && pair.scopes.filter(s=>s.identity_domain=='workforce' && (s.scope_kind=='ALL' || s.scope_kind=='APPLICATION' && s.app_id==x)).size()>0).size()>0;
    if(operation=='options') {
        return sql.select("""
select a.tid appId,a.tid tenantId,a.name tenantName,a.code tenantCode
from iam_application_t a
where a.kind='BUSINESS'
    and a.is_del=0
""").filter(a=>visible(a.appId));
    }
    if(old==null && operation!='save-config') {
        exit 404,'接收配置不存在';
    }
    // 在事务内完成校验与写入；重复请求及并发版本冲突由共享事务规则处理。
    return mutate(ctx,'sync:'+operation,p,()=>{
        old=id==null?null:sql.selectOne("""
select *,tid as id
from iam_sync_config_t
where tid=#{id}
    and is_del=0
    for update
""");
        if(operation!='save-config' && operation!='rotate') {
            exit 400,'配置操作不正确';
        }
        // 以当前版本校验修改目标，阻止旧页面覆盖已经变更的记录。
        if(old!=null && old.version!=p.version) {
            exit 409,'配置版本已变更';
        }
        if(sql.selectOne("""
select tid as id
from iam_application_t
where tid=#{appId}
    and kind='BUSINESS'
    and is_del=0
""")==null) {
            exit 400,'只能配置业务应用';
        }
        var keyId=p.keyId;
        var secret=p.secret;
        var key=old==null?null:sql.selectOne("""
select *,tid as id
from iam_credential_t
where owner_kind='SYNC_CONFIG'
    and owner_id=#{id}
    and credential_type='HMAC_KEY'
    and status='ACTIVE'
""");
        if(keyId==null && key!=null) {
            keyId=JSONUtil.parseObj(key.public_data).keyId;
        }
        if(keyId==null || !keyId.matches('[A-Za-z0-9_-]{3,64}')) {
            exit 400,'密钥标识不正确';
        }
        if(operation=='rotate') {
            if(secret==null || secret=='') {
                exit 400,'轮换密钥不能为空';
            }
            sql.update("""
update iam_sync_config_t
set enabled=0,last_test_result=null,version=version+1,updated_time=utc_timestamp(6)
where tid=#{id}
""");
            sql.update("""
update iam_application_t
set provisioning_mode='OFF',version=version+1,updated_time=utc_timestamp(6)
where tid=#{appId}
""");
        }
        else {
            var endpoint=identityTransport.validateUrl(p.endpoint);
            var instance=p.receiverInstanceId;
            var name=(p.name||'接收配置').trim();
            var timeout=p.timeout||10;
            if(instance==null || !instance.matches('[A-Za-z0-9_.-]{3,100}') || name=='' || name.length()>100 || timeout<1 || timeout>30 || p.mapping!=null && p.mapping!='{}') {
                exit 400,'实例、名称、超时或映射配置不正确';
            }
            var enabled=p.enabled==true?1:0;
            var changed=old==null || old.endpoint!=endpoint || old.receiver_instance_id!=instance || secret!=null && secret!='';
            if(enabled==1 && (changed || old.last_test_result!='SUCCESS')) {
                exit 409,'请先保存关闭状态并测试接收实例，再启用';
            }
            if(old==null) {
                if(sql.selectOne("""
select tid as id
from iam_sync_config_t
where app_id=#{appId}
""")!=null) {
                    exit 409,'应用已有接收配置，请使用原配置修改';
                }
                id=IdUtil.fastSimpleUUID();
                sql.update("""
insert into iam_sync_config_t(tid,
                app_id, display_name, endpoint, protocol_version, receiver_instance_id, timeout_seconds,
                object_types, enabled)
values(#{id},#{appId},#{name},#{endpoint},'IDAAS/2',#{instance},#{timeout},'["ORG","RESOURCE","ROLE","SUBJECT"]',#{enabled})
""");
            }
            else {
                sql.update("""
update iam_sync_config_t
set display_name=#{name},endpoint=#{endpoint},receiver_instance_id=#{instance},
    timeout_seconds=#{timeout},enabled=#{enabled},last_test_result=case
        when #{changed} then null
        else last_test_result
        end,version=version+1,
        updated_time=utc_timestamp(6)
where tid=#{id}
""");
            }
            var mode=enabled==1?'HTTP':'OFF';
            sql.update("""
update iam_application_t
set provisioning_mode=#{mode},version=version+1,updated_time=utc_timestamp(6)
where tid=#{appId}
""");
        }
        if(secret!=null && secret!='') {
            var cipher=identityTransport.encrypt(secret);
            var revision=key==null?1:key.revision+1;
            sql.update("""
update iam_credential_t
set status='RETIRED',version=version+1
where owner_kind='SYNC_CONFIG'
    and owner_id=#{id}
    and credential_type='HMAC_KEY'
    and status='ACTIVE'
""");
            var kid=IdUtil.fastSimpleUUID();
            var data=JSONUtil.toJsonStr({
                keyId:keyId
            });
            sql.update("""
insert into iam_credential_t(tid,
                owner_kind, owner_id, credential_type, slot, revision, algorithm, secret_cipher, public_data,
                status)
values(#{kid},'SYNC_CONFIG',#{id},'HMAC_KEY','primary',#{revision},'HMAC_SHA256_AES256_GCM',#{cipher},#{data},'ACTIVE')
""");
        }
        else if(key==null) {
            exit 400,'接入密钥不能为空';
        }
        // 记录操作主体、目标及结果，供审计与故障追踪。
        audit(ctx,'SUCCESS','sync:'+operation,id,null,{
            id:id,appId:appId
        });
        return {
            id:id
        };
    });

}));
