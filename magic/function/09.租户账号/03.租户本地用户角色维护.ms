{
  "id": "ga_local_roles_01",
  "groupId": "ga_local_accounts_functions_01",
  "name": "03.租户本地用户角色维护",
  "path": "/roles",
  "parameters": [
    {
      "name": "payload",
      "dataType": "Object",
      "type": null,
      "required": false,
      "value": null,
      "description": "该动作所需的账号、角色或资源资料"
    }
  ],
  "script": null,
  "description": "读取或维护租户本地用户与角色的关联，校验关联对象后返回角色配置结果。"
}
================================
// 共享函数：03.租户本地用户角色维护
// 函数路径：/tenant-account/roles
// 业务说明：读取或维护租户本地用户与角色的关联，校验关联对象后返回角色配置结果。
// 函数输入：payload：该动作所需的账号、角色或资源资料。
import db;
import tenantRuntime;
import tenantAccounts;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import identityRuntime;
import cn.hutool.json.JSONUtil;
// 仅替换当前应用角色关联；其他应用的角色关联保留。全部校验在事务写入前完成。
var tenantId=tenantRuntime.id();var p=payload||{};var userId=(p.userId||'')+'';
var appId=(p.appId||tenantAccounts.session().get('appId')||'')+'';var sql=db.normal();
if(userId=='' || appId=='') {exit 400,'用户和应用不能为空';}
if(tenantAccounts.find(tenantId,userId)==null) {exit 404,'用户不存在或不属于当前租户';}
var available=sql.select("""
select id
from rm_role_t
where tenant_id=#{tenantId}
    and app_id=#{appId}
    and deleted=0
    and status=0
""").map(r=>r.id);
var ids=[];
for(id in (p.roleIds||[])) {
 var roleId=(id+'').trim();if(!available.contains(roleId)) {exit 400,'所选角色不存在或不属于当前应用';}
 if(!ids.contains(roleId)) {ids.add(roleId);}
}
if(userId==StpUtil.getLoginId()+'' && ids.size()==0) {exit 409,'不能清空当前登录账号的应用角色';}
return sql.transaction(()=>{
 var managed=identityRuntime.hasTable('idaas_receiver_object_t')?sql.selectOne("""
select snapshot_json
from idaas_receiver_object_t
where object_type='SUBJECT'
    and local_id=#{userId}
    and management_state='MANAGED'
"""):null;
 var owned=managed==null?[]:JSONUtil.parseObj(managed.snapshot_json).roleRelations||[];
 var central=owned.size()==0?[]:sql.select("""
select role_id
from rm_user_role_rela_t
where tenant_id=#{tenantId}
    and id in (#{owned})
""").map(r=>r.role_id);
 if(central.filter(id=>!ids.contains(id)).size()>0) {exit 409,'不能移除中央来源角色，请在平台撤权或先脱管';}
 if(owned.size()>0) {sql.update("""
delete
from rm_user_role_rela_t
where tenant_id=#{tenantId}
    and user_id=#{userId}
    and id not in (#{owned})
    and role_id in
        (select id
         from rm_role_t
         where tenant_id=#{tenantId}
             and app_id=#{appId})
""");}else {sql.update("""
delete
from rm_user_role_rela_t
where tenant_id=#{tenantId}
    and user_id=#{userId}
    and role_id in
        (select id
         from rm_role_t
         where tenant_id=#{tenantId}
             and app_id=#{appId})
""");}
 for(roleId in ids) {
  if(central.contains(roleId)) {continue;}
  var id=IdUtil.getSnowflakeNextIdStr();sql.update("""
insert into rm_user_role_rela_t(tenant_id,
                id, user_id, role_id, auth_from, edit_time, is_temporary)
values(#{tenantId},#{id},#{userId},#{roleId},0,current_timestamp,0)
""");
 }
 return {userId:userId,roleIds:ids};
});
