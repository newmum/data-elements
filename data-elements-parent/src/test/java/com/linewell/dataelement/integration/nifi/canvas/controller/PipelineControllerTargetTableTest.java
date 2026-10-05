package com.linewell.dataelement.integration.nifi.canvas.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PipelineControllerTargetTableTest {

    @Test
    void qualifiesAndNormalizesOracleTargetTableForNifiMetadataLookup() {
        assertThat(PipelineController.nifiTargetTableName(
                "Oracle", Map.of("schema", "ods_owner"), "sym_dict_t1"))
                .isEqualTo("ODS_OWNER.SYM_DICT_T1");
    }

    @Test
    void leavesMySqlTargetTableUnchanged() {
        assertThat(PipelineController.nifiTargetTableName(
                "MySQL", Map.of("schema", "ods_owner"), "sym_dict_t1"))
                .isEqualTo("sym_dict_t1");
    }

    @Test
    void emitsPositiveOdsIdAndBothOdsTimestampsForEveryTargetTemplate() {
        String mapping = PipelineController.buildMappingConfig(
                List.of(Map.of("columnName", "CASE_ID")),
                List.of(
                        Map.of("columnName", "CASE_ID"),
                        Map.of("columnName", "ODS_UUID"),
                        Map.of("columnName", "ODS_RKSJ"),
                        Map.of("columnName", "ODS_GXSJ")),
                "source-table",
                "target-table");

        assertThat(mapping)
                .contains("\"to\": \"/ODS_UUID\"")
                .contains("\"to\": \"/ODS_RKSJ\"")
                .contains("\"to\": \"/ODS_GXSJ\"")
                .contains("TIMESTAMPDIFF(SECOND")
                .doesNotContain("TIMESTAMPDIFF(MILLISECOND");
        assertThat(PipelineController.ODS_UUID_EXPRESSION).doesNotContain("MILLISECOND");
    }

}
