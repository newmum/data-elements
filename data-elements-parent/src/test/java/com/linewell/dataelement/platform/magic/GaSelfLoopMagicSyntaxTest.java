package com.linewell.dataelement.platform.magic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.ssssssss.script.MagicScript;
import static org.junit.jupiter.api.Assertions.*;

class GaSelfLoopMagicSyntaxTest {
    @TestFactory
    List<DynamicTest> compileLiveCandidatesWithActualMagicEngine() throws Exception {
        var root=Path.of("db/migrations/resources/ga-self-loop-20260928");
        try (var stream=Files.walk(root)) {
            var paths=stream.filter(p->p.toString().endsWith(".ms")).sorted().toList();
            assertTrue(paths.size()>=41,"Generate all live candidates before verification");
            return paths.stream().map(path->DynamicTest.dynamicTest(root.relativize(path).toString(),
                    ()->MagicScript.create(Files.readString(path),null).compile())).toList();
        }
    }
}
