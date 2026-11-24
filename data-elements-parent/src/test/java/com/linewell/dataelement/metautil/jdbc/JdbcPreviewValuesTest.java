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
