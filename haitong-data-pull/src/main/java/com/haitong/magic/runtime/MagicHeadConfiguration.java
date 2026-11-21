package com.haitong.magic.runtime;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.ssssssss.magicapi.core.config.MagicAPIProperties;
import org.ssssssss.magicapi.core.model.ApiInfo;
import org.ssssssss.magicapi.core.service.impl.ApiInfoMagicResourceStorage;
import org.ssssssss.magicapi.core.service.impl.RequestMagicDynamicRegistry;
import org.ssssssss.magicapi.core.servlet.MagicHttpServletRequest;
import org.ssssssss.magicapi.utils.Mapping;

@Configuration(proxyBeanMethods = false)
public class MagicHeadConfiguration {
    @Bean
    RequestMagicDynamicRegistry magicRequestMagicDynamicRegistry(
            ApiInfoMagicResourceStorage storage,
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
            MagicAPIProperties properties) throws NoSuchMethodException {
        return new HeadFallbackRegistry(storage, Mapping.create(handlerMapping, properties.getWeb()), properties);
    }

    private static final class HeadFallbackRegistry extends RequestMagicDynamicRegistry {
        HeadFallbackRegistry(ApiInfoMagicResourceStorage storage, Mapping mapping,
                MagicAPIProperties properties) throws NoSuchMethodException {
            super(storage, mapping, properties.isAllowOverride(), properties.getPrefix());
        }

        @Override
        public ApiInfo getApiInfoFromRequest(MagicHttpServletRequest request) {
            ApiInfo resolved = super.getApiInfoFromRequest(request);
            if (resolved != null || !"HEAD".equals(request.getMethod())) {
                return resolved;
            }
            Object matchedPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (matchedPattern == null) {
                return null;
            }
            // Spring has already selected the GET mapping. Keep HEAD on the actual request
            // so authentication, audit and the streaming response see the original method.
            ApiInfo get = getMapping("GET:" + matchedPattern);
            return acceptsHead(get) ? get : null;
        }

        private static boolean acceptsHead(ApiInfo api) {
            if (api == null || !"GET".equals(api.getMethod()) || api.getOptions() == null) {
                return false;
            }
            var options = api.getOptions().stream()
                    .filter(option -> option != null && "head".equals(option.getName()))
                    .toList();
            return options.size() == 1 && "true".equals(options.getFirst().getValue());
        }
    }
}
