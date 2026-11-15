package com.linewell.dataelement.feature.dataaccess.domain;

public record RegisteredJdbcEndpoint(
        String datasourceId,
        String datasourceName,
        String tableId,
        String tableName,
        String jdbcUrl,
        String username,
        String password,
        String driverClassName
) {
    /** Build a parameterized SELECT template for this already authorized JDBC table. */
    public String lookupStatement(String keyColumn, String resultColumn) {
        String quote = jdbcUrl.startsWith("jdbc:mysql:") || jdbcUrl.startsWith("jdbc:mariadb:") ? "`" : "\"";
        String qualified = java.util.Arrays.stream(tableName.split("\\."))
                .map(part -> quotedIdentifier(part, quote))
                .collect(java.util.stream.Collectors.joining("."));
        return "SELECT " + quotedIdentifier(resultColumn, quote) + " FROM " + qualified
                + " WHERE " + quotedIdentifier(keyColumn, quote) + " = ?";
    }

    private static String quotedIdentifier(String identifier, String quote) {
        if (!identifier.matches("[\\p{L}_][\\p{L}\\p{N}_$#]{0,127}")) {
            throw new IllegalArgumentException("数据库字段标识无效");
        }
        return quote + identifier + quote;
    }
}

