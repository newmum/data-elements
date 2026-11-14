package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.dialect.MySQLDialect;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class TaskMonitorSnapshotCleanupMagicTest {
    @ParameterizedTest
    @ValueSource(ints = {20, 100, 1001})
    void physicallyDeletesOnlyOneTenantBatchIncludingLegacySoftDeletes(int size) throws Exception {
        var fixture = new Fixture();
        fixture.jdbc.update("""
                INSERT INTO data_access_task_monitor_snap_t
                SELECT 'old-' || n, 'tenant-a', TIMESTAMP '2000-01-01 00:00:00', MOD(n, 2)
                FROM SYSTEM_RANGE(1, ?) AS numbers(n)
                """, size);
        fixture.jdbc.execute("""
                INSERT INTO data_access_task_monitor_snap_t VALUES
                ('current', 'tenant-a', CURRENT_TIMESTAMP, 0),
                ('foreign', 'tenant-b', TIMESTAMP '2000-01-01 00:00:00', 1),
                ('unknown-age', 'tenant-a', NULL, 1)
                """);

        Map<?, ?> preview = fixture.cleanup(true);
        assertThat(((Number) preview.get("count")).intValue()).isEqualTo(size);
        assertThat(fixture.statements).containsExactly("select");
        assertThat(fixture.rows()).isEqualTo(size + 3);

        fixture.statements.clear();
        Map<?, ?> executed = fixture.cleanup(false);
        assertThat(((Number) executed.get("deleted")).intValue()).isEqualTo(Math.min(1000, size));
        assertThat(executed.get("hasMore")).isEqualTo(size > 1000);
        assertThat(fixture.statements).containsExactly("select", "delete");
        assertThat(fixture.rows()).isEqualTo(Math.max(0, size - 1000) + 3);
        assertThat(fixture.jdbc.queryForList(
                "SELECT tid FROM data_access_task_monitor_snap_t WHERE tid NOT LIKE 'old-%'", String.class))
                .containsExactlyInAnyOrder("current", "foreign", "unknown-age");
        if (size > 1000) {
            assertThat(((Number) fixture.cleanup(false).get("deleted")).intValue()).isEqualTo(1);
            assertThat(fixture.rows()).isEqualTo(3);
        }
    }

    @Test
    void rejectsRestrictedScopeAndUnconfirmedExecutionBeforeThePurgeFunction() throws Exception {
        String script = CanonicalMagicSources.byId("8f8b59890a174e36ad43a290e748290a")
                .replaceAll("(?m)^import .*\\R", "");
        var api = MagicScript.create(script, null);
        var restricted = api.execute(new MagicScriptContext(Map.of(
                "dataScope", new Scope("OWNER"), "body", Map.of("dryRun", false, "confirm", true))));
        var unconfirmed = api.execute(new MagicScriptContext(Map.of(
                "dataScope", new Scope("ALL"), "body", Map.of("dryRun", false))));
        assertThat(assertInstanceOf(ExitValue.class, restricted).getValues()[0]).isEqualTo(403);
        assertThat(assertInstanceOf(ExitValue.class, unconfirmed).getValues()[0]).isEqualTo(400);
    }

    @Test
    void scheduledCleanupUsesTheCatalogAndRestoresTenantAfterFailure() throws Exception {
        String task = CanonicalMagicSources.byPath(
                "task/05.系统定时任务/09.清理到期任务监控快照.ms", "3ed89c080ce8418790474305211073dc")
                .replaceAll("(?m)^import (?!.*TenantContext$).*\\R", "");
        String imports = "import com.linewell.dataelement.platform.tenant.domain.TenantContext\n";
        task = task.replace(imports, imports
                + "var purgeSnapshots = (days, preview) => probe.purge(days, preview)\n");
        var probe = new ScheduledProbe();
        var vars = Map.of("tenantCatalog", probe, "environment", probe, "probe", probe,
                "log", org.slf4j.LoggerFactory.getLogger(getClass()));
        var script = MagicScript.create(task, null);
        var result = TenantContext.call("outer", () -> {
            Object output = script.execute(new MagicScriptContext(vars));
            assertThat(TenantContext.requireTenantId()).isEqualTo("outer");
            return output;
        });
        assertThat(probe.visited).containsExactly("tenant-a", "tenant-b");
        assertThat(assertInstanceOf(List.class, result)).hasSize(2);
    }

    public static final class ScheduledProbe {
        private final List<String> visited = new ArrayList<>();

        public List<String> activeTenantIds() {
            return List.of("tenant-a", "tenant-b");
        }

        public String getProperty(String name, String fallback) {
            assertThat(name).isEqualTo("nifi.monitor-retention-days");
            return fallback;
        }

        public Map<String, Object> purge(int days, boolean preview) {
            assertThat(days).isEqualTo(7);
            assertThat(preview).isFalse();
            String tenant = TenantContext.requireTenantId();
            visited.add(tenant);
            if (tenant.equals("tenant-a")) {
                throw new IllegalStateException("simulate one tenant database outage");
            }
            return Map.of("deleted", 3, "hasMore", false);
        }
    }

    public record Session(String id) { }

    public record Scope(String scope) {
        public Map<String, String> dataSource() {
            return Map.of("scope", scope, "userId", "validation-user");
        }
    }

    private static final class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final MagicScript script;
        final List<String> statements = new ArrayList<>();

        Fixture() throws Exception {
            var source = new DriverManagerDataSource("jdbc:h2:mem:purge_snap_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    CREATE TABLE data_access_task_monitor_snap_t(
                        tid VARCHAR(32) PRIMARY KEY, tenant_id VARCHAR(32), monitor_time TIMESTAMP, is_del INT)
                    """);
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            var dialect = new DialectAdapter();
            dialect.add(new MySQLDialect() {
                @Override public boolean match(String productName) { return true; }
            });
            db.setDialectAdapter(dialect);
            db.setResultProvider((request, status, message, data) -> data);
            var mapper = new ColumnMapperAdapter();
            mapper.add(new DefaultColumnMapperProvider());
            mapper.setDefault("default");
            db.setColumnMapperProvider(mapper);
            db.setColumnMapRowMapper(mapper.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of(new SQLInterceptor() {
                @Override public void preHandle(BoundSql bound, RequestEntity request) {
                    statements.add(bound.getSql().stripLeading().split("\\s+", 2)[0].toLowerCase());
                }
            }));
            script = MagicScript.create(CanonicalMagicSources.byPath(
                    "function/01.通用函数/30.清理到期任务监控快照.ms", "377fbf22fd334ef1b29464ba6a42fe31")
                    .replaceAll("(?m)^import tenantRuntime\\s*\\R", ""), null);
        }

        Map<?, ?> cleanup(boolean preview) {
            var inputs = Map.of("db", db, "tenantRuntime", new Session("tenant-a"),
                    "retentionDays", 7, "dryRun", preview);
            return assertInstanceOf(Map.class, script.execute(new MagicScriptContext(inputs)));
        }

        int rows() {
            return jdbc.queryForObject("SELECT COUNT(*) FROM data_access_task_monitor_snap_t", Integer.class);
        }
    }
}
