package com.linewell.dataelement.integration.nifi.canvas.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver.RuntimeNode;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.magic.module.TaskRuntimeMagicModule;
import com.linewell.dataelement.magic.runtime.result.MagicApiResultProvider;
import com.linewell.dataelement.platform.magic.CanonicalMagicSources;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskRuntimeStatusTest {
    @Test void multipleNodesUseIndependentKeysAndOfflineNodesDoNotHideHealthyTasks() throws Exception {
        var resolver = mock(NifiNodeRuntimeResolver.class);
        var nifi = mock(NifiClient.class);
        var first = new RuntimeNode("tenant-a", "a", "a", "http://a.invalid", "", "", false, "root");
        var second = new RuntimeNode("tenant-a", "b", "b", "http://b.invalid", "", "", false, "root");
        when(resolver.samplingConfigurations()).thenReturn(List.of(Map.of("tid", "a"), Map.of("tid", "b")));
        when(resolver.runtimeNode(anyMap())).thenAnswer(call ->
                "a".equals(((Map<?, ?>) call.getArgument(0)).get("tid")) ? first : second);
        when(nifi.getRuntimeRootStatus(first)).thenReturn(MAPPER.readTree(response("Stopped")));
        when(nifi.getRuntimeRootStatus(second)).thenReturn(MAPPER.readTree(response("Running")));
        var service = new TaskRuntimeStatusService(resolver, nifi);
        try (var tenant = TenantContext.use("tenant-a")) {
            var module = new TaskRuntimeMagicModule(service);
            for (int count : List.of(20, 100)) {
                var snapshot = module.snapshot();
                List<Map<String, Object>> rows = new ArrayList<>();
                for (int i = 0; i < count; i++) rows.add(new HashMap<>(Map.of(
                        "runtimeNodeId", i % 2 == 0 ? "a" : "b", "runtimeProcessGroupId", "child")));
                module.decorate(rows, snapshot);
                for (int i = 0; i < count; i++)
                    assertThat(rows.get(i)).containsEntry("taskStatus", i % 2 == 0 ? 0 : 1);
            }
            verify(nifi, times(1)).getRuntimeRootStatus(first);
            verify(nifi, times(1)).getRuntimeRootStatus(second);
            service.invalidateTenant();
            when(nifi.getRuntimeRootStatus(first)).thenThrow(new IllegalStateException("offline"));
            var partial = module.snapshot();
            assertThat(partial).containsEntry("partial", true);
            var healthy = new HashMap<String, Object>(Map.of("runtimeNodeId", "b", "runtimeProcessGroupId", "child"));
            var offline = new HashMap<String, Object>(Map.of("runtimeNodeId", "a", "runtimeProcessGroupId", "child"));
            var deleted = new HashMap<String, Object>(Map.of("runtimeNodeId", "foreign", "runtimeProcessGroupId", "child"));
            var legacy = new HashMap<String, Object>(Map.of("runtimeProcessGroupId", "child"));
            module.decorate(List.of(healthy, offline, deleted, legacy), partial);
            assertThat(healthy).containsEntry("runtimeState", "RUNNING");
            assertThat(offline).containsEntry("runtimeState", "UNKNOWN").containsEntry("running", false);
            assertThat(deleted).containsEntry("runtimeState", "UNKNOWN");
            assertThat(legacy).containsEntry("nifiNodeId", "a").containsEntry("runtimeLegacyNode", true);
        } finally { service.close(); }
        assertThatThrownBy(service::snapshotAll).isInstanceOf(com.linewell.dataelement.platform.tenant.domain.TenantAccessException.class);
    }
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static String response(String state) { return """
        {"processGroupStatus":{"aggregateSnapshot":{"id":"root","processGroupStatusSnapshots":[
        {"processGroupStatusSnapshot":{"id":"child","activeThreadCount":0,"processorStatusSnapshots":[
        {"processorStatusSnapshot":{"id":"p","runStatus":"%s"}}]}}]}}}
        """.formatted(state); }
    @Test void tenantWithoutNifiReceivesUnavailableStreamAndCanReconnectAfterConfiguration() throws Exception {
        var resolver = mock(NifiNodeRuntimeResolver.class);
        var nifi = mock(NifiClient.class);
        when(resolver.resolve()).thenThrow(new NifiNodeRuntimeResolver.NodeNotConfiguredException());
        var service = new TaskRuntimeStatusService(resolver, nifi);
        try (var tenant = TenantContext.use("tenant-new")) {
            var module = spy(new TaskRuntimeMagicModule(service));
            var mvc = MockMvcBuilders.standaloneSetup(new ScriptHandler(module)).build();
            var response = mvc.perform(head("/nifi/run/events").accept("text/event-stream"))
                    .andExpect(status().isOk()).andExpect(request().asyncNotStarted())
                    .andExpect(header().string("Cache-Control", "no-cache, no-transform"))
                    .andReturn().getResponse();
            assertThat(response.getContentAsByteArray()).isEmpty();
            verify(module, never()).eventStream();
            var result = mvc.perform(get("/nifi/run/events").accept("text/event-stream"))
                    .andExpect(status().isOk()).andExpect(request().asyncStarted())
                    .andExpect(header().string("X-Accel-Buffering", "no")).andReturn();
            assertThat(result.getRequest().getAttribute(MagicApiResultProvider.EVENT_STREAM)).isEqualTo(true);
            for (int retry = 0; retry < 100 && !result.getResponse().getContentAsString().contains("unavailable"); retry++) {
                Thread.sleep(10);
            }
            String event = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(event).contains("event:runtime", "\"fresh\":false", "尚未配置 NiFi 节点");
            verifyNoInteractions(nifi);
            var node = new RuntimeNode("tenant-new", "new-node", "new", "http://nifi.invalid", "", "", false, "root");
            doReturn(node).when(resolver).resolve();
            when(nifi.getRuntimeRootStatus(node)).thenReturn(MAPPER.readTree(response("Running")));
            assertThat(service.snapshot()).containsEntry("fresh", true);
        } finally {
            service.close();
        }
    }

    @RestController
    static class ScriptHandler {
        private final TaskRuntimeMagicModule module;
        private final MagicScript script;

        ScriptHandler(TaskRuntimeMagicModule module) throws Exception {
            this.module = module;
            String source = CanonicalMagicSources.byPath("api/09.海通流程接口/05.运行状态/05.订阅实时状态事件.ms",
                    "nifi_runtime_events_v1").replaceAll("(?m)^import (?:request|response|taskRuntime)\\R", "");
            script = MagicScript.create(source, null);
        }

        @RequestMapping(value = "/nifi/run/events", method = {RequestMethod.GET, RequestMethod.HEAD})
        public Object invoke(HttpServletRequest request, HttpServletResponse response) {
            Object result = script.execute(new MagicScriptContext(Map.of("request", new ScriptRequest(request),
                    "response", response, "taskRuntime", module)));
            return result instanceof org.springframework.http.ResponseEntity<?> ? result
                    : new MagicApiResultProvider().buildResult(null, 0, "success", result);
        }
    }

    public record ScriptRequest(HttpServletRequest servlet) {
        public HttpServletRequest get() { return servlet; }
    }
    @Test void idleScheduledProcessorIsRunningAndNestedStateIsIncluded() throws Exception {
        var index = RuntimeStatusIndex.parse(MAPPER.readTree(response("Running")));
        assertThat(index.groups().get("child").state()).isEqualTo("RUNNING");
        assertThat(index.groups().get("root").running()).isTrue();
        assertThat(index.rawStatus("child").path("processGroupStatus").path("aggregateSnapshot").path("id").asText()).isEqualTo("child");
        assertThat(index.revision()).isNotEqualTo(RuntimeStatusIndex.parse(MAPPER.readTree(response("Stopped"))).revision());
    }
    @Test void absentPermissionAndMalformedResponsesNeverBecomeStopped() throws Exception {
        var tree = MAPPER.readTree(response("Stopped"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)tree.at("/processGroupStatus/aggregateSnapshot/processGroupStatusSnapshots/0/processGroupStatusSnapshot/processorStatusSnapshots/0")).put("canRead", false);
        assertThat(RuntimeStatusIndex.parse(tree).groups().get("child").state()).isEqualTo("UNKNOWN");
        assertThatThrownBy(() -> RuntimeStatusIndex.parse(MAPPER.readTree("{}"))).isInstanceOf(IllegalStateException.class);
    }
    @Test void largeTreeIsIndexedOnceAndMetricsAloneDoNotInvalidateLists() throws Exception {
        var root = MAPPER.createObjectNode(); root.put("id", "root");
        var children = root.putArray("processGroupStatusSnapshots");
        for (int i=0;i<2000;i++) {
            var child = children.addObject().putObject("processGroupStatusSnapshot"); child.put("id", "g"+i);
            child.putArray("processorStatusSnapshots").addObject().putObject("processorStatusSnapshot").put("runStatus", "Running");
        }
        var response = MAPPER.createObjectNode(); response.putObject("processGroupStatus").set("aggregateSnapshot",root);
        var first = RuntimeStatusIndex.parse(response);
        root.put("flowFilesIn",9999);
        assertThat(RuntimeStatusIndex.parse(response).revision()).isEqualTo(first.revision());
        assertThat(first.groups()).hasSize(2001);
    }
    @Test void runningFlowWithInvalidComponentIsAbnormalButCanStillBeStopped() throws Exception {
        var tree = MAPPER.readTree(response("Running"));
        var list = (com.fasterxml.jackson.databind.node.ArrayNode) tree.at("/processGroupStatus/aggregateSnapshot/processGroupStatusSnapshots/0/processGroupStatusSnapshot/processorStatusSnapshots");
        list.addObject().putObject("processorStatusSnapshot").put("runStatus","Invalid");
        var group = RuntimeStatusIndex.parse(tree).groups().get("child");
        assertThat(group.state()).isEqualTo("RUN_ERROR"); assertThat(group.running()).isTrue(); assertThat(group.taskStatus()).isEqualTo(2);
    }
    @Test void twentyAndOneHundredRowsShareOneRootReadAndKeepHistorySeparate() throws Exception {
        var resolver = mock(NifiNodeRuntimeResolver.class); var nifi = mock(NifiClient.class);
        var node = new RuntimeNode("tenant-a","node-a","node","https://test.invalid","u","p",false,"root");
        when(resolver.resolve()).thenReturn(node); when(nifi.getRuntimeRootStatus(node)).thenReturn(MAPPER.readTree(response("Running")));
        try (var tenant = TenantContext.use("tenant-a")) {
            var service = new TaskRuntimeStatusService(resolver,nifi);
            try {
                var module = new TaskRuntimeMagicModule(service);
                for (int size : List.of(20,100)) {
                    List<Map<String,Object>> rows = new ArrayList<>();
                    for (int i=0;i<size;i++) rows.add(new HashMap<>(Map.of("runtimeProcessGroupId","child","lifecycleStatus","STOPPED","taskStatus",2,"monitorStatus","ERROR")));
                    module.decorate(rows,service.snapshot());
                    assertThat(rows).allSatisfy(row -> { assertThat(row.get("taskStatus")).isEqualTo(1); assertThat(row.get("monitorStatus")).isEqualTo("ERROR"); });
                }
                verify(nifi,times(1)).getRuntimeRootStatus(node);
                assertThat(service.describe(service.snapshot(),"missing","RUNNING")).containsEntry("runtimeState","MISSING");
                service.invalidateTenant(); when(nifi.getRuntimeRootStatus(node)).thenThrow(new IllegalStateException("offline"));
                assertThat(service.describe(service.snapshot(),"child","RUNNING")).containsEntry("runtimeState","UNKNOWN");
            } finally { service.close(); }
        }
    }
}
