package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

/** Shared structured-message aliases and their original JSON paths; no database or transport access. */
public final class StructuredJsonRecords {
    public static final int VERSION = 1;
    public static final int MAX_FIELDS = 2000;
    private static final ObjectMapper JSON = new ObjectMapper();
    private StructuredJsonRecords() { }

    public record Entry(Map<String,Object> values, Map<String,List<String>> paths,
                        Map<String,String> kinds, Set<List<String>> containers, String format) { }

    public static String alias(String raw) {
        String value = raw.trim().replaceAll("[^\\p{L}\\p{N}_]+", "_")
                .replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        if (value.isBlank()) value = "column";
        if (Character.isDigit(value.charAt(0))) value = "f_" + value;
        if (value.length() > 255) throw failure("BUDGET_EXCEEDED", "规范字段名超过255字符");
        return value;
    }

    public static Entry normalize(JsonNode value) {
        Map<String,Object> values = new LinkedHashMap<>();
        Map<String,List<String>> paths = new LinkedHashMap<>();
        Map<String,String> kinds = new LinkedHashMap<>();
        Set<List<String>> containers = new LinkedHashSet<>();
        Set<String> names = new HashSet<>();
        if (value.isObject() && !value.isEmpty()) flatten(value, "", List.of(), values, paths, kinds, containers, names, 0);
        else put("value", List.of(), value, values, paths, kinds, names);
        RecordBudget.record(values, "结构化来源记录");
        return new Entry(Collections.unmodifiableMap(values), Collections.unmodifiableMap(paths),
                Collections.unmodifiableMap(kinds), Collections.unmodifiableSet(containers), "JSON");
    }

    public static Entry text(String value) {
        Map<String,Object> values = Collections.singletonMap("value", value);
        RecordBudget.record(values, "文本来源记录");
        return new Entry(values, Map.of("value", List.of()), Map.of("value", "STRING"), Set.of(), "TEXT");
    }

    /** Flat CSV/Excel headers are literal fields, including dots, spaces and leading digits. */
    public static Entry record(Map<String,Object> source) {
        return record(source,StructuredJsonRecords::alias);
    }
    private static Entry record(Map<String,Object> source, java.util.function.Function<String,String> rename) {
        Map<String,Object> values=new LinkedHashMap<>();Map<String,List<String>> paths=new LinkedHashMap<>();
        Map<String,String> kinds=new LinkedHashMap<>();Set<String> names=new HashSet<>();
        source.forEach((raw,value)->{
            String name=rename.apply(raw);
            if(!names.add(name.toLowerCase(Locale.ROOT)))throw failure("AMBIGUOUS_SOURCE_PATH","多个原始表头对应同一安全名: "+name);
            values.put(name,value);paths.put(name,List.of(raw));kinds.put(name,"ANY");
        });
        RecordBudget.record(values,"平面来源记录");
        return new Entry(Collections.unmodifiableMap(values),Collections.unmodifiableMap(paths),Collections.unmodifiableMap(kinds),Set.of(),"RECORD");
    }

    private static void flatten(JsonNode node, String prefix, List<String> path, Map<String,Object> values,
            Map<String,List<String>> paths, Map<String,String> kinds, Set<List<String>> containers,
            Set<String> names, int depth) {
        if (depth > 32) throw failure("BUDGET_EXCEEDED", "结构化来源嵌套超过32层");
        containers.add(path);
        node.fields().forEachRemaining(field -> {
            String name = alias(prefix.isEmpty() ? field.getKey() : prefix + "_" + field.getKey());
            List<String> child = new ArrayList<>(path); child.add(field.getKey()); child = List.copyOf(child);
            if (field.getValue().isObject() && !field.getValue().isEmpty())
                flatten(field.getValue(), name, child, values, paths, kinds, containers, names, depth + 1);
            else put(name, child, field.getValue(), values, paths, kinds, names);
        });
    }

    private static void put(String name, List<String> path, JsonNode value, Map<String,Object> values,
                            Map<String,List<String>> paths, Map<String,String> kinds, Set<String> names) {
        put(name,path,value,values,paths,kinds,names,null);
    }
    private static void put(String name, List<String> path, JsonNode value, Map<String,Object> values,
                            Map<String,List<String>> paths, Map<String,String> kinds, Set<String> names,RecordBudget.Output budget) {
        if (!names.add(name.toLowerCase(Locale.ROOT))) throw failure("AMBIGUOUS_SOURCE_PATH", "多个原始JSON路径映射到同一规范字段: " + name);
        if (values.size() >= MAX_FIELDS) throw failure("BUDGET_EXCEEDED", "结构化来源字段超过2000项");
        String kind = value.isNull() ? "NULL" : value.isObject() ? "OBJECT" : value.isArray() ? "ARRAY"
                : value.isBoolean() ? "BOOLEAN" : value.isNumber() ? "NUMBER" : "STRING";
        Object carrier = value.isNull() ? null : value.isContainerNode() ? jsonText(value)
                : value.isBoolean() ? value.booleanValue() : value.isIntegralNumber()
                ? value.canConvertToLong() ? value.longValue() : value.bigIntegerValue()
                : value.isNumber() ? value.decimalValue() : value.textValue();
        if(budget==null)values.put(name, carrier);else budget.put(name,carrier);
        paths.put(name, path); kinds.put(name, kind);
    }

