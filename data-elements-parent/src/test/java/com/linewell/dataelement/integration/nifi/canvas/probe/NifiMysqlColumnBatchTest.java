package com.linewell.dataelement.integration.nifi.canvas.probe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.mysql.cj.jdbc.Driver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NifiMysqlColumnBatchTest {

    private static final String URL = "jdbc:mysql://validation.invalid/validation";
    private final JdbcTemplate tenantDb = mock(JdbcTemplate.class);
    private final DataScopeAuthorizationService scopes = mock(DataScopeAuthorizationService.class);
    private final DataSourceConnectionPropertyResolver sources =
            new DataSourceConnectionPropertyResolver(tenantDb, new ObjectMapper(), scopes);
    private final NifiConnectionProbe probe = new NifiConnectionProbe(null, null);

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void oneMagicCallUsesOneSourceLookupAndOnePhysicalQuery(int size) throws Exception {
        List<String> tables = IntStream.range(0, size).mapToObj(i -> "validation_" + i).toList();
        var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        var rows = mock(ResultSet.class);
        when(connection.getCatalog()).thenReturn("validation");
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        int[] index = {-1};
        when(rows.next()).thenAnswer(call -> ++index[0] < size);
        when(rows.getString("TABLE_NAME")).thenAnswer(call -> tables.get(index[0]));
        when(rows.getString("COLUMN_NAME")).thenReturn("record_id");
        when(rows.getString("COLUMN_TYPE")).thenReturn("bigint unsigned");
        when(rows.getString("COLUMN_KEY")).thenReturn("PRI");
        List<Map<String, Object>> allowed = allowRegistration();

        try (var tenant = TenantContext.use("validation-police");
             var driver = mockConstruction(Driver.class,
                     (mock, context) -> when(mock.connect(eq(URL), any())).thenReturn(connection))) {
            var response = NifiConnectionProbeHiveTest.runProbe("nifi_columns_probe_post_v1", Map.of(
                    "manifestKey", "source.mysql", "datasourceId", "allowed", "tables", tables,
                    "tenantId", "validation-forged", "config", Map.of("jdbcURL", "jdbc:mysql://ignored.invalid/other")),
                    probe, sources);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            Map<?, ?> payload = (Map<?, ?>) response.getBody();
            assertThat(payload.get("success")).isEqualTo(true);
            List<?> results = (List<?>) payload.get("results");
            assertThat(results).hasSize(size);
            Map<?, ?> first = (Map<?, ?>) results.getFirst();
            assertThat(first.get("table")).isEqualTo(tables.getFirst());
            Map<?, ?> column = (Map<?, ?>) ((List<?>) first.get("columns")).getFirst();
            assertThat(column.get("primaryKey")).isEqualTo(true);
            assertThat(driver.constructed()).hasSize(1);
            verify(driver.constructed().getFirst()).connect(eq(URL), any());
        }
        verify(tenantDb).queryForList(contains("AND tenant_id = ? AND is_del = 0"), eq("allowed"), eq("validation-police"));
        verify(scopes).visibleDataSources("DATA_SOURCE", allowed);
        verify(connection).prepareStatement(contains("FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ?"));
        verify(statement).setString(1, "validation");
        verify(statement).setString(size + 1, tables.getLast());
        verify(statement).executeQuery();
    }

    @Test
    void rejectsCrossTenantAndRoleFilteredSourcesBeforeTheConnection() {
        List<Map<String, Object>> roleDenied = allowRegistration();
        when(tenantDb.queryForList(anyString(), eq("foreign"), eq("validation-police"))).thenReturn(List.of());
        when(scopes.visibleDataSources(eq("DATA_SOURCE"), anyCollection())).thenReturn(List.of());
        try (var tenant = TenantContext.use("validation-police"); var driver = mockConstruction(Driver.class)) {
            assertThat(probeStatus("allowed", List.of("validation_a", "validation_b"))).isEqualTo(403);
            assertThat(probeStatus("foreign", List.of("validation_a", "validation_b"))).isEqualTo(403);
            assertThat(driver.constructed()).isEmpty();
        }
        verify(scopes).visibleDataSources("DATA_SOURCE", roleDenied);
        verify(scopes).visibleDataSources("DATA_SOURCE", List.of());
    }

    @Test
    void rejectsMixedCatalogsBeforeTheMetadataQuery() throws Exception {
        var connection = mock(Connection.class);
        when(connection.getCatalog()).thenReturn("validation");
        allowRegistration();
        try (var tenant = TenantContext.use("validation-police");
             var driver = mockConstruction(Driver.class,
                     (mock, context) -> when(mock.connect(eq(URL), any())).thenReturn(connection))) {
            assertThat(probeStatus("allowed", List.of("validation.valid", "other.foreign"))).isEqualTo(400);
        }
        verify(connection, never()).prepareStatement(anyString());
    }

    private int probeStatus(String datasourceId, List<String> tables) {
        return NifiConnectionProbeHiveTest.runProbe("nifi_columns_probe_post_v1",
                Map.of("manifestKey", "source.mysql", "datasourceId", datasourceId, "tables", tables),
                probe, sources).getStatusCode().value();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> allowRegistration() {
        List<Map<String, Object>> values = List.of(Map.of("tid", "allowed", "db_type", "MYSQL", "jdbc_url", URL));
        when(tenantDb.execute(any(ConnectionCallback.class))).thenReturn(false);
        when(tenantDb.queryForList(anyString(), eq("allowed"), eq("validation-police"))).thenReturn(values);
        when(scopes.visibleDataSources("DATA_SOURCE", values)).thenReturn(values);
        return values;
    }
}
