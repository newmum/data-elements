package com.linewell.dataelement.magic.runtime.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.magic.runtime.route.HeadFallbackMagicRegistry;
import com.linewell.dataelement.magic.runtime.security.PolicyAwareApiResourceStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.ssssssss.magicapi.core.config.MagicConfiguration;
import org.ssssssss.magicapi.core.event.EventAction;
import org.ssssssss.magicapi.core.event.FileEvent;
import org.ssssssss.magicapi.core.event.MagicEvent;
import org.ssssssss.magicapi.core.model.ApiInfo;
import org.ssssssss.magicapi.core.model.Option;
import org.ssssssss.magicapi.core.resource.FileResource;
import org.ssssssss.magicapi.core.servlet.MagicHttpServletRequest;
import org.ssssssss.magicapi.core.web.RequestHandler;
import org.ssssssss.magicapi.utils.Mapping;

class StableMagicResourceServiceTest {
    @TempDir
    Path root;

    private final PolicyAwareApiResourceStorage storage = new PolicyAwareApiResourceStorage("");
    private final RequestMappingHandlerMapping mappings = new RequestMappingHandlerMapping();
    private HeadFallbackMagicRegistry registry;
    private StableMagicResourceService resources;
    private Path group;
    private Runnable prepareRead = () -> {};
    private Runnable beforeRead = () -> {};

    @BeforeEach
    void setup() throws Exception {
        group = Files.createDirectories(root.resolve("api/validation"));
        Files.writeString(group.resolve("group.json"), """
                {"id":"validation","parentId":"0","name":"validation","path":"/validation","type":"api"}
                """);
        registry = new HeadFallbackMagicRegistry(storage, Mapping.create(mappings), false, "");
        registry.setHandler(new RequestHandler(new MagicConfiguration(), registry));
        var resource = new FileResource(root.toFile(), false, root.toString()) {
            @Override
            public void readAll() {
                beforeRead.run();
            }
        };
        resources = new StableMagicResourceService(resource, List.of(storage), this::publish, () -> prepareRead.run(), () -> {});
    }

    @Test
    void keepsGetAndHeadMappedDuringReloadAndReplacesTheScript() throws Exception {
        write("settings", "/settings", "return 202");
        resources.refresh();
        write("settings", "/settings", "return 203");

        var entered = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        beforeRead = () -> {
            entered.countDown();
            await(resume);
        };
        try (var worker = Executors.newSingleThreadExecutor()) {
            var refresh = worker.submit(resources::refresh);
            try {
                await(entered);
                assertThat(resolve("GET", "/validation/settings").getScript()).isEqualTo("return 202");
                assertThat(resolve("HEAD", "/validation/settings").getId()).isEqualTo("settings");
            } finally {
                resume.countDown();
            }
            refresh.get(10, TimeUnit.SECONDS);
        }
        assertThat(resolve("HEAD", "/validation/settings").getScript()).isEqualTo("return 203");
    }

    @Test
    void retiresOldMappingsBeforeAReplacementReusesTheirPath() throws Exception {
        write("moved", "/previous", "return 1");
        write("deleted", "/retired", "return 2");
        resources.refresh();

        write("moved", "/next", "return 3");
        Files.delete(group.resolve("deleted.ms"));
        write("replacement", "/previous", "return 4");
        resources.refresh();
        assertThat(resolve("GET", "/validation/retired")).isNull();
        assertThat(resolve("HEAD", "/validation/previous").getId()).isEqualTo("replacement");
        assertThat(resolve("GET", "/validation/next").getId()).isEqualTo("moved");

        Files.delete(group.resolve("replacement.ms"));
        Files.delete(group.resolve("moved.ms"));
        resources.refresh();
        assertThat(resolve("GET", "/validation/previous")).isNull();
        assertThat(resolve("HEAD", "/validation/next")).isNull();
    }

    @Test
    void abortsOnPreflightFailureWithThePreviousRegistryAndEditorTree() throws Exception {
        write("settings", "/settings", "return 202");
        resources.refresh();
        prepareRead = () -> { throw new IllegalStateException("read failed"); };
        assertThatThrownBy(resources::refresh).isInstanceOf(IllegalStateException.class);
        assertThat(resources.<ApiInfo>file("settings").getScript()).isEqualTo("return 202");
        assertThat(resolve("GET", "/validation/settings").getId()).isEqualTo("settings");
    }

    private void publish(Object event) {
        if (event instanceof FileEvent file) {
            registry.onFileEvent(file);
        } else if (event instanceof MagicEvent magic && magic.getAction() == EventAction.CLEAR) {
            registry.clear(magic);
        }
    }

    private void write(String id, String path, String script) throws Exception {
        var api = new ApiInfo();
        api.setId(id);
        api.setName(id);
        api.setGroupId("validation");
        api.setPath(path);
        api.setScript(script);
        var head = new Option();
        head.setName("head");
        head.setValue("true");
        api.getOptions().add(head);
        Files.write(group.resolve(id + ".ms"), storage.write(api));
    }

    private ApiInfo resolve(String method, String path) throws Exception {
        var servlet = new MockHttpServletRequest(method, path);
        ServletRequestPathUtils.parseAndCache(servlet);
        if (mappings.getHandler(servlet) == null) {
            return null;
        }
        var request = mock(MagicHttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
                .thenReturn(servlet.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        return registry.getApiInfoFromRequest(request);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }
}
