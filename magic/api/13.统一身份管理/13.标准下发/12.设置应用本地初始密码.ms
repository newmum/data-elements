{
  "properties": {},
  "id": "cf026ce5125b49338d2941874f0cbd62",
  "script": null,
  "groupId": "07899fe85f4d59fd90de0507c7f4d188",
  "name": "12.设置应用本地初始密码",
  "path": "/set-initial-password",
  "description": "人工创建人员并完成下发后，以一次性签名事件为目标租户新账号设置初始密码；不在中央人员或任务表保存密码。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：12.设置应用本地初始密码
// 路由：POST /idaas/provision/set-initial-password
// 密码只在本次人工请求中转换为 SM3 摘要，并由目标租户按本地算法加盐保存。
import db;
import identityTransport;
import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import '@/idaas/guard' as guard;
import '@/idaas/directory-scope' as directoryScope;
import '@/idaas/audit' as audit;
import identityRuntime;
import request;
import '@/idaas/api-call' as apiCall;

return identityRuntime.control(()=>apiCall(()=>{
    var p=body||{};
    var taskId=(p.taskId||'')+'';
    var subjectId=(p.subjectId||'')+'';
    var requestId=(p.requestId||'')+'';
    var password=p.initialPassword;
    if(!taskId.matches('[a-f0-9]{32}') || !subjectId.matches('[a-f0-9]{32}') ||
       !requestId.matches('[a-f0-9-]{36}') || password==null || password.length()<6 ||
       password.length()>128 || password.matches('(?i)[0-9a-f]{96}')) {
        exit 400,'请填写6至128位初始密码并重试';
    }
    var sql=db.normal();
    var task=sql.selectOne("""
select t.app_id,t.config_id,t.status,t.config_version,c.endpoint,c.receiver_instance_id,
       c.timeout_seconds,c.enabled,c.last_test_result,c.version
from iam_sync_task_t t
join iam_sync_config_t c on c.tid=t.config_id and c.is_del=0
where t.tid=#{taskId}
""");
    if(task==null || task.status!='SUCCEEDED' || task.enabled!=1 ||
       task.last_test_result!='SUCCESS' || task.version!=task.config_version) {
        exit 409,'请先完成该用户的应用下发';
    }
    var appId=task.app_id;
    var ctx=guard('sync:write',appId,'workforce');
    var scope=directoryScope('users:write','workforce');
    var person=sql.selectOne("""
select s.tid,s.identity_domain,s.status,a.ack_version,a.desired_version,a.sync_status,
       l.local_id,l.receiver_instance_id
from iam_subject_t s
join iam_subject_app_t a on a.subject_id=s.tid and a.app_id=#{appId}
join iam_external_link_t l on l.app_id=#{appId} and l.object_type='SUBJECT' and l.source_id=s.tid
where s.tid=#{subjectId} and s.is_del=0
""");
    if(person==null || person.identity_domain!='workforce' || person.status!='ACTIVE' ||
       person.sync_status!='IN_SYNC' || person.ack_version!=person.desired_version ||
       person.receiver_instance_id!=task.receiver_instance_id) {
        exit 409,'该用户尚未在目标应用完成下发';
    }
    var item=sql.selectOne("""
select tid
from iam_sync_item_t
where task_id=#{taskId} and object_type='SUBJECT' and object_id=#{subjectId}
  and status in ('APPLIED','DUPLICATE')
""");
    if(item==null) {exit 403,'该任务不包含此用户';}
    var orgs=sql.select("""
select distinct org_id
from iam_appointment_t
where subject_id=#{subjectId} and is_del=0 and status='ACTIVE'
""");
    if(!scope.all && (orgs.size()==0 || orgs.filter(o=>!scope.orgIds.contains(o.org_id)).size()>0)) {
        exit 403,'该用户不在当前管理范围';
    }
    var key=sql.selectOne("""
select secret_cipher,public_data
from iam_credential_t
where owner_kind='SYNC_CONFIG' and owner_id=#{task.config_id}
  and credential_type='HMAC_KEY' and status='ACTIVE'
order by revision desc
limit 1
""");
    if(key==null) {exit 409,'接收配置没有有效凭据';}
    var keyId=JSONUtil.parseObj(key.public_data).getStr('keyId');
    var eventId=requestId.replace('-','');
    var event={eventId:eventId,appId:appId,receiverInstanceId:task.receiver_instance_id,
        objectType:'CREDENTIAL',objectId:subjectId,sourceVersion:person.ack_version,
        action:'INITIALIZE',data:{passwordDigest:SmUtil.sm3(password).toUpperCase()}};
    var payload=identityTransport.canonicalJson(event);
    var response=null;
    try {
        response=identityTransport.send2(task.endpoint,key.secret_cipher,keyId,appId,
            task.receiver_instance_id,payload,task.timeout_seconds);
    } catch(e) {exit 502,'接收端暂时无响应，初始密码未确认；请保留输入后重试';}
    var receipt=response.data==null?null:response.data.receipt;
    if(response.code!=0 || receipt==null ||
       !identityTransport.verifyReceipt(key.secret_cipher,receipt,response.data.receiptSignature) ||
       receipt.protocol!='IDAAS/2' || receipt.appId!=appId ||
       receipt.receiverInstanceId!=task.receiver_instance_id || receipt.eventId!=eventId ||
       receipt.objectType!='CREDENTIAL' || receipt.objectId!=subjectId ||
       receipt.sourceVersion!=person.ack_version || receipt.payloadHash!=DigestUtil.sha256Hex(payload) ||
       receipt.localId!=person.local_id ||
       !['APPLIED','DUPLICATE'].contains(receipt.result)) {
        exit 409,'目标账号初始密码未确认；请核对本地账号状态后重试';
    }
    audit(ctx,'SUCCESS','credential:initialize',subjectId,null,{appId:appId,taskId:taskId});
    return {accountReady:true,appId:appId,subjectId:subjectId};
}));
