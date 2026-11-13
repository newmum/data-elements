package com.linewell.dataelement.feature.logmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.audit.*;
import com.linewell.dataelement.platform.persistence.jdbc.StructuredContainsPredicateAdapter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Magic rules, real H2 filtering/pagination, and executed SQL/timeout evidence (not mock row filtering). */
class LogParameterQueryIntegrationTest {
    static final LogScope OWNER = scope("OWNER", List.of());
    static final Map<String,Object> RANGE = Map.of("startTime", "2026-11-01T00:00:00Z", "endTime", "2026-11-02T00:00:00Z");
    static LogScope scope(String mode, List<String> orgs) {
        return new LogScope("validation-tenant", "validation-user", "validation-app", null, null, mode, orgs, "operations");
    }
    static Map<String,Object> input(Object... pairs) {
        var input = new LinkedHashMap<>(RANGE);
        for (int index = 0; index < pairs.length; index += 2) input.put((String) pairs[index], pairs[index + 1]);
        return input;
    }

    @Test void twentyAndHundredRowsUseExactlyTwoEventSqlsForEachStoreAndFilterBeforePagination() {
        var fixture = new Fixture();
        for (String kind : List.of("operations", "requests")) {
            String table = fixture.table(kind);
            for (int index = 0; index < 120; index++) {
                fixture.insert(table, "match-%03d".formatted(index), "validation-tenant", "validation-user", "org-1",
                        "body.label=MiXeD中文 %_!\n", fixture.snapshot(Map.of("label", "MiXeD中文 %_!")), "{}");
            }
            for (int index = 0; index < 120; index++) {
                fixture.insert(table, "aaa-no-match-%03d".formatted(index), "validation-tenant", "validation-user", "org-1",
                        "body.label=other\n", null, "{}");
            }
            for (int size : List.of(20, 100)) {
                fixture.source.reset();
                clearInvocations(fixture.magic.getObject());
                var page = fixture.query.page(OWNER, kind, input("parameterKeyword", "mixed中文 %_!", "size", size));
                assertEquals(120L, page.get("total"));
                var rows = rows(page);
                assertEquals(size, rows.size());
                assertEquals("match-000", rows.getFirst().get("tid"));
                assertTrue(rows.stream().allMatch(row -> "CAPTURED".equals(row.get("parameter_capture_status"))
                        && !row.containsKey("parameter_snapshot") && !row.containsKey("parameter_search_text") && !row.containsKey("details")));
                assertEquals(2, fixture.source.sql.size(), fixture.source.sql.toString());
                assertEquals(List.of(15, 15), fixture.source.timeouts);
                assertTrue(fixture.source.sql.stream().allMatch(sql -> sql.contains("tenant_id=?") && sql.contains("user_id=?")
                        && sql.toLowerCase(Locale.ROOT).contains("instr(") && !sql.contains("mixed中文 %_!")));
                verify(fixture.magic.getObject()).invoke(eq(LogQueryService.PARAMETER_CONDITION_FUNCTION), anyMap());
            }
        }
    }

    @Test void nestedFalseZeroNullAndChineseSearchAreLiteralAndCombinedWithOrdinaryKeyword() {
        var fixture = new Fixture();
        var filters = new LinkedHashMap<String,Object>();
        filters.put("enabled", false); filters.put("count", 0); filters.put("optional", null);
        filters.put("items", Arrays.asList("中文 A%_!.*[x]", null));
        String snapshot = fixture.snapshot(Map.of("filters", filters));
        String projection = fixture.projection(Map.of("filters", filters));
        fixture.insert("log_system_t", "allowed", "validation-tenant", "validation-user", "org-1", projection, snapshot, "{}");
        fixture.insert("log_system_t", "not-literal", "validation-tenant", "validation-user", "org-1", "body.filters.items[0]=中文 AXabc.*x\n", null, "{}");
        for (String needle : List.of("body.filters.enabled=false", "body.filters.count=0", "body.filters.optional=null",
                "body.filters.items[1]=null", "中文 a%_!.*[x]", "a%_!", ".*[x]")) {
            assertEquals(List.of("allowed"), ids(fixture.query.page(OWNER, "operations", input("parameterKeyword", needle))));
        }
        assertEquals(List.of("allowed"), ids(fixture.query.page(OWNER, "operations", input("keyword", "保存", "parameterKeyword", "中文 A%_!"))));
        assertTrue(ids(fixture.query.page(OWNER, "operations", input("keyword", "不相关菜单", "parameterKeyword", "中文"))).isEmpty());
        assertTrue(ids(fixture.query.page(OWNER, "operations", input("parameterKeyword", "中文 false"))).isEmpty(), "not token OR search");
        assertTrue(ids(fixture.query.page(OWNER, "operations", input("parameterKeyword", " 中文 A%_! "))).isEmpty(), "spaces are literal");
        var row = fixture.query.detail(OWNER, "operations", "allowed");
        var body = (Map<?,?>) ((Map<?,?>) row.get("parameter_snapshot")).get("body");
        var actual = (Map<?,?>) body.get("filters");
        assertEquals(false, actual.get("enabled")); assertEquals(0, actual.get("count"));
        assertTrue(actual.containsKey("optional")); assertNull(actual.get("optional"));
    }

