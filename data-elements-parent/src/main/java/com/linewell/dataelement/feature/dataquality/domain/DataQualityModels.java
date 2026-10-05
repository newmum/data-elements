package com.linewell.dataelement.feature.dataquality.domain;

import com.linewell.dataelement.feature.dataaccess.domain.RegisteredJdbcEndpoint;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class DataQualityModels {

    private DataQualityModels() { }

    public record Rule(
            String id,
            String name,
            String type,
            String dimension,
            String columnName,
            String relatedColumn,
            Map<String, Object> parameters,
            String severity,
            int weight
    ) { }

    public record Plan(
            String tenantId,
            String runId,
            String taskId,
            String shardKey,
            int shardCount,
            int sampleLimit,
            RegisteredJdbcEndpoint endpoint,
            List<Rule> rules,
            int timeoutSeconds
    ) {
        public Plan(String tenantId,String runId,String taskId,String shardKey,int shardCount,int sampleLimit,RegisteredJdbcEndpoint endpoint,List<Rule> rules) {
            this(tenantId,runId,taskId,shardKey,shardCount,sampleLimit,endpoint,rules,7200);
        }
    }

    public record Shard(
            String id,
            int number,
            String keyLower,
            String keyUpper,
            boolean upperInclusive
    ) { }

    public record NumericRange(BigDecimal minimum, BigDecimal maximum) {
        public List<Range> split(int requested) {
            if (minimum == null || maximum == null || requested <= 1
                    || minimum.compareTo(maximum) >= 0) {
                return List.of(new Range(null, null, true));
            }
            BigDecimal span = maximum.subtract(minimum).add(BigDecimal.ONE);
            int count = span.compareTo(BigDecimal.valueOf(requested)) < 0
                    ? Math.max(1, span.intValue())
                    : requested;
            BigDecimal step = span.divide(
                    BigDecimal.valueOf(count), 0, java.math.RoundingMode.CEILING
            );
            java.util.ArrayList<Range> ranges = new java.util.ArrayList<>();
            BigDecimal lower = minimum;
            for (int index = 0; index < count && lower.compareTo(maximum) <= 0; index++) {
                BigDecimal upper = lower.add(step);
                boolean last = upper.compareTo(maximum) > 0 || index == count - 1;
                ranges.add(new Range(lower, last ? maximum : upper, last));
                lower = upper;
            }
            return ranges;
        }
    }

    public record Range(BigDecimal lower, BigDecimal upper, boolean upperInclusive) { }

    public record MetricResult(
            Rule rule,
            long checkedCount,
            long violationCount,
            BigDecimal passRate,
            BigDecimal score,
            Map<String, Object> profile
    ) {
        public MetricResult(Rule rule, long checkedCount, long violationCount, BigDecimal passRate, BigDecimal score) {
            this(rule, checkedCount, violationCount, passRate, score, Map.of());
        }
    }

    public record IssueSample(
            Rule rule,
            String rowKey,
            String issueValue,
            String rowSnapshot
    ) { }

    public record Evaluation(
            long rowCount,
            long checkedCount,
            long violationCount,
            List<MetricResult> metrics,
            List<IssueSample> samples
    ) { }
}
