package com.linewell.dataelement.platform.tenant.infrastructure.magic;

import com.linewell.dataelement.platform.tenant.config.TenantDatabaseProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.sql.DataSource;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.dialect.Dialect;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;

/**
 * Resolves Magic API pagination syntax from the datasource selected for the
 * current tenant instead of retaining the dialect of the first request.
 */
final class TenantRoutingMagicDialect implements Dialect {

    private final DataSource routingDataSource;
    private final TenantDatabaseProperties tenantDatabaseProperties;
    private final DialectAdapter dialectAdapter = new DialectAdapter();
    private final ConcurrentMap<String, Dialect> dialects = new ConcurrentHashMap<>();

    TenantRoutingMagicDialect(
            DataSource routingDataSource,
            TenantDatabaseProperties tenantDatabaseProperties
    ) {
        this.routingDataSource = routingDataSource;
        this.tenantDatabaseProperties = tenantDatabaseProperties;
    }

    @Override
    public String getCountSql(String sql) {
        return currentDialect().getCountSql(sql);
    }

    @Override
    public String getPageSql(String sql, BoundSql boundSql, long limit, long offset) {
        return currentDialect().getPageSql(sql, boundSql, limit, offset);
    }

    private Dialect currentDialect() {
        String key = currentDataSourceKey();
        return dialects.computeIfAbsent(key, ignored -> detectDialect());
    }

    private String currentDataSourceKey() {
        if (TenantContext.usesControlDatabase()) {
            return TenantDatabaseProperties.CONTROL_KEY;
        }
        return tenantDatabaseProperties.resolve(TenantContext.getTenantId());
    }

    private Dialect detectDialect() {
        try (Connection connection = routingDataSource.getConnection()) {
            Dialect dialect = dialectAdapter.getDialectFromConnection(connection);
            if (dialect == null) {
                throw new IllegalStateException("Unable to resolve Magic SQL dialect from current tenant datasource");
            }
            return dialect;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to connect to current tenant datasource", exception);
        }
    }
}
