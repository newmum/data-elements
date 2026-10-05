package com.linewell.dataelement.feature.dataquality.runtime;

import com.linewell.dataelement.feature.dataquality.config.DataQualityProperties;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.Evaluation;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.Plan;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.Range;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.Shard;
import com.linewell.dataelement.feature.dataquality.infrastructure.jdbc.JdbcDataQualityEngine;
import com.linewell.dataelement.feature.dataquality.infrastructure.persistence.DataQualityRepository;
import com.linewell.dataelement.platform.persistence.id.NumericId;
import com.linewell.dataelement.platform.tenant.application.TenantExecutionCatalog;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DataQualityWorker {

    private final DataQualityProperties properties;
    private final DataQualityRepository repository;
    private final JdbcDataQualityEngine engine;
    private final TenantExecutionCatalog tenantCatalog;
    private final String workerId;
    private final ExecutorService executor;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final AtomicInteger active = new AtomicInteger();

    public DataQualityWorker(DataQualityProperties properties, DataQualityRepository repository,
            JdbcDataQualityEngine engine, TenantExecutionCatalog tenantCatalog) {
        this.properties = properties;
        this.repository = repository;
        this.engine = engine;
        this.tenantCatalog = tenantCatalog;
        this.workerId = workerId();
        this.executor = Executors.newFixedThreadPool(Math.max(1, properties.getWorkerConcurrency()),
                Thread.ofPlatform().name("data-quality-worker-", 0).factory());
    }

    public void poll() {
        if (!properties.isEnabled() || !properties.getRole().workerEnabled()) return;
        for (String tenantId : tenantCatalog.activeTenantIds()) {
            try {
                TenantContext.run(tenantId, this::pollTenant);
            } catch (Exception exception) {
                log.error("Data quality worker poll failed, tenantId={}", tenantId, exception);
            }
        }
    }

    private void pollTenant() {
        repository.recover(LocalDateTime.now().minus(properties.getStaleLease()), properties.getMaxAttempts());
        initializeRuns();
        claimShards();
    }

    private void initializeRuns() {
        for (Map<String,Object> row : repository.pendingRuns(10)) {
            String tenantId=text(row.get("tenant_id")),runId=text(row.get("tid"));
            if(repository.claimRun(tenantId,runId,workerId)==0)continue;
            try { TenantContext.run(tenantId,()->initialize(tenantId,runId)); }
            catch(Exception e){repository.failRun(tenantId,runId,"DQ-INIT-FAILED",rootMessage(e),chain(e));}
        }
    }

    private void initialize(String tenantId,String runId){
        Plan plan=repository.loadRunPlan(tenantId,runId);
        Map<String,Object> precheck=engine.precheck(plan);
        if(!Boolean.TRUE.equals(precheck.get("success")))throw new IllegalStateException(String.valueOf(precheck.get("message")));
        var numericRange=plan.shardCount()>1?engine.numericRange(plan):null;
        List<Range> ranges=numericRange!=null?numericRange.split(plan.shardCount()):List.of(new Range(null,null,true));
        repository.resetShards(tenantId,runId);
        int number=0;
        for(Range range:ranges)repository.insertShard(tenantId,runId,NumericId.nextId(),number++,decimal(range.lower()),decimal(range.upper()),range.upperInclusive());
        repository.markRunRunning(tenantId,runId,ranges.size());
    }

    private void claimShards(){
        int available=Math.max(0,properties.getWorkerConcurrency()-active.get());
        if(available==0)return;
        for(Map<String,Object> row:repository.candidateShards(available*2)){
            if(active.get()>=properties.getWorkerConcurrency())return;
            String shardId=text(row.get("tid"));
            if(!inFlight.add(shardId))continue;
            if(repository.claimShard(shardId,workerId)==0){inFlight.remove(shardId);continue;}
            String tenantId=text(row.get("tenant_id")),runId=text(row.get("run_id"));
            active.incrementAndGet();
            executor.submit(()->{try{TenantContext.run(tenantId,()->execute(tenantId,runId,shardId));}finally{inFlight.remove(shardId);active.decrementAndGet();}});
        }
    }

    private void execute(String tenantId,String runId,String shardId){
        try{
            if(repository.cancelled(tenantId,runId)){repository.cancelRun(tenantId,runId);return;}
            Plan plan=repository.loadRunPlan(tenantId,runId);
            Shard shard=repository.shard(tenantId,runId,shardId);
            Evaluation evaluation=engine.evaluate(plan,shard);
            repository.saveEvaluation(tenantId,runId,shardId,evaluation);
            repository.finalizeRun(tenantId,runId);
        }catch(Exception e){
            boolean retry=repository.shardAttempt(tenantId,runId,shardId)<properties.getMaxAttempts()&&!repository.cancelled(tenantId,runId);
            repository.failShard(tenantId,runId,shardId,retry,rootMessage(e));
            if(!retry)repository.finalizeRun(tenantId,runId);
            log.error("Data quality shard failed tenantId={}, runId={}, shardId={}, retry={}",tenantId,runId,shardId,retry,e);
        }
    }

    @PreDestroy public void shutdown(){executor.shutdown();}
    private String workerId(){try{return InetAddress.getLocalHost().getHostName()+"-"+UUID.randomUUID();}catch(Exception e){return "dq-worker-"+UUID.randomUUID();}}
    private String decimal(java.math.BigDecimal value){return value==null?null:value.stripTrailingZeros().toPlainString();}
    private String text(Object value){return value==null?null:String.valueOf(value);}
    private String rootMessage(Throwable value){Throwable current=value;while(current.getCause()!=null&&current.getCause()!=current)current=current.getCause();return current.getMessage()==null?current.getClass().getSimpleName():current.getMessage();}
    private String chain(Throwable value){StringBuilder out=new StringBuilder();for(Throwable current=value;current!=null;current=current.getCause()){if(!out.isEmpty())out.append("\nCaused by: ");out.append(current.getClass().getName()).append(": ").append(current.getMessage());}return out.toString();}
}
