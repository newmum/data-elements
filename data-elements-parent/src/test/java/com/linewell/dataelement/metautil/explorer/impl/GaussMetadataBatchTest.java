package com.linewell.dataelement.metautil.explorer.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GaussMetadataBatchTest {
    @ParameterizedTest @ValueSource(ints={20,100})
    void columnBatchUsesOneScopedQueryForTwentyOrHundredTables(int size) throws Exception {
        Connection connection=mock(Connection.class);PreparedStatement statement=mock(PreparedStatement.class);ResultSet rows=mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);when(statement.executeQuery()).thenReturn(rows);
        List<String> tables=java.util.stream.IntStream.range(0,size).mapToObj(i->"validation_table_"+i).toList();
        AtomicInteger index=new AtomicInteger();when(rows.next()).thenAnswer(call->index.getAndIncrement()<size);
        when(rows.getString(anyString())).thenAnswer(call->switch(call.getArgument(0,String.class)) {
            case "table_name" -> tables.get(index.get()-1);
            case "column_name" -> "id";
            case "data_type" -> "integer";
            case "udt_name" -> "int4";
            case "is_nullable" -> "NO";
            default -> null;
        });
        when(rows.getBoolean("is_primary")).thenReturn(true);
        var explorer=new GaussDbMetadataExplorer(){@Override protected Connection getConnection(DataSourceConfig config){return connection;}};
        var config=new DataSourceConfig();config.setDatabaseType(DatabaseType.GAUSSDB);config.setSchema("tenant_owner");
        var result=explorer.getColumnsByTables(config,tables);assertEquals(size,result.size());
        for(String table:tables){assertEquals(1,result.get(table).size());assertTrue(result.get(table).getFirst().getPrimaryKey());}
        verify(connection,times(1)).prepareStatement(argThat(sql->sql.contains("c.table_schema=?")&&sql.contains("c.table_name IN")));
        verify(statement).setString(1,"tenant_owner");verify(statement,times(size+1)).setString(anyInt(),anyString());
    }

    @Test void implicitSchemaComesFromCurrentConnectionOnceAndCannotOverrideExplicitScope() throws Exception {
        Connection connection=mock(Connection.class);when(connection.getSchema()).thenReturn("current_owner");
        var calls=new AtomicInteger();
        var explorer=new GaussDbMetadataExplorer(){@Override protected Connection getConnection(DataSourceConfig config){calls.incrementAndGet();return connection;}};
        var config=new DataSourceConfig();config.setDatabaseType(DatabaseType.GAUSSDB);
        assertEquals("current_owner",explorer.resolveMetadataSchema(config));assertEquals("current_owner",explorer.resolveMetadataSchema(config));
        assertEquals(1,calls.get());assertNull(config.getSchema());
        config.setSchema("explicit_owner");assertEquals("explicit_owner",explorer.resolveMetadataSchema(config));assertEquals(1,calls.get());
    }
}
