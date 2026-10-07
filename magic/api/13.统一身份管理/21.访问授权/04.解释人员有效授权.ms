{
  "properties": {},
  "id": "793fcd8252c55db593deb2b44977b569",
  "script": null,
  "groupId": "d7e5646b56d156198bbf3826dfe4771c",
  "name": "04.解释人员有效授权",
  "createTime": 1790617158595,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/explain",
  "description": "根据人员、应用和身份域计算当前有效角色与资源，并返回各项授权来源及有效期，便于审查访问原因。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：04.解释人员有效授权
// 路由：GET /idaas/grants/explain
// 业务说明：根据人员、应用和身份域计算当前有效角色与资源，并返回各项授权来源及有效期，便于审查访问原因。
import db;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import '@/idaas/guard' as guard;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import '@/idaas/time-window' as window;
import '@/idaas/effective-access' as effective;
import '@/idaas/rebuild-projections' as rebuild;
import '@/idaas/app-list-context' as listContext;
import identityRuntime;
import request;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    var payload={};
    for(key in ['domain','appId','subjectId','page','size','q']) {
        var input=request.getValues(key);
        if(input!=null && input.size()>0) {
            payload.put(key,input[0]);
        }
    }
    // 请求参数与本接口业务处理。
    var operation = 'explain';

    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
    var r=p.record||p;
    var domain=r.domain||p.domain||'workforce';
    var id=r.id||p.id;
    var old=id==null?null:sql.selectOne("""
select *,tid as id
from iam_access_grant_t
where tid=#{id}
    and is_del=0
""");
    var appId=old==null?r.appId||p.appId:old.app_id;
    var permission='grants:'+(operation=='list' || operation=='explain'?'read':'write');
    // 核验会话的功能权限及对象范围，超出范围的请求在读取或写入前拒绝。
    var ctx=operation=='list'?listContext(permission,appId,domain):guard(permission,appId,domain);
    if(operation=='explain') {
        var person=sql.selectOne("""
select tid as id
from iam_subject_t
where tid=#{p.subjectId}
    and identity_domain=#{domain}
    and is_del=0
""");
        if(person==null) {
            exit 404,'人员不存在';
        }
        return effective(p.subjectId,appId,domain);
    }
    // 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
    return mutate(ctx,'grants:'+operation,p,()=>{
        old=id==null?null:sql.selectOne("""
select *,tid as id
from iam_access_grant_t
where tid=#{id}
    and is_del=0
    for update
""");
        if(old==null && p.creating!=true) {
            exit 404,'授权不存在';
        }
        // 以当前版本校验修改目标，阻止旧页面覆盖已经变更的记录。
        if(old!=null && (old.version!=(r.version||p.version) || r.appId!=null && r.appId!=old.app_id || old.identity_domain!=domain)) {
            exit 409,'授权应用、域或版本不一致';
        }
        var app=sql.selectOne("""
select *,tid as id
from iam_application_t
where tid=#{appId}
    and kind='BUSINESS'
    and is_del=0
""");
        if(app==null || !JSONUtil.parseArray(app.identity_domains).contains(domain)) {
            exit 400,'仅允许此身份域的业务应用授权';
        }
        if(operation=='remove' || r.status=='revoked') {
            if(old==null) {
                exit 404,'授权不存在';
            }
            sql.update("""
update iam_access_grant_t
set status='REVOKED',version=version+1,updated_time=utc_timestamp(6)
where tid=#{id}
""");
            rebuild(appId,domain);
            // 记录操作主体、目标及结果，供审计与故障追踪。
            audit(ctx,'SUCCESS','grants:revoke',id,null,{
                id:id
            });
            return {
                id:id
            };
        }
        var types={
            user:'SUBJECT',org:'ORG',group:'GROUP',client:'CLIENT'
        };
        var type=types.get(r.source);
        var principal=r.subjectId;
        if(type==null || principal==null || old!=null && (old.principal_type!=type || (old.subject_id||old.org_id||old.group_id||old.client_id)!=principal)) {
            exit 400,'授权主体不能变更';
        }
        var table=type=='SUBJECT'?'iam_subject_t':type=='ORG'?'iam_org_t':type=='GROUP'?'iam_permission_group_t':'iam_auth_client_t';
        var target=sql.selectOne('select *,tid as id from '+table+' where tid=#{principal} and is_del=0');
        if(target==null || type!='CLIENT' && (target.identity_domain!=domain || target.status!='ACTIVE') || type=='CLIENT' && (target.client_type!='SERVICE' || target.status=='DISABLED')) {
            exit 400,'主体须为启用的同域人员、机构、权限组或机器客户端';
        }
        var roles=r.roleIds||[];
        if(roles.size()>100 || roles.distinct().size()!=roles.size()) {
            exit 400,'角色引用重复或过多';
        }
        for(rid in roles) {
            if(sql.selectOne("""
select tid as id
from iam_role_t
where tid=#{rid}
    and app_id=#{appId}
    and identity_domain=#{domain}
    and status='ACTIVE'
    and is_del=0
""")==null) {
                exit 400,'角色必须属于同业务应用和身份域';
            }
            if(type=='CLIENT' && sql.selectOne("""
select count(*) n
from iam_role_resource_rela_t rr
join iam_resource_t re on re.tid=rr.resource_id
where rr.role_id=#{rid}
    and re.resource_type!='API'
    and re.is_del=0
""").n>0) {
                exit 400,'机器身份只能授予API资源角色';
            }
        }
        var times=window(r.startsAt,r.expiresAt);
        var reason=r.reason||'';
        if(reason.length()>1000) {
            exit 400,'授权说明过长';
        }
        var include=type=='ORG' && r.includeChildren==true?1:0;
        var sid=type=='SUBJECT'?principal:null;
        var oid=type=='ORG'?principal:null;
        var gid=type=='GROUP'?principal:null;
        var cid=type=='CLIENT'?principal:null;
        if(old==null) {
            id=IdUtil.fastSimpleUUID();
            sql.update("""
insert into iam_access_grant_t(tid,
                app_id, identity_domain, principal_type, subject_id, org_id, group_id, client_id,
                include_children, valid_from, valid_until, reason, created_by)
values(#{id},#{appId},#{domain},#{type},#{sid},#{oid},#{gid},#{cid},#{include},#{times.start},#{times.end},#{reason},#{ctx.userId})
""");
        }
        else {
            sql.update("""
update iam_access_grant_t
set include_children=#{include},valid_from=#{times.start},valid_until=#{times.end},reason=#{reason},
    status='ACTIVE',version=version+1,updated_time=utc_timestamp(6)
where tid=#{id}
""");
        }
        sql.update("""
delete
from iam_grant_role_rela_t
where grant_id=#{id}
""");
        for(rid in roles) {
            var link=IdUtil.fastSimpleUUID();
            sql.update("""
insert into iam_grant_role_rela_t(tid, grant_id, role_id, app_id)
values(#{link},#{id},#{rid},#{appId})
""");
        }
        rebuild(appId,domain);
        audit(ctx,'SUCCESS','grants:save',id,null,{
            id:id,appId:appId,type:type
        });
        return {
            id:id
        };
    });

}));
