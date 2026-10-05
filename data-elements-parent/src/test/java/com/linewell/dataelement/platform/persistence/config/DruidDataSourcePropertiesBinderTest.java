package com.linewell.dataelement.platform.persistence.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.wall.WallFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DruidDataSourcePropertiesBinderTest {

    @Test
    void bindsPoolAndValidationSettings() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.druid.max-active", "42")
                .withProperty("spring.datasource.druid.validation-query", "SELECT 1")
                .withProperty("spring.datasource.druid.test-while-idle", "true")
                .withProperty("spring.datasource.druid.filters", "stat,wall");
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl("jdbc:dm://localhost:5236/example");

        new DruidDataSourcePropertiesBinder(environment)
                .postProcessBeforeInitialization(dataSource, "dataSource");

        assertEquals(42, dataSource.getMaxActive());
        assertEquals("SELECT 1", dataSource.getValidationQuery());
        assertTrue(dataSource.isTestWhileIdle());
        assertFalse(dataSource.getProxyFilters().stream().anyMatch(WallFilter.class::isInstance));
    }
}
