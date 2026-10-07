{
  "properties": {},
  "id": "f1b2b970b6195bf8aad27cdded34304d",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "05.读取平台会话",
  "createTime": 1790612165860,
  "updateTime": 1791205192024,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/session",
  "description": "核验平台令牌并读取当前操作账号与权限版本；只服务平台控制台身份，不接受租户本地会话。",
  "parameters": [],
  "returnType": null,
  "mappingPath": null
}
================================
// 共享函数：05.读取平台会话
// 业务说明：核验平台令牌并读取当前操作账号与权限版本；只服务平台控制台身份，不接受租户本地会话。
// 函数输入：无显式参数；从当前 Magic 执行上下文读取会话或请求信息。
// 返回与副作用：核验平台令牌并读取当前操作账号与权限版本；只服务平台控制台身份，不接受租户本地会话。
import db;
import platformSession;
import '@/idaas/context' as context;
import '@/idaas/directory-scope' as directoryScope;

// 基于当前平台会话重算身份域和功能范围，不读取租户本地账号。
var ctx=context();
var domain=platformSession.session().get('identityDomain')||'workforce';
var permissions=[];
for(pair in ctx.pairs) {
    if(pair.scopes.filter(s=>s.identity_domain==domain).size()>0) {
        for(code in pair.codes) {
            if(!permissions.contains(code)) {
                permissions.add(code);
            }
        }
    }
}
var sql=db.normal();
// 先读取控制库应用主档，再按同一角色分配的功能和范围筛选可见应用。
var apps=sql.select("""
select tid as id
from iam_application_t
where kind='BUSINESS'
    and is_del=0
""");
var allowed=[];
var writable=[];
var bindable=[];
var assignable=[];
var capabilities=['workspace','profile'];
var editable=[];
var appPermissions={};
for(a in apps) {
    for(pair in ctx.pairs) {
        for(s in pair.scopes) {
            if(s.identity_domain==domain && (s.scope_kind=='ALL' || (s.scope_kind=='APPLICATION' && s.app_id==a.id))) {
                if(!appPermissions.containsKey(a.id)) {
                    appPermissions.put(a.id,[]);
                }
                for(code in pair.codes) {
                    if(!appPermissions.get(a.id).contains(code)) {
                        appPermissions.get(a.id).add(code);
                    }
                }
                if(pair.codes.contains('apps:write') && !writable.contains(a.id)) {
                    writable.add(a.id);
                }
                if(pair.codes.contains('apps:bind') && !bindable.contains(a.id)) {
                    bindable.add(a.id);
                }
                if(pair.codes.contains('assignments:write') && !assignable.contains(a.id)) {
                    assignable.add(a.id);
                }
            }
        }
    }
}
for(a in apps) {
    for(pair in ctx.pairs) {
        if(!pair.codes.contains('apps:read')) {
            continue;
        }
        for(s in pair.scopes) {
            if(s.identity_domain==domain && (s.scope_kind=='ALL' || (s.scope_kind=='APPLICATION' && s.app_id==a.id))) {
                if(!allowed.contains(a.id)) {
                    allowed.add(a.id);
                }
            }
        }
    }
}
var global=[];
for(pair in ctx.pairs) {
    if(pair.scopes.filter(s=>s.identity_domain==domain && s.scope_kind=='ALL').size()>0) {
        for(code in pair.codes) {
            if(!global.contains(code)) {
                global.add(code);
            }
        }
    }
}
if(permissions.contains('apps:read')) {
    capabilities.add('apps');
}
if(permissions.contains('users:read')) {
    capabilities.add('users');
}
if(permissions.contains('orgs:read')) {
    capabilities.add('orgs');
}
if(global.contains('directory:read')) {
    capabilities.add('org-settings');
}
if(global.contains('operators:read')) {
    capabilities.add('delegates');
}
if(permissions.contains('assignments:read')) {
    capabilities.add('assignments');
}
if(permissions.contains('grants:read')) {
    capabilities.add('grants');
    capabilities.add('permission-groups');
}
if(permissions.contains('roles:read')) {
    capabilities.add('roles');
}
if(permissions.contains('resources:read')) {
    capabilities.add('resources');
}
if(permissions.contains('clients:read')) {
    capabilities.add('clients');
}
if(permissions.contains('sync:read')) {
    capabilities.add('provisioning');
}
if(global.contains('public:read')) {
    capabilities.add('public-identity');
}
if(global.contains('auth-accounts:read')) {
    capabilities.add('auth-accounts');
}
if(global.contains('public:write')) {
    editable.add('legalEntities');
}
if(global.contains('audit:read')) {
    capabilities.add('audit');
}
if(global.contains('operators:read') || global.contains('platform-roles:read') || global.contains('settings:read')) {
    capabilities.add('system');
}
if(permissions.contains('apps:write')) {
    editable.add('apps');
}
if(global.contains('apps:write')) {
    editable.add('app-groups');
}
if(global.contains('operators:write') || global.contains('platform-roles:write')) {
    editable.add('system');
}
if(permissions.contains('users:write')) {
    editable.add('users');
}
if(permissions.contains('orgs:write')) {
    editable.add('orgs');
}
if(global.contains('directory:write')) {
    editable.add('directory');
}
if(permissions.contains('assignments:write')) {
    editable.add('assignments');
}
for(item in [{
    code:'roles:write',table:'roles'
},{
    code:'resources:write',table:'resources'
},{
    code:'grants:write',table:'grants'
},{
    code:'grants:write',table:'groups'
}]) {
    if(permissions.contains(item.code)) {
        editable.add(item.table);
    }
}
if(permissions.contains('sync:write')) {
    editable.add('tasks');
}
// 人员和机构目录权限分别求值，避免应用管理权限扩大共享目录范围。
var directoryRead=ctx.account.must_change_password==1 || !permissions.contains('users:read')?{
    all:false,orgIds:[]
}
:directoryScope('users:read',domain);
var orgRead=ctx.account.must_change_password==1 || !permissions.contains('orgs:read')?{
    all:false,orgIds:[]
}
:directoryScope('orgs:read',domain);
var visibleOrgIds=[];
for(id in directoryRead.orgIds) {
    if(!visibleOrgIds.contains(id)) {
        visibleOrgIds.add(id);
    }
}
for(id in orgRead.orgIds) {
    if(!visibleOrgIds.contains(id)) {
        visibleOrgIds.add(id);
    }
}
// 统一控制台的只读菜单发现：每个身份域分别计算角色与范围的配对，不混入当前操作权限。
var navigationPermissions={};
if(ctx.account.must_change_password!=1) {
    for(identityDomain in ['workforce','public']) {
        var reads=[];
        var globalReads=[];
        var hasScope=false;
        for(pair in ctx.pairs) {
            var scopes=pair.scopes.filter(s=>s.identity_domain==identityDomain);
            if(scopes.size()==0) {
                continue;
            }
            hasScope=true;
            for(code in pair.codes) {
                if(!code.endsWith(':read')) {
                    continue;
                }
                if(!reads.contains(code)) {
                    reads.add(code);
                }
                if(scopes.filter(s=>s.scope_kind=='ALL').size()>0 && !globalReads.contains(code)) {
                    globalReads.add(code);
                }
            }
        }
        if(hasScope) {
            navigationPermissions.put(identityDomain,{
                permissions:reads,globalPermissions:globalReads
            });
        }
    }
}
if(ctx.account.must_change_password==1) {
    capabilities=['workspace','profile'];
    editable=[];
    allowed=[];
    writable=[];
    bindable=[];
    assignable=[];
    global=[];
}
return {
    userId:ctx.userId,username:ctx.account.username,name:ctx.account.display_name,realm:'platform',domain:domain,role:'admin',
    permissions:permissions,globalPermissions:global,navigationPermissions:navigationPermissions,appPermissions:ctx.account.must_change_password==1?{}:appPermissions,appWriteIds:writable,appBindIds:bindable,appAssignableIds:assignable,
    capabilities:capabilities,editableTables:editable,scopeMode:directoryRead.all || orgRead.all?'tenant':'org',allowedAppIds:allowed,orgIds:visibleOrgIds,
    mustChangePassword:ctx.account.must_change_password==1,securityVersion:ctx.account.security_version,operatorVersion:ctx.account.version,subjectVersion:ctx.account.subject_version
};
