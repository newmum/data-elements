package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.feature.logmanagement.LogAccessService;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.linewell.dataelement.magic.runtime.route.RegisteredMagicFunctionInvoker;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

class TenantLogCapabilitiesMagicTest {
    private static final String TENANT = "validation-tenant";
    private static final String APPLICATION = "validation-app";
    private static final String ACTOR = "validation-user";
    private static final String API = "api/07.系统管理/18.日志管理/";
    private static final String GRANTS = "function/01.通用函数/67.读取行业日志授权.ms";

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void readsMenuGrantsAndAncestorsOnceAndRejectsInvalidScopes(int menuCount) throws Exception {
        AtomicInteger sql = new AtomicInteger();
        var jdbc = database(sql);
        addAccount(jdbc, TENANT, ACTOR, 0, 0);
        addMenu(jdbc, TENANT, APPLICATION, "root", "ROOT", "navigation", 0);
        addMenu(jdbc, TENANT, APPLICATION, "disabled", "ROOT", "navigation", 1);
        addMenu(jdbc, TENANT, APPLICATION, "cycle-a", "cycle-b", "navigation", 0);
        addMenu(jdbc, TENANT, APPLICATION, "cycle-b", "cycle-a", "navigation", 0);
        grant(jdbc, TENANT, APPLICATION, ACTOR, "auth", "root", "auth", "OWNER");
        grant(jdbc, TENANT, APPLICATION, ACTOR, "requests", "root", "requests", "ORG");
        grant(jdbc, TENANT, APPLICATION, ACTOR, "exports", "root", "exports", "ALL");
        grant(jdbc, TENANT, APPLICATION, ACTOR, "archive", "root", "archives", "ORG");
        grant(jdbc, TENANT, APPLICATION, ACTOR, "policy", "root", "policies", "ALL");
        grant(jdbc, TENANT, APPLICATION, ACTOR, "security", "disabled", "security", "ALL");
        grant(jdbc, TENANT, APPLICATION, ACTOR, "cycle", "cycle-a", "operations", "ALL");
        grant(jdbc, TENANT, APPLICATION, ACTOR, "missing", "absent-parent", "security", "ALL");
        grant(jdbc, TENANT, "other-application", ACTOR, "foreign-app", "ROOT", "operations", "ALL");
        grant(jdbc, "other-tenant", APPLICATION, ACTOR, "foreign-tenant", "ROOT", "archives", "ALL");
        grant(jdbc, TENANT, APPLICATION, "other-user", "foreign-actor", "ROOT", "archives", "ALL");
        grant(jdbc, TENANT, APPLICATION, ACTOR, "valid-partial", "root", "archives", "OWNER");
        grant(jdbc, TENANT, APPLICATION, ACTOR, "disabled-full", "disabled", "archives", "ALL");
        int existingMenus = jdbc.queryForObject("select count(*) from rm_menu_t where tenant_id=? and app_id=?",
                Integer.class, TENANT, APPLICATION);
        for (int index = existingMenus; index < menuCount; index++) {
            addMenu(jdbc, TENANT, APPLICATION, "nav-" + index, "root", "navigation-" + index, 0);
        }

        String script = CanonicalMagicSources.byPath("function/01.通用函数/66.计算当前行业日志能力.ms",
                "log_tenant_capabilities_v1");
        String grantScript = CanonicalMagicSources.byPath(GRANTS, "log_tenant_grants_v1");
        var session = mock(SaSession.class);
        when(session.get("appId")).thenReturn(APPLICATION);
        var input = Map.<String, Object>of("logGrants", (MagicScriptLambdaFunction) (context, values) ->
                grants(grantScript, jdbc, (String) values[0], (String) values[1], (String) values[2]));
        try (var context = TenantContext.use(TENANT); var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn(ACTOR);
            stp.when(StpUtil::getTokenSession).thenReturn(session);
            sql.set(0);
            var response = (List<?>) execute(script, input);
            assertThat(response.stream().map(Object::toString).toList())
                    .containsExactlyInAnyOrder("auth", "requests", "exports", "policies", "requestParameters");
            assertThat(sql.get()).isEqualTo(2);
        }
        System.out.printf("Log capabilities %d menu fixture: 1 request, 2 SELECTs%n", menuCount);
    }

