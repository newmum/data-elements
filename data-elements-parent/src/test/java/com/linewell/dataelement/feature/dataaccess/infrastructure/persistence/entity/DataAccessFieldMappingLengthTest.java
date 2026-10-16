package com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.mapper.DataAccessFieldMappingMapper;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessAggTaskTService;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.impl.DataAccessFieldMappingServiceImpl;
import com.linewell.dataelement.integration.nifi.canvas.mapping.AccessTaskFieldMappingBinder;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.platform.persistence.config.MybatisPlusConfig;
import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.mybatis.PlatformTenantLineHandler;
import com.linewell.dataelement.platform.tenant.infrastructure.persistence.TenantTableRegistry;
import java.util.*;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataAccessFieldMappingLengthTest {
    private static final long LONGTEXT_CAPACITY=4294967295L;
    private static final ObjectMapper JSON=new ObjectMapper();

    @ParameterizedTest @ValueSource(ints={20,100})
    void deploymentBindingReadsAllWideCapacitiesOnceAndKeepsTenantScope(int count)throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(source);
        jdbc.execute("""
            CREATE TABLE data_access_field_mapping(
              tid VARCHAR(64) PRIMARY KEY,task_id VARCHAR(64),source_field VARCHAR(128),source_field_cn VARCHAR(128),
              source_field_length BIGINT,source_data_type VARCHAR(64),target_field VARCHAR(128),target_field_cn VARCHAR(128),
              target_field_length BIGINT,target_data_type VARCHAR(64),dict_enable INT,dict_value VARCHAR(255),dict_code VARCHAR(255),
              func_enable INT,func_value VARCHAR(255),func_code VARCHAR(255),sort_no INT,tenant_id VARCHAR(64),revision VARCHAR(64),
              created_by VARCHAR(64),created_time TIMESTAMP,updated_by VARCHAR(64),updated_time TIMESTAMP,is_del INT)
            """);
        List<Long>lengths=Arrays.asList(0L,255L,2147483647L,2147483648L,LONGTEXT_CAPACITY,Long.MAX_VALUE,null);
        List<Object[]>batch=new ArrayList<>();
        for(int index=0;index<count;index++)batch.add(new Object[]{"row"+index,"task","source"+index,lengths.get(index%lengths.size()),"LONGTEXT","target"+index,lengths.get((index+1)%lengths.size()),"CLOB",0,0,index,"tenant-a",0});
        batch.add(new Object[]{"foreign","task","private_source",LONGTEXT_CAPACITY,"LONGTEXT","private_target",LONGTEXT_CAPACITY,"CLOB",0,0,0,"tenant-b",0});
        batch.add(new Object[]{"deleted","task","deleted_source",LONGTEXT_CAPACITY,"LONGTEXT","deleted_target",LONGTEXT_CAPACITY,"CLOB",0,0,0,"tenant-a",1});
        jdbc.batchUpdate("INSERT INTO data_access_field_mapping(tid,task_id,source_field,source_field_length,source_data_type,target_field,target_field_length,target_data_type,dict_enable,func_enable,sort_no,tenant_id,is_del) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",batch);

        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(DataAccessFieldMappingMapper.class);configuration.addMapper(LegacyIntMapper.class);
        var registry=mock(TenantTableRegistry.class);when(registry.hasTenantColumn("data_access_field_mapping")).thenReturn(true);
        var tenantPlugin=new MybatisPlusConfig(new PlatformTenantLineHandler(new TenantProperties(),registry)).mybatisPlusInterceptor();
        var counter=new ReadCounter();var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(source);factory.setConfiguration(configuration);factory.setPlugins(tenantPlugin,counter);
        try(var tenant=TenantContext.use("tenant-a");var session=factory.getObject().openSession(true)) {
            // Reproduce the old Integer result mapping against the same persisted value.
            assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,()->session.getMapper(LegacyIntMapper.class).read());
            session.clearCache();counter.reads=0;
            var mapper=session.getMapper(DataAccessFieldMappingMapper.class);
            var rows=mapper.selectList(new LambdaQueryWrapper<DataAccessFieldMapping>().eq(DataAccessFieldMapping::getTaskId,"task").eq(DataAccessFieldMapping::getIsDel,0).orderByAsc(DataAccessFieldMapping::getSortNo));
            assertEquals(count,rows.size());assertEquals(1,counter.reads);
            for(int index=0;index<count;index++){assertEquals(lengths.get(index%lengths.size()),rows.get(index).getSourceFieldLength());assertEquals(lengths.get((index+1)%lengths.size()),rows.get(index).getTargetFieldLength());}
            mapper.insert(new DataAccessFieldMapping().setTid("written").setTaskId("write-only").setSourceFieldLength(LONGTEXT_CAPACITY).setTargetFieldLength(Long.MAX_VALUE).setIsDel(0));
            session.clearCache();var written=mapper.selectById("written");assertEquals(LONGTEXT_CAPACITY,written.getSourceFieldLength());assertEquals(Long.MAX_VALUE,written.getTargetFieldLength());

            var mappings=new DataAccessFieldMappingServiceImpl();ReflectionTestUtils.setField(mappings,"baseMapper",mapper);
            var tasks=mock(IDataAccessAggTaskTService.class);when(tasks.findByPipelineId("pipeline")).thenReturn(new DataAccessAggTaskT().setTid("task"));
            var pipeline=new Pipeline("pipeline","LOB capacity test",null,1L,1L,new Pipeline.Dsl(1,List.of(
                new Pipeline.Node("source","source.mysql","source","source",0,0,Map.of()),
                new Pipeline.Node("map","transform.field-enrichment","mapping","transform",520,0,Map.of())),List.of()),null,null,null,null,null,null,null);
            session.clearCache();counter.reads=0;
            var bound=new AccessTaskFieldMappingBinder(tasks,mappings).bind(pipeline);
            var spec=JSON.readTree(String.valueOf(bound.dsl().nodes().get(1).config().get("mappings")));
            assertEquals(count,spec.path("mappings").size());assertEquals(1,counter.reads,"20/100 mappings must use one SQL read");
            assertFalse(spec.toString().contains("private_source"));assertFalse(spec.toString().contains("deleted_source"));
            System.out.println("mappingCapacityRows="+count+", deploymentBindingSql=1, tenantScoped=true, valuesPreserved=true");
        }
    }

    @Test void jsonPreservesSourceAndTargetCapacities()throws Exception {
        var row=JSON.readValue("{\"sourceFieldLength\":4294967295,\"targetFieldLength\":9223372036854775807}",DataAccessFieldMapping.class);
        assertEquals(LONGTEXT_CAPACITY,row.getSourceFieldLength());assertEquals(Long.MAX_VALUE,row.getTargetFieldLength());
        var json=JSON.readTree(JSON.writeValueAsString(row));assertEquals(LONGTEXT_CAPACITY,json.path("sourceFieldLength").longValue());assertEquals(Long.MAX_VALUE,json.path("targetFieldLength").longValue());
        assertNull(new DataAccessFieldMapping().getSourceFieldLength());
    }

    interface LegacyIntMapper {
        @Select("SELECT source_field_length FROM data_access_field_mapping WHERE tid='row4'")
        Integer read();
    }
    @Intercepts(@Signature(type=Executor.class,method="query",args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class}))
    static class ReadCounter implements Interceptor {
        int reads;
        @Override public Object intercept(Invocation invocation)throws Throwable {reads++;return invocation.proceed();}
    }
}
