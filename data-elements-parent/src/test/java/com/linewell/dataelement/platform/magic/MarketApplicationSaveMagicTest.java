package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.linewell.dataelement.platform.magic.module.JsonModule;
import com.linewell.dataelement.utils.StructuredValues;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.LowerColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class MarketApplicationSaveMagicTest {
    private static final String SAVE = "597176b6460544c395498e374bf8bfe8";

    @ParameterizedTest
    @CsvSource({"MySQL,20", "MySQL,100", "PostgreSQL,20", "PostgreSQL,100"})
    void createsOneDraftPerCatalogWithTwoReadStatementsAndOneProjection(String mode, int size) throws Exception {
        var fixture = new Fixture(mode);
        var catalogIds = IntStream.range(0, size).mapToObj(i -> "validation-" + i).toList();
        var orders = IntStream.range(0, size)
                .mapToObj(i -> draft("draftKey", "%032x".formatted(i + 1), "validation-" + i, "用途"))
                .toList();
        var dryRun = assertInstanceOf(Map.class, fixture.save(Map.of("orders", orders, "dryRun", true)));
        assertEquals(size, dryRun.get("count"));
        assertEquals(0, fixture.count("data_apply_form_t"));
        assertEquals(2, fixture.reads);
        assertEquals(0, fixture.writes);
        assertEquals(List.of(catalogIds), fixture.eligibilityQueries());
        assertEquals("target-validation-0", fixture.eligibilityBatches.getFirst().getFirst().get("targetTableId"));
        assertNull(fixture.eligibilityBatches.getFirst().get(1).get("targetTableId"));
        assertEquals(0, fixture.projection.ids.size());

        var result = assertInstanceOf(Map.class, fixture.save(Map.of("orders", orders)));
        var items = assertInstanceOf(List.class, result.get("items"));
        assertEquals(size, items.size());
        assertEquals(Map.of("tid", "%032x".formatted(1), "catalogIds", List.of("validation-0")), items.getFirst());
        assertEquals(2, fixture.reads);
        assertEquals(List.of(catalogIds), fixture.eligibilityQueries());
        assertEquals(size, fixture.count("data_apply_form_t"));
        assertEquals(size, fixture.count("da_catalog_apply_form_rela"));
        assertEquals(size * 2, fixture.count("da_prop_t"));
        assertEquals(1, fixture.projection.calls);
        assertEquals(size, fixture.projection.ids.size());
        assertEquals("目录 0", fixture.projection.documents.getFirst().get("applyName"));
        assertEquals("owner", fixture.projection.documents.getFirst().get("applyUserId"));
        assertFalse(fixture.projection.partial.getFirst().containsKey("flowStatus"));
        assertEquals(2, fixture.jdbc.queryForObject(
                "SELECT COUNT(*) FROM data_market_shopping_cart_t WHERE is_del=0", Integer.class));
    }

    @Test
    void retriesTheSameDraftKeyAndRollsBackEarlierWritesOnFailure() throws Exception {
        var fixture = new Fixture("MySQL");
        String first = "%032x".formatted(1);
        var order = draft("draftKey", first, "validation-0", "原用途");
        var initial = fixture.save(Map.of("orders", List.of(order)));
        assertEquals(List.of(List.of("validation-0")), fixture.eligibilityQueries());
        fixture.ineligible = Set.of("validation-0");
        assertEquals(initial, fixture.save(Map.of("orders", List.of(order))));
        assertEquals(List.of(), fixture.eligibilityQueries());
        assertExit(409, fixture.save(Map.of("orders", List.of(draft("draftKey", first, "validation-1", "用途")))));
        assertEquals(List.of(), fixture.eligibilityQueries());
        assertEquals(1, fixture.count("data_apply_form_t"));
        assertEquals(1, fixture.count("da_catalog_apply_form_rela"));
        assertEquals(2, fixture.count("da_prop_t"));

        fixture.jdbc.execute("ALTER TABLE da_prop_t ADD CHECK (prop_value <> 'reject-write')");
        assertThrows(RuntimeException.class, () -> fixture.save(Map.of("orders", List.of(
                draft("tid", first, "validation-0", "新用途"),
                draft("draftKey", "%032x".formatted(2), "validation-1", "reject-write")))));
        assertEquals(1, fixture.count("data_apply_form_t"));
        assertEquals("原用途", fixture.jdbc.queryForObject(
                "SELECT prop_value FROM da_prop_t WHERE parent_id=? AND prop_name='applyBasis'", String.class, first));
        assertEquals(2, fixture.projection.calls);
    }

    @Test
    void rejectsForeignOwnersAndCatalogsWithoutPartialWritesAndPreservesSingleContract() throws Exception {
        var fixture = new Fixture("PostgreSQL");
        fixture.jdbc.update("INSERT INTO data_apply_form_t (tid,tenant_id,apply_user_id,created_by,type,is_del,flow_status,catalog_ids)"
                + " VALUES ('foreign-order','tenant-a','other','owner','catalogSubscribe',0,0,'validation-0')");
        var newOrder = draft("draftKey", "%032x".formatted(1), "validation-0", "用途");
        assertExit(404, fixture.save(Map.of("orders", List.of(newOrder,
                draft("tid", "foreign-order", "validation-1", "用途")))));
        assertExit(404, fixture.save(Map.of("orders", List.of(newOrder,
                draft("draftKey", "%032x".formatted(2), "foreign-catalog", "用途")))));
        assertEquals(1, fixture.count("data_apply_form_t"));
        assertEquals(0, fixture.projection.calls);
        assertEquals(List.of(), fixture.eligibilityQueries());

        var legacy = Map.of("type", "catalogSubscribe",
                "propList", Map.of("applyResource", List.of("validation-1"), "applyBasis", "用途"));
        var result = assertInstanceOf(Map.class, fixture.save(legacy));
        assertEquals(1, result.size());
        assertEquals(2, fixture.count("data_apply_form_t"));
    }

    @Test
    void preservesHistoricalCatalogsAndSnapshotsWhileCheckingOnlyNewSelections() throws Exception {
        var fixture = new Fixture("PostgreSQL");
        fixture.legacyDraft("historic", null, " validation-0 , validation-1 ", 4);
        fixture.jdbc.update("UPDATE data_apply_form_t SET resource_scope_json=?,asset_payload_json=?,"
                        + " resource_revision_no=7 WHERE tid='historic'", "{\"scope\":\"validation\"}", "{\"legacy\":true}");
        fixture.ineligible = Set.of("validation-0", "validation-1", "validation-3");

        var retain = draft("tid", "historic", List.of("validation-1", "validation-0"), "编辑历史草稿");
        fixture.eligibilityFailure = new IllegalStateException("validation search unavailable");
        assertInstanceOf(Map.class, fixture.save(retain));
        assertEquals(List.of(), fixture.eligibilityQueries());
        assertEquals("validation-1,validation-0", fixture.catalogsOf("historic"));
        fixture.eligibilityFailure = null;

        var mixed = Map.of("orders", List.of(
                draft("tid", "historic", List.of("validation-0", "validation-2"), "添加一个目录"),
                draft("draftKey", "%032x".formatted(3), "validation-3", "新申请")));
        assertExit(404, fixture.save(mixed));
        assertEquals(List.of(List.of("validation-2", "validation-3")), fixture.eligibilityQueries());
        assertEquals(0, fixture.writes);
        assertEquals(1, fixture.count("data_apply_form_t"));
        assertEquals("validation-1,validation-0", fixture.catalogsOf("historic"));
        assertEquals(1, fixture.projection.calls);

        fixture.ineligible = Set.of("validation-0", "validation-1");
        assertInstanceOf(Map.class, fixture.save(mixed));
        assertEquals(List.of(List.of("validation-2", "validation-3")), fixture.eligibilityQueries());
        assertEquals("validation-0,validation-2", fixture.catalogsOf("historic"));
        assertEquals("{\"scope\":\"validation\"}", fixture.jdbc.queryForObject(
                "SELECT resource_scope_json FROM data_apply_form_t WHERE tid='historic'", String.class));
        assertEquals("{\"legacy\":true}", fixture.jdbc.queryForObject(
                "SELECT asset_payload_json FROM data_apply_form_t WHERE tid='historic'", String.class));
        assertEquals(7, fixture.jdbc.queryForObject(
                "SELECT resource_revision_no FROM data_apply_form_t WHERE tid='historic'", Integer.class));
    }

    @Test
    void canAddCatalogsToOldDraftsWithEmptyOrNullCsvAndNoScope() throws Exception {
        var fixture = new Fixture("MySQL");
        fixture.legacyDraft("empty", "", "", 0);
        fixture.legacyDraft("missing", null, null, 5);
        var edits = List.of(draft("tid", "empty", "validation-0", "空目录草稿"),
                draft("tid", "missing", "validation-1", "旧草稿"));
        assertInstanceOf(Map.class, fixture.save(Map.of("orders", edits)));
        assertEquals(List.of(List.of("validation-0", "validation-1")), fixture.eligibilityQueries());
        assertEquals("validation-0", fixture.catalogsOf("empty"));
        assertEquals("validation-1", fixture.catalogsOf("missing"));
        assertEquals(2, fixture.jdbc.queryForObject(
                "SELECT COUNT(*) FROM data_apply_form_t WHERE resource_scope_json IS NULL", Integer.class));
    }

    @Test
    void eligibilityFailureDoesNotWriteAnyApplicationOrCartRows() throws Exception {
        var fixture = new Fixture("MySQL");
        var input = Map.of("orders", List.of(
                draft("draftKey", "%032x".formatted(1), "validation-0", "新申请")));
        fixture.invalidEligibility = true;
        assertExit(502, fixture.save(input));
        fixture.invalidEligibility = false;
        fixture.eligibilityFailure = new IllegalStateException("validation search unavailable");
        assertThrows(RuntimeException.class, () -> fixture.save(input));
        assertEquals(0, fixture.writes);
        assertEquals(0, fixture.count("data_apply_form_t"));
        assertEquals(0, fixture.projection.calls);
        assertEquals(22, fixture.jdbc.queryForObject(
                "SELECT COUNT(*) FROM data_market_shopping_cart_t WHERE is_del=0", Integer.class));
    }

    @Test
    void aConcurrentDirectoryChangeRollsBackTheBatchInsteadOfReaddingOldCatalogs() throws Exception {
        var fixture = new Fixture("PostgreSQL");
        fixture.legacyDraft("stable", "owner", "validation-0", 0);
        fixture.legacyDraft("raced", "owner", "validation-1", 0);
        fixture.beforeFormUpdate = () -> fixture.changeCatalogFromAnotherConnection("raced", "validation-2");

        assertExit(409, fixture.save(Map.of("orders", List.of(
                draft("tid", "stable", "validation-0", "会回滚的修改"),
                draft("tid", "raced", "validation-1", "已被其它会话移除")))));
        assertEquals(List.of(), fixture.eligibilityQueries());
        assertEquals("validation-0", fixture.catalogsOf("stable"));
        assertEquals("validation-2", fixture.catalogsOf("raced"));
        assertNull(fixture.jdbc.queryForObject(
                "SELECT apply_name FROM data_apply_form_t WHERE tid='stable'", String.class));
        assertEquals(0, fixture.count("da_catalog_apply_form_rela"));
        assertEquals(0, fixture.count("da_prop_t"));
        assertEquals(0, fixture.projection.calls);
    }

    private static Map<String, Object> draft(String key, String tid, String catalog, String purpose) {
        return draft(key, tid, List.of(catalog), purpose);
    }

    private static Map<String, Object> draft(String key, String tid, List<String> catalogs, String purpose) {
        return Map.of(key, tid, "type", "catalogSubscribe",
                "propList", Map.of("applyResource", catalogs, "applyBasis", purpose,
                        "tenant_id", "tenant-b", "applyUserId", "other"));
    }

    private static void assertExit(int expected, Object result) {
        var exit = assertInstanceOf(ExitValue.class, result);
        assertEquals(expected, ((Number) exit.getValues()[0]).intValue());
    }

    public static class Tenant {
        public String id() { return "tenant-a"; }
    }

    public static class Projection {
        private int calls;
        private List<String> ids = List.of();
        private List<Map<String, Object>> documents = List.of();
        private List<Map<String, Object>> partial = List.of();

        public String ensureTenantIndexReady(String index) {
            assertEquals("dataassets_apply_form", index);
            return "tenant_a_dataassets_apply_form";
        }

        public void bulkUpdate(List<UpdateQuery> updates, IndexCoordinates index) {
            assertEquals("tenant_a_dataassets_apply_form", index.getIndexName());
            calls++;
            ids = updates.stream().map(UpdateQuery::getId).toList();
            documents = new ArrayList<>(updates.stream().map(UpdateQuery::getUpsert).toList());
            partial = new ArrayList<>(updates.stream().map(UpdateQuery::getDocument).toList());
        }
    }

    private static final class Fixture {
        private final JdbcTemplate jdbc;
        private final SQLModule db;
        private final String script;
        private final Projection projection = new Projection();
        private final List<List<Map<String, Object>>> eligibilityBatches = new ArrayList<>();
        private Set<String> ineligible = Set.of();
        private RuntimeException eligibilityFailure;
        private boolean invalidEligibility;
        private Runnable beforeFormUpdate;
        private int reads;
        private int writes;

        Fixture(String mode) throws Exception {
            var source = new DriverManagerDataSource("jdbc:h2:mem:market_save_" + UUID.randomUUID()
                    + ";MODE=" + mode + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(source);
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            columns.add(new LowerColumnMapperProvider());
            columns.setDefault("camel");
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of(new SQLInterceptor() {
                @Override
                public void preHandle(BoundSql boundSql, RequestEntity request) {
                    String sql = boundSql.getSql().stripLeading().toLowerCase(Locale.ROOT);
                    if (sql.startsWith("select")) {
                        reads++;
                    } else if (sql.startsWith("update") || sql.startsWith("insert") || sql.startsWith("delete")) {
                        writes++;
                    }
                    if (beforeFormUpdate != null && sql.startsWith("update") && sql.contains("data_apply_form_t")) {
                        Runnable concurrentChange = beforeFormUpdate;
                        beforeFormUpdate = null;
                        concurrentChange.run();
                    }
                }
            }));
            script = "var me = () => authUser(body)\n" + CanonicalMagicSources.byId(SAVE)
                    .replaceAll("(?m)^import (?!java\\.|cn\\.hutool\\.|org\\.springframework\\.data\\.elasticsearch\\.core\\.(?:document|mapping|query)\\.)[^\\r\\n]*\\R", "");
            jdbc.execute("""
                    CREATE TABLE da_catalog_t (tid VARCHAR PRIMARY KEY,tenant_id VARCHAR, is_del INT,
                        asset_status INT,flow_status INT,catalog_name VARCHAR,org_id VARCHAR,target_table_id VARCHAR)
                    """);
            jdbc.execute("""
                    CREATE TABLE data_apply_form_t (tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,is_del INT,
                        type VARCHAR,flow_status INT,apply_user_id VARCHAR,apply_user_name VARCHAR,
                        apply_org_id VARCHAR,apply_name VARCHAR,catalog_ids VARCHAR,created_by VARCHAR,
                        updated_by VARCHAR,created_time TIMESTAMP,updated_time TIMESTAMP,
                        resource_scope_json VARCHAR,asset_payload_json VARCHAR,resource_revision_no INT DEFAULT 1)
                    """);
            jdbc.execute("""
                    CREATE TABLE da_catalog_apply_form_rela (tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,is_del INT,
                        apply_form_id VARCHAR,catalog_id VARCHAR,catalog_org_id VARCHAR,sort_no INT,
                        created_by VARCHAR,updated_by VARCHAR,created_time TIMESTAMP,updated_time TIMESTAMP)
                    """);
            jdbc.execute("""
                    CREATE TABLE da_prop_t (tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,is_del INT,
                        parent_id VARCHAR,prop_name VARCHAR,prop_value VARCHAR,prop_type VARCHAR,data_type VARCHAR,
                        created_by VARCHAR,updated_by VARCHAR,created_time TIMESTAMP,updated_time TIMESTAMP)
                    """);
            jdbc.execute("""
                    CREATE TABLE data_market_shopping_cart_t (tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,is_del INT,
                        catalog_id VARCHAR,apply_org_id VARCHAR,updated_by VARCHAR,updated_time TIMESTAMP)
                    """);
            for (int i = 0; i < 100; i++) {
                jdbc.update("INSERT INTO da_catalog_t VALUES (?,'tenant-a',0,2,2,?,'catalog-org',?)",
                        "validation-" + i, "目录 " + i, i % 2 == 0 ? "target-validation-" + i : null);
                if (i < 20) {
                    jdbc.update("INSERT INTO data_market_shopping_cart_t VALUES (?,'tenant-a',0,?,'org',null,null)",
                            "cart-" + i, "validation-" + i);
                }
            }
            jdbc.update("INSERT INTO da_catalog_t VALUES ('foreign-catalog','tenant-b',0,2,2,'外部目录','org',null)");
            jdbc.update("INSERT INTO data_market_shopping_cart_t VALUES ('outside-tenant','tenant-b',0,'validation-0','org',null,null)");
            jdbc.update("INSERT INTO data_market_shopping_cart_t VALUES ('outside-org','tenant-a',0,'validation-0','other',null,null)");
        }

        Object save(Map<String, ?> body) {
            reads = 0;
            writes = 0;
            eligibilityBatches.clear();
            var inputs = new LinkedHashMap<String, Object>();
            inputs.put("body", body);
            inputs.put("db", db);
            inputs.put("tenantRuntime", new Tenant());
            inputs.put("authUser", (Function<Object[], Object>) arguments -> {
                assertEquals(Map.of(), arguments[0]);
                return Map.of("id", "owner", "userName", "申请人", "orgId", "org");
            });
            inputs.put("marketCatalogEligibility", (Function<Object[], Object>) this::checkEligibility);
            inputs.put("log", LoggerFactory.getLogger(MarketApplicationSaveMagicTest.class));
            inputs.put("esCommonService", projection);
            inputs.put("esOperations", projection);
            inputs.put("jsons", new JsonModule());
            return MagicScript.create(script, null).execute(new MagicScriptContext(inputs));
        }

        private Object checkEligibility(Object[] arguments) {
            assertEquals(1, arguments.length);
            var batch = StructuredValues.objectRows(arguments[0]);
            eligibilityBatches.add(batch);
            if (eligibilityFailure != null) {
                throw eligibilityFailure;
            }
            if (invalidEligibility) {
                return Map.of("eligibleIds", "invalid");
            }
            return Map.of("eligibleIds", batch.stream().map(row -> (String) row.get("tid"))
                    .filter(id -> !ineligible.contains(id)).toList());
        }

        List<List<String>> eligibilityQueries() {
            return eligibilityBatches.stream()
                    .map(rows -> rows.stream().map(row -> (String) row.get("tid")).toList()).toList();
        }

        void legacyDraft(String tid, String applicant, String catalogs, int status) {
            jdbc.update("""
                    INSERT INTO data_apply_form_t (tid,tenant_id,apply_user_id,created_by,type,is_del,flow_status,catalog_ids)
                    VALUES (?,'tenant-a',?,'owner','catalogSubscribe',0,?,?)
                    """, tid, applicant, status, catalogs);
        }

        String catalogsOf(String tid) {
            return jdbc.queryForObject("SELECT catalog_ids FROM data_apply_form_t WHERE tid=?", String.class, tid);
        }

        void changeCatalogFromAnotherConnection(String tid, String catalogs) {
            try (var connection = jdbc.getDataSource().getConnection();
                 var update = connection.prepareStatement("UPDATE data_apply_form_t SET catalog_ids=? WHERE tid=?")) {
                connection.setAutoCommit(true);
                update.setString(1, catalogs);
                update.setString(2, tid);
                assertEquals(1, update.executeUpdate());
            } catch (SQLException error) {
                throw new IllegalStateException("Could not simulate concurrent draft edit", error);
            }
        }

        int count(String table) {
            return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        }
    }
}
