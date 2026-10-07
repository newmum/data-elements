{
  "properties": {},
  "id": "868fca7d381256fd95e5d01079062df0",
  "script": null,
  "groupId": "e325afaf57bd5e38b68f38837525b3b0",
  "name": "01.查询统一认证账号列表",
  "createTime": 1790635007936,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "description": "读取符合请求筛选条件的统一认证账号，应用权限及身份域过滤后返回可展示字段；不发生写入。统一认证账号与平台操作账号、租户本地账号是三套隔离的身份体系。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：01.查询统一认证账号列表
// 路由：GET /idaas/auth-accounts/list
// 业务说明：读取符合请求筛选条件的统一认证账号，应用权限及身份域过滤后返回可展示字段；不发生写入。统一认证账号与平台操作账号、租户本地账号是三套隔离的身份体系。
import db;
import authSession;
import iamSecurity;
import cn.hutool.core.util.IdUtil;
import '@/idaas/guard' as guard;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import '@/idaas/password-policy' as passwordPolicy;
import identityRuntime;
import request;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    var payload={};
    for(key in ['id','appId','domain']) {
        var value=request.getValues(key);
        if(value!=null && value.size()>0) {
            payload.put(key,value[0]);
        }
    }
    // 请求参数与本接口业务处理。
    var operation = 'list';

    // 统一认证账号不属于平台操作账号，也不写应用本地用户或本地密码。
    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
    var r=p.record||p;
    var domain=r.domain||p.domain||'workforce';
    // 先校验当前会话的功能权限、身份域和应用管理范围。
    var ctx=guard(operation=='list'?'auth-accounts:read':'auth-accounts:write',null,domain);
    var id=r.id||p.id;
    if(operation=='list') {
        return sql.select("""
select a.tid as id,a.subject_id subjectId,a.identity_domain domain,a.username,a.status,
        a.must_change_password mustChangePassword,a.legal_entity_id legalEntityId,
        a.version,s.name
from iam_auth_account_t a
join iam_subject_t s on s.tid=a.subject_id
where a.identity_domain=#{domain}
    and a.is_del=0
order by a.created_time desc
limit 250
""");
    }
    var output=mutate(ctx,'auth-accounts:'+operation,p,()=>{
        var old=id==null?null:sql.selectOne("""
select *,tid as id
from iam_auth_account_t
where tid=#{id}
    and is_del=0
    for update
""");
        // 以当前版本校验修改目标，阻止旧页面覆盖已经变更的记录。
        if(old!=null && (old.identity_domain!=domain || old.version!=(r.version||p.version))) {
            exit 409,'账号范围或版本已变更';
        }
        if(operation=='bind-entity') {
            if(old==null || domain!='public') {
                exit 400,'只允许绑定公众认证账号';
            }
            var entityId=p.entityId;
            if(entityId!=null && entityId!='' && sql.selectOne("""
select m.tid as id
from iam_legal_member_t m
join iam_legal_entity_t e on e.tid=m.entity_id
and e.status='ACTIVE'
and e.is_del=0
and e.verification_status='VERIFIED'
where m.entity_id=#{entityId}
    and m.subject_id=#{old.subject_id}
    and m.status='ACTIVE'
    and m.is_del=0
    and m.valid_from<=utc_timestamp(6)
    and (m.valid_until is null
         or m.valid_until>utc_timestamp(6))
    and exists
        (select 1
         from iam_verification_t v
         where v.target_type='LEGAL_ENTITY'
             and v.target_id=e.tid
             and v.status='VERIFIED'
             and v.expires_at>utc_timestamp(6)
             and v.tid=
                 (select x.tid as id
                  from iam_verification_t x
                  where x.target_type='LEGAL_ENTITY'
                      and x.target_id=e.tid
                  order by x.requested_time desc, x.tid desc
                  limit 1))
limit 1
""")==null) {
                exit 403,'法人核验或经办关系尚未生效';
            }
            entityId=entityId==''?null:entityId;
            sql.update("""
update iam_auth_account_t
set legal_entity_id=#{entityId},version=version+1
where tid=#{id}
""");
        }
        else if(operation=='remove') {
            if(old==null) {
                exit 404,'账号不存在';
            }
            sql.update("""
update iam_auth_account_t
set status='DISABLED',is_del=1,version=version+1
where tid=#{id}
""");
        }
        else if(operation=='reset') {
            if(old==null) {
                exit 404,'账号不存在';
            }
            passwordPolicy(p.password);
            sql.update("""
update iam_auth_account_t
set must_change_password=1,version=version+1
where tid=#{id}
""");
        }
        else if(operation=='save') {
            var subject=sql.selectOne("""
select tid as id
from iam_subject_t
where tid=#{r.subjectId}
    and identity_domain=#{domain}
    and is_del=0
""");
            if(subject==null) {
                exit 400,'必须关联同域人员';
            }
            var username=(r.username||'').trim().toLowerCase();
            if(!username.matches('[A-Za-z][A-Za-z0-9_.-]{2,99}') || old!=null && (old.subject_id!=r.subjectId || old.username!=username)) {
                exit 400,'认证账号、主体和身份域保持稳定';
            }
            var status=r.status=='DISABLED'?'DISABLED':'ACTIVE';
            if(old==null) {
                if(p.creating!=true) {
                    exit 404,'认证账号不存在';
                }
                passwordPolicy(r.password);
                if(sql.selectOne("""
select tid as id
from iam_auth_account_t
where identity_domain=#{domain}
    and active_username=#{username}
""")!=null) {
                    exit 409,'认证账号已使用';
                }
                id=IdUtil.fastSimpleUUID();
                sql.update("""
insert into iam_auth_account_t(tid,
                subject_id, identity_domain, username, status, must_change_password)
values(#{id},#{r.subjectId},#{domain},#{username},#{status},1)
""");
                var security=IdUtil.fastSimpleUUID();
                sql.update("""
insert into iam_account_security_t(tid, principal_kind, principal_id)
values(#{security},'AUTH_ACCOUNT',#{id})
""");
            }
            else {
                if(r.legalEntityId!=old.legal_entity_id && r.legalEntityId!=null) {
                    exit 400,'法人认证上下文须通过有效经办关系显式绑定';
                }
                sql.update("""
update iam_auth_account_t
set status=#{status},version=version+1,updated_time=utc_timestamp(6)
where tid=#{id}
""");
            }
        }
        else {
            exit 400,'账号操作不正确';
        }
        if(old==null || operation=='reset') {
            var password=operation=='reset'?p.password:r.password;
            var encoded=iamSecurity.hash(password);
            var prior=sql.selectOne("""
select revision
from iam_credential_t
where owner_kind='AUTH_ACCOUNT'
    and owner_id=#{id}
    and credential_type='PASSWORD'
    and status='ACTIVE'
""");
            var revision=prior==null?1:prior.revision+1;
            sql.update("""
update iam_credential_t
set status='RETIRED'
where owner_kind='AUTH_ACCOUNT'
    and owner_id=#{id}
    and credential_type='PASSWORD'
    and status='ACTIVE'
""");
            var cid=IdUtil.fastSimpleUUID();
            sql.update("""
insert into iam_credential_t(tid,
                owner_kind, owner_id, credential_type, revision, algorithm, secret_hash, status)
values(#{cid},'AUTH_ACCOUNT',#{id},'PASSWORD',#{revision},'PBKDF2_SHA256',#{encoded},'ACTIVE')
""");
        }
        if(old!=null) {
            sql.update("""
update iam_account_security_t
set security_version=security_version+1,failed_count=0,locked_until=null
where principal_kind='AUTH_ACCOUNT'
    and principal_id=#{id}
""");
            sql.update("""
update iam_auth_session_t
set revoked_at=utc_timestamp(6),revoke_reason='ACCOUNT_ADMIN_CHANGED'
where principal_kind='AUTH_ACCOUNT'
    and principal_id=#{id}
    and revoked_at is null
""");
        }
        // 记录操作主体、目标及结果，供审计与故障追踪。
        audit(ctx,'SUCCESS','auth-accounts:'+operation,id,null,{
            id:id
        });
        return {
            id:id
        };
    });
    if(output.id!=null) {
        authSession.revoke(output.id);
    }
    return output;

}));
