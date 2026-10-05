package com.linewell.dataelement.platform.tenant.infrastructure.mybatis;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.persistence.TenantTableRegistry;
import java.util.List;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.schema.Column;
import org.springframework.stereotype.Component;

@Component
public class PlatformTenantLineHandler implements TenantLineHandler {

    private final TenantProperties properties;
    private final TenantTableRegistry tableRegistry;

    public PlatformTenantLineHandler(
            TenantProperties properties,
            TenantTableRegistry tableRegistry
    ) {
        this.properties = properties;
        this.tableRegistry = tableRegistry;
    }

    @Override
    public Expression getTenantId() {
        return new StringValue(TenantContext.requireTenantId());
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return !properties.isEnabled()
                || TenantContext.isIgnored()
                || TenantContext.getTenantId() == null
                || properties.ignores(tableName)
                || !tableRegistry.hasTenantColumn(tableName);
    }

    @Override
    public boolean ignoreInsert(List<Column> columns, String tenantIdColumn) {
        return columns.stream().anyMatch(column ->
                column.getColumnName().equalsIgnoreCase(tenantIdColumn)
        );
    }
}
