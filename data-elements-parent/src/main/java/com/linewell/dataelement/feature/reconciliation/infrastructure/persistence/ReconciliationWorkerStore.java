package com.linewell.dataelement.feature.reconciliation.infrastructure.persistence;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Difference;
import com.linewell.dataelement.platform.persistence.id.NumericId;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Stats;
import com.linewell.dataelement.feature.reconciliation.infrastructure.persistence.mapper.ReconciliationWorkerMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class ReconciliationWorkerStore {

    private final ReconciliationWorkerMapper mapper;

    public ReconciliationWorkerStore(ReconciliationWorkerMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> pendingRuns(int limit) {
        return mapper.selectPendingRuns(Page.of(1, limit, false)).getRecords();
    }

    public int claimRun(String tenantId, String runId, String workerId) {
        return mapper.claimRun(params(
                "tenantId", tenantId,
                "runId", runId,
                "workerId", workerId,
                "now", LocalDateTime.now()
        ));
    }

    public Integer requestedBucketCount(String tenantId, String runId) {
        return mapper.selectRequestedBucketCount(tenantId, runId);
    }

    public void resetBuckets(String tenantId, String runId) {
        mapper.deleteBuckets(tenantId, runId);
    }

    public void insertBucket(
            String tenantId,
            String runId,
            String bucketId,
            int bucketNo,
            String keyLower,
            String keyUpper,
            boolean upperInclusive
    ) {
        mapper.insertBucket(params(
                "tid", bucketId,
                "tenantId", tenantId,
                "runId", runId,
                "bucketNo", bucketNo,
                "keyLower", keyLower,
                "keyUpper", keyUpper,
                "upperInclusive", upperInclusive ? 1 : 0,
                "now", LocalDateTime.now()
        ));
    }

    public void markRunRunning(String tenantId, String runId, int bucketTotal) {
        mapper.markRunRunning(params(
                "tenantId", tenantId,
                "runId", runId,
                "bucketTotal", bucketTotal,
                "now", LocalDateTime.now()
        ));
    }

    public List<Map<String, Object>> candidateBuckets(int limit) {
        return mapper.selectCandidateBuckets(Page.of(1, limit, false)).getRecords();
    }

    public int claimBucket(String bucketId, String workerId) {
        return mapper.claimBucket(params(
                "bucketId", bucketId,
                "workerId", workerId,
                "now", LocalDateTime.now()
        ));
    }

    public void completeBucket(
            String tenantId,
            String runId,
            String bucketId,
            Stats stats
    ) {
        mapper.completeBucket(statsParams(tenantId, runId, bucketId, stats));
    }

    public void cancelBucket(
            String tenantId,
            String runId,
            String bucketId,
            String errorMessage
    ) {
        mapper.cancelBucket(params(
                "tenantId", tenantId,
                "runId", runId,
                "bucketId", bucketId,
                "errorMessage", errorMessage,
                "now", LocalDateTime.now()
        ));
    }

    public Map<String, Object> bucket(
            String tenantId,
            String runId,
            String bucketId
    ) {
        return mapper.selectBucket(tenantId, runId, bucketId);
    }

    public Map<String, Object> bucketState(String tenantId, String runId) {
        return mapper.selectBucketState(tenantId, runId);
    }

    public void updateBucketCompleted(
            String tenantId,
            String runId,
            long completed
    ) {
        mapper.updateBucketCompleted(params(
                "tenantId", tenantId,
                "runId", runId,
                "bucketCompleted", completed,
                "now", LocalDateTime.now()
        ));
    }

    public Map<String, Object> bucketSums(String tenantId, String runId) {
        return mapper.selectBucketSums(tenantId, runId);
    }

    public void finishRun(
            String tenantId,
            String runId,
            String status,
            Map<String, Object> sums,
            BigDecimal consistencyRate,
            long completed
    ) {
        Map<String, Object> values = params(
                "tenantId", tenantId,
                "runId", runId,
                "status", status,
                "sourceCount", longValue(sums.get("source_count")),
                "targetCount", longValue(sums.get("target_count")),
                "matchedCount", longValue(sums.get("matched_count")),
                "missingTargetCount", longValue(sums.get("missing_target_count")),
                "extraTargetCount", longValue(sums.get("extra_target_count")),
                "valueMismatchCount", longValue(sums.get("value_mismatch_count")),
                "diffCount", longValue(sums.get("diff_count")),
                "consistencyRate", consistencyRate,
                "bucketCompleted", completed,
                "now", LocalDateTime.now()
        );
        mapper.finishRun(values);
    }

    public Integer attempt(String tenantId, String runId, String bucketId) {
        return mapper.selectAttempt(tenantId, runId, bucketId);
    }

    public void updateBucketFailure(
            String tenantId,
            String runId,
            String bucketId,
            boolean retry,
            String errorMessage
    ) {
        mapper.updateBucketFailure(params(
                "tenantId", tenantId,
                "runId", runId,
                "bucketId", bucketId,
                "status", retry ? "PENDING" : "FAILED",
                "retry", retry ? 1 : 0,
                "errorMessage", errorMessage,
                "now", LocalDateTime.now()
        ));
    }

    public void failRun(
            String tenantId,
            String runId,
            String errorCode,
            String errorMessage,
            String errorDetail,
            String traceId
    ) {
        mapper.failRun(params(
                "tenantId", tenantId,
                "runId", runId,
                "errorCode", errorCode,
                "errorMessage", errorMessage,
                "errorDetail", errorDetail,
                "traceId", traceId,
                "now", LocalDateTime.now()
        ));
    }

    public void cancelRun(String tenantId, String runId) {
        Map<String, Object> values = params(
                "tenantId", tenantId,
                "runId", runId,
                "now", LocalDateTime.now()
        );
        mapper.cancelRun(values);
        mapper.cancelPendingBuckets(values);
    }

    public boolean isCancelled(String tenantId, String runId) {
        Integer value = mapper.selectCancelRequested(tenantId, runId);
        return value != null && value == 1;
    }

    public void recoverStaleClaims(LocalDateTime staleBefore, int maxAttempts) {
        mapper.recoverInitializingRuns(staleBefore);
        mapper.recoverRunningBuckets(maxAttempts, staleBefore);
    }

    public List<Map<String, Object>> cancelledRuns(int limit) {
        return mapper.selectCancelledRuns(Page.of(1, limit, false)).getRecords();
    }

    public List<Map<String, Object>> terminalRuns(int limit) {
        return mapper.selectTerminalRuns(Page.of(1, limit, false)).getRecords();
    }

    public long countRunDiffs(String tenantId, String runId) {
        return mapper.countRunDiffs(tenantId, runId);
    }

    public void checkpoint(
            String tenantId,
            String runId,
            String bucketId,
            String checkpointSourceKey,
            String checkpointTargetKey,
            Stats stats
    ) {
        Map<String, Object> values =
                statsParams(tenantId, runId, bucketId, stats);
        values.put("checkpointSourceKey", checkpointSourceKey);
        values.put("checkpointTargetKey", checkpointTargetKey);
        mapper.updateCheckpoint(values);
    }

    public void insertDifferences(
            String tenantId,
            String runId,
            String bucketId,
            List<Difference> differences,
            java.util.function.Function<Difference, String> hashFunction
    ) {
        if (differences.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> rows = new ArrayList<>(differences.size());
        for (Difference difference : differences) {
            rows.add(params(
                    "tid", NumericId.nextId(),
                    "tenantId", tenantId,
                    "runId", runId,
                    "bucketId", bucketId,
                    "diffHash", hashFunction.apply(difference),
                    "businessKey", difference.businessKey(),
                    "diffType", difference.type(),
                    "fieldName", difference.fieldName(),
                    "sourceValueMasked", difference.sourceValueMasked(),
                    "targetValueMasked", difference.targetValueMasked(),
                    "sourceRowHash", difference.sourceRowHash(),
                    "targetRowHash", difference.targetRowHash(),
                    "createdTime", now
            ));
        }
        mapper.insertDiffBatch(rows);
    }

    private Map<String, Object> statsParams(
            String tenantId,
            String runId,
            String bucketId,
            Stats stats
    ) {
        return params(
                "tenantId", tenantId,
                "runId", runId,
                "bucketId", bucketId,
                "sourceCount", stats.sourceCount(),
                "targetCount", stats.targetCount(),
                "matchedCount", stats.matchedCount(),
                "missingTargetCount", stats.missingTargetCount(),
                "extraTargetCount", stats.extraTargetCount(),
                "valueMismatchCount", stats.valueMismatchCount(),
                "diffCount", stats.diffCount(),
                "now", LocalDateTime.now()
        );
    }

    private Map<String, Object> params(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return values;
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
