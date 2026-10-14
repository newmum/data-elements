package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OracleTemporalProjectionTest {
    @Test void generatedProjectionExecutesAndJdbcReturnsTimestampWithSeconds()throws Exception {
        try(var connection=java.sql.DriverManager.getConnection("jdbc:h2:mem:oracle-temporal;MODE=Oracle;DB_CLOSE_DELAY=-1")){
            connection.createStatement().execute("CREATE TABLE EVENTS(ID VARCHAR2(20),EVENT_TIME DATE,TS TIMESTAMP)");
            connection.createStatement().execute("INSERT INTO EVENTS VALUES('1',TIMESTAMP '2026-10-14 14:27:49',TIMESTAMP '2026-10-14 14:27:49.123456')");
            try(var rows=connection.createStatement().executeQuery("SELECT "+OracleTemporalProjection.columns(config("ORACLE"))+" FROM EVENTS")){
                assertTrue(rows.next());assertEquals(java.sql.Types.TIMESTAMP,rows.getMetaData().getColumnType(2));assertEquals(java.time.LocalDateTime.of(2026,10,14,14,27,49),rows.getTimestamp(2).toLocalDateTime());assertEquals(123456000,rows.getTimestamp(3).getNanos());
            }
        }
    }
    private Map<String,Object> config(String db){return Map.of("dbType",db,"sourceColumns",List.of(Map.of("columnName","ID","dataType","VARCHAR2"),Map.of("columnName","EVENT_TIME","dataType","DATE"),Map.of("columnName","TS","dataType","TIMESTAMP")));}
    @Test void castsOnlyOracleDateAndRetainsColumnNames() {
        assertEquals("\"ID\",CAST(\"EVENT_TIME\" AS TIMESTAMP) AS \"EVENT_TIME\",\"TS\"",OracleTemporalProjection.columns(config("ORACLE")));
        assertEquals("",OracleTemporalProjection.columns(config("MYSQL")));
        assertEquals("",OracleTemporalProjection.columns(config("DM")));
        assertThrows(RuntimeException.class,()->OracleTemporalProjection.columns(Map.of("dbType","ORACLE","sourceColumns",List.of(Map.of("columnName","DT;DROP TABLE T","dataType","DATE")))));
    }
    @Test void repairsLegacyAndUnifiedTargetsWithoutChangingExplicitSteps()throws Exception {
        var json=new ObjectMapper();Map<String,Object> target=Map.of("dbType","OCEANBASE_ORACLE","targetColumns",List.of(Map.of("columnName","EVENT_TIME","dataType","DATE")));
        var old=json.readTree("{\"mappings\":[{\"from\":\"EVENT_TIME\",\"to\":\"/EVENT_TIME\",\"targetType\":\"DATE\"}]}");
        assertEquals("DATETIME",TemporalRuleRepair.targets(old,target).path("mappings").get(0).path("targetType").asText());assertEquals("DATE",old.path("mappings").get(0).path("targetType").asText());
        var unified=json.readTree("{\"rules\":[{\"target\":{\"name\":\"EVENT_TIME\",\"logicalType\":\"DATE\"},\"steps\":[{\"operator\":\"CAST\",\"args\":{\"logicalType\":\"DATE\"}}]}]}");
        var fixed=TemporalRuleRepair.targets(unified,target);assertEquals("DATETIME",fixed.path("rules").get(0).path("target").path("logicalType").asText());assertEquals("DATE",fixed.path("rules").get(0).path("steps").get(0).path("args").path("logicalType").asText());
    }
}
