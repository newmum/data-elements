package com.linewell.dataelement.feature.reconciliation.runtime;

import com.linewell.dataelement.feature.reconciliation.config.ReconciliationProperties;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Bucket;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Difference;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Listener;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Plan;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Stats;
import com.linewell.dataelement.feature.reconciliation.infrastructure.jdbc.JdbcReconciliationEngine;
import com.linewell.dataelement.feature.reconciliation.infrastructure.jdbc.JdbcReconciliationEngine.NumericRange;
import com.linewell.dataelement.feature.reconciliation.infrastructure.jdbc.JdbcReconciliationEngine.RangeBucket;
import com.linewell.dataelement.feature.reconciliation.infrastructure.jdbc.JdbcReconciliationEngine.ReconciliationCancelledException;
import com.linewell.dataelement.feature.reconciliation.infrastructure.persistence.ReconciliationRepository;
import com.linewell.dataelement.feature.reconciliation.infrastructure.persistence.ReconciliationWorkerStore;
import com.linewell.dataelement.platform.persistence.id.NumericId;
import com.linewell.dataelement.platform.tenant.application.TenantExecutionCatalog;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Independently deployable reconciliation execution role.
 *
 * <p>Run the same jar with {@code data-element.reconciliation.role=WORKER}
 * on a host that can reach source and target databases.</p>
 */
@Slf4j
@Component
public class ReconciliationWorker {

    private final ReconciliationProperties properties;
    private final ReconciliationRepository repository;
    private final JdbcReconciliationEngine engine;
    private final ReconciliationWorkerStore store;
    private final TransactionTemplate transactions;
    private final TenantExecutionCatalog tenantCatalog;
    private final String workerId;
    private final ExecutorService executor;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final AtomicInteger active = new AtomicInteger();

    public ReconciliationWorker(
            ReconciliationProperties properties,
            ReconciliationRepository repository,
            JdbcReconciliationEngine engine,
            ReconciliationWorkerStore store,
            TransactionTemplate transactions,
            TenantExecutionCatalog tenantCatalog
    ) {
        this.properties = properties;
        this.repository = repository;
        this.engine = engine;
        this.store = store;
        this.transactions = transactions;
        this.tenantCatalog = tenantCatalog;
        this.workerId = resolveWorkerId();
        this.executor = Executors.newFixedThreadPool(
                Math.max(1, properties.getWorkerConcurrency()),
                Thread.ofPlatform().name("reconcile-worker-", 0).factory()
        );
    }

    public void poll() {
        if (!properties.isEnabled() || !properties.getRole().workerEnabled()) {
            return;
        }
        for (String tenantId : tenantCatalog.activeTenantIds()) {
            try {
                TenantContext.run(tenantId, this::pollTenant);
            } catch (Exception exception) {
                log.error("Reconciliation worker poll failed, tenantId={}", tenantId, exception);
            }
        }
    }

    private void pollTenant() {
        recoverStaleClaims();
        initializePendingRuns();
        claimBuckets();
        settleTerminalRuns();
    }

    private void initializePendingRuns() {
        List<Map<String, Object>> runs = store.pendingRuns(10);
        for (Map<String, Object> row : runs) {
            String runId = text(row.get("tid"));
            String tenantId = text(row.get("tenant_id"));
            int claimed = store.claimRun(tenantId, runId, workerId);
            if (claimed == 0) {
                continue;
            }
            try {
                TenantContext.run(tenantId, () -> initializeRun(tenantId, runId));
            } catch (Exception e) {
                failRun(tenantId, runId, "RECONCILE-INIT-FAILED", e);
            }
        }
    }

    private void initializeRun(String tenantId, String runId) {
        Plan plan = repository.loadPlan(tenantId, runId);
        Map<String, Object> precheck = engine.precheck(plan);
        if (!Boolean.TRUE.equals(precheck.get("success"))) {
            throw new IllegalStateException(String.valueOf(precheck.get("message")));
        }
        Integer requestedBuckets = store.requestedBucketCount(tenantId, runId);
        int bucketCount = Math.max(1, Math.min(
                requestedBuckets == null ? 1 : requestedBuckets,
                properties.getMaxBucketCount()
        ));
        NumericRange range = bucketCount > 1 ? engine.numericRange(plan) : null;
        List<RangeBucket> ranges = range == null
                ? List.of(new RangeBucket(null, null, true))
                : range.split(bucketCount);
        transactions.executeWithoutResult(status -> {
            store.resetBuckets(tenantId, runId);
            int bucketNo = 0;
            for (RangeBucket item : ranges) {
                store.insertBucket(
                        tenantId,
                        runId,
                        NumericId.nextId(),
                        bucketNo++,
                        decimalText(item.lower()),
                        decimalText(item.upper()),
                        item.upperInclusive()
                );
            }
            store.markRunRunning(tenantId, runId, ranges.size());
        });
    }

