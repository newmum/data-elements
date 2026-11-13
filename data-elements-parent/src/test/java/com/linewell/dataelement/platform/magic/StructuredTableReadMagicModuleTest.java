package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.magic.StructuredTableReadMagicModule.Expression;
import com.linewell.dataelement.utils.StructuredValues;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class StructuredTableReadMagicModuleTest {
    private static final String TENANT = "validation-a";
    private static final String OTHER_TENANT = "validation-b";

    @Test
    void aggregateJoinUsesScopedSnapshotAndSortsBeforeAConstantNumberOfReads() {
        var fixture = new Fixture();
        JdbcTemplate jdbc = new JdbcTemplate(fixture.source());
        jdbc.execute("CREATE TABLE inventory (tid VARCHAR, tenant_id VARCHAR, active INT)");
        jdbc.execute("CREATE TABLE records (tid VARCHAR, tenant_id VARCHAR, inventory_id VARCHAR, accepted INT)");
        for (int i = 0; i <= 104; i++) {
            jdbc.update("INSERT INTO inventory VALUES (?, ?, 1)", "row-" + i, TENANT);
            jdbc.update("INSERT INTO records VALUES (?, ?, ?, 0)", "pending-" + i, TENANT, "row-" + i);
        }
        jdbc.update("INSERT INTO records VALUES ('accepted', ?, 'row-104', 1)", TENANT);
        jdbc.update("INSERT INTO records VALUES ('foreign', ?, 'row-104', 1)", OTHER_TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('foreign', ?, 1)", OTHER_TENANT);

        TenantContext.run(TENANT, () -> {
            StructuredTableReadMagicModule module = fixture.module();
            Expression childKey = module.column("c", "inventory_id", "TEXT");
            Expression childId = module.column("c", "tid", "TEXT");
            Expression accepted = module.column("c", "accepted", "NUMBER");
            Expression acceptedCount = module.countDistinct(module.when(module.greaterThan(accepted, 0), childId, null));
            var child = module.from("records", "c", TENANT);
            var query = module.from("inventory", "r", TENANT);
            query.where(module.equal(module.column("r", "active", "NUMBER"), 1));
            query.leftJoinGrouped(child, "stats", "inventoryKey", "tid",
                    Map.of("inventoryKey", childKey, "acceptedCount", acceptedCount), List.of(childKey));
            child.where(module.equal(childId, "missing"));

            Expression id = module.column("r", "tid", "TEXT");
            Expression count = module.coalesce(List.of(module.column("stats", "acceptedCount", "NUMBER"), 0));
            var columns = Map.of("id", id, "acceptedCount", count);
            fixture.source().reads = 0;
            var small = query.page(columns, count, "desc", "tid", 1, 20);
            assertEquals(2, fixture.source().reads);
            var smallRows = StructuredValues.objectRows(small.get("rows"));
            assertEquals(20, smallRows.size());
            assertEquals("row-104", smallRows.getFirst().get("id"));
            assertEquals(1, ((Number) smallRows.getFirst().get("acceptedCount")).intValue());
            assertEquals(105, ((Number) small.get("total")).intValue());

            fixture.source().reads = 0;
            var large = query.page(columns, count, "desc", "tid", 1, 100);
            assertEquals(2, fixture.source().reads);
            var largeRows = StructuredValues.objectRows(large.get("rows"));
            assertEquals(100, largeRows.size());
            assertEquals(smallRows, largeRows.subList(0, 20));

            query.where(module.in(id, List.of("row-104", "foreign")));
            var matching = query.grouped(Map.of("accepted", module.sum(count),
                    "matching", module.countIf(module.like(count, "1%"))), List.of()).getFirst();
            assertEquals(1, ((Number) matching.get("accepted")).intValue());
            assertEquals(1, ((Number) matching.get("matching")).intValue());
        });
    }

    @Test
    void rankAppliesOuterFilterAfterChoosingWinnerAndSupportsBothPageContracts() {
        var fixture = new Fixture();
        JdbcTemplate jdbc = new JdbcTemplate(fixture.source());
        jdbc.execute("CREATE TABLE inventory (tid VARCHAR, tenant_id VARCHAR, table_name VARCHAR, priority INT, approved INT)");
        jdbc.update("INSERT INTO inventory VALUES ('preferred', ?, 'DuP', 9, 0)", TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('duplicate', ?, 'dup', 1, 1)", TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('winner-a', ?, 'Other A', 9, 1)", TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('winner-b', ?, 'Other B', 9, 1)", TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('foreign', ?, 'Other A', 99, 1)", OTHER_TENANT);
        TenantContext.run(TENANT, () -> {
            StructuredTableReadMagicModule module = fixture.module();
            var query = module.from("inventory", "t", TENANT);
            Expression sourcePriority = module.coalesce(List.of(module.column("t", "priority", "NUMBER"), 0));
            Expression sourceId = module.column("t", "tid", "TEXT");
            var ranked = query.ranked("r", Map.of("tid", sourceId, "priority", sourcePriority,
                            "approved", module.column("t", "approved", "NUMBER")),
                    List.of(module.lower(module.column("t", "table_name", "TEXT"))),
                    List.of(sourcePriority, sourceId), List.of("desc", "desc"));
            ranked.where(module.equal(module.column("r", "approved", "NUMBER"), 1));
            Expression id = module.column("r", "tid", "TEXT");
            Expression priority = module.column("r", "priority", "NUMBER");
            var projection = Map.of("id", id);
            var selected = ranked.page(projection, priority, "desc", "tid", "desc", 1, 2);
            assertEquals(List.of(Map.of("id", "winner-b"), Map.of("id", "winner-a")), selected.get("rows"));
            var empty = ranked.page(projection, priority, "desc", "tid", "desc", 3, 2);
            assertEquals(List.of(), empty.get("rows"));
            assertEquals(2, ((Number) empty.get("total")).intValue());
            var clamped = ranked.pageClamped(projection, priority, "desc", "tid", "desc", 3, 2);
            assertEquals(selected.get("rows"), clamped.get("rows"));
            assertEquals(1, clamped.get("pageNum"));
            fixture.source().reads = 0;
            assertEquals(List.of(Map.of("id", "winner-b")), ranked.rows(projection, id, "desc", 1));
            assertEquals(1, fixture.source().reads);
        });
    }

    @Test
    void rankAndPageCanFollowTheDatabaseNullOrdering() {
        var fixture = new Fixture();
        JdbcTemplate jdbc = new JdbcTemplate(fixture.source());
        jdbc.execute("CREATE TABLE inventory (tid VARCHAR, tenant_id VARCHAR, group_key VARCHAR, title VARCHAR, priority INT)");
        jdbc.update("INSERT INTO inventory VALUES ('null-winner', ?, 'shared', NULL, NULL)", TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('nonnull-winner', ?, 'shared', 'z', 1)", TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('other', ?, 'other', 'a', 1)", TENANT);

        TenantContext.run(TENANT, () -> {
            var module = fixture.module();
            var query = module.from("inventory", "t", TENANT);
            var rootId = module.column("t", "tid", "TEXT");
            var rankedColumns = Map.of("tid", rootId, "title", module.column("t", "title", "TEXT"));
            var partition = List.of(module.column("t", "group_key", "TEXT"));
            var priorities = List.of(module.column("t", "priority", "NUMBER"), rootId);
            var directions = List.of("asc", "asc");
            var nativeRank = query.ranked("r", rankedColumns, partition, priorities, directions, true);
            var defaultRank = query.ranked("r", rankedColumns, partition, priorities, directions);
            var projection = Map.of("id", module.column("r", "tid", "TEXT"));
            var sort = module.column("r", "title", "TEXT");

            assertEquals(List.of(Map.of("id", "null-winner"), Map.of("id", "other")),
                    nativeRank.page(projection, sort, "asc", "tid", "asc", 1, 20, true).get("rows"));
            assertEquals(List.of(Map.of("id", "other"), Map.of("id", "null-winner")),
                    nativeRank.page(projection, sort, "asc", "tid", "asc", 1, 20).get("rows"));
            assertEquals(List.of(Map.of("id", "other"), Map.of("id", "nonnull-winner")),
                    defaultRank.page(projection, sort, "asc", "tid", "asc", 1, 20, true).get("rows"));

            var sorts = List.of(sort, projection.get("id"));
            fixture.source().reads = 0;
            assertEquals(List.of(Map.of("id", "null-winner"), Map.of("id", "other")),
                    nativeRank.rankedRows(projection, sorts, directions, true));
            assertEquals(List.of(Map.of("id", "other"), Map.of("id", "null-winner")),
                    nativeRank.rankedRows(projection, sorts, directions, false));
            assertThrows(IllegalArgumentException.class,
                    () -> nativeRank.rankedRows(projection, sorts, List.of("asc"), true));
            assertThrows(IllegalArgumentException.class,
                    () -> nativeRank.rankedRows(projection, sorts, List.of("asc", "unsupported"), true));
            assertEquals(2, fixture.source().reads);
        });
    }

    @Test
    void multiKeyRowsKeepNullsLastAndUseOneScopedQuery() {
        var fixture = new Fixture();
        JdbcTemplate jdbc = new JdbcTemplate(fixture.source());
        jdbc.execute("CREATE TABLE inventory (tid VARCHAR, tenant_id VARCHAR, group_id VARCHAR, label VARCHAR, sort_no INT)");
        jdbc.update("INSERT INTO inventory VALUES ('group-b', ?, 'b', 'highest', 1)", TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('null-label', ?, 'a', NULL, 1)", TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('lower-label', ?, 'a', 'a', 1)", TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('later-sort', ?, 'a', 'z', 2)", TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('tie-b', ?, 'a', 'z', 1)", TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('tie-a', ?, 'a', 'z', 1)", TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('foreign', ?, 'a', 'z', 0)", OTHER_TENANT);

        TenantContext.run(TENANT, () -> {
            var module = fixture.module();
            var query = module.from("inventory", "r", TENANT);
            var id = module.column("r", "tid", "TEXT");
            var label = module.column("r", "label", "TEXT");
            var sorts = List.of(module.column("r", "group_id", "TEXT"), label,
                    module.column("r", "sort_no", "NUMBER"), id);
            var directions = List.of("asc", "desc", "asc", "asc");
            var projection = Map.of("id", id);

            fixture.source().reads = 0;
            var rows = query.rows(projection, sorts, directions, 6);
            assertEquals(List.of("tie-a", "tie-b", "later-sort", "lower-label", "null-label", "group-b"),
                    rows.stream().map(row -> row.get("id")).toList());
            assertEquals(1, fixture.source().reads);
            assertEquals(rows.subList(0, 2), query.rows(projection, sorts, directions, 2));
            assertEquals(2, fixture.source().reads);

            assertEquals(List.of(Map.of("id", "null-label")), query.rows(projection, label, "asc", 1));
            assertThrows(IllegalArgumentException.class, () -> query.rows(projection, sorts, List.of("asc"), 6));
            assertThrows(IllegalArgumentException.class,
                    () -> query.rows(projection, sorts, List.of("asc", "unsupported", "asc", "asc"), 6));
            assertEquals(3, fixture.source().reads);
        });
    }

    @Test
    void multiKeyReadSupportsABoundedOverflowProbeWithoutChangingTheOriginalLimit() {
        var fixture = new Fixture();
        JdbcTemplate jdbc = new JdbcTemplate(fixture.source());
        jdbc.execute("CREATE TABLE inventory (tid BIGINT, tenant_id VARCHAR)");
        jdbc.update("INSERT INTO inventory SELECT n, ? FROM system_range(1, 10002) AS numbers(n)", TENANT);
        jdbc.update("INSERT INTO inventory VALUES (10003, ?)", OTHER_TENANT);

        TenantContext.run(TENANT, () -> {
            var module = fixture.module();
            var query = module.from("inventory", "r", TENANT);
            var id = module.column("r", "tid", "NUMBER");
            var projection = Map.of("id", id);
            var sorts = List.of(id);
            var directions = List.of("asc");

            fixture.source().reads = 0;
            var rows = query.rows(projection, sorts, directions, 10_001);
            assertEquals(10_001, rows.size());
            assertEquals(10_001L, rows.getLast().get("id"));
            assertThrows(IllegalArgumentException.class, () -> query.rows(projection, sorts, directions, 10_002));
            assertThrows(IllegalArgumentException.class, () -> query.rows(projection, id, "asc", 1_001));
            assertEquals(1, fixture.source().reads);

            assertThrows(IllegalArgumentException.class, () -> query.rankedRows(projection, sorts, directions, true));
            var ranked = query.ranked("picked", Map.of("tid", id), List.of(id), sorts, directions);
            var rankedId = module.column("picked", "tid", "NUMBER");
            var complete = ranked.rankedRows(Map.of("id", rankedId), List.of(rankedId), directions, true);
            assertEquals(10_002, complete.size());
            assertEquals(1L, complete.getFirst().get("id"));
            assertEquals(10_002L, complete.getLast().get("id"));
            assertEquals(2, fixture.source().reads);
        });
    }

    @Test
    void regexMatchesAndReplacesAllOccurrencesWithoutTruncatingLongText() {
        var fixture = new Fixture();
        JdbcTemplate jdbc = new JdbcTemplate(fixture.source());
        jdbc.execute("CREATE TABLE inventory (tid VARCHAR, tenant_id VARCHAR, body CLOB)");
        String middle = "数".repeat(48_000);
        String body = "!?" + middle + "?!带'引号42";
        jdbc.update("INSERT INTO inventory VALUES ('matching', ?, ?)", TENANT, body);
        jdbc.update("INSERT INTO inventory VALUES ('not-matching', ?, ?)", TENANT, body + "extra");
        jdbc.update("INSERT INTO inventory VALUES ('foreign', ?, ?)", OTHER_TENANT, body);

        TenantContext.run(TENANT, () -> {
            var module = fixture.module();
            var query = module.from("inventory", "r", TENANT);
            var id = module.column("r", "tid", "TEXT");
            var replaced = module.regexReplace(module.column("r", "body", "LOB"), "[?!]+", "'_");
            var joined = module.concat("开头:", replaced);
            query.where(module.regexLike(joined, "带'引号[0-9]+$"));

            assertEquals(List.of(Map.of("id", "matching", "body", "开头:'_" + middle + "'_带'引号42")),
                    query.grouped(Map.of("id", id, "body", joined), List.of()));
        });
    }

    @Test
    void concatenatedSearchKeepsNullEmptyAndLongTextSemantics() {
        var fixture = new Fixture();
        JdbcTemplate jdbc = new JdbcTemplate(fixture.source());
        jdbc.execute("CREATE TABLE inventory (tid VARCHAR, tenant_id VARCHAR, title VARCHAR, comment_text CLOB, suffix VARCHAR)");
        String longComment = "x".repeat(6200) + "%_tail";
        jdbc.update("INSERT INTO inventory VALUES ('long', ?, NULL, ?, 'next')", TENANT, longComment);
        jdbc.update("INSERT INTO inventory VALUES ('empty', ?, '', NULL, 'next')", TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('no-literal', ?, 'x', 'abctail', 'next')", TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('foreign', ?, NULL, ?, 'next')", OTHER_TENANT, longComment);

        TenantContext.run(TENANT, () -> {
            var module = fixture.module();
            var id = module.column("r", "tid", "TEXT");
            var joined = module.concatWs(" ", List.of(module.column("r", "title", "TEXT"),
                    module.column("r", "comment_text", "LOB"), module.column("r", "suffix", "TEXT")));
            var query = module.from("inventory", "r", TENANT);
            query.where(module.contains(joined, "%_TAIL NEXT"));
            assertEquals(List.of(Map.of("id", "long", "text", longComment + " next")),
                    query.grouped(Map.of("id", id, "text", joined), List.of()));

            var empty = module.from("inventory", "r", TENANT);
            empty.where(module.equal(id, "empty"));
            assertEquals(List.of(Map.of("text", " next")), empty.grouped(Map.of("text", joined), List.of()));

            var separator = module.concatWs("'%_", List.of(module.column("r", "title", "TEXT"),
                    module.column("r", "suffix", "TEXT")));
            assertEquals(List.of(Map.of("text", "'%_next")), empty.grouped(Map.of("text", separator), List.of()));
        });
    }

    @Test
    void rejectsTenantRebindingAndUnboundedOrUntrustedStructure() {
        var fixture = new Fixture();
        StructuredTableReadMagicModule module = fixture.module();
        var foreign = TenantContext.call(OTHER_TENANT, () -> module.from("records", "c", OTHER_TENANT));
        TenantContext.run(TENANT, () -> {
            var root = module.from("inventory", "r", TENANT);
            var local = module.from("records", "c", TENANT);
            Expression key = module.column("c", "inventory_id", "TEXT");
            assertThrows(IllegalArgumentException.class,
                    () -> root.leftJoinGrouped(foreign, "stats", "key", "tid", Map.of("key", key), List.of(key)));
            assertThrows(IllegalArgumentException.class,
                    () -> root.leftJoinGrouped(local, "stats", "key", "tid", Map.of("key", key), List.of()));
            assertThrows(IllegalArgumentException.class,
                    () -> root.rows(Map.of("key", key), key, "desc", 1001));
            assertThrows(IllegalArgumentException.class,
                    () -> module.jsonText(key, "a') or (1=1"));
            assertThrows(IllegalArgumentException.class,
                    () -> TenantContext.run(OTHER_TENANT, () -> root.grouped(Map.of("key", key), List.of())));
        });
    }

    private record Fixture(CountedSource source, StructuredTableReadMagicModule module) {
        Fixture() {
            this(new CountedSource());
        }

        private Fixture(CountedSource source) {
            this(source, new StructuredTableReadMagicModule(new JdbcTemplate(source)));
        }
    }

    private static final class CountedSource extends DriverManagerDataSource {
        private int reads;

        CountedSource() {
            super("jdbc:h2:mem:structured_read_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection actual = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        try {
                            if ("getMetaData".equals(method.getName())) {
                                DatabaseMetaData metadata = actual.getMetaData();
                                return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                        (ignored, metaMethod, metaArgs) -> {
                                            try {
                                                return "getURL".equals(metaMethod.getName()) ? "jdbc:mysql://validation"
                                                        : metaMethod.invoke(metadata, metaArgs);
                                            } catch (InvocationTargetException error) {
                                                throw error.getCause();
                                            }
                                        });
                            }
                            if ("prepareStatement".equals(method.getName())
                                    && ((String) arguments[0]).stripLeading().startsWith("SELECT")) reads++;
                            return method.invoke(actual, arguments);
                        } catch (InvocationTargetException error) {
                            throw error.getCause();
                        }
                    });
        }
    }
}
