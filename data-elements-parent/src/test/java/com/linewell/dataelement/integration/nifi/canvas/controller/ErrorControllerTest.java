package com.linewell.dataelement.integration.nifi.canvas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.errors.ErrorService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ErrorControllerTest {
    @Test
    void listAndClearRejectAnUnavailablePipelineBeforeReadingOrDeletingHistory() {
        PipelineRepository pipelines = mock(PipelineRepository.class);
        ErrorService errors = mock(ErrorService.class);
        var controller = new ErrorController(errors, pipelines, mock(NifiClient.class));
        when(pipelines.findById("foreign")).thenReturn(Optional.empty());
        assertThat(controller.list("foreign").getStatusCode().value()).isEqualTo(404);
        assertThat(controller.clear("foreign").getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(errors);
    }

    @Test
    void twentyAndOneHundredErrorsUseOneAuthorizedPipelineRead() {
        for (int size : List.of(20, 100)) {
            PipelineRepository pipelines = mock(PipelineRepository.class);
            ErrorService errors = mock(ErrorService.class);
            when(pipelines.findById("p")).thenReturn(Optional.of(new Pipeline("p", "flow", null, null, null, null, null,
                    PipelineStatus.SAVED, null, null, null, null, null)));
            when(errors.list("p")).thenReturn(java.util.Collections.nCopies(size, null));
            assertThat(new ErrorController(errors, pipelines, mock(NifiClient.class)).list("p").getBody()).hasSize(size);
            verify(pipelines).findById("p");
            verify(errors).list("p");
        }
    }

    @Test
    void liveBulletinsReadNiFiWithoutMutatingStoredErrorHistory() throws Exception {
        PipelineRepository pipelines = mock(PipelineRepository.class);
        NifiClient nifi = mock(NifiClient.class);
        ErrorService errors = mock(ErrorService.class);
        when(pipelines.findById("flow-1")).thenReturn(Optional.of(new Pipeline(
                "flow-1", "flow", null, null, null, null, "pg-1",
                PipelineStatus.RUNNING, null, null, null, null, null)));
        when(nifi.getBulletinBoard("pg-1", 0L)).thenReturn(new ObjectMapper().readTree("""
                {"bulletinBoard":{"bulletins":[
                  {"id":12,"bulletin":{"level":"INFO","message":"routine"}},
                  {"id":13,"bulletin":{"level":"ERROR","sourceName":"source",
                    "message":"failed","timestamp":"2026-10-02T00:00:00Z"}}
                ]}}
                """));

        var response = new ErrorController(errors, pipelines, nifi).liveBulletins("flow-1");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) response.getBody();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst()).containsEntry("level", "ERROR").containsEntry("message", "failed");
        verifyNoInteractions(errors);
    }
}
