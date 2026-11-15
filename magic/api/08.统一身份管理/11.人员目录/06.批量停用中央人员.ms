{
  "properties": {},
  "id": "062a1e8f5b825797baf08ddc8654daa8",
  "script": null,
  "groupId": "b3202123a27e56198aed6211ec0f770f",
  "name": "06.批量停用中央人员",
  "createTime": 1791193400395,
  "updateTime": 1791193400395,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/batch-disable",
  "description": "单次最多停用 100 人；在同一事务中逐 ID 校验身份域、版本及机构范围，批量写入人员与应用期望状态。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：批量停用中央人员
// 路由：POST /idaas/users/batch-disable
import db;
import identityRuntime;
import '@/idaas/api-call' as apiCall;
import '@/idaas/directory-scope' as directoryScope;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;

return identityRuntime.control(()=>apiCall(()=>{
    var p=body||{};
    var domain=p.domain||'workforce';
    var rights=directoryScope('users:write',domain);
    var entries=p.entries||[];
    if(entries.size()<1 || entries.size()>100) {
        exit 400,'每次请选择 1 至 100 人';
    }
    var ids=entries.map(e=>e.id);
    if(ids.filter(id=>id==null || !(''+id).matches('[A-Za-z0-9_-]{1,64}')).size()>0
        || ids.distinct().size()!=ids.size()) {
        exit 400,'人员标识重复或不正确';
    }
    var sql=db.normal();
    return mutate(rights.ctx,'users:batch-disable',p,()=>{
        var fresh=directoryScope('users:write',domain);
        var people=sql.select("""
select tid as id,identity_domain,status,version
from iam_subject_t
where tid in <foreach collection="ids" item="id" open="(" close=")" separator=",">#{id}</foreach>
  and identity_domain=#{domain} and is_del=0
for update
""");
        if(people.size()!=ids.size()) {
            exit 404,'人员不存在或不属于当前身份域';
        }
        var appointments=sql.select("""
select subject_id,org_id
from iam_appointment_t
where subject_id in <foreach collection="ids" item="id" open="(" close=")" separator=",">#{id}</foreach>
  and is_del=0 and status='ACTIVE' and valid_from<=utc_timestamp(6)
  and (valid_until is null or valid_until>utc_timestamp(6))
""");
        for(entry in entries) {
            var matches=people.filter(person=>person.id==entry.id);
            if(matches.size()!=1 || matches[0].version!=entry.version) {
                exit 409,'人员版本已变更，请刷新后重试';
            }
            var orgs=appointments.filter(job=>job.subject_id==entry.id).map(job=>job.org_id);
            if(!fresh.all && (orgs.size()==0 || orgs.filter(id=>!fresh.orgIds.contains(id)).size()>0)) {
                exit 403,'选中的人员超出当前管理范围';
            }
        }
        sql.update("""
update iam_subject_t set status='DISABLED',version=version+1,
    updated_by=#{fresh.ctx.userId},updated_time=utc_timestamp(6)
where tid in <foreach collection="ids" item="id" open="(" close=")" separator=",">#{id}</foreach>
""");
        sql.update("""
update iam_subject_app_t set desired_enabled=0,desired_version=desired_version+1,
    version=version+1,updated_time=utc_timestamp(6)
where subject_id in <foreach collection="ids" item="id" open="(" close=")" separator=",">#{id}</foreach>
""");
        audit(fresh.ctx,'SUCCESS','users:batch-disable',null,null,{count:ids.size()});
        return {count:ids.size()};
    });
}));
