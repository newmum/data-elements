package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiAttributeSync;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import com.linewell.dataelement.integration.nifi.canvas.runtime.TaskRuntimeStatusService;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

class NifiAssignedAttributeMagicTest {
    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void previewAndSaveUseEachTaskAssignedNodeAndReleaseIt(int count) throws Exception {
        var nifi = mock(NifiClient.class);
        var scope = mock(NifiClient.RuntimeScope.class);
        var runtimeStatus = mock(TaskRuntimeStatusService.class);
        var json = new ObjectMapper();
        var processors = json.createObjectNode();
        processors.putArray("processors");
        var mapping = new NifiNodeMapping(Map.of(), Map.of(), Map.of()).withRuntimeNode("secondary");
        var pipeline = new Pipeline("validation", "validation", null, 1L, 1L,
                new Pipeline.Dsl(1, List.of(), List.of()), "selected-group", PipelineStatus.STOPPED,
                "hash", 1L, null, mapping, 0L);
        when(nifi.bindAssignedNode("secondary")).thenReturn(scope);
        when(runtimeStatus.resolveLegacyOwner(pipeline)).thenReturn("secondary");
        when(nifi.get("/process-groups/selected-group/processors?includeDescendantGroups=true", JsonNode.class))
                .thenReturn(processors);
        var bindings = new LinkedHashMap<String, Object>();
        bindings.put("nifi", nifi);
        bindings.put("runtimeStatus", runtimeStatus);
        bindings.put("NifiAttributeSync", NifiAttributeSync.class);
        bindings.put("TenantContext", TenantContext.class);
        bindings.put("ResponseEntity", ResponseEntity.class);
        bindings.put("tenantRuntime", new Scope());
        bindings.put("findCanvas", (MagicScriptLambdaFunction) (context, args) -> pipeline);
        bindings.put("respond", (MagicScriptLambdaFunction) (context, args) ->
                MagicScript.create("return action(null)", null).execute(new MagicScriptContext(Map.of("action", args[0]))));
        bindings.put("param", Map.of("id", "validation"));
        try (var tenant = TenantContext.use("police")) {
            var preview = run("nifi_attribute_sync_get_v1", bindings);
            assertThat(((ResponseEntity<?>) preview).getBody()).isInstanceOf(NifiAttributeSync.Preview.class);
            var selected = java.util.stream.IntStream.range(0, count).mapToObj(i ->
                    new NifiAttributeSync.Selection("source-" + i, "schedulingPeriod", "processor-" + i,
                            "30 sec", "60 sec", false)).toList();
            bindings.put("readBody", (MagicScriptLambdaFunction) (context, args) ->
                    new NifiAttributeSync.Request("selected-group", selected));
            bindings.put("mutation", new Mutation());
            bindings.put("body", Map.of());
            bindings.put("AttributeRequest", NifiAttributeSync.Request.class);
            var saved = run("nifi_attribute_sync_post_v1", bindings);
            assertThat(saved).isInstanceOf(ResponseEntity.class);
        }
        var order = inOrder(nifi, scope);
        order.verify(nifi).bindAssignedNode("secondary");
        order.verify(nifi).get(any(String.class), eq(JsonNode.class));
        order.verify(scope).close();
        order.verify(nifi).bindAssignedNode("secondary");
        order.verify(nifi).get(any(String.class), eq(JsonNode.class));
        order.verify(scope).close();
        verify(nifi, never()).bindRuntimeNode();
        verify(nifi, times(2)).get(any(String.class), eq(JsonNode.class));
        verify(runtimeStatus, times(2)).resolveLegacyOwner(pipeline);
    }

    private Object run(String id, Map<String, Object> bindings) throws Exception {
        String source = CanonicalMagicSources.byId(id).replaceAll("(?m)^import [^\\r\\n]*\\R", "");
        return MagicScript.create(source, null).execute(new MagicScriptContext(bindings));
    }

    public static class Scope { public String id() { return "police"; } }

    public static class Mutation {
        public Object withMutationLock(org.apache.commons.lang3.function.FailableSupplier<Object, Throwable> action) throws Throwable {
            return action.get();
        }
    }
}
