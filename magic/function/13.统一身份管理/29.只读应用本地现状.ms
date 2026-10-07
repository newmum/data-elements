{
  "properties": {},
  "id": "ddfd5d8289e95bbc85e62d8708f45b6d",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "29.只读应用本地现状",
  "createTime": 1790813253492,
  "updateTime": 1790949884838,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/local-inventory",
  "description": "按已核验的运行绑定读取租户人员、机构、角色及资源汇总；不向控制库导入或改写租户数据。",
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
// 共享函数：29.只读应用本地现状
// 业务说明：按已核验的运行绑定读取租户人员、机构、角色及资源汇总；不向控制库导入或改写租户数据。
// 函数输入：operation：本次执行的业务操作；payload：请求资料或筛选条件。
// 返回与副作用：按已核验的运行绑定读取租户人员、机构、角色及资源汇总；不向控制库导入或改写租户数据。
import db;
import identityRuntime;
import '@/idaas/guard' as guard;

// 从已核验的运行绑定只读租户本地目录；写入只能由显式的手动导入接口处理。
// 本地账号、密码、角色授权与中央人员/授权始终是两个体系。
var p=payload||{};
var appId=''+(p.appId||'');
var domain=p.domain||'workforce';
if(!appId.matches('[A-Za-z0-9_.:-]{1,64}') || domain!='workforce') {
    exit 400,'应用或身份域不正确';
}
guard('apps:read',appId,domain);
guard('users:read',appId,domain);
guard('grants:read',appId,domain);
if(operation!='counts') {
    guard('orgs:read',appId,domain);
    guard('roles:read',appId,domain);
    guard('resources:read',appId,domain);
}
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
// 汇总分支：只读取租户现有数据并统计，不执行导入或下发。
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
