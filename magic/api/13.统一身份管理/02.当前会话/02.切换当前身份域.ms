{
  "properties": {},
  "id": "e52368d8d58458c096bd3323746f85b9",
  "script": null,
  "groupId": "38dfb5ef9c2052b0acee3e477054544d",
  "name": "02.切换当前身份域",
  "createTime": 1790635007936,
  "updateTime": 1790948254995,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/domain",
  "description": "校验当前账号具备目标身份域的管理范围，切换会话使用的域并返回更新后的上下文；不改变账号本身权限。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：02.切换当前身份域
// 路由：POST /idaas/session/domain
// 业务说明：校验当前账号具备目标身份域的管理范围，切换会话使用的域并返回更新后的上下文；不改变账号本身权限。
import platformSession;
import db;
import request;
import '@/idaas/public-entities' as publicEntities;
import '@/idaas/session' as sessionInfo;
import '@/idaas/applications' as applications;
import '@/idaas/settings' as settings;
import '@/idaas/directory' as directory;
import '@/idaas/directory-config' as directoryConfig;
import '@/idaas/business-catalog' as businessCatalog;
import '@/idaas/business-grants' as businessGrants;
import '@/idaas/permission-groups' as permissionGroups;
import '@/idaas/effective-access' as effectiveAccess;
import cn.hutool.json.JSONUtil;
import '@/idaas/context' as context;
import '@/idaas/audit' as audit;
import identityRuntime;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    // 请求参数与本接口业务处理。
    var payload = body;

    var p=payload||{};
    var ctx=context();
    var domain=p.domain;
    if(!['workforce','public'].contains(domain) || ctx.account.must_change_password==1) {
        exit 403,'当前账号不能切换身份域';
    }
    if(ctx.pairs.filter(pair=>pair.scopes.filter(s=>s.identity_domain==domain).size()>0).size()==0) {
        exit 403,'当前账号没有目标身份域的管理范围';
    }
    platformSession.session().set('identityDomain',domain);
    ctx.requiredDomain=domain;
    // 记录操作主体、目标及结果，供审计与故障追踪。
    audit(ctx,'SUCCESS','profile:domain',ctx.userId,null,{
        domain:domain
    });
    // 切换成功后在本接口重建目标身份域的工作区数据。
    // 有界读取中央目录及当前应用授权；不查询旧共享账号或应用本地账号。
    var http=request.get();
    var previousSnapshot=http.getAttribute('idaas.bootstrap.permissionSnapshot');
    http.setAttribute('idaas.bootstrap.permissionSnapshot',{
        operatorId:platformSession.id(),context:null
    });
    try {
        var sql=db.normal();
        var session=sessionInfo();
        var database={
            schema:1,users:[],orgs:[],apps:[],roles:[],resources:[],groups:[],grants:[],syncConfigs:[],tasks:[],logs:[],catalog:[],legalEntities:[],settings:{
                workforce:{},public:{}
            }
        };
        var settingVersions={};
        if(session.capabilities.contains('apps')) {
            database.apps=applications('list',{
                domain:session.domain
            });
        }
        if(session.capabilities.contains('users')) {
            database.users=directory('subjects','list',{
                domain:session.domain,size:250
            }).list;
        }
        if(session.capabilities.contains('orgs')) {
            database.orgs=directory('orgs','list',{
                domain:session.domain,size:250
            }).list;
        }
        if(!session.mustChangePassword && session.permissions.contains('settings:read')) {
            for(r in settings('read',{})) {
                database.settings.get(session.domain).putAll(r.value);
                settingVersions[r.category]=r.version;
            }
        }
        if(session.permissions.contains('apps:read')) {
            var groups=sql.select("""
select *,tid as id,date_format(created_time, '%Y-%m-%dT%H:%i:%sZ') as created_utc,
       date_format(updated_time, '%Y-%m-%dT%H:%i:%sZ') as updated_utc
from iam_application_group_t
where is_del=0
order by sort_no,code
""");
            database.catalog=groups.map(g=>({
                id:g.id,domain:'workforce',name:g.name,code:g.code,category:'app-group',description:'',status:'enabled',version:g.version,
                createdAt:g.created_utc,updatedAt:g.updated_utc,permissions:[],memberIds:[],appId:'',orgId:'',userId:'',parentId:'',value:'',expiresAt:'',subjectType:''
            }));
        }
        if(session.globalPermissions.contains('directory:read')) {
            database.catalog.addAll(directoryConfig('dictionaries','list',{
                domain:session.domain
            }));
            database.catalog.addAll(directoryConfig('field-definitions','list',{
                domain:session.domain
            }));
        }
        if(session.capabilities.contains('roles')) {
            database.roles=businessCatalog('roles','list',{
                domain:session.domain
            });
        }
        if(session.capabilities.contains('resources')) {
            database.resources=businessCatalog('resources','list',{
                domain:session.domain
            });
        }
        if(session.capabilities.contains('grants')) {
            database.grants=businessGrants('list',{
                domain:session.domain
            });
        }
        if(session.globalPermissions.contains('grants:read')) {
            database.groups=permissionGroups('list',{
                domain:session.domain
            });
        }
        if(session.capabilities.contains('grants')) {
            database.authoritativeAccess={};
            for(user in database.users) {
                var access=[];
                for(app in database.apps) {
                    if(!(session.appPermissions.get(app.id)||[]).contains('grants:read')) {
                        continue;
                    }
                    var value=effectiveAccess(user.id,app.id,session.domain);
                    if(value.hasAccess) {
                        access.add(value);
                    }
                }
                database.authoritativeAccess.put(user.id,access);
            }
        }
        if(session.domain=='public' && session.capabilities.contains('public-identity')) {
            database.legalEntities=publicEntities('list',{});
        }
        if(session.capabilities.contains('audit')) {
            database.logs=sql.select("""
select *,tid as id,date_format(event_time, '%Y-%m-%dT%H:%i:%sZ') as event_utc
from iam_audit_event_t
order by event_time desc
limit 200
""").map(e=>({
                id:e.id,domain:e.identity_domain||'workforce',name:e.action,status:'enabled',version:1,
                createdAt:e.event_utc,updatedAt:e.event_utc,type:e.event_type=='AUTH'?'login':e.event_type=='API'?'api':'operation',actor:e.actor_id||'未知账号',module:'统一身份管理',
                action:e.action,target:e.object_id||'',result:e.result=='SUCCESS'?'成功':e.result=='DENIED'?'拒绝':'失败',ip:e.ip||'',appId:e.app_id||'',traceId:e.trace_id,
                before:'',after:e.snapshot_json||'',httpStatus:e.http_status,durationMs:e.duration_ms
            }));
        }
        return {
            session:session,database:database,capabilities:session.capabilities,settingVersions:settingVersions,directoryPageSize:250,auditPageSize:200
        };
    }
    finally {
        http.setAttribute('idaas.bootstrap.permissionSnapshot',previousSnapshot);
    }
}));
