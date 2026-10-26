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
    @Test void probeSchemaAliasesAndEmptyValuesCannotBlockMapping()throws Exception {
        for(String key:List.of("schema","currentSchema"))for(Object empty:Arrays.asList(null,"","  ")) {
            Fixture f=new Fixture(1);var config=new HashMap<String,Object>();config.put("defaultSchema",empty);config.put(key,"\"Business\"");
            f.config=Map.of("db",config);when(f.metadata.getDatabaseProductName()).thenReturn("Oracle");
            when(f.metadata.getColumns(any(),any(),any(),any())).thenAnswer(call->rows(List.of(jdbcColumn("catalog","Business","table_0","id"))));
            try(var tenant=TenantContext.use("tenant-A")) {
                var registered=f.service.resolve(f.references);assertEquals("Business",registered.get("ref-0").schema());
                assertEquals(1,f.service.physicalBatch(registered).get("ref-0").size());
            }
            verify(f.physical,never()).getSchema();verify(f.physical,never()).prepareStatement(anyString());
        }
    }
    @Test void oceanbaseOracleUsesRegisteredModeAndOwnerOnceForTwentyAndHundredTables()throws Exception {
        for(int count:List.of(20,100))for(var config:List.of(
                Map.<String,Object>of("dbType","oceanbaseoracle","username","app@tenant#cluster"),
                Map.<String,Object>of("dbType","oceanbase","compatibleMode","ORACLE","username","app@tenant#cluster"),
                Map.<String,Object>of("jdbcURL","jdbc:oceanbase:oracle://test/service","dbUser","app@tenant#cluster"))) {
            Fixture f=new Fixture(count);f.config=Map.of("db",config);when(f.metadata.getDatabaseProductName()).thenReturn("OceanBase");
            when(f.physical.getSchema()).thenThrow(new AbstractMethodError("getSchema"));
            when(f.physical.prepareStatement(anyString())).thenThrow(new SQLFeatureNotSupportedException());
            var fields=new ArrayList<Map<String,Object>>();for(int i=0;i<count;i++)fields.add(jdbcColumn("catalog","APP","table_"+i,"id"));
            fields.add(jdbcColumn("catalog","FOREIGN","table_0","secret"));
            when(f.metadata.getColumns(any(),any(),any(),any())).thenAnswer(call->rows(fields));
            try(var tenant=TenantContext.use("tenant-A")) {
                var actual=f.service.physicalBatch(f.service.resolve(f.references));assertEquals(count,actual.size());assertFalse(actual.toString().contains("secret"));
            }
            verify(f.sources,times(1)).resolveBatch(eq("tenant-A"),eq(Set.of("db")),anyString());
            verify(f.jdbc,times(2)).queryForList(anyString(),any(Object[].class));verify(f.engine,times(1)).openRegistered(anyString(),anyMap());
            verify(f.physical,times(1)).getSchema();verify(f.metadata,times(1)).getColumns(eq("catalog"),eq("APP"),eq("%"),eq("%"));
            verify(f.physical,times(1)).prepareStatement(anyString());
        }
    }
    @Test void oldOracleDriverResolvesTheActualSessionSchemaBeforeTheLoginOwner()throws Exception {
        for(int count:List.of(20,100)) {
            Fixture f=new Fixture(count);f.config=Map.of("db",Map.of("dbType","ORACLE","username","LOGIN_USER"));
            when(f.metadata.getDatabaseProductName()).thenReturn("Oracle");when(f.metadata.getUserName()).thenReturn("LOGIN_USER");
            when(f.physical.getSchema()).thenThrow(new SQLFeatureNotSupportedException());
            var context=mock(PreparedStatement.class);var current=mock(ResultSet.class);
            when(f.physical.prepareStatement("SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') FROM DUAL")).thenReturn(context);
            when(context.executeQuery()).thenReturn(current);when(current.next()).thenReturn(true,false);when(current.getString(1)).thenReturn("BUSINESS");
            var fields=new ArrayList<Map<String,Object>>();for(int i=0;i<count;i++)fields.add(jdbcColumn("catalog","BUSINESS","table_"+i,"id"));
            when(f.metadata.getColumns(any(),any(),any(),any())).thenAnswer(call->rows(fields));
            try(var tenant=TenantContext.use("tenant-A")){assertEquals(count,f.service.physicalBatch(f.service.resolve(f.references)).size());}
            verify(context,times(1)).executeQuery();verify(context).setMaxRows(1);verify(context).setQueryTimeout(10);verify(context).close();verify(current).close();
            verify(f.metadata,times(1)).getColumns(eq("catalog"),eq("BUSINESS"),eq("%"),eq("%"));verify(f.metadata,never()).getUserName();
        }
    }
    @Test void kingbaseCanOmitTableCatalogWithoutLosingAuthorizedFieldsOrAddingQueries()throws Exception {
        for(int count:List.of(20,100))for(String absent:Arrays.asList(null,"")) {
            Fixture fixture=new Fixture(count);fixture.config=Map.of("db",Map.of("dbType","KINGBASE","defaultSchema","public"));
            when(fixture.metadata.getDatabaseProductName()).thenReturn("KingbaseES");
            when(fixture.metadata.supportsCatalogsInTableDefinitions()).thenReturn(false);
            when(fixture.metadata.getSearchStringEscape()).thenReturn("\\");
            List<Map<String,Object>> physical=new ArrayList<>();
            for(int i=0;i<count;i++){var row=new HashMap<>(jdbcColumn("catalog","public","table_"+i,"id"));row.put("TABLE_CAT",absent);physical.add(row);}
            physical.add(jdbcColumn("foreign-catalog","public","table_0","foreign_catalog_secret"));
            var foreignSchema=new HashMap<>(jdbcColumn("catalog","foreign-schema","table_0","foreign_schema_secret"));foreignSchema.put("TABLE_CAT",absent);physical.add(foreignSchema);
            when(fixture.metadata.getColumns(any(),any(),any(),any())).thenAnswer(call->rows(physical));
            try(var tenant=TenantContext.use("tenant-A")) {
                var result=fixture.service.physicalBatch(fixture.service.resolve(fixture.references));
                assertEquals(count,result.size());assertEquals(1,result.get("ref-0").size());assertFalse(result.toString().contains("secret"));
            }
            verify(fixture.sources,times(1)).resolveBatch(eq("tenant-A"),eq(Set.of("db")),anyString());
            verify(fixture.jdbc,times(2)).queryForList(anyString(),any(Object[].class));
            verify(fixture.engine,times(1)).openRegistered(anyString(),anyMap());
            verify(fixture.metadata,times(1)).getColumns(eq("catalog"),eq("public"),eq("%"),eq("%"));
            verify(fixture.physical,never()).prepareStatement(anyString());
        }
    }
    @Test void catalogAwareDriversCannotUseMissingCatalogToAcceptAnotherDatabase()throws Exception {
        Fixture fixture=new Fixture(1);fixture.config=Map.of("db",Map.of("defaultSchema","registered"));
        when(fixture.metadata.getDatabaseProductName()).thenReturn("Microsoft SQL Server");
        when(fixture.metadata.supportsCatalogsInTableDefinitions()).thenReturn(true);
        when(fixture.metadata.getSearchStringEscape()).thenReturn("\\");
        var row=new HashMap<>(jdbcColumn("catalog","registered","table_0","id"));row.remove("TABLE_CAT");
        when(fixture.metadata.getColumns(any(),any(),any(),any())).thenAnswer(call->rows(List.of(row)));
        try(var tenant=TenantContext.use("tenant-A")) {
            var error=assertThrows(MappingException.class,()->fixture.service.physicalBatch(fixture.service.resolve(fixture.references)));
            assertEquals("SCHEMA_UNAVAILABLE",error.code());assertTrue(error.getMessage().contains("registered.table_0"));assertTrue(error.getMessage().contains("数据源 db"));
        }
    }
    @Test void optionalCatalogAccessorAndCapabilityDoNotBreakSchemaOnlyDrivers()throws Exception {
        Fixture fixture=new Fixture(1);fixture.config=Map.of("db",Map.of("defaultSchema","registered"));
        when(fixture.metadata.getDatabaseProductName()).thenReturn("Oracle");
        when(fixture.physical.getCatalog()).thenThrow(new AbstractMethodError("getCatalog"));
        when(fixture.metadata.supportsCatalogsInTableDefinitions()).thenThrow(new SQLFeatureNotSupportedException());
        when(fixture.metadata.getSearchStringEscape()).thenReturn("\\");
        var row=new HashMap<>(jdbcColumn("catalog","registered","table_0","id"));row.remove("TABLE_CAT");
        when(fixture.metadata.getColumns(any(),any(),any(),any())).thenAnswer(call->rows(List.of(row)));
        try(var tenant=TenantContext.use("tenant-A")){assertEquals(1,fixture.service.physicalBatch(fixture.service.resolve(fixture.references)).get("ref-0").size());}
        verify(fixture.metadata,times(1)).getColumns(isNull(),eq("registered"),eq("table\\_0"),eq("%"));
    }
    @Test void registeredFormatSurvivesPhysicalOracleDateAndExplicitClearingWins()throws Exception {
        for(int count:List.of(20,100)) {
            Fixture fixture=new Fixture(count);fixture.config=Map.of("db",Map.of("dbType","ORACLE","defaultSchema","registered"));
            when(fixture.metadata.getDatabaseProductName()).thenReturn("Oracle");when(fixture.metadata.getSearchStringEscape()).thenReturn("\\");
            var physical=new ArrayList<Map<String,Object>>();
            for(int i=0;i<count;i++) {
                var table=new HashMap<>(fixture.tables.get(i));table.put("field_governance_config","{\"fields\":[{\"columnName\":\"id\",\"standardField\":\"DATE\",\"standardFieldExplicitlyCleared\":"+(i%2==1)+"}]}");fixture.tables.set(i,table);
                var column=new HashMap<>(fixture.columns.get(i));column.put("data_type","DATE");column.put("data_standard_id","DATETIME");fixture.columns.set(i,column);
                var row=new HashMap<>(jdbcColumn("catalog","registered","table_"+i,"id"));row.put("TYPE_NAME","DATE");physical.add(row);
            }
            when(fixture.metadata.getColumns(any(),any(),any(),any())).thenAnswer(call->rows(physical));
            try(var tenant=TenantContext.use("tenant-A")) {
                var schemas=fixture.service.physicalBatch(fixture.service.resolve(fixture.references));
                for(int i=0;i<count;i++){var field=schemas.get("ref-"+i).get(0);assertEquals("DATETIME",field.path("logicalType").asText());assertTrue(field.path("dateContainsTime").asBoolean());if(i%2==0)assertEquals("DATE",field.path("registeredFormat").asText());else assertFalse(field.has("registeredFormat"));}
            }
            verify(fixture.jdbc,times(2)).queryForList(anyString(),any(Object[].class));verify(fixture.metadata,times(1)).getColumns(any(),any(),any(),any());
        }
    }
    @Test void oldOracleDriverGetSchemaDoesNotBreakExplicitOrDefaultSchemaBatches()throws Exception {
        for(int count:List.of(20,100))for(boolean explicit:List.of(true,false)) {
            Fixture fixture=new Fixture(count);
            fixture.config=Map.of("db",explicit?Map.of("dbType","ORACLE","defaultSchema","registered"):Map.of("dbType","ORACLE"));
            when(fixture.metadata.getDatabaseProductName()).thenReturn("Oracle");
            when(fixture.metadata.getUserName()).thenReturn("registered");
            when(fixture.physical.getSchema()).thenThrow(new AbstractMethodError("Unimplemented method: getSchema()"));
            when(fixture.metadata.getSearchStringEscape()).thenReturn("\\");
            var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<count;i++)rows.add(jdbcColumn("catalog","registered","table_"+i,"id"));
            when(fixture.metadata.getColumns(any(),any(),any(),any())).thenAnswer(call->rows(rows));
            try(var tenant=TenantContext.use("tenant-A")){assertEquals(count,fixture.service.physicalBatch(fixture.service.resolve(fixture.references)).size());}
            verify(fixture.physical,times(explicit?0:1)).getSchema();
            verify(fixture.metadata,times(1)).getColumns(any(),any(),any(),any());
            verify(fixture.engine,times(1)).openRegistered(anyString(),anyMap());
        }
    }
    @Test void oracleDateMetadataIsDatetimeAndOnlyKnownDateContractIsRepaired()throws Exception {
        Fixture fixture=new Fixture(1);fixture.config=Map.of("db",Map.of("dbType","ORACLE","defaultSchema","registered"));
        fixture.columns=List.of(Map.of("table_id","table-0","column_name","id","data_type","DATE","nullable",1));
        Map<String,Object> row=new HashMap<>(jdbcColumn("catalog","registered","table_0","id"));row.put("TYPE_NAME","DATE");
        when(fixture.metadata.getDatabaseProductName()).thenReturn("Oracle");when(fixture.metadata.getSearchStringEscape()).thenReturn("\\");when(fixture.metadata.getColumns(any(),any(),any(),any())).thenAnswer(call->rows(List.of(row)));
        var contract=JSON.createObjectNode();contract.set("sourceRef",fixture.references.get("ref-0"));contract.putArray("source").addObject().put("name","id").put("logicalType","DATE").put("nullable",true);
        try(var ignored=TenantContext.use("tenant-A")){
            var fields=fixture.service.resolve(fixture.references).get("ref-0").fields();assertEquals("DATETIME",fields.get(0).path("logicalType").asText());
            fixture.service.checkContracts(Map.of("node",contract));assertEquals("DATETIME",contract.path("source").get(0).path("logicalType").asText());assertTrue(contract.path("source").get(0).path("dateContainsTime").asBoolean());
            ((com.fasterxml.jackson.databind.node.ObjectNode)contract.path("source").get(0)).put("logicalType","STRING");
            assertEquals("SCHEMA_DRIFT",assertThrows(MappingException.class,()->fixture.service.checkContracts(Map.of("node",contract))).code());
        }
    }

    @Test void dictionaryTablePagesTwentyAndHundredUseFixedCountAndPageReadsAndTenantArguments()throws Exception {
        for(int count:List.of(20,100)) {
            Fixture fixture=new Fixture(count);
            when(fixture.jdbc.queryForObject(contains("SELECT COUNT(*)"),eq(Long.class),any(Object[].class))).thenReturn((long)count);
            when(fixture.jdbc.queryForList(contains("ROW_NUMBER()"),any(Object[].class))).thenAnswer(invocation->{
                String sql=invocation.getArgument(0);Object[] args=(Object[])invocation.getRawArguments()[1];
                assertTrue(sql.contains("tenant_id=?"));assertTrue(sql.contains("is_del=0"));assertEquals("db",args[0]);assertEquals("tenant-A",args[1]);
                assertEquals("%!%!_%",args[2]);assertEquals(1L,args[4]);assertEquals((long)count,args[5]);return fixture.tables;
            });
            try(var ignored=TenantContext.use("tenant-A")){var response=fixture.service.dictionaryTables("db","%_",1,count);assertEquals(count,((List<?>)response.get("rows")).size());}
            verify(fixture.sources,times(1)).resolveBatch(eq("tenant-A"),eq(Set.of("db")),eq("MENU:2081000000000000002"));
            verify(fixture.jdbc,times(1)).queryForObject(contains("SELECT COUNT(*)"),eq(Long.class),any(Object[].class));
            verify(fixture.jdbc,times(1)).queryForList(contains("ROW_NUMBER()"),any(Object[].class));
            verifyNoInteractions(fixture.engine);
        }
    }

    @Test void deniedDictionaryDatasourceStopsBeforeAnyTableReadAndForeignTableCannotReadColumns()throws Exception {
        Fixture fixture=new Fixture(1);fixture.config=Map.of();
        try(var ignored=TenantContext.use("tenant-A")){assertEquals("RESOURCE_FORBIDDEN",assertThrows(MappingException.class,()->fixture.service.dictionaryTables("denied","",1,20)).code());}
        verify(fixture.jdbc,never()).queryForList(anyString(),any(Object[].class));
        verify(fixture.jdbc,never()).queryForObject(anyString(),eq(Long.class),any(Object[].class));
        Fixture foreign=new Fixture(1);
        try(var ignored=TenantContext.use("tenant-A")){assertEquals("RESOURCE_FORBIDDEN",assertThrows(MappingException.class,()->foreign.service.dictionaryColumns("db","other-tenant-table")).code());}
        verify(foreign.jdbc,never()).queryForList(contains("db_table_column_t"),any(Object[].class));
        verifyNoInteractions(foreign.engine);
    }

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

    @Test void mysqlCaseInsensitivePhysicalNamesRespectTheDriverAndStillUseOneBatch()throws Exception {
        for(int count:List.of(20,100)) {
            Fixture f=new Fixture(count);when(f.metadata.supportsMixedCaseIdentifiers()).thenReturn(false);
            f.tables=new ArrayList<>();for(int i=0;i<count;i++)f.tables.add(table("table-"+i,"db","REGISTERED.TABLE_"+i));
            f.physicalRows.add(mysqlColumn("foreign","table_0","secret"));
            try(var ignored=TenantContext.use("tenant-A")) {
                var actual=f.service.physicalBatch(f.service.resolve(f.references));
                assertEquals(count,actual.size());assertFalse(actual.toString().contains("secret"));
            }
            verify(f.statement,times(1)).executeQuery();verify(f.engine,times(1)).openRegistered(eq("db"),anyMap());
            verify(f.metadata,times(1)).supportsMixedCaseIdentifiers();
        }
        Fixture exact=new Fixture(1);when(exact.metadata.supportsMixedCaseIdentifiers()).thenReturn(true);
        exact.tables=List.of(table("table-0","db","REGISTERED.TABLE_0"));
        try(var ignored=TenantContext.use("tenant-A")){assertEquals("SCHEMA_UNAVAILABLE",assertThrows(MappingException.class,()->exact.service.physicalBatch(exact.service.resolve(exact.references))).code());}
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

    @Test void realConnectionResolverRejectsTableDatasourceMismatchBeforePhysicalReads()throws Exception {
        Fixture fixture=new Fixture(100);
        var scopes=mock(DataScopeAuthorizationService.class);
        var sources=new DataSourceConnectionPropertyResolver(fixture.jdbc,JSON,scopes);
        var service=new RegisteredMappingSchemaService(fixture.jdbc,sources,fixture.engine);
        when(fixture.jdbc.queryForList(contains("FROM db_datasource_t"),any(Object[].class))).thenReturn(List.of(
                Map.of("tid","db","jdbc_url","jdbc:mysql://test/allowed"),Map.of("tid","denied-db","jdbc_url","jdbc:mysql://test/denied")));
        when(scopes.visibleDataSources(eq("MENU:2081000000000000002"),anyList())).thenAnswer(invocation->
                ((List<Map<String,Object>>)invocation.getArgument(1)).stream().filter(row->row.get("tid").equals("db")).toList());
        ((com.fasterxml.jackson.databind.node.ObjectNode)fixture.references.get("ref-99")).put("datasourceId","denied-db");
        try (var ignored=TenantContext.use("tenant-A")) { assertEquals("RESOURCE_FORBIDDEN",assertThrows(MappingException.class,()->service.resolve(fixture.references)).code()); }
        verifyNoInteractions(scopes);
        verify(fixture.jdbc,times(1)).queryForList(contains("FROM db_table_t"),any(Object[].class));
        verify(fixture.jdbc,never()).queryForList(contains("db_table_column_t"),any(Object[].class));
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
        verifyNoInteractions(scopes);
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

    @Test void wideOracleAndPostgresTablesReadOnlyTheBoundNamesAndNeverScanTheSchema()throws Exception {
        for(String url:List.of("jdbc:oracle:thin:@test","jdbc:postgresql://test/catalog","jdbc:dm://test:5236","jdbc:sqlserver://test","jdbc:db2://test/catalog"))for(int width:List.of(200,240)) {
            Fixture f=new Fixture(1);f.config=Map.of("db",Map.of("defaultSchema","registered"));
            when(f.metadata.getDatabaseProductName()).thenReturn(url.contains("oracle")?"Oracle":"Other");when(f.metadata.getURL()).thenReturn(url);
            f.physicalRows.clear();for(int i=0;i<width;i++)f.physicalRows.add(jdbcColumn("catalog","registered","table_0","f"+i));
            try(var tenant=TenantContext.use("tenant-A")){assertEquals(width,f.service.physicalBatch(f.service.resolve(f.references)).get("ref-0").size());}
            verify(f.metadata,never()).getColumns(any(),any(),any(),any());verify(f.statement,times(1)).executeQuery();
            verify(f.statement).setString(1,"registered");verify(f.statement).setString(2,"table_0");
            verify(f.physical).prepareStatement(argThat(sql->sql.contains("=?")&&!sql.contains("table_0")&&!sql.contains("TABLE_SCHEM LIKE")));
        }
    }

    @Test void catalogTwentyAndHundredTablesUseOneReadAndRejectOutOfScopeMetadata()throws Exception {
        for(int count:List.of(20,100)) {
            Fixture f=new Fixture(count);f.config=Map.of("db",Map.of("defaultSchema","registered"));when(f.metadata.getDatabaseProductName()).thenReturn("Oracle");when(f.metadata.getURL()).thenReturn("jdbc:oracle:thin:@test");
            f.physicalRows.clear();for(int i=0;i<count;i++)f.physicalRows.add(jdbcColumn("catalog","registered","table_"+i,"id"));
            try(var tenant=TenantContext.use("tenant-A")){assertEquals(count,f.service.physicalBatch(f.service.resolve(f.references)).size());}
            verify(f.statement,times(1)).executeQuery();verify(f.jdbc,times(2)).queryForList(anyString(),any(Object[].class));verify(f.sources,times(1)).resolveBatch(anyString(),anyCollection(),anyString());
        }
        Fixture f=new Fixture(1);f.config=Map.of("db",Map.of("defaultSchema","registered"));when(f.metadata.getDatabaseProductName()).thenReturn("Oracle");when(f.metadata.getURL()).thenReturn("jdbc:oracle:thin:@test");f.physicalRows=List.of(jdbcColumn("catalog","foreign_schema","table_0","secret"));
        try(var tenant=TenantContext.use("tenant-A")){assertEquals("SCHEMA_RESULT_MISMATCH",assertThrows(MappingException.class,()->f.service.physicalBatch(f.service.resolve(f.references))).code());}
    }

    private static ArrayNode schema() {
        var schema=JSON.createArrayNode();schema.addObject().put("name","id").put("logicalType","STRING").put("nullable",true).put("length",64);return schema;
    }
    @Test void authoritativeCommentsTravelWithTwentyAndHundredContractsWithoutExtraQueries() throws Exception {
        for(int count:List.of(20,100)) {
            Fixture fixture=new Fixture(count);
            fixture.columns.replaceAll(column->{var value=new HashMap<>(column);value.put("column_comment","登记业务标识");return value;});
            Map<String,JsonNode> contracts=new LinkedHashMap<>();
            fixture.references.forEach((key,reference)->{
                var contract=JSON.createObjectNode();contract.set("sourceRef",reference);contract.set("targetRef",reference);
                var source=schema();((com.fasterxml.jackson.databind.node.ObjectNode)source.get(0)).put("comment","过期注释");
                contract.set("source",source);contract.set("target",schema());contracts.put(key,contract);
            });
            try(var tenant=TenantContext.use("tenant-A")) {fixture.service.checkContracts(contracts);}
            contracts.values().forEach(contract->{assertEquals("登记业务标识",contract.path("source").get(0).path("comment").asText());assertEquals("登记业务标识",contract.path("target").get(0).path("comment").asText());});
            verify(fixture.jdbc,times(2)).queryForList(anyString(),any(Object[].class));
            verify(fixture.jdbc).queryForList(argThat(sql->sql.contains("column_comment")&&sql.contains("tenant_id")),any(Object[].class));
            verify(fixture.sources).resolveBatch(eq("tenant-A"),eq(Set.of("db")),anyString());
            verify(fixture.statement,times(1)).executeQuery();
        }
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
            when(metadata.getSearchStringEscape()).thenReturn("\\");
            when(physical.prepareStatement(anyString())).thenReturn(statement);when(statement.executeQuery()).thenAnswer(ignored->rows(physicalRows));
            for(int index=0;index<count;index++){
                references.put("ref-"+index,JSON.createObjectNode().put("datasourceId","db").put("tableId","table-"+index));
                tables.add(table("table-"+index,"db","table_"+index));columns.add(column("table-"+index,"id"));physicalRows.add(mysqlColumn("registered","table_"+index,"id"));
            }
            service=new RegisteredMappingSchemaService(jdbc,sources,engine);
        }
    }
}
