package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.errors.ErrorService;
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowError;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiMutationAspect;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException;
import com.linewell.dataelement.integration.nifi.canvas.runtime.TaskRuntimeStatusService;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
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

class NifiFlowErrorMagicTest {
    private static final String ROOT = "api/14.NiFi流程接口/01.接口/";
    private static final String PIPELINES = ROOT + "01.流程管理/";
    private static final String FLOWS = ROOT + "08.流程错误/";
    private static final String HELPER = "function/01.通用函数/45.NiFi流程错误接口响应.ms";
    private static final String DSL = "{\"version\":1,\"nodes\":[],\"edges\":[]}";
    private static final String TENANT = "tenant-a";
    private static final ObjectMapper JSON = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void historyUsesOneScopedLookupAndOneStoreCallRegardlessOfRowCount(int size) throws Exception {
        var f = new Fixture();
        f.pipeline("flow-a", TENANT, 0, DSL, null, null);
        var event = new FlowError("event", FlowError.ErrorLevel.ERROR, FlowError.ErrorPhase.RUNTIME,
                null, null, null, "validation alert", null, null, Instant.parse("2026-10-02T00:00:00Z"));
        when(f.errors.list("flow-a")).thenReturn(Collections.nCopies(size, event));

        assertThat(f.invoke(f.history, "flow-a").getBody()).isEqualTo(Collections.nCopies(size, event));
        assertThat(f.source.reads).isOne();
        verify(f.errors).list("flow-a");
        verifyNoInteractions(f.nifi, f.control);
    }

