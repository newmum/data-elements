package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.linewell.dataelement.mapping.core.*;
import org.apache.nifi.serialization.RecordReader;
import org.apache.nifi.serialization.SimpleRecordSchema;
import org.apache.nifi.serialization.record.*;
import org.apache.nifi.serialization.record.Record;
import org.apache.nifi.serialization.record.type.*;
import java.io.*;
import java.time.*;
import java.util.*;

/** Controlled NDJSON error records can re-enter without becoming ordinary business columns. */
final class MappingErrorRecords implements RecordReader {
    static final String ENVELOPE = "ht.invalid.envelope.version";
    static final String SCHEMA = "ht.invalid.input.schema";
    static final String ATTEMPT = "ht.error.loop.attempt";
    static final String NEXT = "ht.error.loop.nextAt";
    static final String RELATIONSHIP = "ht.error.loop.relationship";
    static final Set<String> TRANSIENT_ATTRIBUTES = Set.of(ENVELOPE, SCHEMA, ATTEMPT, NEXT, RELATIONSHIP,
            "ht.error.code", "ht.error.message", "ht.error.cause.type", "ht.retry.attempt", "ht.retry.nextAt", "ht.invalid.count");
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final MappingIterator<JsonNode> iterator;
    private final String hash;
    private final RecordSchema schema;
    private JsonNode pending;
    private long index;

    MappingErrorRecords(InputStream in, Map<String,String> attributes, MappingEngine.Plan plan) throws IOException {
        hash = plan.hash();
        iterator = JSON.readerFor(JsonNode.class).readValues(in);
        pending = iterator.hasNextValue() ? iterator.nextValue() : null;
        if (pending != null) validate(pending);
        String stored = attributes.get(SCHEMA);
        schema = stored != null ? readSchema(JSON.readTree(stored), 0) : legacySchema(plan, pending);
    }

    static boolean isEnvelope(Map<String,String> attributes, String context) {
        return "1".equals(attributes.get(ENVELOPE)) || ("application/x-ndjson".equals(attributes.get("mime.type"))
                && attributes.containsKey("ht.plan.hash") && context.equals(attributes.get("ht.plan.node.id")));
    }

    long originalIndex() { return index; }
    public RecordSchema getSchema() { return schema; }
    public void close() throws IOException { iterator.close(); }
    @Override public Record nextRecord(boolean coerce, boolean dropUnknown) throws IOException {
        JsonNode envelope = pending;
        pending = null;
        if (envelope == null && iterator.hasNextValue()) envelope = iterator.nextValue();
        if (envelope == null) return null;
        validate(envelope);
        index = envelope.path("inputRecordIndex").longValue();
        Map<String,Object> row = JSON.convertValue(envelope.path("record"), Map.class);
        Object restored = restore(row, "", envelope.path("recordTypes"), 0);
        @SuppressWarnings("unchecked") Map<String,Object> values = (Map<String,Object>) restored;
        // Old envelopes did not carry native tags. Only declared temporal fields may interpret numeric epochs.
        if (!envelope.has("recordTypes")) for (RecordField field : schema.getFields()) {
            Object value = values.get(field.getFieldName());
            if (value instanceof Number number) switch (field.getDataType().getFieldType()) {
                case TIMESTAMP -> values.put(field.getFieldName(), new java.sql.Timestamp(number.longValue()));
                case DATE -> values.put(field.getFieldName(), new java.sql.Date(number.longValue()));
                case TIME -> values.put(field.getFieldName(), new java.sql.Time(number.longValue()));
                default -> { }
            }
        }
        return new MapRecord(schema, values);
    }

    private void validate(JsonNode envelope) {
        if (!envelope.isObject() || !envelope.path("record").isObject() || !envelope.path("errors").isArray()
                || !envelope.path("inputRecordIndex").isIntegralNumber() || !envelope.path("inputRecordIndex").canConvertToLong()
                || envelope.path("inputRecordIndex").longValue() < 0 || !hash.equals(envelope.path("planHash").asText()))
            throw new MappingException("INVALID_ERROR_ENVELOPE", "错误信封结构或原执行计划不一致，原内容已保留");
    }

