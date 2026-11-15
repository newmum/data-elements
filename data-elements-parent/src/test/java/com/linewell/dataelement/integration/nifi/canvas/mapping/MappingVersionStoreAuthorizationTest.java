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
}
