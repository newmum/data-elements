package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.metautil.ddl.CanvasTableDdl;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.platform.magic.module.JsonModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class MaterializationBatchSubmitMagicTest {
    private static final String SCRIPT = "e3320ec899a24426adf5f77e2dab36f9";
    private static final String DDL_SCRIPT = "a0b5bba38fbf428c8e3646864d83d56b";
    private static final String TARGET_CONTEXT = "ods.materialization.targets";

    @Test
    void metadataReadsStayBoundedForOneAndTwentyTargets() throws Exception {
        var one = new Fixture(1);
        one.useCanonicalDdl();
        var small = success(one.batch(one.targets(1)));
        assertThat(small.get("complete")).isEqualTo(true);
        assertThat(results(small)).hasSize(1);
        assertThat(one.executed).containsExactly("db-0");
        int singleReads = one.reads();

        var maximum = new Fixture(20);
        maximum.useCanonicalDdl();
        var full = success(maximum.batch(maximum.targets(20)));
        assertThat(full.get("complete")).isEqualTo(true);
        assertThat(results(full)).hasSize(20).allSatisfy(result -> {
            assertThat(result.get("status")).isEqualTo("succeeded");
            assertThat(result.get("targetTableId")).isInstanceOf(String.class);
        });
        assertThat(maximum.executed).hasSize(20).doesNotHaveDuplicates();
        assertThat(maximum.reads()).isEqualTo(singleReads);
        assertThat(one.connectionReads).isEqualTo(1);
        assertThat(maximum.connectionReads).isEqualTo(1);
        assertThat(maximum.statusRefreshes).hasSize(1);
        assertThat(maximum.statusRefreshes.getFirst()).hasSize(20).doesNotHaveDuplicates();
        assertThat(maximum.registeredTables()).isEqualTo(20);
        assertThat(maximum.jdbc.queryForObject("SELECT access_request_status FROM db_table_t WHERE tid='source-1'", Integer.class))
                .isEqualTo(1);
        System.out.printf("materialization submit 1/20 targets: metadata SQL reads=%d/%d; external writes=1/20%n",
                singleReads, maximum.reads());
    }

    @Test
    void lastForeignOrDeletedTargetRejectsWholeBatchBeforeAnyWrite() throws Exception {
        for (String changed : List.of("tenant_id='foreign'", "is_del=1")) {
            var fixture = new Fixture(20);
            fixture.jdbc.update("UPDATE db_datasource_t SET " + changed + " WHERE tid='db-19'");
            assertThat(fixture.runBody(new LinkedHashMap<>(Map.of("tid", "source-1", "targets", fixture.targets(20),
                    "tenantId", "foreign", "scope", "ALL")))).isInstanceOf(ExitValue.class);
            assertNoWrites(fixture);
        }
    }

    @Test
    void referencedTargetsCannotBeReusedAndCandidateReadsStayBounded() throws Exception {
        var readCounts = new ArrayList<Integer>();
        for (int size : List.of(1, 20)) {
            var fixture = new Fixture(size);
            var targets = fixture.targets(size);
            for (int index = 0; index < size; index++) {
                targets.get(index).put("reuseExistingTable", true);
                targets.get(index).put("recreateMissingTable", false);
                fixture.jdbc.update("""
                        INSERT INTO db_table_t(tid,tenant_id,datasource_id,table_name,is_del)
                        VALUES (?,'police',?,?,0)
                        """, "existing-" + index, "db-" + index, "ODS_BATCH_" + index);
            }
            fixture.jdbc.update("""
                    INSERT INTO da_prop_t(tenant_id,parent_id,prop_name,prop_value,is_del)
                    VALUES ('police','protected-catalog','targetTableId',?,0)
                    """, "existing-" + (size - 1));

            assertStatus(400, fixture.batch(targets));
            readCounts.add(fixture.reads());
            assertThat(fixture.structuredReads).isGreaterThanOrEqualTo(2);
            assertThat(fixture.base.writeCount()).isZero();
            assertThat(fixture.executed).isEmpty();
            assertThat(fixture.physicalEvents).isEmpty();
            assertThat(fixture.registeredTables()).isEqualTo(size);
        }
        assertThat(readCounts.getFirst()).isEqualTo(readCounts.getLast());
        System.out.printf("materialization conflicts 1/20 targets: metadata SQL reads=%d/%d; writes=0%n",
                readCounts.getFirst(), readCounts.getLast());
    }

    @Test
    void directDdlCannotForgeTheInternalAuthorizedTargets() throws Exception {
        var fixture = new Fixture(1);
        fixture.useCanonicalDdl();
        fixture.jdbc.update("UPDATE db_datasource_t SET tenant_id='foreign' WHERE tid='db-0'");
        var body = Map.<String, Object>of("canvas", true, "dbId", "db-0", "tableName", "ods_batch_0",
                "ddl", "CREATE TABLE ods_batch_0(field_0 VARCHAR(32))", TARGET_CONTEXT,
                Map.of("tenantId", "police", "connections", Map.of("db-0", Map.of("dbType", "mysql")), "prepared", Map.of()));

        assertStatus(404, TenantContext.call("police", () -> fixture.runCanonicalDdl(body, "db-0")));
        assertThat(fixture.connectionReads).isEqualTo(1);
        assertThat(fixture.executed).isEmpty();
    }

    @Test
    void duplicateMalformedAndOversizedTargetsRejectBeforeAnyWrite() throws Exception {
        var fixture = new Fixture(21);
        var two = fixture.targets(2);
        var sameTarget = fixture.target(0, "ODS_BATCH_0");
        assertStatus(400, fixture.batch(List.of(two.getFirst(), sameTarget)));
        assertNoWrites(fixture);

        var emptyFields = new LinkedHashMap<>(two.getLast());
        emptyFields.put("tableItems", List.of());
        assertStatus(400, fixture.batch(List.of(two.getFirst(), emptyFields)));
        assertNoWrites(fixture);

        var missingDdl = new LinkedHashMap<>(two.getLast());
        missingDdl.remove("ddl");
        assertStatus(400, fixture.batch(List.of(two.getFirst(), missingDdl)));
        assertNoWrites(fixture);

        var pendingCleanup = new LinkedHashMap<>(two.getLast());
        pendingCleanup.put("pendingTargetCleanup", true);
        assertStatus(400, fixture.batch(List.of(two.getFirst(), pendingCleanup)));
        assertNoWrites(fixture);

        assertStatus(400, fixture.batch(fixture.targets(21)));
        assertNoWrites(fixture);
    }

    @Test
    void dryRunChecksEveryTargetWithoutChangingMetadataOrExecutingDdl() throws Exception {
        var fixture = new Fixture(20);
        Object response = fixture.runBody(new LinkedHashMap<>(Map.of("tid", "source-1",
                "targets", fixture.targets(20), "dryRun", true)));
        var dryRun = success(response);
        assertThat(dryRun.get("sideEffectsApplied")).isEqualTo(false);
        assertThat(results(dryRun)).hasSize(20).allSatisfy(result -> assertThat(result.get("valid")).isEqualTo(true));
        assertThat(fixture.locks.lock.attempts).isZero();
        assertNoWrites(fixture);
    }

    @Test
    void competingSubmitReturnsConflictBeforeReadingOrWritingMetadata() throws Exception {
        var fixture = new Fixture(20);
        fixture.locks.lock.heldByAnother = true;

        assertStatus(409, fixture.batch(fixture.targets(20)));
        assertThat(fixture.locks.key).isEqualTo("data-elements:ods:materialization:police");
        assertThat(fixture.locks.lock.attempts).isEqualTo(1);
        assertThat(fixture.locks.lock.releases).isZero();
        assertThat(fixture.reads()).isZero();
        assertThat(fixture.physicalEvents).isEmpty();
        assertNoWrites(fixture);
    }

    @Test
    void explicitlyReusedTargetIsReadOnceBeforeAnyPhysicalCreation() throws Exception {
        var fixture = new Fixture(2);
        var ordinary = fixture.target(0, "ods_batch_0");
        var reuse = fixture.target(1, "ods_batch_1");
        reuse.put("reuseExistingTable", true);
        reuse.put("recreateMissingTable", false);
        fixture.physicalTables.add("db-1");
        fixture.reuseFailure = "db-1";
        fixture.request.attributes.setAttribute(TARGET_CONTEXT, Map.of("validation", "prior context"));

        assertThat(fixture.batch(List.of(ordinary, reuse))).isInstanceOf(ExitValue.class);
        assertNoWrites(fixture);
        assertThat(fixture.physicalEvents).containsExactly("read:db-1");

        fixture.reuseFailure = null;
        var response = success(fixture.batch(List.of(ordinary, reuse)));
        assertThat(response.get("complete")).isEqualTo(true);
        assertThat(results(response).getLast().get("physicalTableReused")).isEqualTo(true);
        assertThat(fixture.physicalEvents).containsExactly("read:db-1", "write:db-0");
        assertThat(fixture.registeredTables()).isEqualTo(2);
    }

    @Test
    void failedExecutionPreservesCompletedRowsAndRetriesOnlyUnfinishedTargets() throws Exception {
        var fixture = new Fixture(3);
        var targets = fixture.targets(3);
        fixture.failure = "db-1";
        var partial = success(fixture.batch(targets));
        assertThat(partial.get("complete")).isEqualTo(false);
        var outcomes = results(partial);
        assertThat(outcomes.stream().map(row -> row.get("dbId"))).containsExactly("db-0", "db-1", "db-2");
        assertThat(outcomes.stream().map(row -> row.get("status"))).containsExactly("succeeded", "failed", "skipped");
        assertThat(outcomes.get(1).get("message")).isInstanceOf(String.class);
        String completedTableId = String.valueOf(outcomes.getFirst().get("targetTableId"));
        assertThat(completedTableId).isNotBlank().isNotEqualTo("null");
        assertThat(fixture.executed).containsExactly("db-0", "db-1");
        assertThat(fixture.registeredTables()).isEqualTo(1);
        assertThat(fixture.locks.lock.owned).isFalse();
        assertThat(fixture.locks.lock.releases).isEqualTo(1);

        fixture.failure = null;
        var retried = success(fixture.batch(targets.subList(1, targets.size())));
        assertThat(retried.get("complete")).isEqualTo(true);
        assertThat(results(retried).stream().map(row -> row.get("status"))).containsExactly("succeeded", "succeeded");
        assertThat(fixture.executed).containsExactly("db-1", "db-2");
        assertThat(fixture.jdbc.queryForObject("SELECT tid FROM db_table_t WHERE tenant_id='police' AND datasource_id='db-0' AND is_del=0", String.class))
                .isEqualTo(completedTableId);
        assertThat(fixture.registeredTables()).isEqualTo(3);
    }

    @Test
    void retryAfterDdlRequiresExplicitReuseWhenRegistrationFailed() throws Exception {
        var fixture = new Fixture(2);
        // Fail after writing the target table, its columns and its catalog. All three must roll back together.
        fixture.jdbc.execute("ALTER TABLE da_prop_t ADD CONSTRAINT reject_target CHECK (prop_name <> 'targetDbId' OR prop_value <> 'db-1')");
        var targets = fixture.targets(2);
        var first = results(success(fixture.batch(targets)));
        assertThat(first.stream().map(row -> row.get("status"))).containsExactly("succeeded", "failed");
        assertThat(first.getLast().get("sideEffectsPossible")).isEqualTo(true);
        assertThat(fixture.registeredTables()).isEqualTo(1);
        assertThat(fixture.physicalTables).contains("db-1");
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM da_catalog_t WHERE tenant_id='police' AND is_del=0", Integer.class))
                .isEqualTo(1);
        assertThat(fixture.jdbc.queryForObject("""
                SELECT COUNT(*) FROM db_table_column_t c LEFT JOIN db_table_t t ON t.tid=c.table_id AND t.tenant_id=c.tenant_id
                WHERE c.tenant_id='police' AND c.is_del=0 AND t.tid IS NULL
                """, Integer.class)).isZero();
        assertThat(fixture.esDatasourceIds).containsExactly("db-0", "db-0");
        assertThat(fixture.statusRefreshes).containsExactly(List.of("db-0"));
        assertThat(fixture.locks.lock.owned).isFalse();
        assertThat(fixture.locks.lock.releases).isEqualTo(1);

        fixture.jdbc.execute("ALTER TABLE da_prop_t DROP CONSTRAINT reject_target");
        Object retry = fixture.batch(List.of(targets.getLast()));
        if (retry instanceof ExitValue) {
            assertStatus(409, retry);
        } else {
            assertThat(results(success(retry)).getFirst().get("status")).isEqualTo("failed");
        }
        assertThat(fixture.registeredTables()).isEqualTo(1);

        var reuse = new LinkedHashMap<>(targets.getLast());
        reuse.put("reuseExistingTable", true);
        reuse.put("recreateMissingTable", false);
        var recovered = results(success(fixture.batch(List.of(reuse))));
        assertThat(recovered.getFirst().get("status")).isEqualTo("succeeded");
        assertThat(recovered.getFirst().get("physicalTableReused")).isEqualTo(true);
        assertThat(fixture.physicalEvents).containsExactly("read:db-1");
        assertThat(fixture.registeredTables()).isEqualTo(2);
    }

    @Test
    void existingSingleContractKeepsScalarResult() throws Exception {
        var fixture = new Fixture(1);
        var single = new LinkedHashMap<>(fixture.target(0, "ods_batch_0"));
        single.put("tid", "source-1");
        var response = StructuredValues.objectMap(fixture.runBody(single));
        assertThat(response).containsKeys("tid", "targetTableId", "catalogId", "sourceTableId", "physicalTableReused")
                .doesNotContainKeys("batch", "results");
        assertThat(fixture.executed).containsExactly("db-0");
        assertThat(fixture.registeredTables()).isEqualTo(1);
    }

    @Test
    void failedCatalogProjectionPreservesSuccessfulRegistrationWithoutGenericFallback() throws Exception {
        var fixture = new Fixture(2);
        fixture.projectionFailure = true;

        var result = success(fixture.batch(fixture.targets(2)));

        assertThat(result).containsEntry("complete", true);
        assertThat(results(result)).allSatisfy(row -> assertThat(row).containsEntry("status", "succeeded"));
        assertThat(fixture.registeredTables()).isEqualTo(2);
        assertThat(fixture.projectionAttempts).isEqualTo(2);
        assertThat(fixture.genericCatalogWrites).isZero();
        assertThat(fixture.jdbc.queryForObject("SELECT SUM(market_view_count) FROM da_catalog_t WHERE tenant_id='police'", Long.class))
                .isZero();
    }

    private static Map<String, Object> success(Object raw) {
        assertThat(raw).isInstanceOf(Map.class);
        var result = StructuredValues.objectMap(raw);
        assertThat(result.get("batch")).isEqualTo(true);
        return result;
    }

    private static List<Map<String, Object>> results(Map<String, Object> result) {
        return StructuredValues.objectRows(result.get("results"));
    }

    private static void assertStatus(int status, Object result) {
        assertThat(result).isInstanceOfSatisfying(ExitValue.class,
                exit -> assertThat(((Number) exit.getValues()[0]).intValue()).isEqualTo(status));
    }

    private static void assertNoWrites(Fixture fixture) {
        assertThat(fixture.base.writeCount()).isZero();
        assertThat(fixture.executed).isEmpty();
        assertThat(fixture.columnWrites).isZero();
        assertThat(fixture.statusRefreshes).isEmpty();
        assertThat(fixture.registeredTables()).isZero();
    }

    public static final class Fixture extends ExistingTargetMaterializationMagicTest.Fixture {
        private final MagicScript script;
        private MagicScript ddlScript;
        private final DataSourceConnectionPropertyResolver connections;
        private final StructuredTableReadMagicModule tableRead;
        private final RequestModule request = new RequestModule();
        final List<String> executed = new ArrayList<>();
        final List<String> physicalEvents = new ArrayList<>();
        final Set<String> physicalTables = new HashSet<>();
        final List<String> esDatasourceIds = new ArrayList<>();
        final List<List<String>> statusRefreshes = new ArrayList<>();
        int columnWrites;
        int connectionReads;
        int structuredReads;
        boolean batchRequest;
        String failure;
        String reuseFailure;
        boolean projectionFailure;
        int projectionAttempts;
        int genericCatalogWrites;

        Fixture(int targetCount) throws Exception {
            super(1);
            jdbc.execute("ALTER TABLE db_table_t ADD access_request_status INT");
            jdbc.update("UPDATE db_table_t SET access_request_status=1 WHERE tid='source-1'");
            for (int index = 0; index < targetCount; index++) {
                jdbc.update("""
                        INSERT INTO db_datasource_t(tid,tenant_id,db_type,pool_cfg,is_del,db_name,asset_status,show_connect)
                        VALUES (?,'police','mysql',?,0,?,2,1)
                        """, "db-" + index, "{\"host\":\"fixture.invalid\",\"database\":\"validation_target\",\"port\":3306,\"username\":\"validation\"}",
                        "Target " + index);
            }
            var countedConnections = new JdbcTemplate(jdbc.getDataSource()) {
                @Override
                public List<Map<String, Object>> queryForList(String sql, Object... arguments) {
                    connectionReads++;
                    return super.queryForList(sql, arguments);
                }
            };
            connections = new DataSourceConnectionPropertyResolver(countedConnections, new ObjectMapper());
            DataSource real = jdbc.getDataSource();
            tableRead = new StructuredTableReadMagicModule(new JdbcTemplate(new AbstractDataSource() {
                @Override
                public Connection getConnection() throws SQLException {
                    return trackReads(real.getConnection());
                }

                @Override
                public Connection getConnection(String user, String password) throws SQLException {
                    return trackReads(real.getConnection(user, password));
                }
            }));
            String source = CanonicalMagicSources.byId(SCRIPT)
                    .replaceAll("(?m)^import (?!cn\\.hutool\\.|java\\.util\\.|com\\.linewell\\.dataelement\\.shared\\.error\\.ErrorDetailSanitizer)[^\\r\\n]*\\r?\\n", "");
            script = MagicScript.create("var createTable=()=> fixture.physical(body);\n"
                    + "var date_format=value=>value+'';\n" + source, null);
        }

        void useCanonicalDdl() throws Exception {
            String source = CanonicalMagicSources.byId(DDL_SCRIPT).replaceAll(
                    "(?m)^import (?!com\\.linewell\\.dataelement\\.(?:metautil\\.(?:model\\.|jdbc\\.JdbcDriverPropertyResolver)|shared\\.error\\.ErrorDetailSanitizer)|java\\.util\\.|com\\.alibaba\\.druid\\.|cn\\.hutool\\.)[^\\r\\n]*\\r?\\n", "");
            ddlScript = MagicScript.create(source, null);
        }

        List<Map<String, Object>> targets(int count) {
            var targets = new ArrayList<Map<String, Object>>();
            for (int index = 0; index < count; index++) {
                targets.add(target(index, "ods_batch_" + index));
            }
            return targets;
        }

        Map<String, Object> target(int index, String tableName) {
            var props = new LinkedHashMap<>(Map.of("dbId", "db-" + index,
                    "tableName", tableName, "tableNameCn", "目标 " + index));
            var field = new LinkedHashMap<String, Object>(Map.of("columnName", "field_0", "dataType", "varchar",
                    "columnType", "varchar(32)", "length", 32, "nullable", 1));
            return new LinkedHashMap<>(Map.of("propList", props, "tableItems", List.of(field),
                    "ddl", "CREATE TABLE " + tableName + " (field_0 VARCHAR(32))", "recreateMissingTable", true));
        }

        Object batch(List<Map<String, Object>> targets) {
            return runBody(new LinkedHashMap<>(Map.of("tid", "source-1", "targets", targets)));
        }

        Object runBody(Map<String, Object> body) {
            base.resetCounter();
            executed.clear();
            physicalEvents.clear();
            esDatasourceIds.clear();
            statusRefreshes.clear();
            columnWrites = 0;
            connectionReads = 0;
            structuredReads = 0;
            batchRequest = body.containsKey("targets");
            var context = new LinkedHashMap<String, Object>(Map.of("db", base.db, "body", body,
                    "fixture", this, "tenantRuntime", base.scope, "dataScope", base.scope,
                    "jsons", new JsonModule(), "metadataAsset", this, "esCommonService", this,
                    "connectionProperties", connections, "request", request));
            context.put("tableRead", tableRead);
            context.put("redissonClient", locks);
            context.put("platformJdbc", jdbc);
            context.put("log", LoggerFactory.getLogger(MaterializationBatchSubmitMagicTest.class));
            var previous = new LinkedHashMap<>(request.attributes.values);
            try {
                return TenantContext.call("police", () -> script.execute(new MagicScriptContext(context)));
            } finally {
                assertThat(request.attributes.values).isEqualTo(previous);
            }
        }

        int reads() {
            return base.readCount() + connectionReads + structuredReads;
        }

        private Connection trackReads(Connection actual) {
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("getMetaData")) {
                            DatabaseMetaData metadata = actual.getMetaData();
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                    (ignored, metadataMethod, metadataArgs) -> metadataMethod.getName().equals("getURL")
                                            ? "jdbc:mysql://materialization-validation" : invoke(metadata, metadataMethod, metadataArgs));
                        }
                        if (method.getName().equals("prepareStatement")) structuredReads++;
                        return invoke(actual, method, arguments);
                    });
        }

        private static Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException wrapped) {
                throw wrapped.getCause();
            }
        }

        int registeredTables() {
            return jdbc.queryForObject("SELECT COUNT(*) FROM db_table_t WHERE tenant_id='police' AND datasource_id LIKE 'db-%' AND is_del=0", Integer.class);
        }

        @Override
        public Map<String, Object> physical(Map<String, Object> request) {
            String dbId = String.valueOf(request.get("dbId"));
            if (batchRequest) {
                var authorized = StructuredValues.objectMap(this.request.attributes.getAttribute(TARGET_CONTEXT));
                assertThat(authorized.get("tenantId")).isEqualTo("police");
                assertThat(StructuredValues.objectMap(authorized.get("connections"))).containsKey(dbId);
            }
            if (ddlScript != null) {
                Object outcome = runCanonicalDdl(request, dbId);
                if (outcome instanceof ExitValue exit) {
                    throw new AssertionError("Canonical DDL rejected validation target: " + Arrays.toString(exit.getValues()));
                }
                return StructuredValues.objectMap(outcome);
            }
            if (Boolean.TRUE.equals(request.get("validateOnly"))) {
                return Map.of("valid", true, "sideEffectsApplied", false);
            }
            if (Boolean.TRUE.equals(request.get("reuseExistingOnly"))) {
                physicalEvents.add("read:" + dbId);
                if (dbId.equals(reuseFailure) || !physicalTables.contains(dbId)) {
                    return Map.of("physicalTableReused", false);
                }
                return Map.of("created", false, "physicalTableReused", true, "sideEffectsApplied", false,
                        "tableItems", request.get("tableItems"));
            }
            executed.add(dbId);
            physicalEvents.add("write:" + dbId);
            if (dbId.equals(failure)) {
                return Map.of("status", "failed", "code", 500, "message", "Validation physical create failed");
            }
            return Map.of("created", physicalTables.add(dbId));
        }

        Object runCanonicalDdl(Map<String, Object> body, String dbId) {
            var inputs = Map.<String, Object>of("body", body, "tenantRuntime", base.scope,
                    "request", request, "db", base.db, "connectionProperties", connections,
                    "canvasTableDdl", new CanvasTableDdl(), "metadataExplorerService", new PhysicalTarget(this, dbId));
            return ddlScript.execute(new MagicScriptContext(inputs));
        }

        @Override
        public int batchInsertTableColumns(List<Map<String, Object>> columns) {
            columnWrites += columns.size();
            return super.batchInsertTableColumns(columns);
        }

        @Override
        public void refreshRegistrationStatus(String id, boolean finish) {
            statusRefreshes.add(List.of(id));
        }

        public List<Map<String, Object>> refreshRegistrationStatuses(List<String> datasourceIds) {
            statusRefreshes.add(List.copyOf(datasourceIds));
            return List.of();
        }

        @Override
        public void saveOrUpdate(String index, String id, String content, boolean overwrite) {
            var projection = cn.hutool.json.JSONUtil.parseObj(content);
            if ("catalog".equals(projection.getStr("assetType"))) genericCatalogWrites++;
            assertThat(projection.getStr("assetType")).isNotEqualTo("catalog");
            esDatasourceIds.add(projection.getStr("datasourceId"));
        }

        @Override
        public String mergeCatalogProjection(String index, String id, String content, Object count) {
            var projection = cn.hutool.json.JSONUtil.parseObj(content);
            assertThat(index).isEqualTo("dataassets");
            assertThat(projection.getStr("assetType")).isEqualTo("catalog");
            assertThat(projection.getStr("tid")).isEqualTo(id);
            assertThat(((Number) count).longValue()).isEqualTo(projection.getLong("viewCount"));
            if (projection.get("tenantId") != null) assertThat(projection.getStr("tenantId")).isEqualTo("police");
            projectionAttempts++;
            if (projectionFailure) throw new IllegalStateException("validation projection unavailable");
            super.mergeCatalogProjection(index, id, content, count);
            esDatasourceIds.add(projection.getStr("targetDbId"));
            return id;
        }
    }

    public record PhysicalTarget(Fixture fixture, String dbId) {
        public void createTable(DataSourceConfig config, String ddl) {
            assertThat(config.getDatabase()).isEqualTo("validation_target");
            assertThat(ddl).contains("ods_batch_" + dbId.substring(3));
            fixture.executed.add(dbId);
        }
    }

    public static final class RequestModule {
        final Attributes attributes = new Attributes();

        public Attributes get() {
            return attributes;
        }
    }

    public static final class Attributes {
        final Map<String, Object> values = new LinkedHashMap<>(Map.of("validation.existingAttribute", "keep"));

        public Object getAttribute(String name) {
            return values.get(name);
        }

        public void setAttribute(String name, Object value) {
            if (value == null) {
                values.remove(name);
            } else {
                values.put(name, value);
            }
        }
    }
}
