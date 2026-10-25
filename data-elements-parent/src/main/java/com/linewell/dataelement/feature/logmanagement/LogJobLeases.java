package com.linewell.dataelement.feature.logmanagement;

import com.linewell.dataelement.platform.audit.*;
import com.linewell.dataelement.platform.tenant.config.TenantDatabaseProperties;
import jakarta.annotation.PreDestroy;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Worker ownership prevents one application instance from failing another instance's live jobs. */
@Component
public class LogJobLeases {
    private record Task(String tenant,String table,String id) { }
    private final String instance=AuditTrace.random();
    private final ConcurrentHashMap<String,Task> tasks=new ConcurrentHashMap<>();
    private final AuditStore store;private final TenantDatabaseProperties properties;private final Path directory;
    private final ScheduledExecutorService maintenance=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"log-job-leases");t.setDaemon(true);return t;});
    public LogJobLeases(AuditStore store,TenantDatabaseProperties properties,@Value("${data-element.audit.files-path:../logs/backend/log-files}") String directory) {
        this.store=store;this.properties=properties;this.directory=Path.of(directory).toAbsolutePath().normalize();
        maintenance.scheduleWithFixedDelay(this::tick,30,30,TimeUnit.SECONDS);
    }
    public Map<String,Object> fields(){return Map.of("worker_id",instance,"lease_until",LogTimes.utc(Instant.now().plusSeconds(120)));}
    public String workerId(){return instance;}
    public void track(LogScope scope,String table,String id){tasks.put(id,new Task(scope.tenantId(),table,id));}
    public void forget(String id){tasks.remove(id);}
    private void tick() {
        try {
            Map<String,List<Task>> groups=new LinkedHashMap<>();tasks.values().forEach(task->groups.computeIfAbsent(Objects.toString(task.tenant(),"PLATFORM")+":"+task.table(),key->new ArrayList<>()).add(task));
            for(var group:groups.values()) {
                var task=group.getFirst();var p=new HashMap<String,Object>();p.put("worker",instance);p.put("until",LogTimes.utc(Instant.now().plusSeconds(120)));p.put("ids",group.stream().map(Task::id).toList());
                store.named(task.tenant()).update("update "+task.table()+" set lease_until=:until where worker_id=:worker and tid in (:ids) and status in ('PENDING','RUNNING','PREPARING','WRITTEN')",p);
            }
            expire(null,List.of());
            Map<String,List<String>> physical=new LinkedHashMap<>();properties.getTenantBindings().forEach((tenant,key)->physical.computeIfAbsent(key,ignored->new ArrayList<>()).add(tenant));
            for(var tenants:physical.values())expire(tenants.getFirst(),tenants);
        }catch(Exception error){org.slf4j.LoggerFactory.getLogger("audit.health").error("log_job_lease_maintenance_failed type={}",error.getClass().getSimpleName());}
    }
    private void expire(String tenant,List<String> tenants) {
        var jdbc=store.named(tenant);var params=new HashMap<String,Object>();params.put("now",LogTimes.utc(Instant.now()));params.put("tenants",tenants);
        String where=tenant==null?"scope_kind='PLATFORM' and scope_id='PLATFORM' and tenant_id is null":"scope_kind='TENANT' and tenant_id in (:tenants) and scope_id=tenant_id";
        jdbc.update("update log_export_job_t set status='FAILED',last_error_code='WORKER_LEASE_EXPIRED',updated_time=:now,version=version+1 where "+where+" and status in ('PENDING','RUNNING') and lease_until<:now",params);
        jdbc.update("update log_archive_batch_t set status='FAILED',updated_time=:now,version=version+1 where "+where+" and status in ('PREPARING','WRITTEN') and lease_until<:now",params);
        var expired=jdbc.queryForList("select tid,object_key from log_export_job_t where "+where+" and status='SUCCESS' and expires_at<=:now order by expires_at,tid limit 100",params).stream().map(AuditStore::lower).toList();
        if(expired.isEmpty())return;params.put("ids",expired.stream().map(row->String.valueOf(row.get("tid"))).toList());
        jdbc.update("update log_export_job_t set status='EXPIRED',updated_time=:now,version=version+1 where "+where+" and tid in (:ids) and status='SUCCESS' and expires_at<=:now",params);
        for(var row:expired) {
            String key=Objects.toString(row.get("object_key"),"");
            if(!key.matches("[a-f0-9]{32}\\.(csv|jsonl)"))continue;
            Path file=directory.resolve(key).normalize();
            try {if(file.startsWith(directory)&&!Files.isSymbolicLink(file)&&Files.exists(file)&&file.toRealPath().startsWith(directory.toRealPath()))Files.delete(file);}catch(Exception error){org.slf4j.LoggerFactory.getLogger("audit.health").warn("log_export_file_cleanup_failed type={}",error.getClass().getSimpleName());}
        }
    }
    @PreDestroy public void close(){maintenance.shutdown();}
}
