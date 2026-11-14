package com.linewell.dataelement.integration.nifi.canvas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.model.common.StatusCodeEnum;
import com.linewell.dataelement.platform.magic.StructuredTableReadMagicModule;
import com.linewell.dataelement.platform.magic.TableSortMagicModule;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.web.error.GlobalExceptionHandler;
import com.linewell.dataelement.utils.StructuredValues;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.ssssssss.magicapi.core.service.MagicAPIService;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

class PipelineControllerOverviewTest {
    private static final String FUNCTION = "/common/nifiRuntimeOverview";
    private static final Path SOURCE = Path.of("../magic/function/01.通用函数/32.NiFi流程运行总览.ms");

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
            verify(fixture.magic).invoke(FUNCTION, Map.of("sortField", "taskName", "sortDir", "asc"));

            var byRun = fixture.overview("latestRunTime", "desc");
            assertThat(rows(byRun)).extracting(row -> row.get("id"))
                    .containsExactly("id-" + (count - 1), "id-" + (count - 2), "id-" + (count - 3),
                            "id-" + (count - 4), "id-" + (count - 5));
            assertThat(rows(byRun).getFirst().get("latestRunTime")).isEqualTo((long) count - 1);
            verifyNoInteractions(fixture.nifi, fixture.repository);
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
        verify(fixture.magic).invoke(FUNCTION, Map.of());
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
        assertThat(TenantContext.call("tenant-b", () -> fixture.controller.runtimeOverview(null, null)))
                .containsExactlyInAnyOrderEntriesOf(Map.of("total", 0L, "running", 0L, "failed", 0L, "flows", List.of()));

        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller).build();
        try (var tenant = TenantContext.use("tenant-a")) {
            mvc.perform(get("/nifi/api/pipelines/runtime-overview").param("tenantId", "tenant-b"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"total":1,"running":1,"failed":0,"flows":[{"id":"a","taskName":"a",
                              "currentStatus":"RUNNING","processGroupId":null,"latestRunTime":15,"endTime":null}]}
                            """, true));
        }
    }

    @Test void missingTenantAndUnsupportedSortFailBeforeAnyQuery() throws Exception {
        var fixture = new Fixture();
        assertThatThrownBy(() -> fixture.controller.runtimeOverview(null, null)).hasMessageContaining("租户");
        assertThatThrownBy(() -> fixture.runFunction(Map.of())).hasMessageContaining("租户");
        TenantContext.run("tenant-a", () -> {
            assertThatThrownBy(() -> fixture.controller.runtimeOverview("status", "desc"))
                    .hasMessageContaining("不支持的排序字段：status");
            assertThatThrownBy(() -> fixture.controller.runtimeOverview("updatedAt", "sideways"))
                    .hasMessageContaining("排序方向只能是 asc 或 desc");
        });
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/nifi/api/pipelines/runtime-overview"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("当前请求未绑定租户"));
        try (var tenant = TenantContext.use("tenant-a")) {
            mvc.perform(get("/nifi/api/pipelines/runtime-overview").param("sortField", "status"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(StatusCodeEnum.ERROR.getCode()))
                    .andExpect(jsonPath("$.msg").value("不支持的排序字段：status"));
            mvc.perform(get("/nifi/api/pipelines/runtime-overview").param("sortDir", "sideways"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(StatusCodeEnum.ERROR.getCode()))
                    .andExpect(jsonPath("$.msg").value("排序方向只能是 asc 或 desc"));
        }
        assertThat(fixture.reads).isZero();
    }

    private static List<Map<String, Object>> rows(Map<String, Object> result) {
        return StructuredValues.objectRows(result.get("flows"));
    }

    private static List<Object> ids(Map<String, Object> result) {
        return rows(result).stream().map(row -> row.get("id")).toList();
    }

    private static final class Fixture {
        final PipelineRepository repository = mock(PipelineRepository.class);
        final NifiClient nifi = mock(NifiClient.class);
        final MagicAPIService magic = mock(MagicAPIService.class);
        final JdbcTemplate jdbc;
        final PipelineController controller;
        final StructuredTableReadMagicModule tableRead;
        final MagicScript script;
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
                                if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
                                        && sql.stripLeading().toLowerCase(Locale.ROOT).startsWith("select")) reads++;
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
                        last_deployed_at bigint, last_stopped_at bigint, updated_at bigint)
                    """);
            tableRead = new StructuredTableReadMagicModule(jdbc);
            String body = Files.readString(SOURCE).split("================================", 2)[1];
            script = MagicScript.create(body.replaceAll("(?m)^import (tenantRuntime|tableRead|tableSort)[^\\r\\n]*\\r?\\n", ""), null);
            when(magic.invoke(eq(FUNCTION), anyMap())).thenAnswer(call -> runFunction(call.getArgument(1)));
            controller = new PipelineController(repository, nifi, magic, null, jdbc, null, null, null, null);
        }

        void flow(String id, String name, String status, String tenant, int deleted, Long deployed, Long updated) {
            jdbc.update("INSERT INTO nifi_pipeline_t VALUES (?,?,?,?,?,NULL,?,NULL,?)",
                    id, name, status, tenant, deleted, deployed, updated);
        }

        Object runFunction(Map<String, Object> arguments) {
            var bindings = new HashMap<>(arguments);
            bindings.put("tenantRuntime", new TenantMagicModule(null));
            bindings.put("tableRead", tableRead);
            bindings.put("tableSort", new TableSortMagicModule());
            return script.execute(new MagicScriptContext(bindings));
        }

        Map<String, Object> overview(String field, String direction) {
            reads = 0;
            var result = TenantContext.call("tenant-a", () -> controller.runtimeOverview(field, direction));
            assertThat(reads).isEqualTo(2);
            return result;
        }
    }
}
