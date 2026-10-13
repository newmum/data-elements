package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.*;
import com.linewell.dataelement.integration.nifi.canvas.controller.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.lang.reflect.Type;
import java.util.List;

/** Exact decimals apply only to mapping requests; existing application JSON contracts stay unchanged. */
@Configuration
public class MappingJsonConfiguration implements WebMvcConfigurer {
    private final ObjectMapper mapper;
    public MappingJsonConfiguration(ObjectMapper mapper){this.mapper=mapper;}
    @Override public void extendMessageConverters(List<HttpMessageConverter<?>> converters){
        converters.addFirst(new MappingJackson2HttpMessageConverter(mapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)){
            @Override public boolean canRead(Type type,Class<?> context,MediaType mediaType){return (context==FieldMappingController.class||context==MappingAssetController.class)&&super.canRead(type,context,mediaType);}
            @Override public boolean canRead(Class<?> type,MediaType mediaType){return false;}
            @Override public boolean canWrite(Class<?> type,MediaType mediaType){return false;}
            @Override public boolean canWrite(Type type,Class<?> clazz,MediaType mediaType){return false;}
        });
    }
}
