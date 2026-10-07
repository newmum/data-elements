package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.ssssssss.script.MagicScript;

/** Compiles the deliverable against the exact MagicScript engine used by the shared backend. */
class PanshiMagicSyntaxTest {
    @TestFactory
    Stream<DynamicTest> compileTenantBusinessResources() throws IOException {
        var scripts = CanonicalMagicSources.under("function/14.磐石资源中心");
        assertFalse(scripts.isEmpty(), "Missing current Panshi Magic functions");
        return scripts.stream().map(script -> DynamicTest.dynamicTest(script.relativePath(), () -> {
            String source = script.source();
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
