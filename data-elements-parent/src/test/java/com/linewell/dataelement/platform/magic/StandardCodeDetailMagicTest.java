package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
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

class StandardCodeDetailMagicTest {
    private static final String ITEMS = "[{\"value\":\"validation\",\"label\":\"" + "x".repeat(4500) + "\"}]";
    private static final Set<String> DETAIL_KEYS = Set.of(
            "tid", "tenantId", "isDel", "dictName", "dictCode", "dictItemValue",
            "dataCategoryId", "dataCategoryName", "dataCategoryPath", "description",
            "createdBy", "createdTime", "updatedBy", "updatedTime",
            "codeSet", "codeValue", "codeName", "parentCode", "sortNo", "status");

    @Test
    void fiftyLookupLimitKeepsReadsBoundedForTwentyAndHundredClientItems() throws Exception {
        var fixture = new Fixture();
        var lookups = IntStream.range(0, 100).mapToObj(i -> "requested-" + i).toList();
        for (String lookup : lookups) {
            fixture.code(lookup, "tenant-a", 0, "dict-" + lookup, "set-" + lookup, "__HEADER__");
        }
        for (int size : List.of(20, 50)) {
            assertBatch(fixture, lookups.subList(0, size));
            assertEquals(1, fixture.source.reads);
        }
        int hundredReads = 0;
        for (int offset : List.of(0, 50)) {
            assertBatch(fixture, lookups.subList(offset, offset + 50));
            hundredReads += fixture.source.reads;
        }
        assertEquals(2, hundredReads);
        for (Object invalid : List.of(lookups.subList(0, 51), java.util.Collections.nCopies(51, lookups.getFirst()),
                List.of(), List.of(" "), List.of("x".repeat(256)), "not-a-list")) {
            var rejected = assertInstanceOf(ExitValue.class, fixture.run(Map.of("lookups", invalid)));
            assertEquals(400, rejected.getValues()[0]);
            assertEquals(0, fixture.source.reads);
        }
    }

    @Test
    void bothModesPreferTidThenDictCodeThenCodeSetWithinTheCurrentTenant() throws Exception {
        var fixture = new Fixture();
        fixture.code("priority-all", "tenant-a", 0, "ordinary-dict", "ordinary-set", "200");
        fixture.code("header-dict", "tenant-a", 0, "priority-all", "unused", "__HEADER__");
        fixture.code("header-set", "tenant-a", 0, "unused", "priority-all", "__HEADER__");
        fixture.code("header-z-dict", "tenant-a", 0, "priority-alias", "first", "__HEADER__");
        fixture.code("header-a-set", "tenant-a", 0, "second", "priority-alias", "__HEADER__");
        fixture.code("header-set-only", "tenant-a", 0, "different", "set-only", "__HEADER__");
        fixture.code("header-duplicate-a", "tenant-a", 0, "duplicate", "same-a", "__HEADER__");
        fixture.code("header-duplicate-z", "tenant-a", 0, "duplicate", "same-z", "__HEADER__");
        fixture.code("foreign-only", "tenant-b", 0, "priority-alias", "foreign", "__HEADER__");
        fixture.code("deleted-only", "tenant-a", 1, "priority-alias", "deleted", "__HEADER__");

        var batched = fixture.read(Map.of("lookups", List.of("priority-all", "priority-alias", "set-only", "duplicate",
                "foreign-only", "deleted-only", "ordinary-dict", "not-found"), "tenantId", "tenant-b"));
        assertEquals(1, fixture.source.reads);
        assertDetail("priority-all", batched.get("priority-all"));
        assertDetail("header-z-dict", batched.get("priority-alias"));
        assertDetail("header-set-only", batched.get("set-only"));
        assertDuplicateHeader(batched.get("duplicate"));
        for (String missing : List.of("foreign-only", "deleted-only", "ordinary-dict", "not-found")) {
            assertMissing(missing, batched.get(missing));
        }

        assertDetail("priority-all", fixture.read(Map.of("tid", "priority-all", "tenantId", "tenant-b")));
        assertDetail("header-z-dict", fixture.read(Map.of("id", "priority-alias")));
        assertDetail("header-z-dict", fixture.read(Map.of("dictCode", "priority-alias")));
        assertDetail("header-set-only", fixture.read(Map.of("codeSet", "set-only")));
        assertDetail("header-set-only", fixture.read(Map.of("codeValue", "set-only")));
        assertDuplicateHeader(fixture.read(Map.of("tid", "duplicate")));
        assertMissing("foreign-only", fixture.read(Map.of("tid", "foreign-only")));
        assertMissing("deleted-only", fixture.read(Map.of("tid", "deleted-only")));
        assertEquals(Map.of("found", false, "message", "标准代码标识不能为空", "dict_item_value", "[]"),
                fixture.read(Map.of()));
        assertEquals(0, fixture.source.reads);
    }

