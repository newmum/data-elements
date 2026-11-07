package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.hutool.json.JSONUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
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
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.LowerColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class CatalogCommandMagicTest {
    private static final String COMMAND_ID = "0454d01c38ab5d66aafdaf02d9b547d6";

    @Test
    void savePreflightUsesFixedReadsAndRejectsUnauthorizedOrDuplicateSources() throws IOException {
        var fixture = new Fixture();
        fixture.catalog("foreign-existing", "tenant-b", "owned-table", 0, 0, 1);
        for (int size : List.of(20, 100)) {
            var result = assertInstanceOf(Map.class, fixture.run("", "", 1, draft("owned-table", size, true)));
            assertEquals("owned-source", result.get("sourceId"));
            assertEquals(size, result.get("itemCount"));
            assertEquals(4, fixture.reads);
            assertEquals(0, fixture.writes);
        }
        for (String unavailable : List.of("hidden-table", "unbound-table", "foreign-table")) {
            assertStatus(400, fixture.run("", "", 1, draft(unavailable, 20, false)));
            assertEquals(0, fixture.writes);
            assertEquals(0, fixture.saveCalls);
        }
        fixture.scope.mode = "ORG";
        assertStatus(400, fixture.run("", "", 1, draft("hidden-table", 20, false)));
        fixture.scope.mode = "ALL";
        assertInstanceOf(Map.class, fixture.run("", "", 1, draft("hidden-table", 20, true)));
        fixture.catalog("own-existing", "tenant-a", "owned-table", 0, 0, 1);
        assertStatus(409, fixture.run("", "", 1, draft("owned-table", 20, false)));
        fixture.entry = entry(1, "DRAFT", "person-a");
        assertInstanceOf(Map.class, fixture.run("own-existing", "", 1, draft("owned-table", 20, true)));
        assertEquals(0, fixture.saveCalls);
    }

    @Test
    void submitAcceptsLegacyNullsAndRejectsAConcurrentVersionChange() throws IOException {
        var fixture = new Fixture();
        fixture.catalog("catalog-a", "tenant-a", "owned-table", 0, null, null);
        fixture.catalog("catalog-a", "tenant-b", "owned-table", 0, null, null);
        fixture.jdbc.update("INSERT INTO da_catalog_item_t VALUES ('catalog-a','tenant-a',0)");
        fixture.entry = entry(1, "DRAFT", "person-a");
        assertStatus(409, fixture.run("catalog-a", "submit", 2, Map.of()));
        assertEquals(0, fixture.writes);

        fixture.beforeWrite = () -> fixture.jdbc.update("""
                UPDATE da_catalog_t SET resource_revision_no = 2 WHERE tid = 'catalog-a' AND tenant_id = 'tenant-a'
                """);
        assertStatus(409, fixture.run("catalog-a", "submit", 1, Map.of()));
        assertEquals(List.of(0, -1, 2), fixture.state("tenant-a"));
        assertEquals(0, fixture.index.refreshes);

        fixture.jdbc.update("""
                UPDATE da_catalog_t SET resource_revision_no = NULL WHERE tid = 'catalog-a' AND tenant_id = 'tenant-a'
                """);
        var success = assertInstanceOf(Map.class, fixture.run("catalog-a", "submit", 1, Map.of()));
        assertEquals("PENDING_REVIEW", success.get("state"));
        assertEquals(List.of(1, 1, 2), fixture.state("tenant-a"));
        assertEquals(List.of(0, -1, -1), fixture.state("tenant-b"));
        assertEquals(1, fixture.index.refreshes);
    }

    @Test
    void committedCatalogTransitionSurvivesAProjectionFailureWithoutGenericFallback() throws IOException {
        var fixture = new Fixture();
        fixture.catalog("catalog-a", "tenant-a", "owned-table", 0, null, 1);
        fixture.jdbc.update("INSERT INTO da_catalog_item_t VALUES ('catalog-a','tenant-a',0)");
        fixture.entry = entry(1, "DRAFT", "person-a");
        fixture.index.fail = true;

        var result = assertInstanceOf(Map.class, fixture.run("catalog-a", "submit", 1, Map.of()));

        assertEquals("PENDING_REVIEW", result.get("state"));
        assertEquals(List.of(1, 1, 2), fixture.state("tenant-a"));
        assertEquals(1, fixture.index.attempts);
        assertEquals(0, fixture.index.genericWrites);
    }

    @Test
    void reviewRejectsSelfApprovalAndRollsBackStatusWhenAuditWriteFails() throws IOException {
        var fixture = new Fixture();
        fixture.catalog("catalog-a", "tenant-a", "owned-table", 1, 1, null);
        fixture.entry = entry(1, "PENDING_REVIEW", "person-a");
        assertStatus(403, fixture.run("catalog-a", "review", 1, Map.of("approve", true)));
        assertEquals(0, fixture.writes);

        fixture.entry = entry(1, "PENDING_REVIEW", "submitter");
        fixture.jdbc.execute("ALTER TABLE da_prop_t ADD CONSTRAINT validation_opinion CHECK (prop_name <> 'resourceCenterAuditOpinion')");
        assertThrows(RuntimeException.class, () -> fixture.run("catalog-a", "review", 1, Map.of("approve", true)));
        assertEquals(List.of(1, 1, -1), fixture.state("tenant-a"));
        assertEquals(0, fixture.index.refreshes);

        fixture.jdbc.execute("ALTER TABLE da_prop_t DROP CONSTRAINT validation_opinion");
        var approved = assertInstanceOf(Map.class, fixture.run("catalog-a", "review", 1,
                Map.of("approve", true, "opinion", "validation")));
        assertEquals(true, approved.get("approved"));
        assertEquals(List.of(2, 2, 2), fixture.state("tenant-a"));
        assertEquals(1, fixture.index.refreshes);
        assertEquals("validation", fixture.jdbc.queryForObject("SELECT prop_value FROM da_prop_t", String.class));

        fixture.entry = entry(2, "APPROVED", "submitter");
        assertInstanceOf(Map.class, fixture.run("catalog-a", "new-version", 2, Map.of()));
        assertEquals(List.of(0, 0, 3), fixture.state("tenant-a"));
        assertEquals(2, fixture.index.refreshes);
    }

    @Test
    void publicationValidatesAllVisibleOrganizationsWithFixedReadsBeforeUpdating() throws IOException {
        var fixture = new Fixture();
        fixture.jdbc.update("INSERT INTO rm_org_t VALUES ('org-a','tenant-a',0),('foreign','tenant-b',0),('deleted','tenant-a',1)");
        var organizations = new ArrayList<String>();
        for (int index = 0; index < 100; index++) {
            String id = "visibility-" + index;
            organizations.add(id);
            fixture.jdbc.update("INSERT INTO rm_org_t VALUES (?,'tenant-a',0)", id);
        }
        fixture.jdbc.update("INSERT INTO res_catalog_publish_request(tid,tenant_id,request_status,is_del) VALUES "
                + "('revision-a','tenant-a','APPROVED',0),('revision-a','tenant-b','APPROVED',0)");
        for (int size : List.of(20, 100)) {
            fixture.entry = publicationDraft(organizations.subList(0, size));
            var result = assertInstanceOf(Map.class, fixture.runPublication());
            assertEquals("PUBLISHED", result.get("state"));
            assertEquals(2, fixture.reads);
            assertEquals(1, fixture.writes);
            assertEquals("APPROVED", fixture.jdbc.queryForObject("SELECT request_status FROM res_catalog_publish_request "
                    + "WHERE tid='revision-a' AND tenant_id='tenant-b'", String.class));
            fixture.jdbc.update("UPDATE res_catalog_publish_request SET request_status='APPROVED' WHERE tenant_id='tenant-a'");
        }
        for (String unavailable : List.of("foreign", "deleted", "missing")) {
            fixture.entry = publicationDraft(List.of("visibility-0", unavailable));
            assertStatus(400, fixture.runPublication());
            assertEquals(2, fixture.reads);
            assertEquals(0, fixture.writes);
        }
    }

    private static Map<String, Object> publicationDraft(List<String> organizations) {
        return Map.of("draft", Map.ofEntries(
                Map.entry("id", "revision-a"), Map.entry("state", "APPROVED"), Map.entry("provider", "org-a"),
                Map.entry("resourceId", "owned-table"), Map.entry("name", "验证目录"), Map.entry("code", "validation"),
                Map.entry("summary", "验证"), Map.entry("contact", "验证"), Map.entry("classificationId", "class-a"),
                Map.entry("format", "CSV"), Map.entry("frequency", "ONCE"), Map.entry("shareMode", "UNCONDITIONAL"),
                Map.entry("visibility", "DEPARTMENTS"), Map.entry("visibleDepartments", organizations),
                Map.entry("fieldIds", List.of("field-a"))));
    }

    private static Map<String, Object> draft(String table, int size, boolean dryRun) {
        List<Map<String, String>> items = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            items.add(Map.of("colName", "验证列" + i, "colEn", "validation_" + i, "colType", "VARCHAR"));
        }
        return Map.of("propList", new LinkedHashMap<>(Map.of("catalogName", "验证目录", "orgId", "org-a", "sourceTableId", table)),
                "catalogItems", items, "dryRun", dryRun);
    }

    private static Map<String, Object> entry(int version, String state, String submitter) {
        return Map.of("version", version, "draft", Map.of("state", state, "submittedBy", submitter));
    }

    private static void assertStatus(int code, Object result) {
        var exit = assertInstanceOf(ExitValue.class, result);
        assertEquals(code, ((Number) exit.getValues()[0]).intValue());
    }

    public static class Scope {
        String mode = "OWNER";

        public String id() {
            return "tenant-a";
        }

        public Map<String, Object> dataSource() {
            return Map.of("scope", mode, "userId", "person-a", "organizationIds", List.of("org-a"));
        }
    }

    public static class Actor {
        public String getLoginIdAsString() {
            return "person-a";
        }
    }

    public static class Index {
        int refreshes;
        int attempts;
        int genericWrites;
        boolean fail;

        public String mergeCatalogProjection(String index, String id, String content, Object count) {
            var document = JSONUtil.parseObj(content);
            assertEquals("dataassets", index);
            assertEquals(id, document.getStr("tid"));
            assertEquals("tenant-a", document.getStr("tenantId"));
            assertEquals("catalog", document.getStr("assetType"));
            assertEquals(41L, ((Number) count).longValue());
            assertEquals(41L, document.getLong("viewCount"));
            attempts++;
            if (fail) throw new IllegalStateException("validation projection unavailable");
            refreshes++;
            return id;
        }

        public void saveOrUpdate(String index, String id, String document, boolean refresh) {
            genericWrites++;
        }
    }

    public static class Jsons {
        public String toJSONStr(Object value) {
            return JSONUtil.toJsonStr(value);
        }
    }

    private static class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final Scope scope = new Scope();
        final Index index = new Index();
        final MagicScript script;
        int reads;
        int writes;
        int saveCalls;
        Map<String, Object> entry;
        Runnable beforeWrite = () -> {};

        Fixture() throws IOException {
            var source = new DriverManagerDataSource("jdbc:h2:mem:catalog_command_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    CREATE TABLE da_catalog_t (tid VARCHAR(64), tenant_id VARCHAR(64), source_table_id VARCHAR(64),
                      asset_status INT, flow_status INT, resource_revision_no INT, updated_by VARCHAR(64),
                      updated_time TIMESTAMP, is_del INT)
                    """);
            jdbc.execute("CREATE TABLE da_catalog_item_t (catalog_id VARCHAR(64), tenant_id VARCHAR(64), is_del INT)");
            jdbc.execute("""
                    CREATE TABLE da_prop_t (tid VARCHAR(64), tenant_id VARCHAR(64), parent_id VARCHAR(64),
                      data_type VARCHAR(64), prop_name VARCHAR(64), prop_type VARCHAR(64), prop_value VARCHAR(255),
                      is_del INT, created_by VARCHAR(64), updated_by VARCHAR(64), created_time TIMESTAMP, updated_time TIMESTAMP)
                    """);
            jdbc.execute("CREATE TABLE db_table_t (tid VARCHAR(64), tenant_id VARCHAR(64), datasource_id VARCHAR(64), table_name VARCHAR(64), is_del INT)");
            jdbc.execute("CREATE TABLE db_datasource_t (tid VARCHAR(64), tenant_id VARCHAR(64), created_by VARCHAR(64), org_id VARCHAR(64), is_del INT)");
            jdbc.execute("CREATE TABLE dwm_center_layer_source_t (datasource_id VARCHAR(64), tenant_id VARCHAR(64), target_type VARCHAR(64), is_del INT)");
            jdbc.execute("CREATE TABLE rm_org_t (id VARCHAR(64), tenant_id VARCHAR(64), deleted INT)");
            jdbc.execute("""
                    CREATE TABLE res_catalog_publish_request (tid VARCHAR(64), tenant_id VARCHAR(64),
                      request_status VARCHAR(32), is_del INT, published_time TIMESTAMP, updated_time TIMESTAMP,
                      updated_by VARCHAR(64), revision_no INT)
                    """);
            jdbc.update("""
                    INSERT INTO db_table_t VALUES
                    ('owned-table','tenant-a','owned-source','owned',0),
                    ('hidden-table','tenant-a','hidden-source','hidden',0),
                    ('unbound-table','tenant-a','unbound-source','unbound',0),
                    ('foreign-table','tenant-b','owned-source','foreign',0)
                    """);
            jdbc.update("""
                    INSERT INTO db_datasource_t VALUES
                    ('owned-source','tenant-a','person-a','org-a',0),
                    ('hidden-source','tenant-a','person-b','org-b',0),
                    ('unbound-source','tenant-a','person-a','org-a',0)
                    """);
            jdbc.update("""
                    INSERT INTO dwm_center_layer_source_t VALUES
                    ('owned-source','tenant-a','layer',0),
                    ('hidden-source','tenant-a','domain',0),
                    ('unbound-source','tenant-b','layer',0)
                    """);
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
                public void preHandle(BoundSql statement, RequestEntity request) {
                    if (statement.getSql().stripLeading().regionMatches(true, 0, "SELECT", 0, 6)) {
                        reads++;
                    } else {
                        writes++;
                        Runnable hook = beforeWrite;
                        beforeWrite = () -> {};
                        hook.run();
                    }
                }
            }));
            String body = CanonicalMagicSources.byId(COMMAND_ID)
                    .replaceAll("(?m)^import (?!java\\.|cn\\.hutool\\.)[^\\r\\n]*\\r?\\n", "");
            script = MagicScript.create(body, null);
        }

        void catalog(String id, String tenant, String source, int asset, Integer flow, Integer version) {
            jdbc.update("INSERT INTO da_catalog_t VALUES (?,?,?,?,?,?,NULL,NULL,0)", id, tenant, source, asset, flow, version);
        }

        List<Integer> state(String tenant) {
            return jdbc.queryForObject("""
                    SELECT asset_status, COALESCE(flow_status,-1), COALESCE(resource_revision_no,-1)
                      FROM da_catalog_t WHERE tid = 'catalog-a' AND tenant_id = ?
                    """, (rs, row) -> List.of(rs.getInt(1), rs.getInt(2), rs.getInt(3)), tenant);
        }

        Object run(String id, String action, int version, Map<String, Object> payload) {
            return run("catalogs", id, action, version, payload);
        }

        Object runPublication() {
            return run("publications", "", "", 1, Map.of("entryId", "catalog-a", "revisionId", "revision-a"));
        }

        private Object run(String kind, String id, String action, int version, Map<String, Object> payload) {
            reads = 0;
            writes = 0;
            Function<Object[], Object> catalog = args -> {
                var input = assertInstanceOf(Map.class, args[0]);
                return switch (String.valueOf(input.get("mode"))) {
                    case "directory-index-projection" -> Map.of("id", input.get("id"), "tid", input.get("id"),
                            "tenantId", "tenant-a", "assetType", "catalog", "viewCount", 41L);
                    case "structure" -> Map.of("fields", List.of(Map.of("id", "field-a", "name", "validation_field")));
                    default -> entry;
                };
            };
            Function<Object[], Object> save = args -> {
                saveCalls++;
                throw new AssertionError("Validation must not invoke catalog persistence");
            };
            Function<Object[], Object> currentUser = args -> Map.of("orgId", "org-a");
            var context = Map.ofEntries(
                    Map.entry("input", Map.of("kind", kind, "method", "POST", "id", id, "action", action,
                            "body", payload, "version", version)),
                    Map.entry("db", db), Map.entry("tenantRuntime", scope), Map.entry("dataScope", scope),
                    Map.entry("StpUtil", new Actor()), Map.entry("currentUser", currentUser),
                    Map.entry("readPlanning", (Function<Object[], Object>) args -> Map.of("classifications", List.of(Map.of("id", "class-a")))),
                    Map.entry("readCatalog", catalog), Map.entry("saveCatalog", save),
                    Map.entry("esCommonService", index), Map.entry("jsons", new Jsons()),
                    Map.entry("log", LoggerFactory.getLogger(CatalogCommandMagicTest.class)));
            return script.execute(new MagicScriptContext(context));
        }
    }
}
