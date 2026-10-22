package com.linewell.dataelement.metautil.ddl;

import com.linewell.dataelement.metautil.model.dto.*;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.io.StringReader;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SourcePartitionDefinitionTest {
    @Test void unsupportedClobStreamingFallsBackToTextInTheSameQuery() throws Exception {
        for(var failure:List.of(new SQLFeatureNotSupportedException(),new SQLException("driver feature", "0A000",17023))) {
            var connection=mock(Connection.class);var statement=nativeStatement("ignored");
            var result=statement.executeQuery();
            when(result.getCharacterStream(1)).thenThrow(failure);
            when(result.getString(1)).thenReturn("CREATE TABLE APP.T(id NUMBER)");
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            assertEquals("available",NativeSourceDefinition.read(connection,DatabaseType.ORACLE,"APP","T").status());
            verify(connection,times(1)).prepareStatement(anyString());
        }
    }
    @Test void failureMessagesDistinguishVendorCapabilityPermissionsTimeoutAndUnknownErrors() throws Exception {
        var failures=List.of(new SQLException("private SQL", "42000",6550),new SQLException("private owner", "42000",31603),
                new SQLTimeoutException("private host"),new SQLException("private credentials", "08006",12514));
        var statuses=List.of("unsupported","permission_denied","timeout","unavailable");
        for(int i=0;i<failures.size();i++) {
            var connection=mock(Connection.class);when(connection.prepareStatement(anyString())).thenThrow(failures.get(i));
            var result=NativeSourceDefinition.read(connection,DatabaseType.ORACLE,"APP","T");
            assertEquals(statuses.get(i),result.status());
            assertFalse(result.message().contains("private"),"Driver error details must not leak SQL, hosts or secrets");
        }
    }
    @Test void nativeOracleAndOceanbaseDdlCanIdentifyAnnualPartitionsWithoutCatalogPermissions() throws Exception {
        for(var type:List.of(DatabaseType.ORACLE,DatabaseType.OCEANBASE_ORACLE)) {
            String ddl="CREATE TABLE APP.T (id NUMBER, RIDEDATE DATE, CONSTRAINT PK_named_but_unique UNIQUE(id)) "
                    +"PARTITION BY RANGE(RIDEDATE)(PARTITION p_old VALUES LESS THAN (DATE '2012-01-01'),"
                    +"PARTITION p_2012 VALUES LESS THAN (DATE '2013-01-01'),PARTITION p_2013 VALUES LESS THAN (DATE '2014-01-01'))";
            var connection=mock(Connection.class);var statement=nativeStatement(ddl);
            when(connection.prepareStatement(contains("GET_DDL"))).thenReturn(statement);
            when(connection.prepareStatement(contains("ALL_PART_TABLES"))).thenThrow(new SQLException("catalog not available"));
            when(connection.prepareStatement(contains("ALL_CONSTRAINTS"))).thenThrow(new SQLException("catalog not available"));
            var result=PartitionMetadataReader.read(connection,config(type),"APP.T");
            assertEquals("detected",result.get("status"));assertEquals("native_ddl",result.get("detectedFrom"));
            var plan=(Map<?,?>)result.get("plan");assertEquals("YEAR",plan.get("displayStrategy"));
            assertEquals(true,plan.get("sourceBoundaries"));assertEquals("RANGE",plan.get("strategy"));
            assertEquals(3,((List<?>)plan.get("partitions")).size());
            assertEquals("2012-01-01 00:00:00",((Map<?,?>)((List<?>)plan.get("partitions")).getFirst()).get("upper"));
            var constraints=(List<?>)result.get("constraints");assertEquals("U",((Map<?,?>)constraints.getFirst()).get("type"));
            assertEquals("available",result.get("ddlReadStatus"));
            verify(statement).setString(1,"T");verify(statement).setString(2,"APP");verify(statement).setQueryTimeout(15);
        }
    }
    @Test void nativeClobIsReadBeyondFourThousandCharactersAndFailuresRemainNonBlocking() throws Exception {
        String ddl="CREATE TABLE APP.T (id NUMBER, occurred_at DATE"+", note VARCHAR2(50)"+ ") /*"+"x".repeat(9000)+"*/ PARTITION BY RANGE(occurred_at) (PARTITION p0 VALUES LESS THAN (DATE '2025-01-01'),PARTITION p1 VALUES LESS THAN (DATE '2026-01-01'))";
        var connection=mock(Connection.class);
        var nativeDdl=nativeStatement(ddl);
        when(connection.prepareStatement(contains("GET_DDL"))).thenReturn(nativeDdl);
        when(connection.prepareStatement(contains("ALL_PART_TABLES"))).thenThrow(new SQLException("unavailable"));
        when(connection.prepareStatement(contains("ALL_CONSTRAINTS"))).thenThrow(new SQLException("unavailable"));
        var result=PartitionMetadataReader.read(connection,config(DatabaseType.ORACLE),"APP.T");
        assertEquals("available",result.get("ddlReadStatus"));assertEquals("YEAR",((Map<?,?>)result.get("plan")).get("displayStrategy"));
        var denied=mock(Connection.class);when(denied.prepareStatement(anyString())).thenThrow(new SQLException("permission denied"));
        var failure=assertDoesNotThrow(()->PartitionMetadataReader.read(denied,config(DatabaseType.ORACLE),"APP.T"));
        assertEquals("unknown",failure.get("status"));assertEquals("unavailable",failure.get("ddlReadStatus"));
    }
    @Test void regularMonthsAnnualAndIrregularBoundariesAreClassifiedWithoutInventingDates() {
        assertEquals("YEAR",plan(List.of("2023-01-01","2024-01-01","2025-01-01")).get("displayStrategy"));
        assertEquals("MONTH",plan(List.of("2026-01-01","2026-02-01","2026-03-01")).get("displayStrategy"));
        var irregular=plan(List.of("2025-01-01","2025-04-01","2026-01-01"));
        assertEquals("TIME",irregular.get("displayStrategy"));assertEquals(3,((List<?>)irregular.get("partitions")).size());
        var catalog=List.<Map<String,Object>>of(Map.of("PARTITION_NAME","p_fz","HIGH_VALUE","'350100','350200'"));
        var list=PartitionMetadataReader.analyze("LIST","NONE","DSBM","VARCHAR2",catalog,"");
        assertEquals("REGION",((Map<?,?>)list.get("plan")).get("displayStrategy"));
        var enums=PartitionMetadataReader.analyze("LIST","NONE","STATUS","NUMBER",List.of(Map.of("PARTITION_NAME","p0","HIGH_VALUE","0,1")),"");
        assertEquals("LIST",((Map<?,?>)enums.get("plan")).get("displayStrategy"));
        assertEquals(2,((List<?>)((Map<?,?>)((List<?>)((Map<?,?>)list.get("plan")).get("partitions")).getFirst()).get("values")).size());
        assertTrue(PartitionMetadataReader.sameBoundaries(plan(List.of("2023-01-01","2024-01-01")),plan(List.of("2023-01-01","2024-01-01"))));
        assertFalse(PartitionMetadataReader.sameBoundaries(plan(List.of("2023-01-01","2024-01-01")),plan(List.of("2023-01-01","2025-01-01"))));
    }
    @Test void regionPresetHasExactlyTheTenProvidedCodesAndNumericColumnsStayNumeric() {
        var service=new TablePartitions();var region=new ColumnInfo();region.setColumnName("DSBM");region.setColumnType("VARCHAR2(6)");
        var codes=List.of("350100","350200","350300","350400","350500","350600","350700","350800","350900","351000");
        var request=new LinkedHashMap<String,Object>(Map.of("enabled",true,"strategy","REGION","field","DSBM"));
        var plan=service.normalize(request,List.of(region),DatabaseType.OCEANBASE_ORACLE);
        assertEquals("LIST",plan.get("strategy"));assertEquals("REGION",plan.get("displayStrategy"));
        assertEquals(codes,((List<?>)plan.get("partitions")).stream().map(p->((List<?>)((Map<?,?>)p).get("values")).getFirst()).toList());
        String ddl=service.apply("CREATE TABLE ods_t(DSBM VARCHAR2(6))",plan,config(DatabaseType.OCEANBASE_ORACLE));
        assertTrue(ddl.contains("'351000'"));assertFalse(ddl.contains("DEFAULT"));
        region.setColumnType("NUMBER");plan=service.normalize(request,List.of(region),DatabaseType.OCEANBASE_ORACLE);
        ddl=service.apply("CREATE TABLE ods_t(DSBM NUMBER)",plan,config(DatabaseType.OCEANBASE_ORACLE));
        assertTrue(ddl.contains("VALUES (351000)"),ddl);assertFalse(ddl.contains("'351000'"));
    }
    @Test void yearlyGenerationUsesYearBucketsAcrossAllVisibleDialectsAndKeepsSourceBounds() {
        var service=new TablePartitions();var date=new ColumnInfo();date.setColumnName("occurred_at");date.setColumnType("DATE");
        for(var type:List.of(DatabaseType.ORACLE,DatabaseType.OCEANBASE_ORACLE,DatabaseType.DAMENG,DatabaseType.HIVE,DatabaseType.MYSQL,DatabaseType.MARIADB,DatabaseType.OCEANBASE_MYSQL)) {
            var plan=service.normalize(new LinkedHashMap<>(Map.of("enabled",true,"strategy","YEAR","field","occurred_at","startYear","2025","years",2)),List.of(date),type);
            assertEquals("RANGE",plan.get("strategy"));assertEquals("YEAR",plan.get("displayStrategy"));
            var parts=(List<?>)plan.get("partitions");assertEquals(4,parts.size());
            assertEquals("2025",((Map<?,?>)parts.get(1)).get("bucket"));assertEquals("2027-01-01 00:00:00",((Map<?,?>)parts.get(2)).get("upper"));
            String ddl=service.apply("CREATE TABLE ods_t(occurred_at DATE)",plan,config(type));
            assertTrue(ddl.contains(type==DatabaseType.HIVE?"ods_partition":"2027-01-01"));
        }
        var source=plan(List.of("2012-01-01","2013-01-01","2014-01-01"));
        var normalized=service.normalize(source,List.of(date),DatabaseType.OCEANBASE_ORACLE);
        assertEquals(((List<?>)source.get("partitions")).stream().map(p->((Map<?,?>)p).get("upper")).toList(),
                ((List<?>)normalized.get("partitions")).stream().map(p->((Map<?,?>)p).get("upper")).toList());
        assertEquals("YEAR",normalized.get("displayStrategy"));assertEquals(true,normalized.get("sourceBoundaries"));
    }
    private static Map<String,Object> plan(List<String> dates) {
        List<Map<String,Object>> rows=new ArrayList<>();for(String date:dates)rows.add(Map.of("PARTITION_NAME","p_"+rows.size(),"HIGH_VALUE","DATE '"+date+"'"));
        @SuppressWarnings("unchecked") var plan=(Map<String,Object>)PartitionMetadataReader.analyze("RANGE","NONE","occurred_at","DATE",rows,"").get("plan");return plan;
    }
    private static DataSourceConfig config(DatabaseType type) {var config=new DataSourceConfig();config.setDatabaseType(type);config.setUsername("APP");config.setDatabase("ods");return config;}
    private static PreparedStatement nativeStatement(String ddl) throws Exception {
        var statement=mock(PreparedStatement.class);var result=mock(ResultSet.class);when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true,false);when(result.getCharacterStream(1)).thenReturn(new StringReader(ddl));return statement;
    }
}
