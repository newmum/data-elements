package com.linewell.dataelement.integration.nifi.canvas.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PipelineControllerSourceTypeTest {

    @Test
    void repairsMissingRegisteredSftpEndpointWithoutOverwritingManualSettings() {
        Map<String, Object> config = PipelineController.repairFileSourceConfig(
                Map.of("hostname", "registered-host", "port", 22, "username", "registered-user",
                        "password", "test-only", "remotePath", "/registered", "table", "example"),
                Map.of("remotePath", "/manual", "jdbcUrl", "invalid-jdbc", "hostname", ""));
        assertThat(config).containsEntry("hostname", "registered-host")
                .containsEntry("remotePath", "/manual")
                .containsEntry("port", 22)
                .containsEntry("fileFilterRegex", "(?i)^\\Qexample\\E\\.(csv|json|xlsx|xls)$")
                .doesNotContainKey("jdbcUrl");
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
