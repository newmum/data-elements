package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class MarketShoppingCartRemoveMagicTest {
    private static final String RESOURCE_ID = "8cc51c590a964ad2b9a5b76656c5b3ce";
    private static final String TENANT = "tenant-a";
    private static final String DEPARTMENT = "department";
    private static final String LOCK_KEY = "data-elements:market:cart:" + TENANT + ":" + DEPARTMENT;
    private static final Map<String, String> ACTOR = Map.of("id", "operator", "orgId", DEPARTMENT);

    @ParameterizedTest
    @CsvSource({"MySQL,20", "MySQL,100", "PostgreSQL,20", "PostgreSQL,100"})
    void removesOneDepartmentRecordWithOneWriteAndReturnsTrue(String mode, int rows) throws Exception {
        var fixture = new Fixture(mode);
        for (int i = 0; i < rows; i++) {
            fixture.cart("validation-" + i, TENANT, DEPARTMENT, 0);
        }
        fixture.cart("other-tenant", "tenant-b", DEPARTMENT, 0);
        fixture.cart("other-org", TENANT, "other", 0);

        var input = Map.of("tid", "validation-0", "tenantId", "tenant-b", "applyOrgId", "other",
                "userId", "forged", "updatedBy", "forged");
        assertEquals(true, fixture.remove(input));
        assertEquals(1, fixture.statements.size());
        assertTrue(fixture.statements.getFirst().startsWith("update"));
        assertEquals(1, fixture.deleted("validation-0"));
        assertEquals("operator", fixture.value("validation-0", "updated_by"));
        assertEquals("teammate", fixture.value("validation-0", "apply_user_id"));
        assertEquals(0, fixture.deleted("other-tenant"));
        assertEquals(0, fixture.deleted("other-org"));
        assertEquals(rows + 1, fixture.activeRows());
        verify(fixture.redis).getLock(LOCK_KEY);
        verify(fixture.lock).tryLock();
        verify(fixture.lock).unlock();

        assertExit(404, fixture.remove(input));
        assertEquals(1, fixture.statements.size());
        assertEquals(rows + 1, fixture.activeRows());
    }

    @Test
    void foreignMissingAndDeletedRowsCannotBeRemovedOrDisclosed() throws Exception {
        var fixture = new Fixture("MySQL");
        fixture.cart("other-tenant", "tenant-b", DEPARTMENT, 0);
        fixture.cart("other-org", TENANT, "other", 0);
        fixture.cart("deleted", TENANT, DEPARTMENT, 1);
        for (String id : List.of("other-tenant", "other-org", "deleted", "missing")) {
            assertExit(404, fixture.remove(Map.of("tid", id, "tenantId", "tenant-b", "orgId", "other")));
            assertEquals(1, fixture.statements.size());
        }
        assertEquals(0, fixture.deleted("other-tenant"));
        assertEquals(0, fixture.deleted("other-org"));
        assertEquals(1, fixture.deleted("deleted"));
    }

    @Test
    void dryRunChecksTheSameScopeWithoutLockOrWrite() throws Exception {
        var fixture = new Fixture("PostgreSQL");
        fixture.cart("mine", TENANT, DEPARTMENT, 0);
        fixture.cart("other", TENANT, "other", 0);

        assertEquals(Map.of("dryRun", true, "valid", true), fixture.remove(Map.of("tid", "mine", "dryRun", true)));
        assertEquals(1, fixture.statements.size());
        assertTrue(fixture.statements.getFirst().startsWith("select"));
        assertExit(404, fixture.remove(Map.of("tid", "other", "dryRun", true, "orgId", "other")));
        assertEquals(0, fixture.deleted("mine"));
        assertEquals(0, fixture.deleted("other"));
        verifyNoInteractions(fixture.redis, fixture.lock);
    }

    @Test
    void validationAndUnavailableLockHaveNoSqlSideEffects() throws Exception {
        var fixture = new Fixture("MySQL");
        assertExit(400, fixture.remove(Map.of("tid", true)));
        assertExit(400, fixture.remove(Map.of("tid", " ")));
        assertExit(400, fixture.remove(Map.of("tid", "validation", "dryRun", "true")));
        assertExit(401, fixture.remove(Map.of("tid", "validation"), "", ACTOR));
        assertExit(401, fixture.remove(Map.of("tid", "validation"), TENANT, Map.of("orgId", DEPARTMENT)));
        assertExit(409, fixture.remove(Map.of("tid", "validation", "orgId", DEPARTMENT),
                TENANT, Map.of("id", "operator")));
        verifyNoInteractions(fixture.redis, fixture.lock);

        when(fixture.lock.tryLock()).thenReturn(false);
        assertExit(409, fixture.remove(Map.of("tid", "validation")));
        when(fixture.lock.tryLock()).thenThrow(new IllegalStateException("validation lock unavailable"));
        assertExit(503, fixture.remove(Map.of("tid", "validation")));
        assertEquals(List.of(), fixture.statements);
        verify(fixture.lock, org.mockito.Mockito.never()).unlock();
    }

    @Test
    void releasesTheLockWhenTheUpdateFails() throws Exception {
        var fixture = new Fixture("MySQL");
        fixture.cart("mine", TENANT, DEPARTMENT, 0);
        fixture.jdbc.execute("ALTER TABLE data_market_shopping_cart_t ADD CHECK (is_del = 0)");
        assertThrows(RuntimeException.class, () -> fixture.remove(Map.of("tid", "mine")));
        assertEquals(0, fixture.deleted("mine"));
        verify(fixture.lock).unlock();
    }

    private static void assertExit(int code, Object result) {
        var exit = assertInstanceOf(ExitValue.class, result);
        assertEquals(code, ((Number) exit.getValues()[0]).intValue());
    }

    public record Tenant(String id) {}

    private static final class Fixture {
        final JdbcTemplate jdbc;
        final RedissonClient redis = mock(RedissonClient.class);
        final RLock lock = mock(RLock.class);
        final List<String> statements = new ArrayList<>();
        private final SQLModule db;
        private final String script;

        Fixture(String mode) throws Exception {
            var source = new DriverManagerDataSource("jdbc:h2:mem:market_cart_remove_" + UUID.randomUUID()
                    + ";MODE=" + mode + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(source);
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            var columns = new ColumnMapperAdapter();
            columns.add(new CamelColumnMapperProvider());
            columns.setDefault("camel");
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of(new SQLInterceptor() {
                @Override
                public void preHandle(BoundSql sql, RequestEntity request) {
                    statements.add(sql.getSql().stripLeading().toLowerCase(Locale.ROOT));
                }
            }));
            when(redis.getLock(LOCK_KEY)).thenReturn(lock);
            when(lock.tryLock()).thenReturn(true);
            script = CanonicalMagicSources.byId(RESOURCE_ID).replaceAll("(?m)^import [^\\r\\n]*\\R", "");
            jdbc.execute("""
                    CREATE TABLE data_market_shopping_cart_t (
                        tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, apply_org_id VARCHAR,
                        apply_user_id VARCHAR, is_del INT, updated_by VARCHAR, updated_time TIMESTAMP
                    )
                    """);
        }

        void cart(String id, String tenant, String department, int deleted) {
            jdbc.update("INSERT INTO data_market_shopping_cart_t "
                            + "(tid, tenant_id, apply_org_id, apply_user_id, is_del) VALUES (?, ?, ?, 'teammate', ?)",
                    id, tenant, department, deleted);
        }

        int deleted(String id) {
            return jdbc.queryForObject("SELECT is_del FROM data_market_shopping_cart_t WHERE tid=?", Integer.class, id);
        }

        int activeRows() {
            return jdbc.queryForObject("SELECT COUNT(*) FROM data_market_shopping_cart_t WHERE is_del=0", Integer.class);
        }

        String value(String id, String column) {
            return jdbc.queryForObject("SELECT " + column + " FROM data_market_shopping_cart_t WHERE tid=?",
                    String.class, id);
        }

        Object remove(Map<String, ?> body) {
            return remove(body, TENANT, ACTOR);
        }

        Object remove(Map<String, ?> body, String tenant, Map<String, String> actor) {
            statements.clear();
            return MagicScript.create(script, null).execute(new MagicScriptContext(Map.of(
                    "body", body, "db", db, "tenantRuntime", new Tenant(tenant), "redissonClient", redis,
                    "me", (Function<Object[], Object>) ignored -> actor,
                    "log", LoggerFactory.getLogger(MarketShoppingCartRemoveMagicTest.class))));
        }
    }
}
