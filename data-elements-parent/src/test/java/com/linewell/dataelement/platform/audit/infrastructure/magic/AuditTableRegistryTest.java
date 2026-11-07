package com.linewell.dataelement.platform.audit.infrastructure.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.platform.audit.infrastructure.magic.AuditTableRegistry.AuditColumns;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AuditTableRegistryTest {

    private static final AuditColumns LEGACY = new AuditColumns(null, "create_time", null, "update_time");
    private static final AuditColumns MODERN = new AuditColumns("created_by", null, "updated_by", "updated_time");

    @Test
    void usesCurrentSchemaAndKeepsQualifiedTableSeparate() {
        DriverManagerDataSource legacy = schemaFixture();
        AuditTableRegistry registry = new AuditTableRegistry(legacy);

        assertEquals(LEGACY, registry.columnsOf("rm_menu_t"));
        assertEquals(MODERN, registry.columnsOf("\"modern_domain\".\"rm_menu_t\""));
        assertEquals(LEGACY, registry.columnsOf("legacy_domain.rm_menu_t"));
        assertEquals(LEGACY, registry.columnsOf("LEGACY_DOMAIN.rm_menu_t"));
        assertEquals(LEGACY, registry.columnsOf("rm_menu_t"));
    }

    @Test
    void cachesTheSameTableSeparatelyForEachTenantAndControl() throws SQLException {
        DriverManagerDataSource legacy = schemaFixture();
        DriverManagerDataSource modern = new DriverManagerDataSource(legacy.getUrl());
        modern.setSchema("modern_domain");
        DataSource routed = mock(DataSource.class);
        when(routed.getConnection()).thenAnswer(invocation -> {
            if (TenantContext.usesControlDatabase() || "tenant-modern".equals(TenantContext.getTenantId())) {
                return modern.getConnection();
            }
            return legacy.getConnection();
        });
        AuditTableRegistry registry = new AuditTableRegistry(routed);

        try (TenantContext.Scope ignored = TenantContext.use("tenant-legacy")) {
            assertEquals(LEGACY, registry.columnsOf("rm_menu_t"));
            try (TenantContext.Scope control = TenantContext.control()) {
                assertEquals(MODERN, registry.columnsOf("rm_menu_t"));
            }
            assertEquals(LEGACY, registry.columnsOf("rm_menu_t"));
        }
        try (TenantContext.Scope ignored = TenantContext.use("tenant-modern")) {
            assertEquals(MODERN, registry.columnsOf("rm_menu_t"));
            assertEquals(MODERN, registry.columnsOf("rm_menu_t"));
        }

        verify(routed, times(3)).getConnection();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void usesCatalogWithSchemaLessJdbcDriver(boolean schemaUnsupported) throws SQLException {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getCatalog()).thenReturn("tenant_catalog");
        if (schemaUnsupported) {
            when(connection.getSchema()).thenThrow(new SQLFeatureNotSupportedException());
        }
        when(connection.getMetaData()).thenReturn(metadata);
        ResultSet tenantColumns = catalogRows("tenant_catalog", "update_time", "baseline", "updated_time");
        ResultSet controlColumns = catalogRows("baseline", "updated_time", "tenant_catalog", "update_time");
        when(metadata.getColumns("tenant_catalog", null, "rm_menu_t", null)).thenReturn(tenantColumns);
        when(metadata.getColumns("baseline", null, "rm_menu_t", null)).thenReturn(controlColumns);

        AuditTableRegistry registry = new AuditTableRegistry(source);
        assertEquals(new AuditColumns(null, null, null, "update_time"), registry.columnsOf("rm_menu_t"));
        assertEquals(new AuditColumns(null, null, null, "updated_time"), registry.columnsOf("`baseline`.`rm_menu_t`"));
    }

    private static DriverManagerDataSource schemaFixture() {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:audit_schema_" + UUID.randomUUID() + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("create schema legacy_domain");
        jdbc.execute("create schema modern_domain");
        jdbc.execute("create schema legacyxdomain");
        jdbc.execute("create table legacy_domain.rm_menu_t (create_time timestamp, update_time timestamp)");
        jdbc.execute("create table modern_domain.rm_menu_t (created_by varchar(32), updated_by varchar(32), updated_time timestamp)");
        jdbc.execute("create table legacyxdomain.rm_menu_t (updated_time timestamp)");
        jdbc.execute("create table legacy_domain.rmxmenuxT (updated_time timestamp)");
        source.setSchema("legacy_domain");
        return source;
    }

    private static ResultSet catalogRows(String firstCatalog, String firstColumn, String secondCatalog,
                                         String secondColumn) throws SQLException {
        ResultSet rows = mock(ResultSet.class);
        when(rows.next()).thenReturn(true, true, false);
        when(rows.getString("TABLE_NAME")).thenReturn("rm_menu_t");
        when(rows.getString("TABLE_CAT")).thenReturn(firstCatalog, secondCatalog);
        when(rows.getString("COLUMN_NAME")).thenReturn(firstColumn, secondColumn);
        return rows;
    }
}
