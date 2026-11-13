package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class RolePageMagicTest {
    private static final String RESOURCE = "b3519d0888524b2d990c3e09822f6e73";
    private static final String TENANT = "tenant-a";
    private static final String OTHER = "tenant-b";
    private static final String APP = "app-a";
    private static final String APP_OTHER = "app-b";
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final Set<String> ROLE_KEYS = Set.of("tenantId", "id", "createId", "updateId", "deleted",
            "createTime", "updateTime", "status", "name", "codeNum", "description", "appId", "sortNum",
            "dataScope", "userCount", "permCount", "userIds");

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL"})
    void preservesRolePayloadAndThreeReadsAtTwentyAndOneHundred(String dialect) throws Exception {
        var fixture = new Fixture(dialect);
        var ids = IntStream.range(0, 120).mapToObj(i -> "role-%03d".formatted(i)).toList();
        fixture.user(TENANT, "ordinary-user", 0, 0);
        for (int i = 0; i < ids.size(); i++) {
            var id = ids.get(i);
            fixture.role(TENANT, APP, id, "角色 %03d".formatted(i), "code-%03d".formatted(i), i / 5, i / 5, 0);
            fixture.link(TENANT, id, "ordinary-user", null);
            fixture.permission(TENANT, id, "resource-1");
        }
        fixture.role(TENANT, APP_OTHER, "other-app", "其他应用角色", "other", 0, 0, 0);
        fixture.role(OTHER, APP, "role-000", "外租户同 ID", "foreign", 0, 0, 0);
        fixture.role(OTHER, APP, "foreign-role", "外租户角色", "foreign", 0, 0, 0);
        fixture.role(TENANT, APP, "deleted-role", "已删除角色", "deleted", 0, 0, 1);
        fixture.permission(OTHER, "role-000", "foreign-menu");

        for (int size : List.of(20, 100)) {
            var page = fixture.page(Map.of("pageSize", size, "tenantId", OTHER), 3);
            assertEquals(120L, total(page));
            assertEquals(ids.subList(0, size), ids(page));
            for (var role : rows(page)) {
                assertEquals(ROLE_KEYS, role.keySet());
                assertEquals(TENANT, role.get("tenantId"));
                assertEquals(APP, role.get("appId"));
                assertEquals("operator-create", role.get("createId"));
                assertEquals("operator-update", role.get("updateId"));
                assertEquals("validation-description", role.get("description"));
                assertEquals("OWNER", role.get("dataScope"));
                assertEquals(1L, number(role, "userCount"));
                assertEquals(1L, number(role, "permCount"));
                assertEquals("ordinary-user", role.get("userIds"));
            }
        }
        assertEquals(ids.subList(100, 120), ids(fixture.page(Map.of("pageNum", 2, "pageSize", 100), 3)));
        assertEquals(ids.subList(0, 10), ids(fixture.page(null, 3)));
        assertEquals(ids.subList(0, 100), ids(fixture.page(Map.of("pageNum", -1, "pageSize", 120), 3)));

        var pastLast = fixture.page(Map.of("pageNum", 3, "pageSize", 100), 1);
        assertEquals(120L, total(pastLast));
        assertEquals(List.of(), rows(pastLast));
        var otherApp = fixture.page(Map.of("appId", APP_OTHER, "tenantId", OTHER), 3);
        assertEquals(List.of("other-app"), ids(otherApp));
        assertEquals(APP_OTHER, rows(otherApp).getFirst().get("appId"));
        assertEquals(0L, total(fixture.page(Map.of("appId", "unregistered-app"), 1)));
        assertEquals(0L, total(fixture.page(Map.of("ids", "deleted-role,foreign-role,other-app"), 1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL"})
    void sortsGlobalDistinctCountsAndOnlyEnrichesValidUsers(String dialect) throws Exception {
        var fixture = new Fixture(dialect);
        fixture.role(TENANT, APP, "role-a", "运维-北", "OPS_01", 1, 1, 0);
        fixture.role(TENANT, APP, "role-b", "运维-南", "OPS_02", 1, 3, 0);
        fixture.role(TENANT, APP, "role-c", "查看员", "VIEW", 3, 2, 0);
        fixture.role(OTHER, APP, "role-c", "外租户同 ID", "OUTSIDE", 0, 9, 0);
        fixture.user(TENANT, "active", 0, 0);
        fixture.user(TENANT, "disabled", 0, 1);
        fixture.user(TENANT, "deleted-user", 1, 0);
        fixture.user(OTHER, "foreign-user", 0, 0);
        fixture.link(TENANT, "role-b", "active", null);
        fixture.link(TENANT, "role-c", "disabled", BASE_TIME.minusYears(1));
        fixture.link(TENANT, "role-c", "active", null);
        fixture.link(TENANT, "role-c", "active", null);
        fixture.link(TENANT, "role-c", "deleted-user", null);
        fixture.link(TENANT, "role-c", "missing-user", null);
        fixture.link(TENANT, "role-c", "foreign-user", null);
        fixture.link(TENANT, "role-c", null, null);
        fixture.link(OTHER, "role-c", "foreign-user", null);
        fixture.link(OTHER, "role-c", "active", null);
        fixture.permission(TENANT, "role-a", "permission-a");
        fixture.permission(TENANT, "role-a", "permission-b");
        fixture.permission(TENANT, "role-a", "permission-b");
        fixture.permission(TENANT, "role-a", null);
        fixture.permission(TENANT, "role-c", "orphan-resource");
        fixture.permission(OTHER, "role-c", "foreign-permission");

        var largest = fixture.page(Map.of("sortField", "userCount", "pageSize", 1), 3);
        assertEquals(3L, total(largest));
        assertEquals(List.of("role-c"), ids(largest));
        var userHeavy = rows(largest).getFirst();
        assertEquals(2L, number(userHeavy, "userCount"));
        assertEquals(1L, number(userHeavy, "permCount"));
        assertEquals("active,disabled", assertInstanceOf(String.class, userHeavy.get("userIds")));

        var permissionOrder = fixture.page(Map.of("sortField", "permCount", "sortDir", "desc"), 3);
        assertEquals(List.of("role-a", "role-c", "role-b"), ids(permissionOrder));
        assertEquals(List.of(2L, 1L, 0L), rows(permissionOrder).stream().map(row -> number(row, "permCount")).toList());
        assertEquals("", rows(permissionOrder).getFirst().get("userIds"));
        assertEquals(List.of("role-a", "role-b", "role-c"),
                ids(fixture.page(Map.of("sortField", "userCount", "sortDir", "asc"), 3)));
        assertEquals(List.of("role-b", "role-c", "role-a"),
                ids(fixture.page(Map.of("sortField", "createTime", "sortDir", "desc"), 3)));

        var filtered = fixture.page(Map.of("ids", "role-b,role-a,role-a,unknown,foreign",
                "name", "运维", "codeNum", "OPS_0_", "pageSize", 1), 3);
        assertEquals(2L, total(filtered));
        assertEquals(List.of("role-a"), ids(filtered));
        assertEquals(List.of("role-b"), ids(fixture.page(Map.of("ids", "role-a,role-b,role-c",
                "name", "运维", "codeNum", "OPS_0_", "pageSize", 1, "pageNum", 2), 3)));
        assertEquals(List.of(), rows(fixture.page(Map.of("name", "不存在"), 1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL"})
    void keepsNullsLastAndPreservesDefaultSortDirection(String dialect) throws Exception {
        var fixture = new Fixture(dialect);
        fixture.role(TENANT, APP, "no-sort", "空排序", "NULL", null, 0, 0);
        fixture.role(TENANT, APP, "low-sort", "低排序", "LOW", 1, 0, 0);
        fixture.role(TENANT, APP, "high-sort", "高排序", "HIGH", 2, 0, 0);
        var ascending = List.of("low-sort", "high-sort", "no-sort");
        var descending = List.of("high-sort", "low-sort", "no-sort");
        assertEquals(ascending, ids(fixture.page(Map.of("sortDir", "desc"), 3)));
        assertEquals(descending, ids(fixture.page(Map.of("sortField", ""), 3)));
        assertEquals(ascending, ids(fixture.page(Map.of("sortField", "sortNum", "sortDir", "asc"), 3)));
    }

    @Test
    void resolvesCaseVariantRoleLinksUsingTheDatabaseCollation() throws Exception {
        var fixture = new Fixture("MySQL");
        fixture.role(TENANT, APP, "role-canonical", "角色", "ROLE", 1, 0, 0);
        fixture.user(TENANT, "active", 0, 0);
        fixture.link(TENANT, "ROLE-CANONICAL", "active", null);
        fixture.link(TENANT, "role-canonical", "active", null);
        fixture.permission(TENANT, "ROLE-CANONICAL", "permission");
        var role = rows(fixture.page(Map.of("ids", "ROLE-CANONICAL"), 3)).getFirst();
        assertEquals("role-canonical", role.get("id"));
        assertEquals(1L, number(role, "userCount"));
        assertEquals(1L, number(role, "permCount"));
        assertEquals("active", role.get("userIds"));
    }

    @Test
    void rejectsAnExpiredSessionAndAnUnknownSortBeforeReading() throws Exception {
        var fixture = new Fixture("MySQL");
        assertThrows(RuntimeException.class, () -> fixture.execute(Map.of("sortField", "name; select 1")));
        assertEquals(0, fixture.source.statements.size());
        fixture.accounts.expired = true;
        var exit = assertInstanceOf(ExitValue.class, fixture.execute(Map.of("appId", APP)));
        assertEquals("-1", String.valueOf(exit.getValues()[0]));
        assertEquals("账号未登入或登入已过期", exit.getValues()[1]);
        assertEquals(0, fixture.source.statements.size());
    }

    @Test
    void sessionWithoutAnApplicationReturnsAnEmptyPageWithoutReadingRoles() throws Exception {
        var fixture = new Fixture("MySQL");
        fixture.accounts.appId = null;
        var page = fixture.page(Map.of(), 0);
        assertEquals(0L, total(page));
        assertEquals(List.of(), rows(page));
    }

    private static long total(Map<String, Object> page) {
        return number(page, "total");
    }

    private static long number(Map<String, Object> row, String key) {
        return assertInstanceOf(Number.class, row.get(key), key).longValue();
    }

    private static List<Map<String, Object>> rows(Map<String, Object> page) {
        return StructuredValues.objectRows(page.get("list"));
    }

    private static List<Object> ids(Map<String, Object> page) {
        return rows(page).stream().map(row -> row.get("id")).toList();
    }

    public static class Accounts {
        boolean expired;
        String appId = APP;

        public Map<String, Object> session() {
            if (expired) throw new IllegalStateException("Expired fixture session");
            return appId == null ? Map.of("tenantId", TENANT) : Map.of("appId", appId, "tenantId", TENANT);
        }
    }

    private static final class Fixture {
        final CountedSource source;
        final JdbcTemplate jdbc;
        final Accounts accounts = new Accounts();

        Fixture(String dialect) {
            source = new CountedSource(dialect);
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    CREATE TABLE rm_role_t(
                        tenant_id VARCHAR(32), id VARCHAR(32), create_id VARCHAR(32), update_id VARCHAR(32),
                        deleted INTEGER, create_time TIMESTAMP, update_time TIMESTAMP, status INTEGER,
                        name VARCHAR(100), code_num VARCHAR(100), description VARCHAR(255), app_id VARCHAR(32),
                        sort_num INTEGER, data_scope VARCHAR(16), PRIMARY KEY (tenant_id, id)
                    )
                    """);
            jdbc.execute("""
                    CREATE TABLE rm_user_t(id VARCHAR(32), tenant_id VARCHAR(32), deleted INTEGER,
                        status INTEGER, PRIMARY KEY (tenant_id, id))
                    """);
            jdbc.execute("""
                    CREATE TABLE rm_user_role_rela_t(tenant_id VARCHAR(32), id VARCHAR(32), role_id VARCHAR(32),
                        user_id VARCHAR(32), end_time TIMESTAMP, PRIMARY KEY (tenant_id, id))
                    """);
            jdbc.execute("""
                    CREATE TABLE rm_role_menu_rela_t(tenant_id VARCHAR(32), id VARCHAR(32), role_id VARCHAR(32),
                        app_resource_id VARCHAR(32), PRIMARY KEY (tenant_id, id))
                    """);
        }

        void role(String tenant, String app, String id, String name, String code, Integer order, int second, int deleted) {
            jdbc.update("""
                    INSERT INTO rm_role_t VALUES (?,?,'operator-create','operator-update',?,?,?,0,?,?,
                        'validation-description',?,?,'OWNER')
                    """, tenant, id, deleted, BASE_TIME.plusSeconds(second), BASE_TIME, name, code, app, order);
        }

        void user(String tenant, String id, int deleted, int status) {
            jdbc.update("INSERT INTO rm_user_t VALUES (?,?,?,?)", id, tenant, deleted, status);
        }

        void link(String tenant, String role, String user, LocalDateTime until) {
            jdbc.update("INSERT INTO rm_user_role_rela_t VALUES (?,?,?,?,?)",
                    tenant, rowKey(), role, user, until);
        }

        void permission(String tenant, String role, String resource) {
            jdbc.update("INSERT INTO rm_role_menu_rela_t VALUES (?,?,?,?)",
                    tenant, rowKey(), role, resource);
        }

        private static String rowKey() {
            return UUID.randomUUID().toString().replace("-", "");
        }

        Map<String, Object> page(Map<String, Object> body, int reads) throws Exception {
            var result = StructuredValues.objectMap(assertInstanceOf(Map.class, execute(body)));
            assertEquals(Set.of("total", "list"), result.keySet());
            assertInstanceOf(List.class, result.get("list"));
            assertEquals(reads, source.statements.size());
            assertTrue(source.statements.stream().allMatch(sql -> sql.startsWith("select") || sql.startsWith("with")));
            return result;
        }

        Object execute(Map<String, Object> body) throws Exception {
            var text = CanonicalMagicSources.byId(RESOURCE)
                    .replaceAll("(?m)^import (?:tableRead|tableSort|tenantAccounts|tenantRuntime)[ \\t]*\\r?\\n", "");
            var script = MagicScript.create(text, null);
            var context = new HashMap<String, Object>();
            context.put("tableRead", new StructuredTableReadMagicModule(jdbc));
            context.put("tableSort", new TableSortMagicModule());
            context.put("tenantRuntime", new TenantMagicModule(null));
            context.put("tenantAccounts", accounts);
            context.put("body", body);
            source.statements.clear();
            return TenantContext.call(TENANT, () -> script.execute(new MagicScriptContext(context)));
        }
    }

    private static final class CountedSource extends DriverManagerDataSource {
        final List<String> statements = new ArrayList<>();
        private final String dialectUrl;

        CountedSource(String dialect) {
            super("jdbc:h2:mem:role_page_" + UUID.randomUUID() + ";MODE=" + dialect
                    + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
                    + ("MySQL".equals(dialect) ? ";IGNORECASE=TRUE" : ";DEFAULT_NULL_ORDERING=HIGH"), "sa", "");
            dialectUrl = "jdbc:" + dialect.toLowerCase(Locale.ROOT) + "://role-page-validation";
        }

        @Override
        public Connection getConnection() throws SQLException {
            var connection = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getMetaData")) {
                            var metadata = connection.getMetaData();
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                    (ignored, metaMethod, metaArgs) -> metaMethod.getName().equals("getURL")
                                            ? dialectUrl : invoke(metadata, metaMethod, metaArgs));
                        }
                        if (method.getName().equals("prepareStatement")) {
                            statements.add(((String) args[0]).stripLeading().toLowerCase(Locale.ROOT));
                        }
                        return invoke(connection, method, args);
                    });
        }

        private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException error) {
                throw error.getCause();
            }
        }
    }
}

