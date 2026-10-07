package com.linewell.dataelement.feature.reconciliation;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.linewell.dataelement.platform.magic.CanonicalMagicSources;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.ssssssss.script.MagicScript;

/** Offline compilation of canonical sources; no server or database mutation. */
class ReconciliationMagicSyntaxTest {
    @TestFactory
    List<DynamicTest> compileEditableReconciliationInterfaces() throws Exception {
        var scripts = CanonicalMagicSources.under("api/03.数据接入/03.数据对账");
        assertFalse(scripts.isEmpty(), "Current Magic resource tree is missing");
        return scripts.stream().map(script -> DynamicTest.dynamicTest(
                script.relativePath() + " [" + script.metadataId() + "]",
                () -> MagicScript.create(script.source(), null).compile()
        )).toList();
    }
}
