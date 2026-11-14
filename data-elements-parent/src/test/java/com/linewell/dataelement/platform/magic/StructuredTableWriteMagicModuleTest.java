package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.SQLModule;

class StructuredTableWriteMagicModuleTest {
    private static final String TENANT = "validation-a";
    private static final String OTHER_TENANT = "validation-b";
    private static final String TABLE = "validation_row";
    private static final String RELATED = "validation_related";

    @Test
    void locksOnlyTheScopedRowOnTheMagicTransactionConnection() {
        var fixture = new Fixture();
        var scope = new ArrayList<>(IntStream.range(0, 1_000).mapToObj(index -> "other-" + index).toList());
        scope.add("organization-a");

        TenantContext.run(TENANT, () -> {
            fixture.source.opens = 0;
            var transaction = fixture.db.transaction();
            try {
                var fields = List.of("tid", "payload");
                assertEquals(Map.of("tid", "row-a", "payload", "validation payload"),
                        fixture.module.lockRow(TABLE, TENANT, "tid", "row-a", fields,
                                Map.of("is_del", 0, "scope_id", scope)));
                assertNull(fixture.module.lockRow(TABLE, TENANT, "tid", "row-b", fields,
                        Map.of("is_del", 0, "scope_id", scope)));
                assertNull(fixture.module.lockRow(TABLE, TENANT, "tid", "row-a", fields,
                        Map.of("is_del", 0, "scope_id", List.of())));
                assertNull(fixture.module.lockRow(TABLE, TENANT, "tid", "row-a' OR '1'='1", fields, Map.of()));
                assertEquals(1, fixture.source.opens);
            } finally {
                transaction.rollback();
            }
        });
    }

    @Test
    void locksABoundedSetOfKeysOnOneScopedTransactionConnection() {
        var fixture = new Fixture();
        var fields = List.of("tid", "payload");
        var keys = List.of("row-b", "row-a", "row-a' OR '1'='1");
        var active = Map.of("is_del", 0);

        TenantContext.run(TENANT, () -> {
            assertThrows(IllegalStateException.class,
                    () -> fixture.module.lockRows(TABLE, TENANT, "tid", keys, fields, active));
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.module.lockRows(TABLE, OTHER_TENANT, "tid", keys, fields, active));
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.module.lockRows(TABLE, TENANT, "tid", List.of("row-a", "row-a"), fields, active));
            var oversized = IntStream.rangeClosed(0, 1000).mapToObj(String::valueOf).toList();
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.module.lockRows(TABLE, TENANT, "tid", oversized, fields, active));

