{
  "id": "ga_local_role-admin_01",
  "groupId": "ga_local_accounts_functions_01",
  "path": "/role-admin",
  "name": "04.租户本地角色维护",
  "properties": {},
  "parameters": [
    {
      "name": "operation",
      "dataType": "Object",
      "required": false,
      "description": "要执行的本地账号业务动作"
    },
    {
      "name": "payload",
      "dataType": "Object",
      "required": false,
      "description": "该动作所需的账号、角色或资源资料"
    }
  ],
  "description": "复用租户本地角色的查询与保存规则，限制角色操作在当前租户库。",
  "script": null
}
================================
// 共享函数：04.租户本地角色维护
// 函数路径：/tenant-account/role-admin
// 业务说明：复用租户本地角色的查询与保存规则，限制角色操作在当前租户库。
// 函数输入：operation：要执行的本地账号业务动作；payload：该动作所需的账号、角色或资源资料。
import db;
import tenantRuntime;
import tenantAccounts;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import identityRuntime;
// 本地角色的保存和删除：先验证本地账号与角色归属，再在单一租户事务中写入。
var tenantId=tenantRuntime.id();var actor=StpUtil.getLoginId()+'';var p=payload||{};var sql=db.normal();
var id=(p.id||'')+'';var appId=(p.appId||tenantAccounts.session().get('appId')||'')+'';
if(appId=='') {exit 400,'应用不能为空';}
var creating=id=='';if(creating) {id=IdUtil.getSnowflakeNextIdStr();}
var old=sql.selectOne("""
select id
from rm_role_t
where tenant_id=#{tenantId}
    and id=#{id}
    and app_id=#{appId}
    and deleted=0
""");
if(identityRuntime.hasTable('idaas_receiver_object_t') && sql.selectOne("""
select local_id
from idaas_receiver_object_t
where local_id=#{id}
    and object_type='ROLE'
    and management_state='MANAGED'
""")!=null) {exit 409,'中央拥有的角色请在平台维护或先脱管';}
if(!creating && old==null) {exit 404,'角色不存在或不属于当前应用';}
var selected=[];
if(operation=='save') {
 for(userId in ((p.userIds||'')+'').split(',')) {
  var uid=userId.trim();if(uid=='') {continue;}
  if(tenantAccounts.find(tenantId,uid)==null) {exit 400,'所选用户不是本租户的本地用户';}
  if(!selected.contains(uid)) {selected.add(uid);}
 }
}
var self=sql.selectOne("""
select id
from rm_user_role_rela_t
where tenant_id=#{tenantId}
    and role_id=#{id}
    and user_id=#{actor}
limit 1
""");
if(self!=null && (operation=='delete' || !selected.contains(actor))) {exit 409,'不能删除或移除当前登录账号正在使用的角色';}
return sql.transaction(()=>{
 if(operation=='delete') {
  if(old==null) {exit 404,'角色不存在';}
  sql.update("""
update rm_role_t
set deleted=1,update_id=#{actor},update_time=current_timestamp
where tenant_id=#{tenantId}
    and id=#{id}
""");
  sql.update("""
delete
from rm_user_role_rela_t
where tenant_id=#{tenantId}
    and role_id=#{id}
""");
  sql.update("""
delete
from rm_role_menu_rela_t
where tenant_id=#{tenantId}
    and role_id=#{id}
""");
  return {id:id,deleted:true};
 }
 if(operation!='save') {exit 400,'不支持的角色操作';}
 var name=(p.roleName||'').trim();var code=(p.codeNum||'').trim();var description=(p.description||'')+'';
 var status=p.status==null?0:p.status::int;var sort=p.sortNum||0;
 if(name=='' || name.length()>255 || code=='' || code.length()>255 || ![0,1].contains(status)) {exit 400,'角色名称、编码或状态无效';}
 var duplicate=sql.selectOne("""
select id
from rm_role_t
where tenant_id=#{tenantId}
    and app_id=#{appId}
    and code_num=#{code}
    and id<>#{id}
    and deleted=0
""");
 if(duplicate!=null) {exit 409,'当前应用的角色编码已存在';}
 if(creating) {sql.update("""
insert into rm_role_t(tenant_id,
                id, app_id, name, code_num, description, status, sort_num, deleted, create_id, update_id,
                create_time, update_time)
values(#{tenantId},#{id},#{appId},#{name},#{code},#{description},#{status},#{sort},0,#{actor},#{actor},current_timestamp,current_timestamp)
""");}
 else {sql.update("""
update rm_role_t
set name=#{name},code_num=#{code},description=#{description},status=#{status},
    sort_num=#{sort},update_id=#{actor},update_time=current_timestamp
where tenant_id=#{tenantId}
    and id=#{id}
""");}
 sql.update("""
delete
from rm_user_role_rela_t
where tenant_id=#{tenantId}
    and role_id=#{id}
""");
 for(uid in selected) {var rid=IdUtil.getSnowflakeNextIdStr();sql.update("""
insert into rm_user_role_rela_t(tenant_id,
                id, user_id, role_id, auth_from, edit_time, is_temporary)
values(#{tenantId},#{rid},#{uid},#{id},0,current_timestamp,0)
""");}
 return {success:true,id:id};
});
