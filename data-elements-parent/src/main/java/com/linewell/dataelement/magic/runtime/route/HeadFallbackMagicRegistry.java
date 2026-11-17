package com.linewell.dataelement.magic.runtime.route;

import java.util.function.Supplier;
import org.springframework.web.servlet.HandlerMapping;
import org.ssssssss.magicapi.core.exception.MagicAPIException;
import org.ssssssss.magicapi.core.model.ApiInfo;
import org.ssssssss.magicapi.core.service.MagicResourceStorage;
import org.ssssssss.magicapi.core.service.impl.RequestMagicDynamicRegistry;
import org.ssssssss.magicapi.core.servlet.MagicHttpServletRequest;
import org.ssssssss.magicapi.utils.Mapping;

/** Resolves implicit HEAD and guards in-process dispatch against concurrent route replacement. */
public final class HeadFallbackMagicRegistry extends RequestMagicDynamicRegistry {
    private static final String HEAD_OPTION = "head";
    private final ThreadLocal<String> invocationResourceId = new ThreadLocal<>();

    public HeadFallbackMagicRegistry(MagicResourceStorage<ApiInfo> storage, Mapping mapping,
                                     boolean allowOverride, String prefix) throws NoSuchMethodException {
        super(storage, mapping, allowOverride, prefix);
    }

    <T> T invokeRegistered(ApiInfo resource, Supplier<T> action) {
        String key = getMagicResourceStorage().buildMappingKey(resource);
        ApiInfo registered = super.getMapping(key);
        if (registered == null || !resource.getId().equals(registered.getId())) {
            throw new MagicAPIException("Magic API 资源尚未注册：" + resource.getId());
        }
        String previous = invocationResourceId.get();
        invocationResourceId.set(resource.getId());
        try {
            return action.get();
        } finally {
            if (previous == null) {
                invocationResourceId.remove();
            } else {
                invocationResourceId.set(previous);
            }
        }
    }

    @Override
    public ApiInfo getMapping(String key) {
        ApiInfo resource = super.getMapping(key);
        String expected = invocationResourceId.get();
        if (expected == null) {
            return resource;
        }
        // The service looks up the route again before running its script. A concurrent
        // save may reuse the path for another resource. Consume the guard before script execution.
        invocationResourceId.remove();
        return resource != null && expected.equals(resource.getId()) ? resource : null;
    }

    @Override
    public ApiInfo getApiInfoFromRequest(MagicHttpServletRequest request) {
        ApiInfo resolved = super.getApiInfoFromRequest(request);
        if (resolved != null || !"HEAD".equals(request.getMethod())) {
            return resolved;
        }
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern == null) {
            return null;
        }
        ApiInfo get = getMapping("GET:" + pattern);
        return acceptsHead(get) ? get : null;
    }

    public static boolean acceptsHead(ApiInfo api) {
        if (api == null || api.getOptions() == null) {
            return false;
        }
        var options = api.getOptions().stream()
                .filter(option -> option != null && HEAD_OPTION.equals(option.getName()))
                .toList();
        if (options.isEmpty()) {
            return false;
        }
        Object enabled = options.getFirst().getValue();
        if (!"GET".equals(api.getMethod()) || options.size() != 1
                || !("true".equals(enabled) || "false".equals(enabled))) {
            throw new IllegalArgumentException("同址 HEAD 选项只允许在 GET 资源中声明一次 true 或 false");
        }
        return "true".equals(enabled);
    }
}
