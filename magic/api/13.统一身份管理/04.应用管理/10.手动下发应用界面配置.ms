{
  "properties": {},
  "id": "7cb4e9d431d85d5a8c2c865c98c80b96",
  "script": null,
  "groupId": "8e1839fbe6a55ce7aafaae3dcf2d15b4",
  "name": "10.手动下发应用界面配置",
  "createTime": 1790827272222,
  "updateTime": 1790948254995,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/push-portal-config",
  "description": "用户显式发起后读取应用配置表的界面配置，核验运行绑定并调用租户接收端写入；返回每次下发结果，不后台轮询。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：10.手动下发应用界面配置
// 路由：POST /idaas/applications/push-portal-config
// 业务说明：用户显式发起后读取应用配置表的界面配置，核验运行绑定并调用租户接收端写入；返回每次下发结果，不后台轮询。
import db;
import identityTransport;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import '@/idaas/guard' as guard;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import identityRuntime;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    // 请求参数与本接口业务处理。
    var payload = body||{};

    // 此流程仅由明确的接口请求触发，不在后台自动扫描或下发租户数据。
    // 用户显式操作才下发；沿用已测试的接收地址、HMAC信任和签名回执。
    var p=payload||{};
    var appId=''+(p.appId||'');
    if(!appId.matches('[A-Za-z0-9_.:-]{1,64}')) {
        exit 400,'应用不正确';
    }
    if(p.requestId==null || !p.requestId.matches('[A-Za-z0-9_-]{8,64}')) {
        exit 400,'请提供有效请求号';
    }
    // 先校验当前会话的功能权限、身份域和应用管理范围。
    var ctx=guard('apps:write',appId,'workforce');
    guard('sync:write',appId,'workforce');
    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var app=sql.selectOne("""
select tid as id,version
from iam_application_t
where tid=#{appId}
    and kind='BUSINESS'
    and is_del=0
""");
    if(app==null) {
        exit 404,'应用不存在';
    }
    if(p.appVersion!=app.version) {
        exit 409,'应用配置已更新，请刷新后再下发';
    }
    var portal=JSONUtil.createObj();
    for(item in sql.select("""
select config_code,config_value,value_type
from iam_application_config_t
where app_id=#{appId}
    and config_group='PORTAL'
    and is_del=0
""")) {
        portal.put(item.config_code,item.value_type=='BOOLEAN'?item.config_value=='true':item.config_value);
    }
    if(portal.size()==0) {
        exit 409,'请先保存应用界面配置';
    }
    var config=sql.selectOne("""
select *,tid as id
from iam_sync_config_t
where app_id=#{appId}
    and is_del=0
    and enabled=1
    and last_test_result='SUCCESS'
""");
    if(config==null) {
        exit 409,'请先在同步管理中配置并测试该应用的接收地址';
    }
    var key=sql.selectOne("""
select *,tid as id
from iam_credential_t
where owner_kind='SYNC_CONFIG'
    and owner_id=#{config.id}
    and credential_type='HMAC_KEY'
    and status='ACTIVE'
""");
    if(key==null) {
        exit 409,'接收端密钥尚未配置';
    }
    var event={
        eventId:IdUtil.fastSimpleUUID(),appId:appId,receiverInstanceId:config.receiver_instance_id,objectType:'PORTAL_CONFIG',objectId:appId,sourceVersion:app.version,action:'UPSERT',data:portal
    };
    var encoded=identityTransport.canonicalJson(event);
    var hash=DigestUtil.sha256Hex(encoded);
    var keyId=JSONUtil.parseObj(key.public_data).keyId;
    // 仅在本次人工请求中向已配置接收地址发送签名事件。
    var response=identityTransport.send2(config.endpoint,key.secret_cipher,keyId,appId,config.receiver_instance_id,encoded,config.timeout_seconds);
    var receipt=response.data==null?null:response.data.receipt;
    // 核对签名回执的事件号、版本和目标实例，成功后才确认下发结果。
    var valid=response.code==0 && receipt!=null && receipt.protocol=='IDAAS/2' && receipt.appId==appId && receipt.receiverInstanceId==config.receiver_instance_id && receipt.eventId==event.eventId && receipt.objectType=='PORTAL_CONFIG' && receipt.objectId==appId && receipt.sourceVersion==app.version && receipt.payloadHash==hash && identityTransport.verifyReceipt(key.secret_cipher,receipt,response.data.receiptSignature);
    var synced=valid && ['APPLIED','DUPLICATE'].contains(receipt.result);
    var code=valid?receipt.result:'INVALID_RECEIPT';
    var reason=valid?receipt.message||'': '接收地址未返回有效的签名回执';
    // 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
    return mutate(ctx,'applications:push-portal',p,()=>{
        // 记录操作主体、目标及结果，供审计与故障追踪。
        audit(ctx,synced?'SUCCESS':'FAILED','applications:push-portal',appId,code,{
            appId:appId,configKeys:portal.keySet(),receiverInstanceId:config.receiver_instance_id
        });
        return {
            synced:synced,result:code,message:reason,appId:appId,version:app.version
        };
    });
}));
