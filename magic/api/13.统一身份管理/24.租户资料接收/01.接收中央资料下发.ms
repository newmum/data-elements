{
  "properties": {},
  "id": "d523319ceb2b56edbabedb12112417d7",
  "script": null,
  "groupId": "955acd903650573fbe414d610962a487",
  "name": "01.接收中央资料下发",
  "createTime": 1790618621046,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/v2",
  "description": "验证标准报文、来源签名或凭据及幂等号，在租户上下文中更新中央来源拥有的人员、机构等字段并返回回执；保留本地密码、角色与停用状态。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：01.接收中央资料下发
// 路由：POST /idaas/receiver/v2
// 业务说明：验证标准报文、来源签名或凭据及幂等号，在租户上下文中更新中央来源拥有的人员、机构等字段并返回回执；保留本地密码、角色与停用状态。
import db;
import identityTransport;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import java.lang.Number;
import systemConfig;
import identityRuntime;
import request;
import com.linewell.dataelement.utils.PwdRuleUtil;
import '@/idaas/api-call' as apiCall;
return identityRuntime.control(()=>apiCall(()=>{
    // 34.receiver-apply：仅供本接收接口处理对应的下发对象。
    var apply=(binding, event, payloadHash)=>{
        // 本地持久化适配：只替换当前中央来源拥有的字段/关联。密码、本地角色和本地停用不在报文内。
        // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
        var sql=db.normal();
        var type=event.objectType;
        var source=event.objectId;
        var data=event.data||{};
        var app=binding.app_id;
        var tenant=binding.tenant_id;
        var localApp=binding.local_permission_app_id;
        var keys=type=='SUBJECT'?['name','account','phone','email','appointments','roleIds','enabled']:type=='ORG'?['name','code','parentId','enabled']:type=='ROLE'?['name','code','resourceIds','enabled']:['name','code','parentId','type','path','method','enabled'];
        for(k in data.keySet()) {
            if(!keys.contains(k)) {
                exit 400,'报文包含不允许的资料字段';
            }
        }
        if(event.action!='UPSERT' && data.enabled==true) {
            exit 400,'停用或删除事件不能声明来源启用';
        }
        var map=sql.selectOne("""
select *
from idaas_receiver_object_t
where app_id=#{app}
    and object_type=#{type}
    and source_id=#{source}
    for update
""");
        if(map!=null && map.instance_id!=binding.instance_id) {
            return {
                result:'CONFLICT',message:'已有映射属于其他接收实例，请办理迁移',localId:map.local_id
            };
        }
        if(map!=null && map.management_state!='MANAGED') {
            return {
                result:'DETACHED',message:'对象已移交本地维护',localId:map.local_id
            };
        }
        if(map!=null && event.sourceVersion<map.source_version) {
            return {
                result:'STALE',message:'接收端已有更高来源版本',localId:map.local_id,sourceVersion:map.source_version,payloadHash:map.payload_hash
            };
        }
        if(map!=null && event.sourceVersion==map.source_version) {
            return {
                result:map.payload_hash==payloadHash?'DUPLICATE':'CONFLICT',message:map.payload_hash==payloadHash?'相同来源版本已接收':'同版本资料不一致',localId:map.local_id
            };
        }
        var id=map==null?IdUtil.fastSimpleUUID():map.local_id;
        var name=(data.name||'').trim();
        var enabled=data.enabled==true?1:0;
        var prior=map==null || map.snapshot_json==null?{
            fields:{},orgRelations:[],roleRelations:[],resourceRelations:[]
        }
        :identityTransport.parseJson(map.snapshot_json);
        var table=type=='ORG'?'rm_org_t':type=='SUBJECT'?'rm_user_t':type=='ROLE'?'rm_role_t':'rm_menu_t';
        var local=map==null?null:sql.selectOne('select * from '+table+' where tenant_id=#{tenant} and id=#{id} and deleted=0 for update');
        if(map!=null && local==null) {
            return {
                result:'CONFLICT',message:'映射的本地对象已删除',localId:id
            };
        }
        if(local!=null) {
            for(k in prior.fields.keySet()) {
                if(identityTransport.canonicalJson(local.get(k))!=identityTransport.canonicalJson(prior.fields.get(k))) {
                    return {
                        result:'CONFLICT',message:'中央拥有字段已在本地修改，请选择脱管或协调资料',localId:id
                    };
                }
            }
        }
        if(name=='' || name.length()>(type=='RESOURCE'?64:type=='ROLE'?100:type=='SUBJECT'?50:200)) {
            return {
                result:'CONFLICT',message:'资料名称超出本地字段约束',localId:id
            };
        }
        var fields={};
        var next={
            fields:fields,orgRelations:[],roleRelations:[],resourceRelations:[]
        };
        var parents=[];
        var localResources=[];
        var localRoles=[];
        var username=null;
        var code='IAM_'+source;
        var parentId=null;
        var path=null;
        var serial=null;
        var nature=null;
        if(type=='ORG' || type=='RESOURCE') {
            if(data.parentId!=null && data.parentId!='') {
                var parent=sql.selectOne("""
select local_id
from idaas_receiver_object_t
where app_id=#{app}
    and object_type=#{type}
    and source_id=#{data.parentId}
    and management_state='MANAGED'
""");
                if(parent==null) {
                    return {
                        result:'CONFLICT',message:'上级对象尚未接收',localId:id
                    };
                }
                parentId=parent.local_id;
            }
            if(type=='ORG') {
                var parent=parentId==null?null:sql.selectOne("""
select org_path,serial_number
from rm_org_t
where tenant_id=#{tenant}
    and id=#{parentId}
""");
                path=(parent==null?'':parent.org_path||'')+'/'+id;
                serial=parent==null?(local!=null && local.serial_number!=null && local.serial_number.length()==4?local.serial_number:'I'+id.substring(0,3).toUpperCase()):parent.serial_number+id.substring(0,4).toUpperCase();
                nature=parent==null?'2':local==null?'1':local.nature||'1';
                if(parent==null) {
                    parentId='ROOT';
                }
                if(path.length()>500 || serial.length()>50 || sql.selectOne("""
select id
from rm_org_t
where tenant_id=#{tenant}
    and serial_number=#{serial}
    and id<>#{id}
    and deleted=0
""")!=null) {
                    return {
                        result:'CONFLICT',message:'本地机构路径或运行编码冲突，请明确关联',localId:id
                    };
                }
                fields={
                    name:name,dept_code:data.code,parent_id:parentId,org_path:path,serial_number:serial,nature:nature
                };
                next.fields=fields;
            }
            else {
                var method=data.method||'GET';
                var resourceType=data.type=='menu'?0:data.type=='button'?1:2;
                var url=data.path||'';
                if(url.length()>256 || !['menu','button','api'].contains(data.type)) {
                    return {
                        result:'CONFLICT',message:'资源类型或路径超出本地约束',localId:id
                    };
                }
                fields={
                    name:name,parent_id:parentId,sno:code,url:url,method_name:data.code,status:enabled==1?0:1
                };
                next.fields=fields;
            }
        }
        if(type=='ROLE') {
            for(rid in (data.resourceIds||[])) {
                var resource=sql.selectOne("""
select local_id
from idaas_receiver_object_t
where app_id=#{app}
    and object_type='RESOURCE'
    and source_id=#{rid}
    and management_state='MANAGED'
""");
                if(resource==null) {
                    return {
                        result:'CONFLICT',message:'角色资源尚未接收',localId:id
                    };
                }
                localResources.add(resource.local_id);
            }
            fields={
                name:name,code_num:code,status:enabled==1?0:1
            };
            next.fields=fields;
        }
        if(type=='SUBJECT') {
            username=(data.account||'').trim();
            if(!username.matches('[A-Za-z][A-Za-z0-9_.-]{2,49}') || (data.phone||'').length()>128 || (data.email||'').length()>255) {
                return {
                    result:'CONFLICT',message:'拟用账号或联系方式不符合本地约束',localId:id
                };
            }
            if(sql.selectOne("""
select id
from rm_user_t
where tenant_id=#{tenant}
    and user_name=#{username}
    and deleted=0
    and id<>#{id}
""")!=null) {
                return {
                    result:'CONFLICT',message:'同名本地账号须由接收端明确关联，不能自动接管',localId:id
                };
            }
            var requestedOrgIds=(data.appointments||[]).map(job=>job.orgId).distinct();
            var mappedOrgs=requestedOrgIds.size()==0?[]:sql.select("""
select m.source_id,m.local_id
from idaas_receiver_object_t m
join rm_org_t o on o.id=m.local_id and o.tenant_id=#{tenant} and o.deleted=0
where m.app_id=#{app} and m.instance_id=#{binding.instance_id}
    and m.object_type='ORG' and m.management_state='MANAGED'
    and m.source_id in <foreach collection="requestedOrgIds" item="orgId" open="(" close=")" separator=",">#{orgId}</foreach>
""");
            if(mappedOrgs.size()!=requestedOrgIds.size()) {
                return {
                    result:'CONFLICT',message:'任职机构尚未明确关联至本地机构',localId:id
                };
            }
            for(job in (data.appointments||[])) {
                var org=mappedOrgs.filter(row=>row.source_id==job.orgId);
                if(org.size()!=1) {
                    return {
                        result:'CONFLICT',message:'任职机构尚未接收',localId:id
                    };
                }
                parents.add({
                    id:org[0].local_id,primary:job.primary
                });
            }
            if(enabled==1) {
                var requestedRoleIds=(data.roleIds||[]).distinct();
                var mappedRoles=requestedRoleIds.size()==0?[]:sql.select("""
select m.source_id,m.local_id
from idaas_receiver_object_t m
join rm_role_t r on r.id=m.local_id and r.tenant_id=#{tenant}
    and r.app_id=#{localApp} and r.status=0 and r.deleted=0
where m.app_id=#{app} and m.instance_id=#{binding.instance_id}
    and m.object_type='ROLE' and m.management_state='MANAGED'
    and m.source_id in <foreach collection="requestedRoleIds" item="roleId" open="(" close=")" separator=",">#{roleId}</foreach>
""");
                if(mappedRoles.size()!=requestedRoleIds.size()) {
                    return {
                        result:'CONFLICT',message:'人员角色未映射至当前公安应用的有效本地角色',localId:id
                    };
                }
                for(role in mappedRoles) {
                    localRoles.add(role.local_id);
                }
            }
            fields={
                real_name:name,user_name:username,phone:data.phone||'',email:data.email||''
            };
            next.fields=fields;
        }
        // 检查所拥有的关联没有被本地修改；失败发生在任何替换之前。
        for(pair in [{
            table:'rm_user_org_rela_t',ids:prior.orgRelations||[]
        },{
            table:'rm_user_role_rela_t',ids:prior.roleRelations||[]
        },{
            table:'rm_role_menu_rela_t',ids:prior.resourceRelations||[]
        }]) {
            var ids=pair.ids;
            if(ids.size()>0 && sql.select('select id from '+pair.table+
                ' where tenant_id=#{tenant} and id in <foreach collection="ids" item="link" open="(" close=")" separator=",">#{link}</foreach>').size()!=ids.size()) {
                return {
                    result:'CONFLICT',message:'中央来源关联已在本地修改',localId:id
                };
            }
        }
        if(type=='ORG') {
            if(local==null && sql.selectOne("""
select id
from rm_org_t
where tenant_id=#{tenant}
    and dept_code=#{data.code}
    and deleted=0
""")!=null) {
                return {
                    result:'CONFLICT',message:'本地已有同编码机构，请明确关联',localId:id
                };
            }
            if(local==null) {
                sql.update("""
insert into rm_org_t(tenant_id,
                id, name, parent_id, dept_code, org_path, serial_number, nature, status, deleted, create_time,
                update_time)
values(#{tenant},#{id},#{name},#{parentId},#{data.code},#{path},#{serial},#{nature},0,0,utc_timestamp(6),utc_timestamp(6))
""");
            }
            else {
                sql.update("""
update rm_org_t
set name=#{name},parent_id=#{parentId},dept_code=#{data.code},org_path=#{path},
    serial_number=#{serial},nature=#{nature},update_time=utc_timestamp(6)
where tenant_id=#{tenant}
    and id=#{id}
""");
            }
        }
        else if(type=='RESOURCE') {
            if(local==null) {
                sql.update("""
insert into rm_menu_t(tenant_id,
                id, app_id, name, parent_id, sno, url, method_name, resource_type, status, deleted, create_time,
                update_time)
values(#{tenant},#{id},#{localApp},#{name},#{parentId},#{code},#{fields.url},#{data.code},#{resourceType},#{fields.status},0,utc_timestamp(6),utc_timestamp(6))
""");
            }
            else {
                sql.update("""
update rm_menu_t
set name=#{name},parent_id=#{parentId},url=#{fields.url},method_name=#{data.code},
    resource_type=#{resourceType},status=#{fields.status},update_time=utc_timestamp(6)
where tenant_id=#{tenant}
    and id=#{id}
""");
            }
        }
        else if(type=='ROLE') {
            if(local==null) {
                sql.update("""
insert into rm_role_t(tenant_id,
                id, app_id, name, code_num, status, deleted, create_time, update_time)
values(#{tenant},#{id},#{localApp},#{name},#{code},#{fields.status},0,utc_timestamp(6),utc_timestamp(6))
""");
            }
            else {
                sql.update("""
update rm_role_t
set name=#{name},status=#{fields.status},update_time=utc_timestamp(6)
where tenant_id=#{tenant}
    and id=#{id}
""");
            }
            for(link in (prior.resourceRelations||[])) {
                sql.update("""
delete
from rm_role_menu_rela_t
where tenant_id=#{tenant}
    and id=#{link}
""");
            }
            for(rid in localResources) {
                var link=IdUtil.fastSimpleUUID();
                sql.update("""
insert into rm_role_menu_rela_t(tenant_id,
                id, role_id, app_resource_id, extend_id)
values(#{tenant},#{link},#{id},#{rid},'IDAAS')
""");
                next.resourceRelations.add(link);
            }
        }
        else if(type=='SUBJECT') {
            if(local==null) {
                sql.update("""
insert into rm_user_t(tenant_id,
                id, real_name, user_name, phone, email, status, deleted, pwd_status, create_time, update_time)
values(#{tenant},#{id},#{name},#{username},#{fields.phone},#{fields.email},1,0,1,utc_timestamp(6),utc_timestamp(6))
""");
            }
            else {
                sql.update("""
update rm_user_t
set real_name=#{name},user_name=#{username},phone=#{fields.phone},email=#{fields.email},
    update_time=utc_timestamp(6)
where tenant_id=#{tenant}
    and id=#{id}
""");
            }
            if((prior.orgRelations||[]).size()>0) {
                var priorOrgLinks=prior.orgRelations;
                sql.update("""
delete from rm_user_org_rela_t
where tenant_id=#{tenant}
    and id in <foreach collection="priorOrgLinks" item="link" open="(" close=")" separator=",">#{link}</foreach>
""");
            }
            var orgLinks=[];
            for(org in parents) {
                var link=IdUtil.fastSimpleUUID();
                var jobType=org.primary==true?'1':'2';
                orgLinks.add({id:link,orgId:org.id,jobType:jobType});
                next.orgRelations.add(link);
            }
            if(orgLinks.size()>0) {
                sql.update("""
insert into rm_user_org_rela_t(tenant_id,id,user_id,org_id,job_type)
values <foreach collection="orgLinks" item="link" open="(" close=")" separator="),(">
    #{tenant},#{link.id},#{id},#{link.orgId},#{link.jobType}
</foreach>
""");
            }
            if((prior.roleRelations||[]).size()>0) {
                var priorRoleLinks=prior.roleRelations;
                sql.update("""
delete from rm_user_role_rela_t
where tenant_id=#{tenant}
    and id in <foreach collection="priorRoleLinks" item="link" open="(" close=")" separator=",">#{link}</foreach>
""");
            }
            var roleLinks=[];
            for(rid in localRoles) {
                var link=IdUtil.fastSimpleUUID();
                roleLinks.add({id:link,roleId:rid});
                next.roleRelations.add(link);
            }
            if(roleLinks.size()>0) {
                sql.update("""
insert into rm_user_role_rela_t(tenant_id,
                id, user_id, role_id, auth_from, authority_group_id, is_temporary, edit_time)
values <foreach collection="roleLinks" item="link" open="(" close=")" separator="),(">
    #{tenant},#{link.id},#{id},#{link.roleId},9,#{source},0,utc_timestamp(6)
</foreach>
""");
            }
        }
        var snapshot=identityTransport.canonicalJson(next);
        if(map==null) {
            sql.update("""
insert into idaas_receiver_object_t(app_id,
                instance_id, object_type, source_id, local_id, source_version, payload_hash, source_enabled,
                snapshot_json)
values(#{app},#{binding.instance_id},#{type},#{source},#{id},#{event.sourceVersion},#{payloadHash},#{enabled},#{snapshot})
""");
        }
        else {
            sql.update("""
update idaas_receiver_object_t
set source_version=#{event.sourceVersion},payload_hash=#{payloadHash},source_enabled=#{enabled},
    snapshot_json=#{snapshot},updated_time=utc_timestamp(6)
where app_id=#{app}
    and object_type=#{type}
    and source_id=#{source}
""");
        }
        return {
            result:'APPLIED',localId:id,localEnabled:type=='SUBJECT'?local!=null && local.status==0 && local.password!=null:false,sourceEnabled:enabled==1,message:'资料和中央来源关联已提交；本地凭据与停用保持独立'
        };
    };
    // 初始凭据只由人工提交的独立签名事件设置，不进入人员资料快照。
    var applyCredential=(binding,event)=>{
        var data=event.data||{};
        var digest=data.passwordDigest;
        if(event.action!='INITIALIZE' || data.size()!=1 || digest==null || !digest.matches('[0-9A-F]{64}')) {
            exit 400,'初始密码资料不正确';
        }
        var sql=db.normal();
        var app=binding.app_id;
        var tenant=binding.tenant_id;
        var source=event.objectId;
        var map=sql.selectOne("""
select local_id,instance_id,management_state,source_enabled
from idaas_receiver_object_t
where app_id=#{app} and object_type='SUBJECT' and source_id=#{source}
for update
""");
        if(map==null || map.instance_id!=binding.instance_id || map.management_state!='MANAGED' || map.source_enabled!=1) {
            return {result:'CONFLICT',localId:map==null?'':map.local_id,message:'人员来源映射不存在或不可设置初始密码'};
        }
        var localId=map.local_id;
        var user=sql.selectOne("""
select id,password,status,deleted
from rm_user_t
where tenant_id=#{tenant} and id=#{localId}
for update
""");
        if(user==null || user.deleted!=0 || user.password!=null || user.status!=1) {
            return {result:'CONFLICT',localId:localId,message:'本地账号不存在、已启用或已有密码；初始密码不会覆盖现有凭据'};
        }
        var hash=PwdRuleUtil.encryptPwdBySalt(digest);
        sql.update("""
update rm_user_t
set password=#{hash},status=0,update_time=current_timestamp
where tenant_id=#{tenant} and id=#{localId} and deleted=0 and password is null and status=1
""");
        return {result:'APPLIED',localId:localId,localEnabled:true,sourceEnabled:true,message:'初始密码已设置，账号已启用'};
    };
    // 54.接收租户公开界面配置：仅供本接收接口处理对应的下发对象。
    var applyPortal=(binding, event, payloadHash)=>{
        // 只接收 PORTAL 公开界面配置，不触碰 RUNTIME、账号或授权配置。
        var sql=db.normal();
        var app=binding.app_id;
        var tenant=binding.tenant_id;
        var data=event.data||{};
        var names={
            logo:'系统标识',systemName:'系统名称',systemCode:'系统代码',themeColor:'主题颜色',watermarkContent:'水印内容',needLogin:'需要登录',showShoppingCar:'显示购物车',showTagsView:'显示标签页',showWatermark:'显示水印',version:'系统版本'
        };
        if(event.action!='UPSERT' || data.size()==0) {
            exit 400,'应用界面配置报文不能为空';
        }
        for(code in data.keySet()) {
            if(!names.containsKey(code) || data.get(code)==null) {
                exit 400,'报文包含不支持的应用界面配置';
            }
            var value=''+data.get(code);
            if(['needLogin','showShoppingCar','showTagsView','showWatermark'].contains(code)) {
                if(!['true','false'].contains(value)) {
                    exit 400,'界面开关配置不正确';
                }
            }
            else if(code=='logo') {
                if(value.length()>350000 || value!='' && !value.matches('(?s)(https://[^\\s]+|data:image/(png|jpeg|webp|x-icon|svg\\+xml);base64,[A-Za-z0-9+/=]+)')) {
                    exit 400,'应用标识格式不正确';
                }
            }
            else if(code=='themeColor') {
                if(!value.matches('#[0-9a-fA-F]{6}')) {
                    exit 400,'主题颜色格式不正确';
                }
            }
            else if(code=='systemCode') {
                if(!value.matches('[A-Za-z0-9_-]{1,64}')) {
                    exit 400,'系统代码格式不正确';
                }
            }
            else if(value.length()>(code=='systemName'?100:code=='watermarkContent'?200:40)) {
                exit 400,'界面配置长度超出限制';
            }
        }
        var map=sql.selectOne("""
select *
from idaas_receiver_object_t
where app_id=#{app}
    and object_type='PORTAL_CONFIG'
    and source_id=#{app}
    for update
""");
        if(map!=null && (map.instance_id!=binding.instance_id || map.management_state!='MANAGED')) {
            return {
                result:'CONFLICT',localId:app,message:'界面配置来源已变更或移交本地维护'
            };
        }
        if(map!=null && event.sourceVersion<map.source_version) {
            return {
                result:'STALE',localId:app,sourceVersion:map.source_version,message:'接收端已有更新的界面配置'
            };
        }
        if(map!=null && event.sourceVersion==map.source_version) {
            return {
                result:map.payload_hash==payloadHash?'DUPLICATE':'CONFLICT',localId:app,message:map.payload_hash==payloadHash?'相同配置已接收':'同版本界面配置不一致'
            };
        }
        for(code in data.keySet()) {
            var value=''+data.get(code);
            var kind=['needLogin','showShoppingCar','showTagsView','showWatermark'].contains(code)?'BOOLEAN':'STRING';
            var row=sql.selectOne("""
select tid,is_encrypt,config_scope
from sym_config_t
where tenant_id=#{tenant}
    and config_group='PORTAL'
    and config_code=#{code}
    and (is_del=0
         or is_del is null)
    for update
""");
            if(row!=null && (row.is_encrypt==1 || row.config_scope!='PUBLIC')) {
                exit 409,'接收配置与本地非公开配置冲突';
            }
            if(row==null) {
                var id=IdUtil.fastSimpleUUID();
                sql.update("""
insert into sym_config_t(tid,
                config_code, config_name, config_value, is_use, sort_no, is_encrypt, is_del, tenant_id,
                config_group, value_type, config_scope, cache_ttl_seconds, version_no, create_time, update_time)
values(#{id},#{code},#{names.get(code)},#{value},1,0,0,0,#{tenant},'PORTAL',#{kind},'PUBLIC',1800,1,utc_timestamp(6),utc_timestamp(6))
""");
            }
            else {
                sql.update("""
update sym_config_t
set config_value=#{value},is_use=1,value_type=#{kind},config_scope='PUBLIC',version_no=version_no+1,
    update_time=utc_timestamp(6)
where tid=#{row.tid}
    and tenant_id=#{tenant}
    and config_group='PORTAL'
""");
            }
        }
        var snapshot=JSONUtil.toJsonStr(data);
        if(map==null) {
            sql.update("""
insert into idaas_receiver_object_t(app_id,
                instance_id, object_type, source_id, local_id, source_version, payload_hash, source_enabled,
                snapshot_json)
values(#{app},#{binding.instance_id},'PORTAL_CONFIG',#{app},#{app},#{event.sourceVersion},#{payloadHash},1,#{snapshot})
""");
        }
        else {
            sql.update("""
update idaas_receiver_object_t
set source_version=#{event.sourceVersion},payload_hash=#{payloadHash},source_enabled=1,snapshot_json=#{snapshot},
    updated_time=utc_timestamp(6)
where app_id=#{app}
    and object_type='PORTAL_CONFIG'
    and source_id=#{app}
""");
        }
        return {
            result:'APPLIED',localId:app,sourceEnabled:true,message:'公开界面配置已写入租户配置表'
        };
    };
    var payload=body;
    // 请求参数与本接口业务处理。
    // 接收端只接受人工触发的标准下发；回执和随机数用于幂等与防重放。
    var p=payload||{};
    if(p.protocol!='IDAAS/2' || p.keyId==null) {
        exit 400,'只接受IDAAS/2标准报文';
    }
    var route=identityRuntime.receiverRoute(p.keyId);
    if(p.targetAppId!=route.appId || p.receiverInstanceId!=route.instanceId) {
        exit 403,'接收应用或实例不一致';
    }
    // 按已核验的运行绑定进入目标租户库，避免把租户数据写入控制库。
    return identityRuntime.inTenant(route.tenantId,()=>{
        var sql=db.normal();
        // 把读取校验和状态写入放在同一事务，防止并发覆盖。
        var outcome=sql.transaction(()=>{
            var binding=sql.selectOne("""
select *
from idaas_receiver_binding_t
where key_id=#{p.keyId}
    for update
""");
            // 核对接收端密钥、签名、时效和目标实例后才处理下发内容。
            if(binding==null || binding.enabled!=1 || binding.app_id!=route.appId || binding.instance_id!=route.instanceId || binding.tenant_id!=route.tenantId || !identityTransport.verify2(binding.secret_cipher,p.keyId,route.appId,route.instanceId,p.timestamp,p.nonce,p.payload,p.signature)) {
                exit 403,'接收签名、有效期或接入状态无效';
            }
            var event=identityTransport.parseJson(p.payload);
            var hash=DigestUtil.sha256Hex(p.payload);
            if(event.appId!=route.appId || event.receiverInstanceId!=route.instanceId) {
                exit 400,'事件目标应用或实例不正确';
            }
            if(event.eventId==null || !event.eventId.matches('[A-Za-z0-9_-]{8,64}')) {
                exit 400,'事件号格式不正确';
            }
            if(event.objectId==null || !event.objectId.matches('[A-Za-z0-9_.:-]{1,64}') || !['PING','ORG','SUBJECT','RESOURCE','ROLE','PORTAL_CONFIG','CREDENTIAL'].contains(event.objectType)) {
                exit 400,'对象ID或类型不正确';
            }
            if(!['UPSERT','DISABLE','REMOVE','INITIALIZE'].contains(event.action) || event.action=='INITIALIZE' && event.objectType!='CREDENTIAL') {
                exit 400,'事件操作不正确';
            }
            if(identityTransport.canonicalText(p.payload)!=p.payload) {
                exit 400,'事件未使用规范JSON';
            }
            if(!(event.sourceVersion instanceof Number) || event.sourceVersion.longValue()!=event.sourceVersion.doubleValue() || event.sourceVersion<1 || event.sourceVersion>9007199254740991L) {
                exit 400,'来源版本必须是安全正整数';
            }
            var nonceReceipt=sql.selectOne("""
select *
from idaas_receiver_receipt_t
where app_id=#{route.appId}
    and instance_id=#{route.instanceId}
    and nonce=#{p.nonce}
""");
            if(nonceReceipt!=null && (nonceReceipt.payload_hash!=hash || nonceReceipt.event_id!=event.eventId)) {
                exit 409,'随机数不能复用为不同报文';
            }
            var previous=sql.selectOne("""
select *
from idaas_receiver_receipt_t
where app_id=#{route.appId}
    and instance_id=#{route.instanceId}
    and event_id=#{event.eventId}
order by created_time desc
limit 1
""");
            if(previous!=null && (previous.payload_hash!=hash || previous.event_id!=event.eventId)) {
                exit 409,'事件号或随机数不能复用为不同报文';
            }
            var result=null;
            var stateHash=DigestUtil.sha256Hex(identityTransport.canonicalJson({
                type:event.objectType,id:event.objectId,version:event.sourceVersion,action:event.action,data:event.data
            }));
            var prior=previous==null?null:JSONUtil.parseObj(previous.result_json);
            if(prior!=null && ['APPLIED','DUPLICATE'].contains(prior.result)) {
                result={
                    result:'DUPLICATE',localId:prior.localId,message:'已持久接收，重复事件不重新写入'
                };
            }
            else if(event.objectType=='PING') {
                result={
                    result:'APPLIED',localId:'receiver',message:'接收应用、实例与签名核验通过',objectTypes:['ORG','SUBJECT','RESOURCE','ROLE','PORTAL_CONFIG']
                };
            }
            else if(event.objectType=='PORTAL_CONFIG') {
                if(event.objectId!=route.appId) {
                    exit 400,'界面配置只能下发至目标应用';
                }
                result=applyPortal(binding,event,stateHash);
            }
            else if(event.objectType=='CREDENTIAL') {
                result=applyCredential(binding,event);
            }
            else {
                result=apply(binding,event,stateHash);
            }
            var receipt={
                protocol:'IDAAS/2',appId:route.appId,receiverInstanceId:route.instanceId,eventId:event.eventId,objectType:event.objectType,objectId:event.objectId,sourceVersion:result.sourceVersion||event.sourceVersion,payloadHash:hash,stateHash:stateHash||null,result:result.result,localId:result.localId||'',message:result.message,localEnabled:result.localEnabled||false,sourceEnabled:result.sourceEnabled||false
            };
            var signature=identityTransport.receiptSignature(binding.secret_cipher,receipt);
            var encoded=identityTransport.canonicalJson(receipt);
            if(previous==null || previous.nonce!=p.nonce) {
                var id=IdUtil.fastSimpleUUID();
                sql.update("""
insert into idaas_receiver_receipt_t(id,
                app_id, instance_id, event_id, nonce, payload_hash, object_type, source_id, source_version,
                result_json)
values(#{id},#{route.appId},#{route.instanceId},#{event.eventId},#{p.nonce},#{hash},#{event.objectType},#{event.objectId},#{event.sourceVersion},#{encoded})
""");
            }
            return {
                receipt:receipt,receiptSignature:signature
            };
        });
        if(outcome.receipt.objectType=='PORTAL_CONFIG' && ['APPLIED','DUPLICATE'].contains(outcome.receipt.result)) {
            systemConfig.evict('PORTAL');
        }
        return outcome;
    });
}));
