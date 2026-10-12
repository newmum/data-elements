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
import tableSort;
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
    var scopes=[];
    for(pair in ctx.pairs) {if(pair.codes.contains(permission)) {scopes.addAll(pair.scopes.filter(scope=>scope.identity_domain==domain));}}
    var global=scopes.filter(scope=>scope.scope_kind=='ALL').size()>0;
    var allowedApps=scopes.filter(scope=>scope.scope_kind=='APPLICATION').map(scope=>scope.app_id).distinct();
    var paged=p.paged==true || p.paged=='true';
    var page=Math.max(1,(p.page||1)::int);
    var size=Math.min(100,Math.max(1,(p.size||20)::int));
    if(!global && allowedApps.size()==0) {return paged?{list:[],total:0,page:page,size:size}:[];}
    var scopeSql=global?'':' and g.app_id in (#{allowedApps})';
    var subjectId=p.subjectId;
    var principal=p.principalType;
    if(principal!=null && !['SUBJECT','ORG','GROUP','CLIENT'].contains(principal)) {exit 400,'授权对象类型不正确';}
    var search=(p.q||'').trim();
    if(search.length()>200) {exit 400,'搜索内容过长';}
    var status=p.status||'all';
    var objectName="coalesce(s.name,o.name,pg.name,c.name,'对象已移除')";
    var state="case when g.status!='ACTIVE' then 'revoked' when g.valid_until<=utc_timestamp(6) then 'expired' else 'enabled' end";
    var from=" from iam_access_grant_t g join iam_application_t a on a.tid=g.app_id and a.kind='BUSINESS' and a.is_del=0 left join iam_subject_t s on s.tid=g.subject_id and s.identity_domain=g.identity_domain and s.is_del=0 left join iam_org_t o on o.tid=g.org_id and o.identity_domain=g.identity_domain and o.is_del=0 left join iam_permission_group_t pg on pg.tid=g.group_id and pg.identity_domain=g.identity_domain and pg.is_del=0 left join iam_auth_client_t c on c.tid=g.client_id and c.app_id=g.app_id and c.is_del=0 where g.is_del=0 and g.identity_domain=#{domain} and (#{appId} is null or g.app_id=#{appId}) and (#{principal} is null or g.principal_type=#{principal}) and (#{subjectId} is null or coalesce(g.subject_id,g.org_id,g.group_id,g.client_id)=#{subjectId})"+scopeSql+" and (#{status}='all' or ("+state+")=#{status}) and (#{search}='' or locate(lower(#{search}),lower(concat_ws(' ',"+objectName+",a.name,g.reason)))>0)";
    var field=tableSort.field(p,{subjectName:objectName,name:objectName,appName:'a.name',startsAt:'g.valid_from',expiresAt:'g.valid_until',createdAt:'g.created_time',updatedAt:'g.updated_time'},'createdAt');
    var total=sql.selectOne('select count(*) total'+from).total;
    if(!paged && total>1000) {exit 400,'授权超过 1000 项，请使用分页查询';}
    var take=paged?size:1000;
    var offset=paged?(page-1)*size:0;
    var rows=sql.select("select g.*,g.tid as id,"+objectName+" subject_name,a.name app_name,"+state+" view_status,date_format(g.valid_from,'%Y-%m-%dT%H:%i:%sZ') start_utc,date_format(g.valid_until,'%Y-%m-%dT%H:%i:%sZ') end_utc,date_format(g.created_time,'%Y-%m-%dT%H:%i:%sZ') created_utc,date_format(g.updated_time,'%Y-%m-%dT%H:%i:%sZ') updated_utc"+from+' order by case when '+field+' is null then 1 else 0 end,'+field+' '+tableSort.direction(p)+',g.tid asc limit #{take} offset #{offset}');
    var ids=rows.map(x=>x.id);
    var roles=ids.size()==0?[]:sql.select("select rr.grant_id,rr.role_id,r.name from iam_grant_role_rela_t rr join iam_access_grant_t g on g.tid=rr.grant_id and g.app_id=rr.app_id and g.identity_domain=#{domain} and g.is_del=0 left join iam_role_t r on r.tid=rr.role_id and r.app_id=g.app_id and r.identity_domain=g.identity_domain and r.is_del=0 where rr.grant_id in (#{ids}) order by rr.grant_id,rr.role_id limit 100001");
    if(roles.size()>100000) {exit 400,'角色关联过多，请缩小分页范围';}
    var result=rows.map(x=>{
        var related=roles.filter(r=>r.grant_id==x.id);
        return {id:x.id,domain:x.identity_domain,appId:x.app_id,appName:x.app_name,subjectName:x.subject_name,name:x.subject_name,source:x.principal_type=='SUBJECT'?'user':x.principal_type.toLowerCase(),subjectId:x.subject_id||x.org_id||x.group_id||x.client_id,roleIds:related.map(r=>r.role_id),roleNames:related.map(r=>({id:r.role_id,name:r.name||'失效角色'})),includeChildren:x.include_children==1,status:paged?x.view_status:x.status=='ACTIVE'?'enabled':'revoked',startsAt:x.start_utc,expiresAt:x.end_utc,reason:x.reason||'',version:x.version,createdAt:x.created_utc,updatedAt:x.updated_utc};
    });
    return paged?{list:result,total:total,page:page,size:size}:result;
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
