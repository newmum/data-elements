{
  "properties": {},
  "id": "5c56009a3c805d3a90bd53cf23e8245c",
  "script": null,
  "groupId": "64e411148c13506897632e1f5bbe2c15",
  "name": "02.保存平台操作账号",
  "createTime": 1790612165860,
  "updateTime": 1791205192024,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/save",
  "description": "校验请求字段、所属范围和版本；新建或更新平台操作账号及必要关联记录，写入审计并返回保存结果。平台操作账号只用于统一身份管理控制台；凭据与安全状态独立保存，不创建或修改租户本地账号。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：02.保存平台操作账号
// 路由：POST /idaas/operators/save
// 业务说明：校验请求字段、所属范围和版本；新建或更新平台操作账号及必要关联记录，写入审计并返回保存结果。平台操作账号只用于统一身份管理控制台；凭据与安全状态独立保存，不创建或修改租户本地账号。
import db;
import platformSession;
import iamSecurity;
import '@/idaas/guard' as guard;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import '@/idaas/password-policy' as passwordPolicy;
import '@/idaas/directory-scope' as directoryScope;
import '@/idaas/operator-name' as operatorName;
import cn.hutool.core.util.IdUtil;
import identityRuntime;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    // 请求参数与本接口业务处理。
    var operation = 'save';
    var payload = body||{};

    // 平台账号生命周期；凭据仅写安全表，停用/重置导致安全版本失效。
    var sql=db.normal();
    var p=payload||{};
    var r=p.record||p;
    // 先校验当前会话的功能权限、身份域和应用管理范围。
    var ctx=guard(operation=='list'?'operators:read':'operators:write',null,'workforce');
    // 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
    return mutate(ctx,'operators:'+operation,p,()=>{
        var id=r.id;
        var old=sql.selectOne("""
select *,tid as id
from iam_operator_t
where tid=#{id}
    and is_del=0
    for update
""");
        if(old==null && p.creating!=true) {
            exit 404,'平台账号不存在';
        }
        // 以当前版本校验修改目标，阻止旧页面覆盖已经变更的记录。
        if(old!=null && r.version!=old.version) {
            exit 409,'账号已被修改，请刷新';
        }
        if(id==ctx.userId && (operation=='remove' || r.status=='DISABLED' || r.status=='disabled')) {
            exit 409,'不能在当前会话停用或删除自己';
        }
        if(id==ctx.userId && operation=='reset-password') {
            exit 400,'请在个人中心修改自己的密码';
        }
        if(old!=null && (operation=='remove' || r.status=='DISABLED' || r.status=='disabled')) {
            var managers=sql.selectOne("""
select count(distinct o.tid) as total
from iam_operator_t o
join iam_operator_role_rela_t a on a.operator_id=o.tid
and a.status='ACTIVE'
and a.valid_from<=utc_timestamp(6)
and (a.valid_until is null
     or a.valid_until>utc_timestamp(6))
join iam_role_t rr on rr.tid=a.role_id
and rr.status='ACTIVE'
and rr.is_del=0
join iam_role_resource_rela_t rel on rel.role_id=rr.tid
join iam_resource_t res on res.tid=rel.resource_id
join iam_operator_scope_t sc on sc.assignment_id=a.tid
and sc.scope_kind='ALL'
and sc.identity_domain='workforce'
where o.is_del=0
    and o.status='ACTIVE'
    and o.tid<>#{id}
    and res.code='operators:grant'
    and res.is_del=0
    and res.status='ACTIVE'
""");
            if(managers.total==0) {
                exit 409,'不能移除最后一个有效的权限管理账号';
            }
        }
        if(operation=='reset-password') {
            passwordPolicy(r.password);
            var encoded=iamSecurity.hash(r.password);
            var credId=IdUtil.fastSimpleUUID();
            var current=sql.selectOne("""
select coalesce(max(revision), 0)+1 as revision
from iam_credential_t
where owner_kind='OPERATOR'
    and owner_id=#{id}
    and credential_type='PASSWORD'
""");
            sql.update("""
update iam_credential_t
set status='RETIRED',updated_time=utc_timestamp(6)
where owner_kind='OPERATOR'
    and owner_id=#{id}
    and credential_type='PASSWORD'
    and status='ACTIVE'
""");
            sql.update("""
insert into iam_credential_t(tid,
                owner_kind, owner_id, credential_type, slot, revision, algorithm, secret_hash, status)
values(#{credId},'OPERATOR',#{id},'PASSWORD','primary',#{current.revision},'PBKDF2_SHA256',#{encoded},'ACTIVE')
""");
            sql.update("""
update iam_operator_t
set must_change_password=1,version=version+1,updated_time=utc_timestamp(6)
where tid=#{id}
""");
            sql.update("""
update iam_account_security_t
set security_version=security_version+1,failed_count=0,locked_until=null,password_changed_time=utc_timestamp(6)
where principal_kind='OPERATOR'
    and principal_id=#{id}
""");
        }
        else if(operation=='unlock') {
            sql.update("""
update iam_account_security_t
set failed_count=0,locked_until=null,version=version+1,updated_time=utc_timestamp(6)
where principal_kind='OPERATOR'
    and principal_id=#{id}
""");
        }
        else if(operation=='remove') {
            sql.update("""
update iam_operator_t
set is_del=1,status='DISABLED',version=version+1,updated_time=utc_timestamp(6)
where tid=#{id}
""");
            sql.update("""
update iam_account_security_t
set security_version=security_version+1
where principal_kind='OPERATOR'
    and principal_id=#{id}
""");
        }
        else if(operation=='save') {
            var username=(r.username||'').trim().toLowerCase();
            var name=(r.displayName||r.display_name||'').trim();
            var status=r.status=='disabled' || r.status=='DISABLED'?'DISABLED':'ACTIVE';
            if(!username.matches('[A-Za-z][A-Za-z0-9_.-]{2,49}') || name=='' || name.length()>100) {
                exit 400,'账号或显示名称不正确';
            }
            if(old!=null && username!=old.username) {
                exit 400,'登录账号创建后保持稳定';
            }
            if(old!=null && r.subjectId!=null && r.subjectId!=old.subject_id) {
                exit 400,'关联人员创建后保持稳定';
            }
            if(old==null) {
                var subjectId=r.subjectId||null;
                var storedName=name;
                if(subjectId!=null) {
                    var person=sql.selectOne("""
select tid as id,name
from iam_subject_t
where tid=#{subjectId} and identity_domain='workforce' and status='ACTIVE' and is_del=0
""");
                    if(person==null || person.name!=name) {
                        exit 400,'关联人员不存在或姓名已变更';
                    }
                    var scope=directoryScope('users:read','workforce');
                    var orgs=sql.select("""
select distinct org_id
from iam_appointment_t
where subject_id=#{subjectId} and is_del=0 and status='ACTIVE'
  and valid_from<=utc_timestamp(6)
  and (valid_until is null or valid_until>utc_timestamp(6))
""");
                    if(!scope.all && (orgs.size()==0 || orgs.filter(o=>!scope.orgIds.contains(o.org_id)).size()>0)) {
                        exit 403,'关联人员超出中央资料可见范围';
                    }
                    storedName=null;
                }
                var mustChange=passwordPolicy(r.password).forceChange==false?0:1;
                id=IdUtil.fastSimpleUUID();
                var securityId=IdUtil.fastSimpleUUID();
                var credentialId=IdUtil.fastSimpleUUID();
                var encoded=iamSecurity.hash(r.password);
                if(sql.selectOne("""
select tid as id
from iam_operator_t
where active_username=#{username}
""")!=null) {
                    exit 409,'平台账号已存在';
                }
                sql.update("""
insert into iam_operator_t(tid,
                username, display_name, subject_id, status, must_change_password, created_by, updated_by)
values(#{id},#{username},#{storedName},#{subjectId},#{status},#{mustChange},#{ctx.userId},#{ctx.userId})
""");
                sql.update("""
insert into iam_account_security_t(tid,
                principal_kind, principal_id, password_changed_time)
values(#{securityId},'OPERATOR',#{id},utc_timestamp(6))
""");
                sql.update("""
insert into iam_credential_t(tid,
                owner_kind, owner_id, credential_type, algorithm, secret_hash, status)
values(#{credentialId},'OPERATOR',#{id},'PASSWORD','PBKDF2_SHA256',#{encoded},'ACTIVE')
""");
            }
            else {
                operatorName(id,old.subject_id,name,r.subjectVersion,ctx.userId,false);
                sql.update("""
update iam_operator_t
set status=#{status},version=version+1,
        updated_time=utc_timestamp(6),
        updated_by=#{ctx.userId}
where tid=#{id}
""");
                if(old.status!=status) {
                    sql.update("""
update iam_account_security_t
set security_version=security_version+1
where principal_kind='OPERATOR'
    and principal_id=#{id}
""");
                }
            }
        }
        else {
            exit 400,'操作不正确';
        }
        // 记录操作主体、目标及结果，供审计与故障追踪。
        audit(ctx,'SUCCESS','operators:'+operation,id,null,{
            id:id
        });
        return {
            id:id
        };
    });

}));