    /** Bounds encoded container text before retaining it; ordinary strings are never JSON-encoded again. */
    public static String jsonText(Object value) {
        BoundedText writer = new BoundedText();
        try { JSON.writeValue(writer, value); return writer.text.toString(); }
        catch (IOException invalid) {
            Throwable cause=invalid;
            for(int depth=0;cause!=null&&depth<16;depth++,cause=cause.getCause())
                if(cause instanceof MappingException known)throw known;
            throw failure("TYPE_MISMATCH", "结构化值无法无损序列化为JSON文本");
        }
    }

    private static final class BoundedText extends Writer {
        final StringBuilder text = new StringBuilder(); long bytes; boolean high;
        @Override public void write(char[] buffer,int offset,int length) {
            for(int i=offset;i<offset+length;i++) {
                char c=buffer[i];bytes+=Character.isLowSurrogate(c)&&high?0:Character.isHighSurrogate(c)?4:c<128?1:c<2048?2:3;
                high=Character.isHighSurrogate(c);
                if(bytes>RecordBudget.RECORD_BYTES)throw failure("BUDGET_EXCEEDED","JSON文本超过记录预算，原始输入保留");
                text.append(c);
            }
        }
        @Override public void flush() { }
        @Override public void close() { }
    }

    public static final class Shape {
        private final Map<String,List<String>> paths = new LinkedHashMap<>();
        private final Map<String,String> aliases = new HashMap<>();
        private final Map<String,Set<String>> kinds = new LinkedHashMap<>();
        private final Set<String> formats = new LinkedHashSet<>();
        private long records;
        public void accept(Entry entry) {
            for (String existing : paths.keySet()) if (!entry.paths().containsKey(existing)) kinds.get(existing).add("NULL");
            for (var field : entry.paths().entrySet()) {
                String name = field.getKey();
                String prior = aliases.get(name.toLowerCase(Locale.ROOT));
                if (prior != null && (!prior.equals(name) || !paths.get(prior).equals(field.getValue())))
                    throw failure("AMBIGUOUS_SOURCE_PATH", "样本中的规范字段对应不同原始JSON路径: " + name);
                if (prior == null) {
                    if(paths.size()>=MAX_FIELDS)throw failure("BUDGET_EXCEEDED","结构化字段快照超过2000项");
                    paths.put(name, field.getValue()); kinds.put(name, new LinkedHashSet<>());
                    aliases.put(name.toLowerCase(Locale.ROOT),name);
                    if (records > 0) kinds.get(name).add("NULL");
                }
                kinds.get(name).add(entry.kinds().get(name));
            }
            formats.add(entry.format());records++;
        }
        public ObjectNode json() {
            ObjectNode result=JSON.createObjectNode().put("version",VERSION).put("mode",formats.equals(Set.of("RECORD"))?"RECORD":"JSON");
            result.set("fieldPaths",JSON.valueToTree(paths));result.set("fieldKinds",JSON.valueToTree(kinds));
            result.set("formats",JSON.valueToTree(formats));return result;
        }
    }

