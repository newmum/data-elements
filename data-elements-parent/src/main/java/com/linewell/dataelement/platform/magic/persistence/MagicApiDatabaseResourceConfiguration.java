package com.linewell.dataelement.platform.magic.persistence;

import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.ssssssss.magicapi.core.config.MagicAPIProperties;
import org.ssssssss.magicapi.core.resource.Resource;

/**
 * Stores shared Magic API definitions in the control database.
 */
@Configuration
public class MagicApiDatabaseResourceConfiguration {

    @Bean("magicDatabaseResource")
    @Primary
    @ConditionalOnProperty(prefix = "magic-api", name = "resource.type", havingValue = "database")
    public Resource magicDatabaseResource(
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

    private String normalizePrefix(String prefix) {
        String value = StringUtils.defaultIfBlank(prefix, "/magic-api").replace("\\", "/");
        if (!value.startsWith("/")) value = "/" + value;
        if (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