            fixture.source.opens = 0;
            fixture.db.transaction(ignored -> {
                assertEquals(List.of(), fixture.module.lockRows(TABLE, TENANT, "tid", List.of(), fields, active));
                assertEquals(List.of("row-a", "row-b"), fixture.module.lockRows(TABLE, TENANT, "tid", keys, fields, active)
                        .stream().map(row -> row.get("tid")).toList());
                var scope = Map.of("is_del", 0, "scope_id", "organization-a");
                assertEquals(List.of(Map.of("tid", "row-a", "payload", "validation payload")),
                        fixture.module.lockRows(TABLE, TENANT, "tid", keys, fields, scope));
                assertEquals(1, fixture.source.opens);
                return null;
            });
        });
    }

    @Test
    void batchBindsHeterogeneousValuesReturnsMissingRowsAndRollsBackWithMagic() {
        var fixture = new Fixture();
        var now = new Date(1_795_000_000_123L);
        TenantContext.run(TENANT, () -> {
            var transaction = fixture.db.transaction();
            try {
                assertEquals(1, fixture.module.batchUpdate(TABLE, TENANT, List.of("note", "changed_at"),
                        List.of("tid", "parent_key"), Map.of("is_del", 0), List.of(
                                Map.of("tid", "row-a", "parent_key", "parent-a", "note", "updated ' safely", "changed_at", now),
                                Map.of("tid", "row-b", "parent_key", "wrong-parent", "note", "ineligible", "changed_at", now))));
                assertEquals("updated ' safely", fixture.note(TENANT, "row-a"));
                assertEquals("second", fixture.note(TENANT, "row-b"));
                assertEquals("foreign", fixture.note(OTHER_TENANT, "row-a"));
                var timestamp = fixture.jdbc.queryForObject("SELECT changed_at FROM validation_row WHERE tenant_id=? AND tid=?",
                        Timestamp.class, TENANT, "row-a");
                assertEquals(now.getTime(), timestamp.getTime());
                assertEquals(1, fixture.source.batches);
            } finally {
                transaction.rollback();
            }
        });
        assertEquals("first", fixture.note(TENANT, "row-a"));
    }

    @Test
    void rejectsWrongTenantWrongTransactionAndUntrustedFieldsBeforeAnyUpdate() {
        var fixture = new Fixture();
        TenantContext.run(TENANT, () -> {
            var changes = List.<Map<String, ?>>of(Map.of("tid", "row-a", "note", "changed"));
            assertThrows(IllegalStateException.class,
                    () -> fixture.module.batchUpdate(TABLE, TENANT, List.of("note"), List.of("tid"), Map.of(), changes));
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.module.lockRow(TABLE, OTHER_TENANT, "tid", "row-a", List.of("tid"), Map.of()));
            var unrelated = new TransactionTemplate(new DataSourceTransactionManager(new ObservedDataSource()));
            unrelated.executeWithoutResult(status -> assertThrows(IllegalStateException.class,
                    () -> fixture.module.lockRow(TABLE, TENANT, "tid", "row-a", List.of("tid"), Map.of())));
            var readOnly = new TransactionTemplate(new DataSourceTransactionManager(fixture.source));
            readOnly.setReadOnly(true);
            readOnly.executeWithoutResult(status -> assertThrows(IllegalStateException.class,
                    () -> fixture.module.lockRow(TABLE, TENANT, "tid", "row-a", List.of("tid"), Map.of())));

            var transaction = fixture.db.transaction();
            try {
                assertThrows(IllegalArgumentException.class, () -> fixture.module.lockRow(TABLE, TENANT,
                        "tid", "row-a", List.of("tid; drop table"), Map.of()));
                assertThrows(IllegalArgumentException.class, () -> fixture.module.lockRow(TABLE, TENANT,
                        "tid", "row-a", List.of("tid"), Map.of("TENANT_ID", OTHER_TENANT)));
                assertThrows(IllegalArgumentException.class, () -> fixture.module.batchUpdate(TABLE, TENANT,
                        List.of("note"), List.of("tid"), Map.of(), List.of(changes.getFirst(), changes.getFirst())));
                assertThrows(IllegalArgumentException.class, () -> fixture.module.batchUpdate(TABLE, TENANT,
                        List.of("note"), List.of("tid"), Map.of("parent_key", List.of("parent-a")), changes));
                assertEquals(0, fixture.source.batches);
            } finally {
                transaction.rollback();
            }
        });
    }

    @Test
    void jdbcSuccessfulUnknownCountIsAcceptedButMultipleMatchesAreRejected() {
        var fixture = new Fixture();
        TenantContext.run(TENANT, () -> {
            var transaction = fixture.db.transaction();
            try {
                var changes = List.<Map<String, ?>>of(Map.of("tid", "row-a", "note", "changed"));
                fixture.source.batchCounts = new int[]{Statement.SUCCESS_NO_INFO};
                assertEquals(1, fixture.module.batchUpdate(TABLE, TENANT, List.of("note"), List.of("tid"), Map.of(), changes));
                fixture.source.batchCounts = new int[]{2};
                assertThrows(IllegalStateException.class, () -> fixture.module.batchUpdate(TABLE, TENANT,
                        List.of("note"), List.of("tid"), Map.of(), changes));
            } finally {
                transaction.rollback();
            }
        });
        assertEquals("first", fixture.note(TENANT, "row-a"));
    }

    @Test
    void updatesOnlyScopedRowWithDatabaseTimeAndKeepsItsAuditActor() {
        var fixture = new Fixture();
        fixture.jdbc.execute("ALTER TABLE validation_row ADD updated_by VARCHAR DEFAULT 'original-actor'");
        fixture.jdbc.execute("ALTER TABLE validation_row ADD record_count BIGINT DEFAULT 99");
        var values = new LinkedHashMap<String, Object>();
        values.put("note", null);
        values.put("record_count", 0L);
        var scope = Map.of("is_del", 0, "parent_key", "parent-a", "scope_id", List.of("organization-a"));

        TenantContext.run(TENANT, () -> fixture.db.transaction(ignored -> {
            var databaseTime = fixture.jdbc.queryForObject("SELECT CAST(CURRENT_TIMESTAMP AS TIMESTAMP)", Timestamp.class);
            assertEquals(0, fixture.module.updateWithDatabaseTimestamp(TABLE, TENANT, "tid", "row-b", values, scope, "changed_at"));
            assertEquals(0, fixture.module.updateWithDatabaseTimestamp(TABLE, TENANT, "tid", "row-a' OR '1'='1", values, scope, "changed_at"));
            assertEquals(1, fixture.module.updateWithDatabaseTimestamp(TABLE, TENANT, "tid", "row-a", values, scope, "changed_at"));

            var row = fixture.jdbc.queryForMap("SELECT note, record_count, changed_at, updated_by FROM validation_row "
                    + "WHERE tenant_id=? AND tid=?", TENANT, "row-a");
            assertNull(row.get("note"));
            assertEquals(0L, row.get("record_count"));
            assertEquals(databaseTime, row.get("changed_at"));
            assertEquals("original-actor", row.get("updated_by"));
            assertEquals("second", fixture.note(TENANT, "row-b"));
            assertEquals("foreign", fixture.note(OTHER_TENANT, "row-a"));
            return null;
        }));
    }

    @Test
    void acceptsOnlySupportedDatabaseTimePrecisionAndKeepsTheSameTransactionClock() {
        var fixture = new Fixture();
        var values = Map.of("note", "updated at database time");
        var active = Map.of("is_del", 0);
        TenantContext.run(TENANT, () -> fixture.db.transaction(ignored -> {
            for (int precision : List.of(-1, 7)) {
                assertThrows(IllegalArgumentException.class,
                        () -> fixture.module.updateWithDatabaseTimestamp(TABLE, TENANT, "tid", "row-a", values,
                                active, "changed_at", precision));
            }
            assertEquals("first", fixture.note(TENANT, "row-a"));
            var expected = fixture.jdbc.queryForObject("SELECT CAST(CURRENT_TIMESTAMP(6) AS TIMESTAMP)", Timestamp.class);
            assertEquals(1, fixture.module.updateWithDatabaseTimestamp(TABLE, TENANT, "tid", "row-a", values,
                    active, "changed_at", 6));
            assertEquals(expected, fixture.jdbc.queryForObject("SELECT changed_at FROM validation_row "
                    + "WHERE tenant_id=? AND tid=?", Timestamp.class, TENANT, "row-a"));
            return null;
        }));
    }

    @Test
    void rejectsUnsafeTimestampUpdatesAndRollsBackMultipleMatches() {
        var fixture = new Fixture();
        var values = Map.of("note", "must roll back");
        var active = Map.of("is_del", 0);
        TenantContext.run(TENANT, () -> {
            assertThrows(IllegalStateException.class,
                    () -> fixture.module.updateWithDatabaseTimestamp(TABLE, TENANT, "tid", "row-a", values, active, "changed_at"));
            var readOnly = new TransactionTemplate(new DataSourceTransactionManager(fixture.source));
            readOnly.setReadOnly(true);
            readOnly.executeWithoutResult(status -> assertThrows(IllegalStateException.class,
                    () -> fixture.module.updateWithDatabaseTimestamp(TABLE, TENANT, "tid", "row-a", values, active, "changed_at")));

            fixture.db.transaction(ignored -> {
                assertThrows(IllegalArgumentException.class,
                        () -> fixture.module.updateWithDatabaseTimestamp(TABLE, OTHER_TENANT, "tid", "row-a", values, active, "changed_at"));
                assertThrows(IllegalArgumentException.class,
                        () -> fixture.module.updateWithDatabaseTimestamp(TABLE, TENANT, "tid", "row-a", values, active, "changed_at = NULL"));
                assertThrows(IllegalArgumentException.class,
                        () -> fixture.module.updateWithDatabaseTimestamp(TABLE, TENANT, "tid", "row-a", values, active, "TID"));
                assertThrows(IllegalArgumentException.class,
                        () -> fixture.module.updateWithDatabaseTimestamp(TABLE, TENANT, "tid", "row-a", values, active, "TENANT_ID"));
                for (String reserved : List.of("TID", "TENANT_ID", "CHANGED_AT")) {
                    var forbidden = Map.of(reserved, "unsafe");
                    assertThrows(IllegalArgumentException.class,
                            () -> fixture.module.updateWithDatabaseTimestamp(TABLE, TENANT, "tid", "row-a", forbidden, active, "changed_at"));
                    assertThrows(IllegalArgumentException.class,
                            () -> fixture.module.updateWithDatabaseTimestamp(TABLE, TENANT, "tid", "row-a", values, forbidden, "changed_at"));
                }
                return null;
            });

            fixture.jdbc.update("UPDATE validation_row SET parent_key='shared' WHERE tenant_id=?", TENANT);
            assertThrows(IllegalStateException.class,
                    () -> fixture.db.transaction(ignored -> fixture.module.updateWithDatabaseTimestamp(TABLE, TENANT,
                            "parent_key", "shared", values, active, "changed_at")));
        });
        assertEquals("first", fixture.note(TENANT, "row-a"));
        assertEquals("second", fixture.note(TENANT, "row-b"));
    }

    @Test
    void updatesWithOneStatementOnlyWhenTheScopedRelatedKeyExists() {
        var fixture = new Fixture();
        fixture.addRelated(OTHER_TENANT, "row-a", "organization-a", 0);
        fixture.addRelated(TENANT, "row-b", "organization-a", 0);
        fixture.addRelated(TENANT, "row-a", "organization-a", 1);
        fixture.addRelated(TENANT, "row-a", "organization-b", 0);
        fixture.jdbc.setQueryTimeout(7);
        var time = new Date(1_795_000_000_123L);
        var changes = Map.of("note", "updated ' safely", "changed_at", time);
        var target = Map.of("is_del", 0, "note", "first");
        var organizations = new ArrayList<>(IntStream.range(0, 1_000).mapToObj(index -> "other-" + index).toList());
        organizations.add("organization-a");
        var related = Map.of("is_del", 0, "scope_id", organizations);

        TenantContext.run(TENANT, () -> {
            var transaction = fixture.db.transaction();
            try {
                assertEquals(0, fixture.updateIfExists("row-a", changes, target, related));
                fixture.addRelated(TENANT, "row-a", "organization-a", 0);
                assertEquals(0, fixture.updateIfExists("row-a", changes, target,
                        Map.of("is_del", 0, "scope_id", List.of())));
                assertEquals(0, fixture.updateIfExists("row-a' OR '1'='1", changes, target, related));
                assertEquals(1, fixture.updateIfExists("row-a", changes, target, related));
                assertEquals(0, fixture.updateIfExists("row-a", changes, target, related));
                assertEquals("updated ' safely", fixture.note(TENANT, "row-a"));
                assertEquals("foreign", fixture.note(OTHER_TENANT, "row-a"));
                assertEquals(time.getTime(), fixture.jdbc.queryForObject(
                        "SELECT changed_at FROM validation_row WHERE tenant_id=? AND tid=?", Timestamp.class,
                        TENANT, "row-a").getTime());
                assertEquals(5, fixture.source.conditionalUpdates);
                assertEquals(7, fixture.source.conditionalTimeout);
                assertEquals(0, fixture.source.batches);
            } finally {
                transaction.rollback();
            }
        });
        assertEquals("first", fixture.note(TENANT, "row-a"));
    }

    @Test
    void rejectsInvalidConditionalUpdateInputsBeforePreparingSql() {
        var fixture = new Fixture();
        TenantContext.run(TENANT, () -> {
            var changes = Map.of("note", "updated");
            assertThrows(IllegalStateException.class,
                    () -> fixture.updateIfExists("row-a", changes, Map.of(), Map.of()));
            assertThrows(IllegalArgumentException.class, () -> fixture.module.updateIfExists(
                    TABLE, OTHER_TENANT, "tid", "row-a", changes, Map.of(), RELATED, "row_id", Map.of()));
            var transaction = fixture.db.transaction();
            try {
                assertThrows(IllegalArgumentException.class,
                        () -> fixture.updateIfExists(" ", changes, Map.of(), Map.of()));
                assertThrows(IllegalArgumentException.class,
                        () -> fixture.updateIfExists("row-a", Map.of("TID", "new"), Map.of(), Map.of()));
                assertThrows(IllegalArgumentException.class,
                        () -> fixture.updateIfExists("row-a", Map.of("tenant_id", OTHER_TENANT), Map.of(), Map.of()));
                assertThrows(IllegalArgumentException.class,
                        () -> fixture.updateIfExists("row-a", Map.of("note", new Object()), Map.of(), Map.of()));
                assertThrows(IllegalArgumentException.class,
                        () -> fixture.updateIfExists("row-a", changes, Map.of("TENANT_ID", OTHER_TENANT), Map.of()));
                assertThrows(IllegalArgumentException.class,
                        () -> fixture.updateIfExists("row-a", changes, Map.of(), Map.of("row_id", "row-b")));
                assertThrows(IllegalArgumentException.class,
                        () -> fixture.updateIfExists("row-a", changes, Map.of(), Map.of("scope_id", List.of(Map.of()))));
                assertThrows(IllegalArgumentException.class, () -> fixture.module.updateIfExists(
                        TABLE, TENANT, "tid", "row-a", changes, Map.of(), RELATED + "; SELECT 1", "row_id", Map.of()));
                assertEquals(0, fixture.source.conditionalUpdates);
            } finally {
                transaction.rollback();
            }
        });
    }

    @Test
    void rollsBackWhenTheGivenKeyMatchesMoreThanOneTarget() {
        var fixture = new Fixture();
        fixture.jdbc.update("UPDATE validation_row SET parent_key='shared' WHERE tenant_id=?", TENANT);
        fixture.addRelated(TENANT, "shared", "organization-a", 0);
        TenantContext.run(TENANT, () -> assertThrows(IllegalStateException.class,
                () -> fixture.db.transaction(ignored -> fixture.module.updateIfExists(TABLE, TENANT, "parent_key",
                        "shared", Map.of("note", "must roll back"), Map.of("is_del", 0), RELATED, "row_id", Map.of()))));
        assertEquals("first", fixture.note(TENANT, "row-a"));
        assertEquals("second", fixture.note(TENANT, "row-b"));
        assertEquals(1, fixture.source.conditionalUpdates);
    }

    private static final class Fixture {
        final ObservedDataSource source = new ObservedDataSource();
        final JdbcTemplate jdbc = new JdbcTemplate(source);
        final StructuredTableWriteMagicModule module = new StructuredTableWriteMagicModule(jdbc);
        final SQLModule db;

        Fixture() {
            jdbc.execute("""
                    CREATE TABLE validation_row (tenant_id VARCHAR, tid VARCHAR, parent_key VARCHAR, scope_id VARCHAR,
                        note VARCHAR, payload CLOB, changed_at TIMESTAMP, is_del INT, PRIMARY KEY (tenant_id, tid))
                    """);
            jdbc.update("INSERT INTO validation_row VALUES (?, 'row-a', 'parent-a', 'organization-a', 'first', ?, NULL, 0)",
                    TENANT, "validation payload");
            jdbc.update("INSERT INTO validation_row VALUES (?, 'row-b', 'parent-b', 'organization-b', 'second', NULL, NULL, 0)", TENANT);
            jdbc.update("INSERT INTO validation_row VALUES (?, 'row-a', 'parent-a', 'organization-a', 'foreign', NULL, NULL, 0)", OTHER_TENANT);
            jdbc.execute("CREATE TABLE validation_related (tenant_id VARCHAR, row_id VARCHAR, scope_id VARCHAR, is_del INT)");
            var magicSources = new MagicDynamicDataSource();
            magicSources.setDefault(source);
            db = new SQLModule(magicSources);
            db.setDataSourceNode(magicSources.getDataSource());
        }

        String note(String tenant, String id) {
            return jdbc.queryForObject("SELECT note FROM validation_row WHERE tenant_id=? AND tid=?", String.class, tenant, id);
        }

        void addRelated(String tenant, String row, String scope, int deleted) {
            jdbc.update("INSERT INTO validation_related VALUES (?, ?, ?, ?)", tenant, row, scope, deleted);
        }

        int updateIfExists(Object key, Map<String, ?> values, Map<String, ?> target, Map<String, ?> related) {
            return module.updateIfExists(TABLE, TENANT, "tid", key, values, target, RELATED, "row_id", related);
        }
    }

    private static final class ObservedDataSource extends DriverManagerDataSource {
        int opens;
        int batches;
        int conditionalUpdates;
        int conditionalTimeout;
        int[] batchCounts;

        ObservedDataSource() {
            super("jdbc:h2:mem:structured_write_" + UUID.randomUUID() + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        }

        @Override
        public Connection getConnection() throws SQLException {
            opens++;
            Connection connection = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        Object result = invoke(connection, method, args);
                        if (!method.getName().equals("prepareStatement")) return result;
                        boolean conditional = ((String) args[0]).contains(" AND EXISTS (SELECT 1 FROM ");
                        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class},
                                (statementProxy, statementMethod, statementArgs) -> {
                                    Object statementResult = invoke(result, statementMethod, statementArgs);
                                    if (conditional && statementMethod.getName().equals("executeUpdate")) conditionalUpdates++;
                                    if (conditional && statementMethod.getName().equals("setQueryTimeout")) {
                                        conditionalTimeout = (int) statementArgs[0];
                                    }
                                    if (!statementMethod.getName().equals("executeBatch")) return statementResult;
                                    batches++;
                                    return batchCounts == null ? statementResult : batchCounts;
                                });
                    });
        }

        private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException error) {
                throw error.getCause();
            }
        }
    }
}
