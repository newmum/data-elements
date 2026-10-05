package com.linewell.dataelement.integration.nifi.canvas.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.model.nifi.NifiEntity;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PipelineGroupOrganizerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final NifiClient nifi = mock(NifiClient.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final Map<String, List<JsonNode>> tree = new HashMap<>();
    private String orgId = "department1";
    private String applicationName = "业务系统";
    private String taskName = "任务";

    private PipelineGroupOrganizer organizer() {
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        when(nifi.listProcessGroups(anyString())).thenAnswer(i -> new ArrayList<>(tree.getOrDefault(i.getArgument(0), List.of())));
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble(), anyString())).thenAnswer(i -> {
            String id = "group" + tree.values().stream().mapToInt(List::size).sum();
            Map<String, Object> component = Map.of("id", id, "name", i.getArgument(1), "comments", i.getArgument(4),
                    "position", Map.of("x", i.getArgument(2), "y", i.getArgument(3)));
            tree.computeIfAbsent(i.getArgument(0), k -> new ArrayList<>()).add(mapper.valueToTree(Map.of("id", id, "component", component)));
            return new NifiEntity(null, component, null, null);
        });
        when(jdbc.queryForList(anyString(), anyString(), anyString())).thenAnswer(i -> {
            String sql = i.getArgument(0);
            assertThat((String) i.getArgument(1)).isEqualTo("tenant1");
            if (sql.contains("db_datasource_t")) return List.of(Map.of("tid", "db1", "db_name", "业务库", "app_id", "app1", "org_id", orgId));
            if (sql.contains("sym_application_t")) return List.of(Map.of("tid", "app1", "app_name", applicationName, "org_id", orgId));
            if (sql.contains("rm_org_t")) return List.of(Map.of("id", orgId, "name", "所属部门"));
            return List.of(Map.of("tid", "task1", "task_name", taskName, "source_db_id", "db1"));
        });
        return new PipelineGroupOrganizer(nifi, jdbc);
    }

    @Test
    void repeatedPrepareCreatesOnlyDepartmentAndApplicationGroups() {
        try (var scope = TenantContext.use("tenant1")) {
            PipelineGroupOrganizer organizer = organizer();
            var first = organizer.prepare(pipeline());
            var second = organizer.prepare(pipeline());
            assertThat(first.parentId()).isEqualTo(second.parentId());
            verify(nifi, times(2)).createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble(), anyString());
            assertThat(tree.values().stream().flatMap(List::stream).map(g -> g.path("component").path("name").asText()))
                    .containsExactlyInAnyOrder("所属部门", "业务系统");
        }
    }

    @Test
    void omitsTheApplicationLevelWhenItHasTheSameNameAsTheTask() {
        applicationName = "福建省残疾人基础信息";
        taskName = "福建省残疾人基础信息";

        try (var scope = TenantContext.use("tenant1")) {
            var layout = organizer().prepare(pipeline());

            assertThat(layout.parentId()).isEqualTo("group0");
            verify(nifi, times(1)).createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble(), anyString());
            assertThat(tree.values().stream().flatMap(List::stream).map(g -> g.path("component").path("name").asText()))
                    .containsExactlyInAnyOrder("所属部门");
            assertThat(tree.values().stream().flatMap(List::stream).map(g -> g.path("component").path("comments").asText()))
                    .noneMatch(comment -> comment.contains(":application:"));
        }
    }

    @Test
    void groupsResourcePipelinesUnderTheSharedSurveillanceEngineGroup() {
        try (var scope = TenantContext.use("tenant1")) {
            PipelineGroupOrganizer organizer = organizer();
            var first = organizer.prepare(surveillancePipeline("结构化数据布控引擎流组 - 大巴购票 [person_bus]"));
            var second = organizer.prepare(surveillancePipeline("结构化数据布控引擎流组 - 酒店住宿 [person_hotel]"));

            assertThat(first.parentId()).isEqualTo(second.parentId());
            assertThat(first.parentId()).isEqualTo(tree.get("root").getFirst().path("id").asText());
            verify(nifi, times(1)).createProcessGroup(eq("root"), eq("布控引擎组"), anyDouble(), anyDouble(), anyString());
            assertThat(tree.get("root")).singleElement()
                    .extracting(group -> group.path("component").path("name").asText())
                    .isEqualTo("布控引擎组");
        }
    }

    @Test
    void discoversPreviousLeafWhenDepartmentChangesAndAllocatesToTheRight() {
        try (var scope = TenantContext.use("tenant1")) {
            PipelineGroupOrganizer organizer = organizer();
            var first = organizer.prepare(pipeline());
            String leaf = nifi.createProcessGroup(first.parentId(), "流程", 1040, 80, first.comments()).id();
            var same = organizer.prepare(pipeline());
            assertThat(same.position()).isEqualTo(new PipelineGroupOrganizer.Position(1040, 80));
            orgId = "department2";
            var moved = organizer.prepare(pipeline());
            assertThat(moved.parentId()).isNotEqualTo(first.parentId());
            assertThat(moved.previousIds()).containsExactly(leaf);
            assertThat(tree.get(first.topId()).get(1).path("component").path("position").path("x").asDouble()).isEqualTo(520);
        }
    }

    @Test
    void newPositionIsAfterManuallyMovedSiblings() {
        JsonNode moved = mapper.valueToTree(Map.of("component", Map.of("position", Map.of("x", 2100, "y", 170))));
        assertThat(PipelineGroupOrganizer.nextPosition(List.of(moved))).isEqualTo(new PipelineGroupOrganizer.Position(2620, 0));
    }

    @Test
    void movingTaskRemovesItsEmptyOldFolderButPreservesOtherTasks() {
        try (var scope = TenantContext.use("tenant1")) {
            var organizer = new PipelineGroupOrganizer(nifi, jdbc);
            when(nifi.getProcessGroup("old-task")).thenReturn(mapper.valueToTree(Map.of("component", Map.of(
                    "comments", "data-elements:v1:tenant1:task:task1", "parentGroupId", "source"))));
            when(nifi.getProcessGroup("source")).thenReturn(mapper.valueToTree(Map.of("component", Map.of(
                    "comments", "data-elements:v1:tenant1:datasource:db1", "parentGroupId", "app"))));
            when(nifi.get(eq("/flow/process-groups/old-task"), eq(JsonNode.class)))
                    .thenReturn(mapper.valueToTree(Map.of("processGroupFlow", Map.of("flow", Map.of()))));
            when(nifi.get(eq("/flow/process-groups/source"), eq(JsonNode.class)))
                    .thenReturn(mapper.valueToTree(Map.of("processGroupFlow", Map.of("flow", Map.of("processGroups", List.of(Map.of("id", "other-task")))))));
            organizer.pruneEmptyAncestors(List.of("old-task"), "new-task");
            verify(nifi).deleteProcessGroup("old-task");
            verify(nifi, never()).deleteProcessGroup("source");
            verify(nifi, never()).getProcessGroup("new-task");
        }
    }

    @Test
    void removesAllEmptyLegacyWrappersIncludingTheTenantDataAccessGroup() {
        try (var scope = TenantContext.use("tenant1")) {
            var organizer = new PipelineGroupOrganizer(nifi, jdbc);
            Map<String, JsonNode> components = Map.of(
                    "old-task", component("data-elements:v1:tenant1:task:task1", "source"),
                    "source", component("data-elements:v1:tenant1:datasource:db1", "application"),
                    "application", component("data-elements:v1:tenant1:application:app1", "department"),
                    "department", component("data-elements:v1:tenant1:department:department1", "legacy-tenant"),
                    "legacy-tenant", component("data-elements:v1:tenant1:tenant:tenant1", "root"),
                    "root", component("", ""));
            components.forEach((id, group) -> when(nifi.getProcessGroup(id)).thenReturn(group));
            for (String id : List.of("old-task", "source", "application", "department", "legacy-tenant")) {
                when(nifi.get(eq("/flow/process-groups/" + id), eq(JsonNode.class)))
                        .thenReturn(mapper.valueToTree(Map.of("processGroupFlow", Map.of("flow", Map.of()))));
            }

            organizer.pruneEmptyAncestors(List.of("old-task"), "new-task");

            verify(nifi).deleteProcessGroup("old-task");
            verify(nifi).deleteProcessGroup("source");
            verify(nifi).deleteProcessGroup("application");
            verify(nifi).deleteProcessGroup("department");
            verify(nifi).deleteProcessGroup("legacy-tenant");
            verify(nifi, never()).deleteProcessGroup("root");
        }
    }

    private JsonNode component(String comments, String parentId) {
        return mapper.valueToTree(Map.of("component", Map.of("comments", comments, "parentGroupId", parentId)));
    }

    private Pipeline pipeline() {
        return new Pipeline("p1", "流程", null, 1L, 1L, new Pipeline.Dsl(1, List.of(new Pipeline.Node(
                "source", "source.mysql", "源", "source", 0, 0, Map.of("registeredDatasourceId", "db1"))), List.of()),
                null, null, null, null, null, null, null);
    }

    private Pipeline surveillancePipeline(String name) {
        return new Pipeline(name, name, null, 1L, 1L, new Pipeline.Dsl(1, List.of(), List.of()),
                null, null, null, null, null, null, null);
    }
}
