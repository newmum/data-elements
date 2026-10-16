package com.linewell.dataelement.feature.diagnostics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.diagnostics.DiagnosticModels.Selection;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService.*;
import com.linewell.dataelement.integration.nifi.canvas.errors.*;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.*;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;

class NifiDiagnosticEvidenceTest {
    @TempDir Path root;
    private final ObjectMapper json = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    private final PipelineRepository pipelines = mock(PipelineRepository.class);
    private final NifiClient nifi = mock(NifiClient.class);
    private final DataScopeAuthorizationService scopes = mock(DataScopeAuthorizationService.class, CALLS_REAL_METHODS);
    private final AtomicInteger selects = new AtomicInteger();
    private JdbcTemplate jdbc;
    private ErrorService errors;
    private NifiDiagnosticEvidence evidence;
    @BeforeEach void setup() throws Exception {
        TenantContext.bind("tenant-a");
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        jdbc = new JdbcTemplate(new AbstractDataSource() {
            public Connection getConnection() throws SQLException { return counted(DriverManager.getConnection(url)); }
            public Connection getConnection(String user, String password) throws SQLException { return getConnection(); }
        });
        jdbc.execute("create table db_datasource_t(tid varchar primary key, tenant_id varchar, created_by varchar, org_id varchar, is_del int)");
        jdbc.execute("create table db_table_t(tid varchar primary key, tenant_id varchar, datasource_id varchar, is_del int)");
        errors = new ErrorService(root.toString(), json);
        errors.add("flow", new FlowError("error-1", FlowError.ErrorLevel.ERROR, FlowError.ErrorPhase.RUNTIME, "node-1", "node", null, "connection refused", null, null, Instant.now()));
        doReturn(new ScopeDecision("MENU:test", Scope.OWNER, "alice", List.of(), List.of(), true)).when(scopes).currentScope(anyString());
        evidence = new NifiDiagnosticEvidence(pipelines, errors, jdbc, scopes, nifi, json);
        when(nifi.getBulletinBoard(anyString(), anyLong())).thenReturn(json.readTree("""
                {"bulletinBoard":{"bulletins":[{"bulletin":{"groupId":"foreign","sourceId":"foreign","message":"foreign-secret"}},
                {"bulletin":{"groupId":"pg","sourceId":"proc","message":"own-error record={business-person}"}}]}}
                """));
        when(nifi.getProcessGroupStatus("pg")).thenReturn(json.readTree("{\"processGroupStatus\":{\"aggregateSnapshot\":{\"id\":\"pg\",\"queuedCount\":3}}}"));
        when(nifi.getProcessor("proc")).thenReturn(json.readTree("{\"component\":{\"parentGroupId\":\"pg\",\"name\":\"selected\",\"properties\":{\"password\":\"never-send\"}}}"));
        when(nifi.listControllerServices("pg")).thenReturn(List.of());
    }
    @AfterEach void clear() { TenantContext.clear(); }
    private Connection counted(Connection connection) {
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class[]{Connection.class}, (proxy, method, args) -> {
            if (method.getName().equals("prepareStatement") && ((String) args[0]).startsWith("select")) selects.incrementAndGet();
            try { return method.invoke(connection, args); } catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
        });
    }
    private void fixture(int size) {
        List<Pipeline.Node> nodes = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            jdbc.update("insert into db_datasource_t values(?, 'tenant-a','alice','org',0)", "source-" + i);
            jdbc.update("insert into db_table_t values(?, 'tenant-a',?,0)", "table-" + i, "source-" + i);
            nodes.add(new Pipeline.Node("node-" + i, "mysql", "node", "source", 0, 0, Map.of("registeredDatasourceId", "source-" + i, "sourceTableId", "table-" + i)));
        }
        Pipeline pipeline = new Pipeline("flow", "flow", null, null, null, new Pipeline.Dsl(1, nodes, List.of()), "pg", PipelineStatus.RUN_ERROR, "version", 1L, null,
                new NifiNodeMapping(Map.of("node-1", "proc"), Map.of("proc", "node-1"), Map.of()), null);
        when(pipelines.findById("flow")).thenReturn(Optional.of(pipeline));
        selects.set(0);
    }
    private Selection selection() { return new Selection("nifi", null, "flow", "error-1", null); }
    @ParameterizedTest @ValueSource(ints = {20, 100})
    void twentyAndHundredResourcesUseTwoBatchSelectsAndFourNifiReads(int size) {
        fixture(size);
        var authorized = evidence.authorize(selection());
        var facts = evidence.collect(authorized);
        assertThat(selects.get()).isEqualTo(2);
        verify(pipelines, times(1)).findById("flow");
        verify(scopes, times(1)).currentScope(anyString());
        verify(nifi).getBulletinBoard("pg", 0L); verify(nifi).getProcessGroupStatus("pg"); verify(nifi).getProcessor("proc"); verify(nifi).listControllerServices("pg");
        verifyNoMoreInteractions(nifi);
        assertThat(facts.toString()).contains("own-error").doesNotContain("foreign-secret", "never-send", "business-person");
    }
    @Test void mixedUnauthorizedResourceDeniesWholeSelectionBeforeExternalReads() {
        fixture(100); jdbc.update("update db_datasource_t set created_by='bob' where tid='source-99'"); selects.set(0);
        assertThatThrownBy(() -> evidence.authorize(selection())).hasMessageContaining("403");
        assertThat(selects.get()).isEqualTo(2); verifyNoInteractions(nifi);
    }
    @Test void crossTenantTableCannotBeResolved() {
        fixture(20); jdbc.update("update db_table_t set tenant_id='tenant-b' where tid='table-19'"); selects.set(0);
        assertThatThrownBy(() -> evidence.authorize(selection())).hasMessageContaining("403");
        assertThat(selects.get()).isEqualTo(1); verifyNoInteractions(nifi);
    }
    @Test void offlineNifiKeepsHistoricalErrorAndMarksMissingEvidence() {
        fixture(20); when(nifi.getProcessGroupStatus("pg")).thenThrow(new IllegalStateException("private connection details"));
        assertThat(evidence.collect(evidence.authorize(selection())).toString()).contains("未取得该项证据", "connection refused").doesNotContain("private connection details");
    }
}
