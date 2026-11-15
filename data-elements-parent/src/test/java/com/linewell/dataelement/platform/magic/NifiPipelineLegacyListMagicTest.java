package com.linewell.dataelement.platform.magic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class NifiPipelineLegacyListMagicTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void nestedRootSnapshotServesEveryTenantRowWithOneNifiStatusRead(int size) throws Exception {
        var fixture = new Fixture();
        NifiClient nifi = fixture.nifi;
        ObjectNode root = snapshot("root", 0, 0);
        ObjectNode folder = snapshot("folder", 0, 0);
        children(root).add(wrapper(folder));
        for (int i = 0; i < size; i++) {
            fixture.pipeline("tenant-flow-" + i, "pg-" + i, (long) size - i);
            children(folder).add(wrapper(snapshot("pg-" + i, i, i + 1)));
        }
        // A root status can contain other tenants' process groups. Only repository rows are returned.
        children(folder).add(wrapper(snapshot("foreign-pg", 999, 999)));
        fixture.insert("foreign","foreign-pg",1000L,"tenant-b",0,null,null);
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        when(nifi.getProcessGroupStatus("root")).thenReturn(response(root));

        List<Map<String, Object>> rows = fixture.rows(fixture.runtimeGet);

        assertThat(rows).hasSize(size);
        assertThat(rows).extracting(row -> row.get("processGroupId"))
                .doesNotContain("foreign-pg");
        assertThat(rows.get(size - 1)).containsEntry("queuedCount", (long) size)
                .containsEntry("activeThreads", (long) (size - 1))
                .doesNotContainKey("nifiStatusError");
        verify(nifi, times(1)).getRootProcessGroupId();
        verify(nifi, times(1)).getProcessGroupStatus("root");
        verifyNoMoreInteractions(nifi);
    }

    @Test
    void missingProcessGroupHasErrorWithoutPerGroupFallback() throws Exception {
        var fixture = new Fixture();
        NifiClient nifi = fixture.nifi;
        fixture.insert("present", "pg-present", 2L, "tenant-a", 0, """
                {"version":1,"nodes":[{"id":"s","category":"SOURCE","label":"first",
                    "config":{"port":3306,"schema":"schema-a","table":"table-a"}}],"edges":[]}
                """,null);
        fixture.pipeline("gone", "pg-gone", 1);
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        ObjectNode root = snapshot("root", 0, 0);
        var active = snapshot("pg-present", 2, 3);
        active.put("queuedCount", "full ３ files");
        active.put("flowFilesOut", "99999999999999999999999999");
        active.put("flowFilesSent", "1,234");
        children(root).add(wrapper(snapshot("pg-present", 20, 30)));
        children(root).add(wrapper(active));
        when(nifi.getProcessGroupStatus("root")).thenReturn(response(root));

        List<Map<String, Object>> rows = fixture.rows(fixture.runtimeGet);

        assertThat(rows.get(0)).containsEntry("queuedCount", 3L).containsEntry("outputCount", 1L)
                .containsEntry("sourceDatabase", "3306/schema-a").containsEntry("sourceTables", "table-a")
                .containsEntry("sourceNodeName", "first").doesNotContainKey("nifiStatusError");
        assertThat(rows.get(1)).containsKey("nifiStatusError").doesNotContainKey("queuedCount");
        verify(nifi).getRootProcessGroupId();
        verify(nifi).getProcessGroupStatus("root");
        verifyNoMoreInteractions(nifi);
    }

    @Test
    void rootFailureMarksRowsAndDoesNotQueryEachGroup() throws Exception {
        var fixture = new Fixture();
        NifiClient nifi = fixture.nifi;
        fixture.pipeline("first", "pg-first", 2);
        fixture.pipeline("second", "pg-second", 1);
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        when(nifi.getProcessGroupStatus("root")).thenThrow(new IllegalStateException("NiFi unavailable"));

        List<Map<String, Object>> rows = fixture.rows(fixture.runtimeGet);

        assertThat(rows).allSatisfy(row -> assertThat(row)
                .containsEntry("nifiStatusError", "NiFi unavailable").doesNotContainKey("queuedCount"));
        verify(nifi).getRootProcessGroupId();
        verify(nifi).getProcessGroupStatus("root");
        verifyNoMoreInteractions(nifi);
    }

    @Test
    void noDeployedProcessGroupDoesNotCallNifi() throws Exception {
        var fixture = new Fixture();
        NifiClient nifi = fixture.nifi;
        fixture.pipeline("draft", null, 1);

        List<Map<String, Object>> rows = fixture.rows(fixture.runtimeGet);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst()).doesNotContainKey("nifiStatusError");
        verifyNoInteractions(nifi);
    }

    @Test
    void unboundedLegacyListKeepsScopeOrderNullsAndRejectsDamagedSavedState() throws Exception {
        var fixture = new Fixture();
        fixture.jdbc.update("""
                INSERT INTO nifi_pipeline_t(id, tenant_id, is_del, name, updated_at)
                SELECT 'test-' || source.n, 'tenant-a', 0, 'validation', source.n FROM SYSTEM_RANGE(1, 10002) source(n)
                """);
        fixture.insert("bad-dsl", null, 99999L, "tenant-a", 0, "{", null);
        fixture.insert("bad-map", null, 99998L, "tenant-a", 0, "{}", "[]");
        fixture.insert("other", null, 99997L, "tenant-b", 0, null, null);
        fixture.insert("old-marker", null, 99996L, "tenant-a", 2, null, null);
        fixture.insert("null-update", null, null, "tenant-a", 0, "null", "null");

        var rows = fixture.rows(fixture.listGet);
        assertThat(rows).hasSize(10003);
        assertThat(rows.getFirst()).containsEntry("id", "test-10002").containsEntry("nodeCount", 0)
                .containsEntry("description", null).containsEntry("nifiProcessGroupId", null);
        assertThat(rows.getLast()).containsEntry("id", "null-update").containsEntry("updatedAt", null);
        verifyNoInteractions(fixture.nifi);
        assertThat(fixture.rows(fixture.listHead)).isEqualTo(rows);
    }

    private static MagicScript script(String resource, String id, String... imports) throws Exception {
        String code = CanonicalMagicSources.byPath(resource, id);
        MagicScript.create(code, null).compile();
        for (String value : imports) {
            code = code.replaceFirst("(?m)^import " + java.util.regex.Pattern.quote(value) + "\\R", "");
        }
        return MagicScript.create(code, null);
    }

    private static final class Fixture {
        private static final String COMMON = "function/01.通用函数/";
        private static final String API = "api/10.NiFi流程接口/";
        final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:nifi_list_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"));
        final StructuredTableReadMagicModule tableRead = new StructuredTableReadMagicModule(jdbc);
        final NifiClient nifi = mock(NifiClient.class);
        final MagicScript load = script(COMMON + "33.NiFi已保存流程列表.ms", "nifi_saved_canvas_rows_v1",
                "com.fasterxml.jackson.databind.ObjectMapper as objectMapper", "tableRead", "tenantRuntime");
        final MagicScript respond = script(COMMON + "35.NiFi流程列表HTTP响应.ms", "nifi_pipe_list_response_v1");
        final MagicScript list = script(COMMON + "34.NiFi流程列表响应.ms", "nifi_pipe_list_http_v1",
                "'@/common/nifiSavedCanvasRows' as loadSavedCanvases", "'@/common/nifiPipelineListResponse' as respond");
        final MagicScript runtime = script(COMMON + "36.NiFi流程运行列表响应.ms", "nifi_pipe_runtime_list_http_v1",
                "'@/common/nifiSavedCanvasRows' as loadSavedCanvases", "'@/common/nifiPipelineListResponse' as respond",
                "com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi");
        final MagicScript listGet = script(API + "02.流程设计/01.查询流程列表.ms", "nifi_pipe_list_get_v1",
                "'@/common/nifiPipelineListHttp' as readPipelines");
        final MagicScript listHead = script(API + "02.流程设计/02.核验流程列表.ms", "nifi_pipe_list_head_v1",
                "'@/common/nifiPipelineListHttp' as readPipelines");
        final MagicScript runtimeGet = script(API + "05.运行状态/03.查询流程运行列表.ms", "nifi_pipe_runtime_list_get_v1",
                "'@/common/nifiPipelineRuntimeListHttp' as readPipelines");
        final MagicScript runtimeHead = script(API + "05.运行状态/04.核验流程运行列表.ms", "nifi_pipe_runtime_list_head_v1",
                "'@/common/nifiPipelineRuntimeListHttp' as readPipelines");

        Fixture() throws Exception {
            jdbc.execute("""
                    CREATE TABLE nifi_pipeline_t(id varchar(64), name varchar(64), description varchar(1000),
                        tenant_id varchar(64), is_del int, nifi_process_group_id varchar(64), status varchar(32),
                        last_deployed_at bigint, last_stopped_at bigint, updated_at bigint, created_at bigint,
                        dsl_json clob, node_mapping_json clob)
                    """);
        }

        void insert(String id, String group, Long updated, String tenant, int marker, String dsl, String mapping) {
            jdbc.update("""
                    INSERT INTO nifi_pipeline_t(id,name,status,tenant_id,is_del,nifi_process_group_id,created_at,updated_at,dsl_json,node_mapping_json)
                    VALUES (?,?,'RUNNING',?,?,?,1,?,?,?)
                    """, id, id, tenant, marker, group, updated, dsl, mapping);
        }

        void pipeline(String id, String group, long updated) {
            insert(id, group, updated, "tenant-a", 0, null, null);
        }

        List<Map<String, Object>> rows(MagicScript route) {
            return TenantContext.call("tenant-a", () -> {
                MagicScriptLambdaFunction loadSavedCanvases = (context, arguments) -> load.execute(
                        new MagicScriptContext(Map.of("project", arguments[0], "tableRead", tableRead,
                                "tenantRuntime", new TenantMagicModule(null), "objectMapper", JSON)));
                MagicScriptLambdaFunction reply = (context, arguments) ->
                        respond.execute(new MagicScriptContext(Map.of("action", arguments[0])));
                MagicScript behavior = route == listGet || route == listHead ? list : runtime;
                MagicScriptLambdaFunction read = (context, arguments) -> behavior.execute(
                        new MagicScriptContext(Map.of("loadSavedCanvases", loadSavedCanvases, "respond", reply, "nifi", nifi)));
                var response = (ResponseEntity<?>) route.execute(new MagicScriptContext(Map.of("readPipelines", read)));
                assertThat(response.getStatusCode().value()).isEqualTo(200);
                assertThat(response.getBody()).isInstanceOf(List.class);
                return StructuredValues.objectRows(response.getBody());
            });
        }
    }

    private static ObjectNode snapshot(String id, long activeThreads, long queuedCount) {
        ObjectNode snapshot = JSON.createObjectNode();
        snapshot.put("id", id);
        snapshot.put("activeThreadCount", activeThreads);
        snapshot.put("queuedCount", queuedCount);
        snapshot.put("flowFilesIn", 7);
        snapshot.put("flowFilesOut", 5);
        return snapshot;
    }

    private static ArrayNode children(ObjectNode snapshot) {
        JsonNode existing = snapshot.get("processGroupStatusSnapshots");
        return existing instanceof ArrayNode array ? array : snapshot.putArray("processGroupStatusSnapshots");
    }

    private static ObjectNode wrapper(ObjectNode snapshot) {
        ObjectNode wrapper = JSON.createObjectNode();
        wrapper.put("id", snapshot.path("id").asText());
        wrapper.set("processGroupStatusSnapshot", snapshot);
        return wrapper;
    }

    private static JsonNode response(ObjectNode root) {
        ObjectNode response = JSON.createObjectNode();
        response.putObject("processGroupStatus").set("aggregateSnapshot", root);
        return response;
    }
}
