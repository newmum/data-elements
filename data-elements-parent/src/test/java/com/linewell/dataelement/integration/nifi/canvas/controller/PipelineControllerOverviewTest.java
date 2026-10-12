package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PipelineControllerOverviewTest {
    @Test void overviewAggregatesAllAuthorizedFlowsAndOrdersBeforeSelectingFiveInTwoReads() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:overview;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var db=new JdbcTemplate(source);
        db.execute("DROP TABLE IF EXISTS nifi_pipeline_t");
        db.execute("CREATE TABLE nifi_pipeline_t(id varchar(64),name varchar(64),status varchar(32),tenant_id varchar(64),is_del int,nifi_process_group_id varchar(64),last_deployed_at bigint,last_stopped_at bigint,updated_at bigint)");
        for(int i=0;i<120;i++)db.update("INSERT INTO nifi_pipeline_t VALUES(?,?,?,?,0,null,?,null,?)","id-"+i,String.format("flow-%03d",119-i),i%3==0?"RUNNING":i%3==1?"RUN_ERROR":"SAVED","tenant-a",(long)i,(long)i);
        db.update("INSERT INTO nifi_pipeline_t VALUES('outside','aaa','RUNNING','tenant-b',0,null,999,null,999)");
        db.update("INSERT INTO nifi_pipeline_t VALUES('deleted','aaa','RUNNING','tenant-a',1,null,999,null,999)");
        var jdbc=spy(db);var repository=mock(PipelineRepository.class);var nifi=mock(NifiClient.class);
        var controller=new PipelineController(repository,nifi,null,null,jdbc,null,null,null,null);
        TenantContext.run("tenant-a",()->{
            Map<String,Object> result=controller.runtimeOverview("taskName","asc");
            assertThat(((Number)result.get("total")).intValue()).isEqualTo(120);
            assertThat(((Number)result.get("running")).intValue()).isEqualTo(40);
            assertThat(((Number)result.get("failed")).intValue()).isEqualTo(40);
            @SuppressWarnings("unchecked") var flows=(List<Map<String,Object>>)result.get("flows");
            assertThat(flows).extracting(row->row.get("taskName")).containsExactly("flow-000","flow-001","flow-002","flow-003","flow-004");
            var descending=controller.runtimeOverview("latestRunTime","desc");
            @SuppressWarnings("unchecked") var recent=(List<Map<String,Object>>)descending.get("flows");
            assertThat(recent).extracting(row->row.get("id")).containsExactly("id-119","id-118","id-117","id-116","id-115");
        });
        verify(jdbc,times(2)).queryForMap(anyString(),eq("tenant-a"));
        verify(jdbc,times(2)).queryForList(anyString(),eq("tenant-a"));
        verifyNoInteractions(nifi,repository);
        assertThatThrownBy(controller::runtimeOverview).hasMessageContaining("租户");
    }
}
