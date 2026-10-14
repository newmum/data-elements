package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MappingWideTemporalTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    @Test void oneHundredNinetyDictionaryFieldsCompileAndBatchCountsDoNotGrowWithRows() {
        ObjectNode spec=JSON.createObjectNode().put("schemaVersion","2.0");var dictionaries=spec.putObject("dictionaryBindings");var rules=spec.putArray("rules");
        for(int i=0;i<190;i++){
            var d=dictionaries.putObject("d"+i).put("datasourceId","db").put("tableId","t").put("table","dictionary").put("valueColumn","label"+i);
            d.putArray("keyColumns").add("code");d.putArray("keyTypes").add("STRING");
            var r=rules.addObject().put("ruleId","r"+i);r.putObject("target").put("name","label"+i).put("logicalType","STRING");r.putObject("input").put("kind","FIELD").put("name","code");r.putArray("steps").addObject().put("operator","LOOKUP").putObject("args").put("dictionaryId","d"+i);
        }
        var plan=MappingEngine.compile(spec);assertEquals(190,plan.outputFields().size());
        for(int count:List.of(20,100)){
            AtomicInteger calls=new AtomicInteger();List<Map<String,Object>> rows=new ArrayList<>();for(int i=0;i<count;i++)rows.add(Map.of("code","C"+i));
            var result=MappingEngine.execute(plan,rows,0,new ExecutionContext(Instant.parse("2026-10-14T06:27:49Z"),ZONE,Locale.ROOT,"wide"),(dictionary,keys)->{calls.incrementAndGet();assertEquals(count,keys.size());Map<DictionarySpec.Key,DictionarySpec.LookupValue> values=new HashMap<>();keys.forEach(key->values.put(key,new DictionarySpec.LookupValue(true,"translated")));return values;});
            assertEquals(190,calls.get());assertEquals(count,result.rows().size());assertTrue(result.rows().stream().allMatch(row->row.success()&&row.output().size()==190));
        }
    }
    @Test void dateMeaningDependsOnDatabaseAndMode() {
        for(String db:List.of("Oracle","Oracle 12+","OCEANBASE_ORACLE","OCEANBASEORACLE"))assertEquals("DATETIME",JdbcTemporalTypes.logicalType("DATE",Map.of("dbType",db)));
        assertEquals("DATETIME",JdbcTemporalTypes.logicalType("DATE",Map.of("dbType","OCEANBASE","compatibleMode","ORACLE")));
        for(String db:List.of("MYSQL","MARIADB","POSTGRESQL","GAUSSDB","SQLSERVER","DM","DB2","KINGBASE","HIGHGO","GBASE8A","GBASE8S","OSCAR","CLICKHOUSE","HIVE"))assertEquals("DATE",JdbcTemporalTypes.logicalType("DATE",Map.of("dbType",db)),db);
        assertEquals("DATE",JdbcTemporalTypes.logicalType("DATE",Map.of("dbType","OCEANBASE","compatibleMode","MYSQL")));
    }
    @Test void physicalDatetimeAliasesRetainSecondsAndOffsets() {
        var input=Timestamp.from(Instant.parse("2026-10-14T06:27:49Z"));
        for(String type:List.of("DATETIME(6)","TIMESTAMP(9)","DATETIME2(7)","SMALLDATETIME","TIMESTAMP WITHOUT TIME ZONE","DATETIME64(3)"))
            assertEquals(LocalDateTime.of(2026,10,14,14,27,49),ValueTypes.cast(input,type,38,10,RoundingMode.UNNECESSARY,ZONE),type);
        for(String type:List.of("TIMESTAMPTZ","TIMESTAMP WITH TIME ZONE","TIMESTAMP WITH LOCAL TIME ZONE","DATETIMEOFFSET"))assertEquals(Instant.parse("2026-10-14T06:27:49Z"),ValueTypes.cast("2026-10-14T14:27:49+08:00",type,38,10,RoundingMode.UNNECESSARY,ZONE));
        assertEquals(LocalDateTime.of(2026,10,14,14,27,49),ValueTypes.dateTime("2026-10-14T06:27:49Z",ZONE));
        assertEquals(LocalTime.of(14,27,49),ValueTypes.cast(java.sql.Time.valueOf("14:27:49"),"TIME",38,10,RoundingMode.UNNECESSARY,ZONE));
        assertEquals(LocalDate.of(2026,10,14),ValueTypes.cast(input,"DATE",38,10,RoundingMode.UNNECESSARY,ZONE));
        assertNull(ValueTypes.cast(null,"DATETIME",38,10,RoundingMode.UNNECESSARY,ZONE));
    }
    @Test void authoritativeOracleDateRepairsOldInferredDateTargetWithoutTruncation() throws Exception {
        var spec=JSON.readTree("""
          {"schemaVersion":"2.0","schemaContract":{"target":[{"name":"dt","logicalType":"DATETIME","physicalType":"DATE","dateContainsTime":true}]},"rules":[{"ruleId":"time","target":{"name":"dt","logicalType":"DATE"},"input":{"kind":"FIELD","name":"dt"},"steps":[]}]}
          """);
        var plan=MappingEngine.compile(spec);var result=MappingEngine.execute(plan,List.of(Map.of("dt","2026-10-14 14:27:49")),0,new ExecutionContext(Instant.EPOCH,ZONE,Locale.ROOT,"date"),null);
        assertTrue(result.rows().getFirst().success());assertEquals(LocalDateTime.of(2026,10,14,14,27,49),result.rows().getFirst().output().get("dt"));
    }
}
