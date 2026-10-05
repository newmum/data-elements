package com.linewell.dataelement.platform.tenant.infrastructure.magic;

import com.linewell.dataelement.platform.tenant.config.TenantDatabaseProperties;
import java.lang.reflect.Field;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.dialect.Dialect;

/**
 * Magic API caches a dialect in its default datasource node. That default node
 * wraps {@code TenantRoutingDataSource}, so caching a dialect from the first
 * request makes a DM/Oracle request poison later MySQL pagination requests.
 */
@Component
public class TenantRoutingMagicDialectConfigurer {

    private static final Logger log = LoggerFactory.getLogger(TenantRoutingMagicDialectConfigurer.class);

    private final MagicDynamicDataSource magicDynamicDataSource;
    private final DataSource routingDataSource;
    private final TenantDatabaseProperties tenantDatabaseProperties;

    public TenantRoutingMagicDialectConfigurer(
            MagicDynamicDataSource magicDynamicDataSource,
            @Qualifier("dataSource") DataSource routingDataSource,
            TenantDatabaseProperties tenantDatabaseProperties
    ) {
        this.magicDynamicDataSource = magicDynamicDataSource;
        this.routingDataSource = routingDataSource;
        this.tenantDatabaseProperties = tenantDatabaseProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void configureDefaultMagicDialect() {
        MagicDynamicDataSource.DataSourceNode defaultNode = magicDynamicDataSource.getDataSource();
        if (defaultNode == null) {
            log.warn("Magic default datasource node is unavailable; tenant-aware pagination was not configured");
            return;
        }
        try {
            Field field = MagicDynamicDataSource.DataSourceNode.class.getDeclaredField("dialect");
            field.setAccessible(true);
            field.set(defaultNode, new TenantRoutingMagicDialect(
                    routingDataSource,
                    tenantDatabaseProperties
            ));
            log.info("Configured tenant-aware Magic SQL pagination dialect for default datasource");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to configure tenant-aware Magic SQL pagination dialect", exception);
        }
    }
}
