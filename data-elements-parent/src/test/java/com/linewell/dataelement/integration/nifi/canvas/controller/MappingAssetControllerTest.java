package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiMutationAspect;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.SerializedNifiMutation;
import com.linewell.dataelement.integration.nifi.canvas.mapping.*;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.*;
import com.linewell.dataelement.mapping.core.MappingException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MappingAssetControllerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final MappingVersionStore store = mock(MappingVersionStore.class);
    private final UnifiedMappingService engine = mock(UnifiedMappingService.class);
    private final RegisteredMappingSchemaService schemas = mock(RegisteredMappingSchemaService.class);
    private final PipelineRepository pipelines = mock(PipelineRepository.class);
    private final MappingProjectionService projections = mock(MappingProjectionService.class);
    private final MappingUpgradeService upgrades = mock(MappingUpgradeService.class);
    private final MappingAssetController controller = new MappingAssetController(store, engine, schemas, pipelines, projections, upgrades);

    private static Pipeline pipeline(long updatedAt) {
        var source = new Pipeline.Node("source", "source.query", "source", "source", 1, 2, Map.of("registeredDatasourceId", "db"));
        var mapping = new Pipeline.Node("mapping", "transform.field-mapping", "mapping", "transform", 3, 4,
                Map.of("mappings", "old", "sourceTableId", "table"));
        return new Pipeline("p", "name", "description", 1L, updatedAt,
                new Pipeline.Dsl(1, List.of(source, mapping), List.of(new Pipeline.Edge("edge", "source", "mapping", "success"))),
                "group", PipelineStatus.STOPPED, "deployed-hash", 2L, 3L, null, 4L);
    }

    private static ObjectNode request(long expectedUpdatedAt) {
        return JSON.createObjectNode().put("pipelineId", "p").put("nodeId", "mapping").put("expectedUpdatedAt", expectedUpdatedAt);
    }

    private record LockFixture(MappingAssetController proxy, Connection connection) {}

    private LockFixture lockingProxy(boolean acquired) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(acquired ? 1 : 0);
        AspectJProxyFactory factory = new AspectJProxyFactory(controller);
        factory.addAspect(new NifiMutationAspect(dataSource));
        return new LockFixture(factory.getProxy(), connection);
    }

    @Test void restoreUsesTheSharedMutationLockBeforeAnyDraftReadOrWrite() throws Exception {
        var method = MappingAssetController.class.getMethod("restore", String.class, JsonNode.class);
        assertNotNull(method.getAnnotation(SerializedNifiMutation.class));
        assertNotNull(method.getAnnotation(Transactional.class));
        var locked = lockingProxy(false);
        try (var ignored = TenantContext.use("alpha")) {
            var error = assertThrows(ResponseStatusException.class, () -> locked.proxy().restore("plan", request(5)));
            assertEquals(409, error.getStatusCode().value());
            verifyNoInteractions(store, engine, pipelines, projections);
            verify(locked.connection()).close();
        }
    }

    @Test void restoreRevalidatesBeforeMutationAndKeepsSourceAndRuntimeState() throws Exception {
        Pipeline original = pipeline(5);
        ObjectNode saved = JSON.createObjectNode().put("schemaVersion", "2.0");
        when(store.requirePipeline("p")).thenReturn(original);
        when(store.plan("p", "plan")).thenReturn(saved);
        when(pipelines.update(eq("p"), any())).thenAnswer(invocation -> {
            UnaryOperator<Pipeline> update = invocation.getArgument(1);
            return update.apply(original);
        });
        var locked = lockingProxy(true);
        try (var ignored = TenantContext.use("alpha")) {
            Pipeline result = locked.proxy().restore("plan", request(5));
            assertEquals(original.dsl().nodes().getFirst(), result.dsl().nodes().getFirst());
            assertEquals(original.dsl().edges(), result.dsl().edges());
            assertEquals("table", result.dsl().nodes().get(1).config().get("sourceTableId"));
            assertEquals(saved.toString(), result.dsl().nodes().get(1).config().get("mappings"));
            assertEquals(original.nifiProcessGroupId(), result.nifiProcessGroupId());
            assertEquals(original.status(), result.status());
            assertEquals(original.lastDeployedHash(), result.lastDeployedHash());
            assertEquals(original.lastDeployedAt(), result.lastDeployedAt());
            assertEquals(original.lastStoppedAt(), result.lastStoppedAt());
            assertEquals(original.lastBulletinId(), result.lastBulletinId());
            var order = inOrder(locked.connection(), store, engine, pipelines, projections);
            order.verify(locked.connection()).prepareStatement("SELECT GET_LOCK(?, 0)");
            order.verify(store).requirePipeline("p");
            order.verify(store).plan("p", "plan");
            order.verify(engine).compile(saved);
            order.verify(pipelines).update(eq("p"), any());
            order.verify(projections).persist(result);
            order.verify(locked.connection()).prepareStatement("SELECT RELEASE_LOCK(?)");
        }
    }

    @Test void staleRestoreAndChangedDraftDuringMutationDoNotPersistProjection() {
        Pipeline original = pipeline(5);
        when(store.requirePipeline("p")).thenReturn(original);
        assertEquals("DRAFT_VERSION_CONFLICT", assertThrows(MappingException.class,
                () -> controller.restore("plan", request(4))).code());
        verify(store, never()).plan(anyString(), anyString());
        verifyNoInteractions(engine, pipelines, projections);
        when(store.plan("p", "plan")).thenReturn(JSON.createObjectNode());
        when(pipelines.update(eq("p"), any())).thenAnswer(invocation -> {
            UnaryOperator<Pipeline> update = invocation.getArgument(1);
            return update.apply(pipeline(6));
        });
        assertEquals("DRAFT_VERSION_CONFLICT", assertThrows(MappingException.class,
                () -> controller.restore("plan", request(5))).code());
        verifyNoInteractions(projections);
    }

    @Test void revokedTemplateCannotBeReadOrImportedEvenIfNewBindingsWouldBeAccessible() {
        ObjectNode saved = JSON.createObjectNode().put("schemaVersion", "2.0");
        ObjectNode envelope = JSON.createObjectNode().set("content", saved);
        when(store.resources(List.of("template"), "TEMPLATE")).thenReturn(Map.of("template", envelope));
        doThrow(new MappingException("RESOURCE_FORBIDDEN", "revoked")).when(engine).compile(saved);
        assertEquals("RESOURCE_FORBIDDEN", assertThrows(MappingException.class,
                () -> controller.template("template")).code());
        assertEquals("RESOURCE_FORBIDDEN", assertThrows(MappingException.class,
                () -> controller.importTemplate("template", JSON.createObjectNode())).code());
        verify(engine, times(2)).compile(saved);
        verifyNoInteractions(pipelines, projections);
    }

    @Test void immutableTemplateAndPlanReadsReauthorizeWithoutReplacingStoredContent() {
        ObjectNode saved = JSON.createObjectNode().put("schemaVersion", "2.0").put("savedVersionMarker", "original");
        ObjectNode envelope = JSON.createObjectNode().set("content", saved);
        when(store.resources(List.of("template"), "TEMPLATE")).thenReturn(Map.of("template", envelope));
        when(store.plan("p", "plan")).thenReturn(saved);
        assertSame(saved, controller.template("template"));
        assertSame(saved, controller.plan("plan", "p"));
        assertEquals("original", saved.path("savedVersionMarker").asText());
        verify(engine, times(2)).compile(saved);
        verify(store, never()).saveResource(anyString(), anyString(), any(), any());
    }

    @Test void revokedHistoricalPlanCannotUseCurrentAccessiblePipelineToExposeOldReferences() {
        ObjectNode saved = JSON.createObjectNode().put("schemaVersion", "2.0");
        when(store.plan("p", "plan")).thenReturn(saved);
        doThrow(new MappingException("RESOURCE_FORBIDDEN", "revoked")).when(engine).compile(saved);
        assertEquals("RESOURCE_FORBIDDEN", assertThrows(MappingException.class,
                () -> controller.plan("plan", "p")).code());
    }

    @Test void schemaContractReadsBothSidesAsOneAuthorizedPhysicalBatch() {
        ObjectNode body = JSON.createObjectNode();
        body.putObject("sourceRef").put("datasourceId", "db").put("tableId", "source");
        body.putObject("targetRef").put("datasourceId", "db").put("tableId", "target");
        ArrayNode sourceFields = JSON.createArrayNode().add(JSON.createObjectNode().put("name", "sourceField"));
        ArrayNode targetFields = JSON.createArrayNode().add(JSON.createObjectNode().put("name", "targetField"));
        var bound = Map.of("source", new RegisteredMappingSchemaService.Resolved("db", "source", "schema", "source", sourceFields, Map.of()),
                "target", new RegisteredMappingSchemaService.Resolved("db", "target", "schema", "target", targetFields, Map.of()));
        when(schemas.resolve(anyMap())).thenReturn(bound);
        when(schemas.physicalBatch(bound)).thenReturn(Map.of("source", sourceFields, "target", targetFields));
        JsonNode result = controller.schemaContract(body);
        assertEquals(sourceFields, result.path("source"));
        assertEquals(targetFields, result.path("target"));
        verify(schemas, times(1)).resolve(argThat(refs -> refs.size() == 2));
        verify(schemas, times(1)).physicalBatch(bound);
        verify(schemas, never()).physical(any());
    }

    @Test void publishedStartUsesSharedMutationLockAndPassesExpectedRevisionToLifecycle() throws Exception {
        PublishedMappingLifecycleService lifecycle = mock(PublishedMappingLifecycleService.class);
        controller.setPublishedLifecycle(lifecycle);
        Map<String, Object> expected = Map.of("pipelineId", "p", "status", "RUNNING");
        when(lifecycle.start("p", 5L)).thenReturn(expected);
        var locked = lockingProxy(false);
        try (var ignored = TenantContext.use("alpha")) {
            assertThrows(ResponseStatusException.class, () -> locked.proxy().startPublished(request(5)));
            verifyNoInteractions(lifecycle);
            var unlocked = lockingProxy(true);
            assertSame(expected, unlocked.proxy().startPublished(request(5)));
            verify(lifecycle, times(1)).start("p", 5L);
            verifyNoInteractions(store, engine, pipelines, projections, upgrades);
        }
    }
}
