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
    private final Map<String,List<String>> successOutgoing=new HashMap<>();
    MappingFieldLabels(Pipeline pipeline) {
        pipeline.dsl().nodes().forEach(node->nodes.put(node.id(),node));
        if(pipeline.dsl().edges()!=null)pipeline.dsl().edges().forEach(edge->{incoming.computeIfAbsent(edge.target(),key->new ArrayList<>()).add(edge.source());outgoing.computeIfAbsent(edge.source(),key->new ArrayList<>()).add(edge.target());
            if(!Set.of("failure","retry").contains(Objects.toString(edge.outlet(),"").toLowerCase(Locale.ROOT)))successOutgoing.computeIfAbsent(edge.source(),key->new ArrayList<>()).add(edge.target());});
    }
    JsonNode enrich(String node,JsonNode raw) {
        if(!(raw instanceof ObjectNode))return raw;
        ObjectNode spec=raw.deepCopy();Map<String,String> source=labels(node,true),target=labels(node,false);
        Set<String> millisFields=jdbcMillisFields(node);
        for(JsonNode rule:spec.path("rules"))annotate(rule,source,target,millisFields);
        for(JsonNode rule:spec.path("mappings"))annotate(rule,source,target,millisFields);
        Set<String> binaryFields=directBinaryFields(node);
        for(String collection:List.of("rules","mappings"))for(JsonNode rule:spec.path(collection))binaryInput(rule,binaryFields);
        var targets=sharedTargets(node);
        if(targets.size()>1){
            bindSharedTargets(targets,spec);
            bindSharedSource(node,(ObjectNode)spec.path("schemaContract"));
        }
        else if(hasBinary(spec,0))bindBinaryTarget(node,spec);
        for(String side:List.of("source","target"))for(JsonNode field:spec.path("schemaContract").path(side))
            putComment(field,"comment",lookup(side.equals("source")?source:target,field.path("name").asText()));
        return spec;
    }
    /** Only a direct, unambiguous registered table can prove the narrower input range at deployment. */
    private void bindSharedSource(String node,ObjectNode contract){
        ObjectNode registered=null;
        for(String parent:incoming.getOrDefault(node,List.of())){
            Pipeline.Node source=nodes.get(parent);
            ObjectNode reference=directJdbcReference(source);
            if(reference==null || registered!=null && !registered.equals(reference)){
                registered=null;
                break;
            }
            registered=reference;
        }
        JsonNode existing=contract.path("sourceRef");
        if(registered==null){
            if(existing.isObject())contract.put("sourceRangeUnverified",true);
            return;
        }
        if(existing.isObject() && (!existing.path("datasourceId").equals(registered.path("datasourceId"))
                || !existing.path("tableId").equals(registered.path("tableId"))))
            throw new MappingException("SOURCE_REFERENCE_MISMATCH","字段治理的来源引用与实际连接的登记表不一致，请重新读取来源结构");
        contract.remove("sourceRangeUnverified");
        if(!existing.isObject())contract.set("sourceRef",registered);
    }

    private static ObjectNode directJdbcReference(Pipeline.Node source){
        if(source==null || !"source".equals(source.category()) || source.config()==null)return null;
        var config=source.config();
        if(source.manifestKey()==null || !source.manifestKey().startsWith("source.")
                || !first(config,"customQuery","sql").isBlank())return null;
        // Share the registration type catalogue; a second source-name whitelist misses
        // GaussDB, ClickHouse, Hive and other supported JDBC products as they are added.
        try {
            var type=RegisteredDatasourceType.databaseType(source.manifestKey().substring("source.".length()),config);
            if(type==null || !type.isRelational()
                    || type==com.linewell.dataelement.metautil.model.enums.DatabaseType.MAXCOMPUTE)return null;
        }catch(IllegalArgumentException unsupported){return null;}
        String datasource=first(config,"registeredDatasourceId","sourceDbId"), table=first(config,"sourceTableId");
        if(datasource.isBlank() || table.isBlank())return null;
        return JSON.createObjectNode().put("datasourceId",datasource).put("tableId",table);
    }
    /** Discover terminal targets once in memory, crossing transport adapters but never another governance node. */
    private List<Pipeline.Node> sharedTargets(String node){
        List<Pipeline.Node> result=new ArrayList<>();Set<String> visited=new HashSet<>();
        var pending=new ArrayDeque<>(successOutgoing.getOrDefault(node,List.of()));
        while(!pending.isEmpty()){
            String id=pending.removeFirst();if(!visited.add(id))continue;
            var candidate=nodes.get(id);if(candidate==null)continue;
            if("sink".equals(candidate.category())){if(Set.of("sink.jdbc","sink.hive").contains(candidate.manifestKey()))result.add(candidate);continue;}
            if(Set.of("transform.field-mapping","transform.field-enrichment","transform.sql","transform.java","transform.python","transform.shell").contains(candidate.manifestKey()))continue;
            pending.addAll(successOutgoing.getOrDefault(id,List.of()));
        }
        result.sort(Comparator.comparing((Pipeline.Node target)->!"sink.jdbc".equals(target.manifestKey())).thenComparing(Pipeline.Node::id));
        return result;
    }
    private static void bindSharedTargets(List<Pipeline.Node> targets,ObjectNode spec){
        if(targets.size()>16)throw new MappingException("BUDGET_EXCEEDED","共享治理最多关联16个目标");
        ObjectNode contract=spec.path("schemaContract") instanceof ObjectNode object?object:spec.putObject("schemaContract");
        Map<String,JsonNode> previous=new HashMap<>();for(JsonNode target:contract.path("targets"))previous.put(target.path("nodeId").asText(),target);
        var branches=contract.putArray("targets");
        Set<String> identities=new HashSet<>();
        for(var target:targets){
            Map<String,Object> config=target.config()==null?Map.of():target.config();
            String datasource=first(config,"registeredDatasourceId","selectedDatabaseId","targetDbId","datasourceId","dataSourceId","dbId"),table=first(config,"targetTableId","tableId");
            if(datasource.isBlank()||table.isBlank())throw new MappingException("SCHEMA_SCOPE_REQUIRED","共享治理目标“"+target.label()+"”须选择已登记的数据源和目标表");
            String identity=datasource+":"+table;identities.add(identity);
            var branch=branches.addObject().put("nodeId",target.id()).put("label",target.label());
            var reference=branch.putObject("reference").put("datasourceId",datasource).put("tableId",table);
            JsonNode old=previous.get(target.id());
            if(old!=null&&old.path("reference").equals(reference)&&old.path("fields").isArray())branch.set("fields",old.path("fields").deepCopy());
        }
        JsonNode existing=contract.path("targetRef");
        if(existing.isObject()&&!identities.contains(existing.path("datasourceId").asText()+":"+existing.path("tableId").asText()))
            throw new MappingException("TARGET_REFERENCE_MISMATCH","字段治理的目标引用已不属于实际连接的目标分支，请重新读取目标结构");
        if(!existing.isObject())contract.set("targetRef",branches.get(0).path("reference").deepCopy());
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
        if(parents.isEmpty())return Set.of();
        Set<String> result=null;
        for(String id:parents){
            var source=nodes.get(id);
            // A manual backfill is another direct source, not another wire format. Intersect all inputs.
            if(source==null||!source.category().equals("source"))return Set.of();
            Map<String,Object> config=source.config()==null?Map.of():source.config();
            boolean jdbc=first(config,"jdbcUrl","jdbcURL").startsWith("jdbc:")||JdbcTemporalTypes.oracleDateHasTime(config);
            if(!jdbc)return Set.of();
            Set<String> fields=new HashSet<>();
            for(JsonNode field:parse(source.config().get("sourceColumns"))) {
                String type=field.path("columnType").asText(field.path("dataType").asText(field.path("typeName").asText(field.path("type").asText())));
                if(Set.of("DATE","TIME","DATETIME","INSTANT").contains(ValueTypes.type(type)))fields.add(field.path("columnName").asText(field.path("name").asText()).toLowerCase(Locale.ROOT));
            }
            if(result==null)result=fields;else result.retainAll(fields);
        }
        return result==null?Set.of():result;
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
