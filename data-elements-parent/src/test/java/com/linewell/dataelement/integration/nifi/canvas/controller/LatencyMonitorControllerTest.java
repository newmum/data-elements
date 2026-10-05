package com.linewell.dataelement.integration.nifi.canvas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessAggTaskTService;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessTaskMonitorSnapTService;
import com.linewell.dataelement.integration.nifi.canvas.monitor.LatencyMonitorSnapshot;
import com.linewell.dataelement.integration.nifi.canvas.monitor.LatencyMonitorSupport;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LatencyMonitorControllerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final PipelineRepository pipelines = mock(PipelineRepository.class);
    private final NifiClient nifi = mock(NifiClient.class);
    private final LatencyMonitorController controller = new LatencyMonitorController(
            mock(IDataAccessAggTaskTService.class),
            mock(IDataAccessTaskMonitorSnapTService.class), pipelines, nifi,
            new LatencyMonitorSupport());

    @Test
    void liveReadsCurrentProcessGroupStatusWithoutWaitingForSnapshotJob() throws Exception {
        when(pipelines.findById("flow-1")).thenReturn(Optional.of(pipeline()));
        when(nifi.getProcessGroupStatus("pg-1")).thenReturn(json.readTree("""
                {"processGroupStatus":{"aggregateSnapshot":{
                  "queuedCount":"2", "activeThreadCount":"0", "flowFilesIn":"7"
                }}}
                """));

        var response = controller.live("flow-1");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("pipelineId", "flow-1");
        LatencyMonitorSnapshot snapshot = (LatencyMonitorSnapshot) body.get("snapshot");
        assertThat(snapshot.delayLevel()).isEqualTo("BLOCKED");
        verify(nifi).getProcessGroupStatus("pg-1");
    }

    @Test
    void onDemandProvenanceQueryIsDeletedAfterReading() throws Exception {
        when(pipelines.findById("flow-1")).thenReturn(Optional.of(pipeline()));
        when(nifi.submitProvenance("pg-1", 5)).thenReturn(json.readTree("""
                {"provenance":{"id":"query-1"}}
                """));
        when(nifi.getProvenance("query-1")).thenReturn(json.readTree("""
                {"provenance":{"finished":true,"results":{"provenanceEvents":[]}}}
                """));

        var response = controller.cycles("flow-1", 5, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(nifi).deleteProvenance("query-1");
    }

    private Pipeline pipeline() {
        return new Pipeline("flow-1", "flow", null, null, null, null,
                "pg-1", PipelineStatus.RUNNING, null, null, null, null, null);
    }
}
