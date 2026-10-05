package com.linewell.dataelement.integration.nifi.canvas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.metautil.structured.StructuredSourceProbeService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.ssssssss.magicapi.core.service.MagicAPIService;

class ConnectionTestControllerHiveTest {

    private final MagicAPIService magic = mock(MagicAPIService.class);
    private final DataSourceConnectionPropertyResolver registered = mock(DataSourceConnectionPropertyResolver.class);
    private final ConnectionTestController controller = new ConnectionTestController(
            mock(StructuredSourceProbeService.class), magic, registered);

    @Test
    void exposesAll82DescribeColumnsByLabelInsteadOfMapPosition() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 82; i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("comment", "字段 " + i);
            row.put("data_type", i == 0 ? "decimal(18,4)" : "varchar(32)");
            row.put("col_name", "field_" + i);
            rows.add(row);
        }
        when(magic.call(eq("POST"), anyString(), anyMap()))
                .thenReturn(Map.of("success", true, "table", "EVENT_LOG", "columns", rows));

        Map<?, ?> result = probe();

        assertThat(result.get("success")).isEqualTo(true);
        List<?> columns = (List<?>) result.get("columns");
        assertThat(columns).hasSize(82);
        Map<?, ?> first = (Map<?, ?>) columns.getFirst();
        assertThat(first.get("columnName")).isEqualTo("field_0");
        assertThat(first.get("columnComment")).isEqualTo("字段 0");
        assertThat(first.get("dataType")).isEqualTo("decimal(18,4)");
        assertThat(first.get("columnSize")).isEqualTo(18);
        assertThat(first.get("decimalDigits")).isEqualTo(4);
        assertThat(first.get("primaryKey")).isNull();
        assertThat(first.get("nullable")).isNull();
        assertThat(((Map<?, ?>) columns.getLast()).get("columnName")).isEqualTo("field_81");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> context = ArgumentCaptor.forClass(Map.class);
        verify(magic).call(eq("POST"), eq("/dst/database/metadata/huaweiMrsHiveJdbcDebug"), context.capture());
        assertThat((Map<String, Object>) context.getValue().get("body"))
                .containsEntry("action", "columns").containsEntry("contract", "gateway")
                .containsEntry("database", "analytics").containsEntry("table", "EVENT_LOG");
    }

    @Test
    void readsUppercaseLabelsAndDoesNotDuplicatePartitionColumns() {
        when(magic.call(eq("POST"), anyString(), anyMap())).thenReturn(Map.of("success", true, "columns", List.of(
                Map.of("COL_NAME", "event_id", "DATA_TYPE", "bigint", "COMMENT", "事件编号"),
                Map.of("COL_NAME", "dt", "DATA_TYPE", "string"),
                Map.of("COL_NAME", "# Partition Information", "DATA_TYPE", ""),
                Map.of("COL_NAME", "dt", "DATA_TYPE", "string"))));
        assertThat((List<?>) probe().get("columns")).hasSize(2);
    }

    @Test
    void preservesCanonicalMetadataWhenTheGatewayAlreadyNormalizesColumns() {
        when(magic.call(eq("POST"), anyString(), anyMap())).thenReturn(Map.of("success", true, "columns", List.of(
                Map.of("columnName", "id", "dataType", "bigint", "primaryKey", true, "nullable", false))));
        Map<?, ?> column = (Map<?, ?>) ((List<?>) probe().get("columns")).getFirst();
        assertThat(column.get("columnName")).isEqualTo("id");
        assertThat(column.get("primaryKey")).isEqualTo(true);
        assertThat(column.get("nullable")).isEqualTo(false);
    }

    @Test
    void failsClearlyInsteadOfClaimingUnnamedRowsAreFields() {
        when(magic.call(eq("POST"), anyString(), anyMap())).thenReturn(Map.of("success", true, "columns", List.of(
                Map.of("unknown", "id", "data_type", "bigint"))));
        Map<?, ?> result = probe();
        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("error").toString()).contains("缺少字段名");
    }

    @Test
    void retainsGatewayFailureAndRejectsEmptyMetadata() {
        when(magic.call(eq("POST"), anyString(), anyMap())).thenReturn(Map.of("success", false, "error", "Hive unavailable"));
        assertThat(probe().get("error")).isEqualTo("Hive unavailable");
        when(magic.call(eq("POST"), anyString(), anyMap())).thenReturn(Map.of("success", true, "columns", List.of()));
        assertThat(probe().get("success")).isEqualTo(false);
    }

    @Test
    void turnsHiddenHiveClientConfigurationFailureIntoAnActionableCanvasMessage() {
        when(magic.call(eq("POST"), anyString(), anyMap())).thenReturn(Map.of(
                "code", 500, "success", false,
                "error", Map.of("message", "接口脚本执行失败",
                        "detail", "Hive conf file not found: hiveclient.properties")));

        Map<?, ?> result = probe();

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("error").toString()).contains("MRS 客户端配置缺失").contains("hiveclient.properties");
        assertThat(result.get("errorCode")).isEqualTo(500);
    }

    private Map<?, ?> probe() {
        return (Map<?, ?>) controller.columnsProbe(Map.of("manifestKey", "sink.hive", "table", "EVENT_LOG",
                "config", Map.of("hiveProfile", "default", "database", "analytics"))).getBody();
    }

    @Test
    void keepsTheActualGatewayErrorWhenTablesProbeReceivesDataNull() {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("code", 403);
        envelope.put("msg", "当前租户无权访问数据源");
        envelope.put("data", null);
        envelope.put("traceId", "probe-trace");
        when(magic.call(eq("POST"), anyString(), anyMap())).thenReturn(envelope);
        Map<?, ?> result = probeTables();
        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("error")).isEqualTo("当前租户无权访问数据源");
        assertThat(result.get("errorCode")).isEqualTo(403);
        assertThat(result.get("traceId")).isEqualTo("probe-trace");
        assertThat((List<?>) result.get("tables")).isEmpty();
    }

    @Test
    void doesNotTreatDiagnosticDataInsideAFailedEnvelopeAsSuccess() {
        when(magic.call(eq("POST"), anyString(), anyMap())).thenReturn(Map.of(
                "code", 500, "success", false, "msg", "Hive 元数据调用失败",
                "data", Map.of("success", true, "tables", List.of())));
        assertThat(probeTables().get("error")).isEqualTo("Hive 元数据调用失败");
    }

    @Test
    void returnsExplicitFailureForANullGatewayResponse() {
        when(magic.call(eq("POST"), anyString(), anyMap())).thenReturn(null);
        Map<?, ?> result = probeTables();
        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("error").toString()).contains("未返回结果");
    }

    @Test
    void unwrapsNestedSuccessEnvelopesForTableSuggestions() {
        var table = Map.of("tableName", "EVENT_LOG", "schemaName", "analytics");
        when(magic.call(eq("POST"), anyString(), anyMap())).thenReturn(Map.of("code", 0, "data",
                Map.of("code", 200, "data", Map.of("success", true, "tables", List.of(table)))));
        Map<?, ?> result = probeTables();
        assertThat(result.get("success")).isEqualTo(true);
        assertThat((List<?>) result.get("tables")).hasSize(1);
    }

    private Map<?, ?> probeTables() {
        return (Map<?, ?>) controller.tablesProbe(Map.of("manifestKey", "sink.hive", "keyword", "EVENT",
                "config", Map.of("hiveProfile", "default", "database", "analytics"))).getBody();
    }

    @Test void normalizesRawHiveTableNamesAndFiltersBeforeLimiting() {
        when(magic.call(eq("POST"), anyString(), anyMap())).thenReturn(Map.of("success", true,
                "tables", List.of("OTHER", "EVENT_Z", "EVENT_A", "EVENT_A")));
        Map<?, ?> result = probeTables();
        assertThat((List<?>) result.get("tables")).hasSize(2);
        assertThat(((Map<?, ?>) ((List<?>) result.get("tables")).getFirst()).get("tableName")).isEqualTo("EVENT_A");
    }

    @Test void supportsLegacyJdbcHiveSinkAndEmptyTables() {
        when(magic.call(eq("POST"), anyString(), anyMap())).thenReturn(Map.of("success", true, "tables", List.of()));
        var result = (Map<?, ?>) controller.tablesProbe(Map.of("manifestKey", "sink.jdbc", "config",
                Map.of("dbType", "HIVE", "hiveProfile", "default", "database", "analytics"))).getBody();
        assertThat(result.get("success")).isEqualTo(true);
        assertThat((List<?>) result.get("tables")).isEmpty();
    }

    @Test void resolvesOceanBaseOracleModeAndOwnerWithoutRequiringDriverSchemaSupport() throws Exception {
        assertThat(ConnectionTestController.oceanBaseType(Map.of("compatibleMode", "ORACLE"))).isEqualTo("OCEANBASE_ORACLE");
        assertThat(ConnectionTestController.oceanBaseType(Map.of("jdbcUrl", "jdbc:oceanbase:oracle://host/db"))).isEqualTo("OCEANBASE_ORACLE");
        var connection = mock(java.sql.Connection.class);
        when(connection.getSchema()).thenThrow(new java.sql.SQLFeatureNotSupportedException());
        assertThat(ConnectionTestController.metadataSchema(connection,
                Map.of("compatibleMode", "ORACLE", "username", "APP@tenant#cluster"), "source.oceanbase")).isEqualTo("APP");
        assertThat(ConnectionTestController.metadataSchema(connection,
                Map.of("schema", "BUSINESS", "username", "APP"), "source.oceanbase")).isEqualTo("BUSINESS");
    }

    @Test void fallsBackWhenLegacyOceanBaseOrHiveDriverThrowsAbstractMethodErrorForSchema() throws Exception {
        var connection = mock(java.sql.Connection.class);
        when(connection.getSchema()).thenThrow(new AbstractMethodError("Unimplemented method: getSchema()"));

        assertThat(ConnectionTestController.metadataSchema(connection,
                Map.of("compatibleMode", "ORACLE", "username", "APP@tenant#cluster"), "source.oceanbase"))
                .isEqualTo("APP");
        assertThat(ConnectionTestController.metadataSchema(connection,
                Map.of("database", "analytics"), "source.hive"))
                .isEqualTo("analytics");
        verify(connection, times(1)).getSchema();
    }

    @Test void takesRegisteredHiveConnectionModeFromTheActiveTenant() {
        when(registered.resolve("tenant-a", "hive-id")).thenReturn(Map.of("dbType", "hive",
                "metadataAccessMode", "server-managed-mrs", "database", "analytics"));
        when(magic.call(eq("POST"), anyString(), anyMap())).thenReturn(Map.of("success", true, "columns",
                List.of(Map.of("col_name", "id", "data_type", "bigint"))));
        try (var ignored = com.linewell.dataelement.platform.tenant.domain.TenantContext.use("tenant-a")) {
            var result = (Map<?, ?>) controller.columnsProbe(Map.of("manifestKey", "source.hive", "table", "`analytics`.`events`",
                    "config", Map.of("registeredDatasourceId", "hive-id", "database", "wrong-browser-db"))).getBody();
            assertThat(result.get("success")).isEqualTo(true);
        }
        verify(magic).call(eq("POST"), anyString(), argThat(context -> {
            Map<?, ?> body = (Map<?, ?>) context.get("body");
            return "analytics".equals(body.get("database")) && "events".equals(body.get("table"));
        }));
    }

    @Test void rejectsHiveReferencesOutsideTheSelectedTenant() {
        when(registered.resolve("tenant-b", "hive-id")).thenReturn(Map.of());
        try (var ignored = com.linewell.dataelement.platform.tenant.domain.TenantContext.use("tenant-b")) {
            var result = (Map<?, ?>) controller.tablesProbe(Map.of("manifestKey", "sink.hive", "config",
                    Map.of("registeredDatasourceId", "hive-id", "hiveProfile", "default"))).getBody();
            assertThat(result.get("success")).isEqualTo(false);
            assertThat(result.get("error").toString()).contains("当前租户");
        }
        verifyNoInteractions(magic);
    }
}
