package com.linewell.dataelement.integration.nifi.canvas.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineRuntimeStatusResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private NifiClient nifiClient;

    @Test
    void keepsLocalStatusWhenRuntimePayloadIsMissing() {
        PipelineRuntimeStatusResolver resolver = new PipelineRuntimeStatusResolver(nifiClient);
        assertThat(resolver.resolve(PipelineStatus.STOPPED, null, "pg-1"))
                .isEqualTo(PipelineStatus.STOPPED);
    }

    @Test
    void treatsActiveThreadsAsRunning() throws Exception {
        JsonNode status = objectMapper.readTree("""
                {"processGroupStatus":{"aggregateSnapshot":{"activeThreadCount":"3 / 10"}}}
                """);
        PipelineRuntimeStatusResolver resolver = new PipelineRuntimeStatusResolver(nifiClient);
        assertThat(resolver.resolve(PipelineStatus.STOPPED, status, null))
                .isEqualTo(PipelineStatus.RUNNING);
    }

    @Test
    void processorStateWinsWhenNiFiReportsRunningProcessor() throws Exception {
        JsonNode status = objectMapper.readTree("""
                {"processGroupStatus":{"aggregateSnapshot":{"activeThreadCount":0}}}
                """);
        JsonNode flow = objectMapper.readTree("""
                {"processGroupFlow":{"flow":{"processors":[{"component":{"state":"RUNNING"}}]}}}
                """);
        when(nifiClient.get(anyString(), eq(JsonNode.class))).thenReturn(flow);

        PipelineRuntimeStatusResolver resolver = new PipelineRuntimeStatusResolver(nifiClient);
        assertThat(resolver.resolve(PipelineStatus.STOPPED, status, "pg-1"))
                .isEqualTo(PipelineStatus.RUNNING);
    }

    @Test
    void parsesNiFiCountVariants() throws Exception {
        assertThat(PipelineRuntimeStatusResolver.parseCount(objectMapper.readTree("12"))).isEqualTo(12);
        assertThat(PipelineRuntimeStatusResolver.parseCount(objectMapper.readTree("\"4 / 9\""))).isEqualTo(4);
        assertThat(PipelineRuntimeStatusResolver.parseCount(objectMapper.readTree("\"unknown\""))).isZero();
    }
}
