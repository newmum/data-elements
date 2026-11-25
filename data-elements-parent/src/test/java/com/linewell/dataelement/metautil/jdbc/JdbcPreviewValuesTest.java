package com.linewell.dataelement.metautil.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class JdbcPreviewValuesTest {
    @Test void nativeLocalTimestampFractionsDoNotPassThroughTheSecondsOnlyJsonDateFormatter()throws Exception {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper().setDateFormat(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        var timestamp=java.sql.Timestamp.valueOf("2024-01-02 03:04:05.986123");
        assertFalse(mapper.writeValueAsString(timestamp).contains("986123"));
        for(DatabaseType type:new DatabaseType[]{DatabaseType.POSTGRESQL,DatabaseType.KINGBASE,DatabaseType.GAUSSDB,DatabaseType.HIGHGO,DatabaseType.DAMENG,DatabaseType.OCEANBASE_ORACLE})for(int count:new int[]{20,100}){
            ResultSet result=mock(ResultSet.class);
            for(int column=1;column<=count;column++){
                when(result.getString(column)).thenReturn("2024-01-02 03:04:05.986123");
                assertTrue(JdbcPreviewValues.isLocalTimestamp(type,"timestamp"));
                assertTrue(JdbcPreviewValues.isLocalTimestamp(type,"TIMESTAMP(6) WITHOUT TIME ZONE"));
                assertEquals("\"2024-01-02 03:04:05.986123\"",mapper.writeValueAsString(JdbcPreviewValues.read(result,column,true)));
                verify(result).getString(column);
            }
            verifyNoMoreInteractions(result);
        }
    }
    @Test void sqlServerDatetimeFractionsRemainLocalButBinaryAndOffsetTypesAreNeverReclassified()throws Exception {
        ResultSet result=mock(ResultSet.class);when(result.getString(1)).thenReturn("2024-01-02 03:04:05.1234567");
        assertTrue(JdbcPreviewValues.isLocalTimestamp(DatabaseType.SQLSERVER,"datetime2(7)"));
        assertTrue(JdbcPreviewValues.isLocalTimestamp(DatabaseType.SQLSERVER,"DATETIME"));
        assertEquals("2024-01-02 03:04:05.1234567",JdbcPreviewValues.read(result,1,true));
        for(String type:new String[]{"TIMESTAMP","ROWVERSION","DATETIMEOFFSET(7)"})assertFalse(JdbcPreviewValues.isLocalTimestamp(DatabaseType.SQLSERVER,type));
        assertFalse(JdbcPreviewValues.isLocalTimestamp(DatabaseType.POSTGRESQL,"timestamptz"));
        assertFalse(JdbcPreviewValues.isLocalTimestamp(DatabaseType.POSTGRESQL,"TIMESTAMP WITH TIME ZONE"));
        assertFalse(JdbcPreviewValues.isLocalTimestamp(null,"TIMESTAMP"));verify(result).getString(1);verifyNoMoreInteractions(result);
    }
    @Test void gaussMysqlVariantKeepsTheMysqlTimestampInstantContract(){
        var config=new com.linewell.dataelement.metautil.model.dto.DataSourceConfig();config.setDatabaseType(DatabaseType.GAUSSDB);
        config.setGaussdbVariant(com.linewell.dataelement.metautil.jdbc.GaussDbVariant.MYSQL.code());
        assertEquals(DatabaseType.MYSQL,config.getEffectiveDatabaseType());
        assertFalse(JdbcPreviewValues.isLocalTimestamp(config.getEffectiveDatabaseType(),"TIMESTAMP(6)"));
        assertTrue(JdbcPreviewValues.isLocalTimestamp(config.getEffectiveDatabaseType(),"DATETIME(6)"));
    }
    @Test
    void mysqlDateTimeFractionsStayInTheJdbcTextCarrierForTwentyAndHundredFields() throws Exception {
        for(int count:new int[]{20,100}) {
            ResultSet result=mock(ResultSet.class);
            for(int column=1;column<=count;column++) {
                when(result.getString(column)).thenReturn("2024-01-02 03:04:05.986123");
                assertTrue(JdbcPreviewValues.isLocalTimestamp(DatabaseType.MYSQL,"DATETIME"));
                assertTrue(JdbcPreviewValues.isLocalTimestamp(DatabaseType.MARIADB,"datetime(6)"));
                assertEquals("2024-01-02 03:04:05.986123",JdbcPreviewValues.read(result,column,true));
                verify(result).getString(column);
            }
            verifyNoMoreInteractions(result);
        }
        assertFalse(JdbcPreviewValues.isLocalTimestamp(DatabaseType.MYSQL,"DATE"));
        assertFalse(JdbcPreviewValues.isLocalTimestamp(DatabaseType.MARIADB,"TIMESTAMP(6)"));
        assertFalse(JdbcPreviewValues.isLocalTimestamp(DatabaseType.MYSQL,null));
    }
    @Test
    void readsHistoricalLocalTimeAndFractionDirectlyFromJdbc() throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.getString(1)).thenReturn("1990-08-01 00:00:00.0");
        when(result.getString(2)).thenReturn("1990-08-01 00:00:00.123456789");

        assertTrue(JdbcPreviewValues.isLocalTimestamp(DatabaseType.ORACLE, "TIMESTAMP"));
        assertTrue(JdbcPreviewValues.isLocalTimestamp(DatabaseType.HIVE, "timestamp(9)"));
        assertEquals("1990-08-01 00:00:00", JdbcPreviewValues.read(result, 1, true));
        assertEquals("1990-08-01 00:00:00.123456789", JdbcPreviewValues.read(result, 2, true));
        assertNull(JdbcPreviewValues.read(result, 3, true));
        verify(result).getString(1);
        verify(result).getString(2);
        verify(result).getString(3);
        verifyNoMoreInteractions(result);
    }

    @Test
    void retainsTheJdbcObjectForOtherTypes() throws Exception {
        ResultSet result = mock(ResultSet.class);
        OffsetDateTime timestamp = OffsetDateTime.parse("1990-08-01T00:00:00+09:00");
        when(result.getObject(1)).thenReturn(timestamp);

        assertFalse(JdbcPreviewValues.isLocalTimestamp(DatabaseType.ORACLE, "TIMESTAMP(6) WITH TIME ZONE"));
        assertFalse(JdbcPreviewValues.isLocalTimestamp(DatabaseType.ORACLE, "TIMESTAMP WITH LOCAL TIME ZONE"));
        assertFalse(JdbcPreviewValues.isLocalTimestamp(DatabaseType.HIVE, "TIMESTAMP WITH LOCAL TIME ZONE"));
        assertFalse(JdbcPreviewValues.isLocalTimestamp(DatabaseType.HIVE, "DATE"));
        assertFalse(JdbcPreviewValues.isLocalTimestamp(DatabaseType.MYSQL, "TIMESTAMP"));
        assertSame(timestamp, JdbcPreviewValues.read(result, 1, false));
        verify(result).getObject(1);
        verifyNoMoreInteractions(result);
    }
}