    @Test
    void batchUsesExactKeysAfterNativeMatchingAndKeepsTheLegacySingleLookup() throws Exception {
        var fixture = new Fixture();
        fixture.code("valid-code", "tenant-a", 0, "DisplayCode", "different", "__HEADER__");
        fixture.code("lowercase-header", "tenant-a", 0, "legacy", "legacy-set", "__header__");
        var batch = fixture.read(Map.of("lookups", List.of(" DisplayCode ", "DisplayCode", "displaycode", "legacy", "lowercase-header")));
        assertEquals(1, fixture.source.reads);
        assertEquals(Set.of("DisplayCode", "displaycode", "legacy", "lowercase-header"), batch.keySet());
        assertDetail("valid-code", batch.get("DisplayCode"));
        assertMissing("displaycode", batch.get("displaycode"));
        assertMissing("legacy", batch.get("legacy"));
        assertDetail("lowercase-header", batch.get("lowercase-header"));
        assertDetail("valid-code", fixture.read(Map.of("tid", "displaycode")));
        assertDetail("lowercase-header", fixture.read(Map.of("tid", "legacy")));
    }

    private static void assertBatch(Fixture fixture, List<String> lookups) throws Exception {
        var result = fixture.read(Map.of("lookups", lookups));
        assertEquals(Set.copyOf(lookups), result.keySet());
        for (String lookup : lookups) assertDetail(lookup, result.get(lookup));
    }

    private static void assertDetail(String tid, Object value) {
        var record = StructuredValues.objectMap(value);
        assertEquals(DETAIL_KEYS, record.keySet());
        assertEquals(tid, record.get("tid"));
        assertEquals("tenant-a", record.get("tenantId"));
        assertEquals(0, ((Number) record.get("isDel")).intValue());
        assertEquals(0, ((Number) record.get("status")).intValue());
        assertEquals(ITEMS, record.get("dictItemValue"));
    }

    private static void assertDuplicateHeader(Object value) {
        var record = StructuredValues.objectMap(value);
        var tid = String.valueOf(record.get("tid"));
        assertTrue(Set.of("header-duplicate-a", "header-duplicate-z").contains(tid));
        assertDetail(tid, record);
    }

    private static void assertMissing(String lookup, Object value) {
        assertEquals(Map.of("found", false, "tid", lookup, "message", "标准代码不存在或已删除", "dict_item_value", "[]"), value);
    }

    public static final class Log {
        public void warn(String template, Object value) {}
    }

    private static final class Fixture {
        final CountedSource source = new CountedSource();
        final JdbcTemplate jdbc = new JdbcTemplate(source);
        final SQLModule db;

        Fixture() {
            jdbc.execute("""
                    create table da_metadata_code_t(
                        tid varchar_ignorecase(32) primary key, tenant_id varchar(32), is_del int,
                        dict_name varchar(255), dict_code varchar_ignorecase(255), dict_item_value clob,
                        data_category_id varchar(100), data_category_name varchar(100), data_category_path varchar(500),
                        description varchar(1000), created_by varchar(32), created_time timestamp, updated_by varchar(32),
                        updated_time timestamp, code_set varchar_ignorecase(100), code_value varchar_ignorecase(128),
                        code_name varchar(255), parent_code varchar(128), sort_no int, status smallint)
                    """);
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
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of());
        }

        void code(String tid, String tenant, int deleted, String dictCode, String codeSet, String codeValue) {
            jdbc.update("""
                    insert into da_metadata_code_t
                        (tid, tenant_id, is_del, dict_name, dict_code, dict_item_value, code_set, code_value, sort_no, status)
                    values (?, ?, ?, '同名标准代码', ?, ?, ?, ?, 0, 0)
                    """, tid, tenant, deleted, dictCode, ITEMS, codeSet, codeValue);
        }

        Map<String, Object> read(Map<String, Object> body) throws Exception {
            return StructuredValues.objectMap(run(body));
        }

        Object run(Map<String, Object> body) throws Exception {
            var code = CanonicalMagicSources.byId("684305fb587f4e60904f927b36331e34")
                    .replaceAll("(?m)^import (?:tenantRuntime|tableRead|log|db)[ \\t]*\\r?\\n", "");
            var script = MagicScript.create(code, null);
            var vars = Map.<String, Object>of("db", db, "tenantRuntime", new TenantMagicModule(null),
                    "tableRead", new StructuredTableReadMagicModule(jdbc), "log", new Log(), "body", body);
            source.reads = 0;
            return TenantContext.call("tenant-a", () -> script.execute(new MagicScriptContext(vars)));
        }
    }

    private static final class CountedSource extends DriverManagerDataSource {
        int reads;

        CountedSource() {
            super("jdbc:h2:mem:standard_code_detail_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Override
        public Connection getConnection() throws SQLException {
            var connection = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement")) {
                            var sql = ((String) args[0]).stripLeading().toLowerCase(Locale.ROOT);
                            if (sql.startsWith("select") || sql.startsWith("with")) reads++;
                        }
                        try {
                            return method.invoke(connection, args);
                        } catch (InvocationTargetException error) {
                            throw error.getCause();
                        }
                    });
        }
    }
}
