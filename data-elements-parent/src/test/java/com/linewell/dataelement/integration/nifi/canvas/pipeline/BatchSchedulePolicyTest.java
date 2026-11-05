package com.linewell.dataelement.integration.nifi.canvas.pipeline;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.mapper.NifiPipelineTMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BatchSchedulePolicyTest {
    private Pipeline.Node node(String id, String key, Map<String,Object> config) {
        return new Pipeline.Node(id,key,id,"source",0,0,config);
    }
    private Pipeline.Dsl dsl(Pipeline.Node... nodes) {return new Pipeline.Dsl(1,List.of(nodes),List.of());}
    @Test void assignmentIsDailyStableAndDistributedAcrossDayAndTenant() {
        var times=new HashSet<>(); var hours=new HashSet<>();
        for(int i=0;i<100;i++) {
            var a=BatchSchedulePolicy.defaultConfig("tenant-a","pipeline-"+i,"source","source.mysql",Map.of());
            assertThat(a).isEqualTo(BatchSchedulePolicy.defaultConfig("tenant-a","pipeline-"+i,"source","source.mysql",Map.of()));
            var time=java.time.LocalTime.parse(a.get("batchScheduleTime").toString());
            assertThat(a.get("schedulingPeriod")).isEqualTo(time.getSecond()+" "+time.getMinute()+" "+time.getHour()+" * * ?");
            assertThat(a.get("schedulingStrategy")).isEqualTo("CRON_DRIVEN");
            times.add(time);hours.add(time.getHour());
        }
        assertThat(times.size()).isGreaterThan(95);assertThat(hours.size()).isGreaterThan(20);
        assertThat(BatchSchedulePolicy.defaultConfig("tenant-a","p","s","source.ftp",Map.of()))
            .isNotEqualTo(BatchSchedulePolicy.defaultConfig("tenant-b","p","s","source.ftp",Map.of()));
        assertThatThrownBy(()->BatchSchedulePolicy.defaultConfig("","p","s","source.mysql",Map.of())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void existingCustomAndKafkaAreNotMigratedAndCopiesReceiveOwnTime() {
        var legacy=dsl(node("old","source.mysql",Map.of("schedulingPeriod","60 sec")),node("kafka","source.kafka",Map.of("schedulingPeriod","0 sec")));
        assertThat(BatchSchedulePolicy.normalize(legacy,legacy,"tenant","p")).isSameAs(legacy);
        assertThat(BatchSchedulePolicy.normalize(legacy,null,"tenant","p")).isSameAs(legacy);
        var custom=dsl(node("custom","source.api",Map.of("batchSchedulePolicy","CUSTOM","schedulingStrategy","CRON_DRIVEN","schedulingPeriod","0 0 3 * * ?")));
        assertThat(BatchSchedulePolicy.normalize(custom,null,"tenant","p")).isSameAs(custom);
        var created=BatchSchedulePolicy.normalize(dsl(node("s","source.sftp",BatchSchedulePolicy.markDefault(Map.of()))),null,"tenant","p");
        assertThat(BatchSchedulePolicy.normalize(created,created,"tenant","p")).isSameAs(created);
        var copy=BatchSchedulePolicy.normalize(created,null,"tenant","copy");
        assertThat(copy.nodes().getFirst().config()).isNotEqualTo(created.nodes().getFirst().config());
        var pending=dsl(node("old","source.mysql",BatchSchedulePolicy.markDefault(Map.of())));
        assertThat(BatchSchedulePolicy.normalize(pending,legacy,"tenant","p").nodes().getFirst().config().get("schedulingPeriod")).isNotEqualTo("");
    }
    @Test void twentyAndHundredSourceGraphsUseSameConstantRepositoryReadsAndTrustedTenant() {
        for(int count:List.of(20,100)) {
            var mapper=mock(NifiPipelineTMapper.class);var repository=new PipelineRepository(new ObjectMapper(),mapper);
            var nodes=IntStream.range(0,count).mapToObj(i->node("s"+i,"source.mysql",BatchSchedulePolicy.markDefault(Map.of()))).toList();
            var input=new Pipeline("validation","validation",null,null,null,new Pipeline.Dsl(1,nodes,List.of()),null,null,null,null,null,null,null);
            try(var tenant=TenantContext.use("tenant-a")) {
                var saved=repository.save(input);
                assertThat(saved.dsl().nodes()).allSatisfy(n->assertThat(n.config().get("schedulingPeriod")).isNotEqualTo(""));
                verify(mapper,times(2)).selectById("validation");verify(mapper).insert(any(com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.NifiPipelineT.class));
                verifyNoMoreInteractions(mapper);
            }
        }
    }
}