    @Test
    void workerRechecksTheCurrentAccountAndReadsDistinctOrganizationsInTheTaskTenant() throws Exception {
        AtomicInteger sql = new AtomicInteger();
        var jdbc = database(sql);
        addAccount(jdbc, TENANT, ACTOR, 0, 0);
        addAccount(jdbc, "other-tenant", ACTOR, 0, 0);
        grant(jdbc, TENANT, APPLICATION, ACTOR, "requests-org", "ROOT", "requests", "ORG");
        grant(jdbc, TENANT, APPLICATION, ACTOR, "requests-owner", "ROOT", "requests", "OWNER");
        grant(jdbc, "other-tenant", APPLICATION, ACTOR, "requests-all", "ROOT", "requests", "ALL");
        jdbc.update("insert into rm_org_t values (?, 'active-org', 0), (?, 'deleted-org', 1), (?, 'other-org', 0)",
                TENANT, TENANT, "other-tenant");
        jdbc.update("insert into rm_user_org_rela_t values (?, ?, 'active-org'), (?, ?, 'active-org'),"
                + " (?, ?, 'deleted-org'), (?, ?, 'other-org')", TENANT, ACTOR, TENANT, ACTOR,
                TENANT, ACTOR, "other-tenant", ACTOR);
        String grantScript = CanonicalMagicSources.byPath(GRANTS, "log_tenant_grants_v1");
        var magic = mock(RegisteredMagicFunctionInvoker.class);
        when(magic.invoke(eq("log_tenant_grants_v1"), anyMap())).thenAnswer(call -> {
            Map<String, String> parameters = call.getArgument(1);
            assertThat(TenantContext.requireTenantId()).isEqualTo(TENANT);
            assertThat(parameters).doesNotContainKey("tenantId");
            return grants(grantScript, jdbc, parameters.get("actor"), parameters.get("appId"), parameters.get("capability"));
        });
        var access = new LogAccessService(magic);
        try (var caller = TenantContext.use("other-tenant")) {
            sql.set(0);
            var scope = access.tenant(TENANT, ACTOR, APPLICATION, "requests");
            assertThat(scope.tenantId()).isEqualTo(TENANT);
            assertThat(scope.mode()).isEqualTo("ORG");
            assertThat(scope.orgIds()).containsExactly("active-org");
            assertThat(sql.get()).isEqualTo(3);
            assertThat(TenantContext.requireTenantId()).isEqualTo("other-tenant");

            jdbc.update("update rm_user_t set status=1 where tenant_id=? and id=?", TENANT, ACTOR);
            sql.set(0);
            assertThatThrownBy(() -> access.refresh(scope, "requests")).isInstanceOf(SecurityException.class);
            assertThat(sql.get()).isEqualTo(1);
            assertThat(TenantContext.requireTenantId()).isEqualTo("other-tenant");
        }
    }

    @Test
    void authorizationNeverUsesTheDefaultDatabaseForAnUnboundTenant() throws Exception {
        var sql = new AtomicInteger();
        var jdbc = database(sql);
        String script = CanonicalMagicSources.byPath(GRANTS, "log_tenant_grants_v1");
        try (var tenant = TenantContext.use("unbound-tenant")) {
            assertThatThrownBy(() -> grants(script, jdbc, ACTOR, APPLICATION, "requests"))
                    .hasStackTraceContaining("当前租户尚未配置可用的业务数据源");
            assertThat(sql.get()).isZero();
        }
    }

