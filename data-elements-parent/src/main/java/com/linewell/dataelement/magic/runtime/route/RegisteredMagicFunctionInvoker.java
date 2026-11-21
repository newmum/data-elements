package com.linewell.dataelement.magic.runtime.route;

import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.exception.MagicAPIException;
import org.ssssssss.magicapi.core.service.MagicResourceService;
import org.ssssssss.magicapi.function.model.FunctionInfo;
import org.ssssssss.magicapi.function.service.FunctionMagicDynamicRegistry;
import org.ssssssss.magicapi.utils.PathUtils;
import org.ssssssss.magicapi.utils.ScriptManager;
import org.ssssssss.script.MagicScriptContext;

/** Runs a trusted Java callback against a registered Magic function identity. */
@Component
public class RegisteredMagicFunctionInvoker {
    private final MagicResourceService resources;
    private final FunctionMagicDynamicRegistry registry;

    public RegisteredMagicFunctionInvoker(@Lazy MagicResourceService resources, FunctionMagicDynamicRegistry registry) {
        this.resources = resources;
        this.registry = registry;
    }

    @SuppressWarnings("unchecked")
    public <T> T invoke(String resourceId, Map<String, Object> inputs) {
        if (!(resources.file(resourceId) instanceof FunctionInfo function) || !resourceId.equals(function.getId())) {
            throw new MagicAPIException("找不到 Magic Function 资源：" + resourceId);
        }
        String key = registry.getMagicResourceStorage().buildMappingKey(function);
        FunctionInfo registered = registry.getMapping(key);
        if (registered == null || !resourceId.equals(registered.getId())) {
            throw new MagicAPIException("Magic Function 资源尚未注册：" + resourceId);
        }

        // Execute the same registered instance whose identity was checked. A later save
        // can reuse the key without changing the script selected for this invocation.
        var context = new MagicScriptContext();
        String groupId = registered.getGroupId();
        context.setScriptName(PathUtils.replaceSlash("/%s/%s(%s)".formatted(
                resources.getGroupName(groupId), registered.getName(), key)));
        context.putMapIntoContext(inputs);
        return (T) ScriptManager.executeScript(registered.getScript(), context);
    }
}
