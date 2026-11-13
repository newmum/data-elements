package com.linewell.dataelement.integration.nifi.canvas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineGroupOrganizer;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineTaskLifecycleSynchronizer;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.*;
import com.linewell.dataelement.dataservice.pull.ApiPullConfigurationMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PipelineSaveIdentityTest {
    private final PipelineRepository repo = mock(PipelineRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final NifiClient nifi = mock(NifiClient.class);
    private final PipelineGroupOrganizer groups = mock(PipelineGroupOrganizer.class);
    private final PipelineController controller = new PipelineController(repo, nifi, null, null, jdbc, null,
            mock(ApiPullConfigurationMagicModule.class), mock(PipelineTaskLifecycleSynchronizer.class), groups);

    @Test
    void catalogWithoutPipelineIdInBrowserReopensAndSavesSamePipeline() {
        try (var scope = TenantContext.use("tenant")) {
            Pipeline current = pipeline("p1", "original-group", PipelineStatus.RUNNING);
            when(jdbc.queryForList(anyString(), eq(String.class), eq("tenant"), eq("catalog1"))).thenReturn(List.of("p1"));
            when(repo.findById("p1")).thenReturn(Optional.of(current));
            when(repo.update(eq("p1"), any())).thenAnswer(i -> i.<UnaryOperator<Pipeline>>getArgument(1).apply(current));
            assertThat(controller.template(new PipelineController.PipelineTemplateRequest("catalog1")).getBody()).isEqualTo(current);
            Pipeline saved = controller.create(pipeline(null, null, PipelineStatus.DRAFT), "catalog1");
            assertThat(saved.id()).isEqualTo("p1");
            assertThat(saved.nifiProcessGroupId()).isEqualTo("original-group");
            assertThat(saved.status()).isEqualTo(PipelineStatus.RUNNING);
            verify(repo, never()).save(any());
            verifyNoInteractions(nifi);
        }
    }

    @Test
    void savingDesignCannotEraseOrReplaceServerDeploymentState() {
        Pipeline current = pipeline("p1", "original-group", PipelineStatus.RUNNING);
        when(repo.findById("p1")).thenReturn(Optional.of(current));
        when(repo.update(eq("p1"), any())).thenAnswer(i -> i.<UnaryOperator<Pipeline>>getArgument(1).apply(current));
        Pipeline saved = controller.update("p1", pipeline("p1", "foreign-group", PipelineStatus.DRAFT)).getBody();
        assertThat(saved.nifiProcessGroupId()).isEqualTo("original-group");
        assertThat(saved.status()).isEqualTo(PipelineStatus.RUNNING);
    }

    @Test
    void deletionCleansNativeGroupBeforeDeletingTaskAndPipelineRecords() {
        try (var scope = TenantContext.use("tenant")) {
            when(repo.findById("p1")).thenReturn(Optional.of(pipeline("p1", "original-group", PipelineStatus.STOPPED)));
            when(groups.existingIds(any())).thenReturn(List.of("original-group", "recovered-group"));
            when(repo.delete("p1")).thenReturn(true);
            assertThat(controller.delete("p1").getStatusCode().value()).isEqualTo(204);
            var order = inOrder(nifi, jdbc, repo);
            order.verify(nifi).cleanupProcessGroup("original-group");
            order.verify(nifi).cleanupProcessGroup("recovered-group");
            order.verify(jdbc).update(anyString(), eq("tenant"), eq("p1"));
            order.verify(repo).delete("p1");
        }
    }

    @Test
    void optimisticDesignSaveRejectsStaleTimestampWithoutDeploying() {
        var projection=mock(com.linewell.dataelement.integration.nifi.canvas.mapping.MappingProjectionService.class);
        controller.setMappingProjections(projection);
        Pipeline current=pipeline("p1","original-group",PipelineStatus.RUNNING);
        when(repo.findById("p1")).thenReturn(Optional.of(current));
        when(repo.update(eq("p1"),any())).thenAnswer(i->i.<UnaryOperator<Pipeline>>getArgument(1).apply(current));
        org.assertj.core.api.Assertions.assertThatThrownBy(()->controller.update("p1",current,0L)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(projection);
        assertThat(controller.update("p1",current,1L).getBody().nifiProcessGroupId()).isEqualTo("original-group");
        verify(projection,times(1)).persist(any());
        verifyNoInteractions(nifi);
    }

    @Test
    void nativeRuntimeStatusChangeDoesNotBlockSavingTheLoadedDesign() throws Exception {
        Pipeline loaded = pipeline("p1", "original-group", PipelineStatus.STOPPED);
        Pipeline running = new Pipeline(loaded.id(), loaded.name(), loaded.description(), loaded.createdAt(), 99L,
                loaded.dsl(), "native-group", PipelineStatus.RUNNING, "new-deploy-hash", 88L, 77L, null, 66L);
        when(repo.findById("p1")).thenReturn(Optional.of(running));
        when(repo.update(eq("p1"), any())).thenAnswer(i -> i.<UnaryOperator<Pipeline>>getArgument(1).apply(running));
        Pipeline edited = new Pipeline(loaded.id(), "新名称", loaded.description(), loaded.createdAt(), loaded.updatedAt(),
                loaded.dsl(), null, null, null, null, null, null, null);
        Pipeline saved = controller.update("p1", edited, loaded.updatedAt(), loaded.designHash()).getBody();
        assertThat(saved.name()).isEqualTo("新名称");
        assertThat(saved.status()).isEqualTo(PipelineStatus.RUNNING);
        assertThat(saved.nifiProcessGroupId()).isEqualTo("native-group");
        assertThat(saved.lastDeployedHash()).isEqualTo("new-deploy-hash");
        // The server revision is present on the wire, including task-chain embedded pipelines.
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(running)).path("designHash").asText())
                .isEqualTo(loaded.designHash());
        verifyNoInteractions(nifi);
    }

    @Test
    void successiveCanvasSavesKeepThePersistedDescriptionAndRevision() {
        Pipeline loaded = pipeline("p1", null, PipelineStatus.SAVED);
        Pipeline withDescription = new Pipeline(loaded.id(), loaded.name(), "已有流程说明",
                loaded.createdAt(), loaded.updatedAt(), loaded.dsl(), null, loaded.status(),
                null, null, null, null, null);
        var persisted = new AtomicReference<>(withDescription);
        when(repo.findById("p1")).thenAnswer(i -> Optional.of(persisted.get()));
        when(repo.update(eq("p1"), any())).thenAnswer(i -> {
            Pipeline saved = i.<UnaryOperator<Pipeline>>getArgument(1).apply(persisted.get());
            assertThat(saved.description()).isEqualTo(withDescription.description());
            persisted.set(saved);
            return saved;
        });
        Pipeline edited = new Pipeline(loaded.id(), "第一次编辑", null, null, null, loaded.dsl(),
                null, null, null, null, null, null, null);
        Pipeline saved = controller.update("p1", edited, null, withDescription.designHash()).getBody();
        assertThat(saved.designHash()).isEqualTo(persisted.get().designHash());
        Pipeline secondEdit = new Pipeline(loaded.id(), "第二次编辑", null, null, null, loaded.dsl(),
                null, null, null, null, null, null, null);
        assertThat(controller.update("p1", secondEdit, null, saved.designHash()).getBody().name())
                .isEqualTo("第二次编辑");
        Pipeline emptyDescription = new Pipeline(edited.id(), edited.name(), "", null, null, edited.dsl(),
                null, null, null, null, null, null, null);
        assertThat(emptyDescription.designHash()).isEqualTo(edited.designHash());
        verifyNoInteractions(nifi);
    }

    @Test
    void concurrentPlatformDesignChangeStillRejectsAnOldDesignRevision() {
        Pipeline loaded = pipeline("p1", "original-group", PipelineStatus.STOPPED);
        Pipeline changed = new Pipeline(loaded.id(), "其他入口的新名称", loaded.description(), loaded.createdAt(), 99L,
                loaded.dsl(), loaded.nifiProcessGroupId(), loaded.status(), null, null, null, null, null);
        when(repo.findById("p1")).thenReturn(Optional.of(changed));
        when(repo.update(eq("p1"), any())).thenAnswer(i -> i.<UnaryOperator<Pipeline>>getArgument(1).apply(changed));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.update("p1", loaded, null, loaded.designHash()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("平台画布设计");
        verifyNoInteractions(nifi);
    }

    @Test
    void collectedTableDoesNotRequireACompiledAssetOrSearchIndex() {
        try (var scope = TenantContext.use("tenant")) {
            when(jdbc.queryForList(contains("from db_table_t"), eq("tenant"), eq("table1"), eq("tenant")))
                    .thenReturn(List.of(Map.of("tid", "table1", "datasourceId", "source1", "tableName", "orders")));
            Map<String, Object> row = org.springframework.test.util.ReflectionTestUtils.invokeMethod(controller, "loadAssetByTid", "table1");
            assertThat(row).containsEntry("datasourceId", "source1").containsEntry("tableName", "orders");
            verify(jdbc).queryForList(contains("d.tenant_id=? and d.is_del=0"), eq("tenant"), eq("table1"), eq("tenant"));
        }
    }

    @Test
    void technicalTableTemplateRequiresTenantContext() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(controller, "loadAssetByTid", "table1"))
                .isInstanceOf(com.linewell.dataelement.platform.tenant.domain.TenantAccessException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void generatedJdbcNodesLinkTasksByRegisteredIdsRatherThanConnectionStrings() {
        var ds=Map.<String,Object>of("tid", "source1", "dbType", "MySQL", "host", "fixture.invalid", "port", "3306", "database", "fixture");
        var table=Map.<String,Object>of("tid", "table1", "tableName", "orders");
        Map<String,Object> source=org.springframework.test.util.ReflectionTestUtils.invokeMethod(controller,"buildSourceConfig",ds,table,List.of());
        Map<String,Object> sink=org.springframework.test.util.ReflectionTestUtils.invokeMethod(controller,"buildSinkConfig",ds,table,List.of());
        assertThat(source).containsEntry("registeredDatasourceId","source1").containsEntry("sourceTableId","table1");
        assertThat(sink).containsEntry("targetDbId","source1").containsEntry("targetTableId","table1");
        assertThat(sink.get("targetDbId")).isNotEqualTo(sink.get("jdbcUrl"));
    }

    private Pipeline pipeline(String id, String group, PipelineStatus status) {
        return new Pipeline(id, "流程", null, 1L, 1L, new Pipeline.Dsl(1, List.of(), List.of()),
                group, status, "hash", 1L, null, null, null);
    }
}
