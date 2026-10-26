package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.mapping.core.MappingEngine;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService.Scope;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService.ScopeDecision;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.*;
import com.linewell.dataelement.mapping.core.MappingException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MappingVersionStoreAuthorizationTest {
    private static final String RESOURCE = "MENU:2081000000000000002";

    private static final class CountedJdbc extends JdbcTemplate {
        int reads;
        CountedJdbc() {
            super(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "", ""));
        }
        @Override public <T> T query(PreparedStatementCreator creator, PreparedStatementSetter setter, ResultSetExtractor<T> extractor) {
            reads++;
            return super.query(creator, setter, extractor);
        }
    }

    private static final class Fixture {
        final CountedJdbc jdbc = new CountedJdbc();
        final PipelineRepository repository = mock(PipelineRepository.class);
        final DataScopeAuthorizationService scopes = mock(DataScopeAuthorizationService.class, CALLS_REAL_METHODS);
        final MappingVersionStore store = new MappingVersionStore(jdbc, repository, scopes);

        Fixture() {
            jdbc.execute("""
                CREATE TABLE data_access_agg_task_t(pipeline_id varchar(64), tenant_id varchar(32), is_del int,
                    source_db_id varchar(64), target_db_id varchar(64), source_table_id varchar(64), target_table_id varchar(64));
                CREATE TABLE db_table_t(tid varchar(64), tenant_id varchar(32), datasource_id varchar(64), is_del int);
                CREATE TABLE db_datasource_t(tid varchar(64), tenant_id varchar(32), created_by varchar(64), org_id varchar(64), is_del int);
                CREATE TABLE data_access_mapping_publication_t(tenant_id varchar(32), pipeline_id varchar(64), node_id varchar(64), plan_id varchar(64));
                CREATE TABLE data_access_mapping_plan_t(tid varchar(64),tenant_id varchar(32),pipeline_id varchar(64),plan_hash varchar(64),plan_content clob);
                """);
            scope(Scope.OWNER, "alice", List.of());
        }

        void scope(Scope scope, String user, List<String> organizations) {
            doReturn(new ScopeDecision(RESOURCE, scope, user, organizations, List.of("ordinary-role"), true))
                    .when(scopes).currentScope(RESOURCE);
        }

        void source(String id, String tenant, String owner, String organization) {
            jdbc.update("INSERT INTO db_datasource_t VALUES(?,?,?,?,0)", id, tenant, owner, organization);
        }

        void table(String id, String tenant, String datasource) {
            jdbc.update("INSERT INTO db_table_t VALUES(?,?,?,0)", id, tenant, datasource);
        }

        Pipeline pipeline(List<Pipeline.Node> nodes) {
            Pipeline value = new Pipeline("p", "test", "", 1L, 2L,
                    new Pipeline.Dsl(1, nodes, List.of()), null, PipelineStatus.DRAFT, null, null, null, null, null);
            when(repository.findById("p")).thenReturn(Optional.of(value));
            return value;
        }
    }

    private static Pipeline.Node node(String id, Map<String, Object> config) {
        return new Pipeline.Node(id, "transform.field-mapping", id, "transform", 0, 0, config);
    }

    private static void forbidden(Runnable action) {
        assertEquals("RESOURCE_FORBIDDEN", assertThrows(MappingException.class, action::run).code());
    }

    @Test void twentyAndHundredDistinctResourceIdsUseThreeSqlReadsAndOneRoleResolution() {
        for (int count : List.of(20, 100)) {
            Fixture fixture = new Fixture();
            List<Pipeline.Node> nodes = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                String id = String.valueOf(index);
                fixture.source("source-" + id, "alpha", "alice", "org-a");
                fixture.table("table-" + id, "alpha", "source-" + id);
                nodes.add(node(id, Map.of("registeredTableId", "table-" + id)));
            }
            Pipeline expected = fixture.pipeline(nodes);
            try (var ignored = TenantContext.use("alpha")) {
                fixture.jdbc.reads = 0;
                assertSame(expected, fixture.store.requirePipeline("p"));
                assertEquals(3, fixture.jdbc.reads, "task, tables and datasources are each read once");
                verify(fixture.repository, times(1)).findById("p");
                verifyNoInteractions(fixture.scopes);
            }
        }
    }

    @Test void standaloneSourcesAndMappingsNeedOnlyTenantOwnershipForTwentyAndHundredNodes() {
        for (int count : List.of(20, 100)) {
            Fixture fixture = new Fixture();
            List<Pipeline.Node> nodes = new ArrayList<>();
            for (int i = 0; i < count; i++) nodes.add(new Pipeline.Node("source" + i, "source.oracle", "manual", "source", 0, 0,
                    Map.of("host", "fixture.invalid", "table", "ORDERS", "incrementalColumn", "UPDATED_AT")));
            Pipeline expected = fixture.pipeline(nodes);
            try (var ignored = TenantContext.use("alpha")) {
                fixture.jdbc.reads = 0;
                assertSame(expected, fixture.store.requirePipeline("p"));
                assertEquals(1, fixture.jdbc.reads, "one task association read, no registration or per-node reads");
                verifyNoInteractions(fixture.scopes);
            }
        }
    }

    @Test void legacyStandaloneTaskConnectionAndPhysicalTableAreNotRegistrationIds() {
        Fixture fixture = new Fixture();
        Pipeline expected = fixture.pipeline(List.of(new Pipeline.Node("source", "source.oracle", "manual", "source", 0, 0,
                Map.of("host", "fixture.invalid", "table", "ORDERS")),
                new Pipeline.Node("sink", "sink.jdbc", "manual", "sink", 500, 0,
                        Map.of("jdbcUrl", "jdbc:mysql://fixture.invalid/test", "table", "orders_copy"))));
        fixture.jdbc.update("INSERT INTO data_access_agg_task_t VALUES('p','alpha',0,NULL,'jdbc:mysql://fixture.invalid/test',NULL,'orders_copy')");
        try (var ignored = TenantContext.use("alpha")) {
            assertSame(expected, fixture.store.requirePipeline("p"));
            verifyNoInteractions(fixture.scopes);
            // An unknown ID, unlike a matching inline connection, must not be reclassified as standalone.
            fixture.jdbc.update("UPDATE data_access_agg_task_t SET target_db_id='missing-resource'");
            forbidden(() -> fixture.store.requirePipeline("p"));
        }
    }

    @Test void orphanRegisteredTableCannotBeTreatedAsAnUnregisteredCanvas() {
        Fixture fixture = new Fixture();
        fixture.table("orphan", "alpha", null);
        fixture.pipeline(List.of(node("source", Map.of("registeredTableId", "orphan"))));
        try (var ignored = TenantContext.use("alpha")) {
            forbidden(() -> fixture.store.requirePipeline("p"));
        }
    }

    @Test void registeredSourcesIgnoreOwnerAndOrganizationMenuScopes() {
        Fixture fixture = new Fixture();
        fixture.source("first", "alpha", "alice", "org-a");
        fixture.source("second", "alpha", "bob", "org-a");
        fixture.table("one", "alpha", "first");
        fixture.table("two", "alpha", "second");
        // A declared allowed datasource cannot conceal the actual datasource of another table.
        fixture.pipeline(List.of(node("one", Map.of("registeredDatasourceId", "first", "registeredTableId", "one")),
                node("two", Map.of("registeredDatasourceId", "first", "registeredTableId", "two"))));
        try (var ignored = TenantContext.use("alpha")) {
            assertNotNull(fixture.store.requirePipeline("p"));
            fixture.scope(Scope.ORG, "alice", List.of("org-a"));
            assertNotNull(fixture.store.requirePipeline("p"));
            fixture.scope(Scope.ORG, "alice", List.of("other-org"));
            assertNotNull(fixture.store.requirePipeline("p"));
            fixture.scope(Scope.ALL, "alice", List.of());
            assertNotNull(fixture.store.requirePipeline("p"));
        }
    }

    @Test void mixedForeignAndMissingIdsRejectWholeRequestEvenForAllScope() {
        Fixture fixture = new Fixture();
        fixture.scope(Scope.ALL, "alice", List.of());
        fixture.source("first", "alpha", "alice", "org-a");
        fixture.source("foreign", "beta", "alice", "org-a");
        fixture.table("one", "alpha", "first");
        fixture.table("foreign-table", "beta", "foreign");
        try (var ignored = TenantContext.use("alpha")) {
            for (String table : List.of("foreign-table", "missing-table")) {
                fixture.pipeline(List.of(node("one", Map.of("registeredTableId", "one")),
                        node("other", Map.of("registeredTableId", table))));
                forbidden(() -> fixture.store.requirePipeline("p"));
            }
            fixture.pipeline(List.of(node("one", Map.of("registeredDatasourceId", "first")),
                    node("other", Map.of("registeredDatasourceId", "foreign"))));
            forbidden(() -> fixture.store.requirePipeline("p"));
            fixture.table("orphan", "alpha", "foreign");
            fixture.pipeline(List.of(node("one", Map.of("registeredTableId", "orphan"))));
            forbidden(() -> fixture.store.requirePipeline("p"));
        }
    }

    @Test void canonicalTaskAndSerializedMappingReferencesCannotBeRemovedToBypassScope() {
        Fixture fixture = new Fixture();
        fixture.source("first", "alpha", "alice", "org-a");
        fixture.source("hidden", "foreign", "bob", "org-b");
        fixture.jdbc.update("INSERT INTO data_access_agg_task_t VALUES('p','alpha',0,'hidden',NULL,NULL,NULL)");
        fixture.pipeline(List.of(node("one", Map.of("registeredDatasourceId", "first"))));
        try (var ignored = TenantContext.use("alpha")) {
            forbidden(() -> fixture.store.requirePipeline("p"));
            fixture.jdbc.update("DELETE FROM data_access_agg_task_t");
            fixture.pipeline(List.of(node("one", Map.of("registeredDatasourceId", "first", "mappings", """
                {"schemaVersion":"2.0","dictionaryBindings":{"value":{"datasourceId":"hidden"}}}
                """))));
            forbidden(() -> fixture.store.requirePipeline("p"));
        }
    }

    @Test void revokedOrDeletedDatasourceBlocksHistoryButDeletedTableCanStillAuthorizeSavedDesign() {
        Fixture fixture = new Fixture();
        fixture.source("first", "alpha", "alice", "org-a");
        fixture.table("one", "alpha", "first");
        fixture.pipeline(List.of(node("one", Map.of("registeredTableId", "one"))));
        try (var ignored = TenantContext.use("alpha")) {
            fixture.jdbc.update("UPDATE db_table_t SET is_del=1");
            assertNotNull(fixture.store.requirePipeline("p"));
            fixture.jdbc.update("UPDATE db_datasource_t SET created_by='bob'");
            assertNotNull(fixture.store.requirePipeline("p"));
            fixture.scope(Scope.ALL, "alice", List.of());
            fixture.jdbc.update("UPDATE db_datasource_t SET is_del=1");
            forbidden(() -> fixture.store.requirePipeline("p"));
        }
    }

    @Test void businessLiteralPropertyNamesAreNotInterpretedAsRegisteredResourceIds() {
        Fixture fixture = new Fixture();
        fixture.source("first", "alpha", "alice", "org-a");
        Pipeline pipeline = fixture.pipeline(List.of(node("one", Map.of("registeredDatasourceId", "first", "mappings", """
            {"schemaVersion":"2.0","rules":[{"input":{"kind":"LITERAL","value":{"tableId":"business-id","datasourceId":"business-source"}}}]}
            """))));
        try (var ignored = TenantContext.use("alpha")) {
            assertSame(pipeline, fixture.store.requirePipeline("p"));
        }
    }

    @Test void registeredResourcesStillRequireAuthorizationAndBoundedScopes() {
        Fixture fixture = new Fixture();
        fixture.pipeline(List.of(node("source", Map.of("registeredDatasourceId", "first"))));
        try (var ignored = TenantContext.use("alpha")) {
            MappingVersionStore compatibility = new MappingVersionStore(fixture.jdbc, fixture.repository);
            assertEquals("RESOURCE_FORBIDDEN", assertThrows(MappingException.class,
                    () -> compatibility.requirePipeline("p")).code());
            forbidden(() -> fixture.store.requirePipeline("p"));
            List<Pipeline.Node> nodes = new ArrayList<>();
            for (int index = 0; index < 201; index++) nodes.add(node("node-" + index,
                    Map.of("registeredDatasourceId", "source-" + index)));
            fixture.pipeline(nodes);
            assertEquals("BUDGET_EXCEEDED", assertThrows(MappingException.class,
                    () -> fixture.store.requirePipeline("p")).code());
        }
    }

    @Test void publishedReferencesUseOneBoundedReadAfterAuthorizationForTwentyAndHundredNodes() {
        for (int count : List.of(20, 100)) {
            Fixture fixture = new Fixture();
            fixture.source("first", "alpha", "alice", "org-a");
            fixture.pipeline(List.of(node("first", Map.of("registeredDatasourceId", "first"))));
            List<Object[]> rows = new ArrayList<>();
            for (int index = 0; index < count; index++) rows.add(new Object[]{"alpha", "p", "node-" + index, "plan-" + index});
            rows.add(new Object[]{"beta", "p", "foreign-node", "foreign-plan"});
            rows.add(new Object[]{"alpha", "other-pipeline", "other-node", "other-plan"});
            fixture.jdbc.batchUpdate("INSERT INTO data_access_mapping_publication_t VALUES(?,?,?,?)", rows);
            try (var ignored = TenantContext.use("alpha")) {
                fixture.jdbc.reads = 0;
                Map<String, String> published = fixture.store.publishedReferences("p");
                assertEquals(count, published.size());
                assertEquals("plan-0", published.get("node-0"));
                assertFalse(published.containsKey("foreign-node"));
                assertFalse(published.containsKey("other-node"));
                assertEquals(3, fixture.jdbc.reads, "two authorization reads plus one publication read");
                verifyNoInteractions(fixture.scopes);
                assertThrows(UnsupportedOperationException.class, () -> published.put("changed", "changed"));
            }
        }
    }

    @Test void publicationsRejectRevokedResourcesDuplicatesAndOverLimitReferences() {
        Fixture fixture = new Fixture();
        fixture.source("first", "alpha", "bob", "org-a");
        fixture.pipeline(List.of(node("first", Map.of("registeredDatasourceId", "first"))));
        try (var ignored = TenantContext.use("alpha")) {
            fixture.jdbc.reads = 0;
            assertTrue(fixture.store.publishedReferences("p").isEmpty());
            assertEquals(3, fixture.jdbc.reads, "Current-tenant source requires no menu-role query");
            fixture.scope(Scope.ORG, "alice", List.of("org-a"));
            assertTrue(fixture.store.publishedReferences("p").isEmpty());
            fixture.jdbc.update("INSERT INTO data_access_mapping_publication_t VALUES('alpha','p','same','first')");
            fixture.jdbc.update("INSERT INTO data_access_mapping_publication_t VALUES('alpha','p','same','second')");
            assertEquals("RESOURCE_CORRUPTED", assertThrows(MappingException.class,
                    () -> fixture.store.publishedReferences("p")).code());
            fixture.jdbc.update("DELETE FROM data_access_mapping_publication_t");
            List<Object[]> rows = new ArrayList<>();
            for (int index = 0; index < 101; index++) rows.add(new Object[]{"alpha", "p", "node-" + index, "plan-" + index});
            fixture.jdbc.batchUpdate("INSERT INTO data_access_mapping_publication_t VALUES(?,?,?,?)", rows);
            assertEquals("BUDGET_EXCEEDED", assertThrows(MappingException.class,
                    () -> fixture.store.publishedReferences("p")).code());
        }
    }

    @Test void twentyAndHundredPlanVersionsUseOneDigestVerifiedReadAfterAuthorization() {
        var json = new ObjectMapper();
        var document = json.createObjectNode().put("schemaVersion", "2.0").put("marker", "unchanged");
        String content = document.toString(), hash = MappingEngine.hash(document);
        for (int count : List.of(20, 100)) {
            Fixture fixture = new Fixture();
            fixture.source("first", "alpha", "alice", "org-a");
            fixture.pipeline(List.of(node("first", Map.of("registeredDatasourceId", "first"))));
            List<String> ids = new ArrayList<>();
            List<Object[]> rows = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                ids.add("plan-" + index);
                rows.add(new Object[]{"plan-" + index, "alpha", "p", hash, content});
            }
            fixture.jdbc.batchUpdate("INSERT INTO data_access_mapping_plan_t VALUES(?,?,?,?,?)", rows);
            try (var ignored = TenantContext.use("alpha")) {
                fixture.jdbc.reads = 0;
                var plans = fixture.store.batchPlans("p", ids);
                assertEquals(new HashSet<>(ids), plans.keySet());
                assertEquals(document, plans.get("plan-0"));
                assertEquals(3, fixture.jdbc.reads, "two authorization reads plus one digest-verified batch read");
                verifyNoInteractions(fixture.scopes);
                assertThrows(UnsupportedOperationException.class, () -> plans.put("changed", document));
            }
        }
    }

    @Test void batchPlanVersionsRejectMixedTenantPipelineMissingAndTamperedIds() {
        Fixture fixture = new Fixture();
        fixture.source("first", "alpha", "alice", "org-a");
        fixture.pipeline(List.of(node("first", Map.of("registeredDatasourceId", "first"))));
        var original = new ObjectMapper().createObjectNode().put("schemaVersion", "2.0").put("marker", "original");
        String hash = MappingEngine.hash(original), content = original.toString();
        fixture.jdbc.batchUpdate("INSERT INTO data_access_mapping_plan_t VALUES(?,?,?,?,?)", List.of(
                new Object[]{"own", "alpha", "p", hash, content},
                new Object[]{"foreign", "beta", "p", hash, content},
                new Object[]{"other", "alpha", "other-pipeline", hash, content}));
        try (var ignored = TenantContext.use("alpha")) {
            assertEquals(1, fixture.store.batchPlans("p", List.of("own", "own")).size());
            assertTrue(fixture.store.batchPlans("p", List.of()).isEmpty());
            for (String invalid : List.of("foreign", "other", "missing", ""))
                forbidden(() -> fixture.store.batchPlans("p", List.of("own", invalid)));
            fixture.jdbc.update("UPDATE data_access_mapping_plan_t SET plan_content=? WHERE tid='own'", "{\"marker\":\"tampered\"}");
            assertEquals("RESOURCE_CORRUPTED", assertThrows(MappingException.class,
                    () -> fixture.store.batchPlans("p", List.of("own"))).code());
            assertEquals("RESOURCE_CORRUPTED", assertThrows(MappingException.class,
                    () -> fixture.store.plan("p", "own")).code());
            assertEquals("BUDGET_EXCEEDED", assertThrows(MappingException.class,
                    () -> fixture.store.batchPlans("p", Collections.nCopies(101, "own"))).code());
        }
    }

    @Test void oversizedPlanContentIsRejectedBeforeParsingOrHashing() {
        Fixture fixture = new Fixture();
        fixture.source("first", "alpha", "alice", "org-a");
        fixture.pipeline(List.of(node("first", Map.of("registeredDatasourceId", "first"))));
        fixture.jdbc.update("INSERT INTO data_access_mapping_plan_t VALUES('large','alpha','p','unused',?)", "x".repeat(2_097_153));
        try (var ignored = TenantContext.use("alpha")) {
            assertEquals("BUDGET_EXCEEDED", assertThrows(MappingException.class,
                    () -> fixture.store.batchPlans("p", List.of("large"))).code());
        }
    }

    @Test void batchPlanContentHasAnAggregateBudgetAsWellAsPerPlanLimits() {
        Fixture fixture = new Fixture();
        fixture.source("first", "alpha", "alice", "org-a");
        fixture.pipeline(List.of(node("first", Map.of("registeredDatasourceId", "first"))));
        var document = new ObjectMapper().createObjectNode().put("value", "x".repeat(2_000_000));
        String content = document.toString(), hash = MappingEngine.hash(document);
        List<String> ids = new ArrayList<>();
        List<Object[]> rows = new ArrayList<>();
        for (int index = 0; index < 17; index++) {
            ids.add("plan-" + index);
            rows.add(new Object[]{"plan-" + index, "alpha", "p", hash, content});
        }
        fixture.jdbc.batchUpdate("INSERT INTO data_access_mapping_plan_t VALUES(?,?,?,?,?)", rows);
        try (var ignored = TenantContext.use("alpha")) {
            assertEquals("BUDGET_EXCEEDED", assertThrows(MappingException.class,
                    () -> fixture.store.batchPlans("p", ids)).code());
        }
    }
}
