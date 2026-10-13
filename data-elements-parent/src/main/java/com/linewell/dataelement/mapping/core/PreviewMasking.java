package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.regex.Pattern;

/** A preview response must never expose source values by default. */
public final class PreviewMasking {
    private static final Pattern SECRET = Pattern.compile("(?i).*(password|passwd|secret|token|private.?key|身份证|证件号码|id.?card|mobile|phone|telephone|手机号|电话|email|邮箱|address|地址|姓名|real.?name).*" );
    private PreviewMasking() {}
    public static boolean isSensitiveName(String name){return SECRET.matcher(name).matches();}
    public static Set<String> sensitiveNames(JsonNode schema) {
        Set<String> names = new HashSet<>();
        if (schema != null) for (JsonNode field : schema) if (field.path("sensitive").asBoolean() || field.path("sensitivityLevel").asInt()>0 || SECRET.matcher(field.path("name").asText()).matches()) names.add(field.path("name").asText());
        return names;
    }
    public static List<Map<String,Object>> mask(List<Map<String,Object>> rows, Set<String> sensitive, boolean allFields) {
        List<Map<String,Object>> result = new ArrayList<>();
        for (var row : rows) {
            Map<String,Object> safe = new LinkedHashMap<>();
            row.forEach((name,value)->safe.put(name,value==null?null:allFields || sensitive.contains(name) || SECRET.matcher(name).matches() ? "***" : value));
            result.add(safe);
        }
        return result;
    }
}
