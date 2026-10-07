{
  "properties": {},
  "id": "cf6a9cc605f155d0a54adfbb783abec6",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "26.公众法人共用规则",
  "createTime": 1790635007936,
  "updateTime": 1790949884838,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/public-entities",
  "description": "校验法人资料及成员关系、办理身份和状态，供法人管理及自主登记接口复用。",
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
// 共享函数：26.公众法人共用规则
// 业务说明：校验法人资料及成员关系、办理身份和状态，供法人管理及自主登记接口复用。
// 函数输入：operation：本次执行的业务操作；payload：请求资料或筛选条件。
// 返回与副作用：校验法人资料及成员关系、办理身份和状态，供法人管理及自主登记接口复用。
import db;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import '@/idaas/guard' as guard;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import '@/idaas/time-window' as timeWindow;

var sql=db.normal();
var p=payload||{};
var r=p.record||p;
// 先校验当前会话的功能权限、身份域和应用管理范围。
var ctx=guard(operation=='list'?'public:read':'public:write',null,'public');
var id=r.id||p.id;
// 列表分支：先确定当前操作人可见的应用和身份域，再读取并映射业务记录。
if(operation=='list') {
    return sql.select("""
select *,tid as id,date_format(created_time, '%Y-%m-%dT%H:%i:%sZ') created_utc,
       date_format(updated_time, '%Y-%m-%dT%H:%i:%sZ') updated_utc
from iam_legal_entity_t
where is_del=0
order by created_time desc
limit 250
""").map(e=>{
        var members=sql.select("""
select m.*,m.tid as id,s.name,s.phone
from iam_legal_member_t m
join iam_subject_t s on s.tid=m.subject_id
and s.identity_domain='public'
where m.entity_id=#{e.id}
    and m.is_del=0
    and m.status in ('PENDING','ACTIVE')
""");
        var contact=members.filter(m=>m.relation_type=='AGENT');
        return {
            id:e.id,version:e.version,domain:'public',name:e.name,code:e.code,registrationCode:e.registration_code||'',status:e.status=='ACTIVE'?'enabled':'disabled',contactId:contact.size()==0?'':contact[0].subject_id,contact:contact.size()==0?'':contact[0].name,phone:contact.size()==0?'':contact[0].phone||'',verified:e.verification_status=='VERIFIED' && sql.selectOne("""
select tid as id
from iam_verification_t
where target_type='LEGAL_ENTITY'
    and target_id=#{e.id}
    and status='VERIFIED'
    and expires_at>utc_timestamp(6)
    and tid=
        (select x.tid as id
         from iam_verification_t x
         where x.target_type='LEGAL_ENTITY'
             and x.target_id=#{e.id}
         order by x.requested_time desc,x.tid desc
         limit 1)
""")!=null,verificationStatus:e.verification_status,subAccountIds:members.filter(m=>m.relation_type=='SUBACCOUNT').map(m=>m.subject_id),members:members,createdAt:e.created_utc,updatedAt:e.updated_utc
        };
    });
}
// 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
// 事务边界：通过共享幂等写入统一处理版本冲突、提交与审计。
return mutate(ctx,'public-entities:'+operation,p,()=>{
    var old=id==null?null:sql.selectOne("""
select *,tid as id
from iam_legal_entity_t
where tid=#{id}
    and is_del=0
    for update
""");
    if(old!=null && old.version!=(r.version||p.version)) {
        exit 409,'法人记录已变更';
    }
    // 成员启用分支：校验法人及账号关系，再启用办理身份。
    if(operation=='activate-member' || operation=='revoke-member') {
        var member=sql.selectOne("""
select *,tid as id
from iam_legal_member_t
where tid=#{p.memberId}
    and entity_id=#{id}
    and is_del=0
    for update
""");
        if(member==null || member.version!=p.memberVersion || p.reason==null || p.reason.trim()=='') {
            exit 409,'请核对关系版本并填写授权理由';
        }
        var status=operation=='activate-member'?'ACTIVE':'REVOKED';
        var window=timeWindow(p.validFrom,p.validUntil);
        sql.update("""
update iam_legal_member_t
set status=#{status},valid_from=#{window.start},valid_until=#{window.end},version=version+1
where tid=#{member.id}
""");
        audit(ctx,'SUCCESS','public-entities:'+operation,member.id,null,{
            entityId:id,memberId:member.id,reason:p.reason,validFrom:window.start,validUntil:window.end
        });
        return {
            id:id
        };
    }
    // 归档分支：验证版本及引用关系，保留历史记录并撤销有效关联。
    if(operation=='remove') {
        if(old==null) {
            exit 404,'法人记录不存在';
        }
        sql.update("""
update iam_legal_entity_t
set status='ARCHIVED',is_del=1,version=version+1
where tid=#{id}
""");
        sql.update("""
update iam_legal_member_t
set status='REVOKED',version=version+1
where entity_id=#{id}
""");
        audit(ctx,'SUCCESS','public-entities:remove',id,null,{
            id:id
        });
        return {
            id:id
        };
    }
    var name=(r.name||'').trim();
    var code=(r.code||'').trim();
    var registration=(r.registrationCode||'').trim().toUpperCase();
    if(name=='' || name.length()>200 || !code.matches('[A-Za-z0-9_.:-]{2,100}') || old!=null && old.code!=code || registration!='' && !registration.matches('[0-9A-HJ-NPQRTUWXY]{18}')) {
        exit 400,'法人名称、稳定编码或信用代码格式不正确';
    }
    if(r.verified==true && (old==null || old.verification_status!='VERIFIED')) {
        exit 400,'登记和维护不能直接设置核验通过';
    }
    var persons=r.subAccountIds||[];
    if(r.contactId!=null && r.contactId!='' && !persons.contains(r.contactId)) {
        persons.add(r.contactId);
    }
    if(persons.size()>200) {
        exit 400,'关联人数超限';
    }
    for(person in persons) {
        if(sql.selectOne("""
select tid as id
from iam_subject_t
where tid=#{person}
    and identity_domain='public'
    and is_del=0
""")==null) {
            exit 400,'法人关系只能关联公众自然人';
        }
    }
    var credit=registration==''?null:registration;
    var status=r.status=='disabled'?'DISABLED':'ACTIVE';
    if(old==null) {
        if(p.creating!=true) {
            exit 404,'法人记录不存在';
        }
        id=IdUtil.fastSimpleUUID();
        sql.update("""
insert into iam_legal_entity_t(tid, code, name, registration_code, status)
values(#{id},#{code},#{name},#{credit},#{status})
""");
    }
    else {
        sql.update("""
update iam_legal_entity_t
set verification_status=case
        when name<>#{name}
        or not(registration_code<=>#{credit}) then 'UNVERIFIED' else verification_status
        end,name=#{name},registration_code=#{credit},
        status=#{status},version=version+1,updated_time=utc_timestamp(6)
where tid=#{id}
""");
    }
    var prior=sql.select("""
select *,tid as id
from iam_legal_member_t
where entity_id=#{id}
    and is_del=0
""");
    for(member in prior) {
        var type=member.subject_id==r.contactId?'AGENT':'SUBACCOUNT';
        if(!persons.contains(member.subject_id) || type!=member.relation_type) {
            sql.update("""
update iam_legal_member_t
set status='REVOKED',is_del=1,version=version+1
where tid=#{member.id}
""");
        }
    }
    for(person in persons) {
        var type=person==r.contactId?'AGENT':'SUBACCOUNT';
        if(prior.filter(m=>m.subject_id==person && m.relation_type==type && m.is_del==0 && m.status!='REVOKED').size()==0) {
            var mid=IdUtil.fastSimpleUUID();
            sql.update("""
insert into iam_legal_member_t(tid,
                entity_id, subject_id, relation_type, valid_from, status)
values(#{mid},#{id},#{person},#{type},utc_timestamp(6),'PENDING')
""");
        }
    }
    audit(ctx,'SUCCESS','public-entities:save',id,null,{
        id:id,memberCount:persons.size()
    });
    return {
        id:id
    };
});
