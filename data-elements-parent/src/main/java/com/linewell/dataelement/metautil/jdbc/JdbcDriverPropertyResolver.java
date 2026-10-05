package com.linewell.dataelement.metautil.jdbc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Resolves vendor JDBC driver properties from a registered datasource configuration.
 *
 * <p>Datasource custom-property panels persist their entries directly in {@code pool_cfg}.
 * This resolver also accepts the normalized {@code jdbcProperties} object used by the
 * runtime pipeline DSL. Only namespaced JDBC properties are accepted: this prevents a
 * custom value from replacing a NiFi DBCP service's built-in properties such as its
 * connection URL or password.</p>
 */
public final class JdbcDriverPropertyResolver {

    private static final String JDBC_PROPERTIES_KEY = "jdbcProperties";
    private static final String POOL_CONFIG_KEY = "poolCfg";
    private static final String POOL_CONFIG_SNAKE_CASE_KEY = "pool_cfg";
    private static final Pattern NAMESPACED_PROPERTY =
            Pattern.compile("[A-Za-z][A-Za-z0-9_-]*(?:\\.[A-Za-z][A-Za-z0-9_-]*)+");

    private JdbcDriverPropertyResolver() {
    }

    /**
     * Returns valid vendor JDBC properties from direct custom entries and the normalized map.
     * Direct properties preserve compatibility with the existing data-source custom-property
     * panel, for example {@code oracle.jdbc.encryption_client=required}.
     */
    public static Map<String, String> resolve(Map<String, ?> config) {
        Map<String, String> resolved = new LinkedHashMap<>();
        if (config == null || config.isEmpty()) {
            return resolved;
        }

        copyMapValues(resolved, config.get(JDBC_PROPERTIES_KEY));
        copyMapValues(resolved, config.get(POOL_CONFIG_KEY));
        copyMapValues(resolved, config.get(POOL_CONFIG_SNAKE_CASE_KEY));
        config.forEach((key, value) -> {
            if (!JDBC_PROPERTIES_KEY.equals(key)
                    && !POOL_CONFIG_KEY.equals(key)
                    && !POOL_CONFIG_SNAKE_CASE_KEY.equals(key)) {
                put(resolved, key, value);
            }
        });
        return resolved;
    }

    /** Adds resolved driver properties to JDBC {@link Properties}. */
    public static void apply(Properties target, Map<String, ?> config) {
        if (target == null) {
            return;
        }
        resolve(config).forEach(target::setProperty);
    }

    private static void put(Map<String, String> target, Object rawKey, Object rawValue) {
        if (rawKey == null || rawValue == null) {
            return;
        }
        String key = String.valueOf(rawKey).trim();
        String value = String.valueOf(rawValue).trim();
        if (NAMESPACED_PROPERTY.matcher(key).matches() && !value.isEmpty()) {
            target.put(key, value);
        }
    }

    private static void copyMapValues(Map<String, String> target, Object candidate) {
        if (candidate instanceof Map<?, ?> values) {
            values.forEach((key, value) -> put(target, key, value));
        }
    }
}
