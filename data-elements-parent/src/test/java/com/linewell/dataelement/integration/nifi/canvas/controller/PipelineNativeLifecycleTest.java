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
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.*;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.nio.file.Path;
import java.util.*;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;

/** Opt-in native smoke test: empty flows only; removes its isolated test root in finally. */
@EnabledIfEnvironmentVariable(named = "NIFI_SMOKE_NODE_FILE", matches = ".+")
class PipelineNativeLifecycleTest {
    @Test
    void nativeHierarchyRedeployFailureRetryAndHorizontalLayout() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode config = mapper.readTree(Path.of(System.getenv("NIFI_SMOKE_NODE_FILE")).toFile());
        var resolver = mock(NifiNodeRuntimeResolver.class);
        var node = new NifiNodeRuntimeResolver.RuntimeNode("smoke", "smoke", "smoke", config.path("base_url").asText(),
                config.path("auth_username").asText(), config.path("auth_password").asText(), true,
                config.path("root_process_group_id").asText("root"));
        when(resolver.resolve()).thenReturn(node);
        when(resolver.restClient(any())).thenCallRealMethod();
        NifiClient nifi = new NifiClient(resolver, mapper);
        String actualRoot = nifi.getRootProcessGroupId();
        var position = PipelineGroupOrganizer.nextPosition(nifi.listProcessGroups(actualRoot));
        String testRoot = nifi.createProcessGroup(actualRoot, "画布管理验证-" + UUID.randomUUID(), position.x(), position.y()).id();
        when(resolver.resolve()).thenReturn(new NifiNodeRuntimeResolver.RuntimeNode(node.tenantId(), node.id(), node.code(),
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
                    (id, p) -> ((UnaryOperator<Pipeline>) i.getArgument(1)).apply(p)));
            when(repo.updateStatus(anyString(), any())).thenAnswer(i -> pipelines.compute(i.getArgument(0), (id, p) ->
                    new Pipeline(p.id(), p.name(), p.description(), p.createdAt(), p.updatedAt(), p.dsl(),
                            p.nifiProcessGroupId(), i.getArgument(1), p.lastDeployedHash(), p.lastDeployedAt(),
                            p.lastStoppedAt(), p.nodeMapping(), p.lastBulletinId())));
            var compiler = new DslCompiler(nifi, mock(ManifestRegistry.class), mock(FieldMappingService.class), mock(HiveModule.class));
            var controller = new PipelineRunController(repo, compiler, nifi, mock(ErrorService.class),
                    mock(PipelineTaskLifecycleSynchronizer.class), mock(PipelineRuntimeStatusResolver.class), groups,
                    mock(TargetTableCleanupService.class));
            assertThat(controller.deploy("one", null).getStatusCode().value()).isEqualTo(200);
            String firstGroup = pipelines.get("one").nifiProcessGroupId();
            assertThat(controller.deploy("one", null).getStatusCode().value()).isEqualTo(200);
            assertThat(pipelines.get("one").nifiProcessGroupId()).isEqualTo(firstGroup);
            assertThat(controller.deploy("two", null).getStatusCode().value()).isEqualTo(200);
            String taskParent = nifi.getProcessGroup(firstGroup).path("component").path("parentGroupId").asText();
            String sourceParent = nifi.getProcessGroup(taskParent).path("component").path("parentGroupId").asText();
            var tasks = nifi.listProcessGroups(sourceParent);
            assertThat(tasks).hasSize(2);
            assertThat(tasks.stream().map(g -> g.path("component").path("position").path("x").asDouble()).distinct().count()).isEqualTo(2);
            Pipeline old = pipelines.get("one");
            var invalid = new Pipeline.Dsl(2, List.of(new Pipeline.Node("invalid", "unknown", "invalid", "source", 0, 0, Map.of())), List.of());
            pipelines.put("one", withDsl(old, invalid));
            assertThat(controller.deploy("one", null).getStatusCode().value()).isEqualTo(400);
            String partialGroup = pipelines.get("one").nifiProcessGroupId();
            assertThat(partialGroup).isNotEqualTo(firstGroup);
            assertThat(nifi.listProcessGroups(taskParent)).hasSize(1);
            pipelines.put("one", withDsl(pipelines.get("one"), new Pipeline.Dsl(3, List.of(), List.of())));
            assertThat(controller.deploy("one", null).getStatusCode().value()).isEqualTo(200);
            assertThat(pipelines.get("one").nifiProcessGroupId()).isNotEqualTo(partialGroup);
            assertThat(nifi.listProcessGroups(taskParent)).hasSize(1);
            assertThat(controller.undeploy("one").getStatusCode().value()).isEqualTo(200);
            assertThat(nifi.listProcessGroups(sourceParent).stream().map(g -> g.path("id").asText())).doesNotContain(taskParent);
            assertThat(controller.undeploy("two").getStatusCode().value()).isEqualTo(200);
        } finally {
            nifi.cleanupProcessGroupTree(testRoot);
        }
        assertThat(nifi.listProcessGroups(actualRoot).stream().map(g -> g.path("id").asText())).doesNotContain(testRoot);
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
