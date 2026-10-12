package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PipelineIncrementalConfigurationTest {
    private Pipeline design(String column, String mode) {
        var source = new Pipeline.Node("source", "source.oracle", "来源", "source", 0, 0,
                Map.of("sourceTableId", "registered", "incrementalColumn", column, "syncMode", mode));
        return new Pipeline("test", "test", null, null, null,
                new Pipeline.Dsl(1, List.of(source), List.of()), null, null, null, null, null, null, null);
    }
    @Test void missingHistoricalFieldRequiresManualConfigurationAndIsNotBackfilled() {
        var original = design("", "FULL_THEN_INCR");
        assertThatThrownBy(() -> PipelineController.validateIncrementalFields(original))
                .hasMessageContaining("请手工填写");
        assertThat(original.dsl().nodes().getFirst().config()).containsEntry("incrementalColumn", "");
    }
    @Test void savedManualFieldIsKeptExactlyWithoutRegistrationReads() {
        var original = design("MANUAL_VERSION", "FULL_THEN_INCR");
        assertThat(PipelineController.validateIncrementalFields(original)).isSameAs(original);
    }
    @Test void fullModesAllowAnEmptyIncrementalField() {
        for (String mode : List.of("FULL", "PERIODIC_FULL")) {
            var original = design("", mode);
            assertThat(PipelineController.validateIncrementalFields(original)).isSameAs(original);
        }
    }
}
