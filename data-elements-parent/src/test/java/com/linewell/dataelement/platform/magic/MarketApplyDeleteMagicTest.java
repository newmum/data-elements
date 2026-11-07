package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.linewell.dataelement.elasticsearch.EsCommonService;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
import org.ssssssss.script.runtime.ExitValue;

/** Runs the actual delete script with its transaction, structured readers and cart lock. */
class MarketApplyDeleteMagicTest {
    private static final String RESOURCE_ID = "1c10f955048340db9b921cb342b5e8f5";
    private static final String TENANT = "validation-tenant";
    private static final String DEPARTMENT = "validation-dept";
    private static final String ACTOR = "validation-applicant";
    private static final String MARKET_TYPE = "catalogSubscribe";
    private static final String DRAFT = "validation-draft";
    private static final String ES_INDEX = "dataassets_apply_form";

    @Test
    void canonicalResourceCompilesWithAllImports() throws Exception {
        MagicScript.create(CanonicalMagicSources.byId(RESOURCE_ID), null).compile();
    }

    @Test
    void twentyAndHundredRestoreWithFourReadsAndOneEligibilityCall() throws Exception {
        for (int count : List.of(20, 100)) {
            var fixture = new Fixture();
            var ids = IntStream.range(0, count).mapToObj(index -> "c%03d".formatted(index)).toList();
            ids.forEach(id -> fixture.catalog(id, TENANT, 0, 2));
            fixture.draft(ids.stream().collect(Collectors.joining(",")));

            assertThat(fixture.delete(true)).isEqualTo(true);

            assertThat(fixture.readCount()).isEqualTo(4);
            assertThat(fixture.counter.writes).isEqualTo(4);
            assertThat(fixture.eligibilityCalls).containsExactly(ids);
            assertThat(fixture.cartIds()).containsExactlyElementsOf(ids);
            assertThat(fixture.isDeleted("data_apply_form_t", DRAFT)).isEqualTo(1);
            assertThat(fixture.isDeleted("da_catalog_apply_form_rela", "validation-own-relation")).isEqualTo(1);
            assertThat(fixture.isDeleted("da_prop_t", "validation-own-prop")).isEqualTo(1);
            assertThat(fixture.isDeleted("da_prop_t", "validation-unrelated-prop")).isZero();
            assertThat(fixture.isDeleted("da_prop_t", "validation-foreign-prop")).isZero();
            assertThat(fixture.isDeleted("da_catalog_apply_form_rela", "validation-foreign-relation")).isZero();
            verify(fixture.redis).getLock("data-elements:market:cart:" + TENANT + ":" + DEPARTMENT);
            verify(fixture.lock).unlock();
            verify(fixture.es).deleteById(ES_INDEX, DRAFT);
            System.out.printf("Market delete/readd %d: 1 API call, 4 SQL reads, 4 SQL writes, 1 eligibility batch, 1 ES cleanup%n", count);
        }
    }

