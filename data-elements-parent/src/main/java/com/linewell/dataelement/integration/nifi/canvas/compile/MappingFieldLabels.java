package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.mapping.core.JdbcTemporalTypes;
import com.linewell.dataelement.mapping.core.ValueTypes;
import java.util.*;

/** Reuse owned canvas column snapshots for older rules without changing their schema/type policies. */
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
        for(String side:List.of("source","target"))for(JsonNode field:spec.path("schemaContract").path(side))
            putComment(field,"comment",lookup(side.equals("source")?source:target,field.path("name").asText()));
        return spec;
    }
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
