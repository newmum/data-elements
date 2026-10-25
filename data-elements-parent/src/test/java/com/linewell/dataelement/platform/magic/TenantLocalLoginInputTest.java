package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import cn.hutool.crypto.SmUtil;
import com.linewell.dataelement.utils.PwdRuleUtil;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

/** Executes the canonical login script, including real salted credential verification. */
class TenantLocalLoginInputTest {
    private static final String FIXTURE_PASSWORD = "Fixture!Pass9";
    private static final String DIGEST = SmUtil.sm3(FIXTURE_PASSWORD).toUpperCase();
    private SQLModule db;
    private JdbcTemplate jdbc;
    private Audit audit;
    private final Accounts accounts = new Accounts();
    private final Receiver receiver = new Receiver();

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:login_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        var sources = new MagicDynamicDataSource();
        sources.setDefault(source);
        db = new SQLModule(sources);
        db.setDataSourceNode(sources.getDataSource());
        var columns = new ColumnMapperAdapter();
        columns.add(new DefaultColumnMapperProvider());
        columns.add(new CamelColumnMapperProvider());
        columns.setDefault("camel");
        db.setColumnMapperProvider(columns);
        db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
        db.setRowMapColumnMapper(value -> value);
        db.setSqlInterceptors(List.of());
        db.setNamedTableInterceptors(List.of());
        jdbc = new JdbcTemplate(source);
        jdbc.execute("create table rm_user_t(id varchar(40),tenant_id varchar(40),user_name varchar(50),password varchar(256),status int,deleted int)");
        jdbc.execute("create table log_user_login_t(tid varchar(40),account varchar(50),is_success int,token varchar(100),created_by varchar(40),updated_by varchar(40),created_time timestamp,updated_time timestamp,is_del int,tenant_id varchar(40),action_code varchar(64),result varchar(24))");
        audit=new Audit(jdbc,accounts);
        for (String name : List.of("246810", "001234", "0", "manager")) {
            jdbc.update("insert into rm_user_t values(?,?,?,?,0,0)", "user-" + name, "tenant-a", name,
                    PwdRuleUtil.encryptPwdBySalt(DIGEST));
        }
        // A same-name foreign account must never satisfy this tenant's login.
        jdbc.update("insert into rm_user_t values('foreign','tenant-b','foreign-only',?,0,0)",
                PwdRuleUtil.encryptPwdBySalt(DIGEST));
    }

    static Stream<Object> accounts() {
        return Stream.of(246810, " 246810 ", "001234", 0);
    }

    @ParameterizedTest
    @MethodSource("accounts")
    void numericAndTextAccountsLoginWithoutChangingTheirIdentity(Object account) throws Exception {
        var result = assertInstanceOf(Map.class, run(account, DIGEST.toLowerCase(), "tenant-a"));
        assertEquals("tenant-a", result.get("tenantId"));
        assertEquals("tenant-local-v1", result.get("authRealm"));
        assertEquals(String.valueOf(account).trim(), accounts.lastAccount);
        assertEquals(1, jdbc.queryForObject("select count(*) from log_user_login_t", Integer.class));
    }

    @Test
    void failedPasswordWithSpaceCanBeCorrectedOnTheNextAttempt() throws Exception {
        String before = jdbc.queryForObject("select password from rm_user_t where user_name='246810'", String.class);
        assertExit(-1, run(246810, SmUtil.sm3(FIXTURE_PASSWORD + " "), "tenant-a"));
        assertEquals(0, accounts.logins);
        assertEquals(1, jdbc.queryForObject("select count(*) from log_user_login_t where result='FAILED'", Integer.class));
        assertInstanceOf(Map.class, run(246810, DIGEST, "tenant-a"));
        assertEquals(1, accounts.logins);
        assertEquals(before, jdbc.queryForObject("select password from rm_user_t where user_name='246810'", String.class));
    }

    @Test
    void invalidJsonTypesReturnBusinessValidationInsteadOfScriptErrors() throws Exception {
        for (Object invalid : List.of(true, Map.of("value", "manager"), List.of("manager"))) {
            assertExit(400, run(invalid, DIGEST, "tenant-a"));
        }
        for (Object invalid : List.of(123456, true, Map.of("value", DIGEST), List.of(DIGEST))) {
            assertExit(400, run("manager", invalid, "tenant-a"));
        }
        assertExit(400, run(null, DIGEST, "tenant-a"));
        assertExit(400, run("manager", null, "tenant-a"));
        assertExit(400, run("manager", " ", "tenant-a"));
        assertEquals(0, accounts.logins);
    }

    @Test
    void wrongTenantLeadingZerosDisabledAccountAndReceiverChecksRemainEffective() throws Exception {
        assertExit(-1, run("foreign-only", DIGEST, "tenant-a"));
        assertExit(-1, run(1234, DIGEST, "tenant-a"));
        assertExit(403, run("manager", DIGEST, "not-local"));
        jdbc.update("update rm_user_t set status=1 where user_name='manager'");
        assertExit(-1, run("manager", DIGEST, "tenant-a"));
        receiver.allowed = false;
        assertExit(-1, run("246810", DIGEST, "tenant-a"));
        assertEquals(0, accounts.logins);
    }

    private Object run(Object account, Object password, String tenant) throws Exception {
        String script = CanonicalMagicSources.byId("ga_local_login_01")
                .replaceAll("(?m)^import (db|identityRuntime|tenantAccounts|auditEvents);\\r?\\n", "")
                .replaceAll("(?m)^import '@/[^\\r\\n]+;\\r?\\n", "");
        var payload = new LinkedHashMap<String, Object>();
        payload.put("account", account); payload.put("password", password);
        payload.put("tenantId", tenant); payload.put("appId", "app-a");
        var context = Map.<String,Object>of("payload", payload, "db", db,
                "identityRuntime", new Runtime(), "tenantAccounts", accounts, "receiver", receiver,"auditEvents",audit);
        return MagicScript.create("var sourceCheck=id=>receiver.check(id);\n" + script, null)
                .execute(new MagicScriptContext(context));
    }

    private static void assertExit(int expected, Object result) {
        var exit = assertInstanceOf(ExitValue.class, result);
        assertEquals(expected, ((Number)exit.getValues()[0]).intValue());
    }

    @Test void successfulLoginNeverPersistsBearerCredential()throws Exception{
        assertInstanceOf(Map.class,run("manager",DIGEST,"tenant-a"));
        assertEquals(0,jdbc.queryForObject("select count(*) from log_user_login_t where token is not null",Integer.class));
        assertEquals("SUCCESS",jdbc.queryForObject("select result from log_user_login_t",String.class));
    }
    @Test void failedFinalAuditCompensatesNewlyIssuedSession()throws Exception{
        audit.fail=true;
        assertThrows(Exception.class,()->run("manager",DIGEST,"tenant-a"));
        assertTrue(accounts.revoked);
    }
    public static class Audit {
        final JdbcTemplate jdbc;final Accounts accounts;boolean fail;
        Audit(JdbcTemplate jdbc,Accounts accounts){this.jdbc=jdbc;this.accounts=accounts;}
        public void authentication(String tenant,String user,String action,String result,String reason){
            if(fail)throw new IllegalStateException("validation audit write unavailable");
            jdbc.update("insert into log_user_login_t(tid,tenant_id,created_by,action_code,result) values(?,?,?,?,?)",UUID.randomUUID().toString(),tenant,user,action,result);
        }
        public void compensateLogin(){accounts.revoked=true;}
    }
    public static class Runtime {
        public Object inTenant(String tenant, MagicScriptLambdaFunction callback) {
            assertEquals("tenant-a", tenant);
            return callback.apply(null, new Object[0]);
        }
    }

    public static class Accounts {
        int logins; String lastAccount;boolean revoked;
        public boolean isLocal(String tenant) { return "tenant-a".equals(tenant); }
        public String loginLocal(String tenant, String user, String app, String account) {
            assertEquals("tenant-a", tenant); assertEquals("app-a", app);
            assertEquals("user-" + account, user);
            logins++; lastAccount = account;
            return "fixture-session";
        }
    }

    public static class Receiver {
        boolean allowed = true;
        public Map<String,Object> check(String user) { return Map.of("allowed", allowed); }
    }
}
