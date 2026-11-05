package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.mapping.core.JdbcTemporalTypes;
import com.linewell.dataelement.mapping.core.ValueTypes;
import com.linewell.dataelement.mapping.core.MappingException;
import java.util.*;

/** Reuse canvas labels/binary input hints; physical target types remain server-authorized metadata. */
final class MappingFieldLabels {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final Map<String,Pipeline.Node> nodes=new HashMap<>();
    private final Map<String,List<String>> incoming=new HashMap<>(),outgoing=new HashMap<>();
    MappingFieldLabels(Pipeline pipeline) {
        pipeline.dsl().nodes().forEach(node->nodes.put(node.id(),node));
        if(pipeline.dsl().edges()!=null)pipeline.dsl().edges().forEach(edge->{incoming.computeIfAbsent(edge.target(),key->new ArrayList<>()).add(edge.source());outgoing.computeIfAbsent(edge.source(),key->new ArrayList<>()).add(edge.target());});
    }
    JsonNode enrich(String node,JsonNode raw) {
        if(!(raw instanceof ObjectNode))return raw;
        ObjectNode spec=raw.deepCopy();Map<String,String> source=labels(node,true),target=labels(node,false);
        Set<String> millisFields=jdbcMillisFields(node);
        for(JsonNode rule:spec.path("rules"))annotate(rule,source,target,millisFields);
        for(JsonNode rule:spec.path("mappings"))annotate(rule,source,target,millisFields);
        Set<String> binaryFields=directBinaryFields(node);
        for(String collection:List.of("rules","mappings"))for(JsonNode rule:spec.path(collection))binaryInput(rule,binaryFields);
        if(hasBinary(spec,0))bindBinaryTarget(node,spec);
        for(String side:List.of("source","target"))for(JsonNode field:spec.path("schemaContract").path(side))
            putComment(field,"comment",lookup(side.equals("source")?source:target,field.path("name").asText()));
        return spec;
    }
    private Set<String> directBinaryFields(String node){
        var parents=incoming.getOrDefault(node,List.of());if(parents.size()!=1)return Set.of();
        var source=nodes.get(parents.getFirst());if(source==null||!"source".equals(source.category()))return Set.of();
        Set<String> fields=new HashSet<>();
        for(JsonNode field:parse(source.config()==null?null:source.config().get("sourceColumns"))){
            String type=field.path("dataType").asText(field.path("columnType").asText(field.path("typeName").asText(field.path("type").asText())));
            if(ValueTypes.type(type).equals("BINARY"))fields.add(field.path("columnName").asText(field.path("name").asText()).toLowerCase(Locale.ROOT));
        }return fields;
    }
    private static void binaryInput(JsonNode rule,Set<String> fields){
        String input=rule.path("input").path("kind").asText().equals("FIELD")?rule.path("input").path("name").asText():rule.path("from").asText();
        if(fields.contains(input.replaceFirst("^/","").toLowerCase(Locale.ROOT))){
            if(rule.path("input") instanceof ObjectNode object)object.put("logicalType","BINARY");
            else if(rule instanceof ObjectNode object)object.put("sourceType","BINARY");
        }
        for(String branch:List.of("then","else"))if(rule.has(branch))binaryInput(rule.path(branch),fields);
    }
    private static boolean hasBinary(JsonNode node,int depth){
        if(node==null||!node.isContainerNode())return false;
        if(depth>32)throw new MappingException("BUDGET_EXCEEDED","二进制规则结构超过32层");
        for(String key:List.of("sourceType","targetType","logicalType"))if(ValueTypes.type(node.path(key).asText()).equals("BINARY"))return true;
        for(JsonNode child:node)if(hasBinary(child,depth+1))return true;return false;
    }
    private void bindBinaryTarget(String node,ObjectNode spec){
        var sinks=outgoing.getOrDefault(node,List.of()).stream().distinct().map(nodes::get).filter(Objects::nonNull).filter(target->"sink.jdbc".equals(target.manifestKey())).toList();
        if(sinks.isEmpty())return;
        if(sinks.size()!=1)throw new MappingException("BINARY_TARGET_AMBIGUOUS","二进制治理节点连接多个JDBC目标，请分别配置目标分支以确定BLOB/CLOB载体");
        Map<String,Object> config=sinks.getFirst().config()==null?Map.of():sinks.getFirst().config();
        String datasource=first(config,"registeredDatasourceId","selectedDatabaseId","targetDbId","datasourceId","dataSourceId","dbId"),table=first(config,"targetTableId","tableId");
        ObjectNode contract=spec.path("schemaContract") instanceof ObjectNode object?object:spec.putObject("schemaContract");
        if(datasource.isBlank()||table.isBlank()){
            if(contract.path("target").isArray())return;
            throw new MappingException("SCHEMA_SCOPE_REQUIRED","二进制图片写入须选择已登记的目标数据源和目标表，以确认实际BLOB/CLOB类型");
        }
        JsonNode existing=contract.path("targetRef");
        if(existing.isObject()&&(!datasource.equals(existing.path("datasourceId").asText())||!table.equals(existing.path("tableId").asText())))
            throw new MappingException("TARGET_REFERENCE_MISMATCH","字段治理目标引用与实际JDBC目标不一致，请重新选择目标表");
        contract.putObject("targetRef").put("datasourceId",datasource).put("tableId",table);
        if(!contract.path("target").isArray()){
            var aliases=contract.putArray("targetNameAliases");Set<String> names=new LinkedHashSet<>();
            for(String collection:List.of("rules","mappings"))for(JsonNode rule:spec.path(collection)){
                String name=rule.path("target").path("name").asText(rule.path("to").asText()).replaceFirst("^/","");
                if(!name.isBlank())names.add(ValueTypes.field(name));
            }names.forEach(aliases::add);
        }
    }
    private static String first(Map<String,Object> values,String...keys){for(String key:keys){Object value=values.get(key);if(value!=null&&!value.toString().isBlank())return value.toString().trim();}return "";}
    private Set<String> jdbcMillisFields(String node) {
        var parents=incoming.getOrDefault(node,List.of());
        if(parents.size()!=1)return Set.of();
        var source=nodes.get(parents.getFirst());
        // A transform may rename or replace values; never infer transport units through it.
        if(source==null||!source.category().equals("source")||!JdbcTemporalTypes.oracleDateHasTime(source.config()))return Set.of();
        Set<String> result=new HashSet<>();
        for(JsonNode field:parse(source.config().get("sourceColumns"))) {
            String type=field.path("dataType").asText(field.path("columnType").asText(field.path("typeName").asText(field.path("type").asText())));
            if(ValueTypes.type(type).equals("DATE"))result.add(field.path("columnName").asText(field.path("name").asText()).toLowerCase(Locale.ROOT));
        }
        return result;
    }
    private Map<String,String> labels(String start,boolean source) {
        Map<String,String> result=new HashMap<>();Set<String> conflicts=new HashSet<>(),visited=new HashSet<>();
        var graph=source?incoming:outgoing;var pending=new ArrayDeque<>(graph.getOrDefault(start,List.of()));
        while(!pending.isEmpty()) {
            String id=pending.removeFirst();if(!visited.add(id))continue;var node=nodes.get(id);if(node==null)continue;
            boolean boundary=(source?"source":"sink").equals(node.category());
            if(boundary) {
                JsonNode columns=parse(node.config()==null?null:node.config().get(source?"sourceColumns":"targetColumns"));
                for(JsonNode field:columns) {
                    String name=field.path("columnName").asText(field.path("name").asText()).toLowerCase(Locale.ROOT);
                    String comment=field.path("columnComment").asText(field.path("comment").asText());
                    if(name.isBlank()||comment.isBlank())continue;
                    String previous=result.putIfAbsent(name,comment);if(previous!=null&&!previous.equals(comment))conflicts.add(name);
                }
            }else pending.addAll(graph.getOrDefault(id,List.of()));
        }
        conflicts.forEach(result::remove);return result;
    }
    private static JsonNode parse(Object value) {
        try{return value instanceof String text?JSON.readTree(text):JSON.valueToTree(value);}
        catch(Exception ignored){return JSON.createArrayNode();}
    }
    private static String lookup(Map<String,String> comments,String name){return comments.getOrDefault(name.replaceFirst("^/","").toLowerCase(Locale.ROOT),"");}
    private static void putComment(JsonNode node,String key,String value){if(node instanceof ObjectNode object&&!value.isBlank()&&node.path(key).asText().isBlank())object.put(key,value);}
    private static void annotate(JsonNode rule,Map<String,String> source,Map<String,String> target,Set<String> millisFields) {
        if(rule.has("from"))putComment(rule,"fromLabel",lookup(source,rule.path("from").asText()));
        if(rule.has("to"))putComment(rule,"toLabel",lookup(target,rule.path("to").asText()));
        if(rule.path("input").path("kind").asText().equals("FIELD"))putComment(rule.path("input"),"comment",lookup(source,rule.path("input").path("name").asText()));
        putComment(rule.path("target"),"comment",lookup(target,rule.path("target").path("name").asText()));
        String input=rule.path("input").path("kind").asText().equals("FIELD")?rule.path("input").path("name").asText():rule.path("from").asText();
        if(millisFields.contains(input.replaceFirst("^/","").toLowerCase(Locale.ROOT)))JdbcTemporalTypes.annotateMillisInput(rule);
        for(String branch:List.of("then","else"))if(rule.has(branch))annotate(rule.path(branch),source,target,millisFields);
    }
}
