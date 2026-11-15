package com.linewell.dataelement.feature.dataquality;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.linewell.dataelement.platform.magic.CanonicalMagicSources;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.ssssssss.script.MagicScript;

/** Offline compilation of canonical sources; no server or database mutation. */
class DataQualityMagicSyntaxTest {
    private static final List<String> GROUPS = List.of(
            "02.质量规则", "11.质量任务", "12.质量实例", "13.质量候选", "14.质量工单", "15.质检语句模板");

    @TestFactory
    List<DynamicTest> compileEditableQualityInterfaces() throws Exception {
        var tests = new ArrayList<DynamicTest>();
        for (String group : GROUPS) {
            var scripts = CanonicalMagicSources.under("api/04.数据治理/" + group);
            assertFalse(scripts.isEmpty(), "Current Magic resource group is missing: " + group);
            for (var script : scripts) {
                tests.add(DynamicTest.dynamicTest(script.relativePath() + " [" + script.metadataId() + "]",
                        () -> MagicScript.create(script.source(), null).compile()));
            }
        }
        return tests;
    }
}
