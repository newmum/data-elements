package com.linewell.dataelement.platform.audit;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Creates append-only event rows. Magic callers insert prepared rows with their own transaction's SQL object. */
@Component
public class AuditRecorder {
    private static final Logger HEALTH=LoggerFactory.getLogger("audit.health");
    public static final Set<String> RESULTS=Set.of("SUCCESS","FAILED","DENIED","PARTIAL","ACCEPTED","CANCELLED","TIMEOUT","UNKNOWN");
    private final AuditStore store;
    private final AuditIdentityResolver identities;
    private final AuditSanitizer sanitizer;
    private final AuditEmergencyBuffer emergency;
    private final AuditPolicyCache policies;
    private AuditNavigation navigation;
    @org.springframework.beans.factory.annotation.Autowired public void setNavigation(AuditNavigation navigation){this.navigation=navigation;}
    private final java.util.concurrent.ThreadPoolExecutor requests=new java.util.concurrent.ThreadPoolExecutor(1,2,30,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<>(2048),r->{Thread t=new Thread(r,"audit-request-writer");t.setDaemon(true);return t;},new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    public AuditRecorder(AuditStore store,AuditIdentityResolver identities,AuditSanitizer sanitizer,AuditEmergencyBuffer emergency,AuditPolicyCache policies) {
        this.store=store; this.identities=identities; this.sanitizer=sanitizer; this.emergency=emergency;this.policies=policies;
    }
    public record Prepared(String table,Map<String,Object> row) { }
    public Prepared prepare(AuditIdentity actor,String category,String action,String result,String reason,
                            String targetType,String targetId,String targetName,Object details) {
        Objects.requireNonNull(actor,"缺少可信审计主体");
        if(!RESULTS.contains(result) || !Set.of("AUTH","OPERATION","REQUEST","SECURITY","TASK").contains(category)) throw new IllegalArgumentException("日志事件类型或结果无效");
        Map<String,Object> row=new LinkedHashMap<>();
        String id=AuditTrace.random(); var now=LogTimes.utc(Instant.now());
        boolean platform=actor.platform();
        String table=platform?"iam_audit_event_t":category.equals("AUTH")?"log_user_login_t":category.equals("REQUEST")?"log_request_t":"log_system_t";
        row.put("tid",id); row.put("category",category); row.put("source_app",AuditSanitizer.text(actor.sourceApp(),64));
        row.put("actor_kind",actor.kind());
        row.put(platform?"actor_id":category.equals("AUTH")?"created_by":"user_id",actor.id());
        row.put(platform?"actor_account":category.equals("AUTH")||category.equals("REQUEST")?"account":"user_account",AuditSanitizer.text(actor.account(),128));
        row.put(platform?"actor_name":category.equals("OPERATION")||category.equals("SECURITY")||category.equals("TASK")?"user_name":"full_name",AuditSanitizer.text(actor.name(),128));
        row.put(platform?"actor_org_id":"org_id",actor.orgId());
        row.put(platform?"actor_org_name":"org_name",AuditSanitizer.text(actor.orgName(),256));
        row.put(platform?"action":"action_code",AuditSanitizer.text(action,100));
        row.put(platform?"object_type":"target_type",AuditSanitizer.text(targetType,32));
        row.put(platform?"object_id":"target_id",AuditSanitizer.text(targetId,64));
        row.put("target_name",AuditSanitizer.text(targetName,256));
        row.put("result",result); row.put("reason_code",AuditSanitizer.text(reason,100));
        row.put("event_time",now); row.put("received_time",now); row.put("created_time",now);
        row.put("updated_time",now); row.put("request_id",AuditTrace.requestId()); row.put("trace_id",AuditTrace.traceId());
        row.put("operation_id",AuditTrace.requestId()); row.put("session_ref",actor.sessionRef()); row.put("origin_key",id);
        row.put(platform?"snapshot_json":"detail_json",sanitizer.json(details));
        var request=AuditIdentityResolver.request();
        if(request!=null) {
            if(navigation!=null)row.putAll(navigation.snapshot(actor));
            row.put("operation_name",AuditSanitizer.text(Objects.toString(request.getAttribute("audit.functionName"),"").replaceFirst("^\\d+[.、]\\s*",""),256));
            row.put("function_code",AuditSanitizer.text(request.getAttribute("audit.functionId"),100));
            row.put(platform?"request_path":"route_path",AuditSanitizer.text(request.getRequestURI(),1000));
            row.put(platform?"http_method":"request_method",request.getMethod());
            row.put("ip",AuditSanitizer.text(request.getRemoteAddr(),64));
            row.put("user_agent",AuditSanitizer.text(request.getHeader("User-Agent"),512));
        }
        if(platform) {
            row.put("event_type",category.equals("AUTH")?"AUTH":category.equals("REQUEST")?"API":"OPERATION");
            row.put("identity_domain",actor.domain());
        } else {
            row.put("tenant_id",actor.tenantId()); row.put("is_del",0);
            if(!category.equals("REQUEST")) row.put("is_success",result.equals("SUCCESS")?1:0);
        }
        return new Prepared(table,row);
    }
    public String record(AuditIdentity actor,String category,String action,String result,String reason,
                         String targetType,String targetId,String targetName,Object details) {
        var event=prepare(actor,category,action,result,reason,targetType,targetId,targetName,details);
        store.insert(actor.tenantId(),event.table(),event.row()); return String.valueOf(event.row().get("tid"));
    }
    /** Noncritical request collection never replaces the original business error. */
    public void request(AuditIdentity actor,int status,String businessCode,String result,long duration) {
        if(actor==null) return;
        var req=AuditIdentityResolver.request();
        if(req==null) return;
        try {
            boolean optional=result.equals("SUCCESS")&&req.getMethod().equals("GET")&&!req.getRequestURI().matches("(?i).*(preview|sample-data|export|download|grant|auth|login|logout).*" );
            double sample=optional?policies.sampleRate(actor):1;
            if(sample<1&&java.util.concurrent.ThreadLocalRandom.current().nextDouble()>=sample)return;
            var event=prepare(actor,"REQUEST",req.getMethod()+" "+req.getRequestURI(),result,null,null,null,null,null);
            if(!actor.platform()){event.row().put("sampled",sample<1?1:0);event.row().put("sample_rate",sample);}
            event.row().put("http_status",status); event.row().put("business_code",AuditSanitizer.text(businessCode,64));
            event.row().put("duration_ms",Math.max(0,duration));
            try { requests.execute(()->{
                try {store.insert(actor.tenantId(),event.table(),event.row());}
                catch(Exception failure) {emergency.persist(actor.tenantId(),event);}
            }); } catch(java.util.concurrent.RejectedExecutionException full) { emergency.persist(actor.tenantId(),event); }
        } catch(Exception failure) {
            // No exception text/body: a JDBC error can contain sensitive SQL parameters.
            HEALTH.error("audit_request_write_failed request_id={} type={}",AuditTrace.requestId(),failure.getClass().getSimpleName());
        }
    }
    public AuditIdentity current() { return identities.current(); }
    public void observedOperation(AuditIdentity actor,String result,String path,Object label) {
        String category=result.equals("DENIED")?"SECURITY":"OPERATION";
        var event=prepare(actor,category,"HTTP:"+path,result,result.equals("DENIED")?"ACCESS_DENIED":null,"FUNCTION",path,AuditSanitizer.text(label,256),null);
        event.row().put("module_name",AuditSanitizer.text(label,255));
        try {requests.execute(()->{try{store.insert(actor.tenantId(),event.table(),event.row());}catch(Exception error){emergency.persist(actor.tenantId(),event);}});}catch(java.util.concurrent.RejectedExecutionException error){emergency.persist(actor.tenantId(),event);}
    }
    public void afterRevocation(AuditIdentity actor) {
        afterRevocation(actor,true);
    }
    public void afterRevocation(AuditIdentity actor,boolean confirmed) {
        if(actor==null) return;
        var event=prepare(actor,"AUTH","AUTH_LOGOUT",confirmed?"SUCCESS":"PARTIAL",confirmed?null:"PROTOCOL_REVOCATION_PENDING","SESSION",actor.sessionRef(),null,null);
        try {store.insert(actor.tenantId(),event.table(),event.row());}catch(Exception error) {emergency.persist(actor.tenantId(),event);}
    }
    public void compensatedLogin(AuditIdentity actor) {
        if(actor==null)return;
        var anonymous=new AuditIdentity(actor.tenantId(),"ANONYMOUS",null,null,null,null,null,actor.sourceApp(),actor.sessionRef(),actor.domain());
        var event=prepare(anonymous,"AUTH","AUTH_LOGIN_FAILED","FAILED","AUDIT_UNAVAILABLE","USER",actor.id(),null,null);
        try{store.insert(actor.tenantId(),event.table(),event.row());}catch(Exception error){emergency.persist(actor.tenantId(),event);}
    }
    @jakarta.annotation.PreDestroy public void close() { requests.shutdown();try {requests.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e) {Thread.currentThread().interrupt();} }
}
