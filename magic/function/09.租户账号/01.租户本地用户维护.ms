{
  "id": "ga_local_users_01",
  "groupId": "ga_local_accounts_functions_01",
  "name": "01.租户本地用户维护",
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
      "description": "保存或删除所需的本地用户资料"
    }
  ],
  "script": null,
  "description": "用户保存和删除共同使用的本地账号校验、锁定及关联维护。"
}
================================
import db;
import '@/common/audit-operation' as auditOperation;
import tenantRuntime;
import tenantAccounts;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SmUtil;
import identityRuntime;
import cn.hutool.json.JSONUtil;
import com.linewell.dataelement.utils.PwdRuleUtil;
// 用户保存与删除共用本地账号校验、锁定及关联维护，分页逻辑由用户分页 API 执行。
var tenantId=tenantRuntime.id();var actor=StpUtil.getLoginId()+'';var p=payload||{};var sql=db.normal();
if(!tenantAccounts.isLocal(tenantId)) {exit 403,'当前租户未启用本地账号';}
var text=value=>value==null?'':(value+'').trim();
var id=text(p.id);var creating=id=='';if(creating) {id=IdUtil.getSnowflakeNextIdStr();}
var password=text(p.password);var passwordChanged=password!='' && password!='******';
var result=sql.transaction(()=>{
 var old=sql.selectOne("""
select id,password as "password",status,app_file_id as "appFileId",
       real_name as "realName",user_name as "userName",phone,gender,
       id_card as "idCard",date_birth as "dateBirth",nation,
       office_address as "officeAddress",job_number as "jobNumber",
       authorized_strength as "authorizedStrength",origin_position as "originPosition",origin_rank as "originRank"
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
  // 部分行业使用文件服务而不建旧版照片表；不能因历史照片引用阻断用户归档。
  if(old.appFileId!=null && old.appFileId!='' && identityRuntime.hasTable('sym_file_t')) {sql.update("""
update sym_file_t
set deleted=1,update_id=#{actor},update_time=current_timestamp
where tenant_id=#{tenantId}
    and id=#{old.appFileId}
""");}
  auditOperation('USER_DELETED','USER',id,null,{id:id,status:'DELETED'});
  return {id:id,accountDeleted:true};
 }
 if(operation!='save') {exit 400,'不支持的用户操作';}
 if(!creating && old==null) {exit 404,'用户不存在或不属于当前租户';}
 // 启停等局部维护只提交变化字段；未提交的资料和密码均保持原值。
 var realName=text(p.containsKey('realName')?p.realName:old?.realName);
 var account=text(p.containsKey('userName')?p.userName:old?.userName);
 var phone=text(p.containsKey('phone')?p.phone:old?.phone);
 var orgId=text(p.orgId);var statusText=text(p.containsKey('status')?p.status:old?.status);
 if(!creating && !p.containsKey('orgId')){
  var primaryOrg=sql.selectOne("select org_id as \"orgId\" from rm_user_org_rela_t where tenant_id=#{tenantId} and user_id=#{id} and job_type='1'");
  orgId=text(primaryOrg?.orgId);
 }
 if(realName=='' || realName.length()>50) {exit 400,'请填写姓名，长度不超过50个字符';}
 if(account=='' || account.length()>50) {exit 400,'请填写用户账号，长度不超过50个字符';}
 if(phone=='' || phone.length()>128) {exit 400,'请填写手机号，长度不超过128个字符';}
 if(!['0','1'].contains(statusText)) {exit 400,'账号状态无效，请选择启用或禁用';}
 var status=statusText::int;
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
 var gender=p.containsKey('gender')?p.gender:old?.gender;var idCard=p.containsKey('idCard')?p.idCard:old?.idCard;
 var birth=p.containsKey('dateBirth')?p.dateBirth:old?.dateBirth;
 var dateBirth=text(birth)==''?null:text(birth).substring(0,10);var nation=p.containsKey('nation')?p.nation:old?.nation;
 var office=p.containsKey('officeAddress')?p.officeAddress:old?.officeAddress;var job=p.containsKey('jobNumber')?p.jobNumber:old?.jobNumber;
 var strength=p.containsKey('authorizedStrength')?p.authorizedStrength:old?.authorizedStrength;
 var position=p.containsKey('originPosition')?p.originPosition:old?.originPosition;var rank=p.containsKey('originRank')?p.originRank:old?.originRank;
 var file=p.containsKey('appFileId')?p.appFileId:old?.appFileId;
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
 auditOperation(creating?'USER_CREATED':'USER_UPDATED','USER',id,realName,{id:id,name:realName,status:status});
 return {id:id};
});
if(operation=='delete' || passwordChanged || p.status==1) {tenantAccounts.revoke(id);}
return result;