    static void snapshot(Map<String,Object> envelope, Map<String,Object> row) {
        Map<String,Object> types = new LinkedHashMap<>();
        envelope.put("record", snapshotValue(row, "", types, 0));
        envelope.put("recordTypes", types);
    }
    private static Object snapshotValue(Object value, String path, Map<String,Object> types, int depth) {
        checkDepth(depth);
        String type = null; Object encoded = value;
        if (value instanceof java.sql.Timestamp timestamp) { type="SQL_TIMESTAMP"; encoded=timestamp.toInstant().toString(); }
        else if (value instanceof java.sql.Date date) { type="SQL_DATE"; encoded=Long.toString(date.getTime()); }
        else if (value instanceof java.sql.Time time) { type="SQL_TIME"; encoded=Long.toString(time.getTime()); }
        else if (value instanceof java.util.Date date) { type="UTIL_DATE"; encoded=Long.toString(date.getTime()); }
        else if (value instanceof LocalDate) { type="LOCAL_DATE"; encoded=value.toString(); }
        else if (value instanceof LocalDateTime) { type="LOCAL_DATETIME"; encoded=value.toString(); }
        else if (value instanceof LocalTime) { type="LOCAL_TIME"; encoded=value.toString(); }
        else if (value instanceof Instant) { type="INSTANT"; encoded=value.toString(); }
        else if (value instanceof OffsetDateTime) { type="OFFSET_DATETIME"; encoded=value.toString(); }
        else if (value instanceof ZonedDateTime) { type="ZONED_DATETIME"; encoded=value.toString(); }
        else if (value instanceof byte[] bytes) { type="BYTES"; encoded=Base64.getEncoder().encodeToString(bytes); }
        else if (value instanceof UUID) { type="UUID"; encoded=value.toString(); }
        if (type != null) { types.put(path,type); return encoded; }
        if (value instanceof Record record) value=record.toMap();
        if (value instanceof Map<?,?> map) {
            Map<String,Object> result=new LinkedHashMap<>();
            for(var item:map.entrySet()) { String key=String.valueOf(item.getKey()); result.put(key,snapshotValue(item.getValue(),pointer(path,key),types,depth+1)); }
            return result;
        }
        if (value instanceof Object[] array) value=Arrays.asList(array);
        if (value instanceof Collection<?> list) {
            List<Object> result=new ArrayList<>(); int i=0;
            for(Object item:list) result.add(snapshotValue(item,pointer(path,Integer.toString(i++)),types,depth+1));
            return result;
        }
        return value;
    }
    private static Object restore(Object value, String path, JsonNode types, int depth) {
        checkDepth(depth);
        String text=Objects.toString(value,"");
        String type=types.path(path).asText("");
        if (!type.isBlank()) return switch(type) {
            case "SQL_TIMESTAMP" -> java.sql.Timestamp.from(Instant.parse(text));
            case "SQL_DATE" -> new java.sql.Date(Long.parseLong(text));
            case "SQL_TIME" -> new java.sql.Time(Long.parseLong(text));
            case "UTIL_DATE" -> new java.util.Date(Long.parseLong(text));
            case "LOCAL_DATE" -> LocalDate.parse(text);
            case "LOCAL_DATETIME" -> LocalDateTime.parse(text);
            case "LOCAL_TIME" -> LocalTime.parse(text);
            case "INSTANT" -> Instant.parse(text);
            case "OFFSET_DATETIME" -> OffsetDateTime.parse(text);
            case "ZONED_DATETIME" -> ZonedDateTime.parse(text);
            case "BYTES" -> Base64.getDecoder().decode(text);
            case "UUID" -> UUID.fromString(text);
            default -> throw new MappingException("INVALID_ERROR_ENVELOPE", "错误信封原始类型标记无效");
        };
        if (value instanceof Map<?,?> map) {
            Map<String,Object> result=new LinkedHashMap<>();
            for(var item:map.entrySet()) { String key=String.valueOf(item.getKey());result.put(key,restore(item.getValue(),pointer(path,key),types,depth+1)); }
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result=new ArrayList<>();for(int i=0;i<list.size();i++)result.add(restore(list.get(i),pointer(path,Integer.toString(i)),types,depth+1));return result;
        }
        return value;
    }
    private static String pointer(String path,String key) { return path+"/"+key.replace("~","~0").replace("/","~1"); }
    private static void checkDepth(int depth) { if(depth>32)throw new MappingException("BUDGET_EXCEEDED","错误信封嵌套超过32层"); }

