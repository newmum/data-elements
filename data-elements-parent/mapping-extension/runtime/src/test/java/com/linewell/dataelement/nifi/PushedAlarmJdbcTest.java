package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.*;
import com.linewell.dataelement.mapping.core.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated JDBC compatibility check; never connects to a tenant or production database. */
class PushedAlarmJdbcTest {
    @Test void normalizedPushCanBeWrittenToAggregationAndThenRawStorage()throws Exception {
        for(int count:new int[]{20,100}){
            var runner=PushedAlarmCompatibilityTest.runner(true);
            runner.enqueue(PushedAlarmCompatibilityTest.rows(count).toString());runner.run();
            runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);
            var normalized=PushedAlarmCompatibilityTest.JSON.readTree(runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst().toByteArray());
            assertTwoStorageHops(normalized);
        }
    }

    static void assertTwoStorageHops(JsonNode normalized)throws Exception {
        String database="validation_"+UUID.randomUUID().toString().replace("-","");
        try(var aggregation=DriverManager.getConnection("jdbc:h2:mem:"+database+"_aggregation;MODE=Oracle");
            var raw=DriverManager.getConnection("jdbc:h2:mem:"+database+"_raw;MODE=MySQL")){
            create(aggregation,"CLOB");create(raw,"LONGTEXT");
            insert(aggregation,normalized); // One executeBatch, regardless of row count.
            var sourceRows=read(aggregation); // One full query, not a query per record.
            var specification=PushedAlarmCompatibilityTest.plan(true).document();
            for(var field:specification.path("schemaContract").path("source")){
                var source=(com.fasterxml.jackson.databind.node.ObjectNode)field;
                source.put("name",source.path("name").asText().toUpperCase(Locale.ROOT));
                if(source.path("name").asText().equals("EVENT_TIME"))source.put("logicalType","DATETIME");
            }
            for(var rule:specification.path("rules")){
                var source=(com.fasterxml.jackson.databind.node.ObjectNode)rule.path("input");source.put("name",source.path("name").asText().toUpperCase(Locale.ROOT));
            }
            var plan=MappingEngine.compile(specification);
            var converted=MappingEngine.execute(plan,sourceRows,0,new ExecutionContext(Instant.EPOCH,ZoneId.of("Asia/Shanghai"),Locale.ROOT,"validation-jdbc"),(dictionary,keys)->{fail("No dictionary lookup is needed");return Map.of();});
            assertEquals(0,converted.dictionaryBatches());
            var output=PushedAlarmCompatibilityTest.JSON.createArrayNode();
            for(var row:converted.rows()){
                assertTrue(row.success(),"汇聚库读取后的字段应能再次转换");
                var value=output.addObject();row.output().forEach((key,item)->value.set(key,PushedAlarmCompatibilityTest.JSON.valueToTree(NifiTemporalValues.writerValue(item,ZoneId.of("Asia/Shanghai")))));
            }
            insert(raw,output);
            var stored=read(raw);assertEquals(normalized.size(),stored.size());
            var originals=new HashMap<String,JsonNode>();normalized.forEach(row->originals.put(row.path("event_no").asText(),row));
            for(var row:stored)for(String field:PushedAlarmCompatibilityTest.FIELDS){
                JsonNode original=originals.get(row.get("EVENT_NO"));assertNotNull(original,"两级写入应保持事件身份");
                var expected=original.path(field);Object actual=row.get(field.toUpperCase(Locale.ROOT));
                if(field.equals("event_time"))assertTrue(actual instanceof Timestamp timestamp&&timestamp.toInstant().toEpochMilli()==expected.asLong(),"两级写入应保持毫秒时间点");
                else if(expected.isTextual()&&expected.asText().isEmpty())assertNull(actual,"Oracle字符列的空字符串按数据库语义成为NULL");
                else assertTrue(Objects.equals(expected.isNull()?null:expected.asText(),actual),"两级写入应保持字段全文："+field);
            }
        }catch(SQLException error){throw new AssertionError("隔离JDBC验证失败，SQLState="+error.getSQLState()+"，code="+error.getErrorCode());}
    }
    private static void create(Connection connection,String textType)throws SQLException {
        var columns=new ArrayList<String>();
        for(String field:PushedAlarmCompatibilityTest.FIELDS)columns.add(field+" "+(field.equals("pic_base64")?textType:field.equals("event_time")?"TIMESTAMP(3)":"VARCHAR(100)")+(field.equals("event_no")?" PRIMARY KEY":""));
        try(var statement=connection.createStatement()){statement.execute("CREATE TABLE validation_alarm ("+String.join(",",columns)+")");}
    }
    private static void insert(Connection connection,JsonNode rows)throws SQLException {
        var columns=String.join(",",PushedAlarmCompatibilityTest.FIELDS);var parameters=String.join(",",Collections.nCopies(PushedAlarmCompatibilityTest.FIELDS.length,"?"));
        try(var statement=connection.prepareStatement("INSERT INTO validation_alarm ("+columns+") VALUES ("+parameters+")")){
            for(var row:rows){int index=1;for(String field:PushedAlarmCompatibilityTest.FIELDS){
                var value=row.path(field);
                if(value.isNull())statement.setNull(index,field.equals("event_time")?Types.TIMESTAMP:Types.VARCHAR);
                else if(field.equals("event_time"))statement.setTimestamp(index,Timestamp.from(Instant.ofEpochMilli(value.asLong())));
                else statement.setString(index,value.asText());index++;
            }statement.addBatch();}assertEquals(rows.size(),statement.executeBatch().length);
        }
    }
    private static List<Map<String,Object>> read(Connection connection)throws SQLException {
        var rows=new ArrayList<Map<String,Object>>();
        try(var statement=connection.createStatement();var result=statement.executeQuery("SELECT "+String.join(",",PushedAlarmCompatibilityTest.FIELDS)+" FROM validation_alarm ORDER BY event_no")){
            while(result.next()){
                var row=new LinkedHashMap<String,Object>();int index=1;
                for(String field:PushedAlarmCompatibilityTest.FIELDS){row.put(field.toUpperCase(Locale.ROOT),field.equals("event_time")?result.getTimestamp(index):result.getString(index));index++;}rows.add(row);
            }
        }return rows;
    }
}
