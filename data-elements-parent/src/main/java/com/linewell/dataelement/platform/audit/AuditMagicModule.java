package com.linewell.dataelement.platform.audit;

import java.util.*;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;

/** Primitives only. Authorization/business SQL remain in the canonical Magic scripts. */
@Component
@MagicModule("auditEvents")
public class AuditMagicModule {
    private final AuditRecorder recorder;
    private final AuditIdentityResolver identities;
    private final AuditFingerprint fingerprints;
    public AuditMagicModule(AuditRecorder recorder,AuditIdentityResolver identities,AuditFingerprint fingerprints) { this.recorder=recorder; this.identities=identities;this.fingerprints=fingerprints; }
    public String fingerprint(String value,String purpose){return fingerprints.fingerprint(value,purpose);}
    public Map<String,Object> preparePlatform(Map<String,Object> context,String result,String action,String objectId,String reason,Object details) {
        if(context!=null&&AuditIdentityResolver.request()!=null)AuditIdentityResolver.request().setAttribute("audit.platformPermission",context.get("requiredPermission"));
        if(context!=null&&AuditIdentityResolver.request()!=null)AuditIdentityResolver.request().setAttribute("audit.platformContext",context);
        String kind=context==null?"ANONYMOUS":String.valueOf(context.getOrDefault("actorKind","OPERATOR"));
        String id=context==null?null:string(context.get("userId"));
        if(id==null) kind="ANONYMOUS";
        String domain=context==null?"workforce":string(context.getOrDefault("requiredDomain","workforce"));
        var actor=identities.platform(id,kind,domain);
        identities.freeze(actor);
        var prepared=recorder.prepare(actor,action.startsWith("auth:")?"AUTH":action.startsWith("api:")?"REQUEST":"OPERATION",action,result,reason,"IAM",objectId,null,details);
        if(context!=null) {
            prepared.row().put("app_id",context.get("requiredAppId"));
            if(context.get("accountFingerprint")!=null) prepared.row().put("account_fingerprint",context.get("accountFingerprint"));
        }
        return Map.of("table",prepared.table(),"row",prepared.row());
    }
    public Map<String,Object> prepare(String category,String action,String result,String targetType,String targetId,String targetName,Object details) {
        var prepared=recorder.prepare(Objects.requireNonNull(identities.current(),"请重新登录"),category,action,result,null,targetType,targetId,targetName,details);
        if(AuditIdentityResolver.request()!=null)AuditIdentityResolver.request().setAttribute("audit.operationRecorded",true);
        return Map.of("table",prepared.table(),"row",prepared.row());
    }
    public void authentication(String tenantId,String userId,String action,String result,String reason) {
        AuditIdentity actor=userId==null ? new AuditIdentity(tenantId,"ANONYMOUS",null,null,null,null,null,null,null,null) : identities.tenant(tenantId,userId);
        identities.freeze(actor);
        recorder.record(actor,"AUTH",action,result,reason,"SESSION",actor.sessionRef(),null,null);
    }
    public String requestId() { return AuditTrace.requestId(); }
    public String traceId() { return AuditTrace.traceId(); }
    public List<Map<String,Object>> batchRows(Map<String,Object> parent,Map<String,Object> details){
        if(details==null)return List.of();Object ids=details.getOrDefault("targetIds",details.get("orgIds"));
        if(!(ids instanceof Collection<?> values)||values.isEmpty())return List.of();
        if(values.size()>5000)throw new IllegalArgumentException("日志批次超过5000项");
        var actor=Objects.requireNonNull(identities.current());@SuppressWarnings("unchecked") var event=(Map<String,Object>)parent.get("row");
        List<Map<String,Object>> rows=new ArrayList<>();int index=0;
        for(Object id:values){var row=new LinkedHashMap<String,Object>();row.put("tid",AuditTrace.random());row.put("scope_kind",actor.platform()?"PLATFORM":"TENANT");row.put("scope_id",actor.platform()?"PLATFORM":actor.tenantId());row.put("tenant_id",actor.tenantId());row.put("identity_domain",actor.platform()?actor.domain():"global");row.put("event_store","operations");row.put("event_id",event.get("tid"));row.put("batch_id",event.get("tid"));row.put("item_no",++index);row.put("target_type",details.containsKey("orgIds")?"ORGANIZATION":Objects.toString(event.get("target_type"),"OBJECT"));row.put("target_id",AuditSanitizer.text(id,64));row.put("action_code",event.get(actor.platform()?"action":"action_code"));row.put("result",event.get("result"));row.put("created_time",event.get("created_time"));row.put("event_time",event.get("event_time"));rows.add(row);}
        event.put("batch_id",event.get("tid"));event.put("total_count",values.size());event.put("success_count",values.size());event.put("failure_count",0);
        return rows;
    }
    public void bindPlatform(Map<String,Object> context) {
        var logic=com.linewell.dataelement.platform.tenant.api.PlatformSessionMagicModule.LOGIC;
        if(context==null||!logic.isLogin()||!logic.getLoginIdAsString().equals(string(context.get("userId"))))throw new SecurityException("平台审计主体无效");
        if(AuditIdentityResolver.request()!=null)AuditIdentityResolver.request().setAttribute("audit.platformPermission",context.get("requiredPermission"));
        if(context!=null&&AuditIdentityResolver.request()!=null)AuditIdentityResolver.request().setAttribute("audit.platformContext",context);
        var actor=identities.current();String domain=string(context.get("requiredDomain"));
        if(actor!=null)identities.freeze(new AuditIdentity(null,actor.kind(),actor.id(),actor.account(),actor.name(),actor.orgId(),actor.orgName(),actor.sourceApp(),actor.sessionRef(),domain));
    }
    public void afterLogout() {recorder.afterRevocation(identities.current());}
    public void afterLogout(boolean confirmed) {recorder.afterRevocation(identities.current(),confirmed);}
    public void compensateLogin() {var actor=identities.current();cn.dev33.satoken.stp.StpUtil.logout();recorder.compensatedLogin(actor);}
    public void logoutTenant() {
        var actor=identities.current();
        cn.dev33.satoken.stp.StpUtil.logout();
        recorder.afterRevocation(actor);
    }
    public void logoutPlatform() {
        var actor=identities.current();
        com.linewell.dataelement.platform.tenant.api.PlatformSessionMagicModule.LOGIC.logout();
        recorder.afterRevocation(actor);
    }
    private static String string(Object value) { return value==null?null:String.valueOf(value); }
}
