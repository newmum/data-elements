package com.linewell.dataelement.integration.nifi.canvas.mapping;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.ssssssss.magicapi.modules.db.SQLModule;

class FieldMappingStorageGuardTest {
    @Test
    void twentyAndHundredRulesUseOneTenantBoundZeroRowRead() throws Exception {
        for(int count:List.of(20,100)) {
            var fixture=new Fixture("TEXT",16383);
            var rows=new ArrayList<Map<String,Object>>();
            for(int i=0;i<count;i++) rows.add(Map.of("target_field","field_"+i,"func_value","完整规则".repeat(1000)));
            assertNull(FieldMappingStorageGuard.validate(fixture.db,"tenant-a",rows));
            verify(fixture.connection,times(1)).prepareStatement("SELECT `func_value` FROM `data_access_field_mapping` WHERE `tenant_id` = ? AND 1 = 0");
            verify(fixture.statement,times(1)).executeQuery();
            verify(fixture.statement).setString(1,"tenant-a");
            verify(fixture.statement).setQueryTimeout(15);
            verify(fixture.result).close(); verify(fixture.statement).close(); verify(fixture.connection).close();
            System.out.println("Mapping capacity rules="+count+", zero-row tenant SQL=1, writes=0");
        }
    }

    @Test
    void textUsesUtf8ByteCapacityWhileVarcharUsesUnicodeCharacters() throws Exception {
        var rows=List.<Map<String,Object>>of(Map.of("target_field","name","func_value","中".repeat(21846)));
        assertNotNull(FieldMappingStorageGuard.validate(new Fixture("TEXT",16383).db,"tenant-a",rows));
        assertNull(FieldMappingStorageGuard.validate(new Fixture("VARCHAR",30000).db,"tenant-a",rows));
        var fitsText=List.<Map<String,Object>>of(Map.of("target_field","name","func_value","中".repeat(21845)));
        assertNull(FieldMappingStorageGuard.validate(new Fixture("TEXT",16383).db,"tenant-a",fitsText));
        var emoji=List.<Map<String,Object>>of(Map.of("target_field","name","func_value","😀".repeat(10)));
        assertNull(FieldMappingStorageGuard.validate(new Fixture("VARCHAR",10).db,"tenant-a",emoji));
    }

    @Test
    void metadataFailureClosesResourcesAndDoesNotLeakRules() throws Exception {
        var fixture=new Fixture("VARCHAR",255);
        when(fixture.statement.executeQuery()).thenThrow(new java.sql.SQLException("fixture failure"));
        var error=assertThrows(IllegalStateException.class,()->FieldMappingStorageGuard.validate(fixture.db,"tenant-a",
                List.of(Map.of("func_value","confidential-rule-value"))));
        assertEquals("无法检查当前租户的字段映射存储结构",error.getMessage());
        verify(fixture.statement).close(); verify(fixture.connection).close();
    }

    static class Fixture {
        final SQLModule db=mock(SQLModule.class);
        final Connection connection=mock(Connection.class);
        final PreparedStatement statement=mock(PreparedStatement.class);
        final ResultSet result=mock(ResultSet.class);
        Fixture(String type,int capacity) throws Exception {
            var source=mock(DataSource.class); when(source.getConnection()).thenReturn(connection);
            when(db.getJdbcTemplate()).thenReturn(new JdbcTemplate(source));
            var metadata=mock(DatabaseMetaData.class); when(connection.getMetaData()).thenReturn(metadata);
            when(metadata.getIdentifierQuoteString()).thenReturn("`");
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            when(statement.executeQuery()).thenReturn(result);
            var columns=mock(ResultSetMetaData.class); when(result.getMetaData()).thenReturn(columns);
            when(columns.getColumnTypeName(1)).thenReturn(type); when(columns.getPrecision(1)).thenReturn(capacity);
        }
    }
}
