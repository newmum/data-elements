package com.linewell.dataelement.platform.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.linewell.dataelement.magic.runtime.security.PolicyAwareApiResourceStorage;
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
import org.ssssssss.magicapi.core.service.impl.ApiInfoMagicResourceStorage;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Pattern;

@Configuration
public class MagicBootConfiguration {

    public static final String MAGIC_MAPPING_JSON_ERROR = MagicBootConfiguration.class.getName() + ".mappingJsonError";
    public static final String MAGIC_PORTAL_SWITCH_JSON_ERROR = MagicBootConfiguration.class.getName() + ".portalSwitchJsonError";
    public static final String MAGIC_COLLECTION_STATUS_JSON_ERROR = MagicBootConfiguration.class.getName() + ".collectionStatusJsonError";
    public static final String MAGIC_COLLECTION_ORDER_JSON_ERROR = MagicBootConfiguration.class.getName() + ".collectionOrderJsonError";
    public static final String MAGIC_COLLECTION_WRITE_JSON_ERROR = MagicBootConfiguration.class.getName() + ".collectionWriteJsonError";
    private static final Pattern MAPPING_JSON = Pattern.compile(
            "/nifi/(template/(save|import)|dict/(snapshot|lookup)|map/(schema|sample|check|preview|recommend|compile|convert)"
                    + "|plan/restore|maprun/(upgrade|start))");

    @Bean
    ApiInfoMagicResourceStorage apiInfoMagicResourceStorage(MagicAPIProperties properties) {
        return new PolicyAwareApiResourceStorage(properties.getPrefix());
    }

    /** Preserve legacy typed binding and exact mapping decimals on their respective Magic routes. */
    @Bean
    SmartInitializingSingleton magicMappingJsonConverter(MagicConfiguration magic, ObjectMapper mapper) {
        return () -> {
            var converters = new ArrayList<>(magic.getHttpMessageConverters());
            converters.addFirst(new MappingJackson2HttpMessageConverter(mapper) {
                @Override
                public boolean canRead(Class<?> type, MediaType mediaType) {
                    var request = AuditIdentityResolver.request();
                    return type == Object.class && request != null && "POST".equals(request.getMethod())
                            && legacyMapErrorAttribute(request) != null
                            && super.canRead(type, mediaType);
                }

                @Override
                protected Object readInternal(Class<?> type, HttpInputMessage input) throws IOException {
                    try {
                        // Match the former MVC Map binding for JSON primitives, arrays and invalid JSON.
                        return super.readInternal(LinkedHashMap.class, input);
                    } catch (HttpMessageNotReadableException error) {
                        var request = AuditIdentityResolver.request();
                        request.setAttribute(legacyMapErrorAttribute(request), error);
                        throw error;
                    }
                }
            });
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

    private static String legacyMapErrorAttribute(HttpServletRequest request) {
        return switch (request.getRequestURI().substring(request.getContextPath().length())) {
            case "/portal/sso/switch" -> MAGIC_PORTAL_SWITCH_JSON_ERROR;
            case "/dst/collect/statuses" -> MAGIC_COLLECTION_STATUS_JSON_ERROR;
            case "/dst/collect/order" -> MAGIC_COLLECTION_ORDER_JSON_ERROR;
            case "/dst/collect/start", "/dst/collect/rename" -> MAGIC_COLLECTION_WRITE_JSON_ERROR;
            default -> null;
        };
    }

    /** Keep a bounded view of JSON consumed by Magic so scripts can preserve typed request errors. */
    @Bean
    public OncePerRequestFilter magicJsonRequestCache() {
        Pattern requests = Pattern.compile("/nifi/(life/(deploy|activate)|node/attrs|api/(connect|columns|tables))"
                + "|/(sym/table|idaas/workspace)/order|/sym/scope/(role|save|current)|/dwm/column/batch");
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
