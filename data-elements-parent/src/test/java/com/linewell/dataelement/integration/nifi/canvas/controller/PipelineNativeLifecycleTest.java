package com.linewell.dataelement.integration.nifi.canvas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import com.linewell.dataelement.integration.nifi.canvas.errors.ErrorService;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.*;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry;
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import com.linewell.dataelement.integration.nifi.canvas.mapping.MappingUpgradeService;
import com.linewell.dataelement.integration.nifi.canvas.mapping.MappingVersionStore;
import com.linewell.dataelement.integration.nifi.canvas.mapping.UnifiedMappingService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.*;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.mapping.core.MappingEngine;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Exercises the checked-in Magic against a loopback NiFi; always removes its own native test tree. */
@EnabledIfEnvironmentVariable(named = "NIFI_SMOKE_NODE_FILE", matches = ".+")
class PipelineNativeLifecycleTest {
    @Test
    void nativeSiblingsQueueProtectionFailureRetryStartStopAndUndeploy() throws Exception {
        var fixture = nativeFixture();
        var node = fixture.node();
        var nifi = fixture.nifi();
        String actualRoot = nifi.getRootProcessGroupId();
        var position = PipelineGroupOrganizer.nextPosition(nifi.listProcessGroups(actualRoot));
        String testRoot = nifi.createProcessGroup(actualRoot, "画布管理验证-" + UUID.randomUUID(), position.x(), position.y()).id();
        when(fixture.resolver().resolve()).thenReturn(new NifiNodeRuntimeResolver.RuntimeNode(node.tenantId(), node.id(), node.code(),
                node.baseUrl(), node.username(), node.password(), true, testRoot));
        try (var tenant = TenantContext.use("smoke")) {
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            when(jdbc.queryForList(anyString(), anyString(), anyString())).thenAnswer(i -> {
                String sql = i.getArgument(0);
                if (sql.contains("db_datasource_t")) return List.of(Map.of("tid", "db", "db_name", "验证数据源", "app_id", "app", "org_id", "dept"));
                if (sql.contains("sym_application_t")) return List.of(Map.of("tid", "app", "app_name", "验证应用系统"));
                if (sql.contains("rm_org_t")) return List.of(Map.of("id", "dept", "name", "验证部门"));
                return List.of(Map.of("tid", "task-" + i.getArgument(2), "task_name", "验证任务-" + i.getArgument(2), "source_db_id", "db"));
            });
            var groups = new PipelineGroupOrganizer(nifi, jdbc);
            PipelineRepository repo = mock(PipelineRepository.class);
            Map<String, Pipeline> pipelines = new HashMap<>();
            pipelines.put("one", pipeline("one", 1));
            pipelines.put("two", pipeline("two", 1));
            when(repo.findById(anyString())).thenAnswer(i -> Optional.ofNullable(pipelines.get(i.getArgument(0))));
            when(repo.update(anyString(), any())).thenAnswer(i -> pipelines.compute(i.getArgument(0),
                    (id, p) -> i.<UnaryOperator<Pipeline>>getArgument(1).apply(p)));
            when(repo.updateStatus(anyString(), any())).thenAnswer(i -> pipelines.compute(i.getArgument(0), (id, p) ->
                    new Pipeline(p.id(), p.name(), p.description(), p.createdAt(), p.updatedAt(), p.dsl(),
                            p.nifiProcessGroupId(), i.getArgument(1), p.lastDeployedHash(), p.lastDeployedAt(),
                            p.lastStoppedAt(), p.nodeMapping(), p.lastBulletinId())));
            var compiler = new DslCompiler(nifi, mock(ManifestRegistry.class), mock(FieldMappingService.class), mock(HiveModule.class));
            var errors = mock(ErrorService.class);
            var lifecycle = mock(PipelineTaskLifecycleSynchronizer.class);
            var deployment = new NifiDeploymentMagicFixture(repo, compiler, nifi, errors, lifecycle, groups,
                    mock(TargetTableCleanupService.class), true);
            assertThat(deployment.deploy("one", null, false).getStatusCode().value()).isEqualTo(200);
            String firstGroup = pipelines.get("one").nifiProcessGroupId();
            assertThat(deployment.deploy("one", null, false).getStatusCode().value()).isEqualTo(200);
            assertThat(pipelines.get("one").nifiProcessGroupId()).isEqualTo(firstGroup);
            assertThat(deployment.deploy("two", null, false).getStatusCode().value()).isEqualTo(200);
            String secondGroup = pipelines.get("two").nifiProcessGroupId();
            String application = parent(nifi, firstGroup);
            assertThat(parent(nifi, secondGroup)).isEqualTo(application);
            String department = parent(nifi, application);
            assertThat(childIds(nifi, testRoot)).containsExactly(department);
            assertThat(childIds(nifi, department)).containsExactly(application);
            assertThat(comment(nifi, department)).isEqualTo("data-elements:v1:smoke:department:dept");
            assertThat(comment(nifi, application)).isEqualTo("data-elements:v1:smoke:application:app");
            assertThat(comment(nifi, firstGroup)).isEqualTo("data-elements:v1:smoke:pipeline:one");
            assertThat(comment(nifi, secondGroup)).isEqualTo("data-elements:v1:smoke:pipeline:two");
            assertThat(childIds(nifi, application)).containsExactlyInAnyOrder(firstGroup, secondGroup);
            assertThat(nifi.listProcessGroups(application))
                    .extracting(g -> g.path("component").path("position").path("x").asDouble())
                    .doesNotHaveDuplicates();

            Pipeline old = pipelines.get("one");
            var invalid = new Pipeline.Dsl(2, List.of(new Pipeline.Node("invalid", "unknown", "invalid", "source", 0, 0, Map.of())), List.of());
            pipelines.put("one", withDsl(old, invalid));
            String connection = enqueue(nifi, firstGroup);
            long queued = queued(nifi, firstGroup);
            var blocked = deployment.deploy("one", null, false);
            assertThat(blocked.getStatusCode().value()).isEqualTo(400);
            assertThat(((Map<?, ?>) blocked.getBody()).get("error").toString()).contains("排队数据");
            assertThat(pipelines.get("one").nifiProcessGroupId()).isEqualTo(firstGroup);
            assertThat(childIds(nifi, application)).containsExactlyInAnyOrder(firstGroup, secondGroup);
            assertThat(queued(nifi, firstGroup)).isEqualTo(queued);
            // Only empty the synthetic queue owned by this fixture, then exercise compile failure and retry.
            nifi.dropFlowFiles(connection, 30_000L);
            assertThat(queued(nifi, firstGroup)).isZero();
            assertThat(deployment.deploy("one", null, false).getStatusCode().value()).isEqualTo(400);
            String partialGroup = pipelines.get("one").nifiProcessGroupId();
            assertThat(partialGroup).isNotEqualTo(firstGroup);
            assertThat(pipelines.get("one").status()).isEqualTo(PipelineStatus.DEPLOY_FAILED);
            assertThat(childIds(nifi, application)).containsExactlyInAnyOrder(partialGroup, secondGroup);
            pipelines.put("one", withDsl(pipelines.get("one"), new Pipeline.Dsl(3, List.of(), List.of())));
            assertThat(deployment.deploy("one", null, false).getStatusCode().value()).isEqualTo(200);
            String retriedGroup = pipelines.get("one").nifiProcessGroupId();
            assertThat(retriedGroup).isNotIn(firstGroup, partialGroup, secondGroup);
            assertThat(childIds(nifi, application)).containsExactlyInAnyOrder(retriedGroup, secondGroup);

            String generator = generator(nifi, retriedGroup);
            nifi.autoTerminateUnusedRelationships(generator, Set.of());
            assertThat(deployment.start("one").getStatusCode().value()).isEqualTo(200);
            assertThat(pipelines.get("one").status()).isEqualTo(PipelineStatus.RUNNING);
            assertThat(nifi.getProcessor(generator).path("component").path("state").asText()).isEqualTo("RUNNING");
            assertThat(deployment.stop("one").getStatusCode().value()).isEqualTo(200);
            nifi.waitForAllProcessorsStopped(retriedGroup, 30_000L);
            assertThat(nifi.getProcessor(generator).path("component").path("state").asText()).isEqualTo("STOPPED");
            assertThat(pipelines.get("one").status()).isEqualTo(PipelineStatus.STOPPED);

            assertThat(deployment.undeploy("one").getStatusCode().value()).isEqualTo(200);
            assertThat(pipelines.get("one").nifiProcessGroupId()).isNull();
            assertThat(pipelines.get("one").status()).isEqualTo(PipelineStatus.SAVED);
            assertThat(childIds(nifi, application)).containsExactly(secondGroup);
            assertThat(childIds(nifi, department)).containsExactly(application);
            assertThat(deployment.undeploy("two").getStatusCode().value()).isEqualTo(200);
            assertThat(pipelines.get("two").nifiProcessGroupId()).isNull();
            assertThat(childIds(nifi, testRoot)).isEmpty();
        } finally {
            nifi.cleanupProcessGroupTree(testRoot);
        }
        assertThat(nifi.listProcessGroups(actualRoot).stream().map(g -> g.path("id").asText())).doesNotContain(testRoot);
    }

