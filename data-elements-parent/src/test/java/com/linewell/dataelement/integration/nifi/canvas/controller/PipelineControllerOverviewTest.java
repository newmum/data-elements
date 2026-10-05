package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PipelineControllerOverviewTest {
    @Test
    void overviewCountsAllFlowsButReturnsFiveWithoutCallingNifi() {
        PipelineRepository repository = mock(PipelineRepository.class);
        NifiClient nifi = mock(NifiClient.class);
        when(repository.findAll()).thenReturn(List.of(
                pipeline("newest", PipelineStatus.RUNNING),
                pipeline("second", PipelineStatus.DEPLOY_FAILED),
                pipeline("third", PipelineStatus.SAVED),
                pipeline("fourth", PipelineStatus.RUN_ERROR),
                pipeline("fifth", PipelineStatus.STOPPED),
                pipeline("sixth", PipelineStatus.RUNNING),
                pipeline("oldest", PipelineStatus.DRAFT)
        ));
        PipelineController controller = new PipelineController(repository, nifi,
                null, null, null, null, null, null, null);

        Map<String, Object> overview = controller.runtimeOverview();

        assertThat(overview.get("total")).isEqualTo(7);
        assertThat(overview.get("running")).isEqualTo(2L);
        assertThat(overview.get("failed")).isEqualTo(2L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> flows = (List<Map<String, Object>>) overview.get("flows");
        assertThat(flows).extracting(row -> row.get("id"))
                .containsExactly("newest", "second", "third", "fourth", "fifth");
        verifyNoInteractions(nifi);
    }

    private static Pipeline pipeline(String id, PipelineStatus status) {
        return new Pipeline(id, id, null, 1L, 2L, null,
                "group-" + id, status, null, null, null, null, null);
    }
}
