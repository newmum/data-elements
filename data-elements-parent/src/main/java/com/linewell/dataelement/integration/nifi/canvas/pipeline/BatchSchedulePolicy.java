package com.linewell.dataelement.integration.nifi.canvas.pipeline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigInteger;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** New batch-source defaults, persisted in the existing DSL; no per-node database reads. */
public final class BatchSchedulePolicy {
    public static final String AUTOMATIC = "DAILY_STAGGERED";
    public static final String ZONE = "Asia/Shanghai";
    private BatchSchedulePolicy() {}

    public static boolean isBatchSource(String key) {
        return key != null && key.startsWith("source.") && !"source.kafka".equals(key);
    }

    public static boolean automatic(Map<String, Object> config) {
        return config != null && AUTOMATIC.equals(config.get("batchSchedulePolicy"));
    }

    public static Map<String, Object> markDefault(Map<String, Object> config) {
        Map<String, Object> copy = new LinkedHashMap<>(config == null ? Map.of() : config);
        copy.put("batchSchedulePolicy", AUTOMATIC);
        copy.put("batchScheduleZone", ZONE);
        copy.put("schedulingStrategy", "CRON_DRIVEN");
        copy.put("schedulingPeriod", "");
        copy.remove("batchScheduleTime");
        return copy;
    }

    /** Registration's existing batch insert calls this in memory, without repository calls. */
    public static Map<String, Object> defaultConfig(String tenantId, String pipelineId, String nodeId,
                                                   String key, Map<String, Object> config) {
        if (!isBatchSource(key) || "CUSTOM".equals(text(config, "batchSchedulePolicy"))) return config;
        return assign(tenantId, pipelineId, nodeId, config);
    }

    private static Map<String, Object> assign(String tenantId, String pipelineId, String nodeId,
                                              Map<String, Object> config) {
        if (tenantId == null || tenantId.isBlank() || pipelineId == null || pipelineId.isBlank()
                || nodeId == null || nodeId.isBlank()) throw new IllegalArgumentException("错峰调度分配需要可信租户、流程和来源节点标识");
        try {
            // Stable, uniformly distributed daily phase; no clock, process-local random, or tenant-wide scan.
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    ("batch-daily-v1|" + tenantId + "|" + pipelineId + "|" + nodeId).getBytes(StandardCharsets.UTF_8));
            int second = new BigInteger(1, hash).mod(BigInteger.valueOf(86400)).intValue();
            LocalTime time = LocalTime.ofSecondOfDay(second);
            Map<String, Object> result = new LinkedHashMap<>(config == null ? Map.of() : config);
            result.put("batchSchedulePolicy", AUTOMATIC);
            result.put("batchScheduleTime", time.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            result.put("batchScheduleZone", ZONE);
            result.put("schedulingStrategy", "CRON_DRIVEN");
            result.put("schedulingPeriod", time.getSecond() + " " + time.getMinute() + " " + time.getHour() + " * * ?");
            return result;
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public static Pipeline.Dsl normalize(Pipeline.Dsl dsl, Pipeline.Dsl previous, String tenantId, String pipelineId) {
        if (dsl == null || dsl.nodes() == null) return dsl;
        Map<String, Pipeline.Node> old = new java.util.HashMap<>();
        if (previous != null && previous.nodes() != null) previous.nodes().forEach(n -> old.put(n.id(), n));
        boolean[] changed = {false};
        List<Pipeline.Node> nodes = dsl.nodes().stream().map(node -> {
            if (!isBatchSource(node.manifestKey()) || !"source".equals(node.category())) return node;
            Map<String, Object> config = node.config() == null ? Map.of() : node.config();
            boolean created = !old.containsKey(node.id());
            boolean pending = automatic(config) && text(config, "schedulingPeriod").isBlank();
            boolean newDefault = created && (automatic(config) || text(config, "schedulingPeriod").isBlank()
                    && !"CUSTOM".equals(text(config, "batchSchedulePolicy")));
            if (!newDefault && !pending) return node;
            changed[0] = true;
            return new Pipeline.Node(node.id(), node.manifestKey(), node.label(), node.category(), node.x(), node.y(),
                    assign(tenantId, pipelineId, node.id(), config));
        }).toList();
        return changed[0] ? new Pipeline.Dsl(dsl.version(), nodes, dsl.edges()) : dsl;
    }

    private static String text(Map<String, Object> config, String key) {
        Object value = config == null ? null : config.get(key);
        return value == null ? "" : value.toString().trim();
    }
}
