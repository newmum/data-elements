package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.ssssssss.script.MagicScript;

/** Offline compilation of canonical sources; no server or database mutation. */
class LegacyBusinessMagicSyntaxTest {
    @TestFactory
    List<DynamicTest> compileCurrentSecurityMetadataAndAccessResources() throws Exception {
        var scripts = CanonicalMagicSources.under("api/02.数据盘点/02.数据库表/01.元数据管理", "api/09.系统管理/02.审批中心", "api/09.系统管理/03.安全管理", "api/03.数据接入");
        assertFalse(scripts.isEmpty(), "Current Magic resource tree is missing");
        return scripts.stream().map(script -> DynamicTest.dynamicTest(
                script.relativePath() + " [" + script.metadataId() + "]",
                () -> MagicScript.create(script.source(), null).compile()
        )).toList();
    }
}
