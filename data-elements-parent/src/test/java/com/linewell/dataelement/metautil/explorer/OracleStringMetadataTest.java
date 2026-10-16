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
