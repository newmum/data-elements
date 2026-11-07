package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.dialect.Dialect;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.dialect.MySQLDialect;
import org.ssssssss.magicapi.modules.db.dialect.PostgreSQLDialect;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.LowerColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class TaskMonitorTrendMagicTest {
    private static final String START = "2026-10-03 00:00:00";
    private static final String END = "2026-10-03 02:00:00";

    @Test
    void sumsTaskPeaksAndRetainsOnlyActiveTenantSnapshotsInTheHalfOpenRange() throws Exception {
        var fixture = new Fixture("PostgreSQL");
        fixture.snapshot("tenant-a", "task-a", START, 4, 8, 40, 80, 1, 0);
        fixture.snapshot("tenant-a", "task-a", "2026-10-03 00:01:00", 9, 3, 90, 30, 7, 0);
        fixture.snapshot("tenant-a", "task-b", "2026-10-03 00:02:00", 6, null, null, 60, 2, 0);
        fixture.snapshot("tenant-a", "task-a", "2026-10-03 01:00:00", 3, 4, 30, 40, 2, 0);
        fixture.snapshot("tenant-a", "task-a", END, 999, 999, 999, 999, 999, 0);
        fixture.snapshot("tenant-a", "task-a", "2026-10-02 23:59:59", 999, 999, 999, 999, 999, 0);
        fixture.snapshot("tenant-b", "task-a", START, 999, 999, 999, 999, 999, 0);
        fixture.snapshot("tenant-a", "task-a", START, 999, 999, 999, 999, 999, 1);

        var response = assertInstanceOf(Map.class, fixture.run("tenant-a", START, END, "hour"));
        assertThat(response.get("interval")).isEqualTo("hour");
        assertThat(response.get("metric")).isEqualTo("SUM_OF_TASK_HOURLY_SNAPSHOT_PEAKS");
        assertThat(response.get("beginTime")).isEqualTo(START);
        assertThat(response.get("endTime")).isEqualTo(END);
        var points = assertInstanceOf(List.class, response.get("points"));
        assertThat(points).hasSize(2);
        point(points.getFirst(), START, 3, 15, 8, 90, 140, 7);
        point(points.getLast(), "2026-10-03 01:00:00", 1, 3, 4, 30, 40, 2);
        assertThat(fixture.reads).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void readsFixedQueriesRegardlessOfSampleCount(int size) throws Exception {
        for (String mode : List.of("MySQL", "PostgreSQL")) {
            var fixture = new Fixture(mode);
            fixture.samples(size);
            var response = assertInstanceOf(Map.class, fixture.run("tenant-a", START, END, "hour"));
            var points = assertInstanceOf(List.class, response.get("points"));
            assertThat(points).hasSize(1);
            point(points.getFirst(), START, size, size, 0, 0, 0, 0);
            assertThat(fixture.reads).isEqualTo(2);
            System.out.printf("Monitor trend mode=%s samples=%d reads=%d%n", mode, size, fixture.reads);
        }
    }

    @Test
    void rejectsOversizedRangeWithoutReturningAPartialSeries() throws Exception {
        var fixture = new Fixture("MySQL");
        fixture.samples(50001);
        assertThat(code(fixture.run("tenant-a", START, END, "hour"))).isEqualTo(413);
        assertThat(fixture.reads).isEqualTo(2);
    }

    @Test
    void validatesTenantIntervalAndTimeBeforeReadingSnapshots() throws Exception {
        var fixture = new Fixture("MySQL");
        assertThat(code(fixture.run("", START, END, "hour"))).isEqualTo(401);
        assertThat(code(fixture.run("tenant-a", START, END, "minute"))).isEqualTo(400);
        assertThat(code(fixture.run("tenant-a", "invalid", END, "hour"))).isEqualTo(400);
        assertThat(code(fixture.run("tenant-a", END, START, "hour"))).isEqualTo(400);
        assertThat(code(fixture.run("tenant-a", START, "2026-10-10 00:00:01", "hour"))).isEqualTo(400);
        assertThat(fixture.reads).isZero();
        var empty = assertInstanceOf(Map.class, fixture.run("tenant-a", START, "2026-10-10 00:00:00", "hour"));
        assertThat(assertInstanceOf(List.class, empty.get("points"))).isEmpty();
    }

    private static void point(Object value, String time, long samples, long incoming, long outgoing,
                              long bytesIn, long bytesOut, long queue) {
        var point = assertInstanceOf(Map.class, value);
        assertThat(point.get("bucketTime")).isEqualTo(time);
        assertThat(number(point, "sampleCount")).isEqualTo(samples);
        assertThat(number(point, "flowFilesIn")).isEqualTo(incoming);
        assertThat(number(point, "flowFilesOut")).isEqualTo(outgoing);
        assertThat(number(point, "bytesIn")).isEqualTo(bytesIn);
        assertThat(number(point, "bytesOut")).isEqualTo(bytesOut);
        assertThat(number(point, "queuedCountMax")).isEqualTo(queue);
    }

    private static long number(Map<?, ?> row, String key) {
        return assertInstanceOf(Number.class, row.get(key)).longValue();
    }

    private static int code(Object result) {
        return assertInstanceOf(Number.class, assertInstanceOf(ExitValue.class, result).getValues()[0]).intValue();
    }

    public record Session(String tenant) {
        public String id() {
            return tenant;
        }
    }

    private static final class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final MagicScript script;
        int reads;

        Fixture(String mode) throws Exception {
            var source = new DriverManagerDataSource("jdbc:h2:mem:monitor_trend_" + UUID.randomUUID()
                    + ";MODE=" + mode + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    CREATE TABLE data_access_task_monitor_snap_t(
                        tenant_id VARCHAR(32), task_id VARCHAR(32), monitor_time TIMESTAMP,
                        flow_files_in BIGINT, flow_files_out BIGINT, bytes_in BIGINT, bytes_out BIGINT,
                        queued_count BIGINT, is_del INT)
                    """);
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            db.setDialectAdapter(new DialectAdapter() {
                @Override public Dialect getDialectFromConnection(Connection connection) {
                    return "MySQL".equals(mode) ? new MySQLDialect() : new PostgreSQLDialect();
                }
            });
            db.setResultProvider(new DefaultResultProvider("monitor-trend-validation"));
            var mapper = new ColumnMapperAdapter();
            mapper.add(new DefaultColumnMapperProvider());
            mapper.add(new CamelColumnMapperProvider());
            mapper.add(new LowerColumnMapperProvider());
            mapper.setDefault("camel");
            db.setColumnMapperProvider(mapper);
            db.setColumnMapRowMapper(mapper.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of(new SQLInterceptor() {
                @Override public void preHandle(BoundSql bound, RequestEntity request) {
                    assertThat(bound.getSql().stripLeading().substring(0, 6)).isEqualToIgnoringCase("select");
                    reads++;
                }
            }));
            var sourceCode = CanonicalMagicSources.byId("b964b2d54ba0477b86d3fb6e6d18c45b")
                    .replaceAll("(?m)^import tenantRuntime\\s*\\R", "");
            script = MagicScript.create(sourceCode, null);
        }

        void snapshot(String tenant, String task, String time, Integer incoming, Integer outgoing,
                      Integer bytesIn, Integer bytesOut, Integer queue, int deleted) {
            jdbc.update("INSERT INTO data_access_task_monitor_snap_t VALUES(?,?,CAST(? AS TIMESTAMP),?,?,?,?,?,?)",
                    tenant, task, time, incoming, outgoing, bytesIn, bytesOut, queue, deleted);
        }

        void samples(int size) {
            jdbc.update("""
                    INSERT INTO data_access_task_monitor_snap_t
                    SELECT 'tenant-a','task-a',TIMESTAMP '2026-10-03 00:00:00',n,0,0,0,0,0
                      FROM SYSTEM_RANGE(1,?) AS numbers(n)
                    """, size);
        }

        Object run(String tenant, String start, String end, String interval) {
            reads = 0;
            return script.execute(new MagicScriptContext(Map.of("db", db, "tenantRuntime", new Session(tenant),
                    "body", Map.of("beginTime", start, "endTime", end, "interval", interval))));
        }
    }
}