    @Test
    void nativeFieldUpgradeRewiresOnlyMappingNode() throws Exception {
        var fixture = nativeFixture();
        var nifi = fixture.nifi();
        String id = "validation-mapping-upgrade";
        String root = nifi.getRootProcessGroupId();
        assertThat(root).isEqualTo(nifi.get("/flow/process-groups/root", JsonNode.class).path("processGroupFlow").path("id").asText());
        String reader = publicCodec(nifi, root, "PUBLIC-READER-JSON");
        String writer = publicCodec(nifi, root, "PUBLIC-WRITER-JSON");
        var position = PipelineGroupOrganizer.nextPosition(nifi.listProcessGroups(root));
        String group = nifi.createProcessGroup(root, id, position.x(), position.y(), "data-elements:v1:smoke:pipeline:" + id).id();
        try (var tenant = TenantContext.use("smoke")) {
            String source = generator(nifi, group);
            String oldMapping = nifi.createProcessor(group, "org.apache.nifi.processors.standard.ConvertRecord", "validation-old-mapping",
                    400, 0, Map.of("record-reader", reader, "record-writer", writer), "1 sec", "TIMER_DRIVEN").id();
            String sink = nifi.createProcessor(group, "org.apache.nifi.processors.standard.LogAttribute", "validation-sink",
                    800, 0, Map.of(), "1 sec", "TIMER_DRIVEN").id();
            String incoming = nifi.createConnection(group, source, "PROCESSOR", oldMapping, "PROCESSOR", List.of("success")).id();
            String outgoing = nifi.createConnection(group, oldMapping, "PROCESSOR", sink, "PROCESSOR", List.of("success")).id();
            nifi.autoTerminateUnusedRelationships(oldMapping, Set.of("success"));
            nifi.autoTerminateUnusedRelationships(sink, Set.of());
            var plan = MappingEngine.compile(new ObjectMapper().readTree("{\"schemaVersion\":\"2.0\",\"rules\":[]}"));
            var node = new Pipeline.Node("mapping", "transform.field-mapping", "validation-upgraded-mapping", "transform",
                    400, 0, Map.of("mappings", plan.document()));
            var edges = List.of(new Pipeline.Edge("in", "source", "mapping", null), new Pipeline.Edge("out", "mapping", "sink", null));
            var oldEndpoints = Map.of("in", new NifiNodeMapping.EdgeEndpoint(source, oldMapping, incoming),
                    "out", new NifiNodeMapping.EdgeEndpoint(oldMapping, sink, outgoing));
            var bindings = new NifiNodeMapping(Map.of("source", source, "mapping", oldMapping, "sink", sink),
                    Map.of(source, "source", oldMapping, "mapping", sink, "sink"), oldEndpoints);
            var pipeline = new Pipeline(id, id, null, 1L, 2L, new Pipeline.Dsl(1, List.of(node), edges), group,
                    PipelineStatus.STOPPED, "validation-old-hash", 1L, null, bindings, 0L);
            var versions = mock(MappingVersionStore.class);
            when(versions.archive(eq(pipeline), eq(Map.of("mapping", plan)))).thenReturn(Map.of("mapping", "validation-plan"));
            var repository = mock(PipelineRepository.class);
            when(repository.update(eq(id), any())).thenAnswer(call -> call.<UnaryOperator<Pipeline>>getArgument(1).apply(pipeline));
            var transactions = mock(PlatformTransactionManager.class);
            when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            var compiler = new DslCompiler(nifi, mock(ManifestRegistry.class), mock(FieldMappingService.class), mock(HiveModule.class));
            compiler.setMappingVersions(versions);
            var service = new MappingUpgradeService(versions, repository, compiler, nifi, transactions);

            var result = service.replaceNode(pipeline, node, "smoke", false, () -> new UnifiedMappingService.Compiled(plan, Map.of()));

            assertThat(result).containsEntry("sourceProcessorIdsPreserved", true).containsEntry("otherDraftChangesPublished", false);
            var updated = (Pipeline) result.get("pipeline");
            assertThat(updated.nifiProcessGroupId()).isEqualTo(group);
            assertThat(updated.status()).isEqualTo(PipelineStatus.STOPPED);
            String replacement = updated.nodeMapping().primaryProcessorIds().get("mapping");
            assertThat(replacement).isNotIn(source, oldMapping, sink);
            assertThat(updated.nodeMapping().primaryProcessorIds()).containsEntry("source", source).containsEntry("sink", sink);
            assertThat(nifi.getProcessor(source).path("component").path("parentGroupId").asText()).isEqualTo(group);
            assertThat(nifi.getProcessor(oldMapping).path("component").path("state").asText()).isEqualTo("DISABLED");
            assertThat(nifi.getProcessor(replacement).path("component").path("type").asText())
                    .isEqualTo("com.linewell.dataelement.nifi.HaitongTransformRecord");
            var newEndpoints = updated.nodeMapping().edgeProcessorEndpoints();
            assertThat(newEndpoints.get("in")).isEqualTo(new NifiNodeMapping.EdgeEndpoint(source, replacement, newEndpoints.get("in").connectionId()));
            assertThat(newEndpoints.get("out")).isEqualTo(new NifiNodeMapping.EdgeEndpoint(replacement, sink, newEndpoints.get("out").connectionId()));
            assertThat(nifi.listConnections(group)).extracting(connection -> connection.path("id").asText())
                    .contains(newEndpoints.get("in").connectionId(), newEndpoints.get("out").connectionId()).doesNotContain(incoming, outgoing);
            verify(versions).activateNode(pipeline, "mapping", "validation-plan", group);
        } finally {
            nifi.cleanupProcessGroupTree(group);
        }
        assertThat(childIds(nifi, root)).doesNotContain(group);
    }

