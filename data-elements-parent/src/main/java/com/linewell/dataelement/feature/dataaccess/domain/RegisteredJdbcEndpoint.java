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
}

