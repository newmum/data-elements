package com.linewell.dataelement.integration.nifi.canvas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
