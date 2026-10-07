package com.linewell.dataelement.feature.dataquality;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.linewell.dataelement.platform.magic.CanonicalMagicSources;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.ssssssss.script.MagicScript;

/** Offline compilation of canonical sources; no server or database mutation. */
class DataQualityMagicSyntaxTest {
    @TestFactory
    List<DynamicTest> compileEditableQualityInterfaces() throws Exception {
        var scripts = CanonicalMagicSources.under("api/05.数据治理/02.数据质量");
        assertFalse(scripts.isEmpty(), "Current Magic resource tree is missing");
        return scripts.stream().map(script -> DynamicTest.dynamicTest(
                script.relativePath() + " [" + script.metadataId() + "]",
                () -> MagicScript.create(script.source(), null).compile()
        )).toList();
    }
}
