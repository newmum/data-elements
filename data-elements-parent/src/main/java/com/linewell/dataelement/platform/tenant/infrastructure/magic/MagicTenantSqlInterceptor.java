package com.linewell.dataelement.platform.tenant.infrastructure.magic;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.mybatis.PlatformTenantLineHandler;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;

/**
 * Applies tenant predicates to Magic SQL executed against the platform database.
 */
@Component
@Order(-100)
public class MagicTenantSqlInterceptor implements SQLInterceptor {

    private final TenantSqlRewriter rewriter;
    private final TenantProperties properties;

    public MagicTenantSqlInterceptor(
            PlatformTenantLineHandler tenantLineHandler,
            TenantProperties properties
    ) {
        this.rewriter = new TenantSqlRewriter(tenantLineHandler);
        this.properties = properties;
    }

    @Override
    public void preHandle(BoundSql boundSql, RequestEntity requestEntity) {
        if (!properties.isEnabled()
                || TenantContext.isIgnored()
                || TenantContext.getTenantId() == null
                || boundSql == null
                || boundSql.getSql() == null
                || !isPrimaryDataSource(boundSql)) {
            return;
        }
        boundSql.setSql(rewriter.rewrite(boundSql.getSql()));
    }

    private boolean isPrimaryDataSource(BoundSql boundSql) {
        String name = boundSql.getSqlModule() == null
                ? null
                : boundSql.getSqlModule().getDataSourceName();
        return name == null
                || name.isBlank()
                || "default".equalsIgnoreCase(name)
                || "master".equalsIgnoreCase(name);
    }

    private static final class TenantSqlRewriter extends TenantLineInnerInterceptor {

        private TenantSqlRewriter(PlatformTenantLineHandler handler) {
            super(handler);
        }

        private String rewrite(String sql) {
            return parserSingle(sql, null);
        }
    }
}
