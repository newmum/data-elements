package com.linewell.dataelement.platform.magic;

import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.ssssssss.script.MagicScript;

/** Compile the maintained scripts which use the common order allowlist. */
class TableSortingMagicSyntaxTest {
    @TestFactory List<DynamicTest> compileSortedReads() throws Exception {
        return CanonicalMagicSources.under("api","function").stream()
                .filter(source -> source.source().contains("tableSort") || source.source().contains("metadataDiffView"))
                .map(source -> DynamicTest.dynamicTest(source.relativePath(),
                        () -> MagicScript.create(source.source(),null).compile())).toList();
    }
}
