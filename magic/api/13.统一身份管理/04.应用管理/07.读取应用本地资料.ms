{
  "properties" : { },
  "id" : "7e158ace27ea5f6d89811d934a5588e0",
  "script" : null,
  "groupId" : "8e1839fbe6a55ce7aafaae3dcf2d15b4",
  "name" : "07.读取应用本地资料",
  "createTime" : 1790728570302,
  "updateTime" : 1791110563416,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/local-inventory",
  "method" : "GET",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "通过已核验的运行绑定只读查询指定租户的人员、机构、角色、资源及授权现状；读取失败明确报错，不修改平台或租户数据。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
// 接口：07.读取应用本地资料
// 路由：GET /idaas/applications/local-inventory
// 业务说明：通过已核验的运行绑定只读查询指定租户的人员、机构、角色、资源及授权现状；读取失败明确报错，不修改平台或租户数据。
import db;
import '@/idaas/guard' as guard;
import '@/idaas/context' as context;
import '@/idaas/audit' as audit;
import identityRuntime;
import request;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    var payload={};
    for(key in ['appId','appIds','domain','summary']) {
        var values=request.getValues(key);
        if(values!=null && values.size()>0) {
            payload.put(key,values[0]);
        }
    }
    // 请求参数与本接口业务处理。
    var operation = payload.summary=='1'?'counts':'live';

    if(payload.appIds!=null) {
        // 应用卡片只取概要：一次校验权限、一次查运行绑定，并按租户分组聚合本地人数。
        if(operation!='counts') { exit 400,'批量读取只支持概要模式'; }
        var domain=payload.domain||'workforce';
        if(domain!='workforce') { exit 400,'身份域不正确'; }
        var requestedIds=[];
        for(rawId in (''+payload.appIds).split(',')) {
            var appId=(''+rawId).trim();
            if(!appId.matches('[A-Za-z0-9_.:-]{1,64}')) { exit 400,'应用标识不正确'; }
            if(!requestedIds.contains(appId)) { requestedIds.add(appId); }
        }
        if(requestedIds.size()==0 || requestedIds.size()>50) { exit 400,'每批应用数量须为 1 至 50 个'; }
        var ctx=context();
        if(ctx.account.must_change_password==1) { exit 403,'请先在个人中心修改初始密码'; }
        var allowed=(appId,permission)=>{
            for(pair in ctx.pairs) {
                if(!pair.codes.contains(permission)) { continue; }
                for(scope in pair.scopes) {
                    if(scope.identity_domain==domain &&
                        (scope.scope_kind=='ALL' || (scope.scope_kind=='APPLICATION' && scope.app_id==appId))) {
                        return true;
                    }
                }
            }
            return false;
        };
        for(appId in requestedIds) {
            for(permission in ['apps:read','users:read','grants:read']) {
                if(!allowed(appId,permission)) {
                    audit(ctx,'DENIED',permission,appId,'SCOPE_OR_PERMISSION',null);
                    exit 403,'当前平台账号无此功能或对象范围权限';
                }
            }
        }
        var sql=db.normal();
        var bindings=sql.select("""
select rt.app_id,rt.runtime_tenant_id,rt.local_permission_app_id,rt.binding_status
from iam_application_runtime_t rt
join iam_application_t a on a.tid=rt.app_id
and a.kind='BUSINESS'
and a.is_del=0
where rt.app_id in
    <foreach collection="requestedIds" item="id" open="(" close=")" separator=",">#{id}</foreach>
""");
        var results={};
        var tenantIds=[];
        var bindingsByTenant={};
        for(appId in requestedIds) {
            results[appId]={appId:appId,available:false,reason:'请先核验应用连接',counts:{
                users:0,orgs:0,roles:0,resources:0,authorizedUsers:0
            }};
        }
        for(binding in bindings) {
            var appId=''+(binding.app_id||binding.appId);
            var tenantId=''+(binding.runtime_tenant_id||binding.runtimeTenantId||'');
            var localApp=''+(binding.local_permission_app_id||binding.localPermissionAppId||'');
            if((binding.binding_status||binding.bindingStatus)!='VERIFIED' || tenantId=='' || localApp=='') {
                continue;
            }
            if(bindingsByTenant[tenantId]==null) {
                bindingsByTenant[tenantId]=[];
                tenantIds.add(tenantId);
            }
            bindingsByTenant[tenantId].add({appId:appId,localApp:localApp});
        }
        if(tenantIds.size()>10) { exit 400,'单批跨租户数量不能超过 10 个'; }
        for(tenantId in tenantIds) {
            identityRuntime.inspectTenantBinding(tenantId);
            var tenantBindings=bindingsByTenant[tenantId];
            var localApps=tenantBindings.map(x=>x.localApp).distinct();
            var counts=identityRuntime.inTenant(tenantId,()=>{
                var tenantSql=db.normal();
                var people=tenantSql.selectOne("""
select count(*) n from rm_user_t
where tenant_id=#{tenantId} and deleted=0
""");
                var authorized=tenantSql.select("""
select r.app_id as local_app_id,count(distinct u.user_id) n
from rm_user_role_rela_t u
join rm_user_t person on person.id=u.user_id
and person.tenant_id=u.tenant_id
and person.deleted=0
and person.status=0
join rm_role_t r on r.id=u.role_id
and r.tenant_id=u.tenant_id
and r.app_id in
    <foreach collection="localApps" item="localApp" open="(" close=")" separator=",">#{localApp}</foreach>
and r.deleted=0
and r.status=0
where u.tenant_id=#{tenantId}
    and coalesce(u.auth_from, 0)<>9
    and (u.end_time is null or u.end_time>current_timestamp)
group by r.app_id
""");
                return {users:people.n,authorized:authorized};
            });
            var authorizedByApp={};
            for(row in counts.authorized) {
                authorizedByApp[''+(row.local_app_id||row.localAppId)]=row.n;
            }
            for(binding in tenantBindings) {
                results[binding.appId]={appId:binding.appId,available:true,source:'TENANT_LOCAL',counts:{
                    users:counts.users,authorizedUsers:authorizedByApp[binding.localApp]||0
                }};
            }
        }
        return {items:requestedIds.map(appId=>results[appId])};
    }

    // 从已核验的运行绑定只读租户本地目录；写入只能由显式的手动导入接口处理。
    // 本地账号、密码、角色授权与中央人员/授权始终是两个体系。
    var p=payload||{};
    var appId=''+(p.appId||'');
    var domain=p.domain||'workforce';
    if(!appId.matches('[A-Za-z0-9_.:-]{1,64}') || domain!='workforce') {
        exit 400,'应用或身份域不正确';
    }
    // 核验会话的功能权限及对象范围，超出范围的请求在读取或写入前拒绝。
    guard('apps:read',appId,domain);
    guard('users:read',appId,domain);
    guard('grants:read',appId,domain);
    if(operation!='counts') {
        guard('orgs:read',appId,domain);
        guard('roles:read',appId,domain);
        guard('resources:read',appId,domain);
    }
    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var binding=sql.selectOne("""
select rt.runtime_tenant_id,rt.local_permission_app_id,rt.binding_status
from iam_application_runtime_t rt
join iam_application_t a on a.tid=rt.app_id
and a.kind='BUSINESS'
and a.is_del=0
where rt.app_id=#{appId}
""");
    if(binding==null || binding.binding_status!='VERIFIED' || binding.local_permission_app_id==null) {
        return {
            available:false,reason:'请先核验应用连接',counts:{
                users:0,orgs:0,roles:0,resources:0,authorizedUsers:0
            }
        };
    }
    var tenantId=binding.runtime_tenant_id;
    var localApp=binding.local_permission_app_id;
    identityRuntime.inspectTenantBinding(tenantId);
    if(operation=='counts') {
        // 按已核验的运行绑定进入目标租户库，避免把租户数据写入控制库。
        return identityRuntime.inTenant(tenantId,()=>{
            var tenantSql=db.normal();
            var people=tenantSql.selectOne("""
select count(*) n
from rm_user_t
where tenant_id=#{tenantId}
    and deleted=0
""");
            var authorized=tenantSql.selectOne("""
select count(distinct u.user_id) n
from rm_user_role_rela_t u
join rm_user_t person on person.id=u.user_id
and person.tenant_id=u.tenant_id
and person.deleted=0
and person.status=0
join rm_role_t r on r.id=u.role_id
and r.tenant_id=u.tenant_id
and r.app_id=#{localApp}
and r.deleted=0
and r.status=0
where u.tenant_id=#{tenantId}
    and coalesce(u.auth_from, 0)<>9
    and (u.end_time is null
         or u.end_time>current_timestamp)
""");
            return {
                available:true,source:'TENANT_LOCAL',counts:{
                    users:people.n,authorizedUsers:authorized.n
                }
            };
        });
    }
    var local=identityRuntime.inTenant(tenantId,()=>{
        var tenantSql=db.normal();
        var people=tenantSql.select("""
select id,real_name name,user_name account,status
from rm_user_t
where tenant_id=#{tenantId}
    and deleted=0
order by id
""");
        var organizations=tenantSql.select("""
select id,name,dept_code code,parent_id,status
from rm_org_t
where tenant_id=#{tenantId}
    and deleted=0
    and id<>'ROOT'
order by org_path,id
""");
        var roles=tenantSql.select("""
select id,name,code_num code,status
from rm_role_t
where tenant_id=#{tenantId}
    and app_id=#{localApp}
    and deleted=0
order by id
""");
        var menus=tenantSql.select("""
select id,name,code,url,status,resource_type
from rm_menu_t
where tenant_id=#{tenantId}
    and app_id=#{localApp}
    and deleted=0
order by sort_num,id
""");
        var userOrgs=tenantSql.select("""
select u.user_id,u.org_id
from rm_user_org_rela_t u
join rm_org_t o on o.id=u.org_id
and o.tenant_id=u.tenant_id
and o.deleted=0
where u.tenant_id=#{tenantId}
order by u.user_id,u.job_type,u.id
""");
        var userRoles=tenantSql.select("""
select distinct u.user_id,u.role_id
from rm_user_role_rela_t u
join rm_user_t person on person.id=u.user_id
and person.tenant_id=u.tenant_id
and person.deleted=0
and person.status=0
join rm_role_t r on r.id=u.role_id
and r.tenant_id=u.tenant_id
and r.app_id=#{localApp}
and r.deleted=0
and r.status=0
where u.tenant_id=#{tenantId}
    and coalesce(u.auth_from, 0)<>9
    and (u.end_time is null
         or u.end_time>current_timestamp)
order by u.user_id,u.role_id
""");
        var roleMenus=tenantSql.select("""
select distinct x.role_id,x.app_resource_id
from rm_role_menu_rela_t x
join rm_role_t r on r.id=x.role_id
and r.tenant_id=x.tenant_id
and r.app_id=#{localApp}
and r.deleted=0
join rm_menu_t m on m.id=x.app_resource_id
and m.tenant_id=x.tenant_id
and m.app_id=#{localApp}
and m.deleted=0
where x.tenant_id=#{tenantId}
order by x.role_id,x.app_resource_id
""");
        var orgName=(id)=>{
            if(id==null || id=='ROOT') {
                return '根机构';
            }
            var found=organizations.filter(row=>row.id==id);
            return found.size()==0?'关联机构已移除':found[0].name;
        };
        var roleName=(id)=>{
            var found=roles.filter(row=>row.id==id);
            return found.size()==0?'关联角色已移除':found[0].name;
        };
        var userRows=people.map(row=>{
            var orgIds=userOrgs.filter(link=>link.user_id==row.id).map(link=>link.org_id).distinct();
            var roleIds=userRoles.filter(link=>link.user_id==row.id).map(link=>link.role_id).distinct();
            return {
                id:row.id,name:row.name,account:row.account,status:row.status==0?'enabled':'disabled',orgIds:orgIds,orgNames:orgIds.map(orgName),roleNames:roleIds.map(roleName),authorized:row.status==0 && roleIds.size()>0
            };
        });
        var orgRows=organizations.map(row=>({
            id:row.id,name:row.name,code:row.code,parentId:row.parent_id,parentName:orgName(row.parent_id),status:row.status==0?'enabled':'disabled'
        }));
        var roleRows=roles.map(row=>({
            id:row.id,name:row.name,code:row.code,status:row.status==0?'enabled':'disabled',userCount:userRoles.filter(link=>link.role_id==row.id).map(link=>link.user_id).distinct().size(),resourceCount:roleMenus.filter(link=>link.role_id==row.id).size()
        }));
        var resourceRows=menus.map(row=>({
            id:row.id,name:row.name,code:row.code,path:row.url,status:row.status==0?'enabled':'disabled',type:row.resource_type
        }));
        return {
            users:userRows,orgs:orgRows,roles:roleRows,resources:resourceRows,counts:{
                users:userRows.size(),orgs:orgRows.size(),roles:roleRows.size(),resources:menus.size(),authorizedUsers:userRows.filter(row=>row.authorized).size()
            }
        };
    });
    if(operation!='live') {
        exit 400,'操作不正确';
    }
    return {
        available:true,source:'TENANT_LOCAL',runtimeTenantId:tenantId,localPermissionAppId:localApp,counts:local.counts,
        users:local.users,orgs:local.orgs,roles:local.roles,resources:local.resources,authorizedUsers:local.users.filter(row=>row.authorized)
    };

}));
