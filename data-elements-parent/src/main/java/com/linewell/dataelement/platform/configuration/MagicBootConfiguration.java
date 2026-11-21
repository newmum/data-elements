package com.linewell.dataelement.platform.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.linewell.dataelement.magic.runtime.route.HeadFallbackMagicRegistry;
import com.linewell.dataelement.magic.runtime.route.MagicJsonBody;
import com.linewell.dataelement.magic.runtime.security.PolicyAwareApiResourceStorage;
import com.linewell.dataelement.platform.audit.AuditIdentityResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.ssssssss.magicapi.core.config.MagicAPIProperties;
import org.ssssssss.magicapi.core.config.MagicConfiguration;
import org.ssssssss.magicapi.core.service.impl.ApiInfoMagicResourceStorage;
import org.ssssssss.magicapi.utils.Mapping;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;

@Configuration
public class MagicBootConfiguration {

    public static final String MAGIC_MAPPING_JSON_ERROR = MagicBootConfiguration.class.getName() + ".mappingJsonError";
    private static final String OBJECT_MAP_JSON_ERROR = MagicBootConfiguration.class.getName() + ".objectMapJsonError";
    // These names are read by deployed scripts. Each request has one selected resource and one binding error.
    public static final String MAGIC_PORTAL_SWITCH_JSON_ERROR = OBJECT_MAP_JSON_ERROR;
    public static final String MAGIC_COLLECTION_STATUS_JSON_ERROR = OBJECT_MAP_JSON_ERROR;
    public static final String MAGIC_COLLECTION_ORDER_JSON_ERROR = OBJECT_MAP_JSON_ERROR;
    public static final String MAGIC_COLLECTION_WRITE_JSON_ERROR = OBJECT_MAP_JSON_ERROR;

    @Bean
    ApiInfoMagicResourceStorage apiInfoMagicResourceStorage(MagicAPIProperties properties) {
        return new PolicyAwareApiResourceStorage(properties.getPrefix());
    }

    @Bean
    HeadFallbackMagicRegistry magicRequestMagicDynamicRegistry(ApiInfoMagicResourceStorage storage,
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
            MagicAPIProperties properties) throws NoSuchMethodException {
        return new HeadFallbackMagicRegistry(storage, Mapping.create(handlerMapping, properties.getWeb()),
                properties.isAllowOverride(), properties.getPrefix());
    }

    /** Preserve typed binding and exact decimals when the selected resource requests them. */
    @Bean
    SmartInitializingSingleton magicMappingJsonConverter(MagicConfiguration magic, ObjectMapper mapper) {
        return () -> {
            var converters = new ArrayList<>(magic.getHttpMessageConverters());
            converters.addFirst(typedJson(mapper, MagicJsonBody.OBJECT_MAP, LinkedHashMap.class, OBJECT_MAP_JSON_ERROR));
            converters.addFirst(typedJson(mapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS),
                    MagicJsonBody.EXACT_TREE, JsonNode.class, MAGIC_MAPPING_JSON_ERROR));
            magic.setHttpMessageConverters(converters);
        };
    }

    private static MappingJackson2HttpMessageConverter typedJson(ObjectMapper mapper, MagicJsonBody mode,
                                                                  Class<?> target, String errorAttribute) {
        return new MappingJackson2HttpMessageConverter(mapper) {
            @Override
            public boolean canRead(Class<?> type, MediaType mediaType) {
                return type == Object.class && MagicJsonBody.matched(AuditIdentityResolver.request()) == mode
                        && super.canRead(type, mediaType);
            }

            @Override
            protected Object readInternal(Class<?> type, HttpInputMessage input) throws IOException {
                try {
                    return super.readInternal(target, input);
                } catch (HttpMessageNotReadableException error) {
                    AuditIdentityResolver.request().setAttribute(errorAttribute, error);
                    throw error;
                }
            }
        };
    }

    /** Install a lazy wrapper before dispatch. Only a matched CACHED resource retains bytes. */
    @Bean
    public OncePerRequestFilter magicJsonRequestCache() {
        return new OncePerRequestFilter() {
            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                if (!"POST".equals(request.getMethod())) {
                    return true;
                }
                String contentType = request.getContentType();
                return contentType == null || !contentType.regionMatches(true, 0, MediaType.APPLICATION_JSON_VALUE, 0,
                        MediaType.APPLICATION_JSON_VALUE.length()) && !contentType.toLowerCase(Locale.ROOT).contains("+json");
            }

            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                chain.doFilter(new ContentCachingRequestWrapper(request, 65536) {
                    @Override
                    public ServletInputStream getInputStream() throws IOException {
                        return MagicJsonBody.matched(request) == MagicJsonBody.CACHED
                                ? super.getInputStream() : request.getInputStream();
                    }
                }, response);
            }
        };
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        LocalDateTimeSerializer localDateTimeSerializer = new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return builder -> builder.serializerByType(LocalDateTime.class, localDateTimeSerializer);
    }
}
