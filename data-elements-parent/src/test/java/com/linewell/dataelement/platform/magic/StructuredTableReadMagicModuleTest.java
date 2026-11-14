package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.magic.StructuredTableReadMagicModule.Expression;
import com.linewell.dataelement.platform.persistence.KingbaseSql;
import com.linewell.dataelement.utils.StructuredValues;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
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
    void jsonWildcardOrdinalUsesGroupedJoinAndDoesNotExpandForPageTotal() {
        var fixture = new Fixture();
        var jdbc = new JdbcTemplate(fixture.source());
        jdbc.execute("CREATE TABLE collection (id VARCHAR, tenant_id VARCHAR, payload CLOB)");
        jdbc.update("INSERT INTO collection VALUES ('outside', ?, '{\"entries\":[1,2]}')", OTHER_TENANT);

        TenantContext.run(TENANT, () -> {
            var module = fixture.module();
            var child = module.from("collection", "c", TENANT);
            var childId = module.column("c", "id", "TEXT");
            var ordinal = child.leftJoinJsonArray("payload", "entries", "entry_row");
            var parent = module.from("collection", "p", TENANT);
            parent.leftJoinGroupedProjection(child, "stats", "collectionId", "id",
                    Map.of("collectionId", childId, "entryCount", module.count(ordinal)), List.of(childId));
            var count = module.column("stats", "entryCount", "NUMBER");
            var fields = Map.of("entryCount", count);

            assertEquals(Map.of("total", 0L, "rows", List.of()), parent.page(fields, count, "desc", "id", 1, 20));
            assertEquals(1, fixture.source().reads);
            assertFalse(fixture.source().lastSelect.contains("JSON_TABLE"));

            jdbc.update("INSERT INTO collection VALUES ('inside', ?, '{}')", TENANT);
            fixture.source().captureJsonSql = true;
            assertThrows(CapturedSql.class, () -> parent.page(fields, count, "desc", "id", 1, 100));
            assertEquals(3, fixture.source().reads);
            String sql = fixture.source().lastSelect;
            assertTrue(sql.contains("COUNT(entry_row.ordinal)"), sql);
            assertTrue(sql.contains("COLUMNS(ordinal FOR ORDINALITY)) entry_row ON 1=1"), sql);
            assertTrue(sql.contains("ELSE '{}' END, '$.entries[*]'"), sql);
            assertTrue(sql.contains(" GROUP BY c.\"id\""), sql);
            assertEquals(2, sql.split("tenant_id", -1).length - 1, sql);
            String adapted = KingbaseSql.adapt(sql);
            assertTrue(adapted.contains("LEFT JOIN LATERAL (SELECT entry.ordinality AS \"ordinal\""), adapted);
            assertTrue(adapted.contains("FROM public.json_table_elements("), adapted);

            assertThrows(IllegalArgumentException.class, () -> child.leftJoinJsonArray("payload", "entries[*]", "bad"));
            assertThrows(IllegalArgumentException.class, () -> child.leftJoinJsonArray("payload", "entries", "entry_row"));
            assertThrows(IllegalArgumentException.class, () -> child.leftJoinJsonArray("payload;", "entries", "bad"));
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
    void windowBudgetChecksRankedTenantRowsAndMasksOversizedPayloadsInOneRead() {
        var fixture = new Fixture();
        var jdbc = new JdbcTemplate(fixture.source());
        jdbc.execute("CREATE TABLE inventory (tid VARCHAR, tenant_id VARCHAR, name VARCHAR, priority INT, config CLOB)");
        jdbc.update("INSERT INTO inventory VALUES ('a', ?, 'Alpha', 9, '数表')", TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('b', ?, 'Beta', 9, 'abc')", TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('shadow', ?, 'alpha', 1, 'irrelevant large value')", TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('empty', ?, 'Other', 9, '')", TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('old', ?, 'Other', 1, 'never restored')", TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('foreign', ?, 'Alpha', 99, 'foreign data')", OTHER_TENANT);

        TenantContext.run(TENANT, () -> {
            var module = fixture.module();
            var source = module.from("inventory", "t", TENANT);
            var rootId = module.column("t", "tid", "TEXT");
            var ranked = source.ranked("r", Map.of("tid", rootId, "config", module.column("t", "config", "LOB")),
                    List.of(module.lower(module.column("t", "name", "TEXT"))),
                    List.of(module.column("t", "priority", "NUMBER"), rootId), List.of("desc", "desc"));
            var rankedId = module.column("r", "tid", "TEXT");
            var config = module.column("r", "config", "LOB");
            ranked.where(module.not(module.isNull(config)));
            ranked.where(module.not(module.equal(config, "")));
            var budget = ranked.projected("b", Map.of("tid", rankedId, "config", config,
                    "config_count", module.overAll(module.count()),
                    "config_size", module.overAll(module.sum(module.textLength(config))),
                    "marker_id", module.overAll(module.max(rankedId))));
            ranked.where(module.equal(rankedId, "missing"));
            assertEquals(List.of(), ranked.grouped(Map.of("id", rankedId), List.of()));

            fixture.source().reads = 0;
            var within = readBudget(module, budget, 2, 5);
            assertEquals(List.of("a", "b"), within.stream().map(row -> row.get("id")).toList());
            assertEquals(List.of("数表", "abc"), within.stream().map(row -> row.get("payload")).toList());
            assertEquals(List.of("normal", "normal"), within.stream().map(row -> row.get("budget")).toList());
            assertEquals(1, fixture.source().reads);
            String sql = fixture.source().lastSelect;
            assertTrue(sql.contains("COUNT(*) OVER ()"), sql);
            assertTrue(sql.contains("SUM(LENGTH("), sql);
            assertTrue(sql.contains("MAX(") && sql.contains(" OVER ()"), sql);
            assertTrue(sql.contains("THEN NULL ELSE"), sql);
            assertEquals(1, sql.split("ROW_NUMBER\\(", -1).length - 1, sql);

            var overCount = readBudget(module, budget, 1, 5);
            var overLength = readBudget(module, budget, 2, 4);
            assertEquals(overCount, overLength);
            assertEquals(1, overCount.size());
            assertEquals("b", overCount.getFirst().get("id"));
            assertEquals("exceeded", overCount.getFirst().get("budget"));
            assertNull(overCount.getFirst().get("payload"));
            assertEquals(3, fixture.source().reads);
        });
    }

    private List<Map<String, Object>> readBudget(StructuredTableReadMagicModule module,
            StructuredTableReadMagicModule.Query budget, int maxCount, int maxLength) {
        var output = budget.projected("o", Map.of("tid", module.column("b", "tid", "TEXT"),
                "config", module.column("b", "config", "LOB"),
                "config_count", module.column("b", "config_count", "NUMBER"),
                "config_size", module.column("b", "config_size", "NUMBER"), "marker_id", module.column("b", "marker_id", "TEXT")));
        var outputId = module.column("o", "tid", "TEXT");
        var outputExceeded = module.any(List.of(module.greaterThan(module.column("o", "config_count", "NUMBER"), maxCount),
                module.greaterThan(module.column("o", "config_size", "NUMBER"), maxLength)));
        output.where(module.any(List.of(module.not(outputExceeded),
                module.equal(outputId, module.column("o", "marker_id", "TEXT")))));
        return output.rows(Map.of("id", outputId, "budget", module.when(outputExceeded, "exceeded", "normal"),
                "payload", module.when(outputExceeded, null, module.column("o", "config", "LOB"))), outputId, "asc", 10);
    }

    @Test
    void nativeLengthMeasuresTextAndLobAndKeepsNull() {
        var fixture = new Fixture();
        var jdbc = new JdbcTemplate(fixture.source());
        jdbc.execute("CREATE TABLE inventory (tid VARCHAR, tenant_id VARCHAR, label VARCHAR, payload CLOB)");
        jdbc.update("INSERT INTO inventory VALUES ('a', ?, '数表 ', '数表 ')", TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('b', ?, NULL, NULL)", TENANT);

        TenantContext.run(TENANT, () -> {
            var module = fixture.module();
            var id = module.column("t", "tid", "TEXT");
            var query = module.from("inventory", "t", TENANT);
            var result = query.rows(Map.of("text_size", module.textLength(module.column("t", "label", "TEXT")),
                    "lob_size", module.textLength(module.column("t", "payload", "LOB"))), id, "asc", 10);
            assertEquals(3, ((Number) result.getFirst().get("text_size")).intValue());
            assertEquals(3, ((Number) result.getFirst().get("lob_size")).intValue());
            assertNull(result.get(1).get("text_size"));
            assertNull(result.get(1).get("lob_size"));
            assertTrue(fixture.source().lastSelect.contains("LENGTH(t.\"label\")"));
            assertTrue(fixture.source().lastSelect.contains("LENGTH(t.\"payload\")"));
        });
    }

    @Test
    void jsonProjectionPreservesExactOriginalForDialectsWithoutCompactSupport() {
        var fixture = new Fixture();
        var jdbc = new JdbcTemplate(fixture.source());
        jdbc.execute("CREATE TABLE inventory (tid VARCHAR, tenant_id VARCHAR, payload CLOB)");
        String canonical = "{\"phase\":\"business\",\"fields\":[{\"standard\":\"数\"}]}";
        String legacy = "  {phase:'Business';fields:[{},],meta:'原文'}  \r\n";
        jdbc.update("INSERT INTO inventory VALUES ('a', ?, ?), ('b', ?, ?), ('c', ?, NULL), ('d', ?, ?)",
                TENANT, canonical, TENANT, legacy, TENANT, OTHER_TENANT, "{\"private\":true}");

        TenantContext.run(TENANT, () -> {
            var module = fixture.module();
            var query = module.from("inventory", "r", TENANT);
            var id = module.column("r", "tid", "TEXT");
            var input = module.column("r", "payload", "LOB");
            var compact = module.jsonProjection(input,
                    List.of(module.jsonTextEquals(List.of("phase"), "business")), "fields", List.of(
                            List.of(module.jsonNonblank(List.of("standard"))),
                            List.of(module.jsonTrue(List.of("dictionary", "enabled")),
                                    module.jsonNonblank(List.of("dictionary", "sql")))));
            var rows = query.rows(Map.of("id", id, "result", compact), id, "asc", 10);
            assertEquals(List.of("a", "b", "c"), rows.stream().map(row -> row.get("id")).toList());
            assertEquals("R" + canonical, rows.get(0).get("result"));
            assertEquals("R" + legacy, rows.get(1).get("result"));
            assertNull(rows.get(2).get("result"));
            assertEquals(1, fixture.source().reads);
        });
    }

    @Test
    void jsonProjectionRejectsUntrustedPathsAndUnboundedExpressions() {
        var module = new Fixture().module();
        var input = module.column("r", "payload", "TEXT");
        var safe = module.jsonNonblank(List.of("field"));
        assertThrows(IllegalArgumentException.class, () -> module.jsonNonblank(List.of()));
        assertThrows(IllegalArgumentException.class, () -> module.jsonTrue(List.of("a", "b", "c", "d", "e")));
        assertThrows(IllegalArgumentException.class, () -> module.jsonTrue(List.of("x') OR 1=1")));
        assertThrows(IllegalArgumentException.class, () -> module.jsonTextEquals(List.of("field"), null));
        assertThrows(IllegalArgumentException.class, () -> module.jsonProjection(input,
                List.of(safe), "fields[0]", List.of(List.of(safe))));
        assertThrows(IllegalArgumentException.class, () -> module.jsonProjection(module.column("r", "size", "NUMBER"),
                List.of(safe), "fields", List.of(List.of(safe))));
        assertThrows(IllegalArgumentException.class, () -> module.jsonProjection(input,
                List.of(), "fields", List.of(List.of(safe))));
        assertThrows(IllegalArgumentException.class, () -> module.jsonProjection(input,
                List.of(safe), "fields", List.of()));
        assertThrows(IllegalArgumentException.class, () -> module.jsonProjection(input,
                List.of(safe), "fields", List.of(List.of())));
        assertThrows(IllegalArgumentException.class, () -> module.jsonProjection(input,
                List.of(safe), "fields", List.of(java.util.Collections.nCopies(5, safe))));
        assertThrows(IllegalArgumentException.class, () -> module.jsonProjection(input,
                java.util.Collections.nCopies(16, safe), "fields", List.of(List.of(safe))));
    }

    @Test
    void derivedProjectionAndWindowsRejectUntrustedStructure() {
        var fixture = new Fixture();
        var module = fixture.module();
        TenantContext.run(TENANT, () -> {
            var query = module.from("inventory", "t", TENANT);
            var id = module.column("t", "tid", "TEXT");
            var projection = Map.of("id", id);
            assertThrows(IllegalArgumentException.class, () -> module.textLength(module.column("t", "tid", "NUMBER")));
            assertThrows(IllegalArgumentException.class, () -> module.overAll(id));
            assertThrows(IllegalArgumentException.class, () -> module.overAll(module.countDistinct(id)));
            assertThrows(IllegalArgumentException.class, () -> module.overAll(module.overAll(module.count())));
            assertThrows(IllegalArgumentException.class, () -> query.projected("r WHERE 1=1", projection));
            assertThrows(IllegalArgumentException.class, () -> query.projected("r", Map.of("id;DROP", id)));
            assertThrows(IllegalArgumentException.class, () -> query.projected("r", Map.of("id", id, "ID", id)));
            assertThrows(IllegalArgumentException.class, () -> query.projected("r", Map.of("tenant_id", id)));
            assertThrows(IllegalArgumentException.class, () -> query.projected("r", Map.of("TENANT_ID", id)));
            assertThrows(IllegalArgumentException.class, () -> query.projected("r", Map.of()));
            var derived = query.projected("r", projection);
            assertThrows(IllegalArgumentException.class, () -> TenantContext.run(OTHER_TENANT,
                    () -> derived.grouped(Map.of("id", module.column("r", "id", "TEXT")), List.of())));
            assertThrows(IllegalArgumentException.class,
                    () -> TenantContext.run(OTHER_TENANT, () -> query.projected("r", projection)));
            assertEquals(0, fixture.source().reads);
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
    void chainedJoinsKeepEveryHopTenantScopedAndUseOneBoundedRead() {
        var fixture = new Fixture();
        var jdbc = new JdbcTemplate(fixture.source());
        jdbc.execute("CREATE TABLE tasks (tid VARCHAR, tenant_id VARCHAR, source_id VARCHAR, created_time INT)");
        jdbc.execute("CREATE TABLE sources (tid VARCHAR, tenant_id VARCHAR, parent_id VARCHAR, is_del INT)");
        jdbc.update("INSERT INTO tasks VALUES ('first', ?, 'leaf', NULL), ('second', ?, 'missing', 1),"
                + " ('foreign-task', ?, 'leaf', 0)", TENANT, TENANT, OTHER_TENANT);
        jdbc.update("INSERT INTO sources VALUES ('leaf', ?, 'root', 0), ('root', ?, 'root', 0),"
                + " ('root', ?, 'foreign-root', 0), ('missing', ?, 'root', 0)", TENANT, TENANT,
                OTHER_TENANT, OTHER_TENANT);

        TenantContext.run(TENANT, () -> {
            var module = fixture.module();
            var query = module.from("tasks", "r", TENANT).withTimeoutSeconds(10);
            query.leftJoin("sources", "s0", "tid", "source_id",
                    List.of(module.equal(module.column("s0", "is_del", "NUMBER"), 0)));
            for (int depth = 1; depth <= 2; depth++) {
                String parent = "s" + (depth - 1);
                String current = "s" + depth;
                var rootMarker = module.equal(module.column(parent, "parent_id", "TEXT"),
                        module.column(parent, "tid", "TEXT"));
                query.leftJoinFrom(parent, "sources", current, "tid", "parent_id",
                        List.of(module.equal(module.column(current, "is_del", "NUMBER"), 0), module.not(rootMarker)));
            }
            var id = module.column("r", "tid", "TEXT");
            var columns = Map.of("id", id, "child", module.column("s0", "tid", "TEXT"),
                    "parent", module.column("s1", "tid", "TEXT"), "grandparent", module.column("s2", "tid", "TEXT"));
            var ordering = List.of(module.column("r", "created_time", "NUMBER"), id);
            var rows = query.rows(columns, ordering, List.of("asc", "asc"), 2, true);
            assertEquals(List.of("first", "second"), rows.stream().map(row -> row.get("id")).toList());
            assertEquals("root", rows.getFirst().get("parent"));
            assertNull(rows.getFirst().get("grandparent"));
            assertNull(rows.getLast().get("child"));
            assertEquals(1, fixture.source().reads);
            String sql = fixture.source().lastSelect.replace("`", "\"");
            assertTrue(sql.contains("s1.\"tenant_id\" = r.\"tenant_id\""), sql);
            assertTrue(sql.contains("s1.\"tid\" = s0.\"parent_id\""), sql);
            assertFalse(sql.contains("CASE WHEN"), sql);

            query.where(module.equal(id, "first"));
            assertEquals(List.of(rows.getFirst()), query.rows(columns, 2));
            assertEquals(2, fixture.source().reads);
            assertFalse(fixture.source().lastSelect.contains("ORDER BY"));
            assertThrows(IllegalArgumentException.class,
                    () -> query.leftJoinFrom("unknown", "sources", "s3", "tid", "parent_id", List.of()));
            assertThrows(IllegalArgumentException.class,
                    () -> query.leftJoinFrom("s0", "sources", "S1", "tid", "parent_id", List.of()));
            assertThrows(IllegalArgumentException.class,
                    () -> query.leftJoinFrom("s0", "unsafe;select", "s3", "tid", "parent_id", List.of()));
            assertThrows(IllegalArgumentException.class, () -> query.withTimeoutSeconds(0));
            assertThrows(IllegalArgumentException.class, () -> query.rows(columns, 0));
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
    void literalSearchKeepsNativeCaseAndMatchesSymbolsInLongText() {
        var fixture = new Fixture();
        var jdbc = new JdbcTemplate(fixture.source());
        jdbc.execute("CREATE TABLE inventory (tid VARCHAR, tenant_id VARCHAR, body CLOB)");
        String needle = "quoted'%_!\\tail";
        String prefix = "数".repeat(6000);
        jdbc.update("INSERT INTO inventory VALUES ('literal', ?, ?)", TENANT, prefix + needle);
        jdbc.update("INSERT INTO inventory VALUES ('case', ?, ?)", TENANT, prefix + needle.toUpperCase(Locale.ROOT));
        jdbc.update("INSERT INTO inventory VALUES ('wildcard', ?, ?)", TENANT, prefix + "quoted'axb!\\tail");
        jdbc.update("INSERT INTO inventory VALUES ('foreign', ?, ?)", OTHER_TENANT, prefix + needle);
        jdbc.update("INSERT INTO inventory VALUES ('missing', ?, NULL)", TENANT);

        TenantContext.run(TENANT, () -> {
            var module = fixture.module();
            var id = module.column("r", "tid", "TEXT");
            var body = module.column("r", "body", "LOB");
            var query = module.from("inventory", "r", TENANT);
            query.where(module.containsLiteral(body, needle));
            assertEquals(List.of(Map.of("id", "literal")), query.grouped(Map.of("id", id), List.of()));
            assertThrows(IllegalArgumentException.class, () -> module.containsLiteral(body, null));
        });
    }

    @Test
    void nativeSubstringKeepsCaseAndWildcardCharactersAcrossTheWholeClob() {
        var fixture = new Fixture();
        var jdbc = new JdbcTemplate(fixture.source());
        jdbc.execute("CREATE TABLE inventory (tid VARCHAR, tenant_id VARCHAR, body CLOB)");
        String prefix = "数".repeat(16_000);
        jdbc.update("INSERT INTO inventory VALUES ('literal', ?, ?)", TENANT, prefix + " %_! Saved");
        jdbc.update("INSERT INTO inventory VALUES ('case', ?, ?)", TENANT, prefix + " %_! SAVED");
        jdbc.update("INSERT INTO inventory VALUES ('wildcard', ?, ?)", TENANT, prefix + " x_y! Saved");
        jdbc.update("INSERT INTO inventory VALUES ('missing', ?, NULL)", TENANT);

        TenantContext.run(TENANT, () -> {
            var module = fixture.module();
            var id = module.column("r", "tid", "TEXT");
            var body = module.column("r", "body", "LOB");
            var query = module.from("inventory", "r", TENANT);
            query.where(module.hasSubstring(module.replace(body, " ", ""), "%_!Saved"));
            assertEquals(List.of(Map.of("id", "literal")), query.grouped(Map.of("id", id), List.of()));
            assertTrue(fixture.source().lastSelect.contains("INSTR(REPLACE("));
        });
    }

    @Test
    void groupedJoinWithFixedTextWidthComparesCompleteKeysAndPreservesPadding() {
        var fixture = new Fixture();
        var jdbc = new JdbcTemplate(fixture.source());
        jdbc.execute("CREATE TABLE inventory (tid VARCHAR, tenant_id VARCHAR)");
        jdbc.execute("CREATE TABLE records (inventory_id VARCHAR, tenant_id VARCHAR)");
        jdbc.update("INSERT INTO inventory VALUES ('source-1', ?), ('source-2', ?)", TENANT, TENANT);
        jdbc.update("INSERT INTO records VALUES ('source-2 ', ?), ('source-1', ?)", TENANT, OTHER_TENANT);

        TenantContext.run(TENANT, () -> {
            var module = fixture.module();
            var query = module.from("inventory", "r", TENANT);
            var child = module.from("records", "c", TENANT);
            var key = module.column("c", "inventory_id", "TEXT");
            var projection = Map.of("record_key", key, "record_count", module.count());
            assertThrows(IllegalArgumentException.class,
                    () -> query.leftJoinGrouped(child, "agg", "record_key", "tid", projection, List.of(key), 0));
            query.leftJoinGrouped(child, "agg", "record_key", "tid", projection, List.of(key), 64);
            var id = module.column("r", "tid", "TEXT");
            var count = module.column("agg", "record_count", "NUMBER");
            var rows = query.rows(Map.of("id", id, "recordCount", count), id, "asc", 10);
            assertEquals(2, rows.size());
            assertNull(rows.get(0).get("recordCount"));
            assertEquals(1, ((Number) rows.get(1).get("recordCount")).intValue());
            assertTrue(fixture.source().lastSelect.contains(" AS CHAR(64))"));
        });
    }

    @Test
    void boundedTextPrefixTrimsBeforeSlicingAndKeepsNull() {
        var fixture = new Fixture();
        var jdbc = new JdbcTemplate(fixture.source());
        jdbc.execute("CREATE TABLE inventory (tid VARCHAR, tenant_id VARCHAR, body CLOB)");
        String expected = "数".repeat(512);
        jdbc.update("INSERT INTO inventory VALUES ('long', ?, ?)", TENANT, "   " + expected + "据".repeat(2000));
        jdbc.update("INSERT INTO inventory VALUES ('missing', ?, NULL)", TENANT);
        jdbc.update("INSERT INTO inventory VALUES ('spaces', ?, '   ')", TENANT);

        TenantContext.run(TENANT, () -> {
            var module = fixture.module();
            var id = module.column("r", "tid", "TEXT");
            var body = module.column("r", "body", "LOB");
            var prefix = module.trimmedTextPrefix(body, 512);
            var query = module.from("inventory", "r", TENANT);
            var rows = query.rows(Map.of("id", id, "prefix", prefix), id, "asc", 10);
            assertEquals(List.of("long", "missing", "spaces"), rows.stream().map(row -> row.get("id")).toList());
            assertEquals(expected, rows.get(0).get("prefix"));
            assertNull(rows.get(1).get("prefix"));
            assertEquals("", rows.get(2).get("prefix"));
            assertThrows(IllegalArgumentException.class, () -> module.trimmedTextPrefix(body, 0));
            assertThrows(IllegalArgumentException.class, () -> module.trimmedTextPrefix(body, 1001));
            assertThrows(IllegalArgumentException.class,
                    () -> module.trimmedTextPrefix(module.column("r", "tid", "NUMBER"), 512));
        });
    }

    @Test
    void oraclePrefixSlicesTheLobBeforeConvertingToBoundedVarchar() {
        var source = new CountedSource("jdbc:oracle:thin:@validation");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE inventory (tid VARCHAR, tenant_id VARCHAR, body CLOB)");
        String expected = "数".repeat(512);
        jdbc.update("INSERT INTO inventory VALUES ('long', ?, ?)", TENANT, "   " + expected + "据".repeat(2000));

        TenantContext.run(TENANT, () -> {
            var module = new StructuredTableReadMagicModule(jdbc);
            var query = module.from("inventory", "r", TENANT);
            var body = module.column("r", "body", "LOB");
            query.where(module.equal(module.column("r", "tid", "TEXT"), "long"));
            var rows = query.grouped(Map.of("prefix", module.trimmedTextPrefix(body, 512)), List.of());
            assertEquals(List.of(Map.of("prefix", expected)), rows);
            assertEquals("SELECT CAST(SUBSTR(TRIM(SUBSTR(r.\"body\", 1, 2048)), 1, ?) AS VARCHAR2(2048 BYTE)) AS \"prefix\""
                    + " FROM \"inventory\" r WHERE r.\"tenant_id\" = ? AND r.\"tid\" = ?", source.lastSelect);
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
        private final String dialectUrl;
        private int reads;
        private String lastSelect;
        private boolean captureJsonSql;

        CountedSource() {
            this("jdbc:mysql://validation");
        }

        CountedSource(String dialectUrl) {
            super("jdbc:h2:mem:structured_read_" + UUID.randomUUID()
                    + ";MODE=" + (dialectUrl.startsWith("jdbc:oracle:") ? "Oracle" : "MySQL")
                    + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            this.dialectUrl = dialectUrl;
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
                                                return "getURL".equals(metaMethod.getName()) ? dialectUrl
                                                        : metaMethod.invoke(metadata, metaArgs);
                                            } catch (InvocationTargetException error) {
                                                throw error.getCause();
                                            }
                                        });
                            }
                            if ("prepareStatement".equals(method.getName())
                                    && ((String) arguments[0]).stripLeading().startsWith("SELECT")) {
                                reads++;
                                lastSelect = (String) arguments[0];
                                if (captureJsonSql && lastSelect.contains("JSON_TABLE")) throw new CapturedSql();
                            }
                            return method.invoke(actual, arguments);
                        } catch (InvocationTargetException error) {
                            throw error.getCause();
                        }
                    });
        }
    }

    private static final class CapturedSql extends RuntimeException { }
}
