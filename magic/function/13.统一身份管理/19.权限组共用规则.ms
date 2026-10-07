{
  "properties": {},
  "id": "9d08427e037b59d8ba66f337e68cfd68",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "19.权限组共用规则",
  "createTime": 1790617158595,
  "updateTime": 1790949884838,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/permission-groups",
  "description": "维护业务应用权限组、成员和授权关系，校验同一应用内的对象范围。",
  "parameters": [
    {
      "name": "operation",
      "value": null,
      "description": "本次执行的业务操作",
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
      "name": "payload",
      "value": null,
      "description": "请求资料或筛选条件",
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
// 共享函数：19.权限组共用规则
// 业务说明：维护业务应用权限组、成员和授权关系，校验同一应用内的对象范围。
// 函数输入：operation：本次执行的业务操作；payload：请求资料或筛选条件。
// 返回与副作用：维护业务应用权限组、成员和授权关系，校验同一应用内的对象范围。
import db;
import cn.hutool.core.util.IdUtil;
import '@/idaas/guard' as guard;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import '@/idaas/business-grants' as grants;
import '@/idaas/time-window' as window;
import '@/idaas/rebuild-projections' as rebuild;

var sql=db.normal();
var p=payload||{};
var r=p.record||p;
var domain=r.domain||p.domain||'workforce';
// 先校验当前会话的功能权限、身份域和应用管理范围。
var ctx=guard(operation=='list'?'grants:read':'grants:write',null,domain);
// 列表分支：先确定当前操作人可见的应用和身份域，再读取并映射业务记录。
if(operation=='list') {
    var rows=sql.select("""
select *,tid as id,date_format(created_time, '%Y-%m-%dT%H:%i:%sZ') created_utc,
       date_format(updated_time, '%Y-%m-%dT%H:%i:%sZ') updated_utc
from iam_permission_group_t
where identity_domain=#{domain}
    and is_del=0
order by tid
""");
    return rows.map(x=>({
        id:x.id,domain:x.identity_domain,name:x.name,code:x.code,description:x.description||'',status:x.status=='ACTIVE'?'enabled':'disabled',version:x.version,createdAt:x.created_utc,updatedAt:x.updated_utc,userIds:sql.select("""
select subject_id
from iam_group_member_t
where group_id=#{x.id}
    and status='ACTIVE'
""").map(m=>m.subject_id),members:sql.select("""
select subject_id subjectId,date_format(valid_from,
        '%Y-%m-%dT%H:%i:%sZ') startsAt,
       date_format(valid_until, '%Y-%m-%dT%H:%i:%sZ') expiresAt
from iam_group_member_t
where group_id=#{x.id}
    and status='ACTIVE'
"""),items:sql.select("""
select tid as id,app_id
from iam_access_grant_t
where group_id=#{x.id}
    and status='ACTIVE'
    and is_del=0
""").map(g=>({
            appId:g.app_id,roleIds:sql.select("""
select role_id
from iam_grant_role_rela_t
where grant_id=#{g.id}
""").map(y=>y.role_id)
        }))
    }));
}
// 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
// 事务边界：通过共享幂等写入统一处理版本冲突、提交与审计。
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
    if(old!=null && (old.identity_domain!=domain || old.version!=(r.version||p.version))) {
        exit 409,'权限组身份域或版本已变更';
    }
    // 归档分支：验证版本及引用关系，保留历史记录并撤销有效关联。
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