    @Test
    void sameDepartmentExistingCartAndActiveExactLegacyTokensAreSkipped() throws Exception {
        var fixture = new Fixture();
        var catalogs = List.of("first", "middle", "last", "active4", "rejected", "dept", "tenant",
                "cat_x", "cat%x", "cat\\x", "abc", "already", "deletedline", "otherline", "demand");
        catalogs.stream().filter(id -> !id.equals("already") && !id.equals("first"))
                .forEach(id -> fixture.catalog(id, TENANT, 0, 2));
        fixture.draft(String.join(",", catalogs));
        fixture.form("validation-legacy", TENANT, "validation-colleague", "validation-colleague", DEPARTMENT,
                MARKET_TYPE, 1, 0, "first, middle, xabc, catAx, catBx, catCx, last");
        fixture.form("validation-active-withdrawn", TENANT, ACTOR, ACTOR, DEPARTMENT,
                "CATALOG_SUBSCRIPTION", 4, 0, "active4,cat%x");
        fixture.form("validation-unrelated-demand", TENANT, ACTOR, ACTOR, DEPARTMENT, "need", 2, 0, "demand");
        fixture.form("validation-rejected", TENANT, ACTOR, ACTOR, DEPARTMENT, MARKET_TYPE, 5, 0, "rejected");
        fixture.form("validation-other-dept", TENANT, ACTOR, ACTOR, "validation-other-dept",
                MARKET_TYPE, 2, 0, "dept");
        fixture.form("validation-foreign", "validation-other-tenant", ACTOR, ACTOR, DEPARTMENT,
                MARKET_TYPE, 2, 0, "tenant");
        fixture.cart("validation-cart-a", TENANT, DEPARTMENT, "already", 0);
        fixture.cart("validation-cart-b", TENANT, DEPARTMENT, "already", 0);
        fixture.cart("validation-cart-deleted", TENANT, DEPARTMENT, "deletedline", 1);
        fixture.cart("validation-cart-foreign", "validation-other-tenant", DEPARTMENT, "otherline", 0);

        assertThat(fixture.delete(true)).isEqualTo(true);

        assertThat(fixture.cartIds()).containsExactly("abc", "already", "already", "cat\\x", "cat_x", "deletedline",
                "demand", "dept", "otherline", "rejected", "tenant");
        assertThat(fixture.readCount()).isEqualTo(4);
        assertThat(fixture.counter.writes).isEqualTo(4);
        assertThat(fixture.eligibilityCalls).containsExactly(List.of("abc", "cat\\x", "cat_x", "deletedline",
                "demand", "dept", "otherline", "rejected", "tenant"));
    }

    @Test
    void tenantOwnerTypeAndDraftStateAreAllRequiredBeforeWriting() throws Exception {
        var fixture = new Fixture();
        fixture.form("validation-foreign", "validation-other-tenant", ACTOR, ACTOR, DEPARTMENT, MARKET_TYPE, 0, 0, "c001");
        fixture.form("validation-other", TENANT, "validation-other", ACTOR, DEPARTMENT, MARKET_TYPE, 0, 0, "c001");
        fixture.form("validation-other-type", TENANT, ACTOR, ACTOR, DEPARTMENT, "CATALOG_SUBSCRIPTION", 0, 0, "c001");
        fixture.form("validation-submitted", TENANT, ACTOR, ACTOR, DEPARTMENT, MARKET_TYPE, 2, 0, "c001");
        fixture.form("validation-deleted", TENANT, ACTOR, ACTOR, DEPARTMENT, MARKET_TYPE, 0, 1, "c001");
        fixture.form("validation-no-status", TENANT, ACTOR, ACTOR, DEPARTMENT, MARKET_TYPE, null, 0, "c001");

        for (String denied : List.of("validation-foreign", "validation-other", "validation-other-type", "validation-deleted")) {
            assertCode(fixture.run(Map.of("tid", denied, "addToCart", true, "tenantId", "validation-other-tenant",
                    "applyUserId", "validation-other", "orgId", "validation-other-dept")), 404);
        }
        assertCode(fixture.run(Map.of("tid", "validation-submitted")), 409);
        assertCode(fixture.run(Map.of("tid", "validation-no-status")), 409);
        assertThat(fixture.readCount()).isEqualTo(6);
        assertThat(fixture.counter.writes).isZero();
        verifyNoInteractions(fixture.es, fixture.redis, fixture.lock);

        fixture.form("validation-legacy", TENANT, null, ACTOR, DEPARTMENT, MARKET_TYPE, 0, 0, "c001");
        assertThat(fixture.run(Map.of("tid", "validation-legacy"))).isEqualTo(true);
        assertThat(fixture.isDeleted("data_apply_form_t", "validation-legacy")).isEqualTo(1);
    }

