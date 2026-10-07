{
  "properties": {},
  "id": "9e888c8030275ef3aac1db63d8417f3f",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "18.业务访问授权共用规则",
  "createTime": 1790617158595,
  "updateTime": 1790949884838,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/business-grants",
  "description": "核验业务应用授权对象、角色、有效期及版本，更新授权与角色关联并记录审计。",
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
// 共享函数：18.业务访问授权共用规则
// 业务说明：核验业务应用授权对象、角色、有效期及版本，更新授权与角色关联并记录审计。
// 函数输入：operation：本次执行的业务操作；payload：请求资料或筛选条件。
// 返回与副作用：核验业务应用授权对象、角色、有效期及版本，更新授权与角色关联并记录审计。
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
var ctx=operation=='list'?listContext(permission,appId,domain):guard(permission,appId,domain);
// 授权解释分支：计算指定人员的有效访问来源并返回可阅读的依据。
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
// 列表分支：先确定当前操作人可见的应用和身份域，再读取并映射业务记录。
if(operation=='list') {
    var rows=sql.select("""
select *,tid as id,date_format(valid_from, '%Y-%m-%dT%H:%i:%sZ') start_utc,
       date_format(valid_until, '%Y-%m-%dT%H:%i:%sZ') end_utc,date_format(created_time,
        '%Y-%m-%dT%H:%i:%sZ') created_utc,
       date_format(updated_time, '%Y-%m-%dT%H:%i:%sZ') updated_utc
from iam_access_grant_t
where identity_domain=#{domain}
    and is_del=0
order by tid
""");
    return rows.filter(x=>(appId==null || x.app_id==appId) && ctx.pairs.filter(pair=>pair.codes.contains(permission) && pair.scopes.filter(s=>s.identity_domain==domain && (s.scope_kind=='ALL' || s.scope_kind=='APPLICATION' && s.app_id==x.app_id)).size()>0).size()>0).map(x=>({
        id:x.id,domain:x.identity_domain,appId:x.app_id,name:'应用访问授权',source:x.principal_type=='SUBJECT'?'user':x.principal_type.toLowerCase(),subjectId:x.subject_id||x.org_id||x.group_id||x.client_id,roleIds:sql.select("""
select role_id
from iam_grant_role_rela_t
where grant_id=#{x.id}
""").map(t=>t.role_id),includeChildren:x.include_children==1,status:x.status=='ACTIVE'?'enabled':'revoked',startsAt:x.start_utc,expiresAt:x.end_utc,reason:x.reason||'',version:x.version,createdAt:x.created_utc,updatedAt:x.updated_utc
    }));
}
// 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
// 事务边界：通过共享幂等写入统一处理版本冲突、提交与审计。
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
    // 归档分支：验证版本及引用关系，保留历史记录并撤销有效关联。
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
