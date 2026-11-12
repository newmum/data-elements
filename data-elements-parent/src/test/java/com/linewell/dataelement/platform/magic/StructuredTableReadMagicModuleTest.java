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
