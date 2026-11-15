package com.linewell.dataelement.dataservice.flowserve;

import static com.linewell.dataelement.dataservice.flowserve.FlowServeRuntimeAdapter.PUBLISHED_GROUP_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.ssssssss.magicapi.core.model.ApiInfo;
import org.ssssssss.magicapi.core.model.Group;
import org.ssssssss.magicapi.core.service.MagicAPIService;
import org.ssssssss.magicapi.core.service.MagicResourceService;
import org.ssssssss.magicapi.core.service.impl.RequestMagicDynamicRegistry;

class FlowServeRuntimeAdapterTest {
    private static final String FLOW_PATH = "/dws/flowserve";
    private static final String PUBLIC_PREFIX = FLOW_PATH + "/published";
    private final Map<String, ApiInfo> files = new HashMap<>();
    private final MagicResourceService resources = mock(MagicResourceService.class);
    private final MagicAPIService service = mock(MagicAPIService.class);
    private final RequestMagicDynamicRegistry registry = mock(RequestMagicDynamicRegistry.class);
    private final FlowServeRuntimeAdapter adapter = new FlowServeRuntimeAdapter(
            new ObjectMapper(), resources, service, registry, new TenantProperties(), "http://localhost");

    @BeforeEach
    void storeResourcesById() {
        when(resources.getGroup(PUBLISHED_GROUP_ID)).thenReturn(new Group());
        when(resources.getGroupPath(PUBLISHED_GROUP_ID)).thenReturn("dws//flowserve");
        when(resources.file(anyString())).thenAnswer(call -> files.get(call.getArgument(0)));
        when(resources.<ApiInfo>listFiles(PUBLISHED_GROUP_ID)).thenAnswer(call -> files.values().stream().toList());
        when(resources.saveFile(any(ApiInfo.class))).thenAnswer(call -> {
            ApiInfo saved = call.getArgument(0);
            files.put(saved.getId(), saved);
            return true;
        });
        when(resources.delete(anyString())).thenAnswer(call -> files.remove(call.getArgument(0)) != null);
    }

    @Test
    void publishesAndRemovesApisInTheLeafWithoutChangingTheirPublicRoutes() {
        ApiInfo staticApi = new ApiInfo();
        staticApi.setId("static");
        staticApi.setName("19.查询");
        staticApi.setPath("/flows");
        staticApi.setGroupId(PUBLISHED_GROUP_ID);
        files.put(staticApi.getId(), staticApi);

        adapter.deployFromRequest(deployment("first", "第一个", "/__tenant/validation/first", "return 1"));
        adapter.deployFromRequest(deployment("second", "第二个", "/__tenant/validation/second", "return 2"));
        adapter.deployFromRequest(deployment("first", "重发", "/__tenant/validation/first", "return 3"));

        assertThat(files.get("first").getName()).isEqualTo("20.重发");
        assertThat(files.get("first").getPath()).isEqualTo("/published/__tenant/validation/first");
        assertThat(files.get("first").getScript()).isEqualTo("return 3");
        assertThat(files.get("second").getName()).isEqualTo("21.第二个");
        adapter.undeployFromRequest("first");
        adapter.undeployFromRequest("first");

        assertThat(files).containsOnlyKeys("static", "second");
        assertThat(files.get("second").getName()).isEqualTo("20.第二个");
        assertThat(files.get("second").getPath()).isEqualTo("/published/__tenant/validation/second");
        assertThat(files.get("second").getScript()).isEqualTo("return 2");
        assertThatThrownBy(() -> adapter.undeployFromRequest("static")).isInstanceOf(IllegalArgumentException.class);
        assertThat(files.get("static")).isSameAs(staticApi);
        verify(resources, never()).saveGroup(any());
    }

    @Test
    void keepsTheLegacyStoragePathForAnExistingOldRuntime() {
        when(resources.getGroupPath(PUBLISHED_GROUP_ID)).thenReturn(PUBLIC_PREFIX);
        adapter.deployFromRequest(deployment("first", "旧分组服务", "/validation/first", "return 1"));

        assertThat(files.get("first").getPath()).isEqualTo("/validation/first");
        assertThat(files.get("first").getName()).isEqualTo("旧分组服务");
        verify(resources, never()).saveGroup(any());
    }

    @Test
    void debugUsesThePublicPathAndRemovesItsTransientRegistration() {
        when(service.execute(eq("GET"), startsWith(PUBLIC_PREFIX + "/__debug/validation/"), anyMap()))
                .thenReturn(42);

        assertThat(adapter.debug(Map.of("flowId", "validation"), "return 42"))
                .containsEntry("ok", true).containsEntry("response", 42);

        ArgumentCaptor<ApiInfo> route = ArgumentCaptor.forClass(ApiInfo.class);
        verify(registry).register(route.capture());
        assertThat(route.getValue().getPath()).startsWith("/published/__debug/validation/");
        verify(registry).unregister(route.getValue());
        verify(resources, never()).saveFile(any(ApiInfo.class));
    }

    private Map<String, Object> deployment(String id, String name, String path, String script) {
        return Map.of("resourceId", id, "name", name, "path", path, "script", script);
    }
}
