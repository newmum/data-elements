package com.linewell.dataelement.metautil.explorer.impl;

import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SqlServerMetadataExplorerTest {
    @Test void jdbcColumnMetadataKeepsIntegerIdentityAndConstraintsWithConstantReadsAtTwentyAndHundred()throws Exception {
        for(int count:List.of(20,100))for(int mode=0;mode<3;mode++){
            var connection=mock(Connection.class);var metadata=mock(DatabaseMetaData.class);when(connection.getMetaData()).thenReturn(metadata);
            var values=new ArrayList<Map<String,Object>>();
            for(int index=0;index<count;index++){
                var row=new HashMap<String,Object>();row.put("COLUMN_NAME","field_"+index);row.put("REMARKS","来源字段说明");
                row.put("TYPE_NAME",index==0&&mode!=2?"int identity":"int");row.put("DATA_TYPE",Types.INTEGER);
                row.put("COLUMN_SIZE",10);row.put("DECIMAL_DIGITS",0);row.put("NULLABLE",index==0?DatabaseMetaData.columnNoNulls:DatabaseMetaData.columnNullable);
                row.put("ORDINAL_POSITION",index+1);if(mode!=1)row.put("IS_AUTOINCREMENT",index==0?"YES":"NO");values.add(row);
            }
            var columnRows=rows(values);
            var primaryRows=rows(List.of(Map.of("COLUMN_NAME","field_0")));
            var indexRows=rows(List.of(Map.of("COLUMN_NAME","field_0","INDEX_NAME","PK_VALIDATION")));
            when(metadata.getColumns("validation_db","dbo","source_records","%")).thenReturn(columnRows);
            when(metadata.getPrimaryKeys("validation_db","dbo","source_records")).thenReturn(primaryRows);
            when(metadata.getIndexInfo("validation_db","dbo","source_records",false,false)).thenReturn(indexRows);
            var explorer=new SqlServerMetadataExplorer(){@Override protected Connection getConnection(DataSourceConfig ignored){return connection;}};
            var config=new DataSourceConfig();config.setDatabaseType(DatabaseType.SQLSERVER);config.setDatabase("validation_db");config.setSchema("dbo");
            var columns=explorer.getColumns(config,"dbo.source_records");assertEquals(count,columns.size());
            for(int index=0;index<count;index++){
                var column=columns.get(index);assertEquals("field_"+index,column.getColumnName());assertEquals("int",column.getDataType());assertEquals("int",column.getColumnType());
                assertEquals(index==0,column.getAutoIncrement());assertEquals(index==0,column.getPrimaryKey());assertEquals(index!=0,column.getNullable());assertEquals(index+1,column.getOrdinalPosition());
                assertEquals(10L,column.getLength());assertEquals(10,column.getPrecision());assertEquals(0,column.getScale());assertEquals("来源字段说明",column.getColumnComment());
            }
            verify(metadata).getColumns("validation_db","dbo","source_records","%");verify(metadata).getPrimaryKeys("validation_db","dbo","source_records");
            verify(metadata).getIndexInfo("validation_db","dbo","source_records",false,false);verifyNoMoreInteractions(metadata);
            verify(connection,never()).prepareStatement(anyString());verify(connection,never()).createStatement();
        }
    }

    private static ResultSet rows(List<Map<String,Object>> rows)throws Exception {
        var result=mock(ResultSet.class);var position=new AtomicInteger(-1);var last=new AtomicReference<Object>();
        when(result.next()).thenAnswer(call->position.incrementAndGet()<rows.size());
        when(result.getString(anyString())).thenAnswer(call->{String key=call.getArgument(0);var row=rows.get(position.get());
            if(key.equals("IS_AUTOINCREMENT")&&!row.containsKey(key))throw new SQLFeatureNotSupportedException("Optional metadata flag unavailable");
            Object value=row.get(key);last.set(value);return value==null?null:value.toString();});
        when(result.getInt(anyString())).thenAnswer(call->{Object value=rows.get(position.get()).get(call.getArgument(0));last.set(value);return value==null?0:((Number)value).intValue();});
        when(result.getLong(anyString())).thenAnswer(call->{Object value=rows.get(position.get()).get(call.getArgument(0));last.set(value);return value==null?0L:((Number)value).longValue();});
        when(result.wasNull()).thenAnswer(call->last.get()==null);return result;
    }
}
