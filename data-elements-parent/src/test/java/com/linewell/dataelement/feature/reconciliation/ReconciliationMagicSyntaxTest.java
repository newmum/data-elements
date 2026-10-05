package com.linewell.dataelement.feature.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.ssssssss.script.MagicScript;

class ReconciliationMagicSyntaxTest {

    @TestFactory
    List<DynamicTest> compileEditableReconciliationInterfaces() throws Exception {
        Path root = Path.of("db/migrations/resources/reconciliation-magic-20261002/apis");
        List<Path> scripts;
        try (var files = Files.walk(root)) {
            scripts = files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".ms"))
                    .sorted()
                    .toList();
        }
        assertEquals(13, scripts.size());
        return scripts.stream().map(path -> DynamicTest.dynamicTest(
                root.relativize(path).toString(),
                () -> MagicScript.create(Files.readString(path, StandardCharsets.UTF_8), null).compile()
        )).toList();
    }
}
