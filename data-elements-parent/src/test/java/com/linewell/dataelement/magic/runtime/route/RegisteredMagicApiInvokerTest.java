package com.linewell.dataelement.magic.runtime.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.magic.runtime.security.PolicyAwareApiResourceStorage;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.ssssssss.magicapi.core.config.MagicConfiguration;
import org.ssssssss.magicapi.core.config.Constants;
import org.ssssssss.magicapi.core.exception.MagicAPIException;
import org.ssssssss.magicapi.core.interceptor.ResultProvider;
import org.ssssssss.magicapi.core.model.ApiInfo;
import org.ssssssss.magicapi.core.service.MagicResourceService;
import org.ssssssss.magicapi.core.service.impl.DefaultMagicAPIService;
import org.ssssssss.magicapi.core.servlet.MagicHttpServletRequest;
import org.ssssssss.magicapi.core.servlet.MagicRequestContextHolder;
import org.ssssssss.magicapi.core.web.RequestHandler;
import org.ssssssss.magicapi.utils.Mapping;

class RegisteredMagicApiInvokerTest {
    private final MagicResourceService resources = mock(MagicResourceService.class);
    private HeadFallbackMagicRegistry registry;
    private DefaultMagicAPIService scripts;
    private RegisteredMagicApiInvoker invoker;

    @BeforeEach
    void setUp() throws Exception {
        String prefix = "/validation";
        var storage = new PolicyAwareApiResourceStorage(prefix);
        storage.setMagicResourceService(resources);
        when(resources.getGroupName("group")).thenReturn("Validation");
        when(resources.getGroupPath("group")).thenReturn("/callbacks");
        registry = new HeadFallbackMagicRegistry(storage, Mapping.create(new RequestMappingHandlerMapping()), false, prefix);
        registry.setHandler(new RequestHandler(new MagicConfiguration(), registry));
        var request = mock(MagicHttpServletRequest.class);
        var holder = mock(MagicRequestContextHolder.class);
        when(holder.getRequest()).thenReturn(request);
        ResultProvider result = (entity, code, message, data) ->
                Map.of("code", code, "data", data, "usesCurrentRequest", entity.getRequest() == request);
        scripts = new DefaultMagicAPIService(result, "validation", resources, registry, null, true, prefix, holder, null);
        invoker = new RegisteredMagicApiInvoker(resources, storage, registry, scripts);
    }

    @Test
    void executesTheCurrentRegisteredMethodAndPathAndDoesNotMutateTheInputs() {
        var original = register("callback", "POST", "/old", "return payload");
        when(resources.file("callback")).thenReturn(original);
        var inputs = Map.<String, Object>of("payload", "accepted");
        assertThat(invoker.execute("callback", inputs)).isEqualTo("accepted");

        registry.unregister(original);
        var edited = register("callback", "PUT", "/saved", "return apiInfo.getId() + ':' + payload");
        when(resources.file("callback")).thenReturn(edited);
        assertThat(invoker.execute("callback", inputs)).isEqualTo("callback:accepted");
        assertThat(inputs).containsOnlyKeys("payload");
    }

    @Test
    void callPreservesTheServletContextAndResponseEnvelope() {
        var api = register("gateway", "POST", "/read", "return body");
        when(resources.file("gateway")).thenReturn(api);
        assertThat(invoker.call("gateway", Map.of("body", Map.of("success", true))))
                .isEqualTo(Map.of("code", Constants.RESPONSE_CODE_SUCCESS,
                        "usesCurrentRequest", true, "data", Map.of("success", true)));
    }

    @Test
    void refusesAResourceThatIsPresentInStorageButInactiveInTheRegistry() {
        var inactive = resource("callback", "POST", "/same", "return 'stale'");
        when(resources.file("callback")).thenReturn(inactive);
        register("replacement", "POST", "/same", "return 'different'");

        assertThatThrownBy(() -> invoker.execute("callback", Map.of()))
                .isInstanceOf(MagicAPIException.class).hasMessageContaining("callback");
        assertThat(scripts.<Object>execute("POST", "/callbacks/same", new HashMap<>())).isEqualTo("different");
    }

    @Test
    void refusesAReplacementBetweenResolutionAndScriptLookup() {
        var current = register("callback", "POST", "/same", "return 'original'");
        assertThatThrownBy(() -> registry.invokeRegistered(current, () -> {
            registry.unregister(current);
            register("replacement", "POST", "/same", "return 'different'");
            return scripts.execute("POST", "/callbacks/same", new HashMap<>());
        })).isInstanceOf(MagicAPIException.class);
        assertThat(scripts.<Object>execute("POST", "/callbacks/same", new HashMap<>()))
                .isEqualTo("different");
    }

    private ApiInfo register(String id, String method, String path, String script) {
        var api = resource(id, method, path, script);
        assertThat(registry.register(api)).isTrue();
        return api;
    }

    private static ApiInfo resource(String id, String method, String path, String script) {
        var api = new ApiInfo();
        api.setId(id);
        api.setGroupId("group");
        api.setName(id);
        api.setMethod(method);
        api.setPath(path);
        api.setScript(script);
        return api;
    }
}
