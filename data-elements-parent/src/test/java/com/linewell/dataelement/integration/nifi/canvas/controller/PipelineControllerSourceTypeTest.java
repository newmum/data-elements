package com.linewell.dataelement.integration.nifi.canvas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.dataassets.runtime.DataAssetRuntimeAdapter;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.ssssssss.magicapi.core.service.MagicAPIService;

class PipelineControllerSourceTypeTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "person")
    void bothApiTemplateRoutesPassOnlyTheAuthorizedSourceProjectionToMagic(String tableName) {
        String tenant = "validation-tenant";
        String tableId = "validation-table";
        String sourceId = "validation-source";
        String catalogId = "validation-catalog";
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DataAssetRuntimeAdapter assets = mock(DataAssetRuntimeAdapter.class);
        MagicAPIService magic = mock(MagicAPIService.class);
        PipelineRepository repository = mock(PipelineRepository.class);
        NifiClient nifi = mock(NifiClient.class);
        var controller = new PipelineController(repository, nifi, magic, assets, jdbc, null, null, null, null);

        Map<String, Object> table = new LinkedHashMap<>();
        table.put("tid", tableId);
        table.put("datasourceId", sourceId);
        table.put("tableName", tableName);
        when(jdbc.queryForList(contains("from db_table_t"), eq(tenant), eq(tableId), eq(tenant)))
                .thenReturn(List.of(table));
        when(assets.queryById(catalogId)).thenReturn(Map.of("sourceTableId", tableId, "dbId", sourceId,
                "sourceTable", "catalog_table", "targetTable", "ods_table",
                "targetDatasource", Map.of("dbType", "mysql", "jdbcUrl", "jdbc:mysql://validation/ods")));
        var definitions = List.of(Map.of("tableName", "person", "url", "https://validation.invalid/data"));
        Map<String, Object> source = Map.of("tid", sourceId, "dbType", "api", "apiPullItems", definitions,
                "password", "validation-ignored-password", "authToken", "validation-ignored-token",
                "jdbcUrl", "jdbc:mysql://validation/source", "contentType", "legacy/ignored");
        when(magic.execute(eq("POST"), eq("/dst/database/detail"), eq(Map.of("tid", sourceId))))
                .thenReturn(Map.of("data", source));
        var columns = List.of(Map.of("columnName", "person_id"));
        when(magic.execute(eq("GET"), eq("/dst/database/metadata/columns"), eq(Map.of("tid", tableId))))
                .thenReturn(Map.of("data", columns));
        when(magic.invoke(eq("/common/nifiApiTemplateConfig"), anyMap())).thenAnswer(call -> {
            Map<String, Object> parameters = call.getArgument(1);
            assertThat(parameters).containsOnlyKeys("datasource", "tableName")
                    .containsEntry("datasource", Map.of("apiPullItems", definitions))
                    .containsEntry("tableName", tableName == null || tableName.isEmpty() ? null : tableName);
            return Map.of("url", "https://validation.invalid/data", "batchSchedulePolicy", "DAILY_IMMEDIATE");
        });

        try (var ignored = TenantContext.use(tenant)) {
            var node = controller.templateNode(new PipelineController.PipelineTemplateNodeRequest(
                    "source.api", tableId, null, null));
            var flow = controller.template(new PipelineController.PipelineTemplateRequest(catalogId));
            assertThat(node.getStatusCode().value()).isEqualTo(200);
            assertThat(flow.getStatusCode().value()).isEqualTo(200);
            assertThat(node.getBody().manifestKey()).isEqualTo("source.api");
            assertThat(node.getBody().config()).containsEntry("sourceDbId", sourceId)
                    .containsEntry("sourceTableId", tableId).containsEntry("sourceColumns", columns)
                    .containsEntry("url", "https://validation.invalid/data").doesNotContainKeys("password", "authToken");
            var flowSource = flow.getBody().dsl().nodes().getFirst();
            assertThat(flowSource.manifestKey()).isEqualTo("source.api");
            assertThat(flowSource.config()).containsEntry("sourceDbId", sourceId).containsEntry("sourceTableId", tableId)
                    .containsEntry("sourceColumns", columns).containsEntry("table", "catalog_table")
                    .containsEntry("url", "https://validation.invalid/data").doesNotContainKeys("password", "authToken");
        }

        verify(jdbc, times(2)).queryForList(contains("from db_table_t"), eq(tenant), eq(tableId), eq(tenant));
        verify(magic, times(2)).invoke(eq("/common/nifiApiTemplateConfig"), anyMap());
        verifyNoInteractions(repository, nifi);
    }

    @Test
    void registeredKingbaseAliasUsesKingbaseAndUnknownTypesFail() {
        assertThat(PipelineController.normalizeSourceNodeType("kingbase8", Map.of())).isEqualTo("source.kingbase");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> PipelineController.normalizeSourceNodeType("unknown-vendor", Map.of()));
    }

    @Test
    void keepsOceanBaseOracleOnTheOceanBaseSourceManifest() {
        assertThat(PipelineController.normalizeSourceNodeType("oceanbaseoracle", Map.of()))
                .isEqualTo("source.oceanbase");
        assertThat(PipelineController.normalizeSourceNodeType("OCEANBASE_ORACLE", Map.of()))
                .isEqualTo("source.oceanbase");
    }

    @Test
    void keepsNormalOracleOnTheOracleSourceManifest() {
        assertThat(PipelineController.normalizeSourceNodeType("oracle", Map.of()))
                .isEqualTo("source.oracle");
    }

    @Test
    void normalizesRegisteredOracleEndpointModesWithoutGuessing() {
        assertThat(PipelineController.normalizeOracleConnectionType("sid")).isEqualTo("SID");
        assertThat(PipelineController.normalizeOracleConnectionType("serviceName"))
                .isEqualTo("SERVICE_NAME");
        assertThat(PipelineController.normalizeOracleConnectionType("other-mode")).isNull();
    }

    @Test
    void usesOnlyTheExplicitlyRegisteredTimestampAsTheIncrementalColumn() {
        Map<String, Object> governance = Map.of(
                "timestampField", "UPDATE_TIME",
                "fields", List.of(Map.of("columnName", "UPDATE_TIME", "isTimestampField", true)));
        List<Map<String, Object>> columns = List.of(
                Map.of("columnName", "ID", "primaryKey", true),
                Map.of("columnName", "UPDATE_TIME", "dataType", "TIMESTAMP"));

        assertThat(PipelineController.registeredTimestampColumn(governance, columns))
                .isEqualTo("UPDATE_TIME");
    }

    @Test
    void leavesIncrementalColumnBlankWhenRegistrationDidNotSelectATimestamp() {
        Map<String, Object> governance = Map.of(
                "fields", List.of(Map.of("columnName", "CREATE_TIME", "timeRoles", List.of("business"))));
        List<Map<String, Object>> columns = List.of(
                Map.of("columnName", "ID", "primaryKey", true),
                Map.of("columnName", "CREATE_TIME", "dataType", "TIMESTAMP"));

        assertThat(PipelineController.registeredTimestampColumn(governance, columns)).isNull();
    }
}
