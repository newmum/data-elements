{
  "properties": {},
  "id": "d7472c271f795beb9e0ca744a0bb9baa",
  "script": null,
  "groupId": "a1d9f7b3309256e7baebe11719797bac",
  "name": "05.撤销法人账号成员",
  "createTime": 1790635007936,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/revoke-member",
  "description": "核验法人、账号与操作范围，撤销成员关系并阻止后续以该法人身份办理业务。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：05.撤销法人账号成员
// 路由：POST /idaas/public-entities/revoke-member
// 业务说明：核验法人、账号与操作范围，撤销成员关系并阻止后续以该法人身份办理业务。
import db;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import '@/idaas/guard' as guard;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import '@/idaas/time-window' as timeWindow;
import identityRuntime;
import request;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    var payload=body;
    // 请求参数与本接口业务处理。
    var operation = 'revoke-member';

    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
    var r=p.record||p;
    // 先校验当前会话的功能权限、身份域和应用管理范围。
    var ctx=guard(operation=='list'?'public:read':'public:write',null,'public');
    var id=r.id||p.id;
    // 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
    return mutate(ctx,'public-entities:'+operation,p,()=>{
        var old=id==null?null:sql.selectOne("""
select *,tid as id
from iam_legal_entity_t
where tid=#{id}
    and is_del=0
    for update
""");
        // 以当前版本校验修改目标，阻止旧页面覆盖已经变更的记录。
        if(old!=null && old.version!=(r.version||p.version)) {
            exit 409,'法人记录已变更';
        }
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
            // 记录操作主体、目标及结果，供审计与故障追踪。
            audit(ctx,'SUCCESS','public-entities:'+operation,member.id,null,{
                entityId:id,memberId:member.id,reason:p.reason,validFrom:window.start,validUntil:window.end
            });
            return {
                id:id
            };
        }
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

}));
