package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.configuration.MagicBootConfiguration;
import com.linewell.dataelement.platform.tenant.api.TenantAccountsMagicModule;
import com.linewell.dataelement.platform.tenant.application.TenantAccessService;
import com.linewell.dataelement.platform.tenant.application.TenantAccountPolicy;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.web.error.GlobalExceptionHandler;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

public class PortalTenantMagicTest {
    private static final String FUNCTIONS = "function/09.租户账号/";
    private static final String API = "api/01.系统门户/02.登录与会话/";

    private final SaSession token = mock(SaSession.class);
    private final TenantAccessService membership = mock(TenantAccessService.class);
    private final TenantAccountPolicy policy = mock(TenantAccountPolicy.class);
    private final Request request = new Request();
    private final List<String> sql = new ArrayList<>();
    private final Map<String, Object> bindings = new LinkedHashMap<>();
    private JdbcTemplate jdbc;

    @BeforeEach
    void configureDatabase() throws Exception {
        var datasource = new DriverManagerDataSource("jdbc:h2:mem:portal_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(datasource);
        jdbc.execute("""
                CREATE TABLE sym_tenant_t(tid varchar(64) primary key, code varchar(64), name varchar(64),
                  status int, init_flag int, isolation_mode varchar(32), sort_no int,
                  created_time timestamp, json_config varchar(256), is_del int)
                """);
        jdbc.execute("""
                CREATE TABLE rm_user_tenant_rela_t(user_id varchar(64), tenant_id varchar(64),
                  is_default int, status int, is_del int)
                """);
        var sources = new MagicDynamicDataSource();
        sources.setDefault(datasource);
        var db = new SQLModule(sources);
        db.setDataSourceNode(sources.getDataSource());
        var columns = new ColumnMapperAdapter();
        columns.add(new DefaultColumnMapperProvider());
        columns.setDefault("default");
        db.setColumnMapperProvider(columns);
        db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
        db.setRowMapColumnMapper(value -> value);
        db.setNamedTableInterceptors(List.of());
        db.setSqlInterceptors(List.of(new SQLInterceptor() {
            @Override
            public void preHandle(BoundSql statement, RequestEntity requestEntity) {
                sql.add(statement.getSql());
            }
        }));
        bindings.put("db", db);
        bindings.put("request", request);
        bindings.put("identityRuntime", new Control());
        bindings.put("tenantRuntime", new TenantRuntime());
        bindings.put("systemConfig", new Config());
        function("respond", "03.门户旧响应信封", "response", "action");
        function("profile", "04.读取门户租户资料", "profile", "tenantId", "activeOnly");
        function("available", "05.读取会话可用租户", "available", "tenantId", "localSession", "currentProfile");
    }

    @Test
    void listsTwentyAndOneHundredPublicOptionsWithOneSelect() throws Exception {
        try (var spring = springBeans()) {
            for (int count : List.of(20, 100)) {
                jdbc.update("delete from sym_tenant_t");
                for (int index = 0; index < count; index++) {
                    insertTenant("option-" + index, index, 1, 0);
                }
                insertTenant("disabled", -1, 0, 0);
                insertTenant("deleted", -1, 1, 1);
                sql.clear();
                var response = entry("05.查询登录行业", "portal_options_v1");
                var rows = assertInstanceOf(List.class, response.getData());
                assertEquals(count, rows.size());
                assertEquals(Map.of("tid", "option-0", "code", "code-option-0", "name", "option-0"), rows.getFirst());
                assertEquals(1, sql.size());
            }
        }
    }

    @Test
    void readsOnlySelectedActiveBrandAndPreservesParameterPrecedence() throws Exception {
        insertTenant("local", 1, 1, 0);
        try (var spring = springBeans()) {
            assertEquals(20001, entry("06.查询行业登录设置", "portal_settings_v1").getCode());
            request.servlet.addParameter("tenantId[]", "local");
            assertEquals(Map.of("brand", "local"), entry("06.查询行业登录设置", "portal_settings_v1").getData());
            request.servlet.addParameter("tenantId", "forbidden");
            assertEquals(403, entry("06.查询行业登录设置", "portal_settings_v1").getCode());
        }
    }

    @Test
    void sessionReusesItsControlReadAndSwitchCommitsOnlyAfterValidation() throws Exception {
        insertTenant("local", 1, 1, 0);
        insertTenant("other", 2, 1, 0);
        when(token.get("authRealm")).thenReturn("tenant-local-v1");
        try (var spring = springBeans(); var stp = mockStatic(StpUtil.class);
             var tenant = TenantContext.use("local")) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getTokenSession).thenReturn(token);
            var current = assertInstanceOf(Map.class, entry("07.读取当前行业会话", "portal_session_v1").getData());
            assertEquals("LOCAL", current.get("accountSource"));
            var available = assertInstanceOf(List.class, current.get("available"));
            assertEquals(1, available.size());
            assertEquals("local", assertInstanceOf(Map.class, available.getFirst()).get("tid"));
            assertEquals(1, sql.size());

            bindings.put("body", Map.of("tenantId", "other"));
            assertEquals(403, entry("09.切换当前行业", "portal_switch_v1").getCode());
            verify(token, never()).set(eq("tenantId"), any());
            bindings.put("body", Map.of("tenantId", "local"));
            request.servlet.setAttribute(MagicBootConfiguration.MAGIC_PORTAL_SWITCH_JSON_ERROR,
                    new HttpMessageNotReadableException("JSON parse error: validation", null, null));
            var error = entry("09.切换当前行业", "portal_switch_v1");
            assertEquals(500, error.getCode());
            assertEquals("JSON parse error: validation", error.getMsg());
            verify(token, never()).set(eq("tenantId"), any());
            request.servlet.removeAttribute(MagicBootConfiguration.MAGIC_PORTAL_SWITCH_JSON_ERROR);
            assertEquals(0, entry("09.切换当前行业", "portal_switch_v1").getCode());
            verify(token).set("tenantId", "local");

            clearInvocations(token);
            jdbc.execute("alter table sym_tenant_t rename to validation_unavailable");
            assertEquals(500, entry("09.切换当前行业", "portal_switch_v1").getCode());
            verify(token, never()).set(eq("tenantId"), any());
        }
    }

    @Test
    void validatesEveryLegacyMembershipWithTwoQueriesAtTwentyAndOneHundred() throws Exception {
        insertTenant("other", 0, 1, 0);
        insertTenant("blocked-local", 0, 1, 0);
        insertTenant("deleted", 0, 1, 1);
        jdbc.update("insert into rm_user_tenant_rela_t values ('elsewhere','other',1,1,0)");
        jdbc.update("insert into rm_user_tenant_rela_t values ('operator','blocked-local',1,1,0), ('operator','deleted',1,1,0)");
        when(policy.isLocalConfiguration(eq("blocked-local"), any())).thenReturn(true);
        try (var spring = springBeans(); var stp = mockStatic(StpUtil.class);
             var tenant = TenantContext.use("validation-current")) {
            stp.when(StpUtil::getLoginId).thenReturn("operator");
            for (int count : List.of(20, 100)) {
                int start = count == 20 ? 0 : 20;
                jdbc.update("update rm_user_tenant_rela_t set is_default=0 where user_id='operator'");
                for (int index = start; index < count; index++) {
                    String id = "member-" + index;
                    insertTenant(id, index, 1, 0);
                    jdbc.update("insert into rm_user_tenant_rela_t values ('operator',?,?,1,0)", id, index == count - 1 ? 1 : 0);
                }
                sql.clear();
                var result = assertInstanceOf(List.class, entry("08.查询会话可用行业", "portal_tenants_v1").getData());
                assertEquals(count, result.size());
                assertEquals("member-" + (count - 1), assertInstanceOf(Map.class, result.getFirst()).get("tid"));
                for (Object row : result) {
                    assertTrue(assertInstanceOf(Map.class, row).get("tid").toString().startsWith("member-"));
                }
                assertEquals(2, sql.size());
            }
        }
    }

    @Test
    void receiverAdminUsesTheBoundTenantForBothRoleAndRows() throws Exception {
        jdbc.execute("create alias utc_timestamp for 'com.linewell.dataelement.platform.magic.PortalTenantMagicTest.utcTimestamp'");
        jdbc.execute("create table rm_user_role_rela_t(user_id varchar,role_id varchar,tenant_id varchar,end_time timestamp)");
        jdbc.execute("create table rm_role_t(id varchar,tenant_id varchar,deleted int,status int,app_id varchar)");
        jdbc.execute("create table rm_role_menu_rela_t(role_id varchar,tenant_id varchar,app_resource_id varchar)");
        jdbc.execute("create table rm_menu_t(id varchar,tenant_id varchar,deleted int,url varchar)");
        jdbc.execute("create table idaas_receiver_binding_t(key_id varchar, tenant_id varchar, app_id varchar, "
                + "instance_id varchar, enabled int, version int, credential_version int)");
        jdbc.update("insert into rm_user_role_rela_t values ('operator','admin','local',null), ('elsewhere','admin','other',null)");
        jdbc.update("insert into rm_role_t values ('admin','local',0,0,'app'), ('admin','other',0,0,'app')");
        jdbc.update("insert into rm_role_menu_rela_t values ('admin','local','settings'), ('admin','other','settings')");
        jdbc.update("insert into rm_menu_t values ('settings','local',0,'/setting'), ('settings','other',0,'/setting')");
        jdbc.update("insert into idaas_receiver_binding_t values ('foreign','other','app','instance',1,0,0)");
        var accounts = mock(TenantAccountsMagicModule.class);
        when(accounts.isLocal("local")).thenReturn(true);
        when(accounts.session()).thenReturn(token);
        when(token.get("appId")).thenReturn("app");
        bindings.put("tenantAccounts", accounts);
        bindings.put("body", Map.of("action", "read", "tenantId", "other", "keyId", "foreign"));
        var script = CanonicalMagicSources.byId("8b7e7875e5f35f1bbd3b2ba81fea5373")
                .replaceAll("(?m)^import (?:db|tenantRuntime|identityRuntime|tenantAccounts|identityTransport);?\\h*\\R", "");
        try (var stp = mockStatic(StpUtil.class); var tenant = TenantContext.use("local")) {
            stp.when(StpUtil::getLoginId).thenReturn("operator");
            for (int count : List.of(20, 100)) {
                int start = count == 20 ? 0 : 20;
                for (int i = start; i < count; i++) {
                    jdbc.update("insert into idaas_receiver_binding_t values (?, 'local', 'app', 'instance', 1, 0, 0)", "local-" + i);
                }
                sql.clear();
                var rows = assertInstanceOf(List.class, run(script, bindings));
                assertEquals(count, rows.size());
                for (Object row : rows) {
                    assertTrue(assertInstanceOf(Map.class, row).get("keyid").toString().startsWith("local-"));
                }
                assertEquals(2, sql.size());
            }
            stp.when(StpUtil::getLoginId).thenReturn("elsewhere");
            sql.clear();
            assertEquals(403, assertInstanceOf(ExitValue.class, run(script, bindings)).getValues()[0]);
            assertEquals(1, sql.size());
        }
    }

    public static java.sql.Timestamp utcTimestamp(int precision) {
        return java.sql.Timestamp.from(java.time.Instant.now());
    }

    private void insertTenant(String id, int order, int status, int deleted) {
        jdbc.update("insert into sym_tenant_t values (?,?,?,?,0,'DATABASE',?,CURRENT_TIMESTAMP,'{}',?)",
                id, "code-" + id, id, status, order, deleted);
    }

    private org.mockito.MockedStatic<SpringUtil> springBeans() {
        var spring = mockStatic(SpringUtil.class);
        spring.when(() -> SpringUtil.getBean("globalExceptionHandler")).thenReturn(new GlobalExceptionHandler());
        spring.when(() -> SpringUtil.getBean("tenantAccountPolicy")).thenReturn(policy);
        spring.when(() -> SpringUtil.getBean("tenantAccessService")).thenReturn(membership);
        return spring;
    }

    private void function(String alias, String title, String id, String... parameters) throws Exception {
        String script = CanonicalMagicSources.byPath(FUNCTIONS + title + ".ms", "portal_fn_" + id + "_v1");
        bindings.put(alias, (MagicScriptLambdaFunction) (ignored, values) -> {
            var valuesByName = new LinkedHashMap<>(bindings);
            for (int index = 0; index < parameters.length; index++) {
                valuesByName.put(parameters[index], values[index]);
            }
            return run(script, valuesByName);
        });
    }

    private CommonResponse<?> entry(String title, String id) throws Exception {
        var response = assertInstanceOf(ResponseEntity.class,
                run(CanonicalMagicSources.byPath(API + title + ".ms", id), bindings));
        assertEquals(200, response.getStatusCode().value());
        return assertInstanceOf(CommonResponse.class, response.getBody());
    }

    private static Object run(String script, Map<String, Object> variables) {
        String fixture = script.replaceAll("(?m)^import (?:'.+' as .+|db|request|identityRuntime|tenantRuntime|systemConfig)\\s*\\R", "");
        return MagicScript.create(fixture, null).execute(new MagicScriptContext(variables));
    }

    public static class Request {
        final MockHttpServletRequest servlet = new MockHttpServletRequest();
        public MockHttpServletRequest get() { return servlet; }
        public List<String> getValues(String name) {
            String[] values = servlet.getParameterValues(name);
            return values == null ? null : List.of(values);
        }
    }

    public static class Control {
        public Object control(Supplier<Object> action) {
            try (var scope = TenantContext.control()) {
                return action.get();
            }
        }
    }

    public static class TenantRuntime {
        public String id() { return TenantContext.requireTenantId(); }
    }

    public static class Config {
        public Map<String, Object> publicSettings(String id) { return Map.of("brand", id); }
    }
}
