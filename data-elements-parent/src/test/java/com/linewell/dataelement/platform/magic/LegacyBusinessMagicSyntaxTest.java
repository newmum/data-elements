package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.ssssssss.script.MagicScript;

class LegacyBusinessMagicSyntaxTest {

    @TestFactory
    List<DynamicTest> compileMigratedSecurityAndMetadataApis() throws Exception {
        List<Path> scripts;
        try (var files = Files.walk(Path.of("db/migrations/resources/security-magic-20261003"))) {
            scripts = files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".ms"))
                    .sorted().toList();
        }
        scripts = new java.util.ArrayList<>(scripts);
        scripts.add(Path.of("db/migrations/resources/metadata-magic-20261002/delete-relation.ms"));
        scripts.add(Path.of("db/migrations/resources/metadata-magic-20261002/save-relation.ms"));
        scripts.add(Path.of("db/migrations/resources/metadata-magic-20261002/discover-relation.ms"));
        scripts.add(Path.of("db/migrations/resources/metadata-magic-20261002/validate-relation.ms"));
        scripts.add(Path.of("db/migrations/resources/metadata-magic-20261002/preview-foreign-key.ms"));
        scripts.add(Path.of("db/migrations/resources/metadata-magic-20261002/create-foreign-key.ms"));
        scripts.add(Path.of("db/migrations/resources/api-pull-magic-20261003/sync-datasource.ms"));
        scripts.add(Path.of("db/migrations/resources/api-pull-magic-20261003/bind-task.ms"));
        scripts.add(Path.of("db/migrations/resources/api-pull-magic-20261003/list-services.ms"));
        scripts.add(Path.of("db/migrations/resources/api-pull-magic-20261003/list-rules.ms"));
        scripts.add(Path.of("db/migrations/resources/api-pull-magic-20261003/save-service.ms"));
        scripts.add(Path.of("db/migrations/resources/api-pull-magic-20261003/save-rule.ms"));
        scripts.add(Path.of("db/migrations/resources/approval-magic-20261003/permission-by-id.ms"));
        scripts.add(Path.of("db/migrations/resources/approval-magic-20261003/assignment.ms"));
        scripts.add(Path.of("db/migrations/resources/approval-magic-20261003/permissions.ms"));
        scripts.add(Path.of("db/migrations/resources/approval-magic-20261003/start.ms"));
        scripts.add(Path.of("db/migrations/resources/approval-magic-20261003/handle.ms"));
        scripts.add(Path.of("db/migrations/resources/approval-magic-20261003/revoke.ms"));
        scripts.add(Path.of("db/migrations/resources/approval-magic-20261003/termination.ms"));
        scripts.add(Path.of("db/migrations/resources/approval-magic-20261003/previous-nodes.ms"));
        scripts.add(Path.of("db/migrations/resources/approval-magic-20261003/callback.ms"));
        scripts.add(Path.of("db/migrations/resources/approval-magic-20261003/restore-asset.ms"));
        assertEquals(29, scripts.size());
        return scripts.stream().map(path -> DynamicTest.dynamicTest(
                path.getFileName().toString(),
                () -> MagicScript.create(Files.readString(path, StandardCharsets.UTF_8), null).compile()
        )).toList();
    }
}
