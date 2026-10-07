{
  "id": "ga_local_users_01",
  "groupId": "ga_local_accounts_functions_01",
  "name": "02.租户本地用户维护",
  "path": "/users",
  "parameters": [
    {
      "name": "operation",
      "dataType": "Object",
      "type": null,
      "required": false,
      "value": null,
      "description": "要执行的本地账号业务动作"
    },
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
  "description": "复用租户本地用户的查询、保存及状态规则，读写本地账号资料而不修改控制库统一认证账号。"
}
================================
// 共享函数：02.租户本地用户维护
// 函数路径：/tenant-account/users
// 业务说明：复用租户本地用户的查询、保存及状态规则，读写本地账号资料而不修改控制库统一认证账号。
// 函数输入：operation：要执行的本地账号业务动作；payload：该动作所需的账号、角色或资源资料。
import db;
import tenantRuntime;
import tenantAccounts;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SmUtil;
import identityRuntime;
import cn.hutool.json.JSONUtil;
import com.linewell.dataelement.utils.PwdRuleUtil;
// 租户本地用户：查询不返回密码摘要；账号及机构关系在同一业务库事务中维护。
var tenantId=tenantRuntime.id();var actor=StpUtil.getLoginId()+'';var p=payload||{};var sql=db.normal();
if(!tenantAccounts.isLocal(tenantId)) {exit 403,'当前租户未启用本地账号';}
var text=value=>value==null?'':(value+'').trim();
if(operation=='page' || operation=='list') {
 var page=Math.max(1,p.pageNum||1);var size=Math.min(200,Math.max(1,p.pageSize||200));var offset=(page-1)*size;
 var ids=p.ids;var account='%'+text(p.userName)+'%';var name='%'+text(p.realName)+'%';
 if(ids!=null && ids.size()==0) {return operation=='list'?[]:{list:[],total:0};}
 var where=`from rm_user_t u where u.tenant_id=#{tenantId} and u.deleted=0 and u.user_name like #{account} and u.real_name like #{name}`;
 if(ids!=null) {where=where+` and u.id in (#{ids})`;}
 var total=sql.selectOne('select count(*) as total '+where).total;
 var rows=sql.select("""
select u.id,u.real_name as "realName",u.user_name as "userName",u.phone,u.gender,u.status,
       '******' as "password", u.app_file_id as "appFileId",u.create_id as "createId",u.create_time as "createTime",
       u.update_time as "updateTime", u.id_card as "idCard",u.date_birth as "dateBirth",u.nation,
       u.office_address as "officeAddress",u.job_number as "jobNumber", u.authorized_strength as "authorizedStrength",
       u.origin_position as "originPosition",u.origin_rank as "originRank"
"""+where+` order by u.create_time,u.id limit #{size} offset #{offset}`);
 return operation=='list'?rows:{list:rows,total:total,pageNum:page,pageSize:size};
}
var id=text(p.id);var creating=id=='';if(creating) {id=IdUtil.getSnowflakeNextIdStr();}
var password=text(p.password);var passwordChanged=password!='' && password!='******';
var result=sql.transaction(()=>{
 var old=sql.selectOne("""
select id,password as "password",status,app_file_id as "appFileId"
from rm_user_t
where tenant_id=#{tenantId}
    and id=#{id}
    and deleted=0
    for update
""");
 if(operation=='delete') {
  if(old==null) {exit 404,'用户不存在或不属于当前租户';}
  if(id==actor) {exit 409,'不能删除当前登录账号';}
  sql.update("""
update rm_user_t
set deleted=1,status=1,update_id=#{actor},update_time=current_timestamp
where tenant_id=#{tenantId}
    and id=#{id}
""");
  sql.update("""
delete
from rm_user_org_rela_t
where tenant_id=#{tenantId}
    and user_id=#{id}
""");
  sql.update("""
delete
from rm_user_role_rela_t
where tenant_id=#{tenantId}
    and user_id=#{id}
""");
  if(old.appFileId!=null && old.appFileId!='') {sql.update("""
update sym_file_t
set deleted=1,update_id=#{actor},update_time=current_timestamp
where tenant_id=#{tenantId}
    and id=#{old.appFileId}
""");}
  return {id:id,accountDeleted:true};
 }
 if(operation!='save') {exit 400,'不支持的用户操作';}
 if(!creating && old==null) {exit 404,'用户不存在或不属于当前租户';}
 var realName=text(p.realName);var account=text(p.userName);var phone=text(p.phone);var orgId=text(p.orgId);var status=p.status==null?-1:p.status::int;
 if(realName=='' || realName.length()>50 || !account.matches('[A-Za-z][A-Za-z0-9_.-]{2,49}') || phone=='' || phone.length()>128 || ![0,1].contains(status)) {exit 400,'姓名、账号、手机号或账号状态无效';}
 if(id==actor && status!=0) {exit 409,'不能停用当前登录账号';}
 var org=sql.selectOne("""
select id
from rm_org_t
where tenant_id=#{tenantId}
    and id=#{orgId}
    and deleted=0
    and status=0
""");
 if(org==null || orgId=='ROOT') {exit 400,'请选择有效的所属机构';}
 var managed=identityRuntime.hasTable('idaas_receiver_object_t')?sql.selectOne("""
select snapshot_json
from idaas_receiver_object_t
where object_type='SUBJECT'
    and local_id=#{id}
    and management_state='MANAGED'
"""):null;
 var ownedOrgIds=managed==null?[]:JSONUtil.parseObj(managed.snapshot_json).orgRelations||[];
 if(ownedOrgIds.size()>0 && sql.selectOne("""
select id
from rm_user_org_rela_t
where tenant_id=#{tenantId}
    and user_id=#{id}
    and id in (#{ownedOrgIds})
    and job_type='1'
    and org_id=#{orgId}
""")==null) {exit 409,'统一管理的主职请在平台更新或先办理脱管';}
 var duplicate=sql.selectOne("""
select id
from rm_user_t
where tenant_id=#{tenantId}
    and lower(trim(user_name))=lower(#{account})
    and deleted=0
    and id<>#{id}
""");
 if(duplicate!=null) {exit 409,'用户账号已存在';}
 if(creating && !passwordChanged) {exit 400,'新增用户密码不能为空';}
 var hash=old==null?null:old.password;
 if(passwordChanged) {
  if(password.length()<6 || password.length()>128 || password.matches('(?i)[0-9a-f]{96}')) {exit 400,'密码应为6至128个字符，不能直接提交存储摘要';}
  var digest=password.matches('(?i)[0-9a-f]{64}')?password.toUpperCase():SmUtil.sm3(password).toUpperCase();
  hash=PwdRuleUtil.encryptPwdBySalt(digest);
 }
 // 保持现有用户表单字段和照片引用；不接受客户端指定租户或来源字段。
 var gender=p.gender;var idCard=p.idCard;var dateBirth=text(p.dateBirth)==''?null:text(p.dateBirth).substring(0,10);var nation=p.nation;
 var office=p.officeAddress;var job=p.jobNumber;var strength=p.authorizedStrength;var position=p.originPosition;var rank=p.originRank;var file=p.appFileId;
 if(creating) {sql.update("""
insert into rm_user_t(tenant_id,
                id, real_name, user_name, phone, password, status, deleted, create_id, update_id, create_time,
                update_time)
values(#{tenantId},#{id},#{realName},#{account},#{phone},#{hash},#{status},0,#{actor},#{actor},current_timestamp,current_timestamp)
""");}
 sql.update("""
update rm_user_t
set real_name=#{realName},user_name=#{account},phone=#{phone},password=#{hash},
    status=#{status},gender=#{gender}, id_card=#{idCard},date_birth=#{dateBirth},
    nation=#{nation},office_address=#{office},job_number=#{job},
    authorized_strength=#{strength}, origin_position=#{position},origin_rank=#{rank},
    app_file_id=#{file},update_id=#{actor},update_time=current_timestamp
where tenant_id=#{tenantId}
    and id=#{id}
""");
 if(ownedOrgIds.size()==0) {
  sql.update("""
delete
from rm_user_org_rela_t
where tenant_id=#{tenantId}
    and user_id=#{id}
    and job_type='1'
""");
  var relationId=IdUtil.getSnowflakeNextIdStr();
  sql.update("""
insert into rm_user_org_rela_t(tenant_id, id, user_id, org_id, job_type, sort_num)
values(#{tenantId},#{relationId},#{id},#{orgId},'1',0)
""");
 }
 return {id:id};
});
if(operation=='delete' || passwordChanged || p.status==1) {tenantAccounts.revoke(id);}
return result;
