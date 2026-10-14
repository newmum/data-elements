package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.linewell.dataelement.mapping.core.JdbcTemporalTypes;
import com.linewell.dataelement.mapping.core.ValueTypes;
import java.util.*;

/** Preserve Oracle DATE seconds before JDBC exposes the result as SQL DATE. */
final class OracleTemporalProjection {
    private OracleTemporalProjection() {}
    static String columns(Map<String,Object> config) {
        if(!JdbcTemporalTypes.oracleDateHasTime(config)||!(config.get("sourceColumns") instanceof List<?> columns))return "";
        List<String> projections=new ArrayList<>();boolean hasDate=false;
        for(Object value:columns){
            if(!(value instanceof Map<?,?> field))continue;
            String name=first(field,"columnName","name");if(name.isBlank())continue;
            ValueTypes.field(name);
            String quoted="\""+name+"\"",type=first(field,"dataType","columnType","typeName","type");
            if(ValueTypes.type(type).equals("DATE")){projections.add("CAST("+quoted+" AS TIMESTAMP) AS "+quoted);hasDate=true;}
            else projections.add(quoted);
        }
        return hasDate?String.join(",",projections):"";
    }
    private static String first(Map<?,?> field,String... keys){for(String key:keys){Object value=field.get(key);if(value!=null&&!value.toString().isBlank())return value.toString().trim();}return "";}
}
