package com.linewell.dataelement.metautil.explorer;

import com.linewell.dataelement.metautil.explorer.impl.OracleMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.sql.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OracleStringMetadataTest {
    @Test void singleQuotedObjectSummaryUsesTwoExactQueriesAndNeverListsTheWholeDatabase()throws Exception {
        var connection=mock(Connection.class);var metadata=mock(PreparedStatement.class);var count=mock(PreparedStatement.class);var m=mock(ResultSet.class);var n=mock(ResultSet.class);
        when(connection.prepareStatement(contains("FROM ALL_TAB_COMMENTS"))).thenReturn(metadata);when(connection.prepareStatement("SELECT COUNT(*) FROM \"TEST0809\".\"car\"")).thenReturn(count);
        when(metadata.executeQuery()).thenReturn(m);when(m.next()).thenReturn(true,false);when(m.getInt("COLUMN_COUNT")).thenReturn(3);when(m.getString("TABLE_TYPE")).thenReturn("TABLE");when(count.executeQuery()).thenReturn(n);when(n.next()).thenReturn(true,false);when(n.getLong(1)).thenReturn(4L);
        var cfg=new DataSourceConfig();cfg.setDatabaseType(DatabaseType.ORACLE);cfg.setSchema("TEST0809");var explorer=new OracleMetadataExplorer(){@Override protected Connection getConnection(DataSourceConfig ignored){return connection;}};
        var result=explorer.getTableSummary(cfg,"TEST0809.\"car\"");assertEquals("\"car\"",result.getTableName());assertEquals(4L,result.getRowCount());assertEquals(3,result.getColumnCount());verify(metadata).setString(1,"TEST0809");verify(metadata).setString(2,"car");verify(connection,times(2)).prepareStatement(anyString());verify(connection).close();
    }
    @Test void quotedOraclePhysicalNamesAndObjectLookupsNeverUseCaseInsensitiveFallback()throws Exception {
        assertEquals("TEST0809.\"car\"",com.linewell.dataelement.metautil.ddl.DdlDialect.oracleObjectName("test0809.\"car\""));
        assertEquals(com.linewell.dataelement.metautil.ddl.DdlDialect.oracleObjectKey("test0809.car"),com.linewell.dataelement.metautil.ddl.DdlDialect.oracleObjectKey("TEST0809.\"CAR\""));
        assertNotEquals(com.linewell.dataelement.metautil.ddl.DdlDialect.oracleObjectKey("TEST0809.CAR"),com.linewell.dataelement.metautil.ddl.DdlDialect.oracleObjectKey("TEST0809.\"car\""));
        assertNotEquals(com.linewell.dataelement.metautil.ddl.DdlDialect.oracleObjectKey("TEST0809.\"Car\""),com.linewell.dataelement.metautil.ddl.DdlDialect.oracleObjectKey("TEST0809.\"car\""));
        var config=new DataSourceConfig();config.setDatabaseType(DatabaseType.ORACLE);config.setSchema("TEST0809");
        var explorer=new OracleMetadataExplorer();
        List<String> excluded=org.springframework.test.util.ReflectionTestUtils.invokeMethod(explorer,"normalizeExcludedTableNames",List.of("TEST0809.CAR","TEST0809.\"car\"","TEST0809.\"Car\""));
        assertEquals(3,new java.util.HashSet<>(excluded).size());
        assertEquals(java.util.HexFormat.of().withUpperCase().formatHex("\"TEST0809\".\"car\"".getBytes(java.nio.charset.StandardCharsets.UTF_8)),excluded.get(1));
        String exclusionSql=org.springframework.test.util.ReflectionTestUtils.invokeMethod(explorer,"oracleExclusionSql",3);assertTrue(exclusionSql.contains("RAWTOHEX(UTL_RAW.CAST_TO_RAW("));assertFalse(exclusionSql.contains("LOWER("));
        for(String requested:List.of("TEST0809.CAR","TEST0809.\"car\"","\"Mixed.Owner\".\"a.b\"")) {
            var conn=mock(Connection.class);var statement=mock(PreparedStatement.class);var rs=mock(ResultSet.class);when(rs.next()).thenReturn(true,false);when(rs.getString("OBJECT_NAME")).thenReturn("resolved");when(statement.executeQuery()).thenReturn(rs);
            when(conn.prepareStatement(anyString())).thenAnswer(call->{assertFalse(call.getArgument(0,String.class).contains("UPPER("));return statement;});
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(explorer,"resolveOracleObject",conn,config,requested);
            var parts=com.linewell.dataelement.metautil.ddl.DdlDialect.names(requested);verify(statement).setString(1,parts.getFirst().quoted()?parts.getFirst().value():parts.getFirst().value().toUpperCase(java.util.Locale.ROOT));verify(statement).setString(2,parts.getLast().quoted()?parts.getLast().value():parts.getLast().value().toUpperCase(java.util.Locale.ROOT));
        }
    }
    @Test void charUsedAndNativeByteCapacityDoNotGetConfusedForTwentyOrHundredColumns()throws Exception {
        for(int count:List.of(20,100))for(var type:List.of(DatabaseType.ORACLE,DatabaseType.OCEANBASE_ORACLE)) {
            var connection=mock(Connection.class);var resolve=mock(PreparedStatement.class);var primary=mock(PreparedStatement.class);var columns=mock(PreparedStatement.class);
            var object=mock(ResultSet.class);when(object.next()).thenReturn(true,false);when(object.getString("OBJECT_NAME")).thenReturn("PEOPLE");when(resolve.executeQuery()).thenReturn(object);
            var keys=mock(ResultSet.class);when(keys.next()).thenReturn(false);when(primary.executeQuery()).thenReturn(keys);
            var values=mock(ResultSet.class);var index=new AtomicInteger(-1);when(values.next()).thenAnswer(call->index.incrementAndGet()<count);
            when(values.getString("COLUMN_NAME")).thenAnswer(call->"f"+index.get());when(values.getString("DATA_TYPE")).thenReturn("VARCHAR2");
            when(values.getString("CHAR_USED")).thenAnswer(call->index.get()%2==0?"C":"B");when(values.getLong("DATA_LENGTH")).thenReturn(200L);when(values.getLong("CHAR_LENGTH")).thenReturn(50L);
            when(columns.executeQuery()).thenReturn(values);
            when(connection.prepareStatement(contains("FROM ALL_OBJECTS"))).thenReturn(resolve);
            when(connection.prepareStatement(contains("FROM all_constraints"))).thenReturn(primary);
            when(connection.prepareStatement(contains("c.CHAR_USED"))).thenReturn(columns);
            var explorer=new OracleMetadataExplorer(){@Override protected Connection getConnection(DataSourceConfig ignored){return connection;}};
            var config=new DataSourceConfig();config.setDatabaseType(type);config.setSchema("ODS");
            var actual=explorer.getColumns(config,"PEOPLE");assertEquals(count,actual.size());
            for(int i=0;i<count;i++){var c=actual.get(i);assertEquals(200,c.getByteLength());assertEquals(50,c.getCharacterLength());assertEquals(i%2==0?50:200,c.getLength());assertEquals(i%2==0?"VARCHAR2(50 CHAR)":"VARCHAR2(200 BYTE)",c.getColumnType());}
            verify(connection,times(3)).prepareStatement(anyString());verify(connection).close();
        }
    }
}
