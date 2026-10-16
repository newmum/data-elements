package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.apache.nifi.serialization.SimpleRecordSchema;
import org.apache.nifi.serialization.record.*;
import org.apache.nifi.serialization.record.type.*;
import java.util.*;

/** NiFi 2.x's generated Avro schema drops array/map child nullability; supply the full contract. */
final class MappingAvroSchema {
    private static final ObjectMapper JSON=new ObjectMapper();
    private MappingAvroSchema() {}
    static RecordSchema attach(RecordSchema schema){
        JsonNode text=record(schema,new int[]{0});
        return new SimpleRecordSchema(schema.getFields(),text.toString(),"avro",schema.getIdentifier());
    }
    private static JsonNode record(RecordSchema schema,int[]names){
        ObjectNode record=JSON.createObjectNode().put("type","record").put("name","MappingOutput"+names[0]++);ArrayNode fields=record.putArray("fields");
        for(RecordField field:schema.getFields())fields.addObject().put("name",field.getFieldName()).set("type",nullable(type(field.getDataType(),names)));
        return record;
    }
    private static JsonNode type(DataType data,int[]names){
        return switch(data.getFieldType()){
            case BOOLEAN->JSON.getNodeFactory().textNode("boolean");
            case BYTE,SHORT,INT->JSON.getNodeFactory().textNode("int");
            case LONG->JSON.getNodeFactory().textNode("long");
            case FLOAT->JSON.getNodeFactory().textNode("float");
            case DOUBLE->JSON.getNodeFactory().textNode("double");
            case DATE->JSON.createObjectNode().put("type","int").put("logicalType","date");
            case TIME->JSON.createObjectNode().put("type","int").put("logicalType","time-millis");
            case TIMESTAMP->JSON.createObjectNode().put("type","long").put("logicalType","timestamp-millis");
            case DECIMAL->{var decimal=(DecimalDataType)data;yield JSON.createObjectNode().put("type","bytes").put("logicalType","decimal").put("precision",decimal.getPrecision()).put("scale",decimal.getScale());}
            case ARRAY->JSON.createObjectNode().put("type","array").set("items",nullable(type(((ArrayDataType)data).getElementType(),names)));
            case MAP->JSON.createObjectNode().put("type","map").set("values",nullable(type(((MapDataType)data).getValueType(),names)));
            case RECORD->record(((RecordDataType)data).getChildSchema(),names);
            case CHOICE->{ArrayNode options=JSON.createArrayNode();Set<String>seen=new HashSet<>();for(DataType option:((ChoiceDataType)data).getPossibleSubTypes()){
                JsonNode value=type(option,names);String family=value.isTextual()?value.asText():value.path("type").asText();
                if(!seen.add(family))throw new com.linewell.dataelement.mapping.core.MappingException("SCHEMA_TYPE_CONFLICT","同一动态输出含无法共存的Avro逻辑类型，请选择明确输出类型");options.add(value);
            }yield options;}
            case ENUM->JSON.createObjectNode().put("type","enum").put("name","MappingEnum"+names[0]++).set("symbols",JSON.valueToTree(((EnumDataType)data).getEnums()));
            default->JSON.getNodeFactory().textNode("string");
        };
    }
    private static ArrayNode nullable(JsonNode type){ArrayNode union=JSON.createArrayNode().add("null");if(type.isArray())type.forEach(union::add);else union.add(type);return union;}
}
