package com.linewell.dataelement.integration.nifi.canvas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.dataassets.runtime.DataAssetRuntimeAdapter;
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.dataservice.pull.ApiPullConfigurationMagicModule;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineGroupOrganizer;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineTaskLifecycleSynchronizer;
import com.linewell.dataelement.integration.nifi.canvas.mapping.AccessTaskFieldMappingBinder;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.ssssssss.magicapi.core.service.MagicAPIService;

class PipelineControllerReadOnlyDetailTest {
    @Test
    void readingSecondHopKeepsItsActualSourceAndSavedTimestampWithoutRepairWrites() {
        var repository = mock(PipelineRepository.class);
        var nifi = mock(NifiClient.class);
        var magic = mock(MagicAPIService.class);
        var assets = mock(DataAssetRuntimeAdapter.class);
        var jdbc = mock(JdbcTemplate.class);
        var connections = mock(DataSourceConnectionPropertyResolver.class);
        var pull = mock(ApiPullConfigurationMagicModule.class);
        var lifecycle = mock(PipelineTaskLifecycleSynchronizer.class);
        var groups = mock(PipelineGroupOrganizer.class);
        var binder = mock(AccessTaskFieldMappingBinder.class);
        var source = new Pipeline.Node("source-ods", "source.oceanbase", "汇聚库", "source", 60, 100,
                Map.of("sourceTableId", "ods-table", "sourceDbId", "ods-db", "incrementalColumn", "ODS_GXSJ"));
        var saved = new Pipeline("second-hop", "第二跳", null, 10L, 20L,
                new Pipeline.Dsl(1, List.of(source), List.of()), "native-group", PipelineStatus.STOPPED,
                "deployed-hash", 15L, null, null, null);
        when(repository.findById("second-hop")).thenReturn(Optional.of(saved));
        var controller = new PipelineController(repository, nifi, magic, assets, jdbc, connections, pull, lifecycle, groups, binder);

        var response = controller.get("second-hop");

        assertThat(response.getBody()).isSameAs(saved);
        assertThat(response.getBody().dsl().nodes().getFirst().config())
                .containsEntry("sourceTableId", "ods-table").containsEntry("sourceDbId", "ods-db")
                .containsEntry("incrementalColumn", "ODS_GXSJ");
        assertThat(response.getBody().updatedAt()).isEqualTo(20L);
        verify(repository).findById("second-hop");
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(nifi, magic, assets, jdbc, connections, pull, lifecycle, groups, binder);
    }

    @Test
    void missingDetailReturnsNotFoundWithoutRepairOrExternalReads() {
        var repository = mock(PipelineRepository.class);
        when(repository.findById("missing")).thenReturn(Optional.empty());
        var controller = new PipelineController(repository, null, null, null, null, null, null, null, null);
        assertThat(controller.get("missing").getStatusCode().value()).isEqualTo(404);
        verify(repository).findById("missing"); verifyNoMoreInteractions(repository);
    }
}
