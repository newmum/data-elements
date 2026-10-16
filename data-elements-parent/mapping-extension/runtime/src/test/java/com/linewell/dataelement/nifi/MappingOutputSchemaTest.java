package com.linewell.dataelement.nifi;

import org.apache.nifi.serialization.SimpleRecordSchema;
import org.apache.nifi.serialization.record.*;
import org.apache.nifi.serialization.record.type.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MappingOutputSchemaTest {
    private static final RecordSchema BASE=new SimpleRecordSchema(List.of(new RecordField("value",RecordFieldType.STRING.getDataType())));
    @Test void objectAndNumericJsonValuesRetainTheirActualTypes() {
        var object=MappingOutputSchema.resolve(BASE,Map.of("value","ANY"),List.of(Map.of("value",Map.of("a",1,"b",List.of(true)))));
        var map=(MapDataType)object.getField("value").orElseThrow().getDataType();
        assertEquals(RecordFieldType.CHOICE,map.getValueType().getFieldType());
        var numeric=MappingOutputSchema.resolve(BASE,Map.of("value","ANY"),List.of(Map.of("value",12)));
        assertEquals(RecordFieldType.INT,numeric.getField("value").orElseThrow().getDataType().getFieldType());
    }
    @Test void oneColumnCanHaveDifferentTypesInTheSameWindowWithoutStringifyingValues() {
        var schema=MappingOutputSchema.resolve(BASE,Map.of("value","ANY"),List.of(Map.of("value","12"),Map.of("value",12),Map.of("value",false),Map.of("value",List.of(1,"a"))));
        var choice=(ChoiceDataType)schema.getField("value").orElseThrow().getDataType();
        assertEquals(Set.of(RecordFieldType.STRING,RecordFieldType.INT,RecordFieldType.BOOLEAN,RecordFieldType.ARRAY),new HashSet<>(choice.getPossibleSubTypes().stream().map(DataType::getFieldType).toList()));
    }
    @Test void explicitContractsDoNotGetReinferred() {
        assertEquals(RecordFieldType.STRING,MappingOutputSchema.resolve(BASE,Map.of("value","STRING"),List.of(Map.of("value",12))).getField("value").orElseThrow().getDataType().getFieldType());
    }
    @Test void decimalValuesUseOneSufficientPrecisionInsteadOfDuplicateAvroUnionTypes() {
        var schema=MappingOutputSchema.resolve(BASE,Map.of("value","ANY"),List.of(Map.of("value",new java.math.BigDecimal("1E+3")),Map.of("value",new java.math.BigDecimal("0.12345"))));
        var decimal=(DecimalDataType)schema.getField("value").orElseThrow().getDataType();assertEquals(9,decimal.getPrecision());assertEquals(5,decimal.getScale());
    }
    @Test void serializedAvroContractIncludesNullMapValuesAndArrayElements() throws Exception {
        var schema=MappingOutputSchema.resolve(BASE,Map.of("value","ANY"),List.of(Map.of("value",Map.of("a",Arrays.asList("12",12,true,null)))));
        var text=new com.fasterxml.jackson.databind.ObjectMapper().readTree(schema.getSchemaText().orElseThrow());
        var map=text.path("fields").get(0).path("type").get(1);assertEquals("map",map.path("type").asText());
        var array=map.path("values").get(1);assertEquals("array",array.path("type").asText());assertEquals("null",array.path("items").get(0).asText());
    }
}
