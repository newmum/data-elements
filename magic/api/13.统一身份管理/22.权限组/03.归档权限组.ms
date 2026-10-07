{
  "properties": {},
  "id": "ed4e9afa31fe5fd9bdfb3cf54a7e2db5",
  "script": null,
  "groupId": "b2dabd7a0ef25609852aa2dc1d6eadb1",
  "name": "03.归档权限组",
  "createTime": 1790617158595,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/remove",
  "description": "按标识加载权限组并检查所属范围与版本；执行软删除或撤销关联，保留审计历史。维护业务应用的权限组及成员关系，用于聚合可授权的人员范围。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：03.归档权限组
// 路由：POST /idaas/permission-groups/remove
// 业务说明：按标识加载权限组并检查所属范围与版本；执行软删除或撤销关联，保留审计历史。维护业务应用的权限组及成员关系，用于聚合可授权的人员范围。
import db;
import cn.hutool.core.util.IdUtil;
import '@/idaas/guard' as guard;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import '@/idaas/business-grants' as grants;
import '@/idaas/time-window' as window;
import '@/idaas/rebuild-projections' as rebuild;
import identityRuntime;
import request;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    var payload=body;
    // 请求参数与本接口业务处理。
    var operation = 'remove';

    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
    var r=p.record||p;
    var domain=r.domain||p.domain||'workforce';
    // 先校验当前会话的功能权限、身份域和应用管理范围。
    var ctx=guard(operation=='list'?'grants:read':'grants:write',null,domain);
    // 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
    return mutate(ctx,'permission-groups:'+operation,p,()=>{
        var id=r.id||p.id;
        var old=id==null?null:sql.selectOne("""
select *,tid as id
from iam_permission_group_t
where tid=#{id}
    and is_del=0
    for update
""");
        if(old==null && p.creating!=true) {
            exit 404,'权限组不存在';
        }
        // 以当前版本校验修改目标，阻止旧页面覆盖已经变更的记录。
        if(old!=null && (old.identity_domain!=domain || old.version!=(r.version||p.version))) {
            exit 409,'权限组身份域或版本已变更';
        }
        if(operation=='remove') {
            sql.update("""
update iam_permission_group_t
set status='DISABLED',is_del=1,version=version+1
where tid=#{id}
""");
            sql.update("""
update iam_access_grant_t
set status='REVOKED',version=version+1
where group_id=#{id}
""");
            sql.update("""
update iam_group_member_t
set status='REVOKED',version=version+1
where group_id=#{id}
""");
            rebuild(null,domain);
            // 记录操作主体、目标及结果，供审计与故障追踪。
            audit(ctx,'SUCCESS','permission-groups:remove',id,null,{
                id:id
            });
            return {
                id:id
            };
        }
        var name=(r.name||'').trim();
        var description=r.description||'';
        var code=old==null?(r.code||'GROUP_'+IdUtil.fastSimpleUUID()):old.code;
        var status=r.status=='disabled'?'DISABLED':'ACTIVE';
        var users=r.userIds||[];
        var items=r.items||[];
        if(name=='' || name.length()>200 || description.length()>1000 || !code.matches('[A-Za-z][A-Za-z0-9_.:-]{1,99}') || users.size()>1000 || users.distinct().size()!=users.size() || items.size()>50 || items.map(i=>i.appId).distinct().size()!=items.size()) {
            exit 400,'名称、成员或应用组合不正确';
        }
        for(sid in users) {
            if(sql.selectOne("""
select tid as id
from iam_subject_t
where tid=#{sid}
    and identity_domain=#{domain}
    and is_del=0
    and status='ACTIVE'
""")==null) {
                exit 400,'成员须为同域中央人员';
            }
        }
        // 先校验全部组合，组写入与来源、成员都在同一事务内。
        for(item in items) {
            guard('grants:write',item.appId,domain);
            if(sql.selectOne("""
select tid as id
from iam_application_t
where tid=#{item.appId}
    and kind='BUSINESS'
    and status='ACTIVE'
    and is_del=0
""")==null) {
                exit 400,'组合仅允许有效业务应用';
            }
        }
        if(old==null) {
            id=IdUtil.fastSimpleUUID();
            sql.update("""
insert into iam_permission_group_t(tid,
                identity_domain, code, name, description, status)
values(#{id},#{domain},#{code},#{name},#{description},#{status})
""");
        }
        else {
            sql.update("""
update iam_permission_group_t
set name=#{name},description=#{description},status=#{status},version=version+1,
        updated_time=utc_timestamp(6)
where tid=#{id}
""");
        }
        sql.update("""
update iam_group_member_t
set status='REVOKED',version=version+1
where group_id=#{id}
""");
        for(sid in users) {
            var prior=sql.selectOne("""
select *,tid as id
from iam_group_member_t
where group_id=#{id}
    and subject_id=#{sid}
""");
            var details=(r.members||[]).filter(m=>m.subjectId==sid);
            var times=details.size()>0?window(details[0].startsAt,details[0].expiresAt):window(null,null);
            var mid=prior==null?IdUtil.fastSimpleUUID():prior.id;
            if(prior==null) {
                sql.update("""
insert into iam_group_member_t(tid,
                group_id, subject_id, valid_from, valid_until)
values(#{mid},#{id},#{sid},#{times.start},#{times.end})
""");
            }
            else if(details.size()>0) {
                sql.update("""
update iam_group_member_t
set status='ACTIVE',valid_from=#{times.start},valid_until=#{times.end},version=version+1
where tid=#{mid}
""");
            }
            else {
                sql.update("""
update iam_group_member_t
set status='ACTIVE',version=version+1
where tid=#{mid}
""");
            }
        }
        var current=sql.select("""
select *,tid as id
from iam_access_grant_t
where group_id=#{id}
    and is_del=0
""");
        for(g in current) {
            if(items.filter(i=>i.appId==g.app_id).size()==0 || status!='ACTIVE') {
                sql.update("""
update iam_access_grant_t
set status='REVOKED',version=version+1
where tid=#{g.id}
""");
            }
        }
        if(status=='ACTIVE') {
            for(item in items) {
                var existing=current.filter(g=>g.app_id==item.appId);
                var prior=existing.size()>0?existing[0]:null;
                grants('save',{
                    requestId:IdUtil.fastSimpleUUID(),creating:prior==null,record:{
                        id:prior==null?null:prior.id,version:prior==null?1:prior.version,domain:domain,source:'group',subjectId:id,appId:item.appId,roleIds:item.roleIds||[],reason:'权限组组合',status:'enabled'
                    }
                });
            }
        }
        rebuild(null,domain);
        audit(ctx,'SUCCESS','permission-groups:save',id,null,{
            id:id,memberCount:users.size()
        });
        return {
            id:id
        };
    });

}));
