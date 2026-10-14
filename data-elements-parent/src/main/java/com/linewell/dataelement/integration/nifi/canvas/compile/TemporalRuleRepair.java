package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.mapping.core.JdbcTemporalTypes;
import com.linewell.dataelement.mapping.core.ValueTypes;
import java.util.*;

/** Repairs inferred physical DATE targets; explicit conversion steps retain their meaning. */
final class TemporalRuleRepair {
    private TemporalRuleRepair() {}
    static JsonNode targets(JsonNode raw,Map<String,Object> targetConfig) {
        if(raw==null||!raw.isObject()||!JdbcTemporalTypes.oracleDateHasTime(targetConfig))return raw;
        Set<String> dateFields=new HashSet<>();
        if(targetConfig.get("targetColumns") instanceof List<?> columns)for(Object value:columns){
            if(!(value instanceof Map<?,?> field))continue;
            String name=first(field,"columnName","name"),type=first(field,"dataType","columnType","typeName","type");
            if(!name.isBlank()&&ValueTypes.type(type).equals("DATE"))dateFields.add(name);
        }
        if(dateFields.isEmpty())return raw;
        ObjectNode spec=raw.deepCopy();
        for(JsonNode field:spec.path("schemaContract").path("target"))if(field instanceof ObjectNode object&&dateFields.contains(field.path("name").asText()))object.put("logicalType","DATETIME").put("dateContainsTime",true).put("physicalType","DATE");
        for(JsonNode rule:spec.path("rules"))if(rule.path("target") instanceof ObjectNode target&&dateFields.contains(target.path("name").asText())&&ValueTypes.type(target.path("logicalType").asText()).equals("DATE"))target.put("logicalType","DATETIME");
        for(JsonNode mapping:spec.path("mappings"))if(mapping instanceof ObjectNode object&&dateFields.contains(mapping.path("to").asText().replaceFirst("^/",""))&&ValueTypes.type(mapping.path("targetType").asText()).equals("DATE"))object.put("targetType","DATETIME");
        return spec;
    }
    private static String first(Map<?,?> field,String...keys){for(String key:keys){Object value=field.get(key);if(value!=null&&!value.toString().isBlank())return value.toString().trim();}return "";}
}