    private void claimBuckets() {
        int available = Math.max(0, properties.getWorkerConcurrency() - active.get());
        if (available == 0) {
            return;
        }
        List<Map<String, Object>> candidates = store.candidateBuckets(available * 2);
        for (Map<String, Object> row : candidates) {
            if (active.get() >= properties.getWorkerConcurrency()) {
                return;
            }
            String bucketId = text(row.get("tid"));
            if (!inFlight.add(bucketId)) {
                continue;
            }
            int claimed = store.claimBucket(bucketId, workerId);
            if (claimed == 0) {
                inFlight.remove(bucketId);
                continue;
            }
            String tenantId = text(row.get("tenant_id"));
            String runId = text(row.get("run_id"));
            active.incrementAndGet();
            executor.submit(() -> {
                try {
                    TenantContext.run(
                            tenantId,
                            () -> executeBucket(tenantId, runId, bucketId)
                    );
                } finally {
                    inFlight.remove(bucketId);
                    active.decrementAndGet();
                }
            });
        }
    }

    private void executeBucket(String tenantId, String runId, String bucketId) {
        try {
            Plan plan = repository.loadPlan(tenantId, runId);
            Bucket bucket = loadBucket(tenantId, runId, bucketId);
            BucketListener listener = new BucketListener(plan, bucket);
            Stats stats = engine.compare(plan, bucket, listener);
            listener.flush();
            store.completeBucket(tenantId, runId, bucketId, stats);
            finalizeRun(tenantId, runId);
        } catch (ReconciliationCancelledException cancelled) {
            store.cancelBucket(
                    tenantId,
                    runId,
                    bucketId,
                    cancelled.getMessage()
            );
            cancelRun(tenantId, runId);
        } catch (Exception e) {
            handleBucketFailure(tenantId, runId, bucketId, e);
        }
    }

    private Bucket loadBucket(String tenantId, String runId, String bucketId) {
        Map<String, Object> row = store.bucket(tenantId, runId, bucketId);
        Stats stats = new Stats(
                longValue(row.get("source_count")),
                longValue(row.get("target_count")),
                longValue(row.get("matched_count")),
                longValue(row.get("missing_target_count")),
                longValue(row.get("extra_target_count")),
                longValue(row.get("value_mismatch_count")),
                0,
                longValue(row.get("diff_count"))
        );
        return new Bucket(
                bucketId,
                integer(row.get("bucket_no")),
                text(row.get("key_lower")),
                text(row.get("key_upper")),
                integer(row.get("upper_inclusive")) == 1,
                repository.jsonList(row.get("checkpoint_source_key")),
                repository.jsonList(row.get("checkpoint_target_key")),
                stats
        );
    }

    private void finalizeRun(String tenantId, String runId) {
        transactions.executeWithoutResult(status -> {
            Map<String, Object> state = store.bucketState(tenantId, runId);
            long total = longValue(state.get("total"));
            long completed = longValue(state.get("completed"));
            long failed = longValue(state.get("failed"));
            long remaining = longValue(state.get("active"));
            store.updateBucketCompleted(tenantId, runId, completed);
            if (remaining > 0 || completed + failed < total) {
                return;
            }
            Map<String, Object> sums = store.bucketSums(tenantId, runId);
            long source = longValue(sums.get("source_count"));
            long target = longValue(sums.get("target_count"));
            long matched = longValue(sums.get("matched_count"));
            long diff = longValue(sums.get("diff_count"));
            BigDecimal denominator = BigDecimal.valueOf(Math.max(source, target));
            BigDecimal rate = denominator.signum() == 0
                    ? BigDecimal.valueOf(100)
                    : BigDecimal.valueOf(matched)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(denominator, 6, java.math.RoundingMode.HALF_UP);
            String runStatus = failed > 0
                    ? "FAILED"
                    : diff == 0 ? "CONSISTENT" : "INCONSISTENT";
            store.finishRun(
                    tenantId,
                    runId,
                    runStatus,
                    sums,
                    rate,
                    completed
            );
        });
    }