    @Test
    void unavailableDirectoryPreventsAnyDeleteAndUserCanUncheckReadd() throws Exception {
        var fixture = new Fixture();
        fixture.draft("c001,c002");
        fixture.catalog("c001", TENANT, 0, 2);
        fixture.catalog("c002", "validation-other-tenant", 0, 2);

        assertCode(fixture.run(Map.of("tid", DRAFT, "addToCart", true, "dryRun", true)), 409);
        assertCode(fixture.delete(true), 409);
        assertThat(fixture.counter.writes).isZero();
        assertThat(fixture.eligibilityCalls).isEmpty();
        assertThat(fixture.isDeleted("data_apply_form_t", DRAFT)).isZero();
        verifyNoInteractions(fixture.es);
        verify(fixture.lock).unlock();

        assertThat(fixture.delete(false)).isEqualTo(true);
        assertThat(fixture.isDeleted("data_apply_form_t", DRAFT)).isEqualTo(1);
        assertThat(fixture.cartIds()).isEmpty();
    }

    @Test
    void unpublishedOrIneligibleDirectoryPreservesTheDraftUntilUserUnchecksReadd() throws Exception {
        var fixture = new Fixture();
        fixture.draft("c001,c002");
        fixture.catalog("c001", TENANT, 0, 2);
        fixture.catalog("c002", TENANT, 0, 2);
        fixture.ineligible = Set.of("c002");

        for (boolean dryRun : List.of(true, false)) {
            var result = fixture.run(Map.of("tid", DRAFT, "addToCart", true, "dryRun", dryRun,
                    "appId", "validation-untrusted-app"));
            assertCode(result, 409);
            assertThat(((ExitValue) result).getValues()[1].toString()).contains("取消重新加购");
        }
        assertThat(fixture.eligibilityCalls).containsExactly(List.of("c001", "c002"), List.of("c001", "c002"));
        assertThat(fixture.isDeleted("data_apply_form_t", DRAFT)).isZero();
        assertThat(fixture.isDeleted("da_catalog_apply_form_rela", "validation-own-relation")).isZero();
        assertThat(fixture.isDeleted("da_prop_t", "validation-own-prop")).isZero();
        assertThat(fixture.cartIds()).isEmpty();
        assertThat(fixture.counter.writes).isZero();
        verify(fixture.lock).unlock();
        verifyNoInteractions(fixture.es);

        int before = fixture.readCount();
        assertThat(fixture.delete(false)).isEqualTo(true);
        assertThat(fixture.readCount() - before).isEqualTo(1);
        assertThat(fixture.eligibilityCalls).hasSize(2);
        assertThat(fixture.isDeleted("data_apply_form_t", DRAFT)).isEqualTo(1);
    }

    @Test
    void transientEligibilityFailureIsRetryableAndLeavesTheDraftUntouched() throws Exception {
        var fixture = new Fixture();
        fixture.draft("c001");
        fixture.catalog("c001", TENANT, 0, 2);
        fixture.eligibilityUnavailable = true;

        var failure = fixture.delete(true);
        assertCode(failure, 502);
        assertThat(((ExitValue) failure).getValues()[1].toString()).contains("稍后重试");
        assertThat(fixture.isDeleted("data_apply_form_t", DRAFT)).isZero();
        assertThat(fixture.counter.writes).isZero();
        assertThat(fixture.cartIds()).isEmpty();
        verify(fixture.lock).unlock();
        verifyNoInteractions(fixture.es);

        fixture.eligibilityUnavailable = false;
        assertThat(fixture.delete(true)).isEqualTo(true);
        assertThat(fixture.cartIds()).containsExactly("c001");
    }

    @Test
    void draftsWithOnlyExistingCartOrApplicationEntriesNeverCheckEligibility() throws Exception {
        var fixture = new Fixture();
        fixture.draft("c001,c002");
        fixture.cart("validation-cart", TENANT, DEPARTMENT, "c001", 0);
        fixture.form("validation-active", TENANT, ACTOR, ACTOR, DEPARTMENT, MARKET_TYPE, 1, 0, "c002");

        assertThat(fixture.run(Map.of("tid", DRAFT, "addToCart", true, "dryRun", true))).isEqualTo(
                Map.of("dryRun", true, "valid", true, "tid", DRAFT, "readdCount", 0));
        assertThat(fixture.delete(true)).isEqualTo(true);
        assertThat(fixture.cartIds()).containsExactly("c001");
        assertThat(fixture.eligibilityCalls).isEmpty();
        assertThat(fixture.readCount()).isEqualTo(6);
    }

