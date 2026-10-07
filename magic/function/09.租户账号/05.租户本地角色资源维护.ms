{
  "id": "ga_local_resources_01",
  "groupId": "ga_local_accounts_functions_01",
  "path": "/resources",
  "name": "05.租户本地角色资源维护",
  "properties": {},
  "parameters": [
    {
      "name": "payload",
      "dataType": "Object",
      "required": false,
      "description": "该动作所需的账号、角色或资源资料"
    }
  ],
  "description": "维护租户本地角色与菜单资源的关联，供本地系统权限判断使用。",
  "script": null
}
================================
// 共享函数：05.租户本地角色资源维护
// 函数路径：/tenant-account/resources
// 业务说明：维护租户本地角色与菜单资源的关联，供本地系统权限判断使用。
// 函数输入：payload：该动作所需的账号、角色或资源资料。
import db;
import tenantRuntime;
import cn.hutool.core.util.IdUtil;
import identityRuntime;
// 本地角色菜单：无效资源在清理旧授权之前拒绝，替换操作使用同一个租户事务。
var tenantId=tenantRuntime.id();var p=payload||{};var roleId=(p.roleId||'')+'';var sql=db.normal();
var role=sql.selectOne("""
select id,app_id as "appId"
from rm_role_t
where tenant_id=#{tenantId}
    and id=#{roleId}
    and deleted=0
""");
if(identityRuntime.hasTable('idaas_receiver_object_t') && sql.selectOne("""
select local_id
from idaas_receiver_object_t
where local_id=#{roleId}
    and object_type='ROLE'
    and management_state='MANAGED'
""")!=null) {exit 409,'中央拥有角色的资源请在平台维护或先脱管';}
if(role==null) {exit 404,'角色不存在或不属于当前租户';}
var ids=[];var appId=role.appId;
for(resourceId in (p.resourceIds||[])) {
 var id=resourceId+'';
 if(sql.selectOne("""
select id
from rm_menu_t
where tenant_id=#{tenantId}
    and app_id=#{appId}
    and id=#{id}
    and deleted=0
""")==null) {exit 400,'所选菜单不存在或不属于当前应用';}
 if(!ids.contains(id)) {ids.add(id);}
}
return sql.transaction(()=>{
 sql.update("""
delete
from rm_role_menu_rela_t
where tenant_id=#{tenantId}
    and role_id=#{roleId}
""");
 for(resourceId in ids) {var id=IdUtil.getSnowflakeNextIdStr();sql.update("""
insert into rm_role_menu_rela_t(tenant_id,
                id, role_id, app_resource_id, extend_id)
values(#{tenantId},#{id},#{roleId},#{resourceId},'')
""");}
 return {roleId:roleId,count:ids.size()};
});