    /** A contract comes from the registered server snapshot, never by splitting a safe alias. */
    public static final class Contract {
        private final Map<String,List<String>> paths=new LinkedHashMap<>();
        private final Map<String,String> aliases=new HashMap<>();
        private final Map<List<String>,String> reverse=new HashMap<>();
        private final Map<String,Set<String>> kinds=new HashMap<>();
        private final String mode;
        private final boolean skipBlankRecords;
        private final Set<String> formats=new HashSet<>();
        private final JsonNode document;
        public Contract(JsonNode source) {
            if(source==null||source.path("version").asInt()!=VERSION||!source.path("fieldPaths").isObject())
                throw failure("SOURCE_SHAPE_REQUIRED","来源字段缺少有效的原始路径快照，请重新采集字段");
            mode=source.path("mode").asText("JSON");
            if(!Set.of("JSON","RECORD").contains(mode))throw failure("INVALID_SOURCE_SHAPE","来源记录模式无效");
            if (source.has("skipBlankRecords") && (!source.path("skipBlankRecords").isBoolean() || !mode.equals("RECORD"))) {
                throw failure("INVALID_SOURCE_SHAPE", "仅平面记录支持已登记的空行过滤");
            }
            skipBlankRecords = source.path("skipBlankRecords").asBoolean(false);
            Set<String> aliases=new HashSet<>();
            source.path("fieldPaths").fields().forEachRemaining(field->{
                String name=field.getKey();JsonNode path=field.getValue();
                if(!name.equals(alias(name))||!aliases.add(name.toLowerCase(Locale.ROOT))||!path.isArray()||path.size()>32||paths.size()>=MAX_FIELDS)
                    throw failure("INVALID_SOURCE_SHAPE","来源字段路径快照无效");
                List<String> parts=new ArrayList<>();
                for(JsonNode part:path){if(!part.isTextual()||part.textValue().length()>4096)throw failure("INVALID_SOURCE_SHAPE","来源路径段无效");parts.add(part.textValue());}
                List<String> identity=List.copyOf(parts);
                if(reverse.putIfAbsent(identity,name)!=null)throw failure("AMBIGUOUS_SOURCE_PATH","原始来源路径绑定多个安全名");
                paths.put(name,identity);this.aliases.put(name.toLowerCase(Locale.ROOT),name);Set<String> allowed=new HashSet<>();
                source.path("fieldKinds").path(name).forEach(kind->allowed.add(kind.asText()));
                if(mode.equals("RECORD"))allowed.add("ANY");
                if(allowed.isEmpty()||!Set.of("NULL","STRING","NUMBER","BOOLEAN","OBJECT","ARRAY","ANY").containsAll(allowed))
                    throw failure("INVALID_SOURCE_SHAPE","来源字段种类快照无效");
                kinds.put(name,Set.copyOf(allowed));
            });
            if(paths.isEmpty())throw failure("INVALID_SOURCE_SHAPE","来源路径快照没有字段");
            source.path("formats").forEach(value->formats.add(value.asText()));
            if(formats.isEmpty())formats.add(mode.equals("RECORD")?"RECORD":"JSON");
            if(!Set.of("JSON","TEXT","RECORD").containsAll(formats))throw failure("INVALID_SOURCE_SHAPE","来源消息格式无效");
            document=source.deepCopy();
        }
        public String mode(){return mode;}
        public boolean skipBlankRecords() { return skipBlankRecords; }
        public Set<String> formats(){return Set.copyOf(formats);}
        public String hash(){return MappingEngine.hash(document);}
        public String fieldName(String raw){return reverse.getOrDefault(List.of(raw),alias(raw));}
        public Entry require(Entry record) {
            if(!formats.contains(record.format()))throw failure("SOURCE_FORMAT_CHANGED","来源消息格式与登记快照不一致");
            for(var field:record.paths().entrySet()) {
                String name=field.getKey();String known=aliases.get(name.toLowerCase(Locale.ROOT));
                if(known==null)continue;
                if(!known.equals(name))throw failure("SOURCE_PATH_CHANGED","来源字段大小写已改变: "+known);
                if(!paths.get(name).equals(field.getValue()))throw failure("SOURCE_PATH_CHANGED","来源字段原始路径已改变: "+name);
                if(!kinds.get(name).contains("ANY")&&!kinds.get(name).contains(record.kinds().get(name)))
                    throw failure("SOURCE_KIND_CHANGED","来源字段原始种类已改变: "+name);
            }
            for(var field:paths.entrySet()) {
                if(record.containers().contains(field.getValue())
                        &&!(kinds.get(field.getKey()).contains("OBJECT")
                        &&"OBJECT".equals(record.kinds().get(field.getKey()))
                        &&field.getValue().equals(record.paths().get(field.getKey()))))
                    throw failure("SOURCE_KIND_CHANGED","来源叶字段变为嵌套对象: "+field.getKey());
                if(!record.paths().containsKey(field.getKey())&&!kinds.get(field.getKey()).contains("NULL")&&!mode.equals("RECORD"))
                    throw failure("SOURCE_FIELD_MISSING","来源缺少登记的非空字段: "+field.getKey());
            }
            return record;
        }
        public Entry normalize(JsonNode value){
            Entry record=StructuredJsonRecords.normalize(value);
            Map<String,Object> values=new LinkedHashMap<>(record.values());
            Map<String,List<String>> actualPaths=new LinkedHashMap<>(record.paths());
            Map<String,String> actualKinds=new LinkedHashMap<>(record.kinds());
            var budget=new RecordBudget.Output(values,new RecordBudget.OutputWindow());
            Set<String> names=new HashSet<>();actualPaths.keySet().forEach(name->names.add(name.toLowerCase(Locale.ROOT)));
            // A sampled empty object can register a parent alongside its children from other
            // samples. Retain that exact parent only when OBJECT was explicitly observed;
            // ordinary scalar leaves must still fail if they later become containers.
            for(var field:paths.entrySet()) {
                if(!kinds.get(field.getKey()).contains("OBJECT")||actualPaths.containsKey(field.getKey()))continue;
                JsonNode parent=value;
                for(String part:field.getValue()) {parent=parent==null?null:parent.get(part);if(parent==null)break;}
                if(parent!=null&&parent.isObject())put(field.getKey(),field.getValue(),parent,values,actualPaths,actualKinds,names,budget);
            }
            RecordBudget.record(values,"结构化来源记录");
            return require(new Entry(Collections.unmodifiableMap(values),Collections.unmodifiableMap(actualPaths),
                    Collections.unmodifiableMap(actualKinds),record.containers(),record.format()));
        }
        public Entry normalize(Map<String,Object> value){return require(StructuredJsonRecords.record(value,this::fieldName));}
    }

    private static MappingException failure(String code,String detail) { return new MappingException(code,detail); }
}