    @Test void selectionKeepsOwnerOrgAndTenantScopesAndRejectsMixedIdsInOneRead() {
        var fixture = new Fixture();
        for (var identity : List.of(
                List.of("allowed", "validation-tenant", "validation-user", "org-1"),
                List.of("other-owner", "validation-tenant", "other-user", "org-1"),
                List.of("other-org", "validation-tenant", "validation-user", "org-2"),
                List.of("other-tenant", "other-tenant", "validation-user", "org-1"))) {
            fixture.insert("log_system_t", identity.get(0), identity.get(1), identity.get(2), identity.get(3), "body.label=验证\n", null, "{}");
        }
        var hostile = input("parameterKeyword", "验证", "tenantId", "other-tenant", "scope", "ALL", "orgIds", List.of("org-2"));
        assertEquals(List.of("allowed", "other-org"), ids(fixture.query.page(OWNER, "operations", hostile)));
        var org = scope("ORG", List.of("org-1"));
        assertEquals(List.of("allowed", "other-owner"), ids(fixture.query.page(org, "operations", hostile)));
        assertTrue(ids(fixture.query.page(org, "operations", input("parameterKeyword", "验证", "orgId", "org-2"))).isEmpty());
        assertTrue(ids(fixture.query.page(scope("ORG", List.of()), "operations", hostile)).isEmpty());
        var combined = scope("OWNER,ORG", List.of("org-1"));
        assertEquals(List.of("allowed"), ids(fixture.query.page(combined, "operations", hostile)));
        fixture.source.reset();
        assertThrows(SecurityException.class, () -> fixture.query.authorizedEvents(combined, "operations", List.of("allowed", "other-owner"), true));
        assertEquals(1, fixture.source.sql.size());
        fixture.source.reset();
        assertThrows(SecurityException.class, () -> fixture.query.authorizedEvents(OWNER, "operations", List.of("allowed", "other-tenant"), true));
        assertEquals(1, fixture.source.sql.size());
        var context = ArgumentCaptor.forClass(Map.class);
        verify(fixture.magic.getObject(), atLeastOnce()).invoke(eq(LogQueryService.PARAMETER_CONDITION_FUNCTION), context.capture());
        assertTrue(context.getAllValues().stream().allMatch(value -> value.keySet().equals(Set.of("tenantId", "kind", "keyword"))
                && "validation-tenant".equals(value.get("tenantId"))));
    }

    @Test void twentyAndHundredBatchIdsKeepInputOrderAndCurrentScopesInOneSql() {
        var fixture = new Fixture();
        var ids = new ArrayList<String>();
        for (int index = 0; index < 100; index++) {
            String id = "validation-batch-%03d".formatted(index);
            ids.add(id);
            fixture.insert("log_system_t", id, "validation-tenant", "validation-user", "org-1", null, null, "{}");
        }
        // ID-authorized access to historical events must not regain the default seven-day list window.
        fixture.setup.update("update log_system_t set event_time=timestamp '2000-01-01 01:00:00'");
        for (LogScope scope : List.of(OWNER, scope("ORG", List.of("org-1")), scope("ALL", List.of()))) {
            for (int size : List.of(20, 100)) {
                var requested = new ArrayList<>(ids.subList(0, size));
                Collections.reverse(requested);
                for (boolean detail : List.of(false, true)) {
                    fixture.source.reset();
                    var events = fixture.query.authorizedEvents(scope, "operations", requested, detail);
                    assertEquals(requested, events.stream().map(event -> event.get("tid")).toList());
                    assertEquals(1, fixture.source.sql.size(), fixture.source.sql.toString());
                    assertEquals(List.of(15), fixture.source.timeouts);
                    String sql = fixture.source.sql.getFirst();
                    assertTrue(sql.contains("tenant_id=?") && sql.contains("tid in ("));
                    assertFalse(sql.contains("event_time>=") || sql.contains("created_time>=") || sql.contains("limit"));
                    if (scope.mode().equals("OWNER")) assertTrue(sql.contains("user_id=?"));
                    if (scope.mode().equals("ORG")) assertTrue(sql.contains("org_id in ("));
                }
            }
        }
    }

