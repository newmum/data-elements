package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.feature.dataquality.infrastructure.persistence.mapper.DataQualityMapper;
import com.linewell.dataelement.mapping.core.MappingException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RegisteredDictionaryCatalogTest {
    @Test void twentyAndHundredFieldsReadAuthorizationTablesAndColumnsOnce()throws Exception{
        for(int count:List.of(20,100)){
            var jdbc=mock(JdbcTemplate.class);var sources=mock(DataSourceConnectionPropertyResolver.class);var metadata=mock(DataQualityMapper.class);
            when(sources.resolveBatch(eq("tenant"),anyCollection(),anyString())).thenReturn(Map.of("db",Map.of("jdbcURL","jdbc:mysql://test/dictionary","password","never-return-this")));
            when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("tid","t","datasource_id","db","table_name","dictionary","table_name_cn","字典")));
            when(jdbc.queryForList(contains("FROM db_table_column_t"),any(Object[].class))).thenReturn(List.of(Map.of("table_id","t","column_name","code","data_type","VARCHAR"),Map.of("table_id","t","column_name","label","data_type","VARCHAR")));
            var json=new ObjectMapper();var spec=json.createObjectNode().put("schemaVersion","2.0");var rules=spec.putArray("rules");
            for(int i=0;i<count;i++){var rule=rules.addObject().put("ruleId","r"+i);rule.putObject("input").put("kind","FIELD").put("name","code");rule.putObject("target").put("name","label"+i);var d=rule.putArray("steps").addObject().put("operator","LOOKUP").putObject("args").putObject("definition");d.put("datasourceId","db").put("tableId","t").put("valueColumn","label");d.putArray("keyColumns").add("code");}
            try(var ignored=TenantContext.use("tenant")){var bound=new RegisteredDictionaryCatalog(jdbc,sources,metadata).bind(spec);assertEquals(1,bound.specification().path("dictionaryBindings").size());assertFalse(bound.specification().toString().contains("never-return-this"));}
            verify(sources,times(1)).resolveBatch(eq("tenant"),eq(Set.of("db")),eq("MENU:2081000000000000002"));
            verify(jdbc,times(1)).queryForList(contains("tenant_id=? AND is_del=0 AND asset_status>0"),any(Object[].class));
            verify(jdbc,times(1)).queryForList(contains("FROM db_table_column_t"),any(Object[].class));
            verifyNoInteractions(metadata);
        }
    }
    @Test void everyRequestedTableAndColumnMustBelongToAuthorizedRegistration()throws Exception{
        var jdbc=mock(JdbcTemplate.class);var sources=mock(DataSourceConnectionPropertyResolver.class);var metadata=mock(DataQualityMapper.class);
        when(sources.resolveBatch(anyString(),anyCollection(),anyString())).thenReturn(Map.of("db",Map.of("jdbcURL","jdbc:mysql://test/dictionary")));
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("tid","allowed","datasource_id","db","table_name","dictionary")));
        var json=new ObjectMapper();var spec=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree("""
            {"schemaVersion":"2.0","dictionaryBindings":{"d":{"datasourceId":"db","tableId":"foreign","keyColumns":["code"],"valueColumn":"label"}},"rules":[]}
            """);
        try(var ignored=TenantContext.use("tenant")){assertEquals("RESOURCE_FORBIDDEN",assertThrows(MappingException.class,()->new RegisteredDictionaryCatalog(jdbc,sources,metadata).bind(spec)).code());}
        verifyNoInteractions(metadata);
    }
    @Test void twentyAndHundredNodesCompileInOneAuthorizationBatch()throws Exception{
        for(int count:List.of(20,100)){
            var jdbc=mock(JdbcTemplate.class);var sources=mock(DataSourceConnectionPropertyResolver.class);var metadata=mock(DataQualityMapper.class);
            when(sources.resolveBatch(eq("tenant"),anyCollection(),anyString())).thenReturn(Map.of("db",Map.of("jdbcURL","jdbc:mysql://test/dictionary")));
            when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("tid","t","datasource_id","db","table_name","dictionary")));
            when(jdbc.queryForList(contains("FROM db_table_column_t"),any(Object[].class))).thenReturn(List.of(Map.of("table_id","t","column_name","code","data_type","VARCHAR"),Map.of("table_id","t","column_name","label","data_type","VARCHAR")));
            var json=new ObjectMapper();Map<String,com.fasterxml.jackson.databind.JsonNode> specs=new LinkedHashMap<>();
            for(int i=0;i<count;i++)specs.put("node"+i,json.readTree("""
                {"schemaVersion":"2.0","dictionaryBindings":{"same":{"datasourceId":"db","tableId":"t","keyColumns":["code"],"valueColumn":"label"}},"rules":[{"ruleId":"r","input":{"kind":"FIELD","name":"code"},"target":{"name":"label"},"steps":[{"operator":"LOOKUP","args":{"dictionaryId":"same"}}]}]}
                """));
            try(var ignored=TenantContext.use("tenant")){var service=new UnifiedMappingService(new RegisteredDictionaryCatalog(jdbc,sources,metadata),mock(FieldMappingService.class));var plans=service.compileBatch(specs);assertEquals(count,plans.size());assertEquals(1,plans.values().stream().map(value->value.plan().hash()).distinct().count());assertTrue(plans.values().stream().allMatch(value->value.plan().dictionaries().size()==1));}
            verify(sources,times(1)).resolveBatch(anyString(),anyCollection(),anyString());verify(jdbc,times(2)).queryForList(anyString(),any(Object[].class));
        }
    }
    @Test void compilingStoredDictionaryPlanIsIdempotentAfterDerivedHashesAreAttached()throws Exception{
        var jdbc=mock(JdbcTemplate.class);var sources=mock(DataSourceConnectionPropertyResolver.class);var metadata=mock(DataQualityMapper.class);
        when(sources.resolveBatch(eq("tenant"),anyCollection(),anyString())).thenReturn(Map.of("db",Map.of("jdbcURL","jdbc:mysql://test/dictionary")));
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("tid","t","datasource_id","db","table_name","dictionary")));
        when(jdbc.queryForList(contains("FROM db_table_column_t"),any(Object[].class))).thenReturn(List.of(Map.of("table_id","t","column_name","code","data_type","VARCHAR"),Map.of("table_id","t","column_name","label","data_type","VARCHAR")));
        var draft=new ObjectMapper().readTree("""
            {"schemaVersion":"2.0","dictionaryBindings":{"d":{"datasourceId":"db","tableId":"t","keyColumns":["code"],"valueColumn":"label"}},"rules":[{"ruleId":"r","input":{"kind":"FIELD","name":"code"},"target":{"name":"label"},"steps":[{"operator":"LOOKUP","args":{"dictionaryId":"d"}}]}]}
            """);
        try(var tenant=TenantContext.use("tenant")){var service=new UnifiedMappingService(new RegisteredDictionaryCatalog(jdbc,sources,metadata),new FieldMappingService());var first=service.compile(draft).plan();var second=service.compile(first.document()).plan();assertEquals(first.hash(),second.hash());assertEquals(first.document(),second.document());}
    }
    @Test void oldSingleAndSplitSqlLookupsRetainAuthorizationUntilV2Binding()throws Exception{
        var jdbc=mock(JdbcTemplate.class);var sources=mock(DataSourceConnectionPropertyResolver.class);var metadata=mock(DataQualityMapper.class);
        when(sources.resolveBatch(eq("tenant"),anyCollection(),anyString())).thenReturn(Map.of("db",Map.of("jdbcURL","jdbc:mysql://test/dictionary")));
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("tid","t","datasource_id","db","table_name","dictionary")));
        when(jdbc.queryForList(contains("FROM db_table_column_t"),any(Object[].class))).thenReturn(List.of(Map.of("table_id","t","column_name","code","data_type","VARCHAR"),Map.of("table_id","t","column_name","label","data_type","VARCHAR")));
        var json=new ObjectMapper();Map<String,com.fasterxml.jackson.databind.JsonNode> drafts=new LinkedHashMap<>();
        for(boolean multi:List.of(false,true)){var spec=json.createObjectNode().put("version","1.0");var mapping=spec.putArray("mappings").addObject().put("from","code").put("to","label");var lookup=mapping.putObject("lookup").put("sql",multi?"SELECT label FROM dictionary WHERE code IN (:codes)":"SELECT label FROM dictionary WHERE code = ?").put("resultColumn","label").put("multiValue",multi);lookup.putObject("dataSource").put("datasourceId","db");drafts.put(multi?"multi":"single",spec);}
        try(var tenant=TenantContext.use("tenant")){var plans=new UnifiedMappingService(new RegisteredDictionaryCatalog(jdbc,sources,metadata),new FieldMappingService()).compileBatch(drafts);assertEquals(2,plans.size());assertTrue(plans.values().stream().allMatch(compiled->compiled.plan().dictionaries().size()==1));assertEquals("LOOKUP_MULTI",plans.get("multi").plan().document().path("rules").get(0).path("steps").get(0).path("operator").asText());assertFalse(plans.get("single").plan().document().toString().contains("SELECT label"));}
        verify(sources,times(1)).resolveBatch(anyString(),anyCollection(),anyString());verify(jdbc,times(2)).queryForList(anyString(),any(Object[].class));
    }
}
