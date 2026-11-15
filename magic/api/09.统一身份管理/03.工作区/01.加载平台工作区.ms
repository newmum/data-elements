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
import tableSort;
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
import '@/idaas/effective-access-batch' as effectiveAccessBatch;
import '@/idaas/app-list-context' as accessListContext;
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
        var query=(key,fallback)=>{var values=request.getValues(key);return values==null || values.size()==0?fallback:values[0];};
        var table=query('table',null);
        if(table!=null) {
            var p={domain:session.domain,appId:query('appId',null),sortField:query('sortField',null),sortDir:query('sortDir',null)};
            if(table=='effectiveAccess') {
                var targetApp=p.appId;
                var subjectId=query('subjectId',null);
                var orgId=query('orgId',null);
                var domain=session.domain;
                var ctx=accessListContext('grants:read',targetApp,domain);
                var scopes=[];
                for(pair in ctx.pairs) {if(pair.codes.contains('grants:read')) {scopes.addAll(pair.scopes.filter(scope=>scope.identity_domain==domain));}}
                var all=scopes.filter(scope=>scope.scope_kind=='ALL').size()>0;
                var allowedApps=scopes.filter(scope=>scope.scope_kind=='APPLICATION').map(scope=>scope.app_id).distinct();
                var page=Math.max(1,query('page','1')::int);
                var size=Math.min(100,Math.max(1,query('size','20')::int));
                if(!all && allowedApps.size()==0) {return {list:[],total:0,page:page,size:size};}
                var search=query('q','').trim();
                if(search.length()>100) {exit 400,'搜索内容最多100字';}
                var scopeSql=all?'':' AND a.tid IN (#{allowedApps})';
                // Match the batch effective-access rules before counting, ordering or paging.
                var validAppointment=" ap.is_del=0 AND ap.status='ACTIVE' AND ap.valid_from<=utc_timestamp(6) AND (ap.valid_until IS NULL OR ap.valid_until>utc_timestamp(6)) AND po.identity_domain=#{domain} AND po.is_del=0 AND po.status='ACTIVE' AND NOT EXISTS (SELECT 1 FROM iam_org_t ancestor WHERE locate(concat('/',ancestor.tid,'/'),po.path)>0 AND (ancestor.is_del=1 OR ancestor.status!='ACTIVE'))";
                var from=" FROM iam_subject_t s JOIN iam_application_t a ON a.kind='BUSINESS' AND a.is_del=0 AND a.status='ACTIVE' AND JSON_CONTAINS(a.identity_domains,JSON_QUOTE(#{domain})) WHERE s.identity_domain=#{domain} AND s.is_del=0 AND s.status='ACTIVE' AND (#{targetApp} IS NULL OR a.tid=#{targetApp}) AND (#{subjectId} IS NULL OR s.tid=#{subjectId})"+scopeSql+" AND (#{orgId} IS NULL OR EXISTS (SELECT 1 FROM iam_appointment_t ap JOIN iam_org_t po ON po.tid=ap.org_id JOIN iam_org_t chosen ON chosen.tid=#{orgId} AND chosen.identity_domain=#{domain} AND chosen.is_del=0 WHERE ap.subject_id=s.tid AND "+validAppointment+" AND (po.tid=chosen.tid OR po.path LIKE concat(chosen.path,'%')))) AND (#{search}='' OR locate(lower(#{search}),lower(concat_ws(' ',s.name,s.account_hint,a.name)))>0) AND EXISTS (SELECT 1 FROM iam_access_grant_t g LEFT JOIN iam_org_t go ON go.tid=g.org_id WHERE g.app_id=a.tid AND g.identity_domain=#{domain} AND g.is_del=0 AND g.status='ACTIVE' AND g.valid_from<=utc_timestamp(6) AND (g.valid_until IS NULL OR g.valid_until>utc_timestamp(6)) AND (g.principal_type='SUBJECT' AND g.subject_id=s.tid OR g.principal_type='GROUP' AND EXISTS (SELECT 1 FROM iam_group_member_t m JOIN iam_permission_group_t pg ON pg.tid=m.group_id AND pg.identity_domain=#{domain} AND pg.is_del=0 AND pg.status='ACTIVE' WHERE m.subject_id=s.tid AND m.group_id=g.group_id AND m.status='ACTIVE' AND m.valid_from<=utc_timestamp(6) AND (m.valid_until IS NULL OR m.valid_until>utc_timestamp(6))) OR g.principal_type='ORG' AND go.status='ACTIVE' AND go.is_del=0 AND EXISTS (SELECT 1 FROM iam_appointment_t ap JOIN iam_org_t po ON po.tid=ap.org_id WHERE ap.subject_id=s.tid AND "+validAppointment+" AND (po.tid=g.org_id OR g.include_children=1 AND po.path LIKE concat(go.path,'%')))))";
                var fields={name:subjectId==null?'s.name':'a.name',userName:'s.name',appName:'a.name',account:'s.account_hint'};
                var sortColumn=tableSort.field(p,fields,'name');
                var direction=tableSort.direction(p);
                var offset=(page-1)*size;
                var total=sql.selectInt('SELECT count(*)'+from);
                var rows=sql.select('SELECT s.tid user_id,s.name user_name,s.account_hint,s.version,s.created_time,s.updated_time,a.tid app_id,a.name app_name'+from+' ORDER BY '+sortColumn+' '+direction+',s.tid ASC,a.tid ASC LIMIT #{size} OFFSET #{offset}');
                var access=effectiveAccessBatch(rows.map(r=>r.app_id).distinct(),domain,rows.map(r=>r.user_id).distinct());
                return {list:rows.map(r=>{var matches=(access.get(r.user_id)||[]).filter(v=>v.appId==r.app_id);if(matches.size()!=1) {exit 409,'授权状态已变化，请刷新列表';} var value=matches[0];return {id:r.user_id+':'+r.app_id,userId:r.user_id,userName:r.user_name,appId:r.app_id,appName:r.app_name,name:subjectId==null?r.user_name:r.app_name,account:r.account_hint||'',domain:domain,status:'enabled',version:r.version,createdAt:r.created_time,updatedAt:r.updated_time,roleIds:value.roleIds,resourceIds:value.resourceIds,sources:value.sources};}),total:total,page:page,size:size};
            }
            if(table=='legalEntities') {
                p.putAll({paged:true,page:query('page','1'),size:query('size','20'),q:query('q',''),status:query('status','all'),mode:query('mode','entities'),id:query('id',null)});
                return publicEntities('list',p);
            }
            if(table=='grants') {
                p.putAll({paged:true,page:query('page','1'),size:query('size','20'),q:query('q',''),status:query('status','all'),subjectId:query('subjectId',null),principalType:query('principalType',null)});
                return businessGrants('list',p);
            }
            var rows=[];
            if(table=='apps') {rows=applications('list',p);}
            else if(table=='roles' || table=='resources') {rows=businessCatalog(table,'list',p);}
            else if(table=='groups') {rows=permissionGroups('list',p);}
            else if(table=='catalog') {
                var category=query('category','');
                if(category=='line' || category=='dictionary') {rows=directoryConfig('dictionaries','list',p).filter(x=>x.category==category);}
                else if(category=='extension') {rows=directoryConfig('field-definitions','list',p);}
                else if(category=='app-group') {
                    if(!session.permissions.contains('apps:read')) {exit 403,'没有应用目录读取权限';}
                    rows=sql.select("""
SELECT tid as id,name,code,version,description,date_format(created_time,'%Y-%m-%dT%H:%i:%sZ') createdAt,date_format(updated_time,'%Y-%m-%dT%H:%i:%sZ') updatedAt
FROM iam_application_group_t WHERE is_del=0 ORDER BY tid LIMIT 1001
""").map(x=>({id:x.id,name:x.name,code:x.code,version:x.version,description:x.description||'',createdAt:x.createdAt,updatedAt:x.updatedAt,
                        domain:session.domain,status:'enabled',category:'app-group',value:'',permissions:[],memberIds:[],appId:'',orgId:'',userId:'',parentId:'',subjectType:''}));
                } else {exit 400,'不支持的配置类别';}
            } else {exit 400,'不支持的工作区列表';}
            var search=query('q','').trim().toLowerCase();
            var status=query('status','all');
            rows=rows.filter(x=>(status=='all' || x.status==status) && (search=='' || (x.name+' '+(x.code||'')+' '+(x.description||'')).toLowerCase().contains(search)));
            rows=tableSort.rows(rows,p,{name:'name',createdAt:'createdAt',updatedAt:'updatedAt',resourceCount:'resourceCount',memberCount:'memberCount',sortNo:'sortNo'},'name','id');
            var page=Math.max(1,query('page','1')::int);
            var size=Math.min(100,Math.max(1,query('size','20')::int));
            var start=Math.min(rows.size(),(page-1)*size);
            return {list:rows.subList(start,Math.min(rows.size(),start+size)),total:rows.size(),page:page,size:size};
        }

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
            var accessAppIds=database.apps.filter(app=>(session.appPermissions.get(app.id)||[]).contains('grants:read')).map(app=>app.id);
            database.authoritativeAccess=effectiveAccessBatch(accessAppIds,session.domain,database.users.map(user=>user.id));
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
