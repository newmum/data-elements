package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

/** Real Magic SQLModule transactions with an isolated, in-memory H2 datasource. */
class IdaasMagicTransactionTest {

    private SQLModule db;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:idaas_" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        );
        var sources = new MagicDynamicDataSource();
        sources.setDefault(dataSource);
        db = new SQLModule(sources);
        db.setDataSourceNode(sources.getDataSource());
        var columnMappings = new ColumnMapperAdapter();
        columnMappings.add(new DefaultColumnMapperProvider());
        columnMappings.add(new CamelColumnMapperProvider());
        columnMappings.setDefault("camel");
        db.setColumnMapperProvider(columnMappings);
        db.setSqlInterceptors(List.of());
        db.setNamedTableInterceptors(List.of());
        db.setColumnMapRowMapper(columnMappings.getDefaultColumnMapRowMapper());
        db.setRowMapColumnMapper(value -> value);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table business_record(id varchar(32) primary key, name varchar(100))");
        jdbc.execute("""
                create table iam_request_receipt_t (
                    tid varchar(32) primary key, operator_id varchar(32), operation_code varchar(100),
                    request_id varchar(64), request_hash varchar(64), status varchar(20), result_json clob,
                    unique(operator_id, operation_code, request_id)
                )
                """);
        jdbc.execute("create table iam_application_t(tid varchar(32) primary key)");
        jdbc.update("insert into iam_application_t values('idaas-platform-internal')");
    }

    @Test
    void exitBypassesCatchButAlwaysRunsFinally() {
        List<String> events = new ArrayList<>();
        Object result = run("""
                try { exit 403, 'denied'; }
                catch (error) { events.add('caught'); }
                finally { events.add('finally'); }
                """, Map.of("events", events));
        assertInstanceOf(ExitValue.class, result);
        assertEquals(List.of("finally"), events);
    }

    @Test
    void explicitMappingPreservesDtoAliasesUnderTheSharedCamelDefault() {
        Map<?, ?> result = assertInstanceOf(Map.class, run("""
                var sql=db.normal();
                return sql.selectOne('select 1 as "tenantId"');
                """, Map.of()));
        assertEquals(1, ((Number) result.get("tenantId")).intValue());
        assertTrue(result.keySet().contains("tenantId"));
    }

    @Test
    void callbackExitRollsBackActualMagicSqlUpdate() {
        Object result = run("""
                return db.transaction(() => {
                    db.update("insert into business_record(id,name) values('new-record','test')");
                    exit 409, 'validation after write';
                });
                """, Map.of());
        assertInstanceOf(ExitValue.class, result);
        assertEquals(0, count("business_record"));
    }

    @Test
    void callbackReturnCommitsActualMagicSqlUpdate() {
        Object result = run("""
                return db.transaction(() => {
                    db.update("insert into business_record(id,name) values('new-record','test')");
                    return {id:'new-record'};
                });
                """, Map.of());
        assertEquals("new-record", ((Map<?, ?>) result).get("id"));
        assertEquals(1, count("business_record"));
    }

    @Test
    void actualMutationReceiptAndBusinessWriteRollbackTogetherOnExit() throws Exception {
        String source = actualMutationSource("""
                var callback = () => {
                    db.update("insert into business_record(id,name) values('new-record','test')");
                    exit 409, 'validation after write';
                };
                """);
        Object result = run(source, mutationArguments());
        assertInstanceOf(ExitValue.class, result);
        assertEquals(0, count("business_record"));
        assertEquals(0, count("iam_request_receipt_t"));
    }

    @Test
    void actualMutationReceiptCommitsOnceAndReplaysWithoutCallback() throws Exception {
        String source = actualMutationSource("""
                var callback = () => {
                    db.update("insert into business_record(id,name) values('new-record','test')");
                    return {id:'new-record',version:1};
                };
                """);
        Object first = run(source, mutationArguments());
        Object replay = run(source, mutationArguments());
        assertEquals("new-record", ((Map<?, ?>) first).get("id"));
        assertEquals("new-record", ((Map<?, ?>) replay).get("id"));
        assertEquals(1, count("business_record"));
        assertEquals(1, count("iam_request_receipt_t"));
    }

    private String actualMutationSource(String callback) throws Exception {
        // Adapt external authorization/fingerprint modules; execute the actual transaction unchanged.
        return "var context=()=>({pairs:[],permissions:[],account:{}});var guard=(permission,app,domain)=>context();\n"
                + callback + CanonicalMagicSources.byId("cdebc557515854f7aff31170e0b6c630")
                .replaceAll("(?m)^import (db|identityTransport);\\r?\\n", "")
                .replaceAll("(?m)^import '@/[^\\r\\n]+;\\r?\\n", "");
    }

    private Map<String, Object> mutationArguments() {
        return Map.of(
                "ctx", new LinkedHashMap<>(Map.of("userId", "test-user")),
                "operation", "test:save",
                "payload", Map.of("requestId", "test-request-001", "record", Map.of("name", "test"))
        );
    }

    @Test
    void restoredHutoolPayloadCommitsThroughTheActualReceiptFunction() throws Exception {
        String source = actualMutationSource("""
                var callback = () => {
                    db.update("insert into business_record(id,name) values('restored-record','test')");
                    return {id:'restored-record',version:1};
                };
                """);
        Map<String, Object> arguments = new LinkedHashMap<>(mutationArguments());
        arguments.put("payload", JSONUtil.toBean(JSONUtil.toJsonStr(arguments.get("payload")), LinkedHashMap.class));
        Map<?, ?> result = assertInstanceOf(Map.class, run(source, arguments));
        assertEquals("restored-record", result.get("id"));
        assertEquals(1, count("business_record"));
        assertEquals(1, count("iam_request_receipt_t"));
    }

    @Test
    void publicBrandingReadsOnlyAllowedFieldsFromTheActiveGlobalRow() throws Exception {
        jdbc.execute("""
                create table iam_setting_t(scope_kind varchar(20), scope_key varchar(30), category varchar(20),
                    config_key varchar(30), is_del int, status varchar(20), value_json varchar(1000))
                """);
        jdbc.update("insert into iam_setting_t values ('GLOBAL','global','BRANDING','brand',0,'ACTIVE',?)",
                "{\"title\":\"统一身份管理平台\",\"subtitle\":\"验证副标题\",\"logoUrl\":\"/validation.svg\",\"internal\":\"hidden\"}");
        jdbc.update("insert into iam_setting_t values ('APP','validation','BRANDING','brand',0,'ACTIVE','{}')");
        String brand = CanonicalMagicSources.byPath("function/13.统一身份管理/34.读取平台公开品牌.ms",
                        "idaas_public_brand_read_v1")
                .replaceAll("(?m)^import (identityRuntime|db|'.+').*\\R", "");
        MagicScriptLambdaFunction read = (ignored, args) -> run("var apiCall=(callback)=>callback();\n" + brand,
                Map.of("identityRuntime", new ControlFixture()));
        String head = CanonicalMagicSources.byPath("api/08.统一身份管理/01.认证会话/04.读取登录页设置响应头.ms",
                "idaas_public_brand_head_v1");
        String get = CanonicalMagicSources.byId("c5e8f3f5dc9d52d5b23709f6e3c2ec15");
        for (String source : List.of(get, head)) {
            String entry = source.replaceFirst("(?m)^import '@/idaas/publicbrand' as publicBrand;\\R", "");
            var value = assertInstanceOf(Map.class, run(entry, Map.of("publicBrand", read)));
            assertEquals(Map.of("title", "统一身份管理平台", "subtitle", "验证副标题", "logoUrl", "/validation.svg"), value);
        }
    }

    @Test
    void disablingTwentyOrOneHundredSubjectsChecksEveryScopeBeforeWriting() throws Exception {
        jdbc.execute("""
                create table iam_subject_t(tid varchar(64) primary key, identity_domain varchar(20), status varchar(20),
                    version int, is_del int, updated_by varchar(64), updated_time timestamp)
                """);
        jdbc.execute("""
                create table iam_appointment_t(subject_id varchar(64), org_id varchar(64), is_del int,
                    status varchar(20), valid_from timestamp, valid_until timestamp)
                """);
        jdbc.execute("""
                create table iam_subject_app_t(subject_id varchar(64), desired_enabled int, desired_version int,
                    version int, updated_time timestamp)
                """);
        var statements = new ArrayList<String>();
        db.setSqlInterceptors(List.of(new SQLInterceptor() {
            @Override public void preHandle(BoundSql sql, RequestEntity request) {
                statements.add(sql.getSql().stripLeading().split("\\s+", 2)[0]);
            }
        }));
        String source = CanonicalMagicSources.byId("062a1e8f5b825797baf08ddc8654daa8")
                .replaceAll("(?m)^import (db|identityRuntime|'.+').*\\R", "")
                .replace("utc_timestamp(6)", "CURRENT_TIMESTAMP");
        String header = """
                import java.lang.IllegalStateException;
                var apiCall=(callback)=>callback();
                var directoryScope=(permission,domain)=>scope;
                var mutate=(context,action,payload,callback)=>db.transaction(callback);
                var audit=(context,result,action,subject,resource,summary)=>{ if(failAudit) { throw new IllegalStateException('验证审计失败'); } };
                """;
        for (int size : List.of(20, 100)) {
            List<Map<String, Object>> entries = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                String id = "validation-" + size + "-" + i;
                jdbc.update("insert into iam_subject_t(tid,identity_domain,status,version,is_del) values(?,'workforce','ACTIVE',1,0)", id);
                jdbc.update("insert into iam_appointment_t values(?, 'org-a',0,'ACTIVE',TIMESTAMP '2000-01-01 00:00:00',null)", id);
                jdbc.update("insert into iam_subject_app_t(subject_id,desired_enabled,desired_version,version) values(?,1,1,1)", id);
                entries.add(Map.of("id", id, "version", 1));
            }
            String protectedId = (String) entries.getLast().get("id");
            jdbc.update("insert into iam_appointment_t values(?, 'outside',0,'ACTIVE',TIMESTAMP '2000-01-01 00:00:00',null)", protectedId);
            var input = new LinkedHashMap<String, Object>();
            input.put("scope", Map.of("ctx", Map.of("userId", "validation-operator"), "all", false, "orgIds", List.of("org-a")));
            input.put("body", Map.of("entries", entries));
            input.put("identityRuntime", new ControlFixture());
            input.put("failAudit", false);

            statements.clear();
            var rejected = assertInstanceOf(ExitValue.class, run(header + source, input));
            assertEquals(403, rejected.getValues()[0]);
            assertEquals(List.of("SELECT", "SELECT"), statements);
            jdbc.update("delete from iam_appointment_t where subject_id=? and org_id='outside'", protectedId);
            input.put("failAudit", true);
            assertThrows(RuntimeException.class, () -> run(header + source, input));
            assertEquals(1, jdbc.queryForObject("select version from iam_subject_t where tid=?", Integer.class, protectedId));
            assertEquals(1, jdbc.queryForObject("select desired_enabled from iam_subject_app_t where subject_id=?", Integer.class, protectedId));

            input.put("failAudit", false);
            statements.clear();
            var result = assertInstanceOf(Map.class, run(header + source, input));
            assertEquals(size, result.get("count"));
            assertEquals(List.of("SELECT", "SELECT", "UPDATE", "UPDATE"), statements);
            assertEquals(0, jdbc.queryForObject("select desired_enabled from iam_subject_app_t where subject_id=?", Integer.class, protectedId));
            statements.clear();
            var stale = assertInstanceOf(ExitValue.class, run(header + source, input));
            assertEquals(409, stale.getValues()[0]);
            assertEquals(List.of("SELECT", "SELECT"), statements);
        }
    }

    @Test
    void operatorResetSerializesCredentialRotationAndRollsBackFailedAudit() throws Exception {
        jdbc.execute("""
                create table iam_operator_t(tid varchar(64) primary key, is_del int, version int,
                    must_change_password int, updated_time timestamp)
                """);
        jdbc.execute("""
                create table iam_credential_t(tid varchar(64) primary key, owner_kind varchar(12), owner_id varchar(64),
                    credential_type varchar(20), slot varchar(20), revision int, algorithm varchar(30),
                    secret_hash varchar(100), status varchar(20), updated_time timestamp)
                """);
        jdbc.execute("""
                create table iam_account_security_t(principal_kind varchar(12), principal_id varchar(64),
                    security_version int, failed_count int, locked_until timestamp, password_changed_time timestamp)
                """);
        jdbc.update("insert into iam_operator_t values('validation-target',0,1,0,null)");
        jdbc.update("insert into iam_credential_t values('validation-original','OPERATOR','validation-target','PASSWORD',"
                + "'primary',1,'PBKDF2_SHA256','validation-hash','ACTIVE',null)");
        jdbc.update("insert into iam_account_security_t values('OPERATOR','validation-target',1,2,null,null)");
        String source = CanonicalMagicSources.byId("2049857ef4b552d5a47490851cb25d97")
                .replaceAll("(?m)^import (db|identityRuntime|iamSecurity|'.+').*\\R", "")
                .replace("utc_timestamp(6)", "CURRENT_TIMESTAMP");
        source = """
                import cn.hutool.core.util.IdUtil;
                import java.lang.IllegalStateException;
                var apiCall=(callback)=>callback();
                var guard=(permission,app,domain)=>({userId:'validation-operator'});
                var mutate=(context,action,payload,callback)=>db.transaction(callback);
                var passwordPolicy=(password)=>true;
                var audit=(context,result,action,subject,resource,summary)=>{ if(failAudit) { throw new IllegalStateException('验证审计失败'); } };
                """ + source.replaceFirst("(?m)^import cn\\.hutool\\.core\\.util\\.IdUtil\\R", "");
        String script = source;
        var body = Map.of("id", "validation-target", "version", 1, "password", "validation-password");
        var input = Map.<String,Object>of("body", body, "identityRuntime", new ControlFixture(),
                "iamSecurity", new CredentialFixture(), "failAudit", false);
        var rejected = assertInstanceOf(ExitValue.class, run(script, Map.of(
                "body", Map.of("id", "validation-missing", "creating", true), "identityRuntime", new ControlFixture(),
                "iamSecurity", new CredentialFixture(), "failAudit", false)));
        assertEquals(404, rejected.getValues()[0]);
        assertThrows(RuntimeException.class, () -> run(script, Map.of("body", body, "identityRuntime", new ControlFixture(),
                "iamSecurity", new CredentialFixture(), "failAudit", true)));
        assertEquals(1, count("iam_credential_t"));
        assertEquals(1, jdbc.queryForObject("select security_version from iam_account_security_t", Integer.class));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return run(script, input); });
            var second = executor.submit(() -> { start.await(); return run(script, input); });
            start.countDown();
            var outcomes = List.of(first.get(8, TimeUnit.SECONDS), second.get(8, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter(Map.class::isInstance).count());
            var stale = outcomes.stream().filter(ExitValue.class::isInstance).map(ExitValue.class::cast).findFirst().orElseThrow();
            assertEquals(409, stale.getValues()[0]);
        }
        assertEquals(2, count("iam_credential_t"));
        assertEquals(2, jdbc.queryForObject("select revision from iam_credential_t where status='ACTIVE'", Integer.class));
        assertEquals(2, jdbc.queryForObject("select security_version from iam_account_security_t", Integer.class));
    }

    private Object run(String source, Map<String, Object> inputs) {
        Map<String, Object> environment = new LinkedHashMap<>(inputs);
        environment.put("db", db);
        environment.put("identityTransport", new FingerprintStub());
        return MagicScript.create(source, null).execute(new MagicScriptContext(environment));
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    public static class FingerprintStub {
        public String protectedFingerprint(String digest, String purpose) { return digest; }
    }

    public static class ControlFixture {
        public Object control(Supplier<Object> callback) { return callback.get(); }
    }

    public static class CredentialFixture {
        public String hash(String password) { return "validation-new-hash"; }
    }
}