    @Test
    void onlyDeleteAllowsNullAndEmptyCatalogListsWithAnExplicitCompareAndSet() throws Exception {
        for (String catalogList : new String[]{null, ""}) {
            var fixture = new Fixture();
            fixture.draft(catalogList);

            assertThat(fixture.delete(false)).isEqualTo(true);

            assertThat(fixture.counter.draftUpdate).matches(catalogList == null
                    ? "(?s).*catalog_ids\\s+is\\s+null.*"
                    : "(?s).*catalog_ids\\s*=\\s*\\?.*");
            assertThat(fixture.isDeleted("data_apply_form_t", DRAFT)).isEqualTo(1);
            assertThat(fixture.readCount()).isEqualTo(1);
            assertThat(fixture.eligibilityCalls).isEmpty();
            verifyNoInteractions(fixture.redis, fixture.lock);
        }
    }

    @Test
    void dryRunHasNoSideEffectsAndACompetingLockFailsBeforeWriting() throws Exception {
        var fixture = new Fixture();
        fixture.draft("c001");
        fixture.catalog("c001", TENANT, 0, 2);

        assertThat(fixture.run(Map.of("tid", DRAFT, "addToCart", true, "dryRun", true))).isEqualTo(
                Map.of("dryRun", true, "valid", true, "tid", DRAFT, "readdCount", 1));
        assertCode(fixture.run(Map.of("tid", DRAFT, "dryRun", "true")), 400);
        assertThat(fixture.readCount()).isEqualTo(4);
        verifyNoInteractions(fixture.es, fixture.redis, fixture.lock);

        when(fixture.lock.tryLock()).thenReturn(false);
        assertCode(fixture.delete(true), 409);
        assertThat(fixture.readCount()).isEqualTo(5);
        assertThat(fixture.counter.writes).isZero();
        assertThat(fixture.isDeleted("data_apply_form_t", DRAFT)).isZero();
        assertThat(fixture.cartIds()).isEmpty();
        verify(fixture.lock, times(0)).unlock();
        verifyNoInteractions(fixture.es);
    }

    @Test
    void cartWriteFailureRollsBackDraftAndChildrenBeforeRetry() throws Exception {
        var fixture = new Fixture();
        fixture.draft("c001,c002");
        fixture.catalog("c001", TENANT, 0, 2);
        fixture.catalog("c002", TENANT, 0, 2);
        fixture.jdbc.execute("ALTER TABLE data_market_shopping_cart_t ADD CONSTRAINT validation_cart_failure CHECK (catalog_id <> 'c002')");

        assertThatThrownBy(() -> fixture.delete(true)).isInstanceOf(RuntimeException.class);

        assertThat(fixture.isDeleted("data_apply_form_t", DRAFT)).isZero();
        assertThat(fixture.isDeleted("da_catalog_apply_form_rela", "validation-own-relation")).isZero();
        assertThat(fixture.isDeleted("da_prop_t", "validation-own-prop")).isZero();
        assertThat(fixture.cartIds()).isEmpty();
        verify(fixture.lock).unlock();
        verifyNoInteractions(fixture.es);

        fixture.jdbc.execute("ALTER TABLE data_market_shopping_cart_t DROP CONSTRAINT validation_cart_failure");
        assertThat(fixture.delete(true)).isEqualTo(true);
        assertThat(fixture.cartIds()).containsExactly("c001", "c002");
    }

    @Test
    void lostDraftRaceDoesNotTouchChildrenAndIndexErrorDoesNotUndoSuccess() throws Exception {
        var fixture = new Fixture();
        fixture.draft("c001");
        fixture.counter.suppressDraft = true;

        assertCode(fixture.delete(false), 409);
        assertThat(fixture.counter.writes).isEqualTo(1);
        assertThat(fixture.isDeleted("da_catalog_apply_form_rela", "validation-own-relation")).isZero();
        assertThat(fixture.isDeleted("da_prop_t", "validation-own-prop")).isZero();
        verifyNoInteractions(fixture.es);

        fixture.counter.suppressDraft = false;
        fixture.indexFailure = true;
        assertThat(fixture.delete(false)).isEqualTo(true);
        assertThat(fixture.isDeleted("data_apply_form_t", DRAFT)).isEqualTo(1);
        verify(fixture.es).deleteById(ES_INDEX, DRAFT);
    }

