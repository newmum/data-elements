package com.linewell.dataelement.feature.logmanagement;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.platform.audit.AuditStore;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.*;
import org.springframework.stereotype.Service;

/** Permission and range are taken from the same active role assignment in the current application. */
@Service
public class LogAccessService {
    private final AuditStore store;
    private final org.springframework.beans.factory.ObjectProvider<org.ssssssss.magicapi.core.service.MagicAPIService> magic;
    public LogAccessService(AuditStore store,org.springframework.beans.factory.ObjectProvider<org.ssssssss.magicapi.core.service.MagicAPIService> magic) { this.store=store;this.magic=magic; }
    public LogScope current(String capability) {
        if(!StpUtil.isLogin()) throw new SecurityException("请重新登录");
        String tenant=TenantContext.requireTenantId();
        String app=Objects.toString(StpUtil.getTokenSession().get("appId"),"");
        return tenant(tenant,StpUtil.getLoginIdAsString(),app,capability);
    }
    public List<String> capabilities() {
        if(!StpUtil.isLogin())throw new SecurityException("请重新登录");
        String tenant=TenantContext.requireTenantId(),app=Objects.toString(StpUtil.getTokenSession().get("appId"),"");
        var rows=store.jdbc(tenant).queryForList("select distinct m.code,m.id menu_id,r.data_scope from rm_user_role_rela_t ur join rm_role_t r on r.tenant_id=ur.tenant_id and r.id=ur.role_id and r.app_id=? and r.deleted=0 and r.status=0 join rm_role_menu_rela_t rm on rm.tenant_id=r.tenant_id and rm.role_id=r.id join rm_menu_t m on m.tenant_id=rm.tenant_id and m.id=rm.app_resource_id and m.app_id=r.app_id and m.deleted=0 and m.status=0 where ur.tenant_id=? and ur.user_id=? and m.code like 'logs:%'",app,tenant,StpUtil.getLoginIdAsString());
        var active=activeMenus(tenant,app);
        return rows.stream().map(AuditStore::lower).filter(row->active.contains(String.valueOf(row.get("menu_id")))).filter(row->!Set.of("logs:archives","logs:policies").contains(String.valueOf(row.get("code")))||"ALL".equals(row.get("data_scope"))).map(row->String.valueOf(row.get("code")).substring(5)).filter(code->Set.of("auth","operations","requests","security","exports","archives","policies").contains(code)).distinct().toList();
    }
    public LogScope tenant(String tenant,String user,String app,String capability) {
        if(!Set.of("auth","operations","requests","security","exports","archives","policies").contains(capability)) throw new IllegalArgumentException("日志模块无效");
        var rows=store.jdbc(tenant).queryForList("""
            select distinct r.id,r.data_scope,m.id menu_id from rm_user_t u
            join rm_user_role_rela_t ur on ur.tenant_id=u.tenant_id and ur.user_id=u.id
            join rm_role_t r on r.tenant_id=ur.tenant_id and r.id=ur.role_id and r.app_id=? and r.deleted=0 and r.status=0
            join rm_role_menu_rela_t rm on rm.tenant_id=r.tenant_id and rm.role_id=r.id
            join rm_menu_t m on m.tenant_id=rm.tenant_id and m.id=rm.app_resource_id and m.app_id=r.app_id
            where u.tenant_id=? and u.id=? and u.status=0 and u.deleted=0 and m.deleted=0 and m.status=0 and m.code=?
            """,app,tenant,user,"logs:"+capability);
        var active=activeMenus(tenant,app);
        rows=rows.stream().filter(row->active.contains(String.valueOf(AuditStore.lower(row).get("menu_id")))).toList();
        if(rows.isEmpty()) throw new SecurityException("没有此日志模块的访问权限");
        String mode="OWNER";
        for(var row:rows) {
            String value=Objects.toString(AuditStore.lower(row).get("data_scope"),"OWNER");
            if(value.equals("ALL")) { mode="ALL"; break; }
            if(value.equals("ORG")) mode="ORG";
        }
        List<String> orgs=mode.equals("ORG")?store.jdbc(tenant).queryForList("select distinct o.id from rm_user_org_rela_t r join rm_org_t o on o.tenant_id=r.tenant_id and o.id=r.org_id and o.deleted=0 where r.tenant_id=? and r.user_id=?",String.class,tenant,user):List.of();
        return new LogScope(tenant,user,app,null,null,mode,orgs,capability);
    }
    @SuppressWarnings("unchecked")
    private Set<String> activeMenus(String tenant,String app) {
        var request=com.linewell.dataelement.platform.audit.AuditIdentityResolver.request();
        String cacheKey="audit.logMenus:"+tenant+":"+app;
        if(request!=null&&request.getAttribute(cacheKey) instanceof Set<?> cached)return (Set<String>)cached;
        var rows=store.jdbc(tenant).queryForList("select id,parent_id,status,deleted from rm_menu_t where tenant_id=? and app_id=?",tenant,app);
        Map<String,Map<String,Object>> byId=new HashMap<>();rows.forEach(row->{var value=AuditStore.lower(row);byId.put(String.valueOf(value.get("id")),value);});
        Set<String> allowed=new HashSet<>();
        for(String id:byId.keySet()) {
            String current=id;Set<String> seen=new HashSet<>();boolean valid=true;
            while(current!=null&&!current.isBlank()&&!Set.of("ROOT","0").contains(current)) {
                var row=byId.get(current);
                if(row==null||!seen.add(current)||seen.size()>50||!"0".equals(String.valueOf(row.get("status")))||!"0".equals(String.valueOf(row.get("deleted")))) {valid=false;break;}
                current=row.get("parent_id")==null?null:String.valueOf(row.get("parent_id"));
            }
            if(valid)allowed.add(id);
        }
        if(request!=null)request.setAttribute(cacheKey,allowed);return allowed;
    }
    public LogScope platform(Map<String,Object> context,String capability) {
        if(context==null || !platformPermission(capability).equals(context.get("requiredPermission"))) throw new SecurityException("平台审计权限未校验");
        return new LogScope(null,String.valueOf(context.get("userId")),"idaas",String.valueOf(context.get("requiredDomain")),context.get("requiredAppId")==null?null:String.valueOf(context.get("requiredAppId")),"ALL",List.of(),capability);
    }
    public String platformPermission(String capability) {
        return switch(capability) {case "exports"->"audit:export";case "policies"->"audit:configure";case "archives"->"audit:archive";default->"audit:read";};
    }
    public LogScope refresh(LogScope scope,String capability) {
        if(!scope.platform()) return tenant(scope.tenantId(),scope.actorId(),scope.sourceApp(),capability);
        var decision=magic.getObject().invoke("/idaas/log-scope-for-actor",Map.of("actorId",scope.actorId(),"permission",platformPermission(capability),"domain",scope.domain(),"appId",Objects.toString(scope.appId(),"")));
        if(!(decision instanceof Map<?,?> result)||!Boolean.TRUE.equals(result.get("allowed"))) throw new SecurityException("日志权限已变更，请重新提交");
        return new LogScope(null,scope.actorId(),scope.sourceApp(),scope.domain(),scope.appId(),"ALL",List.of(),capability);
    }
    public LogScope intersect(LogScope before,LogScope now) {
        if(before.platform()) return now;
        if(before.mode().equals("ALL")) return now;
        if(now.mode().equals("ALL")) return before;
        boolean owner=before.mode().contains("OWNER")||now.mode().contains("OWNER");
        boolean org=before.mode().contains("ORG")||now.mode().contains("ORG");
        List<String> orgs=before.mode().contains("ORG")?before.orgIds():now.orgIds();
        if(before.mode().contains("ORG")&&now.mode().contains("ORG")) orgs=before.orgIds().stream().filter(now.orgIds()::contains).toList();
        return new LogScope(now.tenantId(),now.actorId(),now.sourceApp(),null,null,owner&&org?"OWNER_AND_ORG":owner?"OWNER":"ORG",orgs,now.capability());
    }
}
