package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.*;
import com.linewell.dataelement.mapping.core.*;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MappingVersionStoreTest {
    static class Counted extends JdbcTemplate {int reads;Counted(DriverManagerDataSource source){super(source);}@Override public <T>T query(PreparedStatementCreator creator,PreparedStatementSetter setter,ResultSetExtractor<T> extractor){reads++;return super.query(creator,setter,extractor);}}
    private Counted database(){var jdbc=new Counted(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","",""));jdbc.execute("""
        CREATE TABLE data_access_mapping_plan_t(tid varchar(32) primary key,tenant_id varchar(32),pipeline_id varchar(64),node_id varchar(64),plan_revision bigint,draft_revision bigint,plan_hash varchar(64),plan_version varchar(16),engine_version varchar(16),function_version varchar(16),source_schema_hash varchar(64),target_schema_hash varchar(64),plan_content clob,resource_version_set clob,created_by varchar(64),created_time timestamp,unique(tenant_id,pipeline_id,node_id,plan_hash,draft_revision),unique(tenant_id,tid));
        CREATE TABLE data_access_mapping_resource_t(tid varchar(32) primary key,tenant_id varchar(32),resource_kind varchar(32),resource_key varchar(128),version_no bigint,resource_hash varchar(64),definition_json clob,resource_content clob,created_by varchar(64),created_time timestamp);
        CREATE TABLE data_access_mapping_publication_t(tid varchar(32) primary key,tenant_id varchar(32),pipeline_id varchar(64),node_id varchar(64),plan_id varchar(32),process_group_id varchar(64),published_time timestamp,foreign key(tenant_id,plan_id) references data_access_mapping_plan_t(tenant_id,tid));
        CREATE TABLE data_access_agg_task_t(pipeline_id varchar(64),tenant_id varchar(32),is_del int,source_db_id varchar(64),target_db_id varchar(64),source_table_id varchar(64),target_table_id varchar(64));
        CREATE TABLE db_datasource_t(tid varchar(64),tenant_id varchar(32),is_del int,created_by varchar(64),org_id varchar(64));
        INSERT INTO data_access_agg_task_t VALUES('p','alpha',0,'source',NULL,NULL,NULL);
        INSERT INTO db_datasource_t VALUES('source','alpha',0,'alice','org-a');
        """);return jdbc;}
    @Test void twentyAndHundredNodeArchivesUseFourReadsIncludingAuthorizationAndHistorySortsBeforePaging()throws Exception{
        var json=new ObjectMapper();var plan=MappingEngine.compile(json.readTree("""
            {"schemaVersion":"2.0","rules":[{"ruleId":"r","input":{"kind":"FIELD","name":"x"},"target":{"name":"y","logicalType":"STRING"},"steps":[]}]}
            """));
        for(int count:List.of(20,100)){var jdbc=database();var repository=mock(PipelineRepository.class);List<Pipeline.Node>nodes=new ArrayList<>();Map<String,MappingEngine.Plan>plans=new LinkedHashMap<>();for(int i=0;i<count;i++){String name=String.format("node-%03d",i);nodes.add(new Pipeline.Node(name,"transform.field-mapping",name,"transform",0,0,Map.of()));plans.put(name,plan);}var pipeline=new Pipeline("p","test","",1L,2L,new Pipeline.Dsl(1,nodes,List.of()),null,PipelineStatus.DRAFT,null,null,null,null,null);when(repository.findById("p")).thenReturn(Optional.of(pipeline));var scopes=mock(DataScopeAuthorizationService.class,CALLS_REAL_METHODS);doReturn(new DataScopeAuthorizationService.ScopeDecision("MENU:2081000000000000002",DataScopeAuthorizationService.Scope.OWNER,"alice",List.of(),List.of("ordinary-role"),true)).when(scopes).currentScope(anyString());var store=new MappingVersionStore(jdbc,repository,scopes);
            try(var ignored=TenantContext.use("alpha")){jdbc.reads=0;var ids=store.archive(pipeline,plans);assertEquals(count,ids.size());assertEquals(4,jdbc.reads);verify(scopes,times(1)).currentScope(anyString());jdbc.reads=0;assertEquals(ids,store.archive(pipeline,plans));assertEquals(4,jdbc.reads);var page=store.history("p",null,2,10,"node_id","asc");assertEquals((long)count,page.get("total"));assertEquals("node-010",((List<Map<String,Object>>)page.get("rows")).getFirst().get("node_id"));store.activate(pipeline,ids,"group");assertEquals(count,jdbc.queryForObject("SELECT COUNT(*) FROM data_access_mapping_publication_t",Integer.class));}
        }
    }
    @Test void mixedTenantResourceIdsRejectEntireRequestAndVersionsRemainImmutable()throws Exception{
        var jdbc=database();var store=new MappingVersionStore(jdbc,mock(PipelineRepository.class));var json=new ObjectMapper();String own,foreign;
        try(var ignored=TenantContext.use("alpha")){own=store.saveResource("TEMPLATE","template",json.createObjectNode(),json.createObjectNode().put("rule","original")).path("id").asText();}
        try(var ignored=TenantContext.use("beta")){foreign=store.saveResource("TEMPLATE","template",json.createObjectNode(),json.createObjectNode().put("rule","foreign")).path("id").asText();}
        try(var ignored=TenantContext.use("alpha")){assertEquals("RESOURCE_FORBIDDEN",assertThrows(MappingException.class,()->store.resources(List.of(own,foreign),"TEMPLATE")).code());assertEquals("original",store.resources(List.of(own),"TEMPLATE").get(own).path("content").path("rule").asText());assertEquals(2,store.saveResource("TEMPLATE","template",json.createObjectNode(),json.createObjectNode().put("rule","new")).path("version").asLong());assertEquals("original",store.resources(List.of(own),"TEMPLATE").get(own).path("content").path("rule").asText());}
    }
}