    @Test void countStatisticsAndExportReadUseTheSameSelectionAndOldExactFilterStillWorks() {
        var fixture = new Fixture();
        String exact = AuditRequestParameters.exactMatchPattern("deletedTaskName", "验证_%任务").replace("%", "");
        fixture.insert("log_system_t", "allowed", "validation-tenant", "validation-user", "org-1", "verified.deletedTaskName=验证_%任务\n",
                fixture.snapshot(Map.of("label", "验证_%任务")), "{\"requestParameterIndex\":{" + exact + "}}");
        fixture.insert("log_system_t", "no-exact", "validation-tenant", "validation-user", "org-1", "body.label=验证_%任务\n", null, "{}");
        var request = input("keyword", "保存", "parameterKeyword", "验证_%任务", "parameterName", "deletedTaskName", "parameterValue", "验证_%任务");
        assertEquals(List.of("allowed"), ids(fixture.query.page(OWNER, "operations", request)));
        assertEquals(1L, ((Number) fixture.query.statistics(OWNER, "operations", request).get("total")).longValue());
        var selection = fixture.query.selection(OWNER, "operations", request);
        var parameters = new LinkedHashMap<>(selection.params()); parameters.put("size", 500); parameters.put("offset", 0);
        assertEquals(List.of("allowed"), fixture.query.read(OWNER, selection, parameters, "event_time asc,tid asc", true)
                .stream().map(row -> (String) row.get("tid")).toList());
        assertTrue(selection.where().contains("detail_json like :auditParameter"));
        assertTrue(selection.where().contains(" and ("));
    }

    @Test void detailsResanitizeSnapshotsMarkLegacyAndNeverInferResultOrExposeOldBodies() throws Exception {
        var fixture = new Fixture();
        String unsafe = "{\"version\":1,\"status\":\"CAPTURED\",\"path\":{},\"query\":{},\"body\":{\"password\":\"validation-secret\",\"dryRun\":true,\"result\":\"FAILED\"},\"verified\":{},\"notices\":[]}";
        fixture.insert("log_system_t", "new", "validation-tenant", "validation-user", "org-1", "", unsafe, "{}");
        fixture.insert("log_system_t", "legacy", "validation-tenant", "validation-user", "org-1", null, null,
                "{\"body\":{\"name\":\"raw-history-body\"},\"requestParameters\":{\"taskName\":\"旧安全任务\",\"password\":\"old-secret\"}}");
        fixture.insert("log_system_t", "broken", "validation-tenant", "validation-user", "org-1", "", "not-json raw-body", "{}");
        var current = fixture.query.detail(OWNER, "operations", "new");
        assertEquals("SUCCESS", current.get("result"));
        String json = new ObjectMapper().writeValueAsString(current);
        assertFalse(json.contains("validation-secret")); assertTrue(current.get("parameter_snapshot") instanceof Map);
        assertEquals(((Map<?,?>) current.get("parameter_snapshot")).get("status"), current.get("parameter_capture_status"));
        var legacy = fixture.query.detail(OWNER, "operations", "legacy");
        assertEquals("LEGACY", legacy.get("parameter_capture_status"));
        assertEquals("LEGACY", ((Map<?,?>) legacy.get("parameter_snapshot")).get("status"));
        assertEquals(Map.of("taskName", "旧安全任务"), legacy.get("request_parameters"));
        String old = new ObjectMapper().writeValueAsString(legacy);
        assertFalse(old.contains("raw-history-body")); assertFalse(old.contains("old-secret"));
        assertEquals("FAILED", fixture.query.detail(OWNER, "operations", "broken").get("parameter_capture_status"));
    }

    @Test void idaasAndOtherKindsAreRejectedBeforeSqlAndTimeProtectionIsUnchanged() {
        var fixture = new Fixture();
        var platform = new LogScope(null, "operator", null, "workforce", null, "ALL", List.of(), "operations");
        for (String kind : List.of("operations", "requests")) {
            assertThrows(RuntimeException.class, () -> fixture.query.selection(platform, kind, input("parameterKeyword", "验证")));
        }
        for (String kind : List.of("auth", "security")) {
            assertThrows(RuntimeException.class, () -> fixture.query.selection(OWNER, kind, input("parameterKeyword", "验证")));
        }
        assertThrows(RuntimeException.class, () -> fixture.query.page(OWNER, "operations", input("parameterKeyword", "x".repeat(201))));
        assertEquals(0, fixture.source.sql.size());
        var defaults = fixture.query.selection(OWNER, "operations", Map.of());
        assertEquals(java.time.Duration.ofDays(7), java.time.Duration.between(
                LogTimes.instant(defaults.params().get("start")), LogTimes.instant(defaults.params().get("end"))));
        assertDoesNotThrow(() -> fixture.query.selection(OWNER, "operations", input("endTime", "2027-01-30T00:00:00Z")));
        assertThrows(IllegalArgumentException.class, () -> fixture.query.selection(OWNER, "operations", input("endTime", "2027-01-31T00:00:00Z")));
    }