    @Test
    void onlyAuthorizedIndustryCallsEnterTheSharedBundleReader() throws Exception {
        String script = CanonicalMagicSources.byPath(API + "02.读取行业日志组件.ms", "tenant_log_components_get_v1");
        AtomicInteger reads = new AtomicInteger();
        MagicScriptLambdaFunction bundle = (context, values) -> {
            reads.incrementAndGet();
            return List.of(Map.of("name", "log-management"));
        };
        var inputs = new HashMap<String, Object>();
        inputs.put("logComponentBundle", bundle);
        inputs.put("capabilities", (MagicScriptLambdaFunction) (context, values) -> List.of());
        var denied = (ResponseEntity<?>) execute(script, inputs);
        assertThat(denied.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(((CommonResponse<?>) denied.getBody()).getCode()).isEqualTo(403);
        assertThat(reads.get()).isZero();

        inputs.put("capabilities", (MagicScriptLambdaFunction) (context, values) -> List.of("auth"));
        var allowed = (ResponseEntity<?>) execute(script, inputs);
        assertThat(allowed.getBody()).isEqualTo(Map.of("code", 0, "success", true,
                "data", List.of(Map.of("name", "log-management"))));
        assertThat(reads.get()).isEqualTo(1);
    }

    @Test
    void sharedBundleReadsOnlyTheActiveCompiledLogComponentFromControl() throws Exception {
        var jdbc = database(new AtomicInteger());
        jdbc.execute("""
                create table ui_component_t (tid varchar(64), name varchar(64), type varchar(8),
                    is_del int, compile_js varchar(100), compile_css varchar(100))
                """);
        jdbc.execute("""
                insert into ui_component_t values
                ('validation-log', 'log-management', '1', 0, 'validation JS', 'validation CSS'),
                ('validation-group', 'log-management', '0', 0, 'group', 'group'),
                ('validation-deleted', 'log-management', '1', 1, 'deleted', 'deleted'),
                ('validation-other', 'other', '1', 0, 'other', 'other')
                """);
        var runtime = new ControlFixture();
        String script = CanonicalMagicSources.byPath("function/01.通用函数/65.读取现役日志组件.ms",
                "log_component_bundle_v1");
        try (var tenant = TenantContext.use(TENANT)) {
            var result = execute(script, Map.of("identityRuntime", runtime,
                    "db", DatasourceCompletionMagicTest.magicDb((DriverManagerDataSource) jdbc.getDataSource())));
            assertThat(result).isEqualTo(List.of(Map.of("tid", "validation-log", "name", "log-management",
                    "compile_js", "validation JS", "compile_css", "validation CSS")));
            assertThat(runtime.calls()).isEqualTo(1);
            assertThat(TenantContext.usesControlDatabase()).isFalse();
            assertThat(TenantContext.requireTenantId()).isEqualTo(TENANT);
        }
    }

    @Test
    void platformGuardRunsBeforeTheBundleAndUsesTheRequestedIdentityDomain() throws Exception {
        String script = CanonicalMagicSources.byPath("api/08.统一身份管理/32.日志管理/01.日志查询.ms",
                "log_query_platform_v1");
        var compiled = List.of(Map.of("name", "log-management"));
        var reads = new AtomicInteger();
        var inputs = new HashMap<String, Object>();
        inputs.put("request", new PlatformQueries());
        inputs.put("identityRuntime", new ControlFixture());
        inputs.put("platformContext", (MagicScriptLambdaFunction) (context, values) -> Map.of("pairs", List.of(Map.of(
                "codes", List.of("audit:archive"), "scopes", List.of(Map.of("identity_domain", "public", "scope_kind", "ALL"))))));
        inputs.put("logGuard", (MagicScriptLambdaFunction) (context, values) -> {
            assertThat(values).containsExactly("archives", "public");
            throw new SecurityException("validation denied");
        });
        inputs.put("logComponentBundle", (MagicScriptLambdaFunction) (context, values) -> {
            reads.incrementAndGet();
            return compiled;
        });
        assertThatThrownBy(() -> execute(script, inputs)).hasStackTraceContaining("validation denied");
        assertThat(reads.get()).isZero();

        inputs.put("logGuard", (MagicScriptLambdaFunction) (context, values) -> {
            assertThat(values).containsExactly("archives", "public");
            assertThat(TenantContext.usesControlDatabase()).isTrue();
            return Map.of("requiredPermission", "audit:archive");
        });
        assertThat(execute(script, inputs)).isEqualTo(compiled);
        assertThat(reads.get()).isEqualTo(1);
        assertThat(TenantContext.getTenantId()).isNull();
    }

    private static Object execute(String script, Map<String, Object> inputs) {
        String fixture = script.replaceAll("(?m)^import (?:tableRead|tenantRuntime|identityRuntime|logManagement|request|'@/[^']+' as \\w+);?\\R", "");
        return MagicScript.create(fixture, null).execute(new MagicScriptContext(inputs));
    }

    private static Object grants(String script, JdbcTemplate jdbc, String actor, String app, String capability) {
        var input = new HashMap<String, Object>();
        input.put("tableRead", new StructuredTableReadMagicModule(jdbc));
        var tenant = new TenantFixture();
        input.put("tenantRuntime", tenant);
        input.put("identityRuntime", tenant);
        input.put("actor", actor);
        input.put("appId", app);
        input.put("capability", capability);
        return execute(script, input);
    }

    private static JdbcTemplate database(AtomicInteger sql) {
        var source = new DriverManagerDataSource("jdbc:h2:mem:log-capabilities-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "") {
            @Override
            public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                        new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                            if (method.getName().equals("prepareStatement") && args[0] instanceof String query
                                    && query.stripLeading().startsWith("SELECT")) {
                                sql.incrementAndGet();
                            }
                            try {
                                return method.invoke(connection, args);
                            } catch (InvocationTargetException failure) {
                                throw failure.getCause();
                            }
                        });
            }
        };
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("create table rm_user_t (tenant_id varchar(64), id varchar(64), status int, deleted int)");
        jdbc.execute("create table rm_user_role_rela_t (tenant_id varchar(64), user_id varchar(64), role_id varchar(64))");
        jdbc.execute("""
                create table rm_role_t (tenant_id varchar(64), id varchar(64), app_id varchar(64),
                    status int, deleted int, data_scope varchar(16))
                """);
        jdbc.execute("create table rm_role_menu_rela_t (tenant_id varchar(64), role_id varchar(64), app_resource_id varchar(64))");
        jdbc.execute("""
                create table rm_menu_t (tenant_id varchar(64), app_id varchar(64), id varchar(64),
                    parent_id varchar(64), code varchar(64), status int, deleted int)
                """);
        jdbc.execute("create table rm_org_t (tenant_id varchar(64), id varchar(64), deleted int)");
        jdbc.execute("create table rm_user_org_rela_t (tenant_id varchar(64), user_id varchar(64), org_id varchar(64))");
        return jdbc;
    }

    private static void addAccount(JdbcTemplate jdbc, String tenant, String user, int status, int deleted) {
        jdbc.update("insert into rm_user_t values (?,?,?,?)", tenant, user, status, deleted);
    }

    private static void addMenu(JdbcTemplate jdbc, String tenant, String app, String id, String parent, String code, int status) {
        jdbc.update("insert into rm_menu_t values (?,?,?,?,?,?,0)", tenant, app, id, parent, code, status);
    }

    private static void grant(JdbcTemplate jdbc, String tenant, String app, String user, String id, String parent,
                              String capability, String scope) {
        String role = "role-" + id;
        addMenu(jdbc, tenant, app, id, parent, "logs:" + capability, 0);
        jdbc.update("insert into rm_role_t values (?,?,?,0,0,?)", tenant, role, app, scope);
        jdbc.update("insert into rm_user_role_rela_t values (?,?,?)", tenant, user, role);
        jdbc.update("insert into rm_role_menu_rela_t values (?,?,?)", tenant, role, id);
    }

    public static class TenantFixture {
        public String id() {
            return TenantContext.requireTenantId();
        }

        public String requireTenantBinding(String tenantId) {
            if (!TENANT.equals(tenantId)) {
                throw new SecurityException("当前租户尚未配置可用的业务数据源");
            }
            return tenantId;
        }
    }

    public static class ControlFixture {
        private int calls;

        public Object control(Supplier<Object> action) {
            calls++;
            try (var scope = TenantContext.control()) {
                return action.get();
            }
        }

        int calls() {
            return calls;
        }
    }

    public static class PlatformQueries {
        public List<String> getValues(String name) {
            return switch (name) {
                case "domain" -> List.of("public");
                case "operation" -> List.of("components");
                default -> List.of();
            };
        }
    }
}
