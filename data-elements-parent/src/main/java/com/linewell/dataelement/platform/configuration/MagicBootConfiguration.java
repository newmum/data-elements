package com.linewell.dataelement.platform.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.linewell.dataelement.platform.audit.AuditIdentityResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.ssssssss.magicapi.core.config.MagicAPIProperties;
import org.ssssssss.magicapi.core.config.MagicConfiguration;
import org.ssssssss.magicapi.core.model.ApiInfo;
import org.ssssssss.magicapi.core.service.impl.ApiInfoMagicResourceStorage;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

@Configuration
public class MagicBootConfiguration {

    public static final String MAGIC_MAPPING_JSON_ERROR = MagicBootConfiguration.class.getName() + ".mappingJsonError";
    private static final Pattern MAPPING_JSON = Pattern.compile(
            "/nifi/api/field-mapping/(templates(?:/[^/]+/import)?|dictionary-snapshots|schema-contract|sample"
                    + "|plans/[^/]+/restore-draft|upgrade-runtime|start-published|validate|preview|recommend|compile|migrate-legacy|lookup-template)");

    @Bean
    ApiInfoMagicResourceStorage apiInfoMagicResourceStorage(MagicAPIProperties properties) {
        return new ApiInfoMagicResourceStorage(properties.getPrefix()) {
            @Override
            public String buildMappingKey(ApiInfo api, String groupPath) {
                String key = super.buildMappingKey(api, groupPath);
                // 仅在资源显式声明叶组根路由时，保留迁移前没有末尾斜杠的地址。
                if ("/".equals(api.getPath()) && api.getProperties() != null
                        && Boolean.TRUE.equals(api.getAttribute("leafRoute")) && !key.endsWith(":/")) {
                    return key.substring(0, key.length() - 1);
                }
                return key;
            }
        };
    }

    /** Preserve exact decimal JSON for mapping scripts without changing the other Magic or MVC converters. */
    @Bean
    SmartInitializingSingleton magicMappingJsonConverter(MagicConfiguration magic, ObjectMapper mapper) {
        return () -> {
            var converters = new ArrayList<>(magic.getHttpMessageConverters());
            converters.addFirst(new MappingJackson2HttpMessageConverter(
                    mapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)) {
                @Override
                public boolean canRead(Class<?> type, MediaType mediaType) {
                    var request = AuditIdentityResolver.request();
                    if (type != Object.class || request == null || !"POST".equals(request.getMethod())) {
                        return false;
                    }
                    String path = request.getRequestURI().substring(request.getContextPath().length());
                    return MAPPING_JSON.matcher(path).matches() && super.canRead(type, mediaType);
                }

                @Override
                protected Object readInternal(Class<?> type, HttpInputMessage input) throws IOException {
                    try {
                        return super.readInternal(JsonNode.class, input);
                    } catch (HttpMessageNotReadableException error) {
                        AuditIdentityResolver.request().setAttribute(MAGIC_MAPPING_JSON_ERROR, error);
                        throw error;
                    }
                }
            });
            magic.setHttpMessageConverters(converters);
        };
    }

    /** Keep a bounded view of JSON consumed by Magic so scripts can preserve typed request errors. */
    @Bean
    public OncePerRequestFilter magicNifiJsonRequestCache() {
        Pattern requests = Pattern.compile("/nifi/api/pipelines/[^/]+/(deploy|activate-candidate|attribute-sync)");
        return new OncePerRequestFilter() {
            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                String path = request.getRequestURI().substring(request.getContextPath().length());
                if (!"POST".equals(request.getMethod()) || !requests.matcher(path).matches()) {
                    return true;
                }
                String contentType = request.getContentType();
                return contentType == null || !contentType.regionMatches(true, 0, MediaType.APPLICATION_JSON_VALUE, 0,
                        MediaType.APPLICATION_JSON_VALUE.length()) && !contentType.toLowerCase(Locale.ROOT).contains("+json");
            }

            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                chain.doFilter(new ContentCachingRequestWrapper(request, 65536), response);
            }
        };
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        LocalDateTimeSerializer localDateTimeSerializer = new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return builder -> builder.serializerByType(LocalDateTime.class, localDateTimeSerializer);
    }
}