    @SuppressWarnings("unchecked")
    static List<Map<String,Object>> rows(Map<String,Object> page) { return (List<Map<String,Object>>) page.get("list"); }
    static List<String> ids(Map<String,Object> page) { return rows(page).stream().map(row -> (String) row.get("tid")).toList(); }

    static final class Fixture {
        final AuditStore store = mock(AuditStore.class);
        final org.springframework.beans.factory.ObjectProvider<org.ssssssss.magicapi.core.service.MagicAPIService> magic = LogParameterMagicFixture.provider();
        final DriverManagerDataSource raw = new DriverManagerDataSource("jdbc:h2:mem:validation-parameter-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        final CountedSource source = new CountedSource(raw);
        final JdbcTemplate setup = new JdbcTemplate(raw);
        final LogQueryService query = new LogQueryService(store, new AuditSanitizer(new ObjectMapper()), magic, new StructuredContainsPredicateAdapter());

        Fixture() {
            when(store.dataSource("validation-tenant")).thenReturn(source);
            when(store.named("validation-tenant")).thenAnswer(call -> new NamedParameterJdbcTemplate(source));
            for (String table : List.of("log_system_t", "log_request_t")) {
                String fields = "tid,tenant_id,user_id,user_account,user_name,account,full_name,org_id,org_name,category,actor_kind,menu_id,menu_path,page_route,operation_name,function_code,action_code,target_type,target_id,target_name,result,reason_code,ip,user_agent,trace_id,request_id,session_ref,business_code,source_app,batch_id,task_id,route_path,parameter_capture_status";
                setup.execute("create table " + table + "(" + Arrays.stream(fields.split(",")).map(name -> name + " varchar(256)").collect(java.util.stream.Collectors.joining(","))
                        + ",event_time timestamp,created_time timestamp,received_time timestamp,duration_ms bigint,http_status integer,total_count bigint,success_count bigint,failure_count bigint,detail_json clob,parameter_snapshot clob,parameter_search_text clob)");
            }
        }
        String table(String kind) { return kind.equals("requests") ? "log_request_t" : "log_system_t"; }
        AuditParameterSnapshot capture(Object body) {
            var request = new MockHttpServletRequest("POST", "/validation/parameters");
            AuditRequestParameters.capture(request, Map.of(), Map.of(), body);
            return AuditRequestParameters.safeSnapshot(request);
        }
        String snapshot(Object body) { return capture(body).json(); }
        String projection(Object body) { return capture(body).searchText(); }
        void insert(String table, String id, String tenant, String user, String org, String projection, String snapshot, String details) {
            setup.update("insert into " + table + "(tid,tenant_id,user_id,org_id,category,event_time,operation_name,result,parameter_search_text,parameter_snapshot,parameter_capture_status,detail_json)"
                    + " values(?,?,?,?,'OPERATION',timestamp '2026-11-01 01:00:00','保存配置','SUCCESS',?,?,?,?)",
                    id, tenant, user, org, projection, snapshot, snapshot == null ? null : "CAPTURED", details);
        }
    }

    static final class CountedSource extends AbstractDataSource {
        final DataSource delegate;
        final List<String> sql = new ArrayList<>();
        final List<Integer> timeouts = new ArrayList<>();
        CountedSource(DataSource delegate) { this.delegate = delegate; }
        void reset() { sql.clear(); timeouts.clear(); }
        @Override public Connection getConnection() throws java.sql.SQLException { return wrap(delegate.getConnection()); }
        @Override public Connection getConnection(String user, String password) throws java.sql.SQLException { return wrap(delegate.getConnection(user, password)); }
        private Connection wrap(Connection connection) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                Object result = call(connection, method, args);
                if (method.getName().equals("prepareStatement")) {
                    String statement = (String) args[0];
                    return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class}, (p, action, values) -> {
                        if (action.getName().equals("executeQuery")) sql.add(statement);
                        if (action.getName().equals("setQueryTimeout")) timeouts.add((Integer) values[0]);
                        return call(result, action, values);
                    });
                }
                return result;
            });
        }
        private Object call(Object target, Method method, Object[] args) throws Throwable {
            try { return method.invoke(target, args); }
            catch (InvocationTargetException error) { throw error.getCause(); }
        }
    }
}
