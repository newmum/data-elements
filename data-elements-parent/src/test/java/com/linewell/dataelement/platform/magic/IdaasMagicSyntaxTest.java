package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.ssssssss.script.MagicScript;

/**
 * Offline syntax verification against the project's actual MagicScript engine.
 * Compilation does not execute the scripts, load Spring, connect databases or
 * resolve API function calls. Function metadata parameters are runtime inputs;
 * MagicScript compilation permits these undeclared environment variables.
 */
class IdaasMagicSyntaxTest {

    @TestFactory
    List<DynamicTest> compileEveryIdentityFunctionAndApiWithoutExecution() throws Exception {
        Path root = Path.of(System.getProperty(
                "idaas.magic.sourceRoot", "db/migrations/resources/idaas-foundation-20260928"
        )).toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(root), "Identity Magic source directory is missing: " + root);
        List<Path> scripts;
        try (var paths = Files.walk(root)) {
            scripts = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".ms"))
                    .filter(path -> {
                        Path relative = root.relativize(path);
                        return relative.getNameCount() > 1
                                && ("functions".equals(relative.getName(0).toString())
                                || "apis".equals(relative.getName(0).toString()));
                    })
                    .sorted()
                    .toList();
        }
        assertFalse(scripts.isEmpty(), "No identity Magic scripts were found");
        return scripts.stream().map(path -> DynamicTest.dynamicTest(
                root.relativize(path).toString(),
                () -> {
                    String source = Files.readString(path, StandardCharsets.UTF_8);
                    if (source.startsWith("\uFEFF")) {
                        source = source.substring(1);
                    }
                    MagicScript.create(source, null).compile();
                }
        )).toList();
    }
}
