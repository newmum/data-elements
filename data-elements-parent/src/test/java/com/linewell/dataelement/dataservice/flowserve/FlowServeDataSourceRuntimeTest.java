package com.linewell.dataelement.dataservice.flowserve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.Map;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;

class FlowServeDataSourceRuntimeTest {
    private JdbcTemplate jdbc;
    private MagicDynamicDataSource dynamicDataSource;
    private FlowServeDataSourceRuntime runtime;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource());
        jdbc.execute("""
                create table db_datasource_t (
                    tid varchar(32) primary key,
                    tenant_id varchar(32),
                    db_name varchar(255),
                    db_type varchar(64),
                    driver_class_name varchar(255),
                    jdbc_url varchar(1024),
                    username varchar(255),
                    password varchar(255),
                    updated_time timestamp,
                    is_del integer,
                    is_enable integer,
                    show_connect integer
                )
                """);
        dynamicDataSource = mock(MagicDynamicDataSource.class);
        DataSourceConnectionPropertyResolver connectionProperties = mock(DataSourceConnectionPropertyResolver.class);
        when(connectionProperties.resolve(anyString(), anyString())).thenReturn(Map.of());
        runtime = new FlowServeDataSourceRuntime(
                jdbc, new ObjectMapper(), connectionProperties, dynamicDataSource, new TenantProperties(), false);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void resolvesMainPlatformConfigByIdAndRegistersCanonicalRuntimeKey() throws Exception {
        insert(
                "source_id",
                TenantProperties.DEFAULT_TENANT_ID,
                "订单数据库",
                "mysql",
                "jdbc:mysql://db-host:3306/orders",
                "app",
                "secret");
        try (TenantContext.Scope ignored = TenantContext.use(TenantProperties.DEFAULT_TENANT_ID)) {
            Map<String, Object> config = runtime.configByName("source_id");

            assertEquals("jdbc:mysql://db-host:3306/orders", config.get("url"));
            assertEquals("app", config.get("username"));
            assertEquals("secret", config.get("password"));
            assertEquals("source_id", config.get("id"));
            assertEquals("source_id", runtime.ensureRuntime("source_id"));
        }

        ArgumentCaptor<DriverManagerDataSource> source = ArgumentCaptor.forClass(DriverManagerDataSource.class);
        verify(dynamicDataSource).put(eq("source_id"), source.capture());
        assertEquals("jdbc:mysql://db-host:3306/orders", source.getValue().getUrl());
    }

    @Test
    void rejectsAmbiguousLegacyNamesButIdsRemainStable() {
        insert("source_a", TenantProperties.DEFAULT_TENANT_ID, "重复名称", "mysql", "jdbc:mysql://a/db", "u", "p");
        insert("source_b", TenantProperties.DEFAULT_TENANT_ID, "重复名称", "mysql", "jdbc:mysql://b/db", "u", "p");
        try (TenantContext.Scope ignored = TenantContext.use(TenantProperties.DEFAULT_TENANT_ID)) {
            IllegalArgumentException error =
                    assertThrows(IllegalArgumentException.class, () -> runtime.configByName("重复名称"));
            assertEquals("数据源名称不唯一，请在编排中重新选择数据源：重复名称", error.getMessage());
            assertEquals("source_a", runtime.configByName("source_a").get("id"));
        }
    }

    @Test
    void doesNotLeakAnotherTenantsDatasource() {
        insert("source_id", "tenant_a", "租户A数据源", "mysql", "jdbc:mysql://a/db", "u", "p");
        try (TenantContext.Scope ignored = TenantContext.use("tenant_b")) {
            assertThrows(IllegalArgumentException.class, () -> runtime.configByName("source_id"));
        }
    }

    private void insert(
            String id,
            String tenantId,
            String name,
            String type,
            String url,
            String username,
            String password) {
        jdbc.update(
                """
                insert into db_datasource_t (
                    tid, tenant_id, db_name, db_type, driver_class_name, jdbc_url,
                    username, password, updated_time, is_del, is_enable, show_connect
                ) values (?, ?, ?, ?, 'com.mysql.cj.jdbc.Driver', ?, ?, ?, current_timestamp, 0, 1, 1)
                """,
                id,
                tenantId,
                name,
                type,
                url,
                username,
                password);
    }

    private DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:flowserve_datasource_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        return dataSource;
    }
}
