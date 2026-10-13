package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.linewell.dataelement.integration.nifi.canvas.pipeline.*;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MappingProjectionServiceTest {
    @ParameterizedTest
    @ValueSource(ints={20,100})
    void savesMandatoryLegacyFlagsWithoutPerRuleReadsOrCrossTenantChanges(int count) {
        DriverManagerDataSource source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        CountingJdbc jdbc=new CountingJdbc(source);
        jdbc.execute("CREATE TABLE data_access_agg_task_t(tid VARCHAR(64),pipeline_id VARCHAR(64),tenant_id VARCHAR(32),is_del INT)");
        jdbc.execute("CREATE TABLE data_access_field_mapping(tid VARCHAR(32) PRIMARY KEY,task_id VARCHAR(64),source_field VARCHAR(128) NOT NULL,target_field VARCHAR(128) NOT NULL,target_data_type VARCHAR(64),func_value TEXT,sort_no INT,tenant_id VARCHAR(32) NOT NULL,created_time TIMESTAMP,updated_time TIMESTAMP,func_code VARCHAR(255),func_enable INT NOT NULL,dict_enable INT NOT NULL,dict_code VARCHAR(255),dict_value VARCHAR(255),is_del INT)");
        jdbc.update("INSERT INTO data_access_agg_task_t VALUES('task','p','tenant',0),('other','p','foreign',0)");
        jdbc.update("INSERT INTO data_access_field_mapping(tid,task_id,source_field,target_field,tenant_id,func_enable,dict_enable,dict_code,dict_value,is_del) VALUES('legacy','task','old','target0','tenant',0,1,'old-code','old-value',0),('foreign','other','untouched','target0','foreign',0,1,'foreign-code','foreign-value',0)");
        List<Map<String,Object>> rules=new ArrayList<>();
        for(int i=0;i<count;i++)rules.add(Map.of("ruleId","r"+i,"input",Map.of("kind","FIELD","name","source"+i),"target",Map.of("name","target"+i,"logicalType","STRING"),"steps",List.of()));
        Pipeline p=new Pipeline("p","test","",1L,2L,new Pipeline.Dsl(1,List.of(new Pipeline.Node("m","transform.field-mapping","映射","transform",0,0,Map.of("mappings",Map.of("schemaVersion","2.0","rules",rules)))),List.of()),null,PipelineStatus.DRAFT,null,null,null,null,null);
        MappingProjectionService service=new MappingProjectionService(jdbc);
        try(var ignored=TenantContext.use("tenant")) {
            for(int attempt=0;attempt<2;attempt++) {
                jdbc.reads=0;service.persist(p);assertEquals(2,jdbc.reads,"Task and mapping rows must each be read once, independent of rule count");
            }
        }
        assertEquals(count,jdbc.queryForObject("SELECT COUNT(*) FROM data_access_field_mapping WHERE tenant_id='tenant' AND is_del=0 AND func_enable=1 AND dict_enable=0 AND dict_code IS NULL AND dict_value IS NULL",Integer.class));
        assertEquals(count,jdbc.queryForObject("SELECT COUNT(*) FROM data_access_field_mapping WHERE tenant_id='tenant'",Integer.class),"Repeated save must reuse stable rule identities");
        assertEquals("foreign-code",jdbc.queryForObject("SELECT dict_code FROM data_access_field_mapping WHERE tid='foreign'",String.class));
        assertEquals("untouched",jdbc.queryForObject("SELECT source_field FROM data_access_field_mapping WHERE tid='foreign'",String.class));
    }
    static class CountingJdbc extends JdbcTemplate {
        int reads;
        CountingJdbc(javax.sql.DataSource source){super(source);}
        @Override public <T> List<T> queryForList(String sql,Class<T>type,Object...args){reads++;return super.queryForList(sql,type,args);}
        @Override public List<Map<String,Object>> queryForList(String sql,Object...args){reads++;return super.queryForList(sql,args);}
    }
    @Test void removedUnifiedNodeSoftDeletesItsProjectionInOneBatch(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString(),eq(String.class),any(),any())).thenReturn(List.of("task"));
        when(jdbc.queryForList(anyString(),anyString(),anyString())).thenReturn(List.of(Map.of("tid","row","func_code","UNIFIED_RULE_V2","func_value","{\"pipelineId\":\"p\",\"nodeId\":\"removed\",\"ruleId\":\"r\"}")));
        Pipeline p=new Pipeline("p","test","",1L,2L,new Pipeline.Dsl(1,List.of(),List.of()),null,PipelineStatus.DRAFT,null,null,null,null,null);
        try(var ignored=TenantContext.use("tenant")){new MappingProjectionService(jdbc).persist(p);}
        verify(jdbc,times(1)).batchUpdate(eq("UPDATE data_access_field_mapping SET is_del=1 WHERE tid=? AND tenant_id=?"),anyList());
        verify(jdbc,times(1)).queryForList(anyString(),eq(String.class),eq("p"),eq("tenant"));
        verify(jdbc,times(1)).queryForList(anyString(),eq("task"),eq("tenant"));
    }
}
