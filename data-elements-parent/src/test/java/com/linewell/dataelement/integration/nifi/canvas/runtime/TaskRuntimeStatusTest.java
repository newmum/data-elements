package com.linewell.dataelement.integration.nifi.canvas.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver.RuntimeNode;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskRuntimeStatusTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static String response(String state) { return """
        {"processGroupStatus":{"aggregateSnapshot":{"id":"root","processGroupStatusSnapshots":[
        {"processGroupStatusSnapshot":{"id":"child","activeThreadCount":0,"processorStatusSnapshots":[
        {"processorStatusSnapshot":{"id":"p","runStatus":"%s"}}]}}]}}}
        """.formatted(state); }
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
