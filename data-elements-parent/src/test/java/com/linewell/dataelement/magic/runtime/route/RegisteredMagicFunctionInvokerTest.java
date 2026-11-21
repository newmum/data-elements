package com.linewell.dataelement.magic.runtime.route;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.ssssssss.magicapi.core.exception.MagicAPIException;
import org.ssssssss.magicapi.core.service.MagicResourceService;
import org.ssssssss.magicapi.function.model.FunctionInfo;
import org.ssssssss.magicapi.function.service.FunctionInfoMagicResourceStorage;
import org.ssssssss.magicapi.function.service.FunctionMagicDynamicRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegisteredMagicFunctionInvokerTest {
    private final MagicResourceService resources = mock(MagicResourceService.class);
    private final FunctionInfoMagicResourceStorage storage = mock(FunctionInfoMagicResourceStorage.class);
    private final FunctionMagicDynamicRegistry registry = mock(FunctionMagicDynamicRegistry.class);
    private final RegisteredMagicFunctionInvoker invoker = new RegisteredMagicFunctionInvoker(resources, registry);

    @Test
    void followsAResourceMovedToANewFunctionPath() {
        var original = function("stable-id", "return value + '-original'");
        var moved = function("stable-id", "return value + '-moved'");
        when(resources.file("stable-id")).thenReturn(original, moved);
        when(registry.getMagicResourceStorage()).thenReturn(storage);
        when(storage.buildMappingKey(original)).thenReturn("/group/original");
        when(storage.buildMappingKey(moved)).thenReturn("/group/moved");
        when(registry.getMapping("/group/original")).thenReturn(original);
        when(registry.getMapping("/group/moved")).thenReturn(moved);

        var input = Map.<String, Object>of("value", "validated");
        assertEquals("validated-original", invoker.invoke("stable-id", input));
        assertEquals("validated-moved", invoker.invoke("stable-id", input));
    }

    @Test
    void rejectsAReusedPathAndUnregisteredResource() {
        var requested = function("expected-id", "return 'expected'");
        when(resources.file("expected-id")).thenReturn(requested);
        when(registry.getMagicResourceStorage()).thenReturn(storage);
        when(storage.buildMappingKey(requested)).thenReturn("/group/entry");
        when(registry.getMapping("/group/entry"))
                .thenReturn(function("other-id", "return 'other'"))
                .thenReturn(null);

        assertThrows(MagicAPIException.class, () -> invoker.invoke("expected-id", Map.of()));
        assertThrows(MagicAPIException.class, () -> invoker.invoke("expected-id", Map.of()));
    }

    private static FunctionInfo function(String id, String script) {
        var function = new FunctionInfo();
        function.setId(id);
        function.setName(id);
        function.setGroupId("test-group");
        function.setScript(script);
        return function;
    }
}
