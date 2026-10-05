package com.linewell.dataelement.feature.reconciliation.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class ReconciliationModels {

    private ReconciliationModels() {
    }

    public record Endpoint(
            String datasourceId,
            String tableId,
            String jdbcUrl,
            String username,
            String password,
            String driverClassName,
            String tableName
    ) {
    }

    public record FieldRule(
            String sourceField,
            String targetField,
            String sourceDataType,
            String targetDataType,
            boolean compareEnabled,
            String normalization,
            BigDecimal numericTolerance,
            String datePrecision,
            Boolean nullEqualsEmpty,
            String maskRule
    ) {
    }

    public record Plan(
            String tenantId,
            String runId,
            String policyId,
            String compareMode,
            List<String> sourceKeys,
            List<String> targetKeys,
            String sourceIncrementField,
            String targetIncrementField,
            String lowerWatermark,
            String upperWatermark,
            int fetchSize,
            long diffLimit,
            BigDecimal numericTolerance,
            boolean nullEqualsEmpty,
            boolean trimStrings,
            boolean ignoreCase,
            Endpoint source,
            Endpoint target,
            List<FieldRule> rules
    ) {
    }

    public record Bucket(
            String id,
            int bucketNo,
            String keyLower,
            String keyUpper,
            boolean upperInclusive,
            List<Object> checkpointSourceKey,
            List<Object> checkpointTargetKey,
            Stats initialStats
    ) {
    }

    public record Row(List<Object> key, Map<String, Object> values) {
    }

    public record Difference(
            String businessKey,
            String type,
            String fieldName,
            String sourceValueMasked,
            String targetValueMasked,
            String sourceRowHash,
            String targetRowHash
    ) {
    }

    public static final class Stats {

        private long sourceCount;
        private long targetCount;
        private long matchedCount;
        private long missingTargetCount;
        private long extraTargetCount;
        private long valueMismatchCount;
        private long duplicateKeyCount;
        private long diffCount;

        public Stats() {
        }

        public Stats(
                long sourceCount,
                long targetCount,
                long matchedCount,
                long missingTargetCount,
                long extraTargetCount,
                long valueMismatchCount,
                long duplicateKeyCount,
                long diffCount
        ) {
            this.sourceCount = sourceCount;
            this.targetCount = targetCount;
            this.matchedCount = matchedCount;
            this.missingTargetCount = missingTargetCount;
            this.extraTargetCount = extraTargetCount;
            this.valueMismatchCount = valueMismatchCount;
            this.duplicateKeyCount = duplicateKeyCount;
            this.diffCount = diffCount;
        }

        public long sourceCount() {
            return sourceCount;
        }

        public long targetCount() {
            return targetCount;
        }

        public long matchedCount() {
            return matchedCount;
        }

        public long missingTargetCount() {
            return missingTargetCount;
        }

        public long extraTargetCount() {
            return extraTargetCount;
        }

        public long valueMismatchCount() {
            return valueMismatchCount;
        }

        public long duplicateKeyCount() {
            return duplicateKeyCount;
        }

        public long diffCount() {
            return diffCount;
        }

        public void source() {
            sourceCount++;
        }

        public void addSource(long count) {
            sourceCount += count;
        }

        public void target() {
            targetCount++;
        }

        public void addTarget(long count) {
            targetCount += count;
        }

        public void matched() {
            matchedCount++;
        }

        public void addMatched(long count) {
            matchedCount += count;
        }

        public void missingTarget() {
            missingTargetCount++;
            diffCount++;
        }

        public void addMissingTarget(long count) {
            missingTargetCount += count;
            diffCount += count;
        }

        public void extraTarget() {
            extraTargetCount++;
            diffCount++;
        }

        public void addExtraTarget(long count) {
            extraTargetCount += count;
            diffCount += count;
        }

        public void valueMismatch() {
            valueMismatchCount++;
            diffCount++;
        }

        public void duplicateKey() {
            duplicateKeyCount++;
            diffCount++;
        }
    }

    public interface Listener {

        void difference(Difference difference);

        void checkpoint(List<Object> sourceKey, List<Object> targetKey, Stats stats);

        boolean cancelled();
    }
}
