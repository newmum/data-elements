package com.linewell.dataelement.platform.magic;

import cn.hutool.json.JSONUtil;
import com.linewell.dataelement.platform.magic.module.JsonModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.*;
import org.ssssssss.magicapi.modules.db.dialect.*;
import org.ssssssss.magicapi.modules.db.provider.*;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

/** Execute maintained Magic reads against SQL, including other-tenant and owner-scope decoys. */
class ResourceTablePagingTest {
    private static final String CATALOG_READ = "084a92c02bd05f8bb8507cc4c4660e40";
    private static final String RESOURCE_COMMAND = "b6aaff9e31925977b94369d347e7c32a";
    private static final String CATALOG_ITEMS = "datapd_catalog_items_list_01";
    private static final String TABLE_PAGE = "datapd_db_table_page_01";
    private static final String LAYER_PAGE = "resource_layer_page_01";
    private static final String DWD_LIST = "5ab722975fb242aa9944638c3fa9c51b";
    private static final String DWS_LIST = "ea38fe47d5324bf0be61209dbfef3791";
    private static final String RESOURCE_STATE = "f32458ca37a55794b4cb49cfb191ca76";
    private static final LocalDateTime VALIDATION_TIME = LocalDateTime.of(2026, 1, 1, 12, 0);

    public static class Tenant { public String id(){return "tenant-a";} }
    public static class Scope {
        private final String kind;
        private final List<String> organizations;

        Scope() { this("OWNER", List.of("org-a")); }
        Scope(String kind, List<String> organizations) {
            this.kind = kind;
            this.organizations = organizations;
        }

        public Map<String, Object> dataSource() {
            return Map.of("scope", kind, "userId", "owner", "organizationIds", organizations);
        }
    }

    private enum DatabaseMode {
        MYSQL("MySQL", "mysql"), POSTGRESQL("PostgreSQL", "postgresql");

        private final String h2Mode;
        private final String driverName;

        DatabaseMode(String h2Mode, String driverName) {
            this.h2Mode = h2Mode;
            this.driverName = driverName;
        }
    }

    private static class Counter {
        int count;
        int writes;

        void reset() {
            count = 0;
            writes = 0;
        }

        void record(String sql) {
            count++;
            if (!sql.stripLeading().regionMatches(true, 0, "SELECT", 0, 6)) {
                writes++;
            }
        }
    }

    /** Count prepared statements in both the native Magic and tableRead paths. */
    private static class CountedSource extends DriverManagerDataSource {
        private final DatabaseMode mode;
        private final Counter counter;

