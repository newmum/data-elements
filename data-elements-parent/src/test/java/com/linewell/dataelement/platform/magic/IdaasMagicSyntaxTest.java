package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.ssssssss.script.MagicScript;

/** Offline compilation of canonical sources; no server or database mutation. */
class IdaasMagicSyntaxTest {
    @TestFactory
    List<DynamicTest> compileEveryIdentityFunctionAndApiWithoutExecution() throws Exception {
        var scripts = CanonicalMagicSources.under("api/13.统一身份管理", "function/13.统一身份管理");
        assertFalse(scripts.isEmpty(), "Current Magic resource tree is missing");
        return scripts.stream().map(script -> DynamicTest.dynamicTest(
                script.relativePath() + " [" + script.metadataId() + "]",
                () -> MagicScript.create(script.source(), null).compile()
        )).toList();
    }
}
