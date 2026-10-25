package com.linewell.dataelement.platform.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.persistence.jdbc.JdbcValueNormalizer;
import com.linewell.dataelement.platform.tenant.config.TenantDatabaseProperties;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;

/** Same-transaction outbox primitives and bounded lease recovery. With no configured destination it performs no scans. */
@Component
public class AuditOutbox {
    private final AuditStore store;private final ObjectMapper mapper;private final TenantDatabaseProperties properties;
    private final Map<String,AuditDestination> destinations=new LinkedHashMap<>();
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"audit-outbox");t.setDaemon(true);return t;});
    public AuditOutbox(AuditStore store,ObjectMapper mapper,TenantDatabaseProperties properties,List<AuditDestination> installed) {
        this.store=store;this.mapper=mapper;this.properties=properties;
        if(installed.size()>32)throw new IllegalArgumentException("日志投递目标超过上限");
        for(var target:installed)if(!target.code().matches("[A-Z0-9_]{1,64}")||destinations.put(target.code(),target)!=null)throw new IllegalArgumentException("日志投递目标标识无效或重复");
        worker.scheduleWithFixedDelay(this::poll,15,15,TimeUnit.SECONDS);
    }
    public Map<String,Object> prepare(AuditIdentity actor,AuditRecorder.Prepared event,String destination) {
        if(!destinations.containsKey(destination))throw new IllegalArgumentException("日志投递目标未由部署配置启用");
        var row=new LinkedHashMap<String,Object>();var now=LogTimes.utc(Instant.now());
        row.put("tid",AuditTrace.random());row.put("scope_kind",actor.platform()?"PLATFORM":"TENANT");row.put("scope_id",actor.platform()?"PLATFORM":actor.tenantId());row.put("tenant_id",actor.tenantId());row.put("identity_domain",actor.platform()?actor.domain():"global");
        row.put("created_time",now);row.put("updated_time",now);row.put("event_store",actor.platform()?"IAM":event.table().equals("log_user_login_t")?"AUTH":event.table().equals("log_request_t")?"REQUEST":"OPERATION");row.put("event_id",event.row().get("tid"));row.put("destination_code",destination);row.put("payload_version",1);
        try {String payload=mapper.writeValueAsString(event.row());if(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65536)throw new IllegalArgumentException("日志投递信封过大");row.put("payload_json",payload);}catch(com.fasterxml.jackson.core.JsonProcessingException error){throw new IllegalArgumentException("日志投递信封无法编码",error);}
        row.put("status","PENDING");row.put("attempt_count",0);row.put("next_retry_at",now);row.put("version",1);return row;
    }
    private void poll() {
        if(destinations.isEmpty())return;
        try {
            drain(null,List.of());
            Map<String,List<String>> groups=new LinkedHashMap<>();properties.getTenantBindings().forEach((tenant,key)->groups.computeIfAbsent(key,k->new ArrayList<>()).add(tenant));
            for(var tenants:groups.values())drain(tenants.getFirst(),tenants);
        }catch(Exception error){org.slf4j.LoggerFactory.getLogger("audit.health").error("audit_outbox_poll_failed type={}",error.getClass().getSimpleName());}
    }
    private void drain(String tenant,List<String> tenantIds) {
        var jdbc=store.named(tenant);var now=LogTimes.utc(Instant.now());var params=new LinkedHashMap<String,Object>();params.put("now",now);params.put("destinations",destinations.keySet());params.put("tenants",tenantIds);
        String scope=tenant==null?"scope_kind='PLATFORM' and scope_id='PLATFORM' and tenant_id is null":"scope_kind='TENANT' and tenant_id in (:tenants) and scope_id=tenant_id";
        String ready="((status in ('PENDING','RETRY') and next_retry_at<=:now) or (status='PROCESSING' and lease_until<:now))";
        var ids=jdbc.queryForList("select tid from log_audit_outbox_t where "+scope+" and destination_code in (:destinations) and "+ready+" order by next_retry_at,tid limit 100",params,String.class);
        if(ids.isEmpty())return;params.put("ids",ids);params.put("lease",AuditTrace.random());params.put("until",LogTimes.utc(Instant.now().plusSeconds(120)));
        jdbc.update("update log_audit_outbox_t set status='PROCESSING',attempt_count=attempt_count+1,lease_owner=:lease,lease_until=:until,updated_time=:now,version=version+1 where "+scope+" and tid in (:ids) and "+ready,params);
        var claimed=jdbc.queryForList("select tid,event_id,event_store,tenant_id,identity_domain,destination_code,payload_json,attempt_count from log_audit_outbox_t where "+scope+" and tid in (:ids) and lease_owner=:lease and status='PROCESSING'",params).stream().map(JdbcValueNormalizer::map).map(AuditStore::lower).toList();
        Map<String,List<Map<String,Object>>> batches=new LinkedHashMap<>();claimed.forEach(row->batches.computeIfAbsent(String.valueOf(row.get("destination_code")),key->new ArrayList<>()).add(row));
        for(var batch:batches.entrySet()) {
            Set<String> ack=Set.of();try {ack=destinations.get(batch.getKey()).deliver(batch.getValue());}catch(Exception ignored) { }
            Set<String> known=new HashSet<>();batch.getValue().forEach(row->known.add(String.valueOf(row.get("event_id"))));if(ack==null||!known.containsAll(ack))ack=Set.of();
            var done=new ArrayList<String>();var retry=new ArrayList<String>();var dead=new ArrayList<String>();
            int attempt=batch.getValue().stream().mapToInt(row->((Number)row.get("attempt_count")).intValue()).max().orElse(1);
            params.put("backoff",Math.min(3600,30L*(1L<<Math.min(7,attempt-1))));
            for(var row:batch.getValue()) {
                String id=String.valueOf(row.get("tid"));if(ack.contains(String.valueOf(row.get("event_id"))))done.add(id);else if(((Number)row.get("attempt_count")).intValue()>=8)dead.add(id);else retry.add(id);
            }
            complete(jdbc,params,scope,done,"DONE",null);complete(jdbc,params,scope,retry,"RETRY","DELIVERY_UNCONFIRMED");complete(jdbc,params,scope,dead,"DEAD","DELIVERY_EXHAUSTED");
        }
    }
    private void complete(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc,Map<String,Object> shared,String scope,List<String> ids,String status,String code) {
        if(ids.isEmpty())return;var p=new LinkedHashMap<>(shared);p.put("ids",ids);p.put("status",status);p.put("code",code);p.put("retry",LogTimes.utc(Instant.now().plusSeconds(((Number)shared.getOrDefault("backoff",30)).longValue()+ThreadLocalRandom.current().nextInt(30))));
        jdbc.update("update log_audit_outbox_t set status=:status,last_error_code=:code,next_retry_at=:retry,lease_owner=null,lease_until=null,updated_time=:now,version=version+1 where "+scope+" and tid in (:ids) and lease_owner=:lease",p);
    }
    @PreDestroy public void close(){worker.shutdown();}
}