    private static void assertCode(Object result, int expected) {
        assertThat(result).isInstanceOf(ExitValue.class);
        assertThat(((ExitValue) result).getValues()[0]).isEqualTo(expected);
    }

    public static final class Scope {
        public String id() {
            return TENANT;
        }
    }

    private static final class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final StructuredTableReadMagicModule reads;
        final QueryCounter counter = new QueryCounter();
        final EsCommonService es = mock(EsCommonService.class);
        final RedissonClient redis = mock(RedissonClient.class);
        final RLock lock = mock(RLock.class);
        final List<List<String>> eligibilityCalls = new ArrayList<>();
        Set<String> ineligible = Set.of();
        boolean eligibilityUnavailable;
        boolean indexFailure;
        int structuredReads;

        Fixture() {
            var source = new DriverManagerDataSource("jdbc:h2:mem:market_delete_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    CREATE TABLE data_apply_form_t(tid VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32),
                        apply_user_id VARCHAR(32),created_by VARCHAR(32),apply_org_id VARCHAR(32),type VARCHAR(32),
                        flow_status INT,is_del INT,catalog_ids VARCHAR(500),updated_time TIMESTAMP,updated_by VARCHAR(32))
                    """);
            jdbc.execute("""
                    CREATE TABLE da_catalog_apply_form_rela(tid VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32),
                        apply_form_id VARCHAR(32),is_del INT,updated_time TIMESTAMP,updated_by VARCHAR(32))
                    """);
            jdbc.execute("""
                    CREATE TABLE da_prop_t(tid VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32),
                        parent_id VARCHAR(32),data_type VARCHAR(32),is_del INT,updated_time TIMESTAMP,updated_by VARCHAR(32))
                    """);
            jdbc.execute("""
                    CREATE TABLE da_catalog_t(tid VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32),
                        is_del INT,asset_status INT,flow_status INT,target_table_id VARCHAR(32))
                    """);
            jdbc.execute("""
                    CREATE TABLE data_market_shopping_cart_t(tid VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32),
                        catalog_id VARCHAR(32),apply_user_id VARCHAR(32),apply_user_name VARCHAR(64),
                        apply_org_id VARCHAR(32),is_del INT,created_by VARCHAR(32),created_time TIMESTAMP,
                        updated_by VARCHAR(32),updated_time TIMESTAMP)
                    """);
            reads = TaskRuntimeMagicFixture.structuredReads(jdbc, sql -> structuredReads++);
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            db.setResultProvider(new DefaultResultProvider("market-delete-test"));
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
            columns.setDefault("camel");
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setSqlInterceptors(List.of(counter));
            db.setNamedTableInterceptors(List.of());
            when(redis.getLock("data-elements:market:cart:" + TENANT + ":" + DEPARTMENT)).thenReturn(lock);
            when(lock.tryLock()).thenReturn(true);
            doAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                assertThat(isDeleted("data_apply_form_t", DRAFT)).isEqualTo(1);
                if (indexFailure) throw new IllegalStateException("validation ES failure");
                return null;
            }).when(es).deleteById(ES_INDEX, DRAFT);
        }

        void form(String id, String tenant, String actor, String creator, String org, String type,
                Integer status, int deleted, String catalogs) {
            jdbc.update("INSERT INTO data_apply_form_t VALUES (?,?,?,?,?,?,?,?,?,NULL,NULL)",
                    id, tenant, actor, creator, org, type, status, deleted, catalogs);
        }

        void draft(String catalogs) {
            form(DRAFT, TENANT, ACTOR, ACTOR, "validation-old-dept", MARKET_TYPE, 0, 0, catalogs);
            jdbc.update("INSERT INTO da_catalog_apply_form_rela VALUES (?,?,?,0,NULL,NULL)", "validation-own-relation", TENANT, DRAFT);
            jdbc.update("INSERT INTO da_catalog_apply_form_rela VALUES (?,?,?,0,NULL,NULL)", "validation-foreign-relation", "validation-other-tenant", DRAFT);
            jdbc.update("INSERT INTO da_prop_t VALUES (?,?,?,?,0,NULL,NULL)", "validation-own-prop", TENANT, DRAFT, "apply_form");
            jdbc.update("INSERT INTO da_prop_t VALUES (?,?,?,?,0,NULL,NULL)", "validation-unrelated-prop", TENANT, DRAFT, "catalog");
            jdbc.update("INSERT INTO da_prop_t VALUES (?,?,?,?,0,NULL,NULL)", "validation-foreign-prop", "validation-other-tenant", DRAFT, "apply_form");
        }

        void catalog(String id, String tenant, int deleted, int flow) {
            jdbc.update("INSERT INTO da_catalog_t VALUES (?,?,?,2,?,?)", id, tenant, deleted, flow, "target-" + id);
        }

        void cart(String id, String tenant, String org, String catalogId, int deleted) {
            jdbc.update("INSERT INTO data_market_shopping_cart_t VALUES (?,?,?,NULL,NULL,?,?,NULL,NULL,NULL,NULL)",
                    id, tenant, catalogId, org, deleted);
        }

        int isDeleted(String table, String id) {
            return jdbc.queryForObject("SELECT is_del FROM " + table + " WHERE tid=?", Integer.class, id);
        }

        List<String> cartIds() {
            return jdbc.queryForList("SELECT catalog_id FROM data_market_shopping_cart_t "
                    + "WHERE tenant_id=? AND apply_org_id=? AND is_del=0 ORDER BY catalog_id", String.class, TENANT, DEPARTMENT);
        }

        int readCount() {
            return counter.reads + structuredReads;
        }

        Object delete(boolean addToCart) throws Exception {
            return run(Map.of("tid", DRAFT, "addToCart", addToCart));
        }

        Object run(Map<String, Object> body) throws Exception {
            String script = CanonicalMagicSources.byId(RESOURCE_ID)
                    .replaceAll("(?m)^import (?:[^\\r\\n]+ as (?:me|esCommonService|redissonClient|marketCatalogEligibility)|log|tableRead|tenantRuntime)\\R", "");
            String verified = "var me = () => body.isEmpty() ? validationIdentity : {}\n" + script;
            var user = Map.of("id", ACTOR, "orgId", DEPARTMENT, "userName", "验证申请人");
            return TenantContext.call(TENANT, () -> MagicScript.create(verified, null).execute(new MagicScriptContext(Map.of(
                    "body", body, "db", db, "tableRead", reads, "tenantRuntime", new Scope(),
                    "validationIdentity", user, "redissonClient", redis, "esCommonService", es, "log", mock(Logger.class),
                    "marketCatalogEligibility", (Function<Object[], Object>) this::checkEligibility))));
        }

        Object checkEligibility(Object[] args) {
            assertThat(args).hasSize(1);
            assertThat(args[0]).isInstanceOf(List.class);
            var rows = (List<?>) args[0];
            var ids = rows.stream().map(Map.class::cast).map(row -> {
                String id = (String) row.get("tid");
                assertThat(row.get("targetTableId")).isEqualTo("target-" + id);
                return id;
            }).toList();
            eligibilityCalls.add(ids);
            return eligibilityUnavailable ? Map.of() : Map.of("eligibleIds",
                    ids.stream().filter(id -> !ineligible.contains(id)).toList());
        }
    }

    private static final class QueryCounter implements SQLInterceptor {
        int reads;
        int writes;
        boolean suppressDraft;
        String draftUpdate;

        @Override
        public void preHandle(BoundSql sql, RequestEntity request) {
            String statement = sql.getSql().stripLeading().toLowerCase(Locale.ROOT);
            if (statement.startsWith("select")) {
                reads++;
            } else {
                writes++;
                if (statement.startsWith("update data_apply_form_t")) {
                    draftUpdate = statement;
                    if (suppressDraft) sql.setSql(sql.getSql() + " AND 1=0");
                }
            }
        }
    }
}