    static String schemaText(RecordSchema schema) throws IOException { return JSON.writeValueAsString(schemaNode(schema,0)); }
    private static ArrayNode schemaNode(RecordSchema schema,int depth) {
        checkDepth(depth); ArrayNode fields=JSON.createArrayNode();
        for(var field:schema.getFields()) {
            ObjectNode node=fields.addObject().put("name",field.getFieldName()).put("nullable",field.isNullable());
            node.set("dataType",typeNode(field.getDataType(),depth+1));
        }
        return fields;
    }
    private static ObjectNode typeNode(DataType type,int depth) {
        checkDepth(depth);ObjectNode node=JSON.createObjectNode().put("type",type.getFieldType().name());
        if(type instanceof DecimalDataType decimal)node.put("precision",decimal.getPrecision()).put("scale",decimal.getScale());
        if(type instanceof RecordDataType record)node.set("fields",schemaNode(record.getChildSchema(),depth+1));
        if(type instanceof ArrayDataType array) {node.set("element",typeNode(array.getElementType(),depth+1));node.put("nullable",array.isElementsNullable());}
        if(type instanceof MapDataType map) {node.set("element",typeNode(map.getValueType(),depth+1));node.put("nullable",map.isValuesNullable());}
        if(type instanceof ChoiceDataType choice) {ArrayNode options=node.putArray("options");choice.getPossibleSubTypes().forEach(option->options.add(typeNode(option,depth+1)));}
        return node;
    }
    private static RecordSchema readSchema(JsonNode fields,int depth) {
        checkDepth(depth);if(!fields.isArray())throw new MappingException("INVALID_ERROR_ENVELOPE","缺少错误记录原始Schema");
        List<RecordField> result=new ArrayList<>();for(JsonNode field:fields)result.add(new RecordField(field.path("name").asText(),readType(field.path("dataType"),depth+1),field.path("nullable").asBoolean(true)));
        return new SimpleRecordSchema(result);
    }
    private static DataType readType(JsonNode node,int depth) {
        checkDepth(depth);RecordFieldType type=RecordFieldType.valueOf(node.path("type").asText());
        return switch(type) {
            case DECIMAL -> type.getDecimalDataType(node.path("precision").asInt(),node.path("scale").asInt());
            case RECORD -> type.getRecordDataType(readSchema(node.path("fields"),depth+1));
            case ARRAY -> type.getArrayDataType(readType(node.path("element"),depth+1),node.path("nullable").asBoolean(true));
            case MAP -> type.getMapDataType(readType(node.path("element"),depth+1),node.path("nullable").asBoolean(true));
            case CHOICE -> {List<DataType> options=new ArrayList<>();node.path("options").forEach(option->options.add(readType(option,depth+1)));yield type.getChoiceDataType(options);}
            case ENUM -> RecordFieldType.STRING.getDataType();
            default -> type.getDataType();
        };
    }
    private static RecordSchema legacySchema(MappingEngine.Plan plan,JsonNode first) {
        List<RecordField> fields=new ArrayList<>();
        for(var field:SchemaContract.read(plan.document().path("schemaContract").path("source"))) {
            RecordFieldType type=switch(field.logicalType()) {case "DATETIME","INSTANT"->RecordFieldType.TIMESTAMP;case "DATE"->RecordFieldType.DATE;case "TIME"->RecordFieldType.TIME;case "INT"->RecordFieldType.INT;case "LONG"->RecordFieldType.LONG;case "DECIMAL"->RecordFieldType.DECIMAL;case "DOUBLE"->RecordFieldType.DOUBLE;case "BOOLEAN"->RecordFieldType.BOOLEAN;default->RecordFieldType.STRING;};
            DataType data=type==RecordFieldType.DECIMAL?type.getDecimalDataType(Math.max(1,field.precision()),Math.max(0,field.scale())):type.getDataType();
            fields.add(new RecordField(field.name(),data,field.nullable()));
        }
        if(fields.isEmpty()&&first!=null)first.path("record").fieldNames().forEachRemaining(name->fields.add(new RecordField(name,RecordFieldType.STRING.getDataType())));
        return new SimpleRecordSchema(fields);
    }
}
