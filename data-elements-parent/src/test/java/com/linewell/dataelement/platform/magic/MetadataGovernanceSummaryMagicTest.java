package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

class MetadataGovernanceSummaryMagicTest {
    @Test
    void countsOnlyActiveTenantAssociationsWithFourReadsAtTwentyAndHundredTables() throws Exception {
        for (String mode : List.of("MySQL", "PostgreSQL")) {
            for (int size : List.of(20, 100)) {
                var fixture = new Fixture(mode);
                for (int index = 0; index < size; index++) {
                    String tableId = "table-" + index;
                    fixture.jdbc.update("insert into db_table_t values (?, 'tenant-a', 0, 'source')", tableId);
                    String standardId = switch (index % 5) {
                        case 0 -> "published";
                        case 1 -> "draft";
                        case 2 -> "deleted-standard";
                        case 3 -> "foreign-standard";
                        default -> "missing-standard";
                    };
                    fixture.field("field-" + index, "tenant-a", 0, tableId, standardId);
                }
                fixture.field("deleted-field", "tenant-a", 1, "table-0", "published");
                fixture.field("foreign-field", "tenant-b", 0, "table-0", "published");

                assertSummary(fixture.run("tenant-a"), 2, size, 2, size, size / 5, "20.00");
                assertEquals(4, fixture.source.reads);
            }
        }
    }

    @Test
    void roundsHalfUpToTwoPlacesAndReturnsZeroForEmptyFieldOrTenantSets() throws Exception {
        var fixture = new Fixture("MySQL");
        fixture.jdbc.update("insert into db_table_t values ('no-columns', 'tenant-a', 0, 'source')");
        assertSummary(fixture.run("tenant-a"), 2, 1, 2, 0, 0, "0.00");
        assertEquals(4, fixture.source.reads);
        assertSummary(fixture.run("empty-tenant"), 0, 0, 0, 0, 0, "0.00");
        assertEquals(4, fixture.source.reads);

        for (int index = 0; index < 32; index++) {
            fixture.field("field-" + index, "tenant-a", 0, "no-columns", index == 0 ? "published" : null);
        }
        assertSummary(fixture.run("tenant-a"), 2, 1, 2, 32, 1, "3.13");
        assertEquals(4, fixture.source.reads);
    }

    private static void assertSummary(Map<String, Object> result, int sources, int entities, int standards,
            int totalFields, int mappedFields, String percentage) {
        assertEquals(4, result.size());
        assertEquals(sources, count(result, "sources"));
        assertEquals(entities, count(result, "entities"));
        assertEquals(standards, count(result, "standards"));
        var coverage = StructuredValues.objectMap(result.get("coverage"));
        assertEquals(3, coverage.size());
        assertEquals(totalFields, count(coverage, "totalFields"));
        assertEquals(mappedFields, count(coverage, "mappedFields"));
        assertEquals(new BigDecimal(percentage), coverage.get("coverage"));
    }

    private static long count(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private static final class Fixture {
        final CountedSource source;
        final JdbcTemplate jdbc;
        final SQLModule db;
        final StructuredTableReadMagicModule tableRead;

        Fixture(String mode) {
            source = new CountedSource(mode);
            jdbc = new JdbcTemplate(source);
            tableRead = new StructuredTableReadMagicModule(jdbc);
            jdbc.execute("create table db_datasource_t(tid varchar(32), tenant_id varchar(32), is_del int)");
            jdbc.execute("create table db_table_t(tid varchar(32), tenant_id varchar(32), is_del int, datasource_id varchar(32))");
            jdbc.execute("create table da_metadata_t(tid varchar(32), tenant_id varchar(32), is_del int, publish_status int)");
            jdbc.execute("""
                    create table db_table_column_t(tid varchar(32), tenant_id varchar(32), is_del int,
                        table_id varchar(32), data_standard_id varchar(32))
                    """);
            jdbc.execute("""
                    insert into db_datasource_t values ('source','tenant-a',0), ('source-empty','tenant-a',0),
                        ('source-deleted','tenant-a',1), ('source-foreign','tenant-b',0)
                    """);
            jdbc.execute("""
                    insert into db_table_t values ('deleted-table','tenant-a',1,'source'),
                        ('missing-source-table','tenant-a',0,'source-missing'),
                        ('deleted-source-table','tenant-a',0,'source-deleted'),
                        ('foreign-source-table','tenant-a',0,'source-foreign'),
                        ('foreign-table','tenant-b',0,'source')
                    """);
            jdbc.execute("""
                    insert into da_metadata_t values ('published','tenant-a',0,1), ('draft','tenant-a',0,0),
                        ('deleted-standard','tenant-a',1,1), ('foreign-standard','tenant-b',0,1),
                        ('draft','tenant-b',0,1)
                    """);
            for (String tableId : List.of("deleted-table", "missing-source-table", "deleted-source-table",
                    "foreign-source-table", "foreign-table", "missing-table")) {
                field(tableId, "tenant-a", 0, tableId, "published");
            }

            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            var mappers = new ColumnMapperAdapter();
            mappers.add(new DefaultColumnMapperProvider());
            db.setColumnMapperProvider(mappers);
            db.setColumnMapRowMapper(mappers.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of());
        }

        void field(String id, String tenant, int deleted, String table, String standard) {
            jdbc.update("insert into db_table_column_t values (?, ?, ?, ?, ?)", id, tenant, deleted, table, standard);
        }

        Map<String, Object> run(String tenant) throws Exception {
            var sourceCode = CanonicalMagicSources.byId("dwm_metadata_governance_summary_01")
                    .replaceAll("(?m)^import (?:tenantRuntime|tableRead)\\r?\\n", "");
            var script = MagicScript.create(sourceCode, null);
            var bindings = Map.<String, Object>of("db", db, "tableRead", tableRead, "tenantRuntime", new TenantMagicModule(null),
                    "body", Map.of("tenantId", "tenant-b"));
            source.reads = 0;
            return TenantContext.call(tenant, () -> StructuredValues.objectMap(script.execute(new MagicScriptContext(bindings))));
        }
    }

    private static final class CountedSource extends DriverManagerDataSource {
        int reads;

        CountedSource(String mode) {
            super("jdbc:h2:mem:metadata_governance_" + UUID.randomUUID() + ";MODE=" + mode
                    + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Override
        public Connection getConnection() throws SQLException {
            var connection = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("prepareStatement")) {
                            var sql = ((String) arguments[0]).stripLeading().toLowerCase(Locale.ROOT);
                            if (sql.startsWith("select") || sql.startsWith("with")) reads++;
                        }
                        try {
                            return method.invoke(connection, arguments);
                        } catch (InvocationTargetException error) {
                            throw error.getCause();
                        }
                    });
        }
    }
}
