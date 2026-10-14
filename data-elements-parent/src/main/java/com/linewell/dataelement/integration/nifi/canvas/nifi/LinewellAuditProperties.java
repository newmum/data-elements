package com.linewell.dataelement.integration.nifi.canvas.nifi;

import com.fasterxml.jackson.databind.JsonNode;
import com.linewell.dataelement.mapping.core.JdbcTemporalTypes;
import com.linewell.dataelement.mapping.core.ValueTypes;
import java.util.*;

/** Internal intents are resolved against the installed extension's real property descriptors. */
public final class LinewellAuditProperties {
    public static final String PREFIX="__haitong.linewell.";
    private LinewellAuditProperties() {}
    public static Map<String,String> defaults(Collection<String> columns,Map<String,Object> config) {
        String now=JdbcTemporalTypes.oracleDateHasTime(config)?"SYSDATE":"CURRENT_TIMESTAMP";
        List<String> insert=new ArrayList<>(),update=new ArrayList<>(),exclude=new ArrayList<>();
        for(String field:columns){ValueTypes.field(field);switch(field.toUpperCase(Locale.ROOT)){
            case "ODS_RKSJ"->{insert.add(field+"="+now);exclude.add(field);}
            case "ODS_GXSJ"->{insert.add(field+"="+now);update.add(field+"="+now);}
            case "ODS_UUID"->exclude.add(field);
            default->{}
        }}
        Map<String,String> values=new LinkedHashMap<>();
        if(!insert.isEmpty())values.put(PREFIX+"insert-values",String.join(",",insert));
        if(!update.isEmpty())values.put(PREFIX+"update-values",String.join(",",update));
        if(!exclude.isEmpty()&&!insert.isEmpty())values.put(PREFIX+"exclude-update",String.join(",",exclude));
        return values;
    }
    public static Map<String,String> resolve(Map<String,String> intents,JsonNode descriptors) {
        Map<String,String> result=new LinkedHashMap<>();
        for(var intent:intents.entrySet()){
            String kind=intent.getKey().substring(PREFIX.length());List<String> matches=new ArrayList<>();
            descriptors.fields().forEachRemaining(entry->{String caption=(entry.getKey()+" "+entry.getValue().path("displayName").asText()).toLowerCase(Locale.ROOT).replaceAll("[\\s_-]","");
                boolean match=switch(kind){
                    case "insert-values"->caption.contains("insert")&&(caption.contains("追加")||caption.contains("append")||caption.contains("add"))&&caption.contains("value");
                    case "update-values"->caption.contains("update")&&(caption.contains("追加")||caption.contains("append")||caption.contains("add"))&&caption.contains("value");
                    case "exclude-update"->caption.contains("merge")&&caption.contains("update")&&(caption.contains("排除")||caption.contains("exclude"));
                    default->false;
                };if(match&&!entry.getValue().path("dynamic").asBoolean())matches.add(entry.getKey());});
            if(matches.size()!=1)throw new IllegalStateException("Linewell写入组件缺少或无法唯一识别ODS时间属性："+kind+"；请核对已安装扩展，禁止创建同名动态属性");
            result.put(matches.getFirst(),intent.getValue());
        }
        return result;
    }
}
