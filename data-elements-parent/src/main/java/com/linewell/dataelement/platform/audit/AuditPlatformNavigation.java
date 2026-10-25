package com.linewell.dataelement.platform.audit;

import java.util.*;

/** Canonical console labels; annotations never grant permission or determine an identity domain. */
final class AuditPlatformNavigation {
    private record Page(String key,String group,String label,boolean applicationScoped,List<String> permissions) {}
    private static Page page(String key,String group,String label,boolean scoped,String... permissions){return new Page(key,group,label,scoped,List.of(permissions));}
    private static final List<Page> PAGES=List.of(
        page("apps","应用管理","应用列表",true,"apps:read"),
        page("app-groups","应用管理","应用分组",false,"apps:read"),
        page("organization","组织与用户","组织与用户",true,"users:read"),
        page("auth-accounts","组织与用户","认证账号",false,"auth-accounts:read"),
        page("delegates","组织与用户","分级管理员",false,"operators:read"),
        page("org-settings/lines","组织与用户","条线管理",false,"directory:read"),
        page("org-settings/dictionaries","组织与用户","职务岗位",false,"directory:read"),
        page("org-settings/fields","组织与用户","扩展字段",false,"directory:read"),
        page("grants/apps","授权管理","应用授权",true,"grants:read"),
        page("grants/users","授权管理","用户授权",true,"grants:read"),
        page("grants/orgs","授权管理","机构授权",true,"grants:read"),
        page("permission-groups","授权管理","权限组",false,"grants:read"),
        page("sync/configs","同步管理","同步配置",false,"sync:read"),
        page("sync/entities","同步管理","同步对象",false,"sync:read"),
        page("sync/tasks","同步管理","同步任务",false,"sync:read"),
        page("audit/logs","审计日志","日志管理",false,"audit:read","audit:export","audit:archive","audit:configure"),
        page("system/access/admins","系统管理","管理员管理",false,"operators:read"),
        page("system/access/roles","系统管理","系统角色",false,"platform-roles:read"),
        page("locked-accounts","系统管理","账号解锁",false,"operators:read"),
        page("system/settings/security","系统管理","账号安全",false,"settings:read"),
        page("system/settings/sso","系统管理","单点登录设置",false,"settings:read"),
        page("system/settings/encryption","系统管理","字段加密",false,"settings:read"),
        page("system/settings/api","系统管理","API 访问控制",false,"settings:read"),
        page("system/settings/branding","系统管理","系统品牌",false,"settings:read"),
        page("persons","公众身份","自然人管理",true,"users:read"),
        page("entities","公众身份","法人管理",false,"public:read"),
        page("verifications","公众身份","身份核验",false,"public:read")
    );
    static Map<String,Object> snapshot(String route,Map<?,?> context){
        if(!route.startsWith("/console/"))return Map.of();
        String[] parts=route.substring(9).split("/",2);if(parts.length!=2||!Set.of("workforce","public").contains(parts[0]))return Map.of();
        String domain=parts[0],key=parts[1];
        var page=PAGES.stream().filter(p->key.equals(p.key())||(p.key().equals("apps")&&key.startsWith("apps/"))).findFirst().orElse(null);
        if(page==null||!(context.get("pairs") instanceof Collection<?> pairs))return Map.of();
        boolean allowed=false;
        for(Object value:pairs){
            if(!(value instanceof Map<?,?> pair)||!(pair.get("codes") instanceof Collection<?> codes)||!(pair.get("scopes") instanceof Collection<?> scopes))continue;
            if(page.permissions().stream().noneMatch(codes::contains))continue;
            for(Object s:scopes)if(s instanceof Map<?,?> scope&&domain.equals(scope.get("identity_domain"))&&(page.applicationScoped()||"ALL".equals(scope.get("scope_kind")))){allowed=true;break;}
            if(allowed)break;
        }
        if(!allowed)return Map.of();
        String group=domain.equals("public")&&Set.of("apps","app-groups","auth-accounts").contains(page.key())?"公众身份":page.group();
        String path=group.equals(page.label())?group:group+" → "+page.label();
        return Map.of("menu_id","idaas:"+domain+":"+page.key(),"menu_path",path,"page_route",route);
    }
}
