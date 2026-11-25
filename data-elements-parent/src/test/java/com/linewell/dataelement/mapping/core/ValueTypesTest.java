package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ValueTypesTest {
    @Test void mysqlZeroFillPreservesUnsignedRangeInsteadOfDisplayWidth() {
        for(String type:List.of("INT(1) UNSIGNED ZEROFILL","integer(2) zerofill"," INT (10)  UNSIGNED   ZEROFILL ")){
            assertEquals("LONG",ValueTypes.type(type));
            assertEquals(4294967295L,ValueTypes.cast("4294967295",type,0,0,RoundingMode.UNNECESSARY));
            assertEquals("TYPE_MISMATCH",assertThrows(MappingException.class,()->ValueTypes.cast("1.5",type,0,0,RoundingMode.UNNECESSARY)).code());
        }
        for(String type:List.of("TINYINT(1) UNSIGNED ZEROFILL","SMALLINT ZEROFILL","MEDIUMINT(8) UNSIGNED ZEROFILL"))assertEquals("INT",ValueTypes.type(type));
        assertEquals("DECIMAL",ValueTypes.type("BIGINT(20) UNSIGNED ZEROFILL"));
        assertEquals(new java.math.BigDecimal("18446744073709551615"),ValueTypes.cast("18446744073709551615","BIGINT(20) ZEROFILL",20,0,RoundingMode.UNNECESSARY));
        assertEquals(new java.math.BigDecimal("1.25"),ValueTypes.cast("1.25","DECIMAL(10,2) UNSIGNED ZEROFILL",10,2,RoundingMode.UNNECESSARY));
        assertEquals("DOUBLE",ValueTypes.type("DOUBLE PRECISION UNSIGNED ZEROFILL"));
        assertEquals("INT",ValueTypes.type("INT(1)"));
        for(String invalid:List.of("VARCHAR(20) ZEROFILL","INT ZEROFILL UNKNOWN","ZEROFILL INT","INT ZEROFILL UNSIGNED","INT UNSIGNED ZEROFILL;DROP"))
            assertEquals("UNSUPPORTED_TYPE",assertThrows(MappingException.class,()->ValueTypes.requireType(invalid)).code(),invalid);
    }
    @Test void zeroFillSourceMetadataRemainsPhysicalWhileSchemaUsesPortableNumericFamilies() throws Exception {
        for(int count:List.of(20,100)){
            var spec=JSON.readTree("""
                {"schemaVersion":"2.0","schemaContract":{"source":[{"name":"flag","logicalType":"INT UNSIGNED ZEROFILL","physicalType":"int(1) unsigned zerofill","nullable":false}],"target":[{"name":"FLAG","logicalType":"DECIMAL","precision":10,"scale":0}]},"rules":[{"input":{"kind":"FIELD","name":"flag"},"target":{"name":"FLAG","logicalType":"DECIMAL","precision":10,"scale":0},"steps":[]}]}
                """);
            var plan=MappingEngine.compile(spec);
            assertEquals("int(1) unsigned zerofill",plan.document().path("schemaContract").path("source").get(0).path("physicalType").asText());
            SchemaContract.requireInput(plan,List.of(new SchemaContract.Field("flag","LONG",false,0,0,0)));
            assertEquals("SCHEMA_DRIFT",assertThrows(MappingException.class,()->SchemaContract.requireInput(plan,List.of(new SchemaContract.Field("flag","BOOLEAN",false,0,0,0)))).code());
            var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<count;i++)rows.add(Map.of("flag",i%2==0?0L:4294967295L));
            var result=MappingEngine.execute(plan,rows,0,CONTEXT,null);
            for(int i=0;i<count;i++){assertTrue(result.rows().get(i).success());assertEquals(new java.math.BigDecimal(i%2==0?"0":"4294967295"),result.rows().get(i).output().get("FLAG"));}
        }
    }
    @Test void numericColumnNamesAreLiteralFieldsAndNeverPathsOrSqlFragments() throws Exception {
        for (String name : List.of("0", "0123", "1231313", "123_name")) {
            assertEquals(name, ValueTypes.field("/" + name));
            for (String expression : List.of("${field:" + name + "}", "123")) {
                var spec=JSON.createObjectNode().put("version","1.0");
                spec.putArray("mappings").addObject().put("from","/"+name).put("to","/"+name).put("expression",expression);
                var result=MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of(name,"validation")),0,CONTEXT,null);
                assertTrue(result.rows().getFirst().success());
                assertEquals(expression.equals("123")?123L:"validation",result.rows().getFirst().output().get(name));
            }
        }
        for (String invalid : List.of("123/child", "123.child", "123[0]", "123 OR 1=1", "123;DROP", "123--", "123\u0000"))
            assertThrows(MappingException.class,()->ValueTypes.field(invalid));
    }
    @Test void nativeBooleansUseSingleDigitScaleZeroDecimalsInLegacySchemaAndExplicitCasts() throws Exception {
        for (String alias : List.of("NUMBER", "NUMERIC", "DECIMAL")) {
            assertEquals(java.math.BigDecimal.ONE, ValueTypes.cast(true, alias, 1, 0, RoundingMode.UNNECESSARY));
            assertEquals(java.math.BigDecimal.ZERO, ValueTypes.cast(false, alias, 1, 0, RoundingMode.UNNECESSARY));
            assertNull(ValueTypes.cast(null, alias, 1, 0, RoundingMode.UNNECESSARY));
        }
        var specs = List.of(
                "{\"version\":\"1.0\",\"mappings\":[{\"from\":\"/x\",\"to\":\"/y\",\"targetType\":\"NUMBER(1)\"}]}",
                "{\"schemaVersion\":\"2.0\",\"schemaContract\":{\"target\":[{\"name\":\"y\",\"logicalType\":\"DECIMAL\",\"precision\":1,\"scale\":0}]},\"rules\":[{\"ruleId\":\"boolean\",\"target\":{\"name\":\"y\"},\"input\":{\"kind\":\"FIELD\",\"name\":\"x\"},\"steps\":[]}]}",
                "{\"version\":\"1.0\",\"mappings\":[{\"from\":\"/x\",\"to\":\"/y\",\"expression\":\"CAST(${field:x} AS DECIMAL(1,0))\"}]}",
                "{\"version\":\"1.0\",\"mappings\":[{\"from\":\"/x\",\"to\":\"/y\",\"transform\":{\"fn\":\"cast\",\"args\":[\"DECIMAL\",1,0]}}]}");
        for (String spec : specs) {
            var plan = MappingEngine.compile(JSON.readTree(spec));
            for (int count : List.of(20, 100)) {
                var rows = new ArrayList<Map<String,Object>>();
                for (int i = 0; i < count; i++) rows.add(Collections.singletonMap("x", i % 3 == 0 ? null : i % 3 == 1));
                var result = MappingEngine.execute(plan, rows, 0, CONTEXT, null);
                assertEquals(count, result.rows().size()); assertEquals(0, result.dictionaryBatches());
                for (int i = 0; i < count; i++) {
                    var row = result.rows().get(i); assertTrue(row.success(), row.errors().toString());
                    assertEquals(i % 3 == 0 ? null : i % 3 == 1 ? java.math.BigDecimal.ONE : java.math.BigDecimal.ZERO, row.output().get("y"));
                }
            }
        }
    }
    @Test void booleanDecimalCarrierDoesNotCoerceTextOrBroadenOtherDecimalDimensions() throws Exception {
        for (int[] dimensions : List.of(new int[]{0,0}, new int[]{2,0}, new int[]{1,1}, new int[]{38,10})) {
            for (boolean value : List.of(false, true)) assertEquals("TYPE_MISMATCH", assertThrows(MappingException.class,
                    () -> ValueTypes.cast(value, "DECIMAL", dimensions[0], dimensions[1], RoundingMode.UNNECESSARY)).code());
        }
        for (String value : List.of("true", "false", "yes", "no")) assertEquals("TYPE_MISMATCH", assertThrows(MappingException.class,
                () -> ValueTypes.cast(value, "DECIMAL", 1, 0, RoundingMode.UNNECESSARY)).code());
        assertEquals(new java.math.BigDecimal("2"), ValueTypes.cast(2, "DECIMAL", 1, 0, RoundingMode.UNNECESSARY));
        var required = MappingEngine.compile(JSON.readTree("{\"version\":\"1.0\",\"mappings\":[{\"from\":\"/x\",\"to\":\"/y\",\"targetType\":\"NUMBER(1)\",\"targetNullable\":false}]}"));
        var invalid = MappingEngine.execute(required, List.of(Collections.singletonMap("x", null), Map.of("x", "true")), 0, CONTEXT, null);
        assertEquals("REQUIRED_FIELD", invalid.rows().get(0).errors().getFirst().code());
        assertEquals("TYPE_MISMATCH", invalid.rows().get(1).errors().getFirst().code());
    }
    @Test void nativeFloatingAliasesUseFiniteDoubleValuesForLegacyAndSchemaRules() throws Exception {
        for (String alias : List.of("BINARY_DOUBLE", "binary_float", "FLOAT4", "FLOAT8")) {
            assertEquals("DOUBLE", ValueTypes.requireType(alias));
            assertNull(ValueTypes.cast(null, alias, 0, 0, RoundingMode.UNNECESSARY));
            for (double value : new double[]{0.0, -0.0, 1.2345678901234567, -0.00000012345, Double.MIN_VALUE, Double.MAX_VALUE}) {
                assertEquals(Double.doubleToLongBits(value), Double.doubleToLongBits((Double)ValueTypes.cast(value, alias, 0, 0, RoundingMode.UNNECESSARY)));
            }
            for (boolean legacy : List.of(false, true)) {
                var spec = JSON.createObjectNode();
                if (legacy) {
                    spec.put("version", "1.0");
                    spec.putArray("mappings").addObject().put("from", "/x").put("to", "/y").put("targetType", alias);
                } else {
                    spec.put("schemaVersion", "2.0");
                    spec.putObject("schemaContract").putArray("target").addObject().put("name", "y").put("logicalType", alias).put("physicalType", alias);
                    var rule = spec.putArray("rules").addObject().put("ruleId", "floating");
                    rule.putObject("target").put("name", "y").put("logicalType", alias);
                    rule.putObject("input").put("kind", "FIELD").put("name", "x"); rule.putArray("steps");
                }
                var plan = MappingEngine.compile(spec);
                for (int size : List.of(20, 100)) {
                    var result = MappingEngine.execute(plan, Collections.nCopies(size, Map.of("x", 123.45678901234567)), 0, CONTEXT, null);
                    assertEquals(size, result.rows().size()); assertEquals(0, result.dictionaryBatches());
                    for (var row : result.rows()) {
                        assertTrue(row.success(), row.errors().toString());
                        assertInstanceOf(Double.class, row.output().get("y")); assertEquals(123.45678901234567, row.output().get("y"));
                    }
                }
            }
        }
    }
    @Test void floatingAliasesContinueRejectingNonfiniteOverflowAndUnknownTypes() {
        for (String type : List.of("BINARY_FLOAT", "BINARY_DOUBLE", "FLOAT4", "FLOAT8")) {
            for (Object bad : List.of("NaN", "Infinity", "-Infinity", "1e309", Double.NaN, Double.POSITIVE_INFINITY)) {
                assertEquals("TYPE_MISMATCH", assertThrows(MappingException.class,
                        () -> ValueTypes.cast(bad, type, 0, 0, RoundingMode.UNNECESSARY)).code());
            }
        }
        assertEquals("UNSUPPORTED_TYPE", assertThrows(MappingException.class, () -> ValueTypes.requireType("BINARY_UNKNOWN")).code());
    }
    @Test void atPrefixedTopLevelFieldsRemainLiteralAndCannotBecomePathsOrSql() {
        assertEquals("@timestamp",ValueTypes.field("/@timestamp"));
        assertEquals("_id",ValueTypes.field("/_id"));
        for(String invalid:List.of("/@timestamp/value","@timestamp.value","@timestamp[0]","@timestamp;DROP TABLE t","@timestamp\nvalue"))
            assertEquals("INVALID_FIELD",assertThrows(MappingException.class,()->ValueTypes.field(invalid)).code());
    }
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final ExecutionContext CONTEXT=new ExecutionContext(Instant.EPOCH,ZoneId.of("Asia/Shanghai"),Locale.CHINA,"synthetic");
    @Test void portableCharacterAliasesIncludingDimensionsWhitespaceAndNationalForms() {
        for(String type:List.of("CHARACTER VARYING", "character varying(200)", " CHARACTER   VARYING (20) ",
                "CHAR VARYING(30)", "NATIONAL CHARACTER VARYING(100)", "NATIONAL CHAR VARYING", "NCHAR VARYING",
                "CHARACTER(2)", "NATIONAL CHARACTER(2)", "NCLOB", "LONGNVARCHAR", "BPCHAR", "MEDIUMTEXT", "LONGTEXT")) {
            assertEquals("STRING",ValueTypes.type(type),type);
            assertEquals("中文123",ValueTypes.cast("中文123",type,38,10,RoundingMode.UNNECESSARY),type);
        }
        assertEquals("INSTANT",ValueTypes.type("TIMESTAMP (6)  WITH TIME ZONE"));
        assertNull(ValueTypes.cast(null,"CHARACTER VARYING",38,10,RoundingMode.UNNECESSARY));
    }
    @Test void screenshotLegacyCastAcross190FieldsAndTwentyOrOneHundredRowsUsesNoProvider() {
        var spec=JSON.createObjectNode().put("version","1.0");var mappings=spec.putArray("mappings");
        for(int i=0;i<190;i++) mappings.addObject().put("from","/x").put("to","/field"+i).put("targetType","CHARACTER VARYING(100)");
        var plan=MappingEngine.compile(spec);
        for(int size:List.of(20,100)) {
            var rows=Collections.<Map<String,Object>>nCopies(size,Map.of("x","中文v"));
            var result=MappingEngine.execute(plan,rows,0,CONTEXT,null);
            assertEquals(size,result.rows().size());assertEquals(0,result.dictionaryBatches());
            assertTrue(result.rows().stream().allMatch(row->row.success()&&row.output().size()==190&&row.output().get("field189").equals("中文v")));
        }
    }
    @Test void explicitCastAcceptsTheScreenshotTypeAlias() throws Exception {
        var plan=MappingEngine.compile(JSON.readTree("""
          {"schemaVersion":"2.0","rules":[{"ruleId":"r","target":{"name":"y"},"input":{"kind":"FIELD","name":"x"},"steps":[{"operator":"CAST","args":{"logicalType":"CHARACTER VARYING"}}]}]}
          """));
        var result=MappingEngine.execute(plan,List.of(Map.of("x",123)),0,CONTEXT,null);
        assertTrue(result.rows().getFirst().success());
        assertEquals("123",result.rows().getFirst().output().get("y"));
        var legacy=MappingEngine.compile(JSON.readTree("""
          {"version":"1.0","mappings":[{"from":"/x","to":"/y","transform":{"fn":"cast","args":["CHARACTER VARYING"]}}]}
          """));
        assertEquals("123",MappingEngine.execute(legacy,List.of(Map.of("x",123)),0,CONTEXT,null).rows().getFirst().output().get("y"));
    }
    @Test void unknownCastTypeFailsAtCompilationRatherThanAfterReceivingRows() throws Exception {
        var spec=JSON.readTree("""
          {"schemaVersion":"2.0","rules":[{"ruleId":"r","target":{"name":"y"},"input":{"kind":"FIELD","name":"x"},"steps":[{"operator":"CAST","args":{"logicalType":"UNKNOWN_DATABASE_TYPE"}}]}]}
          """);
        assertEquals("UNSUPPORTED_TYPE",assertThrows(MappingException.class,()->MappingEngine.compile(spec)).code());
    }
    @Test void sparseReplayIndicesPreserveDeterministicGeneratedValues() throws Exception {
        var plan=MappingEngine.compile(JSON.readTree("{\"version\":\"1.0\",\"mappings\":[{\"to\":\"/id\",\"expression\":\"UUID()\"}]}"));
        var all=MappingEngine.execute(plan,Collections.nCopies(8,Map.of()),0,CONTEXT,null);
        var replay=MappingEngine.executeIndexed(plan,List.of(Map.of(),Map.of()),List.of(2L,7L),CONTEXT,null);
        assertEquals(all.rows().get(2).output().get("id"),replay.rows().get(0).output().get("id"));
        assertEquals(all.rows().get(7).output().get("id"),replay.rows().get(1).output().get("id"));
        assertThrows(MappingException.class,()->MappingEngine.executeIndexed(plan,List.of(Map.of(),Map.of()),List.of(2L,2L),CONTEXT,null));
    }
}
