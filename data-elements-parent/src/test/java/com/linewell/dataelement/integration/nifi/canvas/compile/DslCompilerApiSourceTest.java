package com.linewell.dataelement.integration.nifi.canvas.compile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ComponentManifest;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry;
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.model.nifi.NifiEntity;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DslCompilerApiSourceTest {

    @Test
    void apiSourceUsesTheNifi2InvokeHttpPropertyNames() throws Exception {
        NifiClient nifi = mock(NifiClient.class);
        ManifestRegistry registry = mock(ManifestRegistry.class);
        ComponentManifest manifest;
        try (var stream = getClass().getResourceAsStream("/manifests/sources/api.json")) {
            manifest = new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
        when(registry.get("source.api")).thenReturn(manifest);
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        when(nifi.listProcessGroups("root")).thenReturn(List.of());
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble()))
                .thenReturn(entity("pg"));
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(entity("controller-service"));
        when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(),
                anyMap(), nullable(String.class), nullable(String.class))).thenReturn(entity("processor"));

        Pipeline pipeline = new Pipeline("task", "API 拉取任务", null, null, null,
                new Pipeline.Dsl(1, List.of(new Pipeline.Node("source", "source.api", "接口来源",
                        "source", 0, 0, Map.of(
                                "url", "https://example.test/api/list",
                                "method", "POST",
                                "connectTimeout", "10 sec",
                                "readTimeout", "30 sec"))), List.of()),
                null, null, null, null, null, null, null);

        new DslCompiler(nifi, registry, mock(FieldMappingService.class), mock(HiveModule.class)).compile(pipeline);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createProcessor(eq("pg"), eq("org.apache.nifi.processors.standard.InvokeHTTP"),
                eq("接口来源/调用接口"), anyDouble(), anyDouble(), properties.capture(),
                nullable(String.class), nullable(String.class));
        assertThat(properties.getValue())
                .containsEntry("HTTP URL", "https://example.test/api/list")
                .containsEntry("Socket Read Timeout", "30 sec")
                .containsEntry("Response Generation Required", "true")
                .doesNotContainKeys("Remote URL", "Read Timeout", "Always Output Response");
    }

    @Test
    void apiSourceReplacesOnlyLoopbackWithTheConfiguredNifiReachableAddress() throws Exception {
        NifiClient nifi = mock(NifiClient.class);
        ManifestRegistry registry = mock(ManifestRegistry.class);
        ComponentManifest manifest;
        try (var stream = getClass().getResourceAsStream("/manifests/sources/api.json")) {
            manifest = new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
        when(registry.get("source.api")).thenReturn(manifest);
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        when(nifi.listProcessGroups("root")).thenReturn(List.of());
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble())).thenReturn(entity("pg"));
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(entity("controller-service"));
        when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(),
                anyMap(), nullable(String.class), nullable(String.class))).thenReturn(entity("processor"));

        Pipeline pipeline = new Pipeline("task", "API 拉取任务", null, null, null,
                new Pipeline.Dsl(1, List.of(new Pipeline.Node("source", "source.api", "接口来源", "source", 0, 0,
                        Map.of("url", "http://localhost:8088/dst/application/page"))), List.of()),
                null, null, null, null, null, null, null);

        new DslCompiler(nifi, registry, mock(FieldMappingService.class), mock(HiveModule.class),
                "http://192.168.3.44:8088").compile(pipeline);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createProcessor(eq("pg"), eq("org.apache.nifi.processors.standard.InvokeHTTP"),
                eq("接口来源/调用接口"), anyDouble(), anyDouble(), properties.capture(),
                nullable(String.class), nullable(String.class));
        assertThat(properties.getValue()).containsEntry("HTTP URL", "http://192.168.3.44:8088/dst/application/page");
    }

    private NifiEntity entity(String id) {
        return new NifiEntity(null, Map.of("id", id), null, null);
    }
}
