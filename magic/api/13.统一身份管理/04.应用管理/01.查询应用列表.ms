{
  "properties": {},
  "id": "1c1cbd2c3b35549e9ded9eb532e5db53",
  "script": null,
  "groupId": "8e1839fbe6a55ce7aafaae3dcf2d15b4",
  "name": "01.查询应用列表",
  "createTime": 1790612165860,
  "updateTime": 1791205192024,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "description": "读取符合请求筛选条件的应用，应用权限及身份域过滤后返回可展示字段；不发生写入。限定当前身份域与可管理应用范围；应用主档、运行绑定及界面配置分别持久化，绝不写租户本地登录凭据。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：01.查询应用列表
// 路由：GET /idaas/applications/list
// 业务说明：读取符合请求筛选条件的应用，应用权限及身份域过滤后返回可展示字段；不发生写入。限定当前身份域与可管理应用范围；应用主档、运行绑定及界面配置分别持久化，绝不写租户本地登录凭据。
import db;
import '@/idaas/rebuild-projections' as rebuild;
import '@/idaas/guard' as guard;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import cn.hutool.json.JSONUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.NumberUtil;
import identityRuntime;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    // 请求参数与本接口业务处理。
    var operation = 'list';
    var payload = param||{};

    // 应用主档、运行绑定与界面配置分开保存；界面配置由应用配置表维护。
    // 行业应用登记。修改IAM状态不修改运行租户，不直写应用库。
    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
    var r=p.record||p;
    var domain=r.domain||p.domain||'workforce';
    var target=operation=='list'?null:operation=='inspect'?p.appId:(r.id||null);
    if(operation=='list') {
        import '@/idaas/session' as sessionInfo;
        var session=sessionInfo();
        var ids=session.allowedAppIds;
        if(ids.size()==0) {
            // 核验会话的功能权限及对象范围，超出范围的请求在读取或写入前拒绝。
            guard('apps:read',null,domain);
            return [];
        }
        guard('apps:read',ids[0],domain);
        var configRows=sql.select("""
select app_id,config_code,config_value,value_type
from iam_application_config_t
where config_group='PORTAL'
    and is_del=0
    and app_id in (#{ids})
""");
        return sql.select("""
select a.*,a.tid as id,date_format(a.created_time, '%Y-%m-%dT%H:%i:%sZ') as created_utc,
       date_format(a.updated_time, '%Y-%m-%dT%H:%i:%sZ') as updated_utc,g.name as group_name,o.display_name as owner_name,rt.runtime_tenant_id,
       date_format(rt.verified_time, '%Y-%m-%dT%H:%i:%sZ') as verified_utc,rt.binding_status,rt.local_permission_app_id,rt.backend_code,rt.environment
from iam_application_t a
left join iam_application_group_t g on g.tid=a.group_id
and g.is_del=0
left join iam_operator_profile_v o on o.tid=a.owner_operator_id
and o.is_del=0
left join iam_application_runtime_t rt on rt.app_id=a.tid
where a.kind='BUSINESS'
    and a.is_del=0
    and a.tid in (#{ids})
order by a.sort_no,a.code
""").map(a=>{
            var portal=JSONUtil.createObj();
            for(config in configRows) {
                if(config.app_id==a.id) {
                    portal.put(config.config_code,config.value_type=='BOOLEAN'?config.config_value=='true':config.config_value);
                }
            }
            return {
                id:a.id,domain:domain,name:a.name,code:a.code,status:a.status=='ACTIVE'?'enabled':a.status=='DRAFT'?'pending':'disabled',
                createdAt:a.created_utc,updatedAt:a.updated_utc,version:a.version,sortNo:a.sort_no,group:a.group_name||'未分组',groupId:a.group_id,
                description:a.description||'',homepage:a.homepage||'',owner:a.owner_name||'',ownerOperatorId:a.owner_operator_id,
                protocol:a.auth_mode=='FEDERATED'?'OIDC':'本地账号',environment:a.environment||'',runtimeTenantId:a.runtime_tenant_id,bindingStatus:a.binding_status||'PENDING',
                bindingVerifiedAt:a.verified_utc,localPermissionAppId:a.local_permission_app_id,authMode:a.auth_mode,provisioningMode:a.provisioning_mode,
                redirectUris:'',logoutUri:'',clientId:'',clientType:'public',mfa:false,accessTtl:0,scopes:[],secretVersion:0,
                logoUrl:portal.logo||'',portalConfig:portal
            };
        });
    }
    var permission=operation=='bind'?'apps:bind':'apps:write';
    var ctx=guard(permission,p.creating==true?null:target,domain);
    if(operation=='save' && r.sortPosition!=null) {
        guard('apps:write',null,domain);
    }
    var inspection=null;
    // 在事务内完成校验与写入；重复请求及并发版本冲突由共享事务规则处理。
    return mutate(ctx,'applications:'+operation,p,()=>{
        var existing=target==null?null:sql.selectOne("""
select *,tid as id
from iam_application_t
where tid=#{target}
    and is_del=0
    for update
""");
        if(existing!=null && existing.kind!='BUSINESS') {
            exit 403,'平台内部应用不在此维护';
        }
        // 以当前版本校验修改目标，阻止旧页面覆盖已经变更的记录。
        if(existing!=null && r.version!=existing.version) {
            exit 409,'应用已被修改，请刷新后重试';
        }
        if(existing==null && p.creating!=true) {
            exit 404,'应用不存在';
        }
        if(operation=='bind') {
            var tenantId=(p.runtimeTenantId||'').trim();
            var tid=sql.selectOne("""
select tid
from sym_tenant_t
where tid=#{tenantId}
    and is_del=0
    and status=1
""");
            if(tid==null) {
                exit 400,'运行租户不存在或已停用';
            }
            var linked=sql.selectOne("""
select *,tid as id
from iam_application_runtime_t
where runtime_tenant_id=#{tenantId}
    and app_id<>#{target}
""");
            if(linked!=null) {
                exit 409,'该运行租户已绑定其他应用';
            }
            var current=sql.selectOne("""
select *,tid as id
from iam_application_runtime_t
where app_id=#{target}
""");
            if(current!=null && current.runtime_tenant_id!=tenantId) {
                exit 409,'已有绑定需要单独迁移，不能直接切换业务库';
            }
            var rid=current==null?IdUtil.fastSimpleUUID():current.id;
            var localApp=(p.localPermissionAppId||'').trim();
            if(localApp=='' || localApp.length()>64) {
                exit 400,'请填写本地权限应用标识';
            }
            if(current==null) {
                sql.update("""
insert into iam_application_runtime_t(tid,
                app_id, runtime_tenant_id, local_permission_app_id, binding_status, verified_time)
values(#{rid},#{target},#{tenantId},#{localApp},'VERIFIED',utc_timestamp(6))
""");
            }
            else {
                sql.update("""
update iam_application_runtime_t
set binding_status='VERIFIED',local_permission_app_id=#{localApp}, verified_time=utc_timestamp(6),version=version+1,
        updated_time=utc_timestamp(6)
where tid=#{rid}
""");
            }
            // 记录操作主体、目标及结果，供审计与故障追踪。
            audit(ctx,'SUCCESS','applications:bind',target,null,{
                id:target
            });
            sql.update("""
update iam_application_t
set version=version+1,updated_time=utc_timestamp(6),updated_by=#{ctx.userId}
where tid=#{target}
""");
            return {
                id:target,bindingStatus:'VERIFIED',dataSourceKey:inspection.dataSourceKey,schema:inspection.schema
            };
        }
        else if(operation=='remove') {
            var binding=sql.selectOne("""
select tid as id
from iam_application_runtime_t
where app_id=#{target}
""");
            if(binding!=null) {
                exit 409,'应用仍有运行绑定，请使用停用而非删除';
            }
            sql.update("""
update iam_application_t
set is_del=1,status='ARCHIVED',version=version+1,updated_time=utc_timestamp(6)
where tid=#{target}
""");
        }
        else if(operation=='save') {
            var id=existing==null?IdUtil.fastSimpleUUID():existing.id;
            var name=(r.name||'').trim();
            var code=(r.code||'').trim();
            var status=r.status=='enabled'?'ACTIVE':r.status=='disabled'?'DISABLED':'DRAFT';
            var description=r.description||'';
            var homepage=r.homepage||'';
            if(name=='' || name.length()>100 || !code.matches('[A-Za-z][A-Za-z0-9_-]{1,63}') || description.length()>1000
            || (homepage!='' && !homepage.matches('https?://[^\\s]+'))) {
                exit 400,'请填写有效的应用名称、编码和首页';
            }
            if(existing!=null && code!=existing.code) {
                exit 400,'应用编码登记后保持稳定';
            }
            var duplicate=sql.selectOne("""
select tid as id
from iam_application_t
where code=#{code}
    and tid<>#{id}
""");
            if(duplicate!=null) {
                exit 409,'应用编码已使用';
            }
            var groupId=r.groupId||null;
            if(r.group!=null && r.group!='未分组') {
                var group=sql.selectOne("""
select tid as id
from iam_application_group_t
where name=#{r.group}
    and is_del=0
""");
                groupId=group==null?null:group.id;
                if(groupId==null) {
                    exit 400,'请先在应用分组中登记该分组';
                }
            }
            if(groupId!=null && sql.selectOne("""
select tid as id
from iam_application_group_t
where tid=#{groupId}
    and is_del=0
""")==null) {
                exit 400,'应用分组不存在';
            }
            var ownerId=r.ownerOperatorId||null;
            if(r.owner!=null && r.owner!='') {
                var owners=sql.select("""
select tid as id
from iam_operator_profile_v
where display_name=#{r.owner}
    and is_del=0
    and status='ACTIVE'
""");
                if(owners.size()!=1) {
                    exit 400,'负责人必须对应唯一有效的平台操作账号';
                }
                ownerId=owners[0].id;
            }
            if(ownerId!=null && sql.selectOne("""
select tid as id
from iam_operator_t
where tid=#{ownerId}
    and is_del=0
    and status='ACTIVE'
""")==null) {
                exit 400,'负责人不存在或已停用';
            }
            var portal=r.portalConfig;
            if(portal!=null) {
                var allowed=['logo','systemName','systemCode','themeColor','watermarkContent','needLogin','showShoppingCar','showTagsView','showWatermark','version'];
                for(key in portal.keySet()) {
                    if(!allowed.contains(key) || portal.get(key)==null) {
                        exit 400,'应用界面配置包含不支持的字段';
                    }
                    var value=''+portal.get(key);
                    if(['needLogin','showShoppingCar','showTagsView','showWatermark'].contains(key)) {
                        if(!['true','false'].contains(value)) {
                            exit 400,'界面开关配置不正确';
                        }
                    }
                    else if(key=='logo') {
                        if(value.length()>350000 || value!='' && !value.matches('(?s)(https://[^\\s]+|data:image/(png|jpeg|webp|x-icon|svg\\+xml);base64,[A-Za-z0-9+/=]+)')) {
                            exit 400,'应用标识须为HTTPS图片地址或支持的图片文件';
                        }
                    }
                    else if(key=='themeColor') {
                        if(!value.matches('#[0-9a-fA-F]{6}')) {
                            exit 400,'主题颜色须为六位十六进制颜色';
                        }
                    }
                    else if(key=='systemCode') {
                        if(!value.matches('[A-Za-z0-9_一-龥-]{1,64}')) {
                            exit 400,'租户系统代码不正确';
                        }
                    }
                    else if(value.length()>(key=='systemName'?100:key=='watermarkContent'?200:40)) {
                        exit 400,'应用界面配置长度超出限制';
                    }
                }
            }
            if(existing==null) {
                sql.update("""
insert into iam_application_t(tid,
                code, name, kind, group_id, description, owner_operator_id, homepage, identity_domains, status,
                created_by, updated_by)
values(#{id},#{code},#{name},'BUSINESS',#{groupId},#{description},#{ownerId},#{homepage},'["workforce"]',#{status},#{ctx.userId},#{ctx.userId})
""");
            }
            else {
                sql.update("""
update iam_application_t
set name=#{name},group_id=#{groupId},description=#{description},
    owner_operator_id=#{ownerId}, homepage=#{homepage},status=#{status},version=version+1,
        updated_by=#{ctx.userId},
        updated_time=utc_timestamp(6)
where tid=#{id}
""");
            }
            target=id;
            if(portal!=null) {
                var saved=sql.select("""
select tid as id,config_code,config_value,value_type,is_del
from iam_application_config_t
where app_id=#{id}
    and config_group='PORTAL'
    for update
""");
                for(item in saved) {
                    var retained=false;
                    for(key in portal.keySet()) {
                        if(key==item.config_code) {
                            retained=true;
                        }
                    }
                    if(!retained && item.is_del==0) {
                        sql.update("""
update iam_application_config_t
set is_del=1,version=version+1,updated_by=#{ctx.userId},updated_time=utc_timestamp(6)
where tid=#{item.id}
""");
                    }
                }
                for(key in portal.keySet()) {
                    var value=''+portal.get(key);
                    var valueType=['needLogin','showShoppingCar','showTagsView','showWatermark'].contains(key)?'BOOLEAN':'STRING';
                    var previous=sql.selectOne("""
select tid as id,config_value,value_type,is_del
from iam_application_config_t
where app_id=#{id}
    and config_group='PORTAL'
    and config_code=#{key}
    for update
""");
                    if(previous==null) {
                        var configId=IdUtil.fastSimpleUUID();
                        sql.update("""
insert into iam_application_config_t(tid,
                app_id, config_group, config_code, config_value, value_type, created_by, updated_by)
values(#{configId},#{id},'PORTAL',#{key},#{value},#{valueType},#{ctx.userId},#{ctx.userId})
""");
                    }
                    else if(previous.is_del!=0 || previous.config_value!=value || previous.value_type!=valueType) {
                        sql.update("""
update iam_application_config_t
set config_value=#{value},value_type=#{valueType},is_del=0, version=version+1,
        updated_by=#{ctx.userId},
        updated_time=utc_timestamp(6)
where tid=#{previous.id}
""");
                    }
                }
            }
            if(r.sortPosition!=null) {
                if(!(''+r.sortPosition).matches('[1-9][0-9]{0,2}')) {
                    exit 400,'显示位置格式不正确';
                }
                var position=NumberUtil.parseInt(''+r.sortPosition);
                var ordered=sql.select("""
select tid as id,sort_no
from iam_application_t
where kind='BUSINESS'
    and is_del=0
    and json_contains(identity_domains, json_quote(#{domain}))
order by sort_no,code
for update
""");
                if(position<1 || position>ordered.size() || position>200) {
                    exit 400,'显示位置不在应用清单范围内';
                }
                var next=[];
                var index=1;
                for(row in ordered) {
                    if(row.id!=id) {
                        if(index==position) {
                            next.add(id);
                        }
                        next.add(row.id);
                        index=index+1;
                    }
                }
                if(next.size()<ordered.size()) {
                    next.add(id);
                }
                var rank=0;
                for(appId in next) {
                    rank=rank+1;
                    var sortNo=rank*10;
                    var previous=ordered.filter(row=>row.id==appId)[0];
                    if(previous.sort_no!=sortNo) {
                        sql.update("""
update iam_application_t
set sort_no=#{sortNo},version=version+1,updated_by=#{ctx.userId},
        updated_time=utc_timestamp(6)
where tid=#{appId}
""");
                    }
                }
            }
        }
        else {
            exit 400,'操作不正确';
        }
        if(sql.selectOne("""
select count(*) n
from information_schema.tables
where table_schema=database()
    and table_name='iam_access_grant_t'
""").n>0) {
            rebuild(target,'workforce');
            rebuild(target,'public');
        }
        audit(ctx,'SUCCESS','applications:'+operation,target,null,{
            id:target
        });
        return {
            id:target
        };
    });

}));
