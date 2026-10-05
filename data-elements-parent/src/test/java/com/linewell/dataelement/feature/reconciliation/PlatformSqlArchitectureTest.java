package com.linewell.dataelement.feature.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformSqlArchitectureTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/linewell/dataelement");

    @Test
    void tenantAndReconciliationControlCodeDoNotUseJdbcTemplate() throws Exception {
        List<Path> violations = sourceFiles("tenant", "reconciliation").stream()
                .filter(path -> contains(path, "JdbcTemplate"))
                .toList();

        assertThat(violations).isEmpty();
    }

    @Test
    void reconciliationJavaDoesNotContainDatabaseSpecificControlSql() throws Exception {
        List<Path> violations = sourceFiles("reconciliation").stream()
                .filter(path -> {
                    String source = read(path).toLowerCase();
                    return source.contains("insert ignore into")
                            || source.matches("(?s).*\\blimit\\s+[?0-9].*")
                            || source.contains("cast(? as json)")
                            || source.contains("on duplicate key");
                })
                .toList();

        assertThat(violations).isEmpty();
    }

    private List<Path> sourceFiles(String... packages) throws IOException {
        try (var paths = Files.walk(SOURCE_ROOT)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        String normalized = path.toString().replace('\\', '/');
                        for (String packageName : packages) {
                            if (normalized.contains("/" + packageName + "/")) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .toList();
        }
    }

    private boolean contains(Path path, String text) {
        return read(path).contains(text);
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
