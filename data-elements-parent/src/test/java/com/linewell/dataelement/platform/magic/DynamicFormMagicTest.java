package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.dialect.MySQLDialect;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

class DynamicFormMagicTest {
    private static final String RESOURCE_ID = "b5dd6283a2af4141ac6e53d31ed8f507";

    @Test
    void listUsesOneQueryAtTwentyAndOneHundredRowsAndFiltersByAuthenticatedTenant() throws Exception {
        for (int size : List.of(20, 100)) {
            var fixture = new Fixture();
            for (int index = 1; index <= size; index++) {
                fixture.form("active-" + index, "tenant-a", "form-" + index, "env", index, 0);
            }
            fixture.form("foreign", "tenant-b", "form-1", "env", 0, 0);
            fixture.form("deleted", "tenant-a", "form-1", "env", 0, 1);
            fixture.form("other-env", "tenant-a", "form-1", "other-env", 0, 0);

            var rows = fixture.list(Map.of("env", " env ", "tenantId", "tenant-b"));
            assertEquals(1, fixture.sql.statements);
            assertEquals(size, rows.size());
            assertEquals("active-1", rows.getFirst().get("tid"));
            assertEquals("active-" + size, rows.getLast().get("tid"));
            assertTrue(rows.stream().allMatch(row -> row.get("tenantId").equals("tenant-a")));
            assertEquals(List.of(), fixture.list(Map.of("tid", "foreign")));
            assertEquals("[]", fixture.list(Map.of("formName", " form-1 ", "env", "env")).getFirst().get("formJson"));
        }
    }

    @Test
    void savesFullPayloadAndSoftDeletesWithoutChangingForeignRows() throws Exception {
        var fixture = new Fixture();
        fixture.form("foreign", "tenant-b", "shared-name", "env", 0, 0);
        fixture.form("deleted", "tenant-a", "shared-name", "env", 0, 1);
        var input = Map.of("formName", " shared-name ", "formJson", " [] ", "env", " env ",
            "formConfig", "{\"layout\":2}", "tenantId", "tenant-b");

        String id = (String) fixture.execute("save", input);
        assertEquals("tenant-a", fixture.row(id).get("tenant_id"));
        assertEquals("shared-name", fixture.row(id).get("form_name"));
        assertEquals("{\"layout\":2}", fixture.row(id).get("form_config"));
        assertTrue(causedBy(assertThrows(Exception.class, () -> fixture.execute("save", input)),
            "env运行环境下已存在同名表单"));
        assertEquals(1, fixture.list(Map.of("formName", "shared-name", "env", "env")).size());

        assertEquals(id, fixture.execute("save",
            Map.of("tid", id, "formName", "renamed", "formJson", "[ {} ]", "env", "env")));
        assertEquals("[ {} ]", fixture.row(id).get("form_json"));
        assertNull(fixture.row(id).get("form_config"));
        assertTrue(causedBy(assertThrows(Exception.class, () -> fixture.execute("save",
            Map.of("tid", "foreign", "formName", "unauthorized", "formJson", "[]", "env", "env"))),
            "表单不存在或已删除"));
        assertEquals(false, fixture.execute("delete", Map.of("tid", "foreign", "tenantId", "tenant-b")));
        assertEquals("shared-name", fixture.row("foreign").get("form_name"));
        assertEquals(0, ((Number) fixture.row("foreign").get("is_del")).intValue());
        assertEquals(true, fixture.execute("delete", Map.of("tid", id)));
        assertEquals(false, fixture.execute("delete", Map.of("tid", id)));
        assertEquals(List.of(), fixture.list(Map.of("tid", id)));
    }

    private static boolean causedBy(Throwable error, String message) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof IllegalArgumentException && message.equals(cause.getMessage())) {
                return true;
            }
        }
        return false;
    }

    public static class SessionTenant {
        public String id() {
            return TenantContext.requireTenantId();
        }
    }

    private static class QueryCounter implements SQLInterceptor {
        int statements;

        @Override
        public void preHandle(BoundSql sql, RequestEntity request) {
            statements++;
        }
    }

    private static class Fixture {
        final JdbcTemplate jdbc;
        final QueryCounter sql = new QueryCounter();
        final SQLModule db;
        final MagicScript script;

        Fixture() throws Exception {
            var source = new DriverManagerDataSource("jdbc:h2:mem:dynamic_form_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                CREATE TABLE sym_form (
                    tid VARCHAR(32) PRIMARY KEY, form_name VARCHAR(100) NOT NULL,
                    form_json TEXT NOT NULL, form_config TEXT, env VARCHAR(32) NOT NULL,
                    created_time TIMESTAMP(6), updated_time TIMESTAMP(6), is_del SMALLINT,
                    tenant_id VARCHAR(32) NOT NULL
                )
                """);
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            var dialects = new DialectAdapter();
            dialects.add(new MySQLDialect() {
                @Override
                public boolean match(String url) {
                    return url.contains(":h2:");
                }
            });
            db.setDialectAdapter(dialects);
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setSqlInterceptors(List.of(sql));
            db.setNamedTableInterceptors(List.of());
            db.setResultProvider(new DefaultResultProvider("form-validation"));
            script = MagicScript.create(CanonicalMagicSources.byId(RESOURCE_ID)
                .replaceFirst("(?m)^import tenantRuntime\\r?\\n", ""), null);
        }

        Object execute(String action, Map<String, ?> body) {
            try (var ignored = TenantContext.use("tenant-a")) {
                return script.execute(new MagicScriptContext(Map.of(
                    "action", action, "body", body, "db", db, "tenantRuntime", new SessionTenant())));
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list(Map<String, ?> filters) {
            return (List<Map<String, Object>>) execute("get", filters);
        }

        void form(String id, String tenant, String name, String env, int offset, int deleted) {
            jdbc.update("""
                INSERT INTO sym_form (tid, form_name, form_json, env, created_time, is_del, tenant_id)
                VALUES (?, ?, '[]', ?, DATEADD('SECOND', ?, TIMESTAMP '2026-01-01 00:00:00'), ?, ?)
                """, id, name, env, offset, deleted, tenant);
        }

        Map<String, Object> row(String id) {
            return jdbc.queryForMap("SELECT * FROM sym_form WHERE tid = ?", id);
        }
    }
}
