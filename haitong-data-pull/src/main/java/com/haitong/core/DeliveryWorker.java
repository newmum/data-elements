package com.haitong.core;

import com.haitong.config.*;
import com.haitong.connector.*;
import com.haitong.core.Models.*;
import com.haitong.store.RelayRepository;
import org.slf4j.*;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DeliveryWorker implements SmartLifecycle {
    private static final Logger log=LoggerFactory.getLogger(DeliveryWorker.class);
    private final RelayRepository repo;private final RelayProperties p;private final Json json;
    private final KafkaConnector kafka;private final FtpConnector ftp;private final LocalConnector local;private final LogConnector logTarget;private final ActionConnector actions;
    private final List<ExecutorService> executors=new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService heartbeats=Executors.newScheduledThreadPool(2,Thread.ofPlatform().daemon().name("relay-lease-",0).factory());
    private volatile boolean running;
    @org.springframework.beans.factory.annotation.Value("${relay.files.enabled:false}") private boolean fileTransfers;
    public DeliveryWorker(RelayRepository repo,RelayProperties p,Json json,KafkaConnector kafka,FtpConnector ftp,LocalConnector local,LogConnector logTarget,ActionConnector actions) {
        this.repo=repo;this.p=p;this.json=json;this.kafka=kafka;this.ftp=ftp;this.local=local;this.logTarget=logTarget;this.actions=actions;
    }
    @Override public synchronized void start() {
        if(running || !p.getWorker().isEnabled()) return;running=true;
        for(Kind kind:Kind.values()) {
            if(kind.name().startsWith("FILE_")&&!fileTransfers)continue;
            ExecutorService pool=Executors.newFixedThreadPool(p.getWorker().getThreadsPerType(),Thread.ofPlatform().daemon().name("relay-"+kind+"-",0).factory());
            executors.add(pool);
            for(int i=0;i<p.getWorker().getThreadsPerType();i++) pool.submit(()->loop(kind));
        }
    }
    private void loop(Kind kind) {
        long lastError=0;
        while(running && !Thread.currentThread().isInterrupted()) {
            try {if(!runOne(kind)) Thread.sleep(p.getWorker().getPollMs());}
            catch(InterruptedException e) {Thread.currentThread().interrupt();break;}
            catch(Exception e) {
                if(System.currentTimeMillis()-lastError>10000) {log.warn("Worker unavailable: kind={}, errorClass={}",kind,e.getClass().getSimpleName());lastError=System.currentTimeMillis();}
                try {Thread.sleep(1000);} catch(InterruptedException ignored) {Thread.currentThread().interrupt();break;}
            }
        }
    }
    /** Public for deterministic integration tests; production uses one occupied worker per claimed row. */
    public boolean runOne(Kind kind) {
        Optional<Delivery> picked=repo.claim(kind,p.getWorker().getLeaseSeconds());if(picked.isEmpty()) return false;
        execute(picked.get());return true;
    }
    private void execute(Delivery d) {
        AtomicBoolean valid=new AtomicBoolean(true);
        ScheduledFuture<?> heartbeat=heartbeats.scheduleAtFixedRate(()->{
            try {if(!repo.renew(d,p.getWorker().getLeaseSeconds())) valid.set(false);} catch(RuntimeException e) {valid.set(false);}
        },10,10,TimeUnit.SECONDS);
        try {
            Message m=repo.message(d.messageId()).orElseThrow();TargetPlan target=json.read(d.plan(),TargetPlan.class);
            if(m.outputPayload()==null && (m.fileRef()==null || m.fileRef().isBlank())) throw new DeliveryFailure("PAYLOAD_NOT_AVAILABLE",true,false);
            Map<String,Object> metadata=m.sourceMetadata()==null || m.sourceMetadata().isBlank()?Map.of():json.object(m.sourceMetadata().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Packet packet=new Packet(m.id(),d.id(),m.routeCode(),m.createdAt(),m.outputPayload()==null?null:Base64.getDecoder().decode(m.outputPayload()),m.fileRef(),m.originalName(),metadata);
            if(!valid.get()) return;
            Map<String,Object> result=switch(d.kind()) {
                case LOCAL, FILE_LOCAL -> local.deliver(target,packet);
                case FTP, FILE_FTP -> ftp.deliver(target,packet);
                // Preserve historical enum values for ledger reads, but never call a platform to deliver data.
                case PLATFORM_FTP -> throw new DeliveryFailure("LEGACY_PLATFORM_TARGET_REQUIRES_LOCAL_CONFIGURATION",false,false);
                case KAFKA -> kafka.deliver(target,packet);
                case FILE_KAFKA -> kafka.deliver(target,fileReference(packet));
                case LOG -> logTarget.deliver(target,packet);
                case ACTION -> actions.execute(target.action(),packet.bytes(),d.id(),m.owner());
            };
            if(valid.get() && repo.complete(d,json.write(result))) log.info("Delivery succeeded: id={}, target={}",d.id(),d.targetCode());
        } catch(DeliveryFailure e) {
            if(valid.get()) repo.fail(d,e.code,e.permanent,e.unknown,e.diagnostics);
            log.warn("Delivery failed: id={}, code={}, diagnostics={}",d.id(),e.code,e.diagnostics);
        } catch(Exception e) {
            if(valid.get()) repo.fail(d,"TARGET_EXECUTION_FAILED",false,!d.safe());
            log.warn("Delivery execution failed: id={}, errorClass={}",d.id(),e.getClass().getSimpleName());
            if(e instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {heartbeat.cancel(false);}
    }
    /** Binary bytes and private disk paths never enter a Kafka record. */
    public Packet fileReference(Packet packet) {
        Map<String,Object> ref=new LinkedHashMap<>(packet.metadata());
        ref.put("messageId",packet.messageId());ref.put("deliveryId",packet.deliveryId());ref.put("schemaVersion",1);
        return new Packet(packet.messageId(),packet.deliveryId(),packet.routeCode(),packet.createdAt(),json.bytes(ref),null,packet.originalName(),Map.of());
    }
    @Scheduled(fixedDelay=10000,initialDelay=10000)
    public void recover() {
        if(!p.getWorker().isEnabled()) return;
        try {repo.recoverExpired();repo.recoverFiles(p.getWorker().getMaxAttempts());}
        catch(RuntimeException e) {log.warn("Lease recovery unavailable: errorClass={}",e.getClass().getSimpleName());}
    }
    @Scheduled(fixedDelay=3600000,initialDelay=3600000)
    public void purge() {
        int days=p.getWorker().getPayloadRetentionDays();if(days<=0) return;
        try {int count=repo.purgeSuccessfulPayloads(repo.now()-TimeUnit.DAYS.toMillis(days));log.info("Successful inline payloads purged: count={}",count);}
        catch(RuntimeException e) {log.warn("Payload retention unavailable: errorClass={}",e.getClass().getSimpleName());}
    }
    @Override public synchronized void stop() {
        running=false;executors.forEach(ExecutorService::shutdown);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        for(ExecutorService e:executors) {
            try {e.awaitTermination(Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);} catch(InterruptedException ex) {Thread.currentThread().interrupt();}
            e.shutdownNow();
        }
        heartbeats.shutdownNow();
    }
    @Override public void stop(Runnable callback) {stop();callback.run();}
    @Override public boolean isRunning() {return running;}
    @Override public int getPhase() {return Integer.MAX_VALUE-200;}
    @jakarta.annotation.PreDestroy public void destroy() {stop();}
}
