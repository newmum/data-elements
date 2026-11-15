package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler;
import com.linewell.dataelement.integration.nifi.canvas.errors.ErrorService;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineGroupOrganizer;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineTaskLifecycleSynchronizer;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.TargetTableCleanupService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PipelineIncrementalConfigurationTest {
    private final PipelineRepository repository = mock(PipelineRepository.class);
    private final PipelineTaskLifecycleSynchronizer tasks = mock(PipelineTaskLifecycleSynchronizer.class);
    private final PipelineGroupOrganizer groups = mock(PipelineGroupOrganizer.class);
    private final NifiDeploymentMagicFixture deployment = new NifiDeploymentMagicFixture(repository, mock(DslCompiler.class),
            mock(NifiClient.class), mock(ErrorService.class), tasks, groups, mock(TargetTableCleanupService.class));

    private Pipeline design(String column, String mode) {
        var source = new Pipeline.Node("source", "source.oracle", "来源", "source", 0, 0,
                Map.of("sourceTableId", "registered", "incrementalColumn", column, "syncMode", mode));
        return new Pipeline("test", "test", null, null, null,
                new Pipeline.Dsl(1, List.of(source), List.of()), null, null, null, null, null, null, null);
    }
    private org.springframework.http.ResponseEntity<?> deploy(Pipeline design) {
        when(repository.findById(design.id())).thenReturn(Optional.of(design));
        when(groups.prepareCandidate(design)).thenThrow(new IllegalStateException("validation complete"));
        return deployment.deploy(design.id(), Map.of("preserveExisting", true), false);
    }
    @Test void missingHistoricalFieldRequiresManualConfigurationAndIsNotBackfilled() {
        var original = design("", "FULL_THEN_INCR");
        assertThat(deploy(original).getBody().toString()).contains("请手工填写");
        verifyNoInteractions(tasks);
        assertThat(original.dsl().nodes().getFirst().config()).containsEntry("incrementalColumn", "");
    }
    @Test void savedManualFieldIsKeptExactlyWithoutRegistrationReads() {
        var original = design("MANUAL_VERSION", "FULL_THEN_INCR");
        assertThat(deploy(original).getBody().toString()).contains("validation complete");
        verify(tasks).ensureCatalogAccessTask(null, original);
        assertThat(original.dsl().nodes().getFirst().config()).containsEntry("incrementalColumn", "MANUAL_VERSION");
    }
    @Test void fullModesAllowAnEmptyIncrementalField() {
        for (String mode : List.of("FULL", "PERIODIC_FULL")) {
            var original = design("", mode);
            assertThat(deploy(original).getBody().toString()).contains("validation complete");
        }
    }
}
