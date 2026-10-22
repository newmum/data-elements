package com.haitong.core;
import java.util.Map;
/** Optional dotted paths in the ORIGINAL object. Not JSONPath code, expressions or scripts. */
public final class BusinessFields {
    private BusinessFields(){}
    public static String value(Map<String,Object> input,String path,int max){
        if(path==null||path.isBlank())return null;
        if(path.length()>300||path.contains(".."))throw RelayException.invalid("对账字段路径不合法");
        Object current=input;for(String part:path.split("\\.")){if(!(current instanceof Map<?,?> map))return null;current=map.get(part);}
        if(current==null)return null;
        if(current instanceof Map<?,?>||current instanceof java.util.Collection<?>)throw RelayException.invalid("对账字段必须是标量");
        String value=current.toString();if(value.length()>max)throw RelayException.invalid("对账字段长度超限");return value;
    }
}
