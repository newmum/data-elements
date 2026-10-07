{
  "properties": {},
  "id": "b3c53db488755c1e93d8f67afb2c4b97",
  "script": null,
  "groupId": "b34c35bb56695d918186c7c7928a69cf",
  "name": "01.查询应用业务角色列表",
  "createTime": 1790617158595,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "description": "读取符合请求筛选条件的应用业务角色，应用权限及身份域过滤后返回可展示字段；不发生写入。只管理业务应用角色及其资源关联；平台控制台角色由平台角色接口独立维护。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：01.查询应用业务角色列表
// 路由：GET /idaas/roles/list
// 业务说明：读取符合请求筛选条件的应用业务角色，应用权限及身份域过滤后返回可展示字段；不发生写入。只管理业务应用角色及其资源关联；平台控制台角色由平台角色接口独立维护。
import db;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import '@/idaas/guard' as guard;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import '@/idaas/field-values' as fieldValues;
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
    var entity = 'roles';
    var operation = 'list';

    // 应用业务角色与菜单/API资源。平台内部权限只能由platform-roles维护。
    var sql=db.normal();
    var p=payload||{};
    var r=p.record||p;
    var domain=r.domain||p.domain||'workforce';
    if(!['roles','resources'].contains(entity)) {
        exit 400,'对象类型不正确';
    }
    var table=entity=='roles'?'iam_role_t':'iam_resource_t';
    var id=r.id||p.id;
    var old=id==null?null:sql.selectOne('select *,tid as id from '+table+' where tid=#{id} and is_del=0');
    var appId=old==null?r.appId:old.app_id;
    var permission=entity+':'+(operation=='list'?'read':'write');
    if(operation=='list') {
        // 按操作员的应用管理范围过滤可见数据。
        var ctx=listContext(permission,p.appId||null,domain);
        var rows=sql.select('select t.*,t.tid as id,date_format(t.created_time,\'%Y-%m-%dT%H:%i:%sZ\') created_utc,date_format(t.updated_time,\'%Y-%m-%dT%H:%i:%sZ\') updated_utc from '+table+" t join iam_application_t a on a.tid=t.app_id and a.kind='BUSINESS' and a.is_del=0 where t.is_del=0 and t.identity_domain=#{domain} order by t.tid");
        var visible=rows.filter(x=>ctx.pairs.filter(pair=>pair.codes.contains(permission) && pair.scopes.filter(s=>s.identity_domain==domain && (s.scope_kind=='ALL' || s.scope_kind=='APPLICATION' && s.app_id==x.app_id)).size()>0).size()>0 && (p.appId==null || x.app_id==p.appId));
        return visible.map(x=>({
            id:x.id,appId:x.app_id,domain:x.identity_domain,name:x.name,code:x.code,status:x.status=='ACTIVE'?'enabled':'disabled',version:x.version,createdAt:x.created_utc,updatedAt:x.updated_utc,description:entity=='roles'?x.description||'':'',resourceIds:entity=='roles'?sql.select("""
select resource_id
from iam_role_resource_rela_t
where role_id=#{x.id}
""").map(v=>v.resource_id):[],parentId:entity=='resources'?x.parent_id:null,type:entity=='resources'?x.resource_type.toLowerCase():'',path:entity=='resources'?x.path||'':'',method:entity=='resources'?x.http_method||'GET':'',ext:entity=='resources'?fieldValues('view','RESOURCE',domain,x.id,x.ext_json==null?{}:JSONUtil.parseObj(x.ext_json),{}):{}
        }));
    }
    // 先校验当前会话的功能权限、身份域和应用管理范围。
    var ctx=guard(permission,appId,domain);
    // 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
    return mutate(ctx,entity+':'+operation,p,()=>{
        old=id==null?null:sql.selectOne('select *,tid as id from '+table+' where tid=#{id} and is_del=0 for update');
        if(old==null && p.creating!=true) {
            exit 404,'记录不存在';
        }
        // 以当前版本校验修改目标，阻止旧页面覆盖已经变更的记录。
        if(old!=null && (old.app_id!=r.appId && r.appId!=null || old.identity_domain!=domain || old.version!=(r.version||p.version))) {
            exit 409,'应用、身份域或版本不一致';
        }
        var app=sql.selectOne("""
select *,tid as id
from iam_application_t
where tid=#{appId}
    and kind='BUSINESS'
    and is_del=0
""");
        if(app==null || !JSONUtil.parseArray(app.identity_domains).contains(domain)) {
            exit 400,'目标不是此身份域的业务应用';
        }
        if(operation=='remove') {
            if(entity=='roles' && sql.selectOne("""
select count(*) n
from iam_grant_role_rela_t rr
join iam_access_grant_t g on g.tid=rr.grant_id
where rr.role_id=#{id}
    and g.is_del=0
    and g.status='ACTIVE'
""").n>0) {
                exit 409,'角色仍被有效授权引用，请停用或先撤销来源';
            }
            if(entity=='resources' && (sql.selectOne("""
select count(*) n
from iam_resource_t
where parent_id=#{id}
    and is_del=0
""").n>0 || sql.selectOne("""
select count(*) n
from iam_role_resource_rela_t
where resource_id=#{id}
""").n>0)) {
                exit 409,'资源仍被下级或角色引用';
            }
            sql.update('update '+table+" set status='DISABLED',is_del=1,version=version+1,updated_time=utc_timestamp(6) where tid=#{id}");
            rebuild(appId,domain);
            // 记录操作主体、目标及结果，供审计与故障追踪。
            audit(ctx,'SUCCESS',entity+':remove',id,null,{
                id:id
            });
            return {
                id:id
            };
        }
        var code=(r.code||'').trim();
        var name=(r.name||'').trim();
        var status=r.status=='disabled'?'DISABLED':'ACTIVE';
        if(name=='' || name.length()>(entity=='roles'?100:200) || !code.matches('[A-Za-z][A-Za-z0-9_:.-]{1,99}')) {
            exit 400,'名称或编码不正确';
        }
        if(old!=null && code!=old.code) {
            exit 400,'编码保持稳定';
        }
        if(old==null && sql.selectOne('select tid as id from '+table+' where app_id=#{appId} and code=#{code}')!=null) {
            exit 409,'编码已经使用';
        }
        if(entity=='roles') {
            var description=r.description||'';
            if(description.length()>1000) {
                exit 400,'说明过长';
            }
            var resources=r.resourceIds||[];
            if(resources.size()>500 || resources.distinct().size()!=resources.size()) {
                exit 400,'资源引用重复或过多';
            }
            for(rid in resources) {
                if(sql.selectOne("""
select tid as id
from iam_resource_t
where tid=#{rid}
    and app_id=#{appId}
    and identity_domain=#{domain}
    and status='ACTIVE'
    and is_del=0
""")==null) {
                    exit 400,'资源必须属于同应用和身份域且启用';
                }
            }
            if(old==null) {
                id=IdUtil.fastSimpleUUID();
                sql.update("""
insert into iam_role_t(tid, app_id, identity_domain, code, name, description, status)
values(#{id},#{appId},#{domain},#{code},#{name},#{description},#{status})
""");
            }
            else {
                sql.update("""
update iam_role_t
set name=#{name},description=#{description},status=#{status},version=version+1,
        updated_time=utc_timestamp(6)
where tid=#{id}
""");
            }
            sql.update("""
delete
from iam_role_resource_rela_t
where role_id=#{id}
""");
            for(rid in resources) {
                var link=IdUtil.fastSimpleUUID();
                sql.update("""
insert into iam_role_resource_rela_t(tid,
                role_id, resource_id, app_id)
values(#{link},#{id},#{rid},#{appId})
""");
            }
        }
        else {
            var type=(r.type||'').toUpperCase();
            var parentId=r.parentId||null;
            var path=r.path||'';
            var method=(r.method||'GET').toUpperCase();
            if(!['MENU','BUTTON','API'].contains(type) || path.length()>500 || type=='API' && (!path.matches('/[A-Za-z0-9_./{}:-]*') || !['GET','POST','PUT','PATCH','DELETE'].contains(method))) {
                exit 400,'资源类型或接口路径不正确';
            }
            var visited=[id];
            var cursor=parentId;
            while(cursor!=null) {
                if(visited.contains(cursor)) {
                    exit 400,'资源层级不能循环';
                }
                visited.add(cursor);
                var parent=sql.selectOne("""
select tid as id,parent_id,resource_type
from iam_resource_t
where tid=#{cursor}
    and app_id=#{appId}
    and identity_domain=#{domain}
    and status='ACTIVE'
    and is_del=0
""");
                if(parent==null || parent.resource_type!='MENU') {
                    exit 400,'父资源须为同应用同域菜单';
                }
                cursor=parent.parent_id;
            }
            if(old==null) {
                id=IdUtil.fastSimpleUUID();
                sql.update("""
insert into iam_resource_t(tid,
                app_id, identity_domain, code, name, resource_type, parent_id, path, http_method, status)
values(#{id},#{appId},#{domain},#{code},#{name},#{type},#{parentId},#{path},#{method},#{status})
""");
            }
            else {
                sql.update("""
update iam_resource_t
set name=#{name},resource_type=#{type},parent_id=#{parentId},path=#{path},
        http_method=#{method},
        status=#{status},
        version=version+1,
        updated_time=utc_timestamp(6)
where tid=#{id}
""");
            }
            var ext=JSONUtil.toJsonStr(fieldValues('encode','RESOURCE',domain,id,r.ext||{},old==null || old.ext_json==null?{}:JSONUtil.parseObj(old.ext_json)));
            sql.update("""
update iam_resource_t
set ext_json=#{ext}
where tid=#{id}
""");
        }
        rebuild(appId,domain);
        audit(ctx,'SUCCESS',entity+':save',id,null,{
            id:id,appId:appId
        });
        return {
            id:id
        };
    });

}));
