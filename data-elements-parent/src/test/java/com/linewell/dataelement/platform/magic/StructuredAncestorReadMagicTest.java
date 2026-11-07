package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.platform.magic.StructuredTableReadMagicModule.Expression;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;

class StructuredAncestorReadMagicTest {
    private static final String TENANT = "validation-tenant";

    @ParameterizedTest
    @CsvSource({"mysql, true", "kingbase8, true", "dm, false"})
    void bindsAncestorParametersBeforePageParametersAndOmitsProjectionJoinFromTotal(
            String dialect, boolean recursiveKeyword) throws SQLException {
        var fixture = new Fixture(dialect);
        var module = fixture.module;
        TenantContext.run(TENANT, () -> {
            var seed = module.from("event_t", "e", TENANT);
            seed.where(module.equal(module.column("e", "state", "TEXT"), "valid'seed"));
            var payload = new LinkedHashMap<String, Expression>();
            payload.put("changed_at", module.epochMillis(module.column("e", "event_millis", "NUMBER")));
            payload.put("event_id", module.column("e", "tid", "TEXT"));
            payload.put("label", module.replace(module.trim(module.column("e", "label", "TEXT")), "_", ""));
            var ancestors = seed.ancestors("walk", "record_t", "parent", "tid", "parent_id",
                    module.column("e", "node_id", "TEXT"), module.column("e", "parent_id", "TEXT"), payload,
                    List.of(module.equal(module.column("parent", "is_del", "NUMBER"), 0)), 2);

            var query = module.from("record_t", "r", TENANT);
            query.where(module.equal(module.column("r", "status", "TEXT"), "allowed"));
            var node = module.column("walk", "node_id", "TEXT");
            query.leftJoinGroupedProjection(ancestors, "summary", "node_id", "tid",
                    Map.of("node_id", node, "latest", module.max(module.column("walk", "changed_at", "TIME")),
                            "events", module.countDistinct(module.column("walk", "event_id", "TEXT"))),
                    List.of(node));
            var label = module.coalesce(List.of(module.column("r", "label", "TEXT"), "outer default"));
            var last = module.column("summary", "latest", "TIME");
            var result = query.pageRootRows(Map.of("label", label), last, "desc", "tid", 2, 20);
            assertEquals(60L, result.get("total"));
        });

        assertEquals(2, fixture.sql.size());
        assertTrue(fixture.sql.getFirst().startsWith("SELECT COUNT(*)"));
        assertFalse(fixture.sql.getFirst().contains("structured_walk_"));
        assertEquals(List.of(TENANT, "allowed"), bindings(fixture.countStatement));
        String page = fixture.sql.getLast();
        assertTrue(page.startsWith(recursiveKeyword ? "WITH RECURSIVE " : "WITH \"structured_walk_"));
        assertTrue(page.contains("UNION ALL"));
        assertTrue(page.contains("structured_previous"));
        assertTrue(page.contains("SELECT r.*,"));
        assertFalse(page.contains("valid'seed"));
        if ("dm".equals(dialect)) {
            assertTrue(page.contains("SELECT * FROM ( SELECT TMP.*, ROWNUM ROW_ID"));
        }
        assertEquals(List.of(new Timestamp(0), "_", "", TENANT, "valid'seed", 2, 0, "outer default",
                TENANT, TENANT, "allowed"), bindings(fixture.pageStatement).subList(0, 11));
    }

    @Test
    void rejectsUnboundedWalksReservedPayloadAndNonParentConditions() throws SQLException {
        var fixture = new Fixture("mysql");
        var module = fixture.module;
        TenantContext.run(TENANT, () -> {
            var seed = module.from("record_t", "r", TENANT);
            var node = module.column("r", "tid", "TEXT");
            var parent = module.column("r", "parent_id", "TEXT");
            assertThrows(IllegalArgumentException.class, () -> seed.ancestors("walk", "record_t", "p",
                    "tid", "parent_id", node, parent, Map.of(), List.of(), 17));
            assertThrows(IllegalArgumentException.class, () -> seed.ancestors("walk", "record_t", "p",
                    "tid", "parent_id", node, parent, Map.of("NODE_ID", node), List.of(), 1));
            var invalid = seed.ancestors("walk", "record_t", "p", "tid", "parent_id", node, parent, Map.of(),
                    List.of(module.equal(node, "invalid")), 1);
            assertThrows(IllegalArgumentException.class,
                    () -> invalid.grouped(Map.of("id", module.column("walk", "node_id", "TEXT")), List.of()));
            assertTrue(fixture.sql.isEmpty());
        });
    }

    private static List<Object> bindings(PreparedStatement statement) {
        var byIndex = new TreeMap<Integer, Object>();
        for (var invocation : mockingDetails(statement).getInvocations()) {
            var args = invocation.getArguments();
            if (invocation.getMethod().getName().startsWith("set") && args.length >= 2
                    && args[0] instanceof Integer index) {
                byIndex.put(index, args[1]);
            }
        }
        return new ArrayList<>(byIndex.values());
    }

    private static final class Fixture {
        final List<String> sql = new ArrayList<>();
        final PreparedStatement countStatement = mock(PreparedStatement.class);
        final PreparedStatement pageStatement = mock(PreparedStatement.class);
        final StructuredTableReadMagicModule module;

        Fixture(String dialect) throws SQLException {
            var source = mock(DataSource.class);
            var connection = mock(Connection.class);
            var metadata = mock(DatabaseMetaData.class);
            var count = mock(ResultSet.class);
            var countMetadata = mock(ResultSetMetaData.class);
            var rows = mock(ResultSet.class);
            when(source.getConnection()).thenReturn(connection);
            when(connection.getMetaData()).thenReturn(metadata);
            when(metadata.getURL()).thenReturn("jdbc:" + dialect + "://validation");
            when(metadata.getIdentifierQuoteString()).thenReturn("mysql".equals(dialect) ? "`" : "\"");
            when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
                String statement = invocation.getArgument(0);
                sql.add(statement);
                return statement.startsWith("WITH ") ? pageStatement : countStatement;
            });
            when(countStatement.executeQuery()).thenReturn(count);
            when(count.next()).thenReturn(true, false);
            when(count.getMetaData()).thenReturn(countMetadata);
            when(countMetadata.getColumnCount()).thenReturn(1);
            when(count.getLong(1)).thenReturn(60L);
            when(pageStatement.executeQuery()).thenReturn(rows);
            module = new StructuredTableReadMagicModule(new JdbcTemplate(source));
        }
    }
}
