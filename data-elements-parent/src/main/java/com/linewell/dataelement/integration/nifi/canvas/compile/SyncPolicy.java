package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Saved DSL is the only source of synchronization and cleanup policy. */
public final class SyncPolicy {
    private SyncPolicy() {}
    public static String text(Map<String, Object> cfg, String key) {
        Object value = cfg == null ? null : cfg.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
    public static String mode(Map<String, Object> cfg) {
        String explicit = text(cfg, "syncMode").toUpperCase(Locale.ROOT);
        if ("INCR".equals(explicit)) explicit = "INCREMENTAL";
        String mode = explicit.isBlank() ? (text(cfg, "incrementalColumn").isBlank() ? "FULL" : "INCREMENTAL") : explicit;
        if (!List.of("FULL", "INCREMENTAL", "FULL_THEN_INCR", "PERIODIC_FULL").contains(mode)) {
            throw new IllegalStateException("不支持的同步方式：" + mode);
        }
        return mode;
    }
    public static String fullStrategy(Map<String, Object> cfg) {
        String value = text(cfg, "fullSyncStrategy").toUpperCase(Locale.ROOT);
        if (value.isBlank()) return "UPSERT";
        if (!List.of("UPSERT", "TRUNCATE_RELOAD").contains(value)) throw new IllegalStateException("不支持的全量写入策略：" + value);
        return value;
    }
    public static Pipeline.Node periodicSource(Pipeline p) {
        if (p.dsl() == null || p.dsl().nodes() == null) return null;
        List<Pipeline.Node> sources = p.dsl().nodes().stream().filter(n -> "source".equals(n.category())).toList();
        Pipeline.Node periodic = sources.stream().filter(n -> "PERIODIC_FULL".equals(mode(n.config()))).findFirst().orElse(null);
        if (periodic != null && sources.size() != 1) throw new IllegalStateException("定时全量任务必须只有一个数据库来源节点；多来源请拆分独立任务");
        return periodic;
    }
    public static boolean needsCleanupConfirmation(Pipeline p) {
        if (p.dsl() == null || p.dsl().nodes() == null) return false;
        return p.dsl().nodes().stream().filter(n -> "source".equals(n.category())).anyMatch(n ->
                (List.of("FULL", "FULL_THEN_INCR").contains(mode(n.config())) && Boolean.parseBoolean(text(n.config(), "deleteTargetData")))
                || ("PERIODIC_FULL".equals(mode(n.config())) && "TRUNCATE_RELOAD".equals(fullStrategy(n.config()))));
    }
}