    private static String publicCodec(NifiClient nifi, String root, String name) {
        var codecs = nifi.listControllerServices(root).stream()
                .filter(service -> name.equals(service.path("component").path("name").asText()))
                .toList();
        assertThat(codecs).as("isolated NiFi must provide the enabled public codec %s", name).hasSize(1);
        assertThat(codecs.getFirst().path("component").path("state").asText()).isEqualTo("ENABLED");
        return codecs.getFirst().path("id").asText();
    }

    private static NativeFixture nativeFixture() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode config = mapper.readTree(Path.of(System.getenv("NIFI_SMOKE_NODE_FILE")).toFile());
        assertThat(URI.create(config.path("base_url").asText()).getHost())
                .as("the writable NiFi fixture must be local")
                .isIn("localhost", "127.0.0.1", "[::1]");
        var resolver = mock(NifiNodeRuntimeResolver.class);
        var node = new NifiNodeRuntimeResolver.RuntimeNode("smoke", "smoke", "smoke", config.path("base_url").asText(),
                config.path("auth_username").asText(), config.path("auth_password").asText(), true,
                config.path("root_process_group_id").asText("root"));
        when(resolver.resolve()).thenReturn(node);
        when(resolver.restClient(any())).thenCallRealMethod();
        return new NativeFixture(resolver, node, new NifiClient(resolver, mapper));
    }

    private record NativeFixture(NifiNodeRuntimeResolver resolver, NifiNodeRuntimeResolver.RuntimeNode node, NifiClient nifi) {}

    private static String parent(NifiClient nifi, String id) {
        return nifi.getProcessGroup(id).path("component").path("parentGroupId").asText();
    }

    private static String comment(NifiClient nifi, String id) {
        return nifi.getProcessGroup(id).path("component").path("comments").asText();
    }

    private static List<String> childIds(NifiClient nifi, String parent) {
        return nifi.listProcessGroups(parent).stream().map(g -> g.path("id").asText()).toList();
    }

    private static String generator(NifiClient nifi, String group) {
        return nifi.createProcessor(group, "org.apache.nifi.processors.standard.GenerateFlowFile",
                "validation-source", 0, 0, Map.of(), "1 sec", "TIMER_DRIVEN").id();
    }

    private static String enqueue(NifiClient nifi, String group) throws InterruptedException {
        String source = generator(nifi, group);
        String sink = nifi.createProcessor(group, "org.apache.nifi.processors.standard.LogAttribute",
                "validation-sink", 400, 0, Map.of(), "1 sec", "TIMER_DRIVEN").id();
        nifi.autoTerminateUnusedRelationships(sink, Set.of());
        String connection = nifi.createConnection(group, source, "PROCESSOR", sink, "PROCESSOR", List.of("success")).id();
        nifi.setProcessorRunStatus(source, "RUNNING");
        try {
            long limit = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (queued(nifi, group) <= 0 && System.nanoTime() < limit) {
                Thread.sleep(200);
            }
            assertThat(queued(nifi, group)).isPositive();
        } finally {
            nifi.setProcessorRunStatus(source, "STOPPED");
        }
        nifi.waitForAllProcessorsStopped(group, 30_000L);
        return connection;
    }

    private static long queued(NifiClient nifi, String group) {
        JsonNode aggregate = nifi.getProcessGroupStatus(group).path("processGroupStatus").path("aggregateSnapshot");
        return aggregate.path("queuedCount").asLong(aggregate.path("flowFilesQueued").asLong(-1));
    }

    private Pipeline pipeline(String id, int version) {
        return new Pipeline(id, "验证流程-" + id, null, 1L, 1L, new Pipeline.Dsl(version, List.of(), List.of()),
                null, PipelineStatus.SAVED, null, null, null, null, null);
    }

    private Pipeline withDsl(Pipeline p, Pipeline.Dsl dsl) {
        return new Pipeline(p.id(), p.name(), p.description(), p.createdAt(), p.updatedAt(), dsl,
                p.nifiProcessGroupId(), p.status(), p.lastDeployedHash(), p.lastDeployedAt(), p.lastStoppedAt(), p.nodeMapping(), p.lastBulletinId());
    }
}
