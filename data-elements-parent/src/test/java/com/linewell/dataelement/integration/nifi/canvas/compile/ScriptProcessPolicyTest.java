package com.linewell.dataelement.integration.nifi.canvas.compile;

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
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ScriptProcessPolicyTest {
    @ParameterizedTest
    @ValueSource(strings = {"python", "java", "shell"})
    void usesSeparateArgumentsAndPreservesSpacesQuotesAndSemicolons(String language) {
        String suffix = language.equals("java") ? ".jar" : language.equals("python") ? ".py" : ".sh";
        var properties = ScriptProcessPolicy.properties(node(language, Map.of("programPath", "/opt/My Scripts/process" + suffix,
                "arguments", List.of("a b", "x;y", "\"quoted\""))));
        assertThat(properties).containsEntry("Command Arguments Strategy", "Dynamic Property Arguments")
                .containsEntry("Ignore STDIN", "false").doesNotContainKey("Command Arguments");
        int offset = language.equals("shell") ? 1 : 2;
        assertThat(properties).containsEntry("command.argument." + offset, "a b")
                .containsEntry("command.argument." + (offset + 1), "x;y")
                .containsEntry("command.argument." + (offset + 2), "\"quoted\"");
        if (language.equals("java")) assertThat(properties).containsEntry("command.argument.0", "-jar");
    }

    @Test void rejectsIncompleteOrInvalidProgramsBeforeAnyNifiMutation() {
        for (Map<String, Object> config : List.<Map<String, Object>>of(Map.of(), Map.of("programPath", "-bad.py"),
                Map.of("programPath", "/tmp/file.sh"), Map.of("programPath", "/tmp/ok.py", "arguments", "one;two"),
                Map.of("programPath", "/tmp/ok.py", "arguments", List.of(1)),
                Map.of("programPath", "/tmp/ok.py", "arguments", List.of("${filename}")),
                Map.of("programPath", "/tmp/ok.py", "arguments", List.of("")),
                Map.of("programPath", "/tmp/ok.py", "arguments", IntStream.range(0, 65).mapToObj(i -> "arg").toList()))) {
            assertThatThrownBy(() -> ScriptProcessPolicy.properties(node("python", config)))
                    .isInstanceOf(DslCompiler.NodeConfigException.class);
        }
    }

    @ParameterizedTest @ValueSource(ints = {20, 100})
    void oneCapabilityReadForTwentyOrOneHundredNodes(int count) {
        NifiClient nifi = mock(NifiClient.class);
        when(nifi.findProcessorType(ScriptProcessPolicy.PROCESSOR_TYPE)).thenReturn(Optional.of(ScriptProcessPolicy.PROCESSOR_TYPE));
        var compiler = compiler(nifi, mock(ManifestRegistry.class));
        compiler.preflightSources(pipeline(IntStream.range(0, count)
                .mapToObj(i -> node("python", Map.of("programPath", "/tmp/process.py"))).toList()));
        verify(nifi, times(1)).findProcessorType(ScriptProcessPolicy.PROCESSOR_TYPE);
        verifyNoMoreInteractions(nifi);
    }

    @Test void missingExtensionIsReportedWithoutCreatingAProcessGroup() {
        NifiClient nifi = mock(NifiClient.class);
        when(nifi.findProcessorType(ScriptProcessPolicy.PROCESSOR_TYPE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> compiler(nifi, mock(ManifestRegistry.class)).compile(pipeline(List.of(node("python", Map.of("programPath", "/tmp/process.py"))))))
                .hasMessageContaining("ExecuteStreamCommand");
        verify(nifi).findProcessorType(ScriptProcessPolicy.PROCESSOR_TYPE);
        verify(nifi).bindRuntimeNode();
        verifyNoMoreInteractions(nifi);
    }

    @ParameterizedTest @ValueSource(strings = {"python", "java", "shell"})
    void compilerConnectsSuccessToDownstreamAndKeepsNonzeroOutputInAnErrorQueue(String language) throws Exception {
        NifiClient nifi = mock(NifiClient.class);
        ManifestRegistry registry = mock(ManifestRegistry.class);
        ComponentManifest manifest;
        try (var stream = getClass().getResourceAsStream("/manifests/transforms/" + language + ".json")) {
            manifest = new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
        when(registry.get(manifest.key())).thenReturn(manifest);
        when(nifi.findProcessorType(ScriptProcessPolicy.PROCESSOR_TYPE)).thenReturn(Optional.of(ScriptProcessPolicy.PROCESSOR_TYPE));
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        HiveServiceFixtures.stubGlobals(nifi);
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble())).thenReturn(entity("pg"));
        when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(), anyMap(), nullable(String.class), nullable(String.class))).thenReturn(entity("execute"));
        when(nifi.createRetentionFunnel(anyString(), anyDouble(), anyDouble())).thenReturn(entity("errors"));
        String suffix = language.equals("java") ? ".jar" : language.equals("python") ? ".py" : ".sh";
        var result = compiler(nifi, registry).compile(pipeline(List.of(node(language, Map.of("programPath", "/opt/process" + suffix)))));
        assertThat(result.nodes().get("node").outlets.get("default").relationship()).isEqualTo("output stream");
        verify(nifi).createConnection("pg", "execute", "PROCESSOR", "errors", "FUNNEL", List.of("nonzero status"));
    }

    private DslCompiler compiler(NifiClient nifi, ManifestRegistry registry) {
        return new DslCompiler(nifi, registry, mock(FieldMappingService.class), mock(HiveModule.class));
    }
    private Pipeline.Node node(String language, Map<String, Object> config) {
        return new Pipeline.Node("node", "transform." + language, language + " 加工", "transform", 0, 0, config);
    }
    private Pipeline pipeline(List<Pipeline.Node> nodes) {
        return new Pipeline("script-test", "脚本加工测试", null, null, null, new Pipeline.Dsl(1, nodes, List.of()), null, null, null, null, null, null, null);
    }
    private NifiEntity entity(String id) { return new NifiEntity(null, Map.of("id", id), null, null); }
}
