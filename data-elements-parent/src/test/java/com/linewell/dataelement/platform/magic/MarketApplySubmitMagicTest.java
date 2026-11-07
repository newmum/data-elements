package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.elasticsearch.EsCommonService;
import com.linewell.dataelement.platform.magic.module.JsonModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
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

/** Exercises the canonical submit API with real SQL and a captured Elasticsearch bulk update. */
class MarketApplySubmitMagicTest {
    private static final String TENANT = "validation-tenant";
    private static final String ACTOR = "validation-applicant";
    private static final String MARKET_TYPE = "catalogSubscribe";
    private static final String INDEX = "dataassets_apply_form";
    private static final String TENANT_INDEX = INDEX + "__tenant_" + TENANT;
    private static final Timestamp ORIGINAL_TIME = Timestamp.valueOf("2026-07-01 12:00:00");

    @Test
    void canonicalSourceCompilesWithRuntimeImports() throws Exception {
        MagicScript.create(readScript(), null).compile();
    }

    @Test
    void twentyAndHundredUseOneSelectOneWriteAndOneProjectionBatch() throws Exception {
        for (int count : List.of(20, 100)) {
            var fixture = new Fixture();
            var ids = new ArrayList<String>();
            for (int index = 0; index < count; index++) {
                String id = "validation-market-" + index;
                ids.add(id);
                fixture.add(id, TENANT, ACTOR, MARKET_TYPE, List.of(0, 4, 5).get(index % 3), 0);
            }

            Map<?, ?> result = asMap(fixture.submit(Map.of("flowOrderIds", ids)));

            assertThat(result.keySet()).hasSize(1);
            assertThat(items(result)).containsExactlyElementsOf(ids.stream().map(MarketApplySubmitMagicTest::item).toList());
            assertThat(fixture.sql.reads).isEqualTo(1);
            assertThat(fixture.sql.writes).isEqualTo(1);
            assertThat(fixture.indexRequests).hasSize(1);
            assertThat(fixture.indexRequests.getFirst()).extracting(UpdateQuery::getId).containsExactlyElementsOf(ids);
            assertThat(fixture.indexRequests.getFirst()).allSatisfy(query -> {
                assertThat(query.getDocAsUpsert()).isTrue();
                assertThat(query.getDocument()).containsOnlyKeys("id", "tid", "flowStatus", "updatedTime");
                assertThat(query.getDocument().get("flowStatus")).isEqualTo(2);
                assertThat(query.getDocument().get("updatedTime")).isInstanceOf(String.class);
            });
            assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM data_apply_form_t WHERE flow_status=2 AND updated_by=?",
                    Integer.class, ACTOR)).isEqualTo(count);
            System.out.printf("Market submit %d: 1 API call, 1 SQL read, 1 SQL write, 1 ES bulk update%n", count);
        }
    }

    @Test
    void aSingleInvalidTargetPreventsAllWritesAcrossOwnerTenantTypeAndStatus() throws Exception {
        for (var invalid : List.of(
                new InvalidTarget("validation-foreign", ACTOR, MARKET_TYPE, 0, 0, 404),
                new InvalidTarget(TENANT, "validation-other", MARKET_TYPE, 0, 0, 404),
                new InvalidTarget(TENANT, ACTOR, "CATALOG_SUBSCRIPTION", 0, 0, 404),
                new InvalidTarget(TENANT, ACTOR, "apply", 0, 0, 404),
                new InvalidTarget(TENANT, ACTOR, MARKET_TYPE, 0, 1, 404),
                new InvalidTarget(TENANT, ACTOR, MARKET_TYPE, 1, 0, 409),
                new InvalidTarget(TENANT, ACTOR, MARKET_TYPE, 3, 0, 409))) {
            var fixture = new Fixture();
            fixture.add("validation-allowed");
            fixture.add("validation-invalid", invalid.tenant(), invalid.actor(), invalid.type(), invalid.status(), invalid.deleted());

            var result = fixture.submit(Map.of("flowOrderIds", List.of("validation-allowed", "validation-invalid"),
                    "tenantId", invalid.tenant(), "applyUserId", invalid.actor(), "approveType", invalid.type()));

            assertCode(result, invalid.errorCode());
            assertThat(fixture.status("validation-allowed")).isZero();
            assertThat(fixture.status("validation-invalid")).isEqualTo(invalid.status());
            assertThat(fixture.sql.reads).isEqualTo(1);
            assertThat(fixture.sql.writes).isZero();
            verifyNoInteractions(fixture.index, fixture.operations);
        }
    }

    @Test
    void malformedAndOversizedRequestsDoNotReachStorage() throws Exception {
        var fixture = new Fixture();
        for (var body : List.of(
                Map.of("flowOrderIds", List.of()),
                Map.of("flowOrderIds", "validation-item"),
                Map.of("flowOrderIds", List.of("validation-item", " validation-item ")),
                Map.of("flowOrderIds", List.of(123)),
                Map.of("flowOrderIds", List.of(" ")),
                Map.of("flowOrderIds", List.of("x".repeat(33))),
                Map.of("flowOrderIds", java.util.Collections.nCopies(101, "validation-item")),
                Map.of("flowOrderId", "validation-item", "flowOrderIds", List.of("validation-item")),
                Map.of("flowOrderId", "validation-item", "dryRun", "true"))) {
            assertCode(fixture.submit(body), 400);
        }
        assertThat(fixture.sql.reads).isZero();
        assertThat(fixture.sql.writes).isZero();
        verifyNoInteractions(fixture.index, fixture.operations);
    }

    @Test
    void dryRunChecksEveryIdWithoutWritesOrProjectionAndRequiresTrustedSession() throws Exception {
        var fixture = new Fixture();
        fixture.add("validation-draft");
        fixture.add("validation-approved", TENANT, ACTOR, MARKET_TYPE, 2, 0);
        List<String> ids = List.of("validation-draft", "validation-approved");
        var request = Map.of("flowOrderIds", ids, "dryRun", true);

        assertThat(fixture.submit(request)).isEqualTo(Map.of("dryRun", true, "valid", true, "flowOrderIds", ids));
        assertCode(fixture.submit(Map.of("flowOrderIds", List.of("validation-draft", "validation-missing"), "dryRun", true)), 404);
        fixture.actor = null;
        assertCode(fixture.submit(request), 401);
        fixture.actor = ACTOR;
        fixture.scope.tenant = null;
        assertCode(fixture.submit(request), 401);
        assertThat(fixture.sql.reads).isEqualTo(2);
        assertThat(fixture.sql.writes).isZero();
        assertThat(fixture.updatedTime("validation-draft")).isEqualTo(ORIGINAL_TIME);
        verifyNoInteractions(fixture.index, fixture.operations);
    }

    @Test
    void legacyCreatorFallbackOnlyAppliesWhenTheApplicantIsMissing() throws Exception {
        var fixture = new Fixture();
        fixture.add("validation-owner-null", TENANT, null, MARKET_TYPE, 0, 0);
        fixture.add("validation-owner-empty", TENANT, "", MARKET_TYPE, 0, 0);
        fixture.add("validation-owner-other", TENANT, "validation-other", MARKET_TYPE, 0, 0);
        fixture.jdbc.update("UPDATE data_apply_form_t SET created_by=?", ACTOR);

        List<String> ids = List.of("validation-owner-null", "validation-owner-empty");
        assertCode(fixture.submit(Map.of("flowOrderIds", List.of(ids.getFirst(), "validation-owner-other"))), 404);
        assertThat(fixture.status(ids.getFirst())).isZero();
        assertThat(items(asMap(fixture.submit(Map.of("flowOrderIds", ids)))))
                .containsExactlyElementsOf(ids.stream().map(MarketApplySubmitMagicTest::item).toList());
        assertThat(fixture.status(ids.getFirst())).isEqualTo(2);
        assertThat(fixture.status(ids.getLast())).isEqualTo(2);
        assertThat(fixture.status("validation-owner-other")).isZero();
    }

    @Test
    void affectedCountMismatchRollsBackOtherRowsAndTheBatchCanBeRetried() throws Exception {
        var fixture = new Fixture();
        fixture.add("validation-first");
        fixture.add("validation-raced");
        fixture.sql.suppressOneRow = true;
        var body = Map.of("flowOrderIds", List.of("validation-first", "validation-raced"));

        assertCode(fixture.submit(body), 409);
        assertThat(fixture.sql.reads).isEqualTo(1);
        assertThat(fixture.sql.writes).isEqualTo(1);
        assertThat(fixture.status("validation-first")).isZero();
        assertThat(fixture.status("validation-raced")).isZero();
        verifyNoInteractions(fixture.index, fixture.operations);

        fixture.sql.suppressOneRow = false;
        assertThat(items(asMap(fixture.submit(body)))).containsExactly(item("validation-first"), item("validation-raced"));
        assertThat(fixture.status("validation-first")).isEqualTo(2);
        assertThat(fixture.status("validation-raced")).isEqualTo(2);
    }

    @Test
    void legacySingleResponseAndRetryPreserveGrantTimeWhenProjectionFails() throws Exception {
        var fixture = new Fixture();
        fixture.add("123");
        fixture.indexFailure = true;

        assertThat(fixture.submit(Map.of("flowOrderId", 123, "approveType", MARKET_TYPE))).isEqualTo(item("123"));
        Timestamp grantedAt = fixture.updatedTime("123");
        assertThat(grantedAt).isAfter(ORIGINAL_TIME);
        assertThat(fixture.status("123")).isEqualTo(2);
        assertThat(fixture.sql.writes).isEqualTo(1);

        fixture.indexFailure = false;
        assertThat(fixture.submit(Map.of("flowOrderId", "123"))).isEqualTo(item("123"));
        assertThat(fixture.updatedTime("123")).isEqualTo(grantedAt);
        assertThat(fixture.sql.writes).isEqualTo(1);
        assertThat(fixture.indexRequests).hasSize(2);
        assertThat(fixture.indexRequests.getLast().getFirst().getDocument().get("updatedTime"))
                .isEqualTo(fixture.indexRequests.getFirst().getFirst().getDocument().get("updatedTime"));
    }

    private static Map<String, Object> item(String id) {
        return Map.of("flowOrderId", id, "flowStatus", 2, "directGranted", true);
    }

    private static Map<?, ?> asMap(Object result) {
        assertThat(result).isInstanceOf(Map.class);
        return (Map<?, ?>) result;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<?, ?> result) {
        return (List<Map<String, Object>>) result.get("items");
    }

    private static void assertCode(Object result, int code) {
        assertThat(result).isInstanceOf(ExitValue.class);
        assertThat(((ExitValue) result).getValues()[0]).isEqualTo(code);
    }

    private static String readScript() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (!Files.isDirectory(root.resolve("magic"))) root = root.getParent();
        String content = Files.readString(root.resolve("magic/api/06.数据服务/02.数据超市/13.资源申请提交.ms"));
        return content.split("================================", 2)[1];
    }

    private record InvalidTarget(String tenant, String actor, String type, int status, int deleted, int errorCode) {}

    public static final class Scope {
        String tenant = TENANT;

        public String id() {
            return tenant;
        }
    }

    private static final class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final QueryCounter sql = new QueryCounter();
        final Scope scope = new Scope();
        final EsCommonService index = mock(EsCommonService.class);
        final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        final List<List<UpdateQuery>> indexRequests = new ArrayList<>();
        String actor = ACTOR;
        boolean indexFailure;

        Fixture() {
            var source = new DriverManagerDataSource("jdbc:h2:mem:market_submit_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    CREATE TABLE data_apply_form_t(tid varchar(32) PRIMARY KEY,tenant_id varchar(32),
                        apply_user_id varchar(64),type varchar(64),flow_status int,is_del int,
                        updated_time timestamp,updated_by varchar(64),created_by varchar(32))
                    """);
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            db.setResultProvider(new DefaultResultProvider("market-submit-test"));
            var dialects = new DialectAdapter();
            dialects.add(new MySQLDialect() {
                @Override
                public boolean match(String connectionUrl) {
                    return connectionUrl.contains(":h2:");
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
            db.setSqlInterceptors(List.of(sql));
            db.setNamedTableInterceptors(List.of());
            when(index.ensureTenantIndexReady(INDEX)).thenReturn(TENANT_INDEX);
            doAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                assertThat((IndexCoordinates) invocation.getArgument(1)).isEqualTo(IndexCoordinates.of(TENANT_INDEX));
                indexRequests.add(List.copyOf(invocation.getArgument(0)));
                if (indexFailure) throw new IllegalStateException("validation index failure");
                return null;
            }).when(operations).bulkUpdate(anyList(), any(IndexCoordinates.class));
        }

        void add(String id) {
            add(id, TENANT, ACTOR, MARKET_TYPE, 0, 0);
        }

        void add(String id, String tenant, String owner, String type, int status, int deleted) {
            jdbc.update("INSERT INTO data_apply_form_t VALUES (?,?,?,?,?,?,?,NULL,?)",
                    id, tenant, owner, type, status, deleted, ORIGINAL_TIME, owner);
        }

        int status(String id) {
            return jdbc.queryForObject("SELECT flow_status FROM data_apply_form_t WHERE tid=?", Integer.class, id);
        }

        Timestamp updatedTime(String id) {
            return jdbc.queryForObject("SELECT updated_time FROM data_apply_form_t WHERE tid=?", Timestamp.class, id);
        }

        Object submit(Map<String, ?> body) throws Exception {
            String source = readScript()
                    .replaceAll("(?m)^import (?:[^\\r\\n]+ as (?:me|esCommonService|esOperations)|jsons|log|tenantRuntime)\\r?\\n", "");
            var bindings = new LinkedHashMap<String, Object>();
            bindings.put("body", body);
            bindings.put("db", db);
            bindings.put("tenantRuntime", scope);
            bindings.put("me", (Function<Object[], Object>) args -> actor == null ? Map.of() : Map.of("id", actor));
            bindings.put("jsons", new JsonModule());
            bindings.put("esCommonService", index);
            bindings.put("esOperations", operations);
            bindings.put("log", mock(Logger.class));
            return MagicScript.create(source, null).execute(new MagicScriptContext(bindings));
        }
    }

    private static final class QueryCounter implements SQLInterceptor {
        int reads;
        int writes;
        boolean suppressOneRow;

        @Override
        public void preHandle(BoundSql sql, RequestEntity request) {
            String statement = sql.getSql().stripLeading().toLowerCase(Locale.ROOT);
            if (statement.startsWith("select")) {
                reads++;
            } else {
                writes++;
                assertThat(statement).startsWith("update");
                if (suppressOneRow) sql.setSql(sql.getSql() + " AND tid<>'validation-raced'");
            }
        }
    }
}
