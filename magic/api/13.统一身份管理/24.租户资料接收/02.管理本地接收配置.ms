{
  "properties": {},
  "id": "8b7e7875e5f35f1bbd3b2ba81fea5373",
  "script": null,
  "groupId": "955acd903650573fbe414d610962a487",
  "name": "02.管理本地接收配置",
  "createTime": 1790618621046,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/admin",
  "description": "使用租户本地管理会话维护当前接收实例的地址、凭据或开关；拒绝平台管理会话直接修改租户配置。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：02.管理本地接收配置
// 路由：POST /idaas/receiver/admin
// 业务说明：使用租户本地管理会话维护当前接收实例的地址、凭据或开关；拒绝平台管理会话直接修改租户配置。
import db;
import identityTransport;
import tenantAccounts;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import identityRuntime;
import request;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    var payload=body;
    // 请求参数与本接口业务处理。

    // 本地系统配置权限仅维护此接收实例；不接受平台管理会话。
    var sql=db.normal();
    var p=payload||{};
    if(!StpUtil.isLogin()) {
        exit 401,'请使用本地账号管理接收配置';
    }
    var tenantId=tenantAccounts.session().get('tenantId');
    if(tenantId==null || !tenantAccounts.isLocal(tenantId)) {
        exit 401,'请使用本地账号管理接收配置';
    }
    // 按已核验的运行绑定进入目标租户库，避免把租户数据写入控制库。
    return identityRuntime.inTenant(tenantId,()=>{
        var actor=StpUtil.getLoginId()+'';
        var permissionApp=tenantAccounts.session().get('appId');
        if(sql.selectOne("""
select r.id
from rm_user_role_rela_t u
join rm_role_t r on r.id=u.role_id
and r.tenant_id=u.tenant_id
and r.deleted=0
and r.status=0
join rm_role_menu_rela_t rr on rr.role_id=r.id
and rr.tenant_id=r.tenant_id
join rm_menu_t m on m.id=rr.app_resource_id
and m.tenant_id=rr.tenant_id
and m.deleted=0
and m.url='/setting'
where u.user_id=#{actor}
    and u.tenant_id=#{tenantId}
    and r.app_id=#{permissionApp}
    and (u.end_time is null
         or u.end_time>utc_timestamp(6))
limit 1
""")==null) {
            exit 403,'需要本地系统配置权限';
        }
        if((p.action||'read')=='read') {
            return sql.select("""
select key_id keyId,app_id appId,instance_id instanceId,enabled,version,credential_version credentialVersion
from idaas_receiver_binding_t
where tenant_id=#{tenantId}
""");
        }
        var route=identityRuntime.receiverRoute(p.keyId);
        if(route.tenantId!=tenantId || route.appId!=p.appId || route.instanceId!=p.instanceId) {
            exit 400,'与部署登记的接收实例不一致';
        }
        // 把读取校验和状态写入放在同一事务，防止并发覆盖。
        return sql.transaction(()=>{
            var old=sql.selectOne("""
select *
from idaas_receiver_binding_t
where key_id=#{p.keyId}
    for update
""");
            // 以当前版本校验修改目标，阻止旧页面覆盖已经变更的记录。
            if(old!=null && old.version!=p.version) {
                exit 409,'接收配置已变更';
            }
            var action=p.action;
            var enabled=p.enabled==true?1:0;
            if(old==null && action!='save') {
                exit 404,'请先登记接收配置';
            }
            var recordAction=(operation,source,kind)=>{
                var aid=IdUtil.fastSimpleUUID();
                var nonce=IdUtil.fastSimpleUUID();
                var encoded=identityTransport.canonicalJson({
                    action:operation,actor:actor,objectType:kind,sourceId:source
                });
                var hash=DigestUtil.sha256Hex(encoded);
                sql.update("""
insert into idaas_receiver_receipt_t(id,
                app_id, instance_id, event_id, nonce, payload_hash, object_type, source_id, source_version,
                result_json)
values(#{aid},#{route.appId},#{route.instanceId},#{aid},#{nonce},#{hash},'ADMIN',#{source},1,#{encoded})
""");
            };
            if(action=='inspect-link' || action=='link') {
                if(!['SUBJECT','ORG'].contains(p.objectType) || p.sourceId==null || !p.sourceId.matches('[A-Za-z0-9_.:-]{1,64}') || p.localId==null) {
                    exit 400,'只支持明确关联人员或机构';
                }
                var table=p.objectType=='SUBJECT'?'rm_user_t':'rm_org_t';
                var local=sql.selectOne('select * from '+table+' where tenant_id=#{tenantId} and id=#{p.localId} and deleted=0 for update');
                if(local==null) {
                    exit 404,'本地对象不存在';
                }
                var fields=p.objectType=='SUBJECT'?{
                    real_name:local.real_name,user_name:local.user_name,phone:local.phone,email:local.email
                }
                :{
                    name:local.name,dept_code:local.dept_code,parent_id:local.parent_id,org_path:local.org_path
                };
                var proof=DigestUtil.sha256Hex(identityTransport.canonicalJson(fields));
                if(action=='inspect-link') {
                    return {
                        localId:p.localId,objectType:p.objectType,proof:proof,requiresPrimaryConsent:p.objectType=='SUBJECT'
                    };
                }
                if(p.proof!=proof || p.confirmOwnership!=true || p.objectType=='SUBJECT' && p.replacePrimary!=true) {
                    exit 409,'请核对本地资料并明确同意转交资料和主任职维护';
                }
                if(sql.selectOne("""
select source_id
from idaas_receiver_object_t
where app_id=#{route.appId}
    and ((object_type=#{p.objectType}
          and source_id=#{p.sourceId})
         or (object_type=#{p.objectType}
             and local_id=#{p.localId}))
""")!=null) {
                    exit 409,'中央或本地对象已有来源映射';
                }
                var orgRelations=p.objectType=='SUBJECT'?sql.select("""
select id
from rm_user_org_rela_t
where tenant_id=#{tenantId}
    and user_id=#{p.localId}
    and job_type='1'
""").map(r=>r.id):[];
                var snapshot=identityTransport.canonicalJson({
                    fields:fields,orgRelations:orgRelations,roleRelations:[],resourceRelations:[]
                });
                sql.update("""
insert into idaas_receiver_object_t(app_id,
                instance_id, object_type, source_id, local_id, source_version, payload_hash, source_enabled,
                snapshot_json)
values(#{route.appId},#{route.instanceId},#{p.objectType},#{p.sourceId},#{p.localId},0,#{proof},1,#{snapshot})
""");
                recordAction('link',p.sourceId,p.objectType);
                return {
                    linked:true,localId:p.localId
                };
            }
            if(action=='detach') {
                var object=sql.selectOne("""
select *
from idaas_receiver_object_t
where app_id=#{route.appId}
    and object_type=#{p.objectType}
    and source_id=#{p.sourceId}
    for update
""");
                if(object==null || object.source_version!=p.sourceVersion) {
                    exit 409,'来源对象版本已变更';
                }
                sql.update("""
update idaas_receiver_object_t
set management_state='DETACHED',source_enabled=1,updated_time=utc_timestamp(6)
where app_id=#{route.appId}
    and object_type=#{p.objectType}
    and source_id=#{p.sourceId}
""");
                recordAction('detach',p.sourceId,p.objectType);
                return {
                    detached:true
                };
            }
            if(action!='save') {
                exit 400,'配置操作不正确';
            }
            var cipher=p.secret==null || p.secret==''?old==null?null:old.secret_cipher:identityTransport.encrypt(p.secret);
            if(cipher==null) {
                exit 400,'接收密钥不能为空';
            }
            if(old==null) {
                sql.update("""
insert into idaas_receiver_binding_t(key_id,
                app_id, instance_id, tenant_id, local_permission_app_id, secret_cipher, enabled)
values(#{p.keyId},#{route.appId},#{route.instanceId},#{tenantId},#{permissionApp},#{cipher},#{enabled})
""");
            }
            else {
                var rotation=p.secret!=null && p.secret!=''?1:0;
                sql.update("""
update idaas_receiver_binding_t
set secret_cipher=#{cipher},enabled=#{enabled},credential_version=credential_version+#{rotation},version=version+1,
        updated_time=utc_timestamp(6)
where key_id=#{p.keyId}
""");
            }
            recordAction('save',p.keyId,'CONFIG');
            return {
                keyId:p.keyId,saved:true
            };
        });
    });

}));
