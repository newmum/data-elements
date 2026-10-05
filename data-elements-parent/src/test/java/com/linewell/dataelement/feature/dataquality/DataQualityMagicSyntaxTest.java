package com.linewell.dataelement.feature.dataquality;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.ssssssss.script.MagicScript;

class DataQualityMagicSyntaxTest {

    @TestFactory
    List<DynamicTest> compileEditableQualityInterfaces() throws Exception {
        Path root = Path.of("db/migrations/resources/quality-magic-20261002/apis");
        List<Path> scripts;
        try (var files = Files.walk(root)) {
            scripts = files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".ms"))
                    .sorted()
                    .toList();
        }
        assertEquals(16, scripts.size());
        return scripts.stream().map(path -> DynamicTest.dynamicTest(
                path.getFileName().toString(),
                () -> MagicScript.create(Files.readString(path, StandardCharsets.UTF_8), null).compile()
        )).toList();
    }
}
