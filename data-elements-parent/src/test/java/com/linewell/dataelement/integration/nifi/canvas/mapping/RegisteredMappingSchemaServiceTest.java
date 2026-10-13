package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.mapping.core.MappingException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegisteredMappingSchemaServiceTest {
    private static final ObjectMapper JSON=new ObjectMapper();

    @Test void twentyAndHundredPhysicalTablesUseOneConnectionAndOneBoundedMysqlRead()throws Exception {
        for (int count:List.of(20,100)) {
            Fixture fixture=new Fixture(count);
            try (var ignored=TenantContext.use("tenant-A")) {
                var registered=fixture.service.resolve(fixture.references);
                var actual=fixture.service.physicalBatch(registered);
                assertEquals(count,actual.size());
                actual.values().forEach(fields->{assertEquals(1,fields.size());assertEquals("id",fields.get(0).path("name").asText());});
            }
            verify(fixture.sources,times(1)).resolveBatch(eq("tenant-A"),eq(Set.of("db")),anyString());
            verify(fixture.jdbc,times(1)).queryForList(contains("FROM db_table_t"),any(Object[].class));
            verify(fixture.jdbc,times(1)).queryForList(contains("FROM `db_table_column_t`"),any(Object[].class));
            verify(fixture.engine,times(1)).openRegistered(eq("db"),anyMap());
            verify(fixture.physical,times(1)).prepareStatement(contains("FROM information_schema.COLUMNS"));
            verify(fixture.statement,times(1)).executeQuery();
            verify(fixture.statement,times(count*2)).setString(anyInt(),anyString());
            verify(fixture.statement).setMaxRows(RegisteredMappingSchemaService.MAX_METADATA_SCAN_ROWS+1);
            verify(fixture.statement).setQueryTimeout(10);
            verify(fixture.metadata,never()).getColumns(any(),any(),any(),any());
            verify(fixture.physical).close();
        }
    }

    @Test void checkContractsBatchesAllNodesAndDeduplicatesRepeatedSourceTargetReferences()throws Exception {
        for (int count:List.of(20,100)) {
            Fixture fixture=new Fixture(count);
            Map<String,JsonNode> contracts=new LinkedHashMap<>();
            for (var entry:fixture.references.entrySet()) {
                var contract=JSON.createObjectNode();
                contract.set("sourceRef",entry.getValue());contract.set("targetRef",entry.getValue());
                contract.set("source",schema());contract.set("target",schema());
                contracts.put(entry.getKey(),contract);
            }
            try (var ignored=TenantContext.use("tenant-A")) { fixture.service.checkContracts(contracts); }
            verify(fixture.engine,times(1)).openRegistered(anyString(),anyMap());
            verify(fixture.statement,times(1)).executeQuery();
            verify(fixture.statement,times(count*2)).setString(anyInt(),anyString());
        }
    }

    @Test void mysqlReadsAuthorizedCatalogsTogetherAndKeepsIdenticalTableNamesSeparate()throws Exception {
        Fixture fixture=new Fixture(2);
        fixture.tables=List.of(table("table-0","db","catalog_one.shared"),table("table-1","db","catalog_two.shared"));
        fixture.physicalRows=List.of(mysqlColumn("catalog_one","shared","first"),mysqlColumn("catalog_two","shared","second"),mysqlColumn("foreign","shared","secret"));
        try (var ignored=TenantContext.use("tenant-A")) {
            var result=fixture.service.physicalBatch(fixture.service.resolve(fixture.references));
            assertEquals("first",result.get("ref-0").get(0).path("name").asText());
            assertEquals("second",result.get("ref-1").get(0).path("name").asText());
            assertFalse(result.toString().contains("secret"));
        }
        verify(fixture.statement,times(1)).executeQuery();
        verify(fixture.statement).setString(1,"catalog_one");verify(fixture.statement).setString(3,"catalog_two");
    }

    @Test void jdbcFallbackScansOneEscapedSchemaOnceForTwentyAndHundredTables()throws Exception {
        for (int count:List.of(20,100)) {
            Fixture fixture=new Fixture(count);
            when(fixture.metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
            when(fixture.physical.getSchema()).thenReturn("app_schema");
            when(fixture.metadata.getSearchStringEscape()).thenReturn("\\");
            fixture.config=Map.of("db",Map.of("defaultSchema","app_schema"));
            fixture.physicalRows=new ArrayList<>();
            for (int index=0;index<count;index++) fixture.physicalRows.add(jdbcColumn("catalog","app_schema","table_"+index,"id"));
            fixture.physicalRows.add(jdbcColumn("catalog","foreign","table_0","secret"));
            when(fixture.metadata.getColumns(eq("catalog"),eq("app\\_schema"),eq("%"),eq("%")))
                    .thenAnswer(ignored->rows(fixture.physicalRows));
            try (var ignored=TenantContext.use("tenant-A")) {
                var result=fixture.service.physicalBatch(fixture.service.resolve(fixture.references));
                assertEquals(count,result.size());assertEquals(1,result.get("ref-0").size());
            }
            verify(fixture.engine,times(1)).openRegistered(anyString(),anyMap());
            verify(fixture.metadata,times(1)).getColumns(any(),any(),any(),any());
            verify(fixture.physical,never()).prepareStatement(anyString());
        }
    }

    @Test void genericJdbcCrossSchemaIsRejectedWithoutPerTableFallback()throws Exception {
        Fixture fixture=new Fixture(2);
        when(fixture.metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        fixture.tables=List.of(table("table-0","db","one.shared"),table("table-1","db","two.shared"));
        try (var ignored=TenantContext.use("tenant-A")) {
            var registered=fixture.service.resolve(fixture.references);
            assertEquals("UNSUPPORTED_SCHEMA_BATCH",assertThrows(MappingException.class,()->fixture.service.physicalBatch(registered)).code());
        }
        verify(fixture.engine,times(1)).openRegistered(anyString(),anyMap());
        verify(fixture.metadata,never()).getColumns(any(),any(),any(),any());
        verify(fixture.physical,never()).prepareStatement(anyString());
    }

    @Test void registeredMissingOrForeignTableRejectsWholeBatchBeforeColumnOrPhysicalReads()throws Exception {
        for (String deniedId:List.of("foreign-tenant-table","missing-table","deleted-table")) {
            Fixture fixture=new Fixture(100);
            ((com.fasterxml.jackson.databind.node.ObjectNode)fixture.references.get("ref-99")).put("tableId",deniedId).put("tenantId","tenant-B");
            try (var ignored=TenantContext.use("tenant-A")) {
                assertEquals("RESOURCE_FORBIDDEN",assertThrows(MappingException.class,()->fixture.service.resolve(fixture.references)).code());
            }
            verify(fixture.sources).resolveBatch(eq("tenant-A"),anyCollection(),anyString());
            verify(fixture.jdbc,never()).queryForList(contains("db_table_column_t"),any(Object[].class));
            verifyNoInteractions(fixture.engine);
        }
    }

    @Test void realRoleResolverRejectsMixedAuthorizedDatasourceIdsBeforeTableOrPhysicalReads()throws Exception {
        Fixture fixture=new Fixture(100);
        var scopes=mock(DataScopeAuthorizationService.class);
        var sources=new DataSourceConnectionPropertyResolver(fixture.jdbc,JSON,scopes);
        var service=new RegisteredMappingSchemaService(fixture.jdbc,sources,fixture.engine);
        when(fixture.jdbc.queryForList(contains("FROM db_datasource_t"),any(Object[].class))).thenReturn(List.of(
                Map.of("tid","db","jdbc_url","jdbc:mysql://test/allowed"),Map.of("tid","denied-db","jdbc_url","jdbc:mysql://test/denied")));
        when(scopes.visibleDataSources(eq("MENU:2081000000000000002"),anyList())).thenAnswer(invocation->
                ((List<Map<String,Object>>)invocation.getArgument(1)).stream().filter(row->row.get("tid").equals("db")).toList());
        ((com.fasterxml.jackson.databind.node.ObjectNode)fixture.references.get("ref-99")).put("datasourceId","denied-db");
        try (var ignored=TenantContext.use("tenant-A")) { assertThrows(IllegalStateException.class,()->service.resolve(fixture.references)); }
        verify(scopes,times(1)).visibleDataSources(anyString(),anyList());
        verify(fixture.jdbc,never()).queryForList(contains("FROM db_table_t"),any(Object[].class));
        verifyNoInteractions(fixture.engine);
    }

    @Test void changingRealTenantCannotReuseAnotherTenantsTableEvenWithSameDatasourceId()throws Exception {
        Fixture fixture=new Fixture(1);
        var scopes=mock(DataScopeAuthorizationService.class);
        var sources=new DataSourceConnectionPropertyResolver(fixture.jdbc,JSON,scopes);
        var service=new RegisteredMappingSchemaService(fixture.jdbc,sources,fixture.engine);
        when(fixture.jdbc.queryForList(contains("FROM db_datasource_t"),any(Object[].class))).thenReturn(List.of(Map.of("tid","db","jdbc_url","jdbc:mysql://test/registered")));
        when(scopes.visibleDataSources(anyString(),anyList())).thenAnswer(invocation->invocation.getArgument(1));
        when(fixture.jdbc.queryForList(contains("FROM db_table_t"),any(Object[].class))).thenAnswer(invocation->{
            Object[] args=(Object[])invocation.getRawArguments()[1];
            assertEquals(TenantContext.requireTenantId(),args[args.length-1]);
            return TenantContext.requireTenantId().equals("tenant-A")?fixture.tables:List.of(table("table-B","db","different_table"));
        });
        try (var ignored=TenantContext.use("tenant-A")) { assertEquals(1,service.resolve(fixture.references).size()); }
        try (var ignored=TenantContext.use("tenant-B")) { assertEquals("RESOURCE_FORBIDDEN",assertThrows(MappingException.class,()->service.resolve(fixture.references)).code()); }
        verifyNoInteractions(fixture.engine);
        verify(scopes,times(2)).visibleDataSources(anyString(),anyList());
    }

    @Test void missingPhysicalTableAndFieldBudgetsFailClosedWithoutAdditionalQueries()throws Exception {
        Fixture missing=new Fixture(2);missing.physicalRows=List.of(mysqlColumn("registered","table_0","id"));
        try (var ignored=TenantContext.use("tenant-A")) {
            var registered=missing.service.resolve(missing.references);
            assertEquals("SCHEMA_UNAVAILABLE",assertThrows(MappingException.class,()->missing.service.physicalBatch(registered)).code());
        }
        Fixture tooWide=new Fixture(1);tooWide.physicalRows=new ArrayList<>();
        for(int column=0;column<=2000;column++)tooWide.physicalRows.add(mysqlColumn("registered","table_0","c"+column));
        try (var ignored=TenantContext.use("tenant-A")) {
            var registered=tooWide.service.resolve(tooWide.references);
            assertEquals("BUDGET_EXCEEDED",assertThrows(MappingException.class,()->tooWide.service.physicalBatch(registered)).code());
        }
        verify(missing.statement,times(1)).executeQuery();verify(tooWide.statement,times(1)).executeQuery();
    }

    @Test void requestAndRegisteredFieldBudgetsAreCheckedBeforePhysicalReads()throws Exception {
        Fixture references=new Fixture(101);
        try (var ignored=TenantContext.use("tenant-A")) { assertEquals("BUDGET_EXCEEDED",assertThrows(MappingException.class,()->references.service.resolve(references.references)).code()); }
        verifyNoInteractions(references.sources,references.engine);
        Fixture columns=new Fixture(100);columns.columns=new ArrayList<>();
        for(int index=0;index<20_001;index++)columns.columns.add(column("table-"+(index%100),"c"+index));
        try (var ignored=TenantContext.use("tenant-A")) { assertEquals("BUDGET_EXCEEDED",assertThrows(MappingException.class,()->columns.service.resolve(columns.references)).code()); }
        verifyNoInteractions(columns.engine);
    }

    @Test void ninthDatasourceIsRejectedBeforeAnyResourceLookup()throws Exception {
        Fixture fixture=new Fixture(9);
        for(int index=0;index<9;index++)((com.fasterxml.jackson.databind.node.ObjectNode)fixture.references.get("ref-"+index)).put("datasourceId","db-"+index);
        try (var ignored=TenantContext.use("tenant-A")) {
            assertEquals("RESOURCE_FORBIDDEN",assertThrows(MappingException.class,()->fixture.service.resolve(fixture.references)).code());
        }
        verifyNoInteractions(fixture.sources,fixture.engine);
    }

    @Test void jdbcFallbackCountsUnrequestedColumnsAgainstScanBudget()throws Exception {
        Fixture fixture=new Fixture(1);
        when(fixture.metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        fixture.config=Map.of("db",Map.of("defaultSchema","registered"));
        when(fixture.metadata.getSearchStringEscape()).thenReturn("\\");
        ResultSet oversized=mock(ResultSet.class);AtomicInteger scanned=new AtomicInteger();
        when(oversized.next()).thenAnswer(ignored->scanned.incrementAndGet()<=20_001);
        when(oversized.getString("TABLE_CAT")).thenReturn("catalog");when(oversized.getString("TABLE_SCHEM")).thenReturn("registered");when(oversized.getString("TABLE_NAME")).thenReturn("not_requested");
        when(fixture.metadata.getColumns(any(),any(),any(),any())).thenReturn(oversized);
        try (var ignored=TenantContext.use("tenant-A")) {
            var registered=fixture.service.resolve(fixture.references);
            assertEquals("BUDGET_EXCEEDED",assertThrows(MappingException.class,()->fixture.service.physicalBatch(registered)).code());
        }
        assertEquals(20_001,scanned.get());verify(oversized).close();
        verify(fixture.metadata,times(1)).getColumns(any(),any(),any(),any());
    }

    private static ArrayNode schema() {
        var schema=JSON.createArrayNode();schema.addObject().put("name","id").put("logicalType","STRING").put("nullable",true).put("length",64);return schema;
    }
    private static Map<String,Object> table(String id,String datasource,String name){return Map.of("tid",id,"datasource_id",datasource,"table_name",name);}
    private static Map<String,Object> column(String table,String name){return Map.of("table_id",table,"column_name",name,"data_type","VARCHAR","length",64,"nullable",1);}
    private static Map<String,Object> mysqlColumn(String schema,String table,String name){return Map.of("TABLE_SCHEMA",schema,"TABLE_NAME",table,"COLUMN_NAME",name,"TYPE_NAME","varchar","COLUMN_SIZE",64,"IS_NULLABLE","YES","EXTRA","");}
    private static Map<String,Object> jdbcColumn(String catalog,String schema,String table,String name){return Map.of("TABLE_CAT",catalog,"TABLE_SCHEM",schema,"TABLE_NAME",table,"COLUMN_NAME",name,"TYPE_NAME","varchar","COLUMN_SIZE",64,"NULLABLE",DatabaseMetaData.columnNullable,"IS_AUTOINCREMENT","NO");}

    private static ResultSet rows(List<Map<String,Object>> rows)throws SQLException {
        ResultSet result=mock(ResultSet.class);AtomicInteger cursor=new AtomicInteger(-1);
        when(result.next()).thenAnswer(ignored->cursor.incrementAndGet()<rows.size());
        when(result.getString(anyString())).thenAnswer(invocation->{Object value=rows.get(cursor.get()).get(invocation.getArgument(0));return value==null?null:value.toString();});
        when(result.getInt(anyString())).thenAnswer(invocation->{Object value=rows.get(cursor.get()).get(invocation.getArgument(0));return value==null?0:((Number)value).intValue();});
        return result;
    }

    private static final class Fixture {
        final JdbcTemplate jdbc=mock(JdbcTemplate.class);
        final DataSourceConnectionPropertyResolver sources=mock(DataSourceConnectionPropertyResolver.class);
        final UnifiedMappingService engine=mock(UnifiedMappingService.class);
        final Connection physical=mock(Connection.class);
        final DatabaseMetaData metadata=mock(DatabaseMetaData.class);
        final PreparedStatement statement=mock(PreparedStatement.class);
        final RegisteredMappingSchemaService service;
        final Map<String,JsonNode> references=new LinkedHashMap<>();
        Map<String,Map<String,Object>> config=Map.of("db",Map.of("jdbcURL","jdbc:mysql://test/registered"));
        List<Map<String,Object>> tables=new ArrayList<>(),columns=new ArrayList<>(),physicalRows=new ArrayList<>();

        Fixture(int count)throws Exception {
            Connection platform=mock(Connection.class);DatabaseMetaData platformMetadata=mock(DatabaseMetaData.class);
            when(platform.getMetaData()).thenReturn(platformMetadata);when(platformMetadata.getURL()).thenReturn("jdbc:mysql://test/platform");when(platformMetadata.getIdentifierQuoteString()).thenReturn("`");
            when(jdbc.execute(any(ConnectionCallback.class))).thenAnswer(invocation->((ConnectionCallback<?>)invocation.getArgument(0)).doInConnection(platform));
            when(sources.resolveBatch(anyString(),anyCollection(),anyString())).thenAnswer(ignored->config);
            when(jdbc.queryForList(contains("FROM db_table_t"),any(Object[].class))).thenAnswer(ignored->tables);
            when(jdbc.queryForList(contains("db_table_column_t"),any(Object[].class))).thenAnswer(ignored->columns);
            when(engine.openRegistered(anyString(),anyMap())).thenReturn(physical);
            when(physical.getMetaData()).thenReturn(metadata);when(physical.getCatalog()).thenReturn("catalog");when(metadata.getDatabaseProductName()).thenReturn("MySQL");
            when(physical.prepareStatement(anyString())).thenReturn(statement);when(statement.executeQuery()).thenAnswer(ignored->rows(physicalRows));
            for(int index=0;index<count;index++){
                references.put("ref-"+index,JSON.createObjectNode().put("datasourceId","db").put("tableId","table-"+index));
                tables.add(table("table-"+index,"db","table_"+index));columns.add(column("table-"+index,"id"));physicalRows.add(mysqlColumn("registered","table_"+index,"id"));
            }
            service=new RegisteredMappingSchemaService(jdbc,sources,engine);
        }
    }
}
