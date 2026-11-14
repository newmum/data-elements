package com.linewell.dataelement.integration.nifi.canvas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.dataassets.runtime.DataAssetRuntimeAdapter;
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineGroupOrganizer;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineTaskLifecycleSynchronizer;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.ssssssss.magicapi.core.service.MagicAPIService;

class PipelineControllerReadOnlyDetailTest {
    @Test
    void bothSaveEndpointsUseTheHiveProjectionWithoutAnyMetadataOrNifiReads() {
        for(Long revision : java.util.Arrays.asList(null,20L)) {
            var repository=mock(PipelineRepository.class);
            var source=new Pipeline.Node("s","source.mysql","source","source",0,0,Map.of());
            var mapping=new Pipeline.Node("m","transform.field-enrichment","mapping","transform",300,0,Map.of());
            var hive=com.linewell.dataelement.integration.nifi.canvas.pipeline.HiveDownstreamFixtures.rawTarget(new Pipeline.Node("h","sink.hive","hive","sink",600,0,Map.of()));
            var saved=new Pipeline("p","saved",null,10L,20L,new Pipeline.Dsl(1,List.of(source,hive),List.of()),null,null,null,null,null,null,null);
            var edited=new Pipeline("p","edited",null,null,null,new Pipeline.Dsl(1,List.of(source,mapping,hive),List.of(new Pipeline.Edge("sm","s","m","default"),new Pipeline.Edge("mh","m","h","default"))),null,null,null,null,null,null,null);
            when(repository.findById("p")).thenReturn(Optional.of(saved));
            when(repository.update(org.mockito.ArgumentMatchers.eq("p"),org.mockito.ArgumentMatchers.any())).thenAnswer(call->{
                java.util.function.UnaryOperator<Pipeline> mutate=call.getArgument(1);return mutate.apply(saved);
            });
            var controller=new PipelineController(repository,null,null,null,null,null,null,null,null);
            var result=controller.update("p",edited,revision).getBody();
            assertThat(result.dsl().nodes()).containsExactly(source,hive);
            assertThat(result.dsl().edges()).singleElement().satisfies(e->{assertThat(e.source()).isEqualTo("s");assertThat(e.target()).isEqualTo("h");});
        }
    }
    @Test
    void readingSecondHopKeepsItsActualSourceAndSavedTimestampWithoutRepairWrites() {
        var repository = mock(PipelineRepository.class);
        var nifi = mock(NifiClient.class);
        var magic = mock(MagicAPIService.class);
        var assets = mock(DataAssetRuntimeAdapter.class);
        var jdbc = mock(JdbcTemplate.class);
        var connections = mock(DataSourceConnectionPropertyResolver.class);
        var transactions = mock(PlatformTransactionManager.class);
        var lifecycle = mock(PipelineTaskLifecycleSynchronizer.class);
        var groups = mock(PipelineGroupOrganizer.class);
        var source = new Pipeline.Node("source-ods", "source.oceanbase", "汇聚库", "source", 60, 100,
                Map.of("sourceTableId", "ods-table", "sourceDbId", "ods-db", "incrementalColumn", "ODS_GXSJ"));
        var saved = new Pipeline("second-hop", "第二跳", null, 10L, 20L,
                new Pipeline.Dsl(1, List.of(source), List.of()), "native-group", PipelineStatus.STOPPED,
                "deployed-hash", 15L, null, null, null);
        when(repository.findById("second-hop")).thenReturn(Optional.of(saved));
        var controller = new PipelineController(repository, nifi, magic, assets, jdbc, connections, transactions,
                lifecycle, groups);

        var response = controller.get("second-hop");

        assertThat(response.getBody()).isSameAs(saved);
        assertThat(response.getBody().dsl().nodes().getFirst().config())
                .containsEntry("sourceTableId", "ods-table").containsEntry("sourceDbId", "ods-db")
                .containsEntry("incrementalColumn", "ODS_GXSJ");
        assertThat(response.getBody().updatedAt()).isEqualTo(20L);
        verify(repository).findById("second-hop");
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(nifi, magic, assets, jdbc, connections, transactions, lifecycle, groups);
    }

    @Test
    void projectedDetailKeepsTheStoredRevisionForTheNextSave() {
        var source = new Pipeline.Node("s", "source.mysql", "source", "source", 0, 0, Map.of());
        var stored = new Pipeline("p", "stored", null, 10L, 20L,
                new Pipeline.Dsl(1, List.of(source), List.of()), null, null, null, null, null, null, null);
        var projected = new Pipeline("p", "stored", null, 10L, 20L,
                new Pipeline.Dsl(1, List.of(), List.of()), null, null, null, null, null, null, null);

        assertThat(projected.designHash()).isNotEqualTo(stored.designHash());
        assertThat(projected.withStoredDesignVersion(stored).designHash()).isEqualTo(stored.designHash());
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
