{
  "properties": {},
  "id": "9d76df5fd4ad5e05ba9ff2f10dfa75e4",
  "script": null,
  "groupId": "af2400b9cd0d518c8a01dfc98665b222",
  "name": "01.加载平台工作区",
  "createTime": 1790612165860,
  "updateTime": 1790948254995,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/bootstrap",
  "description": "按当前会话的身份域和应用范围汇总工作区指标、应用和目录数据，返回页面初始化所需资料；不读取租户本地账号。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：01.加载平台工作区
// 路由：GET /idaas/workspace/bootstrap
// 业务说明：按当前会话的身份域和应用范围汇总工作区指标、应用和目录数据，返回页面初始化所需资料；不读取租户本地账号。
import db;
import request;
import platformSession;
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
import identityRuntime;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    // 请求参数与本接口业务处理。

    // 有界读取中央目录及当前应用授权；不查询旧共享账号或应用本地账号。
    var http=request.get();
    var previousSnapshot=http.getAttribute('idaas.bootstrap.permissionSnapshot');
    http.setAttribute('idaas.bootstrap.permissionSnapshot',{
        operatorId:platformSession.id(),context:null
    });
    try {
        // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
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
