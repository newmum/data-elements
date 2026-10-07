package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

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
}