        CountedSource(DatabaseMode mode, Counter counter) {
            super("jdbc:h2:mem:resources_" + UUID.randomUUID() + ";MODE=" + mode.h2Mode
                    + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            this.mode = mode;
            this.counter = counter;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if ("getMetaData".equals(method.getName())) {
                            DatabaseMetaData metadata = connection.getMetaData();
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                    (ignored, metaMethod, metaArgs) -> "getURL".equals(metaMethod.getName())
                                            ? "jdbc:" + mode.driverName + "://resource-table-validation"
                                            : invoke(metadata, metaMethod, metaArgs));
                        }
                        if ("prepareStatement".equals(method.getName())) {
                            counter.record((String) args[0]);
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
    @ParameterizedTest
    @EnumSource(DatabaseMode.class)
    void catalogSortsEntireAuthorizedResultBeforePagingWithThreeReads(DatabaseMode mode) throws Exception {
        var f = new Fixture(mode);
        // Repeated active bindings may establish eligibility, but may never duplicate catalog rows.
        f.database.update("INSERT INTO dwm_center_layer_source_t VALUES('validation-second-binding','tenant-a','permitted','layer',0)");
        for(String field:List.of("name","updatedAt","createdAt","itemCount","fields")) {
            f.counter.reset();var a=f.catalogCommand(20,field);assertEquals(3,f.counter.count);
            f.counter.reset();var b=f.catalogCommand(100,field);assertEquals(3,f.counter.count);
            assertEquals(0, f.counter.writes);
            var large=StructuredValues.objectRows(b.get("list"));
            assertEquals(120,((Number)b.get("total")).intValue());assertEquals(100,large.size());
            assertEquals(100, large.stream().map(row -> row.get("id")).distinct().count());
            assertEquals(a.get("list"),large.subList(0,20));
            assertTrue(large.stream().allMatch(row->row.get("id").toString().startsWith("cat-")));
            if(field.equals("itemCount") || field.equals("fields"))assertEquals("cat-119",large.getFirst().get("id"));
        }
        f.counter.count=0;assertThrows(Exception.class,()->f.catalog(20,"untrusted SQL"));assertEquals(0,f.counter.count);
        f.counter.reset();
        var last = f.catalog(2, 100, Map.of("sortField", "itemCount"), new Scope());
        assertEquals(20, StructuredValues.objectRows(last.get("list")).size());
        assertEquals(3, f.counter.count);
        f.counter.reset();
        var empty = f.catalog(3, 100, Map.of("sortField", "itemCount"), new Scope());
        assertEquals(120, ((Number) empty.get("total")).intValue());
        assertEquals(3, empty.get("page"));
        assertEquals(List.of(), empty.get("list"));
        assertEquals(1, f.counter.count);
        System.out.printf("Resource catalog %s: 20/100 rows each 1 gateway request, 3 SQL; last page 3, empty page 1%n", mode);
    }

    @ParameterizedTest
    @EnumSource(DatabaseMode.class)
    void catalogAccessFollowsActiveSourcesAndBindingsWithinTheCurrentTenant(DatabaseMode mode) throws Exception {
        var f = new Fixture(mode);
        f.database.update("UPDATE da_catalog_t SET created_by='another-user',org_id='org-unrelated' WHERE tid LIKE 'cat-%'");
        f.datasource("validation-owner-other-org", "tenant-a", "owner", "org-b", 0);
        f.binding("validation-owner-other-org", "tenant-a", "domain", 0);
        f.table("validation-owner-other-org", "tenant-a", "validation-owner-other-org", 0);
        f.catalogRow("validation-owner-other-org", "tenant-a", "validation-owner-other-org", 0);
        f.datasource("validation-outside", "tenant-a", "another-user", "org-b", 0);
        f.binding("validation-outside", "tenant-a", "layer", 0);
        f.table("validation-outside", "tenant-a", "validation-outside", 0);
        f.catalogRow("validation-outside", "tenant-a", "validation-outside", 0);

        f.catalogRow("validation-foreign-catalog", "tenant-b", "permitted", 0);
        f.catalogRow("validation-deleted-catalog", "tenant-a", "permitted", 1);
        f.table("validation-foreign-table", "tenant-b", "permitted", 0);
        f.catalogRow("validation-foreign-table", "tenant-a", "validation-foreign-table", 0);
        f.table("validation-deleted-table", "tenant-a", "permitted", 1);
        f.catalogRow("validation-deleted-table", "tenant-a", "validation-deleted-table", 0);
        for (String source : List.of("validation-foreign-source", "validation-deleted-source",
                "validation-foreign-binding", "validation-deleted-binding", "validation-wrong-binding")) {
            f.datasource(source, source.endsWith("foreign-source") ? "tenant-b" : "tenant-a",
                    "owner", "org-a", source.endsWith("deleted-source") ? 1 : 0);
            f.binding(source, source.endsWith("foreign-binding") ? "tenant-b" : "tenant-a",
                    source.endsWith("wrong-binding") ? "connection" : "layer", source.endsWith("deleted-binding") ? 1 : 0);
            f.table(source, "tenant-a", source, 0);
            f.catalogRow(source, "tenant-a", source, 0);
        }

        var owner = new Scope();
        var organization = new Scope("ORG", List.of("org-a"));
        var all = new Scope("ALL", List.of());
        var input = Map.<String, Object>of("tenantId", "tenant-b", "orgId", "org-b", "scope", "ALL");
        var ownerPage = f.catalog(1, 100, input, owner);
        assertEquals(121, ((Number) ownerPage.get("total")).intValue());
        assertTrue(StructuredValues.objectRows(ownerPage.get("list")).stream().map(row -> row.get("id").toString())
                .allMatch(id -> id.startsWith("cat-") || id.equals("validation-owner-other-org")));
        var organizationPage = f.catalog(1, 100, input, organization);
        assertEquals(240, ((Number) organizationPage.get("total")).intValue());
        assertTrue(StructuredValues.objectRows(organizationPage.get("list")).stream().map(row -> row.get("id").toString())
                .allMatch(id -> id.startsWith("cat-") || id.startsWith("hidden-")));
        assertEquals(242, ((Number) f.catalog(1, 100, input, all).get("total")).intValue());
        assertEquals(0, ((Number) f.catalog(1, 100, input, new Scope("ORG", List.of())).get("total")).intValue());

        assertSingleCatalog(f, "cat-001", owner);
        assertSingleCatalog(f, "validation-owner-other-org", owner);
        assertSingleCatalog(f, "hidden-001", organization);
        assertSingleCatalog(f, "validation-outside", all);
        assertUnavailableCatalog(f, "hidden-001", owner);
        assertUnavailableCatalog(f, "validation-owner-other-org", organization);
        assertUnavailableCatalog(f, "validation-outside", organization);
        for (String id : List.of("validation-foreign-catalog", "validation-deleted-catalog",
                "validation-foreign-table", "validation-deleted-table", "validation-foreign-source",
                "validation-deleted-source", "validation-foreign-binding", "validation-deleted-binding",
                "validation-wrong-binding", "validation-missing")) {
            assertUnavailableCatalog(f, id, all);
        }
    }

    @ParameterizedTest
    @EnumSource(DatabaseMode.class)
    void catalogFiltersAndDraftProjectionPreserveThePublicContract(DatabaseMode mode) throws Exception {
        var f = new Fixture(mode);
        f.database.update("""
                UPDATE da_catalog_t SET catalog_name='validation name',catalog_name_en='validation_code',
                    source_table_name='validation technical table',db_id='validation-declared-source',
                    flow_status=1,resource_revision_no=NULL,updated_by='validation-reviewer'
                WHERE tid='cat-119'
                """);
        f.database.update("UPDATE da_catalog_t SET flow_status=3 WHERE tid='cat-118'");
        f.database.update("UPDATE da_catalog_t SET asset_status=1 WHERE tid='cat-117'");
        f.database.update("UPDATE da_catalog_item_t SET source_table_column_id=NULL,col_name='首项' WHERE tid='119-0'");
        f.database.update("UPDATE da_catalog_item_t SET col_name='第二项' WHERE tid='119-1'");
        f.database.update("UPDATE da_catalog_item_t SET is_del=1 WHERE catalog_id='cat-000'");
        f.database.update("""
                INSERT INTO da_catalog_item_t VALUES
                  ('validation-foreign-item','tenant-b','cat-119','validation-foreign-field','Foreign',0,0),
                  ('validation-deleted-item','tenant-a','cat-119','validation-deleted-field','Deleted',0,1),
                  ('validation-foreign-only','tenant-b','cat-000','validation-foreign-only','Foreign',0,0)
                """);

        var scope = new Scope();
        for (String keyword : List.of(" validation name ", "validation_code", "technical table", "validation%name")) {
            assertEquals(1, ((Number) f.catalog(1, 20, Map.of("keyword", keyword), scope).get("total")).intValue());
        }
        assertEquals(0, ((Number) f.catalog(1, 20, Map.of("keyword", "' OR 1=1 --"), scope).get("total")).intValue());
        assertEquals(119, ((Number) f.catalog(1, 100, Map.of("cataloged", true), scope).get("total")).intValue());
        assertEquals(117, ((Number) f.catalog(1, 20, Map.of("status", "APPROVED"), scope).get("total")).intValue());
        for (var state : Map.of("RETURNED", "cat-118", "DRAFT", "cat-117").entrySet()) {
            var response = f.catalog(1, 20, Map.of("status", state.getKey()), scope);
            var row = StructuredValues.objectRows(response.get("list")).getFirst();
            assertEquals(1, ((Number) response.get("total")).intValue());
            assertEquals(state.getValue(), row.get("id"));
            assertEquals(state.getKey(), StructuredValues.objectMap(row.get("draft")).get("state"));
        }

        f.counter.reset();
        var response = f.catalog(1, 20, Map.of("status", "PENDING_REVIEW", "cataloged", true,
                "sourceId", "validation-declared-source", "keyword", "validation technical table"), scope);
        assertEquals(3, f.counter.count);
        assertEquals(Set.of("list", "total", "page", "size"), response.keySet());
        assertEquals(1, ((Number) response.get("total")).intValue());
        var row = StructuredValues.objectRows(response.get("list")).getFirst();
        assertEquals(Set.of("id", "version", "draft", "revisions", "createdAt", "updatedAt", "createdBy"), row.keySet());
        assertEquals("cat-119", row.get("id"));
        assertEquals(1, ((Number) row.get("version")).intValue());
        assertEquals(List.of(), row.get("revisions"));
        var draft = StructuredValues.objectMap(row.get("draft"));
        assertEquals("PENDING_REVIEW", draft.get("state"));
        assertEquals("validation-declared-source", draft.get("sourceId"));
        assertEquals("permitted", draft.get("resourceId"));
        assertEquals("validation-reviewer", draft.get("submittedBy"));
        assertNotNull(draft.get("submittedAt"));
        var fieldIds = (List<?>) draft.get("fieldIds");
        assertEquals(120, fieldIds.size());
        assertEquals(List.of("119-0", "f1"), fieldIds.subList(0, 2));
        assertFalse(fieldIds.contains("validation-foreign-field"));
        assertFalse(fieldIds.contains("validation-deleted-field"));
        var labels = StructuredValues.objectMap(draft.get("fieldLabels"));
        assertEquals("首项", labels.get("119-0"));
        assertEquals("第二项", labels.get("f1"));

        f.counter.reset();
        var badStatus = assertInstanceOf(ExitValue.class, f.catalogRead(
                Map.of("mode", "page", "page", 1, "size", 20, "query", Map.of("status", "UNKNOWN")), scope));
        assertEquals("400", String.valueOf(badStatus.getValues()[0]));
        assertEquals(0, f.counter.count);
    }

    private static void assertSingleCatalog(Fixture fixture, String id, Scope scope) throws Exception {
        fixture.counter.reset();
        var row = StructuredValues.objectMap(fixture.catalogRead(Map.of("mode", "one", "id", id), scope));
        assertEquals(id, row.get("id"));
        assertTrue(row.containsKey("draft"));
        assertEquals(2, fixture.counter.count);
        assertEquals(0, fixture.counter.writes);
    }

    private static void assertUnavailableCatalog(Fixture fixture, String id, Scope scope) throws Exception {
        fixture.counter.reset();
        var response = assertInstanceOf(ExitValue.class, fixture.catalogRead(Map.of("mode", "one", "id", id), scope));
        assertEquals("404", String.valueOf(response.getValues()[0]));
        assertEquals(1, fixture.counter.count);
        assertEquals(0, fixture.counter.writes);
    }

    @ParameterizedTest
    @EnumSource(DatabaseMode.class)
    void resourceStateRetainsCompatibilityShapeAndBulkReadBudget(DatabaseMode mode) throws Exception {
        var fixture = new Fixture(mode, 0);
        fixture.createCompatibilityTables();
        fixture.catalogRow("validation-hidden-catalog", "tenant-a", "forbidden", 0);
        fixture.catalogRow("validation-foreign-catalog", "tenant-b", "permitted", 0);
        int previousSize = 0;
        for (int size : List.of(20, 100, 101)) {
            for (int index = previousSize + 1; index <= size; index++) {
                String catalog = "validation-catalog-" + index;
                String publication = "validation-publication-" + index;
                String subscription = "validation-subscription-" + index;
                String delivery = "validation-delivery-" + index;
                fixture.catalogRow(catalog, "tenant-a", "permitted", 0);
                fixture.database.update("UPDATE da_catalog_t SET updated_time=? WHERE tid=?", VALIDATION_TIME.plusSeconds(index), catalog);
                fixture.database.update("INSERT INTO da_catalog_item_t VALUES(?,'tenant-a',?,?,'字段',1,0)",
                        "validation-item-" + index, catalog, "validation-field-" + index);
                fixture.publication(publication, "tenant-a", catalog, "PUBLISHED", index, 0,
                        Map.of("name", catalog, "provider", "org-b", "visibility", "WORKSPACE"));
                fixture.subscription(subscription, "tenant-a", "owner", 0,
                        subscriptionSnapshot(publication, "ACTIVE", "2000-01-01T00:00:00Z", "2999-01-01T00:00:00Z"));
                fixture.delivery(delivery, "tenant-a", subscription, "RESOURCE_EXPORT", 0, index,
                        Map.of("content", "validation export", "filename", "validation.csv"));
            }

            fixture.counter.reset();
            var response = fixture.resourceState(new Scope());
            assertEquals(size > 100 ? 18 : 9, fixture.counter.count);
            assertEquals(0, fixture.counter.writes);
            assertEquals(3, response.get("pageNo"));
            assertEquals(20, response.get("pageSize"));
            assertEquals("validation-keyword", response.get("entityKeyword"));
            assertEquals(Map.of("id", "owner", "orgId", "org-a", "name", "Authorized actor"), response.get("actor"));
            var capabilities = StructuredValues.objectMap(response.get("capabilities"));
            assertEquals(true, capabilities.get("manualDelivery"));
            assertEquals(false, capabilities.get("scheduledDelivery"));
            var state = StructuredValues.objectMap(response.get("state"));
            assertEquals(Set.of("schemaVersion", "ignoredMatches", "technicalRevisions", "autoApprove", "busConfigured",
                    "layers", "models", "catalogs", "publications", "subscriptions", "deliveries", "deliveryScheduling"), state.keySet());
            assertEquals(List.of(Map.of("id", "validation-layer")), state.get("layers"));
            assertEquals(List.of(Map.of("id", "validation-entity")), state.get("models"));
            assertEquals(false, state.get("deliveryScheduling"));

            var catalogs = StructuredValues.objectRows(state.get("catalogs"));
            var publications = StructuredValues.objectRows(state.get("publications"));
            var subscriptions = StructuredValues.objectRows(state.get("subscriptions"));
            var deliveries = StructuredValues.objectRows(state.get("deliveries"));
            assertEquals(size, catalogs.size());
            assertEquals(size, publications.size());
            assertEquals(size, subscriptions.size());
            assertEquals(size, deliveries.size());
            for (int index = 1; index <= size; index++) {
                var catalog = catalogs.get(size - index);
                assertEquals("validation-catalog-" + index, catalog.get("id"));
                var draft = StructuredValues.objectMap(catalog.get("draft"));
                assertEquals(List.of("validation-field-" + index), draft.get("fieldIds"));
                assertEquals("validation-publication-" + index, publications.get(index - 1).get("id"));
                assertEquals("validation-delivery-" + index, deliveries.get(size - index).get("id"));
                assertFalse(deliveries.get(size - index).containsKey("content"));
            }
            assertTrue(subscriptions.stream().allMatch(row -> "ACTIVE".equals(row.get("state"))));
            var subscriptionIds = subscriptions.stream().map(row -> row.get("id").toString()).toList();
            assertEquals(subscriptionIds.stream().sorted().toList(), subscriptionIds);
            if (size == 20) {
                for (String kind : List.of("ORG", "ALL")) {
                    var scoped = StructuredValues.objectMap(fixture.resourceState(new Scope(kind, List.of("org-a"))).get("state"));
                    var scopedCatalogIds = StructuredValues.objectRows(scoped.get("catalogs")).stream().map(row -> row.get("id")).toList();
                    assertEquals(21, scopedCatalogIds.size());
                    assertTrue(scopedCatalogIds.contains("validation-hidden-catalog"));
                    assertFalse(scopedCatalogIds.contains("validation-foreign-catalog"));
                }
            }
            previousSize = size;
        }
        System.out.printf("Resource state compatibility %s: 20/100 records each 9 SQL, 101 records 18 SQL in catalog dependency%n", mode);
    }

    @ParameterizedTest
    @EnumSource(DatabaseMode.class)
    void catalogStructureChecksTenantAndOrdersActiveColumns(DatabaseMode mode) throws Exception {
        var fixture = new Fixture(mode, 0);
        fixture.createCompatibilityTables();
        fixture.database.update("UPDATE db_table_t SET table_name='validation_table',table_name_cn='验证表',table_comment='测试' WHERE tid='forbidden'");
        fixture.table("validation-foreign-table", "tenant-b", "permitted", 0);
        fixture.table("validation-deleted-table", "tenant-a", "permitted", 1);
        fixture.column("validation-column-b", "tenant-a", "forbidden", 1, 0);
        fixture.column("validation-column-a", "tenant-a", "forbidden", 1, 0);
        fixture.column("validation-column-c", "tenant-a", "forbidden", 2, 0);
        fixture.column("validation-foreign-column", "tenant-b", "forbidden", 0, 0);
        fixture.column("validation-deleted-column", "tenant-a", "forbidden", 0, 1);
        fixture.column("validation-unrelated-column", "tenant-a", "permitted", 0, 0);

        // Structure also supports publication validation for an authorized consumer from another department.
        var structure = namedRead(fixture, "structure", "forbidden", 2);
        assertEquals(Set.of("id", "sourceId", "name", "displayName", "comment", "updatedAt", "fields", "kind", "keys"), structure.keySet());
        assertEquals("validation_table", structure.get("name"));
        assertEquals("验证表", structure.get("displayName"));
        assertEquals("table", structure.get("kind"));
        assertEquals(List.of(), structure.get("keys"));
        var fields = StructuredValues.objectRows(structure.get("fields"));
        assertEquals(List.of("validation-column-a", "validation-column-b", "validation-column-c"),
                fields.stream().map(row -> row.get("id")).toList());
        var first = fields.getFirst();
        assertEquals(Set.of("id", "name", "comment", "nativeType", "length", "scale", "nullable", "primaryKey",
                "ordinal", "masking", "path", "typeFamily"), first.keySet());
        assertEquals(first.get("name"), first.get("path"));
        assertEquals(first.get("nativeType"), first.get("typeFamily"));
        for (String id : List.of("validation-foreign-table", "validation-deleted-table", "validation-missing-table")) {
            assertNamedReadDenied(fixture, "structure", id, 1);
        }
    }

    private static Map<String, Object> namedRead(Fixture fixture, String mode, String id, int reads) throws Exception {
        fixture.counter.reset();
        var result = StructuredValues.objectMap(fixture.catalogRead(Map.of("mode", mode, "id", id), new Scope()));
        assertEquals(id, result.get("id"));
        assertEquals(reads, fixture.counter.count, mode + " " + id);
        assertEquals(0, fixture.counter.writes);
        return result;
    }

    private static void assertNamedReadDenied(Fixture fixture, String mode, String id, int reads) throws Exception {
        fixture.counter.reset();
        var result = fixture.catalogRead(Map.of("mode", mode, "id", id), new Scope());
        assertEquals("404", String.valueOf(assertInstanceOf(ExitValue.class, result).getValues()[0]));
        assertEquals(reads, fixture.counter.count, mode + " " + id);
        assertEquals(0, fixture.counter.writes);
    }

    private static Map<String, Object> subscriptionSnapshot(String publication, String state, String start, String end) {
        return Map.of("id", "forged-id", "applicantId", "forged-applicant", "department", "forged-department", "version", 99,
                "publicationId", publication, "state", state, "validFrom", start, "validTo", end);
    }

    @Test
    void publicationReadHonorsLegacyVisibilityAndReturnsRevisionMetadata() throws Exception {
        var fixture = new Fixture(DatabaseMode.MYSQL, 0);
        fixture.createCompatibilityTables();
        var visible = Map.<String, Object>of("visibility", "DEPARTMENTS", "provider", "org-b", "visibleDepartments", List.of("org-a"));
        var privateSnapshot = Map.<String, Object>of("visibility", "DEPARTMENTS", "provider", "org-b", "visibleDepartments", List.of("org-c"));
        fixture.publication("validation-visible", "tenant-a", "catalog-v", "PUBLISHED", 3, 0, visible);
        fixture.publication("validation-provider", "tenant-a", "catalog-v", "OFFLINE", 2, 0,
                Map.of("visibility", "DEPARTMENTS", "provider", "org-a", "visibleDepartments", List.of("org-c")));
        fixture.publication("validation-private", "tenant-a", "catalog-v", "PUBLISHED", 4, 0, privateSnapshot);
        fixture.publication("validation-pending", "tenant-a", "catalog-v", "PENDING", 5, 0, visible);
        fixture.publication("validation-deleted", "tenant-a", "catalog-v", "PUBLISHED", 6, 1, visible);
        fixture.publication("validation-foreign", "tenant-b", "catalog-v", "PUBLISHED", 7, 0, visible);
        fixture.publication("validation-no-snapshot", "tenant-a", "catalog-v", "PUBLISHED", 8, 0, null);

        var publication = namedRead(fixture, "publication", "validation-visible", 1);
        assertEquals(Set.of("id", "entryId", "revisionId", "revision", "state", "version", "busState",
                "publishedBy", "reason", "createdAt", "updatedAt"), publication.keySet());
        assertEquals("catalog-v", publication.get("entryId"));
        assertEquals("validation-visible", publication.get("revisionId"));
        assertEquals("PUBLISHED", publication.get("state"));
        assertEquals("NOT_CONFIGURED", publication.get("busState"));
        assertEquals("reviewer", publication.get("publishedBy"));
        assertEquals(5, ((Number) publication.get("version")).intValue());
        var revision = StructuredValues.objectMap(publication.get("revision"));
        assertEquals("APPROVED", revision.get("state"));
        assertEquals(3, ((Number) revision.get("number")).intValue());
        assertEquals(List.of(), revision.get("checks"));
        assertEquals("owner", revision.get("submittedBy"));
        assertEquals("reviewer", revision.get("reviewer"));
        assertEquals("approved for validation", revision.get("opinion"));
        assertEquals("OFFLINE", namedRead(fixture, "publication", "validation-provider", 1).get("state"));
        for (String id : List.of("validation-private", "validation-pending", "validation-deleted", "validation-foreign",
                "validation-no-snapshot", "validation-missing")) {
            assertNamedReadDenied(fixture, "publication", id, 1);
        }

        var snapshot = fixture.resourceState(new Scope());
        var state = StructuredValues.objectMap(snapshot.get("state"));
        assertEquals(List.of("validation-provider", "validation-visible"),
                StructuredValues.objectRows(state.get("publications")).stream().map(row -> row.get("id")).toList());
    }

    @ParameterizedTest
    @EnumSource(DatabaseMode.class)
    void formAndExportReadsUseApplicantOrProviderAndRetainLegacyStates(DatabaseMode mode) throws Exception {
        var fixture = new Fixture(mode, 0);
        fixture.createCompatibilityTables();
        String past = "2000-01-01T00:00:00Z", future = "2999-01-01T00:00:00Z";
        fixture.publication("validation-private", "tenant-a", "catalog-v", "PUBLISHED", 1, 0,
                Map.of("visibility", "DEPARTMENTS", "provider", "org-b", "visibleDepartments", List.of("org-c")));
        fixture.publication("validation-provider", "tenant-a", "catalog-v", "OFFLINE", 2, 0,
                Map.of("visibility", "DEPARTMENTS", "provider", "org-a", "visibleDepartments", List.of("org-c")));
        fixture.publication("validation-pending", "tenant-a", "catalog-v", "PENDING", 3, 0,
                Map.of("provider", "org-a"));
        fixture.publication("validation-foreign", "tenant-b", "catalog-v", "PUBLISHED", 4, 0,
                Map.of("provider", "org-a"));
        fixture.subscription("validation-own-form", "tenant-a", "owner", 0, subscriptionSnapshot("validation-private", "SCHEDULED", past, future));
        fixture.subscription("validation-provider-form", "tenant-a", "another-user", 0, subscriptionSnapshot("validation-provider", "ACTIVE", past, future));
        fixture.subscription("validation-pending-form", "tenant-a", "another-user", 0, subscriptionSnapshot("validation-pending", "PENDING", past, future));
        fixture.subscription("validation-expired-form", "tenant-a", "owner", 0, subscriptionSnapshot("validation-missing", "ACTIVE", past, past));
        fixture.subscription("validation-orphan-form", "tenant-a", "owner", 0, subscriptionSnapshot("validation-missing", "ACTIVE", past, future));
        fixture.subscription("validation-future-form", "tenant-a", "owner", 0, subscriptionSnapshot("validation-private", "SCHEDULED", future, future));
        fixture.subscription("validation-private-form", "tenant-a", "another-user", 0, subscriptionSnapshot("validation-private", "ACTIVE", past, future));
        fixture.subscription("validation-wrong-publication", "tenant-a", "another-user", 0, subscriptionSnapshot("validation-foreign", "ACTIVE", past, future));
        fixture.subscription("validation-deleted-form", "tenant-a", "owner", 1, subscriptionSnapshot("validation-private", "ACTIVE", past, future));
        fixture.subscription("validation-foreign-form", "tenant-b", "owner", 0, subscriptionSnapshot("validation-private", "ACTIVE", past, future));
        fixture.subscription("validation-no-scope", "tenant-a", "owner", 0, null);
        fixture.subscription("validation-no-provider", "tenant-a", "another-user", 0, subscriptionSnapshot("validation-missing", "ACTIVE", past, future));

        var owned = namedRead(fixture, "subscription", "validation-own-form", 2);
        assertEquals("ACTIVE", owned.get("state"));
        assertEquals("owner", owned.get("applicantId"));
        assertEquals("applicant-org", owned.get("department"));
        assertEquals(4, ((Number) owned.get("version")).intValue());
        assertEquals("PAUSED", namedRead(fixture, "subscription", "validation-provider-form", 2).get("state"));
        assertEquals("PENDING", namedRead(fixture, "subscription", "validation-pending-form", 2).get("state"));
        assertEquals("EXPIRED", namedRead(fixture, "subscription", "validation-expired-form", 2).get("state"));
        assertEquals("PAUSED", namedRead(fixture, "subscription", "validation-orphan-form", 2).get("state"));
        assertEquals("SCHEDULED", namedRead(fixture, "subscription", "validation-future-form", 2).get("state"));
        for (String id : List.of("validation-private-form", "validation-wrong-publication", "validation-no-provider")) {
            assertNamedReadDenied(fixture, "subscription", id, 2);
        }
        for (String id : List.of("validation-deleted-form", "validation-foreign-form", "validation-no-scope")) {
            assertNamedReadDenied(fixture, "subscription", id, 1);
        }
        var orgless = fixture.catalogRead(Map.of("mode", "subscription", "id", "validation-no-provider"), new Scope(), "");
        assertEquals("404", String.valueOf(assertInstanceOf(ExitValue.class, orgless).getValues()[0]));

        fixture.delivery("validation-own-export", "tenant-a", "validation-own-form", "RESOURCE_EXPORT", 0, 1,
                Map.of("content", "validation export", "filename", "validation.csv", "id", "forged-task", "state", "FORGED"));
        fixture.delivery("validation-provider-export", "tenant-a", "validation-provider-form", "RESOURCE_EXPORT", 0, 2, null);
        fixture.delivery("validation-denied-export", "tenant-a", "validation-private-form", "RESOURCE_EXPORT", 0, 3, Map.of());
        fixture.delivery("validation-foreign-form-export", "tenant-a", "validation-foreign-form", "RESOURCE_EXPORT", 0, 4, Map.of());
        fixture.delivery("validation-foreign-export", "tenant-b", "validation-own-form", "RESOURCE_EXPORT", 0, 5, Map.of());
        fixture.delivery("validation-deleted-export", "tenant-a", "validation-own-form", "RESOURCE_EXPORT", 1, 6, Map.of());
        fixture.delivery("validation-other-engine", "tenant-a", "validation-own-form", "NIFI", 0, 7, Map.of());

        var task = namedRead(fixture, "delivery", "validation-own-export", 3);
        assertEquals("validation-own-form", task.get("subscriptionId"));
        assertEquals("SUCCEEDED", task.get("state"));
        assertEquals(1, ((Number) task.get("version")).intValue());
        assertEquals("validation export", task.get("content"));
        assertEquals("validation.csv", task.get("filename"));
        assertEquals("validation-provider-form", namedRead(fixture, "delivery", "validation-provider-export", 3).get("subscriptionId"));
        assertNamedReadDenied(fixture, "delivery", "validation-denied-export", 3);
        assertNamedReadDenied(fixture, "delivery", "validation-foreign-form-export", 2);
        for (String id : List.of("validation-foreign-export", "validation-deleted-export", "validation-other-engine")) {
            assertNamedReadDenied(fixture, "delivery", id, 1);
        }

        var state = StructuredValues.objectMap(fixture.resourceState(new Scope()).get("state"));
        var subscriptions = StructuredValues.objectRows(state.get("subscriptions"));
        assertEquals(Set.of("validation-own-form", "validation-provider-form", "validation-pending-form", "validation-expired-form",
                "validation-orphan-form", "validation-future-form"), new HashSet<>(subscriptions.stream().map(row -> row.get("id")).toList()));
        var deliveries = StructuredValues.objectRows(state.get("deliveries"));
        assertEquals(List.of("validation-provider-export", "validation-own-export"), deliveries.stream().map(row -> row.get("id")).toList());
        assertTrue(deliveries.stream().noneMatch(row -> row.containsKey("content")));
    }
    @ParameterizedTest
    @EnumSource(DatabaseMode.class)
    void modelLogsUseFullSqlPaginationAndNeverReturnOtherTenantActorsOrModels(DatabaseMode mode) throws Exception {
        var f=new Fixture(mode);
        f.counter.count=0;var small=f.command(Map.of("table","modelLogs","page",1,"size",20,"sortField","modelName","sortDir","asc"));assertEquals(3,f.counter.count);
        f.counter.count=0;var large=f.command(Map.of("table","modelLogs","page",1,"size",100,"sortField","modelName","sortDir","asc"));assertEquals(3,f.counter.count);
        var rows=StructuredValues.objectRows(large.get("list"));assertEquals(120,((Number)large.get("total")).intValue());
        assertEquals(small.get("list"),rows.subList(0,20));assertEquals(100,rows.size());
        assertTrue(rows.stream().allMatch(row->"Authorized actor".equals(row.get("actorName"))));
        assertTrue(rows.stream().allMatch(row->row.get("id").toString().startsWith("log-")));
        f.counter.reset();
        assertThrows(Exception.class,()->f.command(Map.of("table","modelLogs","modelId","model-outside")));
        assertEquals(1, f.counter.count);
        System.out.printf("Model logs %s: 20/100 rows each 3 SQL%n", mode);
    }

    @ParameterizedTest
    @EnumSource(DatabaseMode.class)
    void modelLogReferencesAndFiltersPreserveHistoricalVisibility(DatabaseMode mode) throws Exception {
        var fixture = new Fixture(mode, 0);
        for (int index = 0; index < 3; index++) {
            fixture.addModel(index);
        }
        fixture.database.update("UPDATE res_logical_model SET model_status='ARCHIVED' WHERE tid='model-001'");
        fixture.database.update("UPDATE res_logical_model SET is_del=1 WHERE tid='model-002'");
        fixture.database.update("INSERT INTO rm_user_t VALUES('validation-fallback-actor','tenant-a','','validation-account'),"
                + "('validation-foreign-actor','tenant-b','Foreign actor','validation-foreign-account')");
        fixture.modelPlan("validation-plan-item", "tenant-a", "model-000", 0);
        fixture.modelItemSet("validation-plan-item", "tenant-a", "model-001", 0);
        fixture.modelPlan("validation-stale-plan", "tenant-a", "model-002", 0);
        fixture.modelItemSet("validation-stale-plan", "tenant-a", "model-001", 0);
        fixture.modelPlan("validation-deleted-plan", "tenant-a", "model-000", 1);
        fixture.modelItemSet("validation-deleted-plan", "tenant-a", "model-001", 0);
        fixture.modelPlan("model-001", "tenant-b", "model-000", 0);
        fixture.modelItemSet("model-000", "tenant-a", "model-001", 1);
        fixture.modelLog("validation-log-plan", "tenant-a", "owner", "物化执行完成", "validation-plan-item", "Plan", "valid marker");
        fixture.modelLog("validation-log-stale", "tenant-a", "owner", "物化执行完成", "validation-stale-plan", "Historical plan", "Other detail");
        fixture.modelLog("validation-log-itemset", "tenant-a", "validation-fallback-actor", "生成模型数据项集", "validation-deleted-plan", "Itemset", "x".repeat(4200) + "valid marker");
        fixture.modelLog("validation-log-archived", "tenant-a", "validation-foreign-actor", "维护逻辑模型：archive", "model-001", "Archived", "Archive detail");
        fixture.modelLog("validation-log-direct", "tenant-a", "owner", "创建逻辑模型", "model-000", "Direct", "Direct detail");
        fixture.modelLog("validation-log-unlinked", "tenant-a", "owner", "创建逻辑模型", "model-outside", null, "Missing detail");
        fixture.modelLog("validation-log-unsupported", "tenant-a", "owner", "历史未知事件", "model-000", "Unsupported", "Unsupported detail");
        fixture.modelLog("validation-log-foreign", "tenant-b", "owner", "创建逻辑模型", "model-000", "Foreign", "Foreign detail");

        var input = Map.<String, Object>of("table", "modelLogs", "size", 20, "sortField", "time", "sortDir", "desc");
        for (String scopeKind : List.of("ALL", "ORG", "OWNER")) {
            fixture.counter.reset();
            var result = StructuredValues.objectMap(fixture.commandValue(input, new Scope(scopeKind, List.of("org-a"))));
            assertEquals(Set.of("list", "total", "page", "size"), result.keySet());
            assertEquals(6, ((Number) result.get("total")).intValue());
            var rows = StructuredValues.objectRows(result.get("list"));
            assertEquals(List.of("validation-log-archived", "validation-log-direct", "validation-log-itemset",
                    "validation-log-plan", "validation-log-stale", "validation-log-unlinked"),
                    rows.stream().map(row -> row.get("id")).toList());
            var archived = rows.getFirst();
            assertEquals(Set.of("id", "actorId", "actorName", "action", "objectId", "name", "detail", "time", "modelId",
                    "modelName", "modelCode", "modelArchived", "stage", "event"), archived.keySet());
            assertEquals("model-001", archived.get("modelId"));
            assertEquals(true, archived.get("modelArchived"));
            assertEquals("版本", archived.get("stage"));
            assertNull(archived.get("actorName"));
            assertEquals("model-000", rows.get(1).get("modelId"));
            assertEquals("model-001", rows.get(2).get("modelId"));
            assertEquals("validation-account", rows.get(2).get("actorName"));
            assertEquals("model-000", rows.get(3).get("modelId"));
            assertEquals("Authorized actor", rows.get(3).get("actorName"));
            assertNull(rows.get(4).get("modelId"));
            assertEquals("Historical plan", rows.get(4).get("modelName"));
            assertEquals("关联模型已不存在", rows.get(5).get("modelName"));
            assertEquals(3, fixture.counter.count);
            assertEquals(0, fixture.counter.writes);
        }

        assertModelLogIds(fixture, Map.of("stage", "物化"), 3, "validation-log-plan", "validation-log-stale");
        assertModelLogIds(fixture, Map.of("modelId", "model-000"), 4, "validation-log-direct", "validation-log-plan");
        assertModelLogIds(fixture, Map.of("keyword", "valid%marker"), 3, "validation-log-itemset", "validation-log-plan");
        assertModelLogIds(fixture, Map.of("keyword", "' OR 1=1 --"), 1);
        assertModelLogIds(fixture, Map.of("page", 2), 1);
        for (String id : List.of("model-outside", "model-002", "validation-missing-model")) {
            fixture.counter.reset();
            var denied = fixture.commandValue(Map.of("table", "modelLogs", "modelId", id), new Scope());
            assertEquals("404", String.valueOf(assertInstanceOf(ExitValue.class, denied).getValues()[0]));
            assertEquals(1, fixture.counter.count);
        }
    }

    private static void assertModelLogIds(Fixture fixture, Map<String, Object> filters, int reads, String... expected) throws Exception {
        var input = new HashMap<String, Object>(filters);
        input.put("table", "modelLogs");
        fixture.counter.reset();
        var result = fixture.command(input);
        assertEquals(List.of(expected), StructuredValues.objectRows(result.get("list")).stream().map(row -> row.get("id")).toList());
        assertEquals(reads, fixture.counter.count);
        assertEquals(0, fixture.counter.writes);
    }
    @Test void boundedModelConfigurationValidatesEveryRequestedIdBeforeOrdering() throws Exception {
        var f=new Fixture();f.counter.count=0;var models=f.models();assertEquals(5,f.counter.count);
        var ids=(StructuredValues.objectRows(models.get("models"))).stream().map(row->row.get("id")).toList();
        f.counter.count=0;var small=f.commandWithModels(Map.of("table","models","ids",ids,"sortField","entityCount","sortDir","desc","size",20),models);
        var large=f.commandWithModels(Map.of("table","models","ids",ids,"sortField","entityCount","sortDir","desc","size",100),models);
        assertEquals(0,f.counter.count);assertEquals(small.get("keys"),((List<?>)large.get("keys")).subList(0,20));
        assertEquals("model-119",((List<?>)large.get("keys")).getFirst());
        assertThrows(Exception.class,()->f.commandWithModels(Map.of("table","models","ids",List.of("model-001","model-outside")),models));
        assertThrows(Exception.class,()->f.commandWithModels(Map.of("table","models","ids",List.of("model-001","model-001")),models));
    }

    @ParameterizedTest
    @EnumSource(DatabaseMode.class)
    void modelProjectionReadsEachConfigurationInBulk(DatabaseMode mode) throws Exception {
        var fixture = new Fixture(mode, 0);
        int previousSize = 0;
        for (int size : List.of(20, 100)) {
            for (int index = previousSize; index < size; index++) {
                var model = fixture.addModel(index);
                fixture.database.update("INSERT INTO res_logical_model_revision VALUES(?,'tenant-a',?,1,'{}','validation','digest',?)",
                        model + "-revision", model, VALIDATION_TIME);
                fixture.database.update("INSERT INTO res_materialization_plan VALUES(?,'tenant-a',?,'{}','READY',1,?,?,0)",
                        model + "-plan", model, VALIDATION_TIME, VALIDATION_TIME);
                fixture.database.update("INSERT INTO res_model_itemset VALUES(?,'tenant-a',?,'Validation','[]','ACTIVE',1,?,?,0)",
                        model + "-items", model, VALIDATION_TIME, VALIDATION_TIME);
            }
            fixture.counter.reset();
            var data = fixture.models();
            assertEquals(8, fixture.counter.count);
            assertEquals(0, fixture.counter.writes);
            var models = StructuredValues.objectRows(data.get("models"));
            assertEquals(size, models.size());
            assertTrue(models.stream().allMatch(row -> StructuredValues.objectRows(row.get("frozen")).size() == 1));
            assertEquals(size, StructuredValues.objectRows(data.get("materializations")).size());
            assertEquals(size, StructuredValues.objectRows(data.get("itemSets")).size());
            assertEquals(List.of(), data.get("resources"));
            previousSize = size;
        }
    }
    @ParameterizedTest
    @EnumSource(DatabaseMode.class)
    void childItemsSortBeforePagingAndRejectUnauthorizedParents(DatabaseMode mode) throws Exception {
        var f=new Fixture(mode);
        String originalSource=CanonicalMagicSources.byId(CATALOG_ITEMS);
        final String source=originalSource.replaceAll("(?m)^import (tenantRuntime|dataScope|tableRead|tableSort)[^\\r\\n]*\\r?\\n","");
        var database=f.database;
        database.execute("ALTER TABLE da_catalog_t ADD field_governance_config VARCHAR");
        database.execute("ALTER TABLE da_catalog_item_t ADD col_length INT");
        database.execute("ALTER TABLE da_catalog_item_t ADD created_time TIMESTAMP");database.execute("ALTER TABLE da_catalog_item_t ADD updated_time TIMESTAMP");
        for(String name:List.of("col_en","col_type","col_comment","is_pk","is_masking","is_fk","is_nullable","date_format","mark_lvl","col_unit","col_precision","masking_id","share_type","share_condition","is_dict","dict_name","col_max_length","default_value","data_standard_id","quality_rule","enable_code_table","code_table_id","target_table_column_id"))database.execute("ALTER TABLE da_catalog_item_t ADD "+name+" VARCHAR");
        database.update("UPDATE da_catalog_item_t SET col_length=sort_no,col_name=CONCAT('Field ',LPAD(sort_no,3,'0'))");
        for(String field:List.of("colName","colLength")) {
            f.counter.count=0;var small=f.execute(source,Map.of("body",Map.of("catalogId","cat-119","pageNo",1,"pageSize",20,"sortField",field,"sortDir","desc"),"dataScope",new Scope()));assertEquals(3,f.counter.count);
            f.counter.count=0;var large=f.execute(source,Map.of("body",Map.of("catalogId","cat-119","pageNo",1,"pageSize",100,"sortField",field,"sortDir","desc"),"dataScope",new Scope()));assertEquals(3,f.counter.count);
            var rows=StructuredValues.objectRows(large.get("list"));assertEquals(120,((Number)large.get("total")).intValue());assertEquals(small.get("list"),rows.subList(0,20));assertEquals(119,((Number)rows.getFirst().get("colLength")).intValue());
        }
        f.counter.reset();
        var hidden = f.childItems(source, Map.of("catalogId", "hidden-119", "pageNo", 1), new Scope());
        assertEquals("404", String.valueOf(assertInstanceOf(ExitValue.class, hidden).getValues()[0]));
        assertEquals(1, f.counter.count);

        var fallbackConfig = """
                {"fields":[{"columnName":"b","columnComment":"Fallback B","length":12,"tid":"source-b"},
                {"columnName":"a","columnComment":"Fallback A","length":15,"tid":"source-a"}]}
                """;
        database.update("UPDATE da_catalog_t SET field_governance_config=? WHERE tid='cat-119'", fallbackConfig);
        f.counter.reset();
        var outOfRange = StructuredValues.objectMap(f.childItems(source,
                Map.of("catalogId", "cat-119", "pageNo", 3, "pageSize", 100), new Scope()));
        assertEquals(120, ((Number) outOfRange.get("total")).intValue());
        assertEquals(List.of(), outOfRange.get("list"));
        assertEquals(2, f.counter.count);

        database.update("UPDATE da_catalog_item_t SET col_length=NULL WHERE tid='119-119'");
        database.update("INSERT INTO da_catalog_item_t(tid,tenant_id,catalog_id,col_name,col_length,is_del) "
                + "VALUES('validation-foreign-item','tenant-b','cat-119','Foreign',9999,0),"
                + "('validation-deleted-item','tenant-a','cat-119','Deleted',9999,1)");
        f.counter.reset();
        var flat = StructuredValues.objectRows(f.childItems(source,
                Map.of("catalogId", "cat-119", "sortField", "colLength", "sortDir", "desc"), new Scope()));
        assertEquals(120, flat.size());
        assertEquals(118, ((Number) flat.getFirst().get("colLength")).intValue());
        assertEquals("119-119", flat.getLast().get("tid"));
        assertEquals(3, f.counter.count);

        f.catalogRow("validation-unbound-catalog", "tenant-a", "", 0);
        database.update("UPDATE da_catalog_t SET field_governance_config=? WHERE tid='validation-unbound-catalog'", fallbackConfig);
        f.counter.reset();
        var fallback = StructuredValues.objectMap(f.childItems(source,
                Map.of("tid", "validation-unbound-catalog", "page", 2, "size", 1, "sortField", "colEn", "sortDir", "asc"),
                new Scope("ORG", List.of("org-a"))));
        assertEquals(2, ((Number) fallback.get("total")).intValue());
        var fallbackRows = StructuredValues.objectRows(fallback.get("list"));
        assertEquals(1, fallbackRows.size());
        assertEquals("source-b", fallbackRows.getFirst().get("sourceTableColumnId"));
        assertEquals(2, f.counter.count);
        f.counter.reset();
        var forbiddenFallback = f.childItems(source, Map.of("id", "validation-unbound-catalog"),
                new Scope("ORG", List.of("org-b")));
        assertEquals("404", String.valueOf(assertInstanceOf(ExitValue.class, forbiddenFallback).getValues()[0]));
        assertEquals(1, f.counter.count);

        database.update("""
                INSERT INTO da_catalog_item_t(tid,tenant_id,catalog_id,col_name,sort_no,is_del)
                SELECT CONCAT('validation-item-',seq_no),'tenant-a','cat-119','Validation field',seq_no,0
                FROM SYSTEM_RANGE(1,881) AS seq(seq_no)
                """);
        f.counter.reset();
        var aboveLegacyCap = f.childItems(source, Map.of("catalogId", "cat-119"), new Scope());
        assertEquals("400", String.valueOf(assertInstanceOf(ExitValue.class, aboveLegacyCap).getValues()[0]));
        assertEquals(3, f.counter.count);
        System.out.printf("Catalog items %s: 20/100 rows each 3 SQL; flat 3, empty page 2, denied parent 1%n", mode);
    }
    @ParameterizedTest
    @EnumSource(DatabaseMode.class)
    void warehouseTableSearchUsesLayerNamesAndTwoReadsBeforePagination(DatabaseMode mode) throws Exception {
        var fixture = new Fixture(mode);
        fixture.prepareWarehouseTableSearch();
        String dwd = warehouseListSource(DWD_LIST);
        String dws = warehouseListSource(DWS_LIST);
        String dwdField = "catalogOrTableName";
        String dwsField = "catalogName";

        fixture.warehouseTable("dwd-directory", "tenant-a", "permitted", "dwd",
                "input_physical", "输入表中文名", "基础目录", null, 4, 0);
        fixture.database.update("UPDATE db_table_t SET table_name_en=? WHERE tid=?", "input_english", "dwd-directory");
        fixture.warehouseTable("dws-directory", "tenant-a", "permitted", "dws",
                "service_physical", "服务表中文名", "专题目录", null, 4, 0);
        fixture.warehouseTable("dws-cn-fallback", "tenant-a", "permitted", "dws",
                "fallback_cn", "中文目录回退", "  ", null, 4, 0);
        fixture.warehouseTable("dws-comment-fallback", "tenant-a", "permitted", "dws",
                "fallback_comment", "  ", null, "注释目录回退", 4, 0);
        String longComment = "a".repeat(64_001) + "超长注释尾部";
        fixture.warehouseTable("dws-long-comment", "tenant-a", "permitted", "dws",
                "fallback_long_comment", null, null, longComment, 4, 0);
        fixture.warehouseTable("dws-hidden-comment", "tenant-a", "permitted", "dws",
                "hidden_comment", null, "优先目录名", longComment, 4, 0);
        fixture.warehouseTable("dwd-canonical", "tenant-a", "permitted", "dwd",
                "duplicate_name", "保留中文名", "保留目录", null, 4, 0);
        fixture.warehouseTable("dwd-older-duplicate", "tenant-a", "permitted", "dwd",
                "DUPLICATE_NAME", "旧中文名", "旧目录独有关键词", null, 2, 0);

        assertEquals(List.of("dwd-directory"), warehouseIds(fixture.warehouseSearch(dwd, dwdField, "基础目录", 1, 20)));
        assertEquals(List.of("dwd-directory"), warehouseIds(fixture.warehouseSearch(dwd, dwdField, "input_physical", 1, 20)));
        assertEquals(List.of("dwd-directory"), warehouseIds(fixture.warehouseSearch(dwd, dwdField, "输入表中文名", 1, 20)));
        assertEquals(List.of("dwd-directory"), warehouseIds(fixture.warehouseSearch(dwd, dwdField, "input_english", 1, 20)));
        assertEquals(List.of(), warehouseIds(fixture.warehouseSearch(dwd, dwdField, "旧目录独有关键词", 1, 20)));
        assertEquals(List.of("dws-directory"), warehouseIds(fixture.warehouseSearch(dws, dwsField, "专题目录", 1, 20)));
        assertEquals(List.of(), warehouseIds(fixture.warehouseSearch(dws, dwsField, "service_physical", 1, 20)));
        assertEquals(List.of(), warehouseIds(fixture.warehouseSearch(dws, dwsField, "服务表中文名", 1, 20)));
        var chineseFallback = fixture.warehouseSearch(dws, dwsField, "中文目录回退", 1, 20);
        assertEquals(List.of("dws-cn-fallback"), warehouseIds(chineseFallback));
        assertEquals("中文目录回退", StructuredValues.objectRows(chineseFallback.get("list")).getFirst().get("catalogName"));
        var commentFallback = fixture.warehouseSearch(dws, dwsField, "注释目录回退", 1, 20);
        assertEquals(List.of("dws-comment-fallback"), warehouseIds(commentFallback));
        assertEquals("注释目录回退", StructuredValues.objectRows(commentFallback.get("list")).getFirst().get("catalogName"));
        var longFallback = fixture.warehouseSearch(dws, dwsField, "超长注释尾部", 1, 20);
        assertEquals(List.of("dws-long-comment"), warehouseIds(longFallback));
        assertEquals(longComment, StructuredValues.objectRows(longFallback.get("list")).getFirst().get("catalogName"));

        String literal = "%_!' OR 1=1 --";
        for (String layer : List.of("dwd", "dws")) {
            fixture.warehouseTable(layer + "-literal", "tenant-a", "permitted", layer,
                    layer + "_literal", null, "前缀" + literal + "后缀", null, 4, 0);
            fixture.warehouseTable(layer + "-wildcard-decoy", "tenant-a", "permitted", layer,
                    layer + "_wildcard_decoy", null, "前缀xy!' OR 1=1 --后缀", null, 4, 0);
            String source = layer.equals("dwd") ? dwd : dws;
            String field = layer.equals("dwd") ? dwdField : dwsField;
            assertEquals(List.of(layer + "-literal"), warehouseIds(fixture.warehouseSearch(source, field, literal, 1, 20)));

            for (int index = 0; index < 110; index++) {
                String suffix = String.format("%03d", index);
                fixture.warehouseTable(layer + "-batch-" + suffix, "tenant-a", "permitted", layer,
                        layer + "_batch_" + suffix, null, "批量目录 " + suffix, null, 4, 0);
            }
            fixture.warehouseTable(layer + "-foreign", "tenant-b", "permitted", layer,
                    layer + "_foreign", null, "批量目录 999", null, 4, 0);
            fixture.warehouseTable(layer + "-foreign-source", "tenant-a", "foreign-only", layer,
                    layer + "_foreign_source", null, "批量目录 999", null, 4, 0);
            fixture.warehouseTable(layer + "-deleted-source", "tenant-a", "deleted-source", layer,
                    layer + "_deleted_source", null, "批量目录 999", null, 4, 0);
            fixture.warehouseTable(layer + "-deleted-table", "tenant-a", "permitted", layer,
                    layer + "_deleted_table", null, "批量目录 999", null, 4, 1);

            for (int size : List.of(20, 100)) {
                fixture.counter.reset();
                var first = fixture.warehouseSearch(source, field, "批量目录", 1, size);
                assertEquals(2, fixture.counter.count);
                assertEquals(0, fixture.counter.writes);
                assertEquals(1, ((Number) first.get("pageNum")).intValue());
                assertEquals(size, ((Number) first.get("pageSize")).intValue());
                assertEquals(110, ((Number) first.get("total")).intValue());
                assertEquals(size, warehouseIds(first).size());
                assertEquals(layer + "-batch-109", warehouseIds(first).getFirst());
                fixture.counter.reset();
                var second = fixture.warehouseSearch(source, field, "批量目录", 2, size);
                assertEquals(2, fixture.counter.count);
                assertEquals(0, fixture.counter.writes);
                assertEquals(2, ((Number) second.get("pageNum")).intValue());
                assertEquals(110, ((Number) second.get("total")).intValue());
                assertEquals(Math.min(size, 110 - size), warehouseIds(second).size());
                assertEquals(layer + "-batch-" + String.format("%03d", 109 - size), warehouseIds(second).getFirst());
            }
        }
        String publicPage = CanonicalMagicSources.byId(TABLE_PAGE)
                .replaceAll("(?m)^import (tenantRuntime|tableSort)[^\\r\\n]*\\r?\\n", "");
        for (var invalid : List.of(
                Map.of("field", "catalogName) OR 1=1 --", "type", "like", "value", literal),
                Map.of("field", dwdField, "type", "eq", "value", literal),
                Map.of("field", dwsField, "type", "unrecognized", "value", literal))) {
            fixture.counter.reset();
            var response = assertInstanceOf(ExitValue.class,
                    fixture.executeValue(publicPage, Map.of("body", Map.of("conditions", List.of(invalid)))));
            assertEquals(400, ((Number) response.getValues()[0]).intValue());
            assertEquals(0, fixture.counter.count);
        }
        System.out.printf("DWD/DWS catalog search %s: 20/100 rows each 2 SQL, literal search and tenant/deletion checks passed%n", mode);
    }

    private static List<String> warehouseIds(Map<String, Object> result) {
        return StructuredValues.objectRows(result.get("list")).stream().map(row -> row.get("tid").toString()).toList();
    }

    private static String warehouseListSource(String endpointId) throws Exception {
        // Replace Magic HTTP imports with local functions to execute all three canonical scripts in one request context.
        String imports = "(?m)^import [^\\r\\n]*\\r?\\n";
        return """
                import java.util.List
                import java.util.Map
                var tablePage = () => {
                %s
                };
                var layerPage = () => {
                %s
                };
                %s
                """.formatted(CanonicalMagicSources.byId(TABLE_PAGE).replaceAll(imports, ""),
                CanonicalMagicSources.byId(LAYER_PAGE).replaceAll(imports, ""),
                CanonicalMagicSources.byId(endpointId).replaceAll(imports, ""));
    }

    @Test void dictionarySiblingOrderUsesSqlAndTwoReadsAtTwentyAndOneHundredItems() throws Exception {
        var f=new Fixture();
        f.database.execute("CREATE TABLE sym_dict_t(tid VARCHAR,tenant_id VARCHAR,dict_code VARCHAR,dict_name VARCHAR,tree_path VARCHAR,parent_id VARCHAR,sort_no INT,level_no INT,is_del INT,created_time TIMESTAMP,updated_time TIMESTAMP)");
        f.database.update("INSERT INTO sym_dict_t VALUES('root','tenant-a','type','类型','root','0',0,2,0,NULL,NULL),('foreign-root','tenant-b','type','秘密','foreign','0',0,2,0,NULL,NULL)");
        String source=CanonicalMagicSources.under("api").stream().filter(s->s.relativePath().endsWith("02.字典树查询.ms")).findFirst().orElseThrow().source().replaceAll("(?m)^import (tenantRuntime|tableSort)[^\\r\\n]*\\r?\\n","");
        for(int size:List.of(20,100)){
            for(int i=size==20?0:20;i<size;i++)f.database.update("INSERT INTO sym_dict_t VALUES(?,'tenant-a',?,?,'root.child','root',?,3,0,NULL,NULL)","dict-"+i,"code-"+i,String.format("Name %03d",i),100-i);
            f.counter.count=0;
            var context=new HashMap<String,Object>();context.put("db",f.sql);context.put("tenantRuntime",new Tenant());context.put("tableSort",new TableSortMagicModule());context.put("query",Map.of());context.put("body",Map.of("dictCode","type","showRow",true,"sortField","dictName","sortDir","asc"));
            var rows=StructuredValues.objectRows(MagicScript.create(source,null).execute(new MagicScriptContext(context)));
            assertEquals(2,f.counter.count);assertEquals(size,rows.size());assertEquals("Name 000",rows.getFirst().get("dictName"));assertEquals("Name "+String.format("%03d",size-1),rows.getLast().get("dictName"));
            assertTrue(rows.stream().allMatch(r->r.get("tid").toString().startsWith("dict-")));
        }
    }

    private static class Fixture {
        final Counter counter=new Counter();final SQLModule sql;final JdbcTemplate database;
        final StructuredTableReadMagicModule tableRead;
        Fixture() {
            this(DatabaseMode.MYSQL);
        }
        Fixture(DatabaseMode mode) {
            this(mode, 120);
        }
        Fixture(DatabaseMode mode, int catalogSize) {
            var ds=new CountedSource(mode, counter);
            var sources=new MagicDynamicDataSource();sources.setDefault(ds);sql=new SQLModule(sources);sql.setDataSourceNode(sources.getDataSource());
            sql.setDialectAdapter(new DialectAdapter());sql.setResultProvider(new DefaultResultProvider("resource-table-test"));
            var mapper=new ColumnMapperAdapter();mapper.add(new DefaultColumnMapperProvider());mapper.add(new CamelColumnMapperProvider());
            mapper.add(new LowerColumnMapperProvider());mapper.setDefault("camel");
            sql.setColumnMapperProvider(mapper);sql.setSqlInterceptors(List.of());sql.setNamedTableInterceptors(List.of());sql.setColumnMapRowMapper(mapper.getDefaultColumnMapRowMapper());sql.setRowMapColumnMapper(v->v);
            var db=new JdbcTemplate(ds);database=db;
            tableRead = new StructuredTableReadMagicModule(db);
            db.execute("CREATE TABLE db_datasource_t(tid VARCHAR,tenant_id VARCHAR,created_by VARCHAR,org_id VARCHAR,is_del INT)");
            db.execute("CREATE TABLE db_table_t(tid VARCHAR,tenant_id VARCHAR,datasource_id VARCHAR,is_del INT)");
            db.execute("CREATE TABLE dwm_center_layer_source_t(tid VARCHAR,tenant_id VARCHAR,datasource_id VARCHAR,target_type VARCHAR,is_del INT)");
            db.execute("CREATE TABLE da_catalog_t(tid VARCHAR,tenant_id VARCHAR,catalog_name VARCHAR,catalog_name_en VARCHAR,asset_desc VARCHAR,org_id VARCHAR,source_table_id VARCHAR,source_table_name VARCHAR,db_id VARCHAR,asset_status INT,flow_status INT,resource_revision_no INT,created_by VARCHAR,updated_by VARCHAR,created_time TIMESTAMP,updated_time TIMESTAMP,is_del INT)");
            db.execute("CREATE TABLE da_catalog_item_t(tid VARCHAR,tenant_id VARCHAR,catalog_id VARCHAR,source_table_column_id VARCHAR,col_name VARCHAR,sort_no INT,is_del INT)");
            db.execute("CREATE TABLE res_resource_operation_log(tid VARCHAR,tenant_id VARCHAR,actor_id VARCHAR,action_name VARCHAR,object_id VARCHAR,object_name VARCHAR,detail_text VARCHAR,created_time TIMESTAMP)");
            db.execute("CREATE TABLE res_logical_model(tid VARCHAR,tenant_id VARCHAR,model_name VARCHAR,model_code VARCHAR,model_status VARCHAR,design_json VARCHAR,revision_no INT,model_type VARCHAR,database_id VARCHAR,domain_name VARCHAR,owner_name VARCHAR,model_description VARCHAR,created_time TIMESTAMP,updated_time TIMESTAMP,is_del INT)");
            db.execute("CREATE TABLE res_logical_model_revision(tid VARCHAR,tenant_id VARCHAR,model_id VARCHAR,revision_number INT,content_json VARCHAR,note VARCHAR,content_hash VARCHAR,created_time TIMESTAMP)");
            db.execute("CREATE TABLE res_materialization_plan(tid VARCHAR,tenant_id VARCHAR,model_id VARCHAR,plan_json VARCHAR,plan_status VARCHAR,revision_no INT,created_time TIMESTAMP,updated_time TIMESTAMP,is_del INT)");
            db.execute("CREATE TABLE res_model_itemset(tid VARCHAR,tenant_id VARCHAR,model_id VARCHAR,itemset_name VARCHAR,items_json VARCHAR,itemset_status VARCHAR,revision_no INT,created_time TIMESTAMP,updated_time TIMESTAMP,is_del INT)");
            db.execute("CREATE TABLE rm_user_t(id VARCHAR,tenant_id VARCHAR,real_name VARCHAR,user_name VARCHAR)");
            db.update("INSERT INTO rm_user_t VALUES('owner','tenant-a','Authorized actor','owner'),('owner','tenant-b','Wrong tenant actor','owner')");
            for(String source:List.of("permitted","forbidden")) {
                db.update("INSERT INTO db_datasource_t VALUES(?,'tenant-a',?,'org-a',0)",source,source.equals("permitted")?"owner":"another-user");
                db.update("INSERT INTO db_table_t VALUES(?,'tenant-a',?,0)",source,source);
                db.update("INSERT INTO dwm_center_layer_source_t VALUES(?,'tenant-a',?,'domain',0)",source,source);
            }
            for(int i=0;i<catalogSize;i++) {
                String id=String.format("%03d",i),name="Name "+String.format("%03d",119-i);
                db.update("INSERT INTO da_catalog_t VALUES(?,'tenant-a',?,'CODE','','org-a','permitted','Table','permitted',2,2,1,'owner','owner',CURRENT_TIMESTAMP,DATEADD('SECOND',?,CURRENT_TIMESTAMP),0)","cat-"+id,name,i);
                db.update("INSERT INTO da_catalog_t VALUES(?,'tenant-a','Outside scope','CODE','','org-a','forbidden','Table','forbidden',2,2,1,'owner','owner',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)","hidden-"+id);
                for(int n=0;n<=i;n++)db.update("INSERT INTO da_catalog_item_t VALUES(?,'tenant-a',?,?,'Field',?,0)",id+"-"+n,"cat-"+id,"f"+n,n);
                addModel(i);
                db.update("INSERT INTO res_resource_operation_log VALUES(?,'tenant-a','owner','创建逻辑模型',?,'Model','Created',DATEADD('SECOND',?,CURRENT_TIMESTAMP))","log-"+id,"model-"+id,i);
                db.update("INSERT INTO res_resource_operation_log VALUES(?,'tenant-b','owner','创建逻辑模型','model-outside','Wrong tenant','Outside',CURRENT_TIMESTAMP)","outside-"+id);
            }
            db.update("INSERT INTO res_logical_model VALUES('model-outside','tenant-b','Outside','MODEL','ACTIVE','{}',1,'MANUAL','','','','',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)");
        }
        String addModel(int index) {
            String id = String.format("model-%03d", index);
            String name = String.format("Name %03d", 119 - index);
            String design = "{\"entities\":[" + String.join(",", Collections.nCopies(index, "{}")) + "]}";
            database.update("INSERT INTO res_logical_model VALUES(?,'tenant-a',?,'MODEL','ACTIVE',?,1,'MANUAL','','','','',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",
                    id, name, design);
            return id;
        }
        void modelPlan(String id, String tenant, String model, int deleted) {
            database.update("INSERT INTO res_materialization_plan VALUES(?,?,?,'{}','READY',1,?,?,?)",
                    id, tenant, model, VALIDATION_TIME, VALIDATION_TIME, deleted);
        }
        void modelItemSet(String id, String tenant, String model, int deleted) {
            database.update("INSERT INTO res_model_itemset VALUES(?,?,?,'Validation','[]','ACTIVE',1,?,?,?)",
                    id, tenant, model, VALIDATION_TIME, VALIDATION_TIME, deleted);
        }
        void modelLog(String id, String tenant, String actor, String action, String object, String name, String detail) {
            database.update("INSERT INTO res_resource_operation_log VALUES(?,?,?,?,?,?,?,?)",
                    id, tenant, actor, action, object, name, detail, VALIDATION_TIME);
        }
        void prepareWarehouseTableSearch() {
            database.execute("ALTER TABLE db_datasource_t ADD db_name VARCHAR");
            database.execute("ALTER TABLE db_datasource_t ADD app_id VARCHAR");
            for (String column : List.of("table_name", "table_name_cn", "table_name_en", "catalog_name",
                    "table_comment", "data_source_type")) {
                database.execute("ALTER TABLE db_table_t ADD " + column + " VARCHAR");
            }
            database.execute("ALTER TABLE db_table_t ADD field_count INT");
            database.execute("ALTER TABLE db_table_t ADD annotated INT");
            database.execute("ALTER TABLE db_table_t ADD updated_time TIMESTAMP");
            datasource("foreign-only", "tenant-b", "owner", "org-a", 0);
            datasource("deleted-source", "tenant-a", "owner", "org-a", 1);
        }
        void warehouseTable(String id, String tenant, String datasource, String layer, String tableName,
                String chineseName, String catalogName, String comment, int fieldCount, int deleted) {
            database.update("""
                    INSERT INTO db_table_t(tid,tenant_id,datasource_id,data_source_type,table_name,table_name_cn,
                        catalog_name,table_comment,field_count,annotated,updated_time,is_del)
                    VALUES(?,?,?,?,?,?,?,?,?,0,CURRENT_TIMESTAMP,?)
                    """, id, tenant, datasource, layer, tableName, chineseName, catalogName, comment, fieldCount, deleted);
        }
        Map<String, Object> warehouseSearch(String source, String field, String keyword, int pageNum, int size)
                throws Exception {
            var conditions = new ArrayList<>(List.of(Map.of("field", field, "type", "like", "value", keyword)));
            return execute(source, Map.of("body", Map.of("pageNum", pageNum, "pageSize", size,
                    "tenantId", "tenant-b", "sortField", "catalogName", "sortDir", "desc",
                    "conditions", conditions)));
        }
        void datasource(String id, String tenant, String owner, String organization, int deleted) {
            database.update("INSERT INTO db_datasource_t(tid,tenant_id,created_by,org_id,is_del) VALUES(?,?,?,?,?)",
                    id, tenant, owner, organization, deleted);
        }
        void binding(String source, String tenant, String targetType, int deleted) {
            database.update("INSERT INTO dwm_center_layer_source_t VALUES(?,?,?,?,?)",
                    source + "-binding", tenant, source, targetType, deleted);
        }
        void table(String id, String tenant, String source, int deleted) {
            database.update("INSERT INTO db_table_t(tid,tenant_id,datasource_id,is_del) VALUES(?,?,?,?)", id, tenant, source, deleted);
        }
        void catalogRow(String id, String tenant, String table, int deleted) {
            database.update("""
                    INSERT INTO da_catalog_t(tid,tenant_id,catalog_name,org_id,source_table_id,db_id,asset_status,
                      flow_status,created_by,updated_by,created_time,updated_time,is_del)
                    VALUES(?,? ,?,'org-a',?,?,2,2,'owner','owner',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)
                    """, id, tenant, id, table, table, deleted);
        }
        void createCompatibilityTables() {
            for (String column : List.of("table_name VARCHAR", "table_name_cn VARCHAR", "table_comment VARCHAR", "updated_time TIMESTAMP")) {
                database.execute("ALTER TABLE db_table_t ADD " + column);
            }
            database.execute("""
                    CREATE TABLE db_table_column_t(tid VARCHAR,tenant_id VARCHAR,table_id VARCHAR,is_del INT,column_name VARCHAR,
                        column_comment VARCHAR,data_type VARCHAR,length INT,scale INT,nullable INT,primary_key INT,ordinal_position INT,is_masking INT)
                    """);
            database.execute("""
                    CREATE TABLE res_catalog_publish_request(tid VARCHAR,tenant_id VARCHAR,catalog_id VARCHAR,request_status VARCHAR,
                        snapshot_json TEXT,revision_number INT,revision_no INT,created_by VARCHAR,created_time TIMESTAMP,
                        reviewer_id VARCHAR,reviewed_time TIMESTAMP,review_comment VARCHAR,published_time TIMESTAMP,offline_reason VARCHAR,
                        sync_status VARCHAR,updated_time TIMESTAMP,is_del INT)
                    """);
            database.execute("""
                    CREATE TABLE data_apply_form_t(tid VARCHAR,tenant_id VARCHAR,apply_user_id VARCHAR,apply_org_id VARCHAR,
                        resource_scope_json TEXT,resource_revision_no INT,created_time TIMESTAMP,updated_time TIMESTAMP,is_del INT)
                    """);
            database.execute("""
                    CREATE TABLE data_distribution_task_t(tid VARCHAR,tenant_id VARCHAR,apply_form_id VARCHAR,resource_result_json TEXT,
                        task_status VARCHAR,status_message VARCHAR,created_time TIMESTAMP,updated_time TIMESTAMP,execution_engine VARCHAR,is_del INT)
                    """);
        }
        void column(String id, String tenant, String table, int ordinal, int deleted) {
            database.update("INSERT INTO db_table_column_t VALUES(?,?,?,?,?,'验证字段','varchar',12,0,1,0,?,0)",
                    id, tenant, table, deleted, id, ordinal);
        }
        void publication(String id, String tenant, String catalog, String status, int revision, int deleted, Map<String, Object> snapshot) {
            database.update("""
                    INSERT INTO res_catalog_publish_request VALUES(?,?,?,?,?,?,?,'owner',?,'reviewer',?,'approved for validation',?,
                        'validation reason',NULL,?,?)
                    """, id, tenant, catalog, status, snapshot == null ? null : JSONUtil.toJsonStr(snapshot), revision, revision + 2,
                    VALIDATION_TIME, VALIDATION_TIME, VALIDATION_TIME, VALIDATION_TIME, deleted);
        }
        void subscription(String id, String tenant, String applicant, int deleted, Map<String, Object> snapshot) {
            database.update("INSERT INTO data_apply_form_t VALUES(?,?,?,'applicant-org',?,4,?,?,?)",
                    id, tenant, applicant, snapshot == null ? null : JSONUtil.toJsonStr(snapshot), VALIDATION_TIME, VALIDATION_TIME, deleted);
        }
        void delivery(String id, String tenant, String form, String engine, int deleted, int timeOffset, Map<String, Object> result) {
            var time = VALIDATION_TIME.plusSeconds(timeOffset);
            database.update("INSERT INTO data_distribution_task_t VALUES(?,?,?,?,'SUCCEEDED','',?,?,?,?)",
                    id, tenant, form, result == null ? null : JSONUtil.toJsonStr(result), time, time, engine, deleted);
        }
        Map<String,Object> catalog(int size,String field) throws Exception {
            return catalog(1, size, Map.of("sortField", field, "sortDir", "desc"), new Scope());
        }
        Map<String, Object> catalogCommand(int size, String field) throws Exception {
            String readSource = catalogReadSource();
            var scope = new Scope();
            int[] dispatches = {0};
            Function<Object[], Object> catalogFunction = args -> {
                dispatches[0]++;
                return executeValue(readSource, Map.of("input", args[0], "dataScope", scope));
            };
            var result = execute(gatewaySource(), Map.of("catalogRead", catalogFunction, "dataScope", scope,
                    "body", Map.of("kind", "table", "method", "GET", "body",
                            Map.of("table", "catalogs", "page", 1, "size", size, "sortField", field, "sortDir", "desc"))));
            assertEquals(1, dispatches[0]);
            return result;
        }
        Map<String, Object> catalog(int page, int size, Map<String, Object> query, Scope scope) throws Exception {
            return StructuredValues.objectMap(catalogRead(Map.of("mode", "page", "page", page, "size", size, "query", query), scope));
        }
        Object catalogRead(Map<String, Object> input, Scope scope) throws Exception {
            return catalogRead(input, scope, "org-a");
        }
        Object catalogRead(Map<String, Object> input, Scope scope, String organization) throws Exception {
            return executeValue(catalogReadSource(), Map.of("input", input, "dataScope", scope, "currentOrganization", organization));
        }
        private String catalogReadSource() throws Exception {
            String source=CanonicalMagicSources.byId(CATALOG_READ);
            source=source.replaceAll("(?m)^import (tenantRuntime|dataScope|tableSort|tableRead|jsons|'@post:[^\\r\\n]+)[^\\r\\n]*\\r?\\n","")
                    .replace("StpUtil.getLoginIdAsString()", "'owner'");
            return "var currentUser=()=>({orgId:currentOrganization,name:'Authorized actor'});\n" + source;
        }
        Map<String, Object> resourceState(Scope scope) throws Exception {
            String catalogSource = catalogReadSource();
            String source = CanonicalMagicSources.byId(RESOURCE_STATE)
                    .replaceAll("(?m)^import (tenantRuntime|'@/[^\\r\\n]+)[^\\r\\n]*\\r?\\n", "");
            int[] catalogCalls = {0};
            Function<Object[], Object> catalogRead = args -> {
                catalogCalls[0]++;
                return executeValue(catalogSource, Map.of("input", args[0], "dataScope", scope));
            };
            String dependencies = """
                    var technicalRead = input => ({entities:[{id:'validation-entity'}],pageNo:input.pageNo,
                        pageSize:input.pageSize,entityKeyword:input.entityKeyword});
                    var planningRead = input => ({layers:[{id:'validation-layer'}]});
                    var modelRead = input => ({models:input.entities});
                    """;
            var query = Map.of("pageNo", 2, "pageSize", 50, "keyword", "wrapped-keyword");
            var legacy = Map.of("pageNo", 1, "pageSize", 100, "keyword", "legacy-keyword");
            // Magic RequestHandler injects unwrapped GET parameters as top-level variables.
            var result = execute(dependencies + source, Map.of("catalogRead", catalogRead, "query", query, "param", legacy,
                    "pageNo", 3, "pageSize", 20, "keyword", "validation-keyword"));
            assertEquals(1, catalogCalls[0]);
            return result;
        }
        Object childItems(String source, Map<String, Object> body, Scope scope) {
            return executeValue(source, Map.of("body", body, "dataScope", scope, "jsons", new JsonModule()));
        }
        Map<String,Object> models() throws Exception {
            String source=CanonicalMagicSources.under("function").stream().filter(s->s.relativePath().endsWith("05.model-read.ms")).findFirst().orElseThrow().source();
            source=source.replaceAll("(?m)^import (tenantRuntime|jsons|'@/[^\\r\\n]+)[^\\r\\n]*\\r?\\n","");
            return execute(source,Map.of("input",Map.of("projectionOnly",true)));
        }
        Map<String,Object> command(Map<String,Object> query) throws Exception{return commandWithModels(query,Map.of());}
        Map<String,Object> commandWithModels(Map<String,Object> query,Map<String,Object> models) throws Exception {
            return execute("var modelRead=(input)=>models;\n"+gatewaySource(),Map.of("body",Map.of("kind","table","method","GET","body",query),"models",models,"dataScope",new Scope()));
        }
        Object commandValue(Map<String, Object> query, Scope scope) throws Exception {
            return executeValue(gatewaySource(), Map.of("body", Map.of("kind", "table", "method", "GET", "body", query), "dataScope", scope));
        }
        private String gatewaySource() throws Exception {
            String source=CanonicalMagicSources.byId(RESOURCE_COMMAND);
            return source.replaceAll("(?m)^import (tenantRuntime|dataScope|tableSort|tableRead|'[^\\r\\n]+)[^\\r\\n]*\\r?\\n", "");
        }
        Map<String,Object> execute(String source,Map<String,Object> extra) throws Exception {
            return StructuredValues.objectMap(executeValue(source, extra));
        }
        Object executeValue(String source, Map<String, Object> extra) {
            var context=new HashMap<String,Object>(extra);context.put("db",sql);context.put("tenantRuntime",new Tenant());context.put("tableSort",new TableSortMagicModule());
            context.put("tableRead", tableRead);
            context.putIfAbsent("jsons", new JsonModule());
            context.putIfAbsent("currentOrganization", "org-a");
            var script = MagicScript.create(source, null);
            return TenantContext.call("tenant-a", () -> script.execute(new MagicScriptContext(context)));
        }
    }
}
