package com.linewell.dataelement.platform.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Bounded local recovery buffer for sanitized events. It does not claim zero loss on disk failure. */
@Component
public class AuditEmergencyBuffer {
    private final AuditStore store;
    private final ObjectMapper mapper;
    private final Path directory;
    private final ScheduledExecutorService recovery=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"audit-recovery");t.setDaemon(true);return t;});
    public final AtomicLong writeFailures=new AtomicLong(),dropped=new AtomicLong();
    public AuditEmergencyBuffer(AuditStore store,ObjectMapper mapper,@Value("${data-element.audit.spool-path:../logs/backend/audit-spool}") String directory) {
        this.store=store;this.mapper=mapper;this.directory=Path.of(directory).toAbsolutePath().normalize();
        recovery.scheduleWithFixedDelay(this::recover,30,30,TimeUnit.SECONDS);
    }
    public synchronized void persist(String tenantId,AuditRecorder.Prepared event) {
        writeFailures.incrementAndGet();
        try {
            Files.createDirectories(directory);
            try { Files.setPosixFilePermissions(directory,PosixFilePermissions.fromString("rwx------")); } catch(UnsupportedOperationException ignored) { }
            long count;try(var files=Files.list(directory)) { count=files.limit(10001).count(); }
            if(count>=10000) throw new IllegalStateException("Audit spool limit");
            var row=new LinkedHashMap<>(event.row());
            row.replaceAll((key,value)->value instanceof java.time.LocalDateTime t?t.toInstant(java.time.ZoneOffset.UTC).toString():value instanceof Timestamp t?t.toInstant().toString():value);
            var data=new LinkedHashMap<String,Object>();data.put("tenantId",tenantId);data.put("table",event.table());data.put("row",row);
            Path file=directory.resolve(AuditTrace.random()+".json");
            byte[] bytes=mapper.writeValueAsBytes(data);
            if(bytes.length>131072) throw new IllegalArgumentException("Audit spool event limit");
            Path pending=file.resolveSibling(file.getFileName()+".pending");
            try(var channel=java.nio.channels.FileChannel.open(pending,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {var buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}
            try {Files.move(pending,file,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException unsupported) {Files.move(pending,file);}
            try {Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"));}catch(UnsupportedOperationException ignored) { }
        } catch(Exception error) {
            dropped.incrementAndGet();
            LoggerFactory.getLogger("audit.health").error("audit_emergency_buffer_unavailable type={}",error.getClass().getSimpleName());
        }
    }
    @SuppressWarnings("unchecked")
    private void recover() {
        if(!Files.isDirectory(directory)) return;
        try(var files=Files.list(directory)) {
            // Reads are local bounded files; database operations are inserts, not per-event lookups.
            for(Path file:files.filter(p->p.getFileName().toString().matches("[a-f0-9]{32}\\.json")).limit(100).toList()) {
                try {
                    if(Files.size(file)>131072) continue;
                    var data=mapper.readValue(Files.readAllBytes(file),Map.class);
                    var row=(Map<String,Object>)data.get("row");
                    row.replaceAll((key,value)->Set.of("event_time","received_time","created_time","updated_time").contains(key)&&value!=null?LogTimes.utc(java.time.Instant.parse(String.valueOf(value))):value);
                    try {store.insert((String)data.get("tenantId"),(String)data.get("table"),row);}
                    catch(org.springframework.dao.DuplicateKeyException alreadyRecovered) { }
                    Files.delete(file);
                } catch(Exception ignored) { /* Retain for bounded retry; health count remains visible. */ }
            }
        } catch(Exception error) { LoggerFactory.getLogger("audit.health").warn("audit_recovery_scan_failed type={}",error.getClass().getSimpleName()); }
    }
    @PreDestroy public void close() { recovery.shutdown(); }
}
