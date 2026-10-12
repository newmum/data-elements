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
import tableSort;
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

if(operation=='page') {
    var kind=p.kind||'users';
    if(!['users','authorizedUsers','orgs','roles','resources'].contains(kind)){exit 400,'列表类型不正确';}
    var page=Math.max(1,(p.page||1)::int);
    var size=Math.min(100,Math.max(1,(p.size||20)::int));
    var offset=(page-1)*size;
    return identityRuntime.inTenant(tenantId,()=>{
        var tenantSql=db.normal();
        var authorized=" EXISTS (SELECT 1 FROM rm_user_role_rela_t ur JOIN rm_role_t ar ON ar.id=ur.role_id AND ar.tenant_id=ur.tenant_id AND ar.app_id=#{localApp} AND ar.deleted=0 AND ar.status=0 WHERE ur.tenant_id=#{tenantId} AND ur.user_id=u.id AND COALESCE(ur.auth_from,0)<>9 AND (ur.end_time IS NULL OR ur.end_time>current_timestamp))";
        var from='';var fields='';var orderFields={name:'u.real_name'};
        if(kind=='users'||kind=='authorizedUsers') {
            from=' FROM rm_user_t u WHERE u.tenant_id=#{tenantId} AND u.deleted=0';
            if(kind=='authorizedUsers'){from=from+' AND u.status=0 AND '+authorized;}
            fields='u.id,u.real_name name,u.user_name account,u.status,CASE WHEN u.status=0 AND '+authorized+' THEN 1 ELSE 0 END authorized';
        } else if(kind=='orgs') {
            from=" FROM rm_org_t u LEFT JOIN rm_org_t parent ON parent.id=u.parent_id AND parent.tenant_id=u.tenant_id AND parent.deleted=0 WHERE u.tenant_id=#{tenantId} AND u.deleted=0 AND u.id<>'ROOT'";
            fields="u.id,u.name,u.dept_code code,u.parent_id parentId,COALESCE(parent.name,'根机构') parentName,u.status";orderFields={name:'u.name'};
        } else if(kind=='resources') {
            from=' FROM rm_menu_t u WHERE u.tenant_id=#{tenantId} AND u.app_id=#{localApp} AND u.deleted=0';
            fields='u.id,u.name,u.code,u.url path,u.status';orderFields={name:'u.name'};
        } else {
            var users="(SELECT COUNT(DISTINCT edge.user_id) FROM rm_user_role_rela_t edge JOIN rm_user_t person ON person.id=edge.user_id AND person.tenant_id=edge.tenant_id AND person.deleted=0 AND person.status=0 WHERE edge.tenant_id=#{tenantId} AND edge.role_id=u.id AND u.status=0 AND COALESCE(edge.auth_from,0)<>9 AND (edge.end_time IS NULL OR edge.end_time>current_timestamp))";
            var resources="(SELECT COUNT(DISTINCT edge.app_resource_id) FROM rm_role_menu_rela_t edge JOIN rm_menu_t menu ON menu.id=edge.app_resource_id AND menu.tenant_id=edge.tenant_id AND menu.app_id=#{localApp} AND menu.deleted=0 WHERE edge.tenant_id=#{tenantId} AND edge.role_id=u.id)";
            from=' FROM rm_role_t u WHERE u.tenant_id=#{tenantId} AND u.app_id=#{localApp} AND u.deleted=0';
            fields='u.id,u.name,u.code_num code,u.status,'+users+' userCount,'+resources+' resourceCount';
            orderFields={name:'u.name',userCount:users,resourceCount:resources};
        }
        var total=tenantSql.selectOne('SELECT COUNT(*) total'+from).total;
        var order=tableSort.field(p,orderFields,'name');
        var rows=tenantSql.select('SELECT '+fields+from+' ORDER BY CASE WHEN '+order+' IS NULL THEN 1 ELSE 0 END ASC,'+order+' '+tableSort.direction(p)+',u.id ASC LIMIT #{size} OFFSET #{offset}');
        var orgsByUser={};var rolesByUser={};
        if((kind=='users'||kind=='authorizedUsers')&&rows.size()>0) {
            var ids=rows.map(x=>x.id);
            var orgLinks=tenantSql.select("""
SELECT edge.user_id,org.name FROM rm_user_org_rela_t edge
JOIN rm_user_t person ON person.id=edge.user_id AND person.tenant_id=edge.tenant_id AND person.deleted=0
JOIN rm_org_t org ON org.id=edge.org_id AND org.tenant_id=edge.tenant_id AND org.deleted=0
WHERE edge.tenant_id=#{tenantId} AND edge.user_id IN (#{ids}) ORDER BY edge.user_id,edge.job_type,edge.id
""");
            var roleLinks=tenantSql.select("""
SELECT DISTINCT edge.user_id,role.name FROM rm_user_role_rela_t edge
JOIN rm_user_t person ON person.id=edge.user_id AND person.tenant_id=edge.tenant_id AND person.deleted=0 AND person.status=0
JOIN rm_role_t role ON role.id=edge.role_id AND role.tenant_id=edge.tenant_id AND role.app_id=#{localApp} AND role.deleted=0 AND role.status=0
WHERE edge.tenant_id=#{tenantId} AND edge.user_id IN (#{ids}) AND COALESCE(edge.auth_from,0)<>9 AND (edge.end_time IS NULL OR edge.end_time>current_timestamp)
ORDER BY edge.user_id,role.name
""");
            for(edge in orgLinks){if(orgsByUser[edge.user_id]==null){orgsByUser[edge.user_id]=[];}orgsByUser[edge.user_id].add(edge.name);}
            for(edge in roleLinks){if(rolesByUser[edge.user_id]==null){rolesByUser[edge.user_id]=[];}rolesByUser[edge.user_id].add(edge.name);}
        }
        for(row in rows){
            row.status=row.status==0?'enabled':'disabled';
            if(kind=='users'||kind=='authorizedUsers'){row.orgNames=orgsByUser[row.id]||[];row.roleNames=rolesByUser[row.id]||[];row.authorized=row.authorized==1;}
        }
        return {available:true,source:'TENANT_LOCAL',list:rows,total:total,page:page,size:size};
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
