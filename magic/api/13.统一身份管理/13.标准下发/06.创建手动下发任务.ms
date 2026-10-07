{
  "properties": {},
  "id": "d2ae39bdaaf355b98c48d3f1ae366416",
  "script": null,
  "groupId": "07899fe85f4d59fd90de0507c7f4d188",
  "name": "06.创建手动下发任务",
  "createTime": 1790612165860,
  "updateTime": 1791194872495,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/create",
  "description": "核验应用与资料范围，生成待执行的下发任务及目标快照；创建记录本身不通过后台轮询执行。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：06.创建手动下发任务
// 路由：POST /idaas/provision/create
// 业务说明：核验应用与资料范围，生成待执行的下发任务及目标快照；创建记录本身不通过后台轮询执行。
import db;
import identityTransport;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import '@/idaas/guard' as guard;
import '@/idaas/context' as context;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import '@/idaas/rebuild-projections' as rebuild;
import '@/idaas/effective-access-batch' as effectiveBatch;
import '@/idaas/resource-enabled' as enabled;
import identityRuntime;
import request;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    var payload=body;
    // 请求参数与本接口业务处理。

    // 此流程仅由明确的接口请求触发，不在后台自动扫描或下发租户数据。
    // 冻结规范JSON和目标实例。仅同步明确分配的资料，不把全库人员隐含分配到应用。
    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
    context();
    var config=sql.selectOne("""
select *,tid as id
from iam_sync_config_t
where tid=#{p.configId}
    and is_del=0
""");
    if(config==null) {
        exit 404,'配置不存在';
    }
    var appId=config.app_id;
    // 先校验当前会话的功能权限、身份域和应用管理范围。
    var ctx=guard('sync:write',appId,'workforce');
    // 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
    return mutate(ctx,'sync:create',p,()=>{
        config=sql.selectOne("""
select *,tid as id
from iam_sync_config_t
where tid=#{p.configId}
    and is_del=0
    for update
""");
        if(config.enabled!=1 || config.last_test_result!='SUCCESS') {
            exit 409,'接收配置尚未测试并启用';
        }
        var existingLocal=config.mapping_json!=null &&
            JSONUtil.parseObj(config.mapping_json).getStr('mode')=='EXISTING_LOCAL';
        if(existingLocal && p.kind!='user') {
            exit 409,'现有本地目录映射仅支持按人员下发；机构和权限保留在接收系统维护';
        }
        rebuild(appId,'workforce');
        var selected=p.selectedIds||[];
        if(selected.size()>1000) {
            exit 400,'选中对象过多';
        }
        var subjects=sql.select("""
select s.*,s.tid as id,sa.account_alias,sa.desired_enabled,sa.desired_version,sa.assignment_status
from iam_subject_app_t sa
join iam_subject_t s on s.tid=sa.subject_id
where sa.app_id=#{appId}
    and s.identity_domain='workforce'
""");
        if(p.kind=='user' && selected.size()>0) {
            if(selected.filter(id=>subjects.filter(s=>s.id==id).size()==0).size()>0) {
                exit 400,'请先在应用中分配所选人员';
            }
            subjects=subjects.filter(s=>selected.contains(s.id));
        }
        if(p.kind=='org') {
            subjects=[];
        }
        var orgIds=[];
        var ranges=sql.select("""
select o.path,ao.include_children
from iam_app_org_t ao
join iam_org_t o on o.tid=ao.org_id
where ao.app_id=#{appId}
    and ao.status='ACTIVE'
""");
        var orgs=sql.select("""
select *,tid as id
from iam_org_t
where identity_domain='workforce'
order by depth,tid
""");
        for(org in orgs) {
            if(ranges.filter(r=>r.path==org.path || r.include_children==1 && org.path.startsWith(r.path)).size()>0) {
                orgIds.add(org.id);
            }
        }
        var subjectIds=subjects.map(subject=>subject.id);
        var allJobs=subjectIds.size()==0?[]:sql.select("""
select subject_id,org_id,valid_from,valid_until,is_primary
from iam_appointment_t
where subject_id in <foreach collection="subjectIds" item="subjectId" open="(" close=")" separator=",">#{subjectId}</foreach>
    and status='ACTIVE' and is_del=0
""");
        for(subject in subjects) {
            for(job in allJobs.filter(row=>row.subject_id==subject.id)) {
                if(!orgIds.contains(job.org_id)) {
                    orgIds.add(job.org_id);
                }
            }
        }
        if(p.kind=='org' && selected.size()>0) {
            if(selected.filter(id=>!orgIds.contains(id)).size()>0) {
                exit 403,'机构不在应用分配范围内';
            }
            orgIds=orgIds.filter(id=>selected.contains(id));
        }
        // 每个任职机构需要的祖先也进入同一依赖顺序。
        for(org in orgs) {
            if(orgIds.contains(org.id)) {
                for(parent in orgs) {
                    if(org.path.startsWith(parent.path) && !orgIds.contains(parent.id)) {
                        orgIds.add(parent.id);
                    }
                }
            }
        }
        // Imported applications may keep their own org/role/menu rows. Their explicit
        // source-to-local links live in the receiver's object map. In this mode a
        // selected person is the only event; the receiver resolves every referenced
        // org and role against that map and fails closed if a link is missing.
        var mappedUser=existingLocal;
        if(mappedUser) {
            orgIds=[];
        }
        var roleRows=mappedUser?[]:sql.select("""
select *,tid as id
from iam_role_t
where app_id=#{appId}
    and identity_domain='workforce'
order by tid
""");
        var resourceRows=mappedUser?[]:sql.select("""
select *,tid as id
from iam_resource_t
where app_id=#{appId}
    and identity_domain='workforce'
order by tid
""");
        var roleResources=mappedUser?[]:sql.select("""
select rr.role_id,rr.resource_id
from iam_role_resource_rela_t rr
join iam_resource_t r on r.tid=rr.resource_id and r.app_id=rr.app_id
  and r.status='ACTIVE' and r.is_del=0
where rr.app_id=#{appId}
order by rr.role_id,rr.resource_id
""");
        var enabledResourceIds=mappedUser?[]:enabled(appId,'workforce',roleResources.map(row=>row.resource_id).distinct());
        var accessBySubject=subjectIds.size()==0?{}:effectiveBatch(appId,'workforce',subjectIds);
        var validJobs=subjectIds.size()==0?[]:sql.select("""
select subject_id,org_id,is_primary
from iam_appointment_t
where subject_id in <foreach collection="subjectIds" item="subjectId" open="(" close=")" separator=",">#{subjectId}</foreach>
    and status='ACTIVE' and is_del=0 and valid_from<=utc_timestamp(6)
    and (valid_until is null or valid_until>utc_timestamp(6))
order by subject_id,org_id
""");
        var ordered=[];
        var remaining=resourceRows;
        var visited=[];
        while(remaining.size()>0) {
            var round=remaining.filter(r=>r.parent_id==null || visited.contains(r.parent_id));
            if(round.size()==0) {
                exit 409,'资源依赖不完整';
            }
            for(r in round) {
                ordered.add(r);
                visited.add(r.id);
            }
            remaining=remaining.filter(r=>!visited.contains(r.id));
        }
        var taskId=IdUtil.fastSimpleUUID();
        var key=sql.selectOne("""
select public_data
from iam_credential_t
where owner_kind='SYNC_CONFIG'
    and owner_id=#{config.id}
    and credential_type='HMAC_KEY'
    and status='ACTIVE'
""");
        if(key==null) {
            exit 409,'没有活动接入凭据';
        }
        var snapshot=identityTransport.canonicalJson({
            endpoint:config.endpoint,instance:config.receiver_instance_id,protocol:config.protocol_version,mapping:config.mapping_json,keyId:JSONUtil.parseObj(key.public_data).keyId
        });
        var mode=p.mode||'FULL';
        if(!['FULL','DELTA','RECONCILE'].contains(mode)) {
            exit 400,'任务模式不正确';
        }
        var candidateIds=[];
        for(id in orgIds) {
            if(!candidateIds.contains(id)) {
                candidateIds.add(id);
            }
        }
        for(row in resourceRows) {
            if(!candidateIds.contains(row.id)) {
                candidateIds.add(row.id);
            }
        }
        for(row in roleRows) {
            if(!candidateIds.contains(row.id)) {
                candidateIds.add(row.id);
            }
        }
        for(id in subjectIds) {
            if(!candidateIds.contains(id)) {
                candidateIds.add(id);
            }
        }
        if(candidateIds.size()>1000) {
            exit 400,'任务超过1000项，请缩小应用分配范围';
        }
        var previousRows=candidateIds.size()==0?[]:sql.select("""
select object_type,object_id,source_version,payload_json
from (select object_type,object_id,source_version,payload_json,
    row_number() over(partition by object_type,object_id
      order by source_version desc,created_time desc) rn
  from iam_sync_item_t
  where app_id=#{appId}
    and object_id in <foreach collection="candidateIds" item="candidateId" open="(" close=")" separator=",">#{candidateId}</foreach>) latest
where rn=1
""");
        var links=candidateIds.size()==0?[]:sql.select("""
select *,tid as id
from iam_external_link_t
where app_id=#{appId} and receiver_instance_id=#{config.receiver_instance_id}
  and source_id in <foreach collection="candidateIds" item="candidateId" open="(" close=")" separator=",">#{candidateId}</foreach>
""");
        var ackRows=mode!='DELTA' || candidateIds.size()==0?[]:sql.select("""
select object_type,object_id,payload_json
from (select i.object_type,i.object_id,i.payload_json,
    row_number() over(partition by i.object_type,i.object_id order by i.created_time desc) rn
  from iam_sync_item_t i
  join iam_external_link_t l on l.app_id=i.app_id and l.object_type=i.object_type
    and l.source_id=i.object_id and l.ack_version=i.source_version
    and l.receiver_instance_id=#{config.receiver_instance_id}
  where i.app_id=#{appId} and i.status in ('APPLIED','DUPLICATE')
    and i.object_id in <foreach collection="candidateIds" item="candidateId" open="(" close=")" separator=",">#{candidateId}</foreach>) ack
where rn=1
""");
        sql.update("""
insert into iam_sync_task_t(tid,
                app_id, config_id, config_version, config_snapshot_json, mode, request_id, created_by)
values(#{taskId},#{appId},#{config.id},#{config.version},#{snapshot},#{mode},#{p.requestId},#{ctx.userId})
""");
        var sequence=0;
        var events={};
        var pendingItems=[];
        var add=(type,id,version,data,parent)=>{
            var prior=previousRows.filter(row=>row.object_type==type && row.object_id==id);
            var previous=prior.size()==0?null:prior[0];
            if(previous!=null) {
                var oldData=identityTransport.parseJson(previous.payload_json).data;
                var equal=identityTransport.canonicalJson(data)==identityTransport.canonicalJson(oldData);
                var floor=previous.source_version+(equal?0:1);
                if(version<floor) {
                    version=floor;
                }
            }
            // DELTA只跳过同实例已经确认且内容相同的对象；未确认失败事件仍需重发。
            var found=links.filter(row=>row.object_type==type && row.source_id==id);
            var link=found.size()==0?null:found[0];
            if(mode=='DELTA' && link!=null) {
                var confirmed=ackRows.filter(row=>row.object_type==type && row.object_id==id);
                var ack=confirmed.size()==0?null:confirmed[0];
                if(ack!=null && identityTransport.parseJson(ack.payload_json).receiverInstanceId==config.receiver_instance_id && identityTransport.canonicalJson(data)==identityTransport.canonicalJson(identityTransport.parseJson(ack.payload_json).data)) {
                    return;
                }
            }
            var eventId=IdUtil.fastSimpleUUID();
            sequence=sequence+1;
            var event={
                eventId:eventId,appId:appId,receiverInstanceId:config.receiver_instance_id,objectType:type,objectId:id,sourceVersion:version,action:data.enabled==true?'UPSERT':'DISABLE',data:data
            };
            var encoded=identityTransport.canonicalJson(event);
            var hash=DigestUtil.sha256Hex(encoded);
            var dependency=parent==null?null:events.get(type+'/'+parent);
            pendingItems.add({id:eventId,type:type,objectId:id,version:version,action:event.action,
                sequence:sequence,dependency:dependency,encoded:encoded,hash:hash});
            events.put(type+'/'+id,eventId);
        };
        for(org in orgs) {
            if(orgIds.contains(org.id)) {
                add('ORG',org.id,org.version,{
                    name:org.name,code:org.code,parentId:org.parent_id,enabled:org.status=='ACTIVE' && org.is_del==0
                },org.parent_id);
            }
        }
        for(resource in ordered) {
            add('RESOURCE',resource.id,resource.version,{
                name:resource.name,code:resource.code,parentId:resource.parent_id,type:resource.resource_type.toLowerCase(),path:resource.path||'',method:resource.http_method||'GET',enabled:resource.status=='ACTIVE' && resource.is_del==0
            },resource.parent_id);
        }
        for(role in roleRows) {
            var resources=roleResources.filter(row=>row.role_id==role.id && enabledResourceIds.contains(row.resource_id)).map(row=>row.resource_id);
            add('ROLE',role.id,role.version,{
                name:role.name,code:role.code,resourceIds:resources,enabled:role.status=='ACTIVE' && role.is_del==0
            },null);
        }
        for(subject in subjects) {
            var access=accessBySubject.get(subject.id);
            var jobs=validJobs.filter(row=>row.subject_id==subject.id).map(j=>({
                orgId:j.org_id,primary:j.is_primary==1
            }));
            add('SUBJECT',subject.id,subject.desired_version,{
                name:subject.name,account:subject.account_alias||subject.account_hint,phone:subject.phone||'',email:subject.email||'',appointments:jobs,roleIds:access.roleIds,enabled:access.hasAccess && subject.assignment_status=='ACTIVE'
            },null);
        }
        if(sequence>1000 || sequence==0 && mode!='DELTA') {
            exit 400,'没有可下发对象或任务超过1000项，请先配置应用分配范围';
        }
        if(pendingItems.size()>0) {
            sql.update("""
insert into iam_sync_item_t(tid,task_id,app_id,object_type,object_id,source_version,
    event_action,sequence_no,depends_on_id,payload_json,payload_hash)
values <foreach collection="pendingItems" item="item" open="(" close=")" separator="),(">
    #{item.id},#{taskId},#{appId},#{item.type},#{item.objectId},#{item.version},
     #{item.action},#{item.sequence},#{item.dependency},#{item.encoded},#{item.hash}
</foreach>
""");
        }
        var taskStatus=sequence==0?'SUCCEEDED':'PENDING';
        sql.update("""
update iam_sync_task_t
set total_count=#{sequence},status=#{taskStatus}
where tid=#{taskId}
""");
        sql.update("""
update iam_subject_app_t
set sync_status='PENDING'
where app_id=#{appId}
    and subject_id in
        (select object_id
         from iam_sync_item_t
         where task_id=#{taskId}
             and object_type='SUBJECT')
""");
        // 记录操作主体、目标及结果，供审计与故障追踪。
        audit(ctx,'SUCCESS','sync:create',taskId,null,{
            id:taskId,items:sequence
        });
        return {
            id:taskId,total:sequence
        };
    });

}));
