package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.core.*;
import com.linewell.dataelement.mapping.core.MappingException;
import com.linewell.dataelement.mapping.core.RecordBudget;
import java.io.*;
import java.util.*;

/** Restore exact numeric tokens and explicitly declared ISO SQL dates alongside the selected JSON Reader. */
final class ExactJsonNumbers implements AutoCloseable {
    private static final JsonFactory JSON = JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
            .maxStringLength((int) RecordBudget.RECORD_BYTES).maxNumberLength(1000).maxNestingDepth(40).build()).build();
    private final JsonParser parser;
    private final boolean array;
    private JsonToken pending;
    private Set<String> trackedNullFields=Set.of();
    private Set<String> trackedIsoDateFields=Set.of();
    private final Set<String> seenIsoDateFields=new HashSet<>();
    private static final java.util.regex.Pattern ISO_DATE=java.util.regex.Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private final Set<String> seenNullFields=new HashSet<>(),explicitNullFields=new HashSet<>();

    ExactJsonNumbers(InputStream input) throws IOException {
        parser = JSON.createParser(input);
        pending = parser.nextToken();
        array = pending == JsonToken.START_ARRAY;
        if (array) pending = parser.nextToken();
    }

    void restore(Map<String, Object> row) throws IOException {
        if (pending != JsonToken.START_OBJECT) throw mismatch();
        seenNullFields.clear();explicitNullFields.clear();
        seenIsoDateFields.clear();
        value(row, 0);
        pending = parser.nextToken();
    }

    void trackNullFields(Collection<String> names) {
        if(names.size()>2000)throw new MappingException("BUDGET_EXCEEDED","NULL来源字段超过2000个");
        trackedNullFields=Set.copyOf(names);
    }

    boolean explicitNull(String name) { return explicitNullFields.contains(name); }
    void trackIsoDateFields(Collection<String> names) {
        if(names.size()>2000)throw new MappingException("BUDGET_EXCEEDED","DATE来源字段超过2000个");
        trackedIsoDateFields=Set.copyOf(names);
    }

    void requireEnd() throws IOException {
        if (array) {
            if (pending != JsonToken.END_ARRAY || parser.nextToken() != null) throw mismatch();
        } else if (pending != null) throw mismatch();
    }

    private Object value(Object decoded, int depth) throws IOException {
        if (depth > 32) throw new MappingException("BUDGET_EXCEEDED", "JSON输入记录嵌套超过32层");
        JsonToken token = parser.currentToken();
        if (token == JsonToken.START_OBJECT) {
            if (!(decoded instanceof Map<?, ?>)) { parser.skipChildren(); return decoded; }
            @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) decoded;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) throw mismatch();
                String name = parser.currentName();
                if (parser.nextToken() == null) throw mismatch();
                if(depth==0&&trackedIsoDateFields.contains(name)&&!seenIsoDateFields.add(name))
                    throw new MappingException("AMBIGUOUS_FIELD","JSON输入包含重复的已声明DATE字段，原输入已保留");
                if(depth==0&&trackedNullFields.contains(name)) {
                    // Duplicate names never prove a nullable column's original value.
                    if(!seenNullFields.add(name))explicitNullFields.remove(name);
                    else if(parser.currentToken()==JsonToken.VALUE_NULL)explicitNullFields.add(name);
                }
                // Reader filtering and custom representations remain authoritative; do not insert fields.
                if (map.containsKey(name)) map.put(name, value(map.get(name), depth + 1));
                else parser.skipChildren();
            }
        } else if (token == JsonToken.START_ARRAY) {
            if (decoded instanceof Object[] items && items.getClass().getComponentType() == Object.class) {
                int index = 0;
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    if (parser.currentToken() == null || index >= items.length) throw mismatch();
                    items[index] = value(items[index], depth + 1); index++;
                }
                if (index != items.length) throw mismatch();
            } else if (decoded instanceof List<?> items) {
                @SuppressWarnings("unchecked") List<Object> mutable = (List<Object>) items;
                int index = 0;
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    if (parser.currentToken() == null || index >= mutable.size()) throw mismatch();
                    mutable.set(index, value(mutable.get(index), depth + 1)); index++;
                }
                if (index != mutable.size()) throw mismatch();
            } else parser.skipChildren(); // Preserve byte[], Byte[] and explicitly typed Reader values.
        } else if (token == JsonToken.VALUE_NUMBER_FLOAT && decoded instanceof Number) {
            return parser.getDecimalValue();
        } else if (token == JsonToken.VALUE_NUMBER_INT && decoded instanceof Number) {
            return parser.getNumberValue();
        } else if (token == JsonToken.VALUE_STRING) {
            parser.finishToken();
            // NiFi 2.11 can create a legacy SQL Date carrier from a java.time historical
            // instant. Preserve the original calendar text instead of shifting its date.
            // Other Reader representations, nested values and custom formats stay intact.
            if(depth==1&&decoded instanceof java.sql.Date&&trackedIsoDateFields.contains(parser.currentName())){
                String text=parser.getText();if(ISO_DATE.matcher(text).matches())return text;
            }
        } else if (token == null || token == JsonToken.END_ARRAY || token == JsonToken.END_OBJECT) throw mismatch();
        return decoded;
    }

    private static MappingException mismatch() {
        return new MappingException("JSON_NUMBER_CODEC_MISMATCH", "JSON数字适配与Reader记录边界不一致，原输入已保留");
    }

    @Override public void close() throws IOException { parser.close(); }
}
