package com.linewell.dataelement.magic.runtime.route;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.exception.MagicAPIException;
import org.ssssssss.magicapi.core.model.ApiInfo;
import org.ssssssss.magicapi.core.service.MagicAPIService;
import org.ssssssss.magicapi.core.service.MagicResourceService;
import org.ssssssss.magicapi.core.service.impl.ApiInfoMagicResourceStorage;

/** Dispatches trusted in-process callbacks through the currently registered resource. */
@Component
public class RegisteredMagicApiInvoker {
    private final MagicResourceService resources;
    private final ApiInfoMagicResourceStorage storage;
    private final HeadFallbackMagicRegistry registry;
    private final MagicAPIService scripts;

    public RegisteredMagicApiInvoker(@Lazy MagicResourceService resources, ApiInfoMagicResourceStorage storage,
                                     HeadFallbackMagicRegistry registry, @Lazy MagicAPIService scripts) {
        this.resources = resources;
        this.storage = storage;
        this.registry = registry;
        this.scripts = scripts;
    }

    /** Uses the current servlet context and builds the usual Magic response. */
    public Object call(String resourceId, Map<String, Object> inputs) {
        return invoke(resourceId, inputs, true);
    }

    /** Returns the raw script value for a trusted Java engine callback. */
    public Object execute(String resourceId, Map<String, Object> inputs) {
        return invoke(resourceId, inputs, false);
    }

    private Object invoke(String resourceId, Map<String, Object> inputs, boolean withServletContext) {
        if (!(resources.file(resourceId) instanceof ApiInfo api) || !resourceId.equals(api.getId())) {
            throw new MagicAPIException("找不到 Magic API 资源：" + resourceId);
        }
        // MagicAPIService adds its configured URL prefix during lookup.
        String route = storage.buildMappingKey(api, resources.getGroupPath(api.getGroupId()));
        int separator = route.indexOf(':');
        String method = route.substring(0, separator);
        String path = route.substring(separator + 1);
        // The service adds apiInfo to the input map before it executes the resource.
        var variables = new LinkedHashMap<>(inputs);
        return registry.invokeRegistered(api, () -> withServletContext
                ? scripts.call(method, path, variables)
                : scripts.execute(method, path, variables));
    }
}
