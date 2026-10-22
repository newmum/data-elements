package com.linewell.dataelement.integration.nifi.canvas.compile;

import java.util.Map;

/** Native date-fetch contract, using NiFi descriptor names rather than UI labels. */
public final class DateTableFetchPolicy {
    public static final String TYPE = "com.linewell.microservice.processors.database.GenerateDateTableFetch";
    public static final String BUNDLE_VERSION = "2.9.0";
    public static final String PARTITION_SIZE = "gen-table-fetch-partition-size";
    public static final String PARTITION_COLUMN = "gen-table-column-for-val-partitioning";

    private DateTableFetchPolicy() {}

    public static boolean isTableFetch(String type) {
        return type != null && (type.endsWith(".GenerateTableFetch") || type.endsWith(".GenerateDateTableFetch"));
    }

    public static String timestamp(Map<String, Object> config) {
        String column = SyncPolicy.text(config, "incrementalColumn");
        if (!column.matches("[A-Za-z_][A-Za-z0-9_$]*")) {
            throw new IllegalStateException("时间分片来源缺少有效的时间戳字段，请在来源组件填写时间戳列；登记集成流程可从登记配置带入，不能用主键代替时间戳");
        }
        return column;
    }

    public static void apply(Map<String, Object> config, Map<String, String> properties) {
        String timestamp = timestamp(config);
        if (!SyncPolicy.text(config, "tieBreakerColumn").isBlank()) {
            throw new IllegalStateException("GenerateDateTableFetch 按来源配置的时间戳分片，不支持次级水位列，请清空次级增量字段后发布");
        }
        properties.put(PARTITION_COLUMN, timestamp);
        properties.put(PARTITION_SIZE, "10000");
        properties.put("fetch-partition-type", "2"); // Installed extension: year=1, month=2, day=3, hour=4.
        properties.put("fetch-partition-type-count", "1");
        properties.put("Max Wait Time", "0 seconds");
        // The existing 2.9.0 custom processor does not declare this property.
        // Do not send a standard NiFi processor's setting as an ignored dynamic property.
        properties.remove("Initial Load Strategy");
        properties.put("max-date", null);
        properties.put("min-date", null);
        move(properties, "Database Type", "db-fetch-db-type");
        move(properties, "Additional WHERE Clause", "db-fetch-where-clause");
        move(properties, "Custom ORDER BY Column", "gen-table-custom-orderby-column");
        properties.remove("Partition Size");
    }

    private static void move(Map<String, String> properties, String label, String descriptor) {
        if (properties.containsKey(label)) properties.put(descriptor, properties.remove(label));
    }
}
