package com.haitong.core;

import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.haitong.config.RelayProperties;
import java.util.*;

@Component
public final class Json {
    private final JsonMapper mapper;
    private final int maxStringLength;
    public Json() { this(new RelayProperties()); }
    @org.springframework.beans.factory.annotation.Autowired
    public Json(RelayProperties properties) {
        // UTF-8 characters cannot outgrow the bounded request bytes. Avoid a second,
        // hidden 1 MiB field limit rejecting an otherwise permitted Base64 record.
        maxStringLength=properties.getLimits().getMaxMessageBytes();
        mapper=JsonMapper.builder(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).maxStringLength(maxStringLength).maxNumberLength(200).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    }
    public String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("JSON 序列化失败", e); }
    }
    public byte[] bytes(Object value) {
        try { return mapper.writeValueAsBytes(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("JSON 序列化失败", e); }
    }
    public <T> T read(String value,Class<T> type) {
        try { return mapper.readValue(value,type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("JSON 读取失败", e); }
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> object(byte[] value) {
        PayloadFiles.utf8(value);
        try {
            Object parsed=mapper.readValue(value,Object.class);
            if(!(parsed instanceof Map<?,?> m)) throw RelayException.invalid("请求体必须是一个 JSON 对象");
            return new LinkedHashMap<>((Map<String,Object>)m);
        } catch(RelayException e) { throw e; }
        catch(StreamConstraintsException e) { throw new RelayException(422,"JSON_LIMIT_EXCEEDED",
            "JSON超过解析限制：单字段最多"+maxStringLength+"字符、嵌套最多64层、数字最多200字符"); }
        catch(Exception e) { throw RelayException.invalid("JSON语法不合法，请检查括号、引号、逗号或重复字段"); }
    }
    public void require(Map<String,Object> object,List<String> fields) {
        for(String path:fields) {
            Object value=object;
            for(String part:path.split("\\.")) value=value instanceof Map<?,?> m?m.get(part):null;
            if(value==null || value instanceof String s && s.isBlank()) throw RelayException.invalid("缺少必填字段："+path);
        }
    }
    public Map<String,Object> normalize(Map<String,Object> input,Map<String,String> rename) {
        Map<String,Object> result=new LinkedHashMap<>(input);
        for(var item:rename.entrySet()) {
            if(!result.containsKey(item.getKey()) || item.getKey().equals(item.getValue())) continue;
            if(result.containsKey(item.getValue())) throw RelayException.invalid("字段映射覆盖已有字段");
            result.put(item.getValue(),result.remove(item.getKey()));
        }
        return result;
    }
}
