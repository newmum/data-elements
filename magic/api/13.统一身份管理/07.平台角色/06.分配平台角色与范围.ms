{
  "properties": {},
  "id": "0ebea9ab19a951019a530accee61f353",
  "script": null,
  "groupId": "1e0dee785fc65df8a070fb6a38c924ad",
  "name": "06.分配平台角色与范围",
  "createTime": 1790612165860,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/assign",
  "description": "逐项核验被授予的功能及对象范围不超过当前操作人权限，更新平台角色分配并记录审计。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：06.分配平台角色与范围
// 路由：POST /idaas/platform-roles/assign
// 业务说明：逐项核验被授予的功能及对象范围不超过当前操作人权限，更新平台角色分配并记录审计。
import db;
import '@/idaas/guard' as guard;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import '@/idaas/directory-scope' as directoryScope;
import cn.hutool.core.util.IdUtil;
import java.time.Instant;
import identityRuntime;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    // 请求参数与本接口业务处理。
    var operation = 'assign';
    var payload = body||{};

    // 平台内部角色与显式管理范围；所有转授逐项验证功能及范围上限。
    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
    var r=p.record||p;
    var internal='idaas-platform-internal';
    // 先校验当前会话的功能权限、身份域和应用管理范围。
    var ctx=guard(operation=='list' || operation=='resources' || operation=='assignments'?'platform-roles:read':operation=='assign'?'operators:grant':'platform-roles:write',null,'workforce');
    // 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
    return mutate(ctx,'platform-roles:'+operation,p,()=>{
        var id=r.id;
        var name=(r.name||'').trim();
        var code=(r.code||'').trim();
        if(operation=='assign') {
            var operatorId=p.operatorId;
            var target=sql.selectOne("""
select *,tid as id
from iam_operator_t
where tid=#{operatorId}
    and is_del=0
    and status='ACTIVE'
    for update
""");
            if(target==null) {
                exit 400,'目标平台账号不存在或已停用';
            }
            if(target.version!=p.version) {
                exit 409,'平台账号已被修改，请刷新';
            }
            var assignments=p.assignments||[];
            if(assignments.size()>30) {
                exit 400,'角色分配数量过多';
            }
            var verified=[];
            var roleIds=[];
            for(a in assignments) {
                if(roleIds.contains(a.roleId)) {
                    exit 400,'同一角色不能重复分配';
                }
                roleIds.add(a.roleId);
                var role=sql.selectOne("""
select tid as id
from iam_role_t
where tid=#{a.roleId}
    and app_id=#{internal}
    and is_del=0
    and status='ACTIVE'
""");
                if(role==null) {
                    exit 400,'所选角色不是有效的平台角色';
                }
                var codes=sql.select("""
select r.code
from iam_role_resource_rela_t rr
join iam_resource_t r on r.tid=rr.resource_id
and r.is_del=0
and r.status='ACTIVE'
where rr.role_id=#{a.roleId}
""").map(x=>x.code);
                var scopes=a.scopes||[];
                if(scopes.size()==0 || scopes.size()>30) {
                    exit 400,'请提供明确管理范围';
                }
                for(s in scopes) {
                    if(!['workforce','public'].contains(s.identityDomain) || !['ALL','APPLICATION','ORG'].contains(s.scopeKind)) {
                        exit 400,'身份域或范围类型不正确';
                    }
                    if(s.scopeKind=='ALL' && s.appId!=null) {
                        exit 400,'全局范围不能同时指定应用';
                    }
                    if(s.scopeKind=='APPLICATION' && sql.selectOne("""
select tid as id
from iam_application_t
where tid=#{s.appId}
    and kind='BUSINESS'
    and is_del=0
""")==null) {
                        exit 400,'范围应用不存在';
                    }
                    if(s.scopeKind!='ORG' && s.orgId!=null) {
                        exit 400,'非机构范围不能指定机构';
                    }
                    if(s.scopeKind=='ORG' && (s.appId!=null || sql.selectOne("""
select tid as id
from iam_org_t
where tid=#{s.orgId}
    and identity_domain=#{s.identityDomain}
    and status='ACTIVE'
    and is_del=0
""")==null)) {
                        exit 400,'机构范围须为同域有效机构，不能借此修改应用配置';
                    }
                    for(permission in codes) {
                        if(s.scopeKind=='ORG') {
                            var boundary=directoryScope(permission,s.identityDomain);
                            if(!boundary.all && !boundary.orgIds.contains(s.orgId)) {
                                exit 403,'机构范围超出可转授权上限';
                            }
                            if(s.includeChildren==true && !boundary.all) {
                                var root=sql.selectOne("""
select path
from iam_org_t
where tid=#{s.orgId}
""");
                                var nodes=sql.select("""
select tid as id
from iam_org_t
where path like concat(#{root.path}, '%')
    and is_del=0
""");
                                if(nodes.filter(n=>!boundary.orgIds.contains(n.id)).size()>0) {
                                    exit 403,'下级范围超出可转授权上限';
                                }
                            }
                        }
                        else {
                            guard(permission,s.scopeKind=='ALL'?null:s.appId,s.identityDomain);
                        }
                    }
                }
                // API dates are UTC ISO-8601. Bind canonical strings to avoid JDBC/session timezone conversion.
                var start=null;
                var end=null;
                try {
                    if(a.validFrom!=null && a.validFrom!='') {
                        start=Instant.parse(a.validFrom).toString().substring(0,19).replace('T',' ');
                    }
                    if(a.validUntil!=null && a.validUntil!='') {
                        end=Instant.parse(a.validUntil).toString().substring(0,19).replace('T',' ');
                    }
                }
                catch(e) {
                    exit 400,'授权时间须为有效UTC时间';
                }
                if(end!=null && sql.selectOne("""
select case
           when #{end}<=coalesce(#{start},
        utc_timestamp(6))
                or #{end}<=utc_timestamp(6) then 1 else 0
       end as invalid
""").invalid==1) {
                    exit 400,'到期时间须晚于生效时间及当前时间';
                }
                verified.add({
                    roleId:a.roleId,scopes:a.scopes,validFrom:start,validUntil:end
                });
            }
            // 校验完全部对象后才替换，不允许部分成功。
            var old=sql.select("""
select tid as id
from iam_operator_role_rela_t
where operator_id=#{operatorId}
""");
            for(a in old) {
                sql.update("""
delete
from iam_operator_scope_t
where assignment_id=#{a.id}
""");
            }
            sql.update("""
update iam_operator_role_rela_t
set status='REVOKED',version=version+1,updated_time=utc_timestamp(6)
where operator_id=#{operatorId}
""");
            for(a in verified) {
                var present=sql.selectOne("""
select tid as id
from iam_operator_role_rela_t
where operator_id=#{operatorId}
    and role_id=#{a.roleId}
""");
                var assignmentId=present==null?IdUtil.fastSimpleUUID():present.id;
                if(present==null) {
                    sql.update("""
insert into iam_operator_role_rela_t(tid,
                operator_id, role_id, status, valid_from, valid_until)
values(#{assignmentId},#{operatorId},#{a.roleId},'ACTIVE',coalesce(#{a.validFrom},utc_timestamp(6)),#{a.validUntil})
""");
                }
                else {
                    sql.update("""
update iam_operator_role_rela_t
set status='ACTIVE',valid_from=coalesce(#{a.validFrom}, utc_timestamp(6)),valid_until=#{a.validUntil},
    version=version+1,updated_time=utc_timestamp(6)
where tid=#{assignmentId}
""");
                }
                for(s in a.scopes) {
                    var scopeId=IdUtil.fastSimpleUUID();
                    var appId=s.scopeKind=='APPLICATION'?s.appId:null;
                    var orgId=s.scopeKind=='ORG'?s.orgId:null;
                    var children=s.includeChildren==true?1:0;
                    sql.update("""
insert into iam_operator_scope_t(tid,
                assignment_id, identity_domain, scope_kind, app_id, org_id, include_children)
values(#{scopeId},#{assignmentId},#{s.identityDomain},#{s.scopeKind},#{appId},#{orgId},#{children})
""");
                }
            }
            sql.update("""
update iam_operator_t
set version=version+1,updated_time=utc_timestamp(6)
where tid=#{operatorId}
""");
            id=operatorId;
        }
        else {
            var old=sql.selectOne("""
select *,tid as id
from iam_role_t
where tid=#{id}
    and app_id=#{internal}
    and is_del=0
    for update
""");
            if(old==null && p.creating!=true) {
                exit 404,'平台角色不存在';
            }
            // 以当前版本校验修改目标，阻止旧页面覆盖已经变更的记录。
            if(old!=null && old.version!=r.version) {
                exit 409,'角色已被修改，请刷新';
            }
            var references=old==null?[]:sql.select("""
select a.tid as id
from iam_operator_role_rela_t a
where role_id=#{id}
    and status='ACTIVE'
    and (valid_until is null
         or valid_until>utc_timestamp(6))
""");
            if(operation=='remove') {
                if(references.size()>0) {
                    exit 409,'角色仍被账号使用，请先处理分配';
                }
                sql.update("""
update iam_role_t
set is_del=1,status='DISABLED',version=version+1,updated_time=utc_timestamp(6)
where tid=#{id}
""");
            }
            else {
                if(name=='' || name.length()>100 || !code.matches('[A-Za-z][A-Za-z0-9_-]{1,99}')) {
                    exit 400,'平台角色名称或编码不正确';
                }
                if(old!=null && code!=old.code) {
                    exit 400,'角色编码保持稳定';
                }
                var resourceIds=r.resourceIds||[];
                var verified=[];
                for(resourceId in resourceIds) {
                    var resource=sql.selectOne("""
select code
from iam_resource_t
where tid=#{resourceId}
    and app_id=#{internal}
    and is_del=0
    and status='ACTIVE'
""");
                    if(resource==null || !ctx.permissions.contains(resource.code)) {
                        exit 403,'不能授予超出自身权限的资源';
                    }
                    for(a in references) {
                        for(s in sql.select("""
select *,tid as id
from iam_operator_scope_t
where assignment_id=#{a.id}
""")) {
                            guard(resource.code,s.scope_kind=='ALL'?null:s.app_id,s.identity_domain);
                        }
                    }
                    if(!verified.contains(resourceId)) {
                        verified.add(resourceId);
                    }
                }
                var status=r.status=='DISABLED' || r.status=='disabled'?'DISABLED':'ACTIVE';
                var description=r.description||'';
                if(description.length()>1000) {
                    exit 400,'说明过长';
                }
                if(old==null) {
                    id=IdUtil.fastSimpleUUID();
                    if(sql.selectOne("""
select tid as id
from iam_role_t
where app_id=#{internal}
    and code=#{code}
""")!=null) {
                        exit 409,'角色编码已使用';
                    }
                    sql.update("""
insert into iam_role_t(tid, app_id, code, name, identity_domain, status, description)
values(#{id},#{internal},#{code},#{name},'workforce',#{status},#{description})
""");
                }
                else {
                    sql.update("""
update iam_role_t
set name=#{name},status=#{status},description=#{description},version=version+1,
        updated_time=utc_timestamp(6)
where tid=#{id}
""");
                }
                sql.update("""
delete
from iam_role_resource_rela_t
where role_id=#{id}
""");
                for(resourceId in verified) {
                    var linkId=IdUtil.fastSimpleUUID();
                    sql.update("""
insert into iam_role_resource_rela_t(tid,
                role_id, resource_id, app_id)
values(#{linkId},#{id},#{resourceId},#{internal})
""");
                }
            }
        }
        var managers=sql.selectOne("""
select count(distinct o.tid) as total
from iam_operator_t o
join iam_operator_role_rela_t a on a.operator_id=o.tid
and a.status='ACTIVE'
and a.valid_from<=utc_timestamp(6)
and (a.valid_until is null
     or a.valid_until>utc_timestamp(6))
join iam_role_t r on r.tid=a.role_id
and r.status='ACTIVE'
and r.is_del=0
join iam_role_resource_rela_t rr on rr.role_id=r.tid
join iam_resource_t res on res.tid=rr.resource_id
join iam_operator_scope_t s on s.assignment_id=a.tid
and s.scope_kind='ALL'
and s.identity_domain='workforce'
where o.status='ACTIVE'
    and o.is_del=0
    and res.code='operators:grant'
    and res.status='ACTIVE'
    and res.is_del=0
""");
        if(managers.total==0) {
            exit 409,'必须保留一个有效的全局权限管理入口';
        }
        // 记录操作主体、目标及结果，供审计与故障追踪。
        audit(ctx,'SUCCESS','platform-roles:'+operation,id,null,{
            id:id
        });
        return {
            id:id
        };
    });

}));
