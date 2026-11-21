package com.linewell.dataelement.platform.magic.persistence;

import com.linewell.dataelement.magic.runtime.resource.StableMagicResourceService;
import java.util.List;
import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.ssssssss.magicapi.core.config.MagicAPIProperties;
import org.ssssssss.magicapi.core.model.MagicEntity;
import org.ssssssss.magicapi.core.service.MagicResourceService;
import org.ssssssss.magicapi.core.service.MagicResourceStorage;

/**
 * Stores shared Magic API definitions in the control database.
 */
@Configuration
@ConditionalOnProperty(prefix = "magic-api", name = "resource.type", havingValue = "database")
public class MagicApiDatabaseResourceConfiguration {

    @Bean("magicDatabaseResource")
    @Primary
    public DmCompatibleDatabaseResource magicDatabaseResource(
            MagicAPIProperties properties,
            @Qualifier("controlDataSource") DataSource controlDataSource
    ) {
        org.ssssssss.magicapi.core.config.Resource resourceConfig = properties.getResource();
        return new DmCompatibleDatabaseResource(
                new JdbcTemplate(controlDataSource),
                resourceConfig.getTableName(),
                normalizePrefix(resourceConfig.getPrefix()),
                resourceConfig.isReadonly()
        );
    }

    @Bean
    MagicResourceService magicResourceService(@Qualifier("magicDatabaseResource") DmCompatibleDatabaseResource resource,
            List<MagicResourceStorage<? extends MagicEntity>> storages, ApplicationEventPublisher publisher) {
        return new StableMagicResourceService(resource, storages, publisher,
                resource::prepareRefresh, resource::finishRefresh);
    }

    private String normalizePrefix(String prefix) {
        String value = StringUtils.defaultIfBlank(prefix, "/magic-api").replace("\\", "/");
        if (!value.startsWith("/")) value = "/" + value;
        if (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
