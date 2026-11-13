package com.linewell.dataelement.feature.logmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.audit.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LogParameterExportCompatibilityTest {
    @TempDir Path directory;

    @Test void jsonlAndNewArchivesUseNormalizedSafeSnapshotsWhileCsvRemainsSummaryOnly() throws Exception {
        var fixture = new LogParameterQueryIntegrationTest.Fixture();
        fixture.insert("log_system_t", "allowed", "validation-tenant", "validation-user", "org-1",
                "body.label=导出参数关键词\n", fixture.snapshot(Map.of("label", "导出参数关键词", "enabled", false)), "{}");
        fixture.insert("log_system_t", "excluded", "validation-tenant", "validation-user", "org-1",
                "body.label=不匹配\n", fixture.snapshot(Map.of("label", "不匹配")), "{}");
        var access = mock(LogAccessService.class);
        var scope = LogParameterQueryIntegrationTest.scope("ALL", java.util.List.of());
        when(access.refresh(any(), anyString())).thenReturn(scope);
        when(access.intersect(any(), any())).thenAnswer(call -> call.getArgument(0));
        var lifecycle = new LogLifecycleService(fixture.store, access, fixture.query, new ObjectMapper(),
                mock(AuditRecorder.class), mock(AuditPolicyCache.class), mock(LogJobLeases.class), directory.toString());
        var input = LogParameterQueryIntegrationTest.input("parameterKeyword", "导出参数关键词");
        try {
            Path jsonl = directory.resolve("validation.jsonl");
            assertEquals(1L, invoke(lifecycle, "writeFile", new Class<?>[]{LogScope.class, String.class, Map.class, Path.class, String.class, long.class},
                    scope, "operations", input, jsonl, "JSONL", 100L));
            var lines = Files.readAllLines(jsonl, StandardCharsets.UTF_8);
            assertEquals(1, lines.size());
            var row = new ObjectMapper().readValue(lines.getFirst(), Map.class);
            assertEquals("allowed", row.get("tid"));
            assertEquals("CAPTURED", row.get("parameter_capture_status"));
            assertTrue(row.get("parameter_snapshot") instanceof Map);
            assertFalse(row.containsKey("parameter_search_text"));
            assertEquals(false, ((Map<?,?>) ((Map<?,?>) row.get("parameter_snapshot")).get("body")).get("enabled"));

            Path csv = directory.resolve("validation.csv");
            assertEquals(1L, invoke(lifecycle, "writeFile", new Class<?>[]{LogScope.class, String.class, Map.class, Path.class, String.class, long.class},
                    scope, "operations", input, csv, "CSV", 100L));
            String summary = Files.readString(csv, StandardCharsets.UTF_8);
            assertEquals(2, summary.lines().count());
            assertFalse(summary.contains("parameter_snapshot"));
            assertFalse(summary.contains("导出参数关键词"));
            assertFalse(summary.contains("enabled"));

            Path archive = directory.resolve("validation.archive.jsonl");
            String[] digest = new String[1];
            assertEquals(1L, invoke(lifecycle, "writeArchiveFile", new Class<?>[]{LogScope.class, String.class, Map.class, Path.class, String[].class},
                    scope, "operations", input, archive, digest));
            assertEquals(lines, Files.readAllLines(archive, StandardCharsets.UTF_8));
            assertNotNull(digest[0]);
        } finally {
            lifecycle.close();
        }
    }

    @Test void csvEscapesNullQuotesCommasAndControlsWithoutChangingOrdinaryText() throws Exception {
        try (var fixture = new LifecycleFixture()) {
            var lifecycle = fixture.service;
            assertEquals("\"\"", invoke(lifecycle, "csv", new Class<?>[]{Object.class}, (Object) null));
            assertEquals("\"中文普通文本\"", invoke(lifecycle, "csv", new Class<?>[]{Object.class}, "中文普通文本"));
            assertEquals("\"  普通文本  \"", invoke(lifecycle, "csv", new Class<?>[]{Object.class}, "  普通文本  "));
            assertEquals("\"中文,\"\"引号\"\"\"", invoke(lifecycle, "csv", new Class<?>[]{Object.class}, "中文,\"引号\""));
            assertEquals("\"首行  次行 末列 \"", invoke(lifecycle, "csv", new Class<?>[]{Object.class}, "首行\r\n次行\t末列\u007f"));
            assertEquals("\"说明=+@-\"", invoke(lifecycle, "csv", new Class<?>[]{Object.class}, "说明=+@-"));
            assertEquals("\"0\"", invoke(lifecycle, "csv", new Class<?>[]{Object.class}, 0));
        }
    }

    @Test void csvNeutralizesFormulasAfterWhitespaceAndControlPrefixes() throws Exception {
        try (var fixture = new LifecycleFixture()) {
            for (String prefix : List.of("", " ", "  ", "\t", "\r\n", "\u0000", "\u007f", "\u00a0", "\u2003", "\u2028", "\ufeff", " \t\u00a0")) {
                for (String marker : List.of("=", "+", "@", "-")) {
                    String value = prefix + marker + "SUM(1,2)";
                    String expected = "\"'" + prefix.replaceAll("[\\x00-\\x1f\\x7f]", " ") + marker + "SUM(1,2)\"";
                    assertEquals(expected, invoke(fixture.service, "csv", new Class<?>[]{Object.class}, value),
                            "formula marker=" + marker + ", prefix=" + prefix.codePoints().boxed().toList());
                }
            }
            assertEquals("\"'=\"\"中文\"\"\"", invoke(fixture.service, "csv", new Class<?>[]{Object.class}, "=\"中文\""));
        }
    }

    @Test void csvUsesNormalizedHistoricalCreationTimeWithoutMutatingJsonlOrArchives() throws Exception {
        var fixture = new LogParameterQueryIntegrationTest.Fixture();
        fixture.insert("log_system_t", "legacy", "validation-tenant", "validation-user", "org-1", null, null, "{}");
        fixture.insert("log_system_t", "current", "validation-tenant", "validation-user", "org-1", null, null, "{}");
        fixture.insert("log_system_t", "undated", "validation-tenant", "validation-user", "org-1", null, null, "{}");
        fixture.setup.update("update log_system_t set event_time=null,created_time=timestamp '2026-11-01 09:30:00' where tid='legacy'");
        fixture.setup.update("update log_system_t set created_time=timestamp '2026-11-01 00:00:00' where tid='current'");
        fixture.setup.update("update log_system_t set event_time=null,created_time=null where tid='undated'");
        var access = mock(LogAccessService.class);
        var scope = LogParameterQueryIntegrationTest.scope("ALL", List.of());
        when(access.refresh(any(), anyString())).thenReturn(scope);
        when(access.intersect(any(), any())).thenAnswer(call -> call.getArgument(0));
        var lifecycle = new LogLifecycleService(fixture.store, access, fixture.query, new ObjectMapper(),
                mock(AuditRecorder.class), mock(AuditPolicyCache.class), mock(LogJobLeases.class), directory.toString());
        var input = LogParameterQueryIntegrationTest.input();
        try {
            Path csv = directory.resolve("validation-history.csv");
            assertEquals(2L, invoke(lifecycle, "writeFile", new Class<?>[]{LogScope.class, String.class, Map.class, Path.class, String.class, long.class},
                    scope, "operations", input, csv, "CSV", 100L));
            var lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
            assertEquals(3, lines.size());
            assertTrue(lines.getFirst().startsWith("\ufeff发生时间,"));
            assertTrue(lines.get(1).startsWith("\"2026-11-01T01:00:00Z\","), "new event time takes precedence over created_time");
            assertTrue(lines.get(2).startsWith("\"2026-11-01T01:30:00Z\","), "legacy Asia/Shanghai creation time is normalized exactly once");

            Path jsonl = directory.resolve("validation-history.jsonl");
            assertEquals(2L, invoke(lifecycle, "writeFile", new Class<?>[]{LogScope.class, String.class, Map.class, Path.class, String.class, long.class},
                    scope, "operations", input, jsonl, "JSONL", 100L));
            var snapshots = Files.readAllLines(jsonl, StandardCharsets.UTF_8);
            var legacy = new ObjectMapper().readValue(snapshots.get(1), Map.class);
            assertNull(legacy.get("event_time"));
            assertEquals("2026-11-01T01:30:00Z", legacy.get("created_time"));
            assertEquals(true, legacy.get("legacy"));
            Path archive = directory.resolve("validation-history.archive.jsonl");
            assertEquals(2L, invoke(lifecycle, "writeArchiveFile", new Class<?>[]{LogScope.class, String.class, Map.class, Path.class, String[].class},
                    scope, "operations", input, archive, new String[1]));
            assertEquals(snapshots, Files.readAllLines(archive, StandardCharsets.UTF_8));
        } finally {
            lifecycle.close();
        }
    }

    private final class LifecycleFixture implements AutoCloseable {
        final LogLifecycleService service = new LogLifecycleService(mock(AuditStore.class), mock(LogAccessService.class),
                mock(LogQueryService.class), new ObjectMapper(), mock(AuditRecorder.class), mock(AuditPolicyCache.class),
                mock(LogJobLeases.class), directory.toString());
        @Override public void close() { service.close(); }
    }

    private Object invoke(Object target, String name, Class<?>[] signature, Object... arguments) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, signature);
        method.setAccessible(true);
        try { return method.invoke(target, arguments); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception exception) throw exception;
            throw error;
        }
    }
}