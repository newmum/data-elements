package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.errors.ErrorService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.DslHasher;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.HiveDownstreamFixtures;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import com.linewell.dataelement.integration.nifi.canvas.runtime.RuntimeStatusIndex;
import com.linewell.dataelement.integration.nifi.canvas.runtime.TaskRuntimeStatusService;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.model.common.StatusCodeEnum;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

class NifiPipelineReadMagicTest {
    private static final String API = "api/09.海通流程接口/";
    private static final String PIPELINE_API = API;
    private static final String COMMON = "function/01.通用函数/";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void runtimeReadUsesSavedBindingAndCompilerFingerprintWithoutExposingForeignRows() throws Exception {
        var fixture = new Fixture();
        fixture.flow("design", "运行中的画布", "SAVED", "tenant-a", 0, 50L, 60L);
        fixture.flow("foreign", "外部画布", "RUNNING", "tenant-b", 0, 50L, 60L);
        fixture.flow("deleted", "删除画布", "RUNNING", "tenant-a", 1, 50L, 60L);
        fixture.flow("invalid", "损坏画布", "RUNNING", "tenant-a", 0, 50L, 60L);
        fixture.flow("draft", "未部署画布", "DRAFT", "tenant-a", 0, null, null);
        var design = new Pipeline.Dsl(1, List.of(), List.of());
        fixture.jdbc.update("UPDATE nifi_pipeline_t SET nifi_process_group_id=?, dsl_json=? WHERE id=?",
                "group-a", JSON.writeValueAsString(design), "design");
        fixture.jdbc.update("UPDATE nifi_pipeline_t SET dsl_json='broken' WHERE id='invalid'");
        var runtime = mock(TaskRuntimeStatusService.class);
        var index = mock(RuntimeStatusIndex.class);
        JsonNode nativeStatus = JSON.readTree("{\"group\":\"group-a\"}");
        when(index.rawStatus("group-a")).thenReturn(nativeStatus);
        var fresh = Map.<String, Object>of("revision", 8, "index", index, "fresh", true);
        when(runtime.snapshot()).thenReturn(fresh);
        when(runtime.describe(fresh, "group-a", "SAVED")).thenReturn(Map.of("runtimeState", "RUNNING", "runtimeFresh", true));
        var errors = mock(ErrorService.class);
        var bindings = Map.<String, Object>of("runtimeStatus", runtime, "errorStore", errors);
        for (boolean head : List.of(false, true)) {
            var link = body(fixture.runRead("ui", head, "design", Map.of()));
            assertThat(link).containsEntry("url", "/nifi-ui/#/process-groups/group-a")
                    .containsEntry("processGroupId", "group-a").containsEntry("reusesBrowserSession", true)
                    .containsEntry("authenticatedByBackend", true);
            assertThat(fixture.reads).isEqualTo(1);
            var state = body(fixture.runRead("status", head, "design", bindings));
            assertThat(state).containsEntry("status", "RUNNING").containsEntry("deployed", true)
                    .containsEntry("runtimeRevision", 8).containsEntry("nifiStatus", nativeStatus)
                    .containsEntry("currentHash", DslHasher.deploymentHash(design, DslHasher.CURRENT_COMPILER_REVISION))
                    .doesNotContainKey("nifiStatusError");
            assertThat(fixture.reads).isEqualTo(1);
        }
        when(runtime.describe(fresh, "group-a", "SAVED")).thenReturn(Map.of("runtimeState", "MISSING", "runtimeFresh", true));
        assertThat(body(fixture.runRead("status", false, "design", bindings))).containsEntry("status", "SAVED")
                .containsEntry("nifiStatusError", "NiFi 中未找到已关联的流程");

        for (String absent : List.of("foreign", "deleted", "invalid")) {
            for (String operation : List.of("ui", "status", "page")) {
                assertThat(fixture.runRead(operation, false, absent, Map.of()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(fixture.reads).isEqualTo(1);
                assertThat(fixture.detailSql.getFirst()).contains("tenant_id");
            }
        }
        assertThat(fixture.runRead("ui", false, "draft", Map.of()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        fixture.jdbc.update("UPDATE nifi_pipeline_t SET nifi_process_group_id=' ' WHERE id='draft'");
        assertThat(fixture.runRead("ui", false, "draft", Map.of()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        for (String status : List.of("STOPPED", "DEPLOY_FAILED", "RUNNING", "DEPLOYING")) {
            fixture.jdbc.update("UPDATE nifi_pipeline_t SET status=?, last_deployed_hash=? WHERE id='design'",
                    status, status.equals("RUNNING") ? null : "old-compiler-fingerprint");
            assertThat(body(fixture.runRead("ui", false, "design", Map.of()))).containsEntry("processGroupId", "group-a");
        }
        org.mockito.Mockito.verify(errors, org.mockito.Mockito.times(3)).list("design");
        org.mockito.Mockito.verifyNoMoreInteractions(errors);
    }

    @Test void processorPagesSortCompleteSnapshotWithOneTenantQueryAndOneNativeReadPerRequest() throws Exception {
        var fixture = new Fixture();
        fixture.flow("design", "处理器分页", "SAVED", "tenant-a", 0, 50L, 60L);
        fixture.jdbc.update("UPDATE nifi_pipeline_t SET nifi_process_group_id='group-a' WHERE id='design'");
        var processors = new ArrayList<Map<String, Object>>();
        for (int i = 149; i >= 0; i--) {
            processors.add(Map.of("processorStatusSnapshot", Map.of("id", "p" + i, "name", String.format("name%03d", i),
                    "flowFilesIn", i, "input", i + " (1 KB)", "read", (150 - i) + ".5 KB", "taskCount", i)));
        }
        JsonNode snapshot = JSON.valueToTree(Map.of("processGroupStatus", Map.of("aggregateSnapshot",
                Map.of("processGroupStatusSnapshots", List.of(Map.of("processGroupStatusSnapshot",
                        Map.of("processorStatusSnapshots", processors)))))));
        var nifi = mock(NifiClient.class);
        when(nifi.getProcessGroupStatus("group-a")).thenReturn(snapshot);
        var bindings = Map.<String, Object>of("nifi", nifi);
        fixture.request.put("sortField", "flowFilesIn");
        fixture.request.put("sortDir", "asc");
        var pages = new ArrayList<List<Map<String, Object>>>();
        for (int size : List.of(20, 100)) {
            fixture.request.put("size", Integer.toString(size));
            var page = body(fixture.runRead("page", size == 100, "design", bindings));
            assertThat(page).containsEntry("page", 1).containsEntry("total", 150).containsEntry("size", size)
                    .containsEntry("deployed", true);
            var rows = StructuredValues.objectRows(page.get("list"));
            assertThat(rows).hasSize(size);
            assertThat(rows.getFirst()).containsEntry("key", "p0");
            pages.add(rows);
            assertThat(fixture.reads).isEqualTo(1);
        }
        org.mockito.Mockito.verify(nifi, org.mockito.Mockito.times(2)).getProcessGroupStatus("group-a");
        assertThat(pages.getFirst()).isEqualTo(pages.getLast().subList(0, 20));

        fixture.request.put("size", "20");
        fixture.request.put("page", "2");
        fixture.request.put("sortField", "name");
        fixture.request.put("sortDir", "desc");
        assertThat(StructuredValues.objectRows(body(fixture.runRead("page", false, "design", bindings)).get("list"))
                .getFirst().get("key")).isEqualTo("p129");
        fixture.request.put("page", "1");
        fixture.request.put("sortField", "bytesIn");
        var byBytes = StructuredValues.objectRows(body(fixture.runRead("page", false, "design", bindings)).get("list"));
        assertThat(byBytes.getFirst()).containsEntry("key", "p0").containsEntry("bytesInCount", new java.math.BigDecimal("154112.0"));
        fixture.request.put("sortField", "name;delete");
        assertError(fixture.runRead("page", false, "design", bindings), 500, "不支持的排序字段：name;delete");
        fixture.request.put("size", "101");
        assertError(fixture.runRead("page", false, "design", bindings), 500, "处理器分页参数无效");
        fixture.request.put("page", "bad");
        assertError(fixture.runRead("page", false, "design", bindings), StatusCodeEnum.PARAMS_UNVALID.getCode(),
                "参数[page]类型错误，期望类型: int");
        assertThat(fixture.reads).isZero();
    }

    @Test void overviewAggregatesAllAuthorizedFlowsAndOrdersBeforeSelectingFiveInTwoReads() throws Exception {
        for (int count : List.of(20, 100)) {
            var fixture = new Fixture();
            for (int index = 0; index < count; index++) {
                String status = switch (index % 4) {
                    case 0 -> "RUNNING";
                    case 1 -> "RUN_ERROR";
                    case 2 -> "DEPLOY_FAILED";
                    default -> "SAVED";
                };
                fixture.flow("id-" + index, String.format("flow-%03d", count - 1 - index), status,
                        "tenant-a", 0, (long) index, (long) index);
            }
            fixture.flow("outside", "aaa", "RUNNING", "tenant-b", 0, 999L, 999L);
            fixture.flow("deleted", "aaa", "RUNNING", "tenant-a", 1, 999L, 999L);

            var byName = fixture.overview("taskName", "asc");
            assertThat(byName).containsEntry("total", (long) count).containsEntry("running", (long) count / 4)
                    .containsEntry("failed", (long) count / 2);
            assertThat(rows(byName)).extracting(row -> row.get("taskName"))
                    .containsExactly("flow-000", "flow-001", "flow-002", "flow-003", "flow-004");
            var byRun = fixture.overview("latestRunTime", "desc");
            assertThat(rows(byRun)).extracting(row -> row.get("id"))
                    .containsExactly("id-" + (count - 1), "id-" + (count - 2), "id-" + (count - 3),
                            "id-" + (count - 4), "id-" + (count - 5));
            assertThat(rows(byRun).getFirst().get("latestRunTime")).isEqualTo((long) count - 1);
        }
    }

    @Test void overviewUsesNullLastAndStableIdsForBothTimeAndNameSorts() throws Exception {
        var fixture = new Fixture();
        fixture.flow("b", "b", "SAVED", "tenant-a", 0, 12L, 12L);
        fixture.flow("a", "a", "RUNNING", "tenant-a", 0, 12L, 12L);
        fixture.flow("c", "c", "STOPPED", "tenant-a", 0, 10L, 10L);
        fixture.flow("null-z", "z", null, "tenant-a", 0, null, null);
        fixture.flow("null-a", null, "DEPLOYED", "tenant-a", 0, null, null);
        fixture.jdbc.update("UPDATE nifi_pipeline_t SET nifi_process_group_id=?, last_stopped_at=? WHERE id=?",
                "group-c", 1700000000123L, "c");

        assertThat(ids(fixture.overview(null, null))).containsExactly("a", "b", "c", "null-a", "null-z");
        assertThat(ids(fixture.overview(" latestRunTime ", " ASC ")))
                .containsExactly("c", "a", "b", "null-a", "null-z");
        assertThat(ids(fixture.overview("taskName", "desc"))).containsExactly("null-z", "c", "b", "a", "null-a");
        var row = rows(fixture.overview("latestRunTime", "asc")).getFirst();
        assertThat(row).containsOnlyKeys("id", "taskName", "currentStatus", "processGroupId", "latestRunTime", "endTime")
                .containsEntry("currentStatus", "STOPPED").containsEntry("processGroupId", "group-c")
                .containsEntry("latestRunTime", 10L).containsEntry("endTime", 1700000000123L);
        assertThat(rows(fixture.overview(null, null)).getLast()).containsEntry("currentStatus", null)
                .containsEntry("latestRunTime", null).containsEntry("endTime", null);
    }

    @Test void routeReturnsTheUnwrappedLegacyJsonAndAnEmptyTenantGetsZeroCounts() throws Exception {
        var fixture = new Fixture();
        fixture.flow("a", "a", "RUNNING", "tenant-a", 0, 15L, 16L);
        assertThat(body(TenantContext.call("tenant-b", () -> fixture.overviewResponse(null, null))))
                .containsExactlyInAnyOrderEntriesOf(Map.of("total", 0L, "running", 0L, "failed", 0L, "flows", List.of()));

        fixture.request.put("tenantId", "tenant-b");
        try (var tenant = TenantContext.use("tenant-a")) {
            JsonNode rawJson = JSON.readTree(JSON.writeValueAsBytes(body(fixture.overviewResponse(null, null))));
            assertThat(rawJson)
                    .isEqualTo(JSON.readTree("""
                            {"total":1,"running":1,"failed":0,"flows":[{"id":"a","taskName":"a",
                              "currentStatus":"RUNNING","processGroupId":null,"latestRunTime":15,"endTime":null}]}
                            """));
        }
    }

    @Test void missingTenantAndUnsupportedSortFailBeforeAnyQuery() throws Exception {
        var fixture = new Fixture();
        assertError(fixture.overviewResponse(null, null), 403, "当前请求未绑定租户");
        try (var tenant = TenantContext.use("tenant-a"); var control = TenantContext.control()) {
            assertError(fixture.overviewResponse(null, null), StatusCodeEnum.ERROR.getCode(), "结构化查询租户无效");
        }
        TenantContext.run("tenant-a", () -> {
            assertError(fixture.overviewResponse("status", "desc"), StatusCodeEnum.ERROR.getCode(),
                    "不支持的排序字段：status");
            assertError(fixture.overviewResponse("updatedAt", "sideways"), StatusCodeEnum.ERROR.getCode(),
                    "排序方向只能是 asc 或 desc");
        });
        assertThat(fixture.reads).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void pageBindsQueryOnceAndReturnsScopedRawJson(int size) throws Exception {
        var fixture = new Fixture();
        fixture.flow("visible", "%_,literal", "SAVED", "tenant-a", 0, null, null);
        fixture.flow("bare-prefix", "%_", "SAVED", "tenant-a", 0, null, null);
        fixture.flow("wildcard", "ab,literal", "SAVED", "tenant-a", 0, null, null);
        fixture.flow("foreign", "%_,literal", "SAVED", "tenant-b", 0, null, null);
        fixture.flow("deleted", "%_,literal", "SAVED", "tenant-a", 1, null, null);
        fixture.request.put("page[]", "2", "9");
        fixture.request.put("size", String.valueOf(size));
        fixture.request.put("sortField", "nodeCount");
        fixture.request.put("sortDir", "asc");
        fixture.request.put("q", "%_", "literal");
        fixture.request.put("tenantId", "tenant-b");
        var response = TenantContext.call("tenant-a", () -> fixture.route(fixture.pageGet));
        assertThat(body(response)).containsExactlyInAnyOrderEntriesOf(
                Map.of("page", 2, "size", size, "total", 1L, "list", List.of()));
        assertThat(fixture.reads).isOne();
    }

    @Test
    void pageRejectsInvalidNumbersAndUnavailableIndustryTenantBeforeReading() throws Exception {
        var fixture = new Fixture();
        assertError(fixture.route(fixture.pageGet), 403, "当前请求未绑定租户");
        try (var tenant = TenantContext.use("tenant-a"); var control = TenantContext.control()) {
            assertError(fixture.route(fixture.pageGet), StatusCodeEnum.ERROR.getCode(), "流程查询必须绑定行业租户");
        }
        fixture.request.put("size", "2147483648");
        TenantContext.run("tenant-a", () -> assertError(fixture.route(fixture.pageGet),
                StatusCodeEnum.PARAMS_UNVALID.getCode(), "参数[size]类型错误，期望类型: int"));
        assertThat(fixture.reads).isZero();
    }

    @Test
    void headUsesTheSameResponseObjectAsGetForAllThreeRoutes() throws Exception {
        var fixture = new Fixture();
        try (var tenant = TenantContext.use("tenant-a")) {
            assertThat(fixture.route(fixture.pageHead)).isEqualTo(fixture.route(fixture.pageGet));
            assertThat(fixture.route(fixture.overviewHead)).isEqualTo(fixture.route(fixture.overviewGet));
        }
        var ping = script(API + "01.组件清单/05.查询画布接口健康.ms", "nifi_canvas_ping_get_v1");
        var pingHead = script(API + "01.组件清单/06.核验画布接口健康.ms", "nifi_canvas_ping_head_v1");
        assertThat(fixture.route(ping)).isEqualTo(ResponseEntity.ok(Map.of("status", "ok")));
        assertThat(fixture.route(pingHead)).isEqualTo(fixture.route(ping));
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void detailPreservesSavedSecondHopAndRawJsonInOneScopedQuery(int nodeCount) throws Exception {
        var fixture = new Fixture();
        var nodes = new ArrayList<Pipeline.Node>();
        var source = new Pipeline.Node("source-ods", "source.oceanbase", "汇聚库", "source", 60, 100,
                Map.of("sourceTableId", "ods-table", "sourceDbId", "ods-db", "incrementalColumn", "ODS_GXSJ"));
        nodes.add(source);
        for (int index = 1; index < nodeCount; index++) {
            nodes.add(new Pipeline.Node("sink-" + index, "sink.mysql", "目标 " + index, "sink", index, 0, Map.of()));
        }
        var saved = new Pipeline("validation-second-hop", "第二跳", null, 10L, 20L,
                new Pipeline.Dsl(1, nodes, List.of()), "validation-native-group", PipelineStatus.STOPPED,
                "validation-deployed-hash", 15L, 19L,
                new NifiNodeMapping(Map.of(source.id(), "validation-processor"), null, null),
                9007199254740993L);
        fixture.detailFlow(saved, "sToP", "tenant-a", 0);

        var get = TenantContext.call("tenant-a", () -> fixture.detail(fixture.detailGet, saved.id()));
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get.getBody()).isEqualTo(saved);
        assertThat((JsonNode) JSON.valueToTree(get.getBody())).isEqualTo(JSON.valueToTree(saved));
        assertThat(fixture.detailSql).singleElement().satisfies(sql -> assertThat(sql)
                .startsWith("SELECT ").contains("tenant_id", "id"));
        assertThat(fixture.reads).isOne();

        var head = TenantContext.call("tenant-a", () -> fixture.detail(fixture.detailHead, saved.id()));
        assertThat(head).isEqualTo(get);
        assertThat(fixture.reads).isOne();
        assertThat(fixture.detailSql).hasSize(1);
        assertThat(fixture.jdbc.queryForObject("SELECT dsl_json FROM nifi_pipeline_t WHERE id=?", String.class, saved.id()))
                .isEqualTo(JSON.writeValueAsString(saved.dsl()));
    }

    @Test
    void projectedHiveDetailSendsTheStoredDesignHashForTheNextSave() throws Exception {
        var fixture = new Fixture();
        var source = new Pipeline.Node("s", "source.mysql", "前段", "source", 0, 0, Map.of());
        var mapping = new Pipeline.Node("m", "transform.field-enrichment", "字段映射", "transform", 300, 0, Map.of());
        var hive = HiveDownstreamFixtures.rawTarget(
                new Pipeline.Node("h", "sink.hive", "Hive", "sink", 600, 0, Map.of()));
        var saved = new Pipeline("validation-hive", "已保存画布", null, 10L, 20L,
                new Pipeline.Dsl(1, List.of(source, mapping, hive), List.of(
                        new Pipeline.Edge("sm", "s", "m", "default"),
                        new Pipeline.Edge("mh", "m", "h", "default"))),
                null, PipelineStatus.SAVED, null, null, null, null, null);
        fixture.detailFlow(saved, "unknown", "tenant-a", 0);

        var response = TenantContext.call("tenant-a", () -> fixture.detail(fixture.detailGet, saved.id()));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOfSatisfying(Pipeline.class, projected -> {
            assertThat(projected.dsl().nodes()).containsExactly(source, hive);
            assertThat(projected.dsl().edges()).containsExactly(new Pipeline.Edge("mh-hive-raw-0", "s", "h", "default"));
            assertThat(projected.status()).isEqualTo(PipelineStatus.SAVED);
            assertThat(projected.designHash()).isEqualTo(saved.designHash());
        });
        JsonNode wire = JSON.valueToTree(response.getBody());
        assertThat(wire.path("designHash").asText()).isEqualTo(saved.designHash());
        assertThat(wire.has("storedDesignHash")).isFalse();
        assertThat(wire.has("description")).isFalse();
        assertThat(fixture.detailSql).hasSize(1);
    }

    @Test
    void detailRejectsForeignDeletedAndUnreadableRowsWithAnEmptyNotFound() throws Exception {
        var fixture = new Fixture();
        fixture.flow("validation-foreign", "其他租户", "RUNNING", "tenant-b", 0, null, null);
        fixture.flow("validation-deleted", "已删除", "RUNNING", "tenant-a", 1, null, null);
        fixture.flow("validation-invalid-dsl", "损坏设计", "DRAFT", "tenant-a", 0, null, null);
        fixture.flow("validation-invalid-mapping", "损坏映射", "DRAFT", "tenant-a", 0, null, null);
        fixture.jdbc.update("UPDATE nifi_pipeline_t SET dsl_json=? WHERE id=?", "{broken", "validation-invalid-dsl");
        fixture.jdbc.update("UPDATE nifi_pipeline_t SET node_mapping_json=? WHERE id=?", "[]", "validation-invalid-mapping");
        for (String id : List.of("validation-foreign", "validation-deleted", "validation-invalid-dsl",
                "validation-invalid-mapping", "validation-missing", "x' OR '1'='1")) {
            var result = TenantContext.call("tenant-a", () -> fixture.detail(fixture.detailGet, id));
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(result.getBody()).isNull();
            assertThat(fixture.detailSql).hasSize(1);
        }

        var foreignOwner = TenantContext.call("tenant-b", () -> fixture.detail(fixture.detailGet, "validation-foreign"));
        assertThat(foreignOwner.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(foreignOwner.getBody()).isInstanceOfSatisfying(Pipeline.class, pipeline -> {
            assertThat(pipeline.status()).isEqualTo(PipelineStatus.RUNNING);
            assertThat(pipeline.dsl()).isNull();
            assertThat(pipeline.nodeMapping()).isNull();
            assertThat(pipeline.updatedAt()).isNull();
        });
    }

    @Test
    void detailRejectsMissingControlAndIgnoredTenantBeforeReading() throws Exception {
        var fixture = new Fixture();
        assertError(fixture.detail(fixture.detailHead, "validation-missing"), 403, "当前请求未绑定租户");
        assertThat(fixture.detailSql).isEmpty();
        try (var tenant = TenantContext.use("tenant-a"); var control = TenantContext.control()) {
            assertError(fixture.detail(fixture.detailGet, "validation-missing"), 403, "流程数据必须在当前租户数据库中访问");
            assertThat(fixture.detailSql).isEmpty();
        }
        try (var tenant = TenantContext.use("tenant-a"); var ignored = TenantContext.ignore()) {
            assertError(fixture.detail(fixture.detailGet, "validation-missing"), 403, "流程数据必须在当前租户数据库中访问");
            assertThat(fixture.detailSql).isEmpty();
        }
    }

    private static void assertError(ResponseEntity<?> response, int code, String message) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOfSatisfying(CommonResponse.class, error -> {
            assertThat(error.getCode()).isEqualTo(code);
            assertThat(error.getMsg()).isEqualTo(message);
        });
    }

    private static Map<String, Object> body(ResponseEntity<?> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        return StructuredValues.objectMap(response.getBody());
    }

    private static MagicScript script(String resource, String metadataId, String... bindings) throws IOException {
        String source = CanonicalMagicSources.byPath(resource, metadataId);
        MagicScript.create(source, null).compile();
        for (String binding : bindings) {
            source = source.replaceFirst("(?m)^import " + java.util.regex.Pattern.quote(binding) + "\\R", "");
        }
        return MagicScript.create(source, null);
    }

    public static final class QueryParameters {
        private final Map<String, List<String>> values = new LinkedHashMap<>();

        void put(String key, String... arguments) {
            values.put(key, Arrays.asList(arguments));
        }

        void setOptional(String key, String value) {
            if (value == null) {
                values.remove(key);
            } else {
                put(key, value);
            }
        }

        public List<String> getValues(String name) {
            return values.get(name);
        }
    }

    private static List<Map<String, Object>> rows(Map<String, Object> result) {
        return StructuredValues.objectRows(result.get("flows"));
    }

    private static List<Object> ids(Map<String, Object> result) {
        return rows(result).stream().map(row -> row.get("id")).toList();
    }

    private static final class Fixture {
        final QueryParameters request = new QueryParameters();
        final JdbcTemplate jdbc;
        final StructuredTableReadMagicModule tableRead;
        final MagicScript overview = script(COMMON + "20.海通流程运行总览.ms", "4a9311c67f104f589affeb51aa1e3a62",
                "tableRead", "tableSort", "tenantRuntime", "'@/common/nifiPipelineReadHttp' as withHttpResponse");
        final MagicScript page = script(COMMON + "22.海通流程分页.ms", "947c14bf3a694e99ae078d79d4307cbd",
                "tableRead", "tableSort", "tenantRuntime", "'@/common/nifiPipelineReadHttp' as withHttpResponse");
        final MagicScript response = script(COMMON + "29.海通流程只读接口响应.ms", "nifi_pipeline_read_http_v1", "request");
        final MagicScript pageGet = script(PIPELINE_API + "02.流程设计/04.查询流程分页.ms", "nifi_pipeline_page_get_v1",
                "'@/common/nifiPipelinePage' as readPipelinePage");
        final MagicScript pageHead = script(PIPELINE_API + "02.流程设计/05.核验流程分页.ms", "nifi_pipeline_page_head_v1",
                "'@/common/nifiPipelinePage' as readPipelinePage");
        final MagicScript overviewGet = script(PIPELINE_API + "05.运行状态/01.查询流程运行总览.ms", "nifi_runtime_overview_get_v1",
                "'@/common/nifiRuntimeOverview' as readOverview");
        final MagicScript overviewHead = script(PIPELINE_API + "05.运行状态/02.核验流程运行总览.ms", "nifi_runtime_overview_head_v1",
                "'@/common/nifiRuntimeOverview' as readOverview");
        final MagicScript detailResponse = script(COMMON + "32.海通流程详情接口响应.ms", "nifi_pipe_detail_http_v1",
                "com.fasterxml.jackson.databind.ObjectMapper as objectMapper", "tableRead", "tenantRuntime");
        final MagicScript detailGet = script(PIPELINE_API + "02.流程设计/06.查询流程详情.ms", "nifi_pipe_detail_get_v1",
                "'@/common/nifiPipelineDetailHttp' as pipelineDetail");
        final MagicScript detailHead = script(PIPELINE_API + "02.流程设计/07.核验流程详情.ms", "nifi_pipe_detail_head_v1",
                "'@/common/nifiPipelineDetailHttp' as pipelineDetail");
        final List<String> detailSql = new ArrayList<>();
        final MagicScript decodeCanvas = script(COMMON + "43.海通解码保存画布.ms", "e06d4eb123494419e67f70f2fc03bceb",
                "com.fasterxml.jackson.databind.ObjectMapper as mapper");
        final MagicScript findCanvas = script(COMMON + "48.海通读取租户画布.ms", "nifi_pipeline_find_v1",
                "tableRead", "'@/common/nifiDecodeCanvas' as decodeCanvas");
        int reads;

        Fixture() throws Exception {
            var source = new DriverManagerDataSource("jdbc:h2:mem:nifi_overview_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "") {
                @Override public Connection getConnection() throws SQLException {
                    Connection connection = super.getConnection();
                    return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                            new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                                if (method.getName().equals("getMetaData")) {
                                    // Use H2's MySQL grammar with the normal MySQL pagination renderer.
                                    var metadata = connection.getMetaData();
                                    return Proxy.newProxyInstance(getClass().getClassLoader(),
                                            new Class<?>[]{DatabaseMetaData.class}, (ignored, operation, parameters) -> {
                                                if (operation.getName().equals("getURL")) {
                                                    return "jdbc:mysql://localhost/nifi_overview_validation";
                                                }
                                                try {
                                                    return operation.invoke(metadata, parameters);
                                                } catch (InvocationTargetException error) {
                                                    throw error.getCause();
                                                }
                                            });
                                }
                                if (method.getName().equals("prepareStatement") && args[0] instanceof String sql) {
                                    detailSql.add(sql);
                                    if (sql.stripLeading().toLowerCase(Locale.ROOT).startsWith("select")) reads++;
                                }
                                try {
                                    return method.invoke(connection, args);
                                } catch (InvocationTargetException error) {
                                    throw error.getCause();
                                }
                            });
                }
            };
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    CREATE TABLE nifi_pipeline_t(id varchar(64), name varchar(64), status varchar(32),
                        tenant_id varchar(64), is_del int, nifi_process_group_id varchar(64),
                        last_deployed_at bigint, last_stopped_at bigint, updated_at bigint,
                        description varchar(1000), last_deployed_hash varchar(64), created_at bigint,
                        dsl_json clob, node_mapping_json clob, last_bulletin_id bigint)
                    """);
            tableRead = new StructuredTableReadMagicModule(jdbc);
        }

        void flow(String id, String name, String status, String tenant, int deleted, Long deployed, Long updated) {
            jdbc.update("""
                    INSERT INTO nifi_pipeline_t (id,name,status,tenant_id,is_del,last_deployed_at,updated_at)
                    VALUES (?,?,?,?,?,?,?)
                    """,
                    id, name, status, tenant, deleted, deployed, updated);
        }

        void detailFlow(Pipeline saved, String status, String tenant, int deleted) throws IOException {
            jdbc.update("""
                    INSERT INTO nifi_pipeline_t (id,name,status,tenant_id,is_del,description,created_at,updated_at,
                      dsl_json,nifi_process_group_id,last_deployed_hash,last_deployed_at,last_stopped_at,
                      node_mapping_json,last_bulletin_id)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    saved.id(), saved.name(), status, tenant, deleted, saved.description(), saved.createdAt(),
                    saved.updatedAt(), JSON.writeValueAsString(saved.dsl()), saved.nifiProcessGroupId(),
                    saved.lastDeployedHash(), saved.lastDeployedAt(), saved.lastStoppedAt(),
                    JSON.writeValueAsString(saved.nodeMapping()), saved.lastBulletinId());
        }

        ResponseEntity<?> detail(MagicScript route, String id) {
            detailSql.clear();
            reads = 0;
            MagicScriptLambdaFunction pipelineDetail = (context, args) -> detailResponse.execute(
                    new MagicScriptContext(Map.of("flowId", args[0], "objectMapper", JSON,
                            "tenantRuntime", new TenantMagicModule(null), "tableRead", tableRead)));
            return (ResponseEntity<?>) route.execute(new MagicScriptContext(Map.of(
                    "param", Map.of("id", id, "tenantId", "tenant-b"), "pipelineDetail", pipelineDetail)));
        }

        ResponseEntity<?> runRead(String operation, boolean head, String id, Map<String, Object> extras) throws Exception {
            String functionFile;
            String functionId;
            String importName;
            String routeId;
            String getRoute;
            String headRoute;
            switch (operation) {
                case "ui" -> {
                    functionFile = "49.海通流程原生地址响应.ms";
                    functionId = "nifi_pipeline_ui_link_http_v1";
                    importName = "nifiPipelineUiLink";
                    routeId = "nifi_pipeline_ui_link";
                    getRoute = "02.流程设计/10.查询流程原生地址.ms";
                    headRoute = "02.流程设计/11.核验流程原生地址.ms";
                }
                case "status" -> {
                    functionFile = "50.海通流程运行状态响应.ms";
                    functionId = "nifi_pipeline_status_http_v1";
                    importName = "nifiPipelineStatus";
                    routeId = "nifi_pipeline_status";
                    getRoute = "05.运行状态/05.查询流程运行状态.ms";
                    headRoute = "05.运行状态/06.核验流程运行状态.ms";
                }
                case "page" -> {
                    functionFile = "51.海通处理器状态分页响应.ms";
                    functionId = "nifi_processor_page_http_v1";
                    importName = "nifiProcessorStatusPage";
                    routeId = "nifi_processor_page";
                    getRoute = "05.运行状态/07.查询处理器状态分页.ms";
                    headRoute = "05.运行状态/08.核验处理器状态分页.ms";
                }
                default -> throw new IllegalArgumentException(operation);
            }
            var read = script(COMMON + functionFile, functionId, "'@/common/nifiFindCanvas' as findCanvas",
                    "'@/common/nifiPipelineReadHttp' as withHttpResponse", "tenantRuntime", "tableSort",
                    "com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi",
                    "com.linewell.dataelement.integration.nifi.canvas.errors.ErrorService as errorStore",
                    "com.linewell.dataelement.integration.nifi.canvas.runtime.TaskRuntimeStatusService as runtimeStatus");
            var endpoint = script(API + (head ? headRoute : getRoute),
                    routeId + (head ? "_head" : "_get") + "_v1", "'@/common/" + importName + "' as read");
            MagicScriptLambdaFunction decode = (context, args) -> decodeCanvas.execute(
                    new MagicScriptContext(Map.of("row", args[0], "mapper", JSON)));
            MagicScriptLambdaFunction find = (context, args) -> findCanvas.execute(new MagicScriptContext(
                    Map.of("flowId", args[0], "tenantId", args[1], "tableRead", tableRead, "decodeCanvas", decode)));
            MagicScriptLambdaFunction http = (context, args) -> response.execute(new MagicScriptContext(Map.of("action", args[0], "request", request)));
            MagicScriptLambdaFunction action = (context, args) -> {
                var variables = new LinkedHashMap<>(extras);
                variables.putAll(Map.of("flowId", args[0], "findCanvas", find, "withHttpResponse", http,
                        "tableSort", new TableSortMagicModule(), "tenantRuntime", new TenantMagicModule(null)));
                return read.execute(new MagicScriptContext(variables));
            };
            detailSql.clear();
            reads = 0;
            return TenantContext.call("tenant-a", () -> (ResponseEntity<?>) endpoint.execute(new MagicScriptContext(
                    Map.of("param", Map.of("id", id, "tenantId", "tenant-b"), "read", action))));
        }

        Object executeRead(MagicScript businessScript) {
            MagicScriptLambdaFunction withHttpResponse = (context, args) ->
                    response.execute(new MagicScriptContext(Map.of("action", args[0], "request", request)));
            return businessScript.execute(new MagicScriptContext(Map.of("withHttpResponse", withHttpResponse,
                    "tableRead", tableRead, "tableSort", new TableSortMagicModule(),
                    "tenantRuntime", new TenantMagicModule(null))));
        }

        ResponseEntity<?> route(MagicScript route) {
            MagicScriptLambdaFunction readPage = (context, args) -> executeRead(page);
            MagicScriptLambdaFunction readOverview = (context, args) -> executeRead(overview);
            return (ResponseEntity<?>) route.execute(new MagicScriptContext(
                    Map.of("readPipelinePage", readPage, "readOverview", readOverview)));
        }

        ResponseEntity<?> overviewResponse(String field, String direction) {
            request.setOptional("sortField", field);
            request.setOptional("sortDir", direction);
            return route(overviewGet);
        }

        Map<String, Object> overview(String field, String direction) {
            reads = 0;
            var result = body(TenantContext.call("tenant-a", () -> overviewResponse(field, direction)));
            assertThat(reads).isEqualTo(2);
            return result;
        }
    }
}
