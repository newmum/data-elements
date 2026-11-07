package com.linewell.dataelement.platform.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class UiComponentCacheServiceTest {

    private static final List<String> SHELL_COMPONENTS = List.of("global-nav", "shopping-car");
    private static final List<String> WORKBENCH_COMPONENTS = List.of("global-nav", "shopping-car", "my-workbench");
    private static final List<String> TASK_COMPONENTS = List.of("global-nav", "shopping-car", "data-convergence",
            "data-convergence-hive", "access-apply", "schedule-setting");
    private static final List<String> REVIEWED_COMPONENTS = Stream.concat(WORKBENCH_COMPONENTS.stream(),
            TASK_COMPONENTS.stream()).distinct().toList();
    private final Map<String, String> redisValues = new HashMap<>();

    private JdbcTemplate jdbcTemplate;
    private UiComponentCacheService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:ui-component-cache-" + System.nanoTime()
                        + ";DB_CLOSE_DELAY=-1"
        );
        dataSource.setUser("sa");
        jdbcTemplate = spy(new JdbcTemplate(dataSource));
        jdbcTemplate.execute("""
                create table ui_component_t (
                    name varchar(128),
                    compile_js clob,
                    compile_css clob,
                    is_del integer,
                    type varchar(8)
                )
                """);

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString()))
                .thenAnswer(invocation -> redisValues.get(invocation.getArgument(0)));
        org.mockito.Mockito.doAnswer(invocation -> {
            redisValues.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        org.mockito.Mockito.doAnswer(invocation -> {
            redisValues.remove(invocation.getArgument(0));
            return true;
        }).when(redisTemplate).delete(anyString());

        service = new UiComponentCacheService(
                jdbcTemplate,
                redisTemplate,
                new ObjectMapper(),
                new SystemConfigProperties()
        );
    }

    @Test
    void cachesActiveCompiledComponentsAndEvictsAfterWrite() {
        jdbcTemplate.update(
                "insert into ui_component_t values ('demo','old-js','old-css',0,'1')"
        );
        jdbcTemplate.update(
                "insert into ui_component_t values ('group',null,null,0,'2')"
        );

        assertEquals(1, service.list().size());
        assertEquals("old-js", service.list().getFirst().get("compileJs"));

        jdbcTemplate.update(
                "update ui_component_t set compile_js='new-js' where name='demo'"
        );
        assertEquals("old-js", service.list().getFirst().get("compileJs"));

        service.evict();
        assertEquals("new-js", service.list().getFirst().get("compileJs"));
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void reviewedEntryPackagesUseOneColdReadAndNoAdditionalWarmReads(int componentCount) throws IOException {
        REVIEWED_COMPONENTS.forEach(name -> saveComponent(name, reviewedJs(name)));
        for (int index = REVIEWED_COMPONENTS.size(); index < componentCount; index++) {
            saveComponent("other-" + index, "compiled-other-component");
        }
        MagicScript script = componentApi();
        assertEquals(Map.of("complete", false, "components", List.of()),
                run(script, "runtimeBatch", "other-1"));
        verify(jdbcTemplate, times(0)).query(anyString(), any(ResultSetExtractor.class));

        Object taskBundle = run(script, "runtimeBatch", "data-convergence");
        assertPackage(taskBundle, TASK_COMPONENTS);
        assertEquals(taskBundle, run(script, "runtimeBatch", "data-convergence-hive"));
        assertPackage(run(script, "runtimeBatch", "my-workbench"), WORKBENCH_COMPONENTS);
        assertPackage(run(script, "runtimeBatch", "app-shell"), SHELL_COMPONENTS);
        assertPackage(run(script, "runtimeBatch", "my-workbench"), WORKBENCH_COMPONENTS);
        assertEquals(componentCount, ((List<?>) run(script, "list", "my-workbench")).size());
        verify(jdbcTemplate, times(1)).query(anyString(), any(ResultSetExtractor.class));
    }

    @Test
    void changedOrAmbiguousWorkbenchReturnsTheFullPackageInTheSameResponse() throws IOException {
        SHELL_COMPONENTS.forEach(name -> saveComponent(name, reviewedJs(name)));
        saveComponent("my-workbench", "changed-compiled-workbench");
        saveComponent("new-dependency", "compiled-new-dependency");
        MagicScript script = componentApi();

        assertEquals(Map.of("complete", true, "scope", "all", "components", service.list()),
                run(script, "runtimeBatch", "my-workbench"));

        saveComponent("my-workbench", reviewedJs("my-workbench"));
        service.evict();
        assertEquals(Map.of("complete", true, "scope", "all", "components", service.list()),
                run(script, "runtimeBatch", "my-workbench"));

        jdbcTemplate.update("update ui_component_t set compile_js='changed-shell' where name='global-nav'");
        service.evict();
        assertEquals(Map.of("complete", true, "scope", "all", "components", service.list()),
                run(script, "runtimeBatch", "app-shell"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"changed", "missing", "collision"})
    void unsafeTaskEntryReturnsTheFullPackageAfterCacheRefresh(String drift) throws IOException {
        TASK_COMPONENTS.forEach(name -> saveComponent(name, reviewedJs(name)));
        saveComponent("new-dependency", "compiled-new-dependency");
        MagicScript script = componentApi();
        assertPackage(run(script, "runtimeBatch", "data-convergence"), TASK_COMPONENTS);

        switch (drift) {
            case "changed" -> jdbcTemplate.update("update ui_component_t set compile_js=? where name=?",
                    "changed-compiled-apply", "access-apply");
            case "missing" -> jdbcTemplate.update("update ui_component_t set is_del=1 where name=?",
                    "schedule-setting");
            case "collision" -> saveComponent("AccessApply", "compiled-alias");
            default -> throw new IllegalArgumentException("Unknown fixture change: " + drift);
        }
        service.evict();

        Object fallback = run(script, "runtimeBatch", "data-convergence");
        verify(jdbcTemplate, times(2)).query(anyString(), any(ResultSetExtractor.class));
        assertEquals(Map.of("complete", true, "scope", "all", "components", service.list()), fallback);
        assertEquals(fallback, run(script, "runtimeBatch", "data-convergence-hive"));
        verify(jdbcTemplate, times(2)).query(anyString(), any(ResultSetExtractor.class));
    }

    private void assertPackage(Object result, List<String> names) {
        Map<?, ?> bundle = (Map<?, ?>) result;
        List<?> components = (List<?>) bundle.get("components");
        assertEquals(true, bundle.get("complete"));
        assertEquals("root", bundle.get("scope"));
        assertEquals(names.size(), components.size());
        assertEquals(Set.copyOf(names.stream()
                .map(name -> Map.of("name", name, "compileJs", reviewedJs(name), "compileCss", ""))
                .toList()), new HashSet<>(components));
    }

    private String reviewedJs(String name) {
        return "compiled-" + name + "-fixture";
    }

    private void saveComponent(String name, String compiledJs) {
        jdbcTemplate.update("insert into ui_component_t values (?,?,'',0,'1')", name, compiledJs);
    }

    private MagicScript componentApi() throws IOException {
        Path path = Path.of("magic/api/09.系统管理/10.页面组件/01.页面组件管理.ms");
        if (!Files.isRegularFile(path)) {
            path = Path.of("..").resolve(path);
        }
        String script = Files.readString(path).split("\\R={32}\\R", 2)[1]
                .replace("import uiComponentCache", "")
                .replace("import uiComponentAdmin", "");
        for (String name : REVIEWED_COMPONENTS) {
            String withFixtureHash = script.replaceFirst("(?<='" + name + "'(?:\\]:? = |: )')[a-f0-9]{64}",
                    DigestUtil.sha256Hex(reviewedJs(name)));
            assertNotEquals(script, withFixtureHash, "Missing source attestation for " + name);
            script = withFixtureHash;
        }
        return MagicScript.create(script, null);
    }

    private Object run(MagicScript script, String action, String root) {
        return script.execute(new MagicScriptContext(Map.of(
                "uiComponentCache", service, "action", action, "body", Map.of("root", root))));
    }
}