    @Test
    void foreignDeletedAndUnreadablePipelinesNeverReachTheErrorStoreOrNifi() throws Exception {
        var f = new Fixture();
        f.pipeline("foreign", "tenant-b", 0, DSL, null, "pg-foreign");
        f.pipeline("deleted", TENANT, 1, DSL, null, "pg-deleted");
        f.pipeline("malformed", TENANT, 0, "{broken", null, "pg-malformed");
        f.pipeline("bad-mapping", TENANT, 0, DSL, "[]", "pg-malformed");
        for (String id : List.of("foreign", "deleted", "malformed", "bad-mapping", "missing")) {
            assertThat(f.invoke(f.history, id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(f.source.reads).isOne();
            assertThat(f.invoke(f.live, id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(f.source.reads).isOne();
        }
        verifyNoInteractions(f.nifi, f.errors, f.control);

        var absent = f.invoke(f.clear, "foreign");
        assertThat(absent.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(absent.getBody()).isNull();
        assertThat(f.source.reads).isOne();
        verifyNoInteractions(f.nifi, f.errors);
        verify(f.cache).invalidateTenant();
        verify(f.lock).prepareStatement("SELECT RELEASE_LOCK(?)");
    }

    @Test
    void nativeBulletinsPreserveFilterDefaultValuesAndOrderWithoutChangingHistory() throws Exception {
        var f = new Fixture();
        f.pipeline("flow-a", TENANT, null, "null", "null", "pg-a");
        f.pipeline("draft", TENANT, 0, DSL, null, " ");
        when(f.nifi.getBulletinBoard("pg-a", 0L)).thenReturn(JSON.readTree("""
                {"bulletinBoard":{"bulletins":[
                  {"id":12,"bulletin":{"level":"INFO","message":"routine"}},
                  {"id":13,"bulletin":{"level":"ERROR","sourceName":"source","message":"failed",
                    "timestamp":"2026-10-02T00:00:00Z"}},
                  {"id":"n-14","bulletin":{"level":"warning","sourceId":"processor","timestampIso":"iso",
                    "timestamp":"fallback"}}
                ]}}
                """));

        var rows = StructuredValues.objectRows(f.invoke(f.live, "flow-a").getBody());
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst()).containsOnlyKeys("id", "level", "sourceId", "sourceName", "message", "timestamp")
                .containsEntry("id", "13").containsEntry("level", "ERROR").containsEntry("sourceId", null)
                .containsEntry("message", "failed").containsEntry("timestamp", "2026-10-02T00:00:00Z");
        assertThat(rows.getLast()).containsEntry("id", "n-14").containsEntry("level", "warning")
                .containsEntry("sourceName", "").containsEntry("message", "").containsEntry("timestamp", "iso");
        assertThat(f.source.reads).isOne();
        var notDeployed = f.invoke(f.live, "draft");
        assertThat(notDeployed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(notDeployed.getBody()).isEqualTo(Map.of("error", "流程尚未部署"));
        verify(f.nifi).getBulletinBoard("pg-a", 0L);
        verifyNoInteractions(f.errors, f.control);
    }

    @Test
    void clearRetainsTheLockAndNodeBindingAcrossFallbackAndSuccess() throws Exception {
        var f = new Fixture();
        f.pipeline("flow-a", TENANT, 0, DSL, null, "pg-a");
        var binding = mock(NifiClient.RuntimeScope.class);
        when(f.nifi.bindRuntimeNode()).thenReturn(binding);
        var upstream = new NifiException("validation upstream unavailable", 403, null);
        var close = new IllegalStateException("validation binding failure");
        when(f.nifi.clearGroupBulletins(eq("pg-a"), any(Instant.class))).thenThrow(upstream).thenReturn(2);
        doThrow(close).doNothing().when(binding).close();

        assertThat(f.invoke(f.clear, "flow-a").getBody()).isEqualTo(
                Map.of("historyCleared", true, "bulletinsCleared", 0, "nativeCleared", false));
        assertThat(upstream.getSuppressed()).containsExactly(close);
        assertThat(f.source.reads).isOne();
        assertThat(f.invoke(f.clearAlias, "flow-a").getBody()).isEqualTo(
                Map.of("historyCleared", true, "bulletinsCleared", 2, "nativeCleared", true));
        assertThat(f.source.reads).isOne();

        var order = inOrder(f.lock, f.nifi, binding, f.errors, f.cache);
        for (int call = 0; call < 2; call++) {
            order.verify(f.lock).prepareStatement("SELECT GET_LOCK(?, 0)");
            order.verify(f.nifi).bindRuntimeNode();
            order.verify(f.nifi).clearGroupBulletins(eq("pg-a"), any(Instant.class));
            order.verify(binding).close();
            order.verify(f.errors).clear("flow-a");
            order.verify(f.cache).invalidateTenant();
            order.verify(f.lock).prepareStatement("SELECT RELEASE_LOCK(?)");
            order.verify(f.lock).close();
        }
    }

    @Test
    void contentionSkipsTheCallbackAndUnexpectedFailureLeavesHistoryIntact() throws Exception {
        var f = new Fixture();
        f.pipeline("flow-a", TENANT, 0, DSL, null, "pg-a");
        when(f.lockStatus.getInt(1)).thenReturn(0);
        var contention = f.invoke(f.clear, "flow-a");
        assertThat(contention.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((CommonResponse<?>) contention.getBody()).getMsg()).contains("409", "正在保存或部署流程");
        assertThat(f.source.reads).isZero();
        verifyNoInteractions(f.nifi, f.errors, f.cache);
        verify(f.lock, never()).prepareStatement("SELECT RELEASE_LOCK(?)");

        when(f.lockStatus.getInt(1)).thenReturn(1);
        var binding = mock(NifiClient.RuntimeScope.class);
        when(f.nifi.bindRuntimeNode()).thenReturn(binding);
        when(f.nifi.clearGroupBulletins(eq("pg-a"), any(Instant.class)))
                .thenThrow(new IllegalStateException("validation failure"));
        var failure = f.invoke(f.clear, "flow-a");
        assertThat(((CommonResponse<?>) failure.getBody()).getMsg()).isEqualTo("validation failure");
        verify(binding).close();
        verifyNoInteractions(f.errors);
        verify(f.cache).invalidateTenant();
        verify(f.lock).prepareStatement("SELECT RELEASE_LOCK(?)");
    }

    @Test
    void headSharesTheHistoryContractAndUndeployedClearDoesNotBindNifi() throws Exception {
        var f = new Fixture();
        f.pipeline("flow-a", TENANT, 0, DSL, null, null);
        when(f.errors.list("flow-a")).thenReturn(List.of());
        assertThat(f.invoke(f.historyHead, "flow-a")).isEqualTo(f.invoke(f.history, "flow-a"));
        assertThat(f.invoke(f.clear, "flow-a").getBody()).isEqualTo(
                Map.of("historyCleared", true, "bulletinsCleared", 0, "nativeCleared", true));
        assertThat(f.source.reads).isOne();
        verify(f.errors).clear("flow-a");
        verifyNoInteractions(f.nifi);
    }

    private static MagicScript script(String path, String id, String... bindings) throws IOException {
        String source = CanonicalMagicSources.byPath(path, id);
        MagicScript.create(source, null).compile();
        for (String binding : bindings) {
            source = source.replaceFirst("(?m)^import " + java.util.regex.Pattern.quote(binding) + "\\R", "");
        }
        return MagicScript.create(source, null);
    }

    private static MagicScript endpoint(String path, String id) throws IOException {
        return script(path, id, "'@/common/nifiFlowErrorHttp' as flowErrors");
    }

    private static final class Fixture {
        final ErrorService errors = mock(ErrorService.class);
        final NifiClient nifi = mock(NifiClient.class);
        final DataSource control = mock(DataSource.class);
        final Connection lock = mock(Connection.class);
        final ResultSet lockStatus = mock(ResultSet.class);
        final TaskRuntimeStatusService cache = mock(TaskRuntimeStatusService.class);
        final NifiMutationAspect mutation = new NifiMutationAspect(control);
        final ReadCountingSource source = new ReadCountingSource();
        final JdbcTemplate jdbc = new JdbcTemplate(source);
        final StructuredTableReadMagicModule tableRead = new StructuredTableReadMagicModule(jdbc);
        final MagicScript helper = script(HELPER, "nifi_flow_error_http_v1", "tableRead", "tenantRuntime",
                "com.fasterxml.jackson.databind.ObjectMapper as objectMapper",
                "com.linewell.dataelement.integration.nifi.canvas.errors.ErrorService as errorStore",
                "com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiMutationAspect as mutation",
                "com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi");
        final MagicScript history = endpoint(PIPELINES + "12.查询流程错误历史.ms", "nifi_pipe_error_history_get_v1");
        final MagicScript historyHead = endpoint(PIPELINES + "13.核验流程错误历史.ms", "nifi_pipe_error_history_head_v1");
        final MagicScript live = endpoint(PIPELINES + "14.查询流程实时公告.ms", "nifi_pipe_error_bulletin_get_v1");
        final MagicScript clear = endpoint(PIPELINES + "16.清理流程错误.ms", "nifi_pipe_error_clear_post_v1");
        final MagicScript clearAlias = endpoint(FLOWS + "05.清理流程错误.ms", "nifi_flow_error_clear_post_v1");

        Fixture() throws Exception {
            var statement = mock(PreparedStatement.class);
            var metadata = mock(DatabaseMetaData.class);
            when(control.getConnection()).thenReturn(lock);
            when(lock.getMetaData()).thenReturn(metadata);
            when(metadata.getURL()).thenReturn("jdbc:mysql://loopback/baseline");
            when(lock.prepareStatement(any(String.class))).thenReturn(statement);
            when(statement.executeQuery()).thenReturn(lockStatus);
            when(lockStatus.next()).thenReturn(true);
            when(lockStatus.getInt(1)).thenReturn(1);
            mutation.setRuntimeStatus(cache);
            jdbc.execute("CREATE TABLE nifi_pipeline_t (id VARCHAR(64), tenant_id VARCHAR(64), is_del INT,"
                    + " dsl_json CLOB, node_mapping_json CLOB, nifi_process_group_id VARCHAR(64))");
        }

        void pipeline(String id, String tenant, Integer deleted, String dsl, String mapping, String group) {
            jdbc.update("INSERT INTO nifi_pipeline_t VALUES(?,?,?,?,?,?)", id, tenant, deleted, dsl, mapping, group);
        }

        ResponseEntity<?> invoke(MagicScript endpoint, String id) {
            source.reads = 0;
            MagicScriptLambdaFunction flowErrors = (runtime, args) -> helper.execute(new MagicScriptContext(Map.of(
                    "flowId", args[0], "operation", args[1], "tableRead", tableRead,
                    "tenantRuntime", new TenantMagicModule(null), "objectMapper", JSON,
                    "errorStore", errors, "nifi", nifi, "mutation", mutation)));
            return TenantContext.call(TENANT, () -> (ResponseEntity<?>) endpoint.execute(new MagicScriptContext(
                    Map.of("path", Map.of("id", id), "flowErrors", flowErrors))));
        }
    }

    private static final class ReadCountingSource extends DriverManagerDataSource {
        int reads;

        ReadCountingSource() {
            super("jdbc:h2:mem:nifi_flow_errors_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("prepareStatement") && arguments[0] instanceof String sql
                                && sql.stripLeading().regionMatches(true, 0, "SELECT", 0, 6)) {
                            reads++;
                        }
                        try {
                            return method.invoke(connection, arguments);
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
        }
    }
}
