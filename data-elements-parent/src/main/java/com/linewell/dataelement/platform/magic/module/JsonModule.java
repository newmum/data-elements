package com.linewell.dataelement.platform.magic.module;


import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/**
 * @Description: 字符串模块
 * @Author: gaoZhenWen
 * @Date: 2023/2/3 14:27
 * @Copyright: Fujian Linewell Software Co., Ltd. All rights reserved.
 */
@Component
@MagicModule("jsons")
@Slf4j
public class JsonModule {

    public static final ObjectMapper objectMapper = new ObjectMapper();
    {
        // 方法：序列化null时写入 "key": null
        objectMapper.getSerializerProvider().setNullValueSerializer(new JsonSerializer<Object>() {
            @Override
            public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeNull();
            }
        });
        JavaTimeModule module = new JavaTimeModule();
        // 1. 定义不含 'T' 的格式
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        // 2. 将此格式应用于 LocalDateTime 的序列化
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));

        objectMapper.registerModule(module);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
    }


    @Comment("字符串转JSON")
    public JSONObject toJSONObject(@Comment("json字符串")String str) {
        return JSONUtil.parseObj(str);
    }
    @Comment("字符串转JSON")
    public JSONArray toJSONArray(@Comment("json字符串")String str) {
        return JSONUtil.parseArray(str);
    }

    @Comment("json对象或数组串转JSON(包含null值)")
    public String toJSONStr(@Comment("json对象或数组")Object obj) throws JsonProcessingException {
        //return JSONUtil.toJsonStr(JSONUtil.parseObj(obj,false));
        return objectMapper.writeValueAsString(obj);
    }
}
