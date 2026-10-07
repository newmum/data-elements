{
  "properties": {},
  "id": "98326ef1fc5558f4816375a354792250",
  "script": null,
  "groupId": "07899fe85f4d59fd90de0507c7f4d188",
  "name": "07.执行手动下发任务",
  "createTime": 1790612165860,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/execute",
  "description": "用户显式触发后领取指定任务，构造标准人员或机构报文，调用接收端并保存回执与失败原因。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：07.执行手动下发任务
// 路由：POST /idaas/provision/execute
// 业务说明：用户显式触发后领取指定任务，构造标准人员或机构报文，调用接收端并保存回执与失败原因。
import db;
import identityTransport;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import java.lang.System;
import java.lang.IllegalStateException;
import '@/idaas/guard' as guard;
import '@/idaas/audit' as audit;
import identityRuntime;
import request;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    var payload=body;
    // 请求参数与本接口业务处理。

    // 此流程仅由明确的接口请求触发，不在后台自动扫描或下发租户数据。
    // 租约在本库事务中取得，HTTP在提交后执行。只有签名且完整匹配的回执能推进ack。
    var sql=db.normal();
    var p=payload||{};
    var task=sql.selectOne("""
select *,tid as id
from iam_sync_task_t
where tid=#{p.id}
""");
    if(task==null) {
        exit 404,'任务不存在';
    }
    // 先校验当前会话的功能权限、身份域和应用管理范围。
    var ctx=guard('sync:execute',task.app_id,'workforce');
    var lease=IdUtil.fastSimpleUUID();
    var taskId=task.id;
    // 把读取校验和状态写入放在同一事务，防止并发覆盖。
    var claimed=sql.transaction(()=>{
        var row=sql.selectOne("""
select *,tid as id,lease_until>utc_timestamp(6) live_lease
from iam_sync_task_t
where tid=#{taskId}
    for update
""");
        if(['SUCCEEDED','CANCELED'].contains(row.status)) {
            return false;
        }
        if(row.live_lease==1 && row.lease_token!=null) {
            exit 409,'任务正在执行，请稍后查看';
        }
        var config=sql.selectOne("""
select *,tid as id
from iam_sync_config_t
where tid=#{row.config_id}
    and is_del=0
""");
        if(config==null || config.enabled!=1 || config.version!=row.config_version) {
            exit 409,'接收配置已关闭或变更，请重新预览创建任务';
        }
        sql.update("""
update iam_sync_item_t
set status='FAILED',result_json='{"message":"执行中断，使用原事件重新确认"}'
where task_id=#{taskId}
    and status='RUNNING'
""");
        sql.update("""
update iam_sync_task_t
set status='RUNNING',lease_token=#{lease},lease_until=date_add(utc_timestamp(6),interval 60 second),
    version=version+1
where tid=#{taskId}
""");
        return true;
    });
    if(!claimed) {
        return {
            id:taskId,status:task.status
        };
    }
    var items=sql.select("""
select i.*,i.tid as id
from iam_sync_item_t i
join iam_sync_config_t c on c.tid=#{task.config_id}
where i.task_id=#{taskId}
    and i.status='PENDING'
    and i.attempt_count<c.max_attempts
order by i.sequence_no
limit 20
""");
    for(item in items) {
        var config=sql.selectOne("""
select *,tid as id
from iam_sync_config_t
where tid=#{task.config_id}
    and is_del=0
""");
        var snapshot=JSONUtil.parseObj(task.config_snapshot_json);
        if(config==null || config.enabled!=1 || config.version!=task.config_version || config.endpoint!=snapshot.endpoint || config.receiver_instance_id!=snapshot.instance) {
            break;
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
            break;
        }
        var keyId=JSONUtil.parseObj(key.public_data).keyId;
        if(keyId!=snapshot.keyId) {
            break;
        }
        // 树父项、角色资源、人员任职及角色均为依赖；增量中省略的项已由同实例确认。
        var frozen=identityTransport.parseJson(item.payload_json);
        var references=[];
        if(frozen.data.parentId!=null) {
            references.add({
                type:item.object_type,id:frozen.data.parentId
            });
        }
        if(item.object_type=='ROLE') {
            for(rid in frozen.data.resourceIds||[]) {
                references.add({
                    type:'RESOURCE',id:rid
                });
            }
        }
        if(item.object_type=='SUBJECT') {
            for(job in frozen.data.appointments||[]) {
                references.add({
                    type:'ORG',id:job.orgId
                });
            }
            for(rid in frozen.data.roleIds||[]) {
                references.add({
                    type:'ROLE',id:rid
                });
            }
        }
        var dependencies=references.map(ref=>sql.selectOne("""
select status,attempt_count
from iam_sync_item_t
where task_id=#{taskId}
    and object_type=#{ref.type}
    and object_id=#{ref.id}
""")).filter(d=>d!=null);
        if(dependencies.filter(d=>d.status=='CONFLICT' || d.status=='FAILED' && d.attempt_count>=config.max_attempts).size()>0) {
            sql.update("""
update iam_sync_item_t
set status='CONFLICT',result_json='{"result":"DEPENDENCY_BLOCKED","message":"前置对象未能完成，请处理前置失败后重新预览"}',version=version+1,
        updated_time=utc_timestamp(6)
where tid=#{item.id}
    and status in ('PENDING','FAILED')
""");
            continue;
        }
        if(dependencies.filter(d=>!['APPLIED','DUPLICATE'].contains(d.status)).size()>0) {
            continue;
        }
        var started=System.currentTimeMillis();
        var attempt=item.attempt_count+1;
        var attemptId=IdUtil.fastSimpleUUID();
        var active=sql.transaction(()=>{
            var current=sql.selectOne("""
select lease_token,status
from iam_sync_task_t
where tid=#{taskId}
    for update
""");
            if(current.lease_token!=lease || current.status!='RUNNING') {
                return false;
            }
            sql.update("""
update iam_sync_task_t
set lease_until=date_add(utc_timestamp(6),interval 60 second)
where tid=#{taskId}
""");
            sql.update("""
update iam_sync_item_t
set status='RUNNING',attempt_count=attempt_count+1
where tid=#{item.id}
""");
            sql.update("""
insert into iam_sync_attempt_t(tid,
                item_id, attempt_no, credential_id, started_time, transport_status)
values(#{attemptId},#{item.id},#{attempt},#{key.id},utc_timestamp(6),'SENT')
""");
            return true;
        });
        if(!active) {
            break;
        }
        var httpStatus=null;
        var payload=identityTransport.canonicalText(item.payload_json);
        var ok=false;
        var receipt=null;
        var code='TRANSPORT_OR_RECEIPT_INVALID';
        var message='网络或回执校验失败';
        try {
            if(DigestUtil.sha256Hex(payload)!=item.payload_hash) {
                throw new IllegalStateException('冻结事件摘要不一致');
            }
            // 仅在本次人工请求中向已配置接收地址发送签名事件。
            var response=identityTransport.send2(snapshot.endpoint,key.secret_cipher,keyId,task.app_id,snapshot.instance,payload,config.timeout_seconds);
            httpStatus=response.httpStatus;
            receipt=response.data==null?null:response.data.receipt;
            // 核对签名回执的事件号、版本和目标实例，成功后才确认下发结果。
            if(response.code==0 && receipt!=null && identityTransport.verifyReceipt(key.secret_cipher,receipt,response.data.receiptSignature) && receipt.protocol=='IDAAS/2' && receipt.appId==task.app_id && receipt.receiverInstanceId==snapshot.instance && receipt.eventId==item.id && receipt.objectType==item.object_type && receipt.objectId==item.object_id && receipt.sourceVersion==item.source_version && receipt.payloadHash==item.payload_hash && receipt.localId!=null && receipt.localId!='') {
                code=receipt.result;
                message=receipt.message||'';
                ok=['APPLIED','DUPLICATE'].contains(code);
            }
        }
        catch(e) {
            receipt=null;
        }
        var status=ok?code:code=='CONFLICT' || code=='DETACHED' || code=='STALE'?'CONFLICT':'FAILED';
        var safe=JSONUtil.toJsonStr({
            result:code,message:message,localId:receipt==null?null:receipt.localId,sourceVersion:receipt==null?null:receipt.sourceVersion
        });
        var duration=System.currentTimeMillis()-started;
        var transportStatus=ok?'ACKNOWLEDGED':'REJECTED';
        sql.transaction(()=>{
            sql.update("""
update iam_sync_attempt_t
set finished_time=utc_timestamp(6),transport_status=#{transportStatus},http_status=#{httpStatus},
    result_code=#{code},safe_message=#{message},duration_ms=#{duration}
where tid=#{attemptId}
""");
            sql.update("""
update iam_sync_item_t
set status=#{status},result_json=#{safe},version=version+1,
        next_retry_time=null,
        updated_time=utc_timestamp(6)
where tid=#{item.id}
""");
            if(ok) {
                var link=sql.selectOne("""
select *,tid as id
from iam_external_link_t
where app_id=#{task.app_id}
    and object_type=#{item.object_type}
    and source_id=#{item.object_id}
    for update
""");
                if(link!=null && (link.receiver_instance_id!=snapshot.instance || link.local_id!=receipt.localId)) {
                    sql.update("""
update iam_sync_item_t
set status='CONFLICT',result_json='{"message":"接收实例或本地映射不一致"}'
where tid=#{item.id}
""");
                }
                else {
                    var lid=link==null?IdUtil.fastSimpleUUID():link.id;
                    if(link==null) {
                        sql.update("""
insert into iam_external_link_t(tid,
                app_id, receiver_instance_id, object_type, source_id, local_id, ack_version, ack_hash,
                last_ack_time)
values(#{lid},#{task.app_id},#{snapshot.instance},#{item.object_type},#{item.object_id},#{receipt.localId},#{item.source_version},#{receipt.stateHash},utc_timestamp(6))
""");
                    }
                    else if(item.source_version>=link.ack_version) {
                        sql.update("""
update iam_external_link_t
set ack_version=#{item.source_version},ack_hash=#{receipt.stateHash},last_ack_time=utc_timestamp(6),version=version+1
where tid=#{lid}
""");
                    }
                    if(item.object_type=='SUBJECT') {
                        sql.update("""
update iam_subject_app_t
set ack_version=greatest(ack_version, #{item.source_version}),sync_status=case
        when desired_version<=#{item.source_version} then 'IN_SYNC' else 'PENDING'
        end,version=version+1,
        updated_time=utc_timestamp(6)
where app_id=#{task.app_id}
    and subject_id=#{item.object_id}
""");
                    }
                }
            }
            else if(item.object_type=='SUBJECT') {
                var syncStatus=status=='CONFLICT'?'CONFLICT':'FAILED';
                sql.update("""
update iam_subject_app_t
set sync_status=#{syncStatus},version=version+1
where app_id=#{task.app_id}
    and subject_id=#{item.object_id}
""");
            }
        });
    }
    return sql.transaction(()=>{
        var counts=sql.selectOne("""
select sum(status in ('APPLIED', 'DUPLICATE')) success,sum(status in ('FAILED',
        'CONFLICT')) failure,sum(status in ('PENDING',

        'RUNNING')) pending
from iam_sync_item_t
where task_id=#{taskId}
""");
        var status=counts.pending>0?'PENDING':counts.failure>0?counts.success>0?'PARTIAL':'FAILED':'SUCCEEDED';
        sql.update("""
update iam_sync_task_t
set status=#{status},success_count=#{counts.success},failure_count=#{counts.failure},lease_token=null,
    lease_until=null,version=version+1,updated_time=utc_timestamp(6)
where tid=#{taskId}
    and lease_token=#{lease}
""");
        // 记录操作主体、目标及结果，供审计与故障追踪。
        audit(ctx,status=='SUCCEEDED'?'SUCCESS':'FAILED','sync:execute',taskId,status,{
            id:taskId,success:counts.success,failure:counts.failure
        });
        return {
            id:taskId,status:status
        };
    });

}));
