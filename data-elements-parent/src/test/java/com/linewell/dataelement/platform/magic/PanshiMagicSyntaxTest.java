package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.ssssssss.script.MagicScript;

/** Compiles the deliverable against the exact MagicScript engine used by the shared backend. */
class PanshiMagicSyntaxTest {
    @TestFactory
    Stream<DynamicTest> compileTenantBusinessResources() throws IOException {
        Path root = Path.of("db/migrations/resources/panshi-integration-20261002");
        List<Path> scripts;
        try (Stream<Path> paths = Files.walk(root)) {
            scripts = paths.filter(path -> path.toString().endsWith(".ms")).sorted().toList();
        }
        assertFalse(scripts.isEmpty(), "Missing Panshi Magic deliverables");
        return scripts.stream().map(path -> DynamicTest.dynamicTest(root.relativize(path).toString(), () -> {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            MagicScript.create(source, null).compile();
            assertTrue(source.contains("tenantRuntime.id()"), "Tenant must originate in the server session");
            Pattern clientTenantAssignment = Pattern.compile(
                    "\\btenantId\\s*=\\s*[^;\\n]*(?:body|param|principal)\\.tenant(?:Id|_id)\\b");
            assertFalse(clientTenantAssignment.matcher(source).find(),
                    "Tenant ID must not be assigned from client input");
            assertFalse(source.contains("tenant.id()"), "Use the session tenant runtime");
        }));
    }
}
