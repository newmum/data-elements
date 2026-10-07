{
  "properties": {},
  "id": "10b412971f255f5cb7831b43cd182d46",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "17.重建授权结果投影",
  "createTime": 1790617158595,
  "updateTime": 1791194872495,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/rebuild-projections",
  "description": "在明确的业务写入请求中重算受影响人员与应用的授权结果并更新投影；不启动后台轮询。",
  "parameters": [
    {
      "name": "appId",
      "value": null,
      "description": "目标业务应用标识",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "identityDomain",
      "value": null,
      "description": "人员或公众身份域",
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
// 共享函数：17.重建授权结果投影
// 业务说明：在明确的业务写入请求中重算受影响人员与应用的授权结果并更新投影；不启动后台轮询。
// 函数输入：appId：目标业务应用标识；identityDomain：人员或公众身份域。
// 返回与副作用：在明确的业务写入请求中重算受影响人员与应用的授权结果并更新投影；不启动后台轮询。
import db;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import '@/idaas/effective-access-batch' as effectiveBatch;

var sql=db.normal();
var domain=identityDomain||'workforce';
// 只在明确调用时枚举受影响应用，不安装周期扫描任务。
var apps=sql.select("""
select tid as id
from iam_application_t
where kind='BUSINESS'
    and is_del=0
""");
for(app in apps) {
    if(appId!=null && app.id!=appId) {
        continue;
    }
    // 同时覆盖现有人员和已有投影人员，使授权撤销也能更新旧投影。
    var subjects=sql.select("""
select tid as id
from iam_subject_t
where identity_domain=#{domain}
    and is_del=0
union
select sa.subject_id tid
from iam_subject_app_t sa
join iam_subject_t s on s.tid=sa.subject_id
where s.identity_domain=#{domain}
    and sa.app_id=#{app.id}
""");
    // 一次锁定现有投影；计算与写入每 500 人一组，SQL 数不按人员数逐项增长。
    var priors=sql.select("""
select *,tid as id
from iam_subject_app_t
where app_id=#{app.id}
for update
""");
    var process=(batch)=>{
        var accessById=effectiveBatch(app.id,domain,batch.map(row=>row.id));
        var changes=[];
        for(subject in batch) {
            var access=accessById.get(subject.id);
            var hash=DigestUtil.sha256Hex(JSONUtil.toJsonStr({
                access:access.hasAccess,roles:access.roleIds,resources:access.resourceIds
            }));
            var found=priors.filter(row=>row.subject_id==subject.id);
            var prior=found.size()==0?null:found[0];
            if(prior==null && !access.hasAccess) {
                continue;
            }
            var mode=access.hasAccess?'GRANT_DERIVED':'DIRECTORY_ONLY';
            var status=access.hasAccess || prior!=null && prior.directory_assigned==1?'ACTIVE':'REVOKED';
            var desired=access.hasAccess?1:0;
            if(prior==null || prior.projection_hash!=hash || prior.desired_enabled!=desired
                || prior.assignment_mode!=mode || prior.assignment_status!=status) {
                changes.add({id:prior==null?IdUtil.fastSimpleUUID():prior.id,
                    subjectId:subject.id,mode:mode,status:status,desired:desired,hash:hash});
            }
        }
        if(changes.size()>0) {
            sql.update("""
insert into iam_subject_app_t(tid,subject_id,app_id,assignment_mode,directory_assigned,
    assignment_status,desired_enabled,projection_hash)
values <foreach collection="changes" item="row" open="(" close=")" separator="),(">
    #{row.id},#{row.subjectId},#{app.id},#{row.mode},0,#{row.status},#{row.desired},#{row.hash}
</foreach>
on duplicate key update assignment_mode=values(assignment_mode),
    assignment_status=values(assignment_status),desired_enabled=values(desired_enabled),
    projection_hash=values(projection_hash),desired_version=desired_version+1,
    version=version+1,sync_status=case when ack_version>0 then 'PENDING' else 'NOT_CONNECTED' end,
    updated_time=utc_timestamp(6)
""");
        }
    };
    var batch=[];
    for(subject in subjects) {
        batch.add(subject);
        if(batch.size()==500) {
            process(batch);
            batch=[];
        }
    }
    if(batch.size()>0) {
        process(batch);
    }
}
return true;