    private void handleBucketFailure(
            String tenantId,
            String runId,
            String bucketId,
            Exception exception
    ) {
        Integer attempt = store.attempt(tenantId, runId, bucketId);
        boolean retry = attempt != null
                && attempt < properties.getMaxAttempts()
                && !isCancelled(tenantId, runId);
        store.updateBucketFailure(
                tenantId,
                runId,
                bucketId,
                retry,
                rootMessage(exception)
        );
        if (!retry) {
            failRun(tenantId, runId, "RECONCILE-BUCKET-FAILED", exception);
        }
        log.error(
                "Reconciliation bucket failed, tenantId={}, runId={}, bucketId={}, retry={}",
                tenantId,
                runId,
                bucketId,
                retry,
                exception
        );
    }

    private void failRun(
            String tenantId,
            String runId,
            String errorCode,
            Exception exception
    ) {
        store.failRun(
                tenantId,
                runId,
                errorCode,
                rootMessage(exception),
                stackSummary(exception),
                UUID.randomUUID().toString()
        );
    }

    private void cancelRun(String tenantId, String runId) {
        store.cancelRun(tenantId, runId);
    }

    private boolean isCancelled(String tenantId, String runId) {
        return store.isCancelled(tenantId, runId);
    }

    private void recoverStaleClaims() {
        store.recoverStaleClaims(
                LocalDateTime.now().minusMinutes(10),
                properties.getMaxAttempts()
        );
    }

    private void settleTerminalRuns() {
        List<Map<String, Object>> cancelledRuns = store.cancelledRuns(50);
        for (Map<String, Object> row : cancelledRuns) {
            cancelRun(text(row.get("tenant_id")), text(row.get("tid")));
        }

        List<Map<String, Object>> terminalRuns = store.terminalRuns(50);
        for (Map<String, Object> row : terminalRuns) {
            finalizeRun(text(row.get("tenant_id")), text(row.get("tid")));
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    private final class BucketListener implements Listener {

        private final Plan plan;
        private final Bucket bucket;
        private final List<Difference> pending = new ArrayList<>();
        private long storedDiffCount;
        private long lastCancelCheck;
        private boolean cancelled;

        private BucketListener(Plan plan, Bucket bucket) {
            this.plan = plan;
            this.bucket = bucket;
            this.storedDiffCount = store.countRunDiffs(
                    plan.tenantId(),
                    plan.runId()
            );
        }

        @Override
        public void difference(Difference difference) {
            if (difference == null
                    || storedDiffCount + pending.size() >= plan.diffLimit()) {
                return;
            }
            pending.add(difference);
            if (pending.size() >= properties.getDiffWriteBatchSize()) {
                flush();
            }
        }

        @Override
        public void checkpoint(
                List<Object> sourceKey,
                List<Object> targetKey,
                Stats stats
        ) {
            flush();
            store.checkpoint(
                    plan.tenantId(),
                    plan.runId(),
                    bucket.id(),
                    repository.toJson(sourceKey == null ? List.of() : sourceKey),
                    repository.toJson(targetKey == null ? List.of() : targetKey),
                    stats
            );
        }

        @Override
        public boolean cancelled() {
            long now = System.currentTimeMillis();
            if (now - lastCancelCheck > 1_000L) {
                cancelled = isCancelled(plan.tenantId(), plan.runId());
                lastCancelCheck = now;
            }
            return cancelled;
        }

        private void flush() {
            if (pending.isEmpty()) {
                return;
            }
            store.insertDifferences(
                    plan.tenantId(),
                    plan.runId(),
                    bucket.id(),
                    pending,
                    difference -> sha256(
                            plan.runId() + "|" + difference.businessKey() + "|"
                                    + difference.type() + "|"
                                    + String.valueOf(difference.fieldName())
                    )
            );
            storedDiffCount += pending.size();
            pending.clear();
        }
    }

    private String resolveWorkerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID();
        } catch (Exception e) {
            return "worker-" + UUID.randomUUID();
        }
    }

    private String decimalText(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private int integer(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private String stackSummary(Throwable throwable) {
        StringBuilder text = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < 10) {
            if (!text.isEmpty()) {
                text.append("\nCaused by: ");
            }
            text.append(current.getClass().getName())
                    .append(": ")
                    .append(current.getMessage());
            current = current.getCause();
        }
        return text.toString();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
