{
  "properties": {},
  "id": "440df30da8fa582e9ce22c24c6693e06",
  "script": null,
  "groupId": "07899fe85f4d59fd90de0507c7f4d188",
  "name": "05.测试下发接收端",
  "createTime": 1790612165860,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/test",
  "description": "由用户触发连接测试，使用已保存的接收配置验证目标接口可达性与鉴权并返回测试结果。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：05.测试下发接收端
// 路由：POST /idaas/provision/test
// 业务说明：由用户触发连接测试，使用已保存的接收配置验证目标接口可达性与鉴权并返回测试结果。
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
    var payload=body;
    // 请求参数与本接口业务处理。
    var operation = 'test';

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
    if(old==null && operation!='save-config') {
        exit 404,'接收配置不存在';
    }
    if(operation=='test') {
        var key=sql.selectOne("""
select *,tid as id
from iam_credential_t
where owner_kind='SYNC_CONFIG'
    and owner_id=#{id}
    and credential_type='HMAC_KEY'
    and status='ACTIVE'
""");
        if(key==null) {
            exit 409,'没有活动接入密钥';
        }
        var event={
            eventId:IdUtil.fastSimpleUUID(),appId:appId,receiverInstanceId:old.receiver_instance_id,objectType:'PING',objectId:'receiver',sourceVersion:1,action:'UPSERT',data:{}
        };
        var encoded=identityTransport.canonicalJson(event);
        var keyId=JSONUtil.parseObj(key.public_data).keyId;
        // 仅在本次人工请求中向已配置接收地址发送签名事件。
        var response=identityTransport.send2(old.endpoint,key.secret_cipher,keyId,appId,old.receiver_instance_id,encoded,old.timeout_seconds);
        var receipt=response.data==null?null:response.data.receipt;
        // 核对签名回执的事件号、版本和目标实例，成功后才确认下发结果。
        var ok=response.code==0 && receipt!=null && receipt.appId==appId && receipt.receiverInstanceId==old.receiver_instance_id && receipt.eventId==event.eventId && receipt.result=='APPLIED' && identityTransport.verifyReceipt(key.secret_cipher,receipt,response.data.receiptSignature);
        var result=ok?'SUCCESS':'FAILED';
        // 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
        return mutate(ctx,'sync:test',p,()=>{
            var latest=sql.selectOne("""
select version
from iam_sync_config_t
where tid=#{id}
    for update
""");
            // 以当前版本校验修改目标，阻止旧页面覆盖已经变更的记录。
            if(latest.version!=old.version) {
                exit 409,'测试期间配置已变更';
            }
            sql.update("""
update iam_sync_config_t
set last_test_time=utc_timestamp(6),last_test_result=#{result}
where tid=#{id}
""");
            // 记录操作主体、目标及结果，供审计与故障追踪。
            audit(ctx,ok?'SUCCESS':'FAILED','sync:test',id,result,{
                id:id
            });
            if(!ok) {
                return {
                    verified:false,result:'FAILED'
                };
            }
            return {
                verified:true,result:'SUCCESS',appId:appId,receiverInstanceId:old.receiver_instance_id
            };
        });
    }
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
        audit(ctx,'SUCCESS','sync:'+operation,id,null,{
            id:id,appId:appId
        });
        return {
            id:id
        };
    });

}));
