package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.ssssssss.script.MagicScript;

/** Compile each selected business, identity, audit, and sorting script once. */
class BusinessMagicSyntaxTest {
    private static final String TENANT_ACCOUNTS = "function/09.租户账号";
    private static final String IDAAS_LOGGING = "api/13.统一身份管理/34.日志管理";
    private static final List<String> IDENTITY_GROUPS = List.of("api/09.系统管理", TENANT_ACCOUNTS,
            "api/13.统一身份管理", "function/13.统一身份管理");
    private static final List<String> BUSINESS_GROUPS = List.of(
            "api/02.数据盘点/02.数据库表/01.元数据管理", "api/09.系统管理/02.审批中心",
            "api/09.系统管理/03.安全管理", "api/03.数据接入");
    private static final List<String> MULTI_VALUE_IDS = List.of(
            "e3320ec899a24426adf5f77e2dab36f9", "ods_data_agg_task_ensure_01");

    @TestFactory
    List<DynamicTest> compileCurrentResources() throws IOException {
        var sources = CanonicalMagicSources.under("api", "function");
        for (String directory : IDENTITY_GROUPS) {
            assertTrue(sources.stream().anyMatch(script -> inGroup(script, directory)),
                    () -> "Current Magic resource group is missing: " + directory);
        }
        assertTrue(sources.stream().anyMatch(script -> inAnyGroup(script, BUSINESS_GROUPS)),
                "Current Magic resource tree is missing");

        Map<String, CanonicalMagicSources.Script> scripts = new LinkedHashMap<>();
        for (var script : sources) {
            String source = script.source();
            if (inAnyGroup(script, IDENTITY_GROUPS) || inAnyGroup(script, BUSINESS_GROUPS)
                    || source.contains("tableSort") || source.contains("metadataDiffView")) {
                scripts.put(script.relativePath(), script);
            }
        }
        assertLoggingResourcesSelected(scripts.keySet());

        addRequiredScript(scripts, "function/01.通用函数/19.记录业务操作.ms", "log_operation_function_v1");
        addRequiredScript(scripts, "api/01.系统门户/02.单点登录.ms", "c98ee7cf1481482586080eb71cf423d5");
        addRequiredScript(scripts, "api/01.系统门户/04.系统登出.ms", "10def1f21f6e42769e696534efbb91a9");
        for (String id : MULTI_VALUE_IDS) {
            var script = CanonicalMagicSources.scriptById(id);
            scripts.put(script.relativePath(), script);
        }

        return scripts.values().stream().map(script -> DynamicTest.dynamicTest(
                script.relativePath() + " [" + script.metadataId() + "]",
                () -> MagicScript.create(script.source(), null).compile()
        )).toList();
    }

    private static boolean inGroup(CanonicalMagicSources.Script script, String directory) {
        return script.relativePath().startsWith(directory + "/");
    }

    private static boolean inAnyGroup(CanonicalMagicSources.Script script, List<String> directories) {
        return directories.stream().anyMatch(directory -> inGroup(script, directory));
    }

    private static void assertLoggingResourcesSelected(Set<String> selectedPaths) throws IOException {
        // The logging suite covered every direct script in these folders, including newly added ones.
        var root = CanonicalMagicSources.magicRoot();
        for (String directory : List.of(TENANT_ACCOUNTS, IDAAS_LOGGING)) {
            try (var files = Files.list(root.resolve(directory))) {
                files.map(path -> path.getFileName().toString())
                        .filter(name -> name.endsWith(".ms"))
                        .forEach(name -> assertSelected(selectedPaths, directory + "/" + name));
            }
        }

        // Retain the pinned checks even when a listed authentication or audit script is removed from its group.
        for (String path : List.of(
                "api/09.系统管理/04.机构管理/02.机构保存修改.ms",
                "api/09.系统管理/04.机构管理/05.机构删除.ms",
                "api/13.统一身份管理/01.认证会话/01.登录管理控制台.ms",
                "api/13.统一身份管理/01.认证会话/02.退出管理控制台.ms",
                "api/13.统一身份管理/27.认证账号会话/02.登录统一认证账号.ms",
                "api/13.统一身份管理/27.认证账号会话/03.退出统一认证会话.ms",
                "api/13.统一身份管理/28.多因子认证/05.验证登录二次口令.ms",
                "function/13.统一身份管理/02.功能与对象范围校验.ms",
                "function/13.统一身份管理/03.记录脱敏审计.ms",
                "function/13.统一身份管理/09.接口访问与审计封装.ms",
                "function/13.统一身份管理/32.日志任务权限重校验.ms",
                "function/13.统一身份管理/33.日志请求权限.ms")) {
            assertSelected(selectedPaths, path);
        }
    }

    private static void assertSelected(Set<String> selectedPaths, String path) {
        assertTrue(selectedPaths.contains(path), () -> "Required Magic script is missing from compilation: " + path);
    }

    private static void addRequiredScript(Map<String, CanonicalMagicSources.Script> scripts,
                                          String path, String id) throws IOException {
        scripts.put(path, new CanonicalMagicSources.Script(id, path, CanonicalMagicSources.byPath(path, id)));
    }
}
