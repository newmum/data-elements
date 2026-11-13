package com.linewell.dataelement.platform.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuditParameterSnapshotTest {
    @Test void ordinaryBusinessCodesRemainSearchableWithoutAllowingSourceOrOtherHighRiskFields() {
        var normal = AuditParameterSnapshot.capture(Map.of(), Map.of("statusCode", 200, "businessCode", "validation-business",
                "regionCode", "validation-region"), null, Map.of(), List.of());
        assertEquals("CAPTURED", normal.status());
        assertTrue(normal.searchText().contains("query.statusCode=200\n"));
        assertTrue(normal.searchText().contains("query.businessCode=validation-business\n"));
        assertTrue(normal.searchText().contains("query.regionCode=validation-region\n"));

        var unsafe = new LinkedHashMap<String, Object>();
        for (String key : List.of("code", "CODE", "sourceCode", "source_code", "source-code", "source code", "sql", "sqlText",
                "script", "scriptContent", "connection", "connectionString", "config", "configuration", "statement",
                "queryText", "command", "expression", "headers", "源码", "脚本", "配置", "连接串")) {
            unsafe.put(key, "validation-high-risk-value");
        }
        var omitted = AuditParameterSnapshot.capture(Map.of(), Map.of(), unsafe, Map.of(), List.of());
        assertEquals("OMITTED", omitted.status());
        assertFalse((omitted.json() + omitted.searchText()).contains("validation-high-risk-value"));
        assertEquals("", omitted.searchText());
        assertTrue(omitted.notices().contains("HIGH_RISK_FIELD_OMITTED"));
    }

    @Test void legacyOnlyIndexesDisplayAllowlistedParametersAndNeverInventsTheirSources() {
        var parameters = new LinkedHashMap<String, Object>();
        parameters.put("taskId", "validation-task"); parameters.put("dryRun", false);
        parameters.put("taskName", List.of("历史任务_%", "第二历史任务"));
        parameters.put("deletedTaskName", "已记录历史名称");
        parameters.put("raw", "validation-raw-parameter");
        parameters.put("businessCode", "validation-unrecorded-code");
        parameters.put("query", Map.of("id", "validation-forged-query"));
        parameters.put("verified", Map.of("taskName", "validation-forged-fact"));
        var details = new LinkedHashMap<String, Object>();
        details.put("requestParameters", parameters);
        for (String key : List.of("req_param", "raw", "query", "path", "body", "verified", "requestParameterIndex", "legacy")) {
            details.put(key, Map.of("taskId", "validation-untrusted-detail"));
        }
        var snapshot = AuditParameterSnapshot.fromLegacy(details);
        assertLegacy(snapshot.asMap());
        assertEquals(AuditRequestParameters.display(parameters), snapshot.asMap().get("legacy"));
        assertTrue(snapshot.notices().contains("LEGACY_SOURCE_NOT_RECORDED"));
        assertEquals(Set.of("legacy.taskId=validation-task", "legacy.dryRun=false", "legacy.taskName[0]=历史任务_%",
                "legacy.taskName[1]=第二历史任务", "legacy.deletedTaskName=已记录历史名称"),
                new HashSet<>(snapshot.searchText().lines().toList()));
        for (String raw : List.of("validation-raw", "validation-unrecorded", "validation-forged", "validation-untrusted")) {
            assertFalse((snapshot.json() + snapshot.searchText()).contains(raw), raw);
        }
    }

    @Test void legacyJsonObjectsUseTheSameWhitelistAndRoundTripWithoutClaimingCompleteCapture() throws Exception {
        var details = Map.of("requestParameters", Map.of("id", "validation-id", "dryRun", false, "taskName", "已记录任务"));
        var snapshot = AuditParameterSnapshot.fromLegacy(new ObjectMapper().writeValueAsString(details));
        assertEquals(AuditParameterSnapshot.fromLegacy(details).asMap(), snapshot.asMap());
        assertEquals(snapshot.asMap(), AuditParameterSnapshot.parse(snapshot.json()));
        assertLegacy(snapshot.withStatus("CAPTURED").asMap());
        var annotated = snapshot.withNotice("BODY_NOT_CAPTURED");
        assertLegacy(annotated.asMap());
        assertEquals(snapshot.asMap().get("legacy"), annotated.asMap().get("legacy"));
        assertEquals(snapshot.searchText(), annotated.searchText());
    }

    @Test void legacyPhonesAndSensitiveTextAreMaskedAgainAndSourceTextIsOmitted() {
        var snapshot = AuditParameterSnapshot.fromLegacy(Map.of("requestParameters", Map.of(
                "id", "13800138000", "taskId", "validation-task", "taskName", List.of("联系13800138000",
                        "validation@example.test", "password=validation-old-secret", "select private_value from private_table"),
                "deletedTaskName", "Bearer validation-old-bearer", "phone", "13900139000")));
        var legacy = (Map<?, ?>) snapshot.asMap().get("legacy");
            assertEquals(List.of("联系138****8000", "v***@example.test", "[OMITTED]"), legacy.get("taskName"));
        assertFalse(legacy.containsKey("id"));
        assertFalse(legacy.containsKey("phone"));
        assertFalse(legacy.containsKey("deletedTaskName"));
        String output = snapshot.json() + snapshot.searchText();
        for (String raw : List.of("13800138000", "13900139000", "validation@example.test", "validation-old-secret",
                "validation-old-bearer", "private_value", "private_table")) assertFalse(output.contains(raw), raw);
        assertTrue(snapshot.searchText().contains("legacy.taskName[0]=联系138****8000"));
            assertFalse(snapshot.searchText().contains("[OMITTED]"));
        assertTrue(snapshot.notices().contains("HIGH_RISK_TEXT_OMITTED"));
        assertLegacy(snapshot.asMap());
    }

    @Test void parsingUntrustedLegacySnapshotsReappliesWhitelistAndMaskingAndDropsFabricatedSources() throws Exception {
        var stored = new LinkedHashMap<String, Object>();
        stored.put("version", 1); stored.put("status", "LEGACY");
        stored.put("path", Map.of("id", "validation-forged-path"));
        stored.put("query", Map.of("id", "validation-forged-query"));
        stored.put("body", Map.of("name", "validation-forged-body"));
        stored.put("verified", Map.of("taskName", "validation-forged-fact"));
        stored.put("legacy", Map.of("taskId", "validation-id", "taskName", "联系13800138000", "deletedTaskName", "javascript:private_source",
                "password", "validation-raw-password", "raw", "validation-raw-unknown", "req_param", "validation-raw-request"));
        stored.put("notices", List.of("validation-raw-notice", "BODY_CONVERSION_FAILED"));
        var parsed = AuditParameterSnapshot.parse(new ObjectMapper().writeValueAsString(stored));
        assertLegacy(parsed);
        assertEquals(Map.of("taskId", "validation-id", "taskName", "联系138****8000"), parsed.get("legacy"));
        assertTrue(((List<?>) parsed.get("notices")).contains("LEGACY_SOURCE_NOT_RECORDED"));
        assertTrue(((List<?>) parsed.get("notices")).contains("BODY_CONVERSION_FAILED"));
        for (String raw : List.of("validation-forged", "validation-raw", "13800138000", "private_source")) {
            assertFalse(parsed.toString().contains(raw), raw);
        }
        var rebuilt = AuditParameterSnapshot.fromLegacy(Map.of("requestParameters", parsed.get("legacy")));
        assertTrue(rebuilt.searchText().contains("legacy.taskName=联系138****8000"));
        assertFalse(rebuilt.searchText().contains("validation-forged"));
        @SuppressWarnings("unchecked") var legacy = (Map<String, Object>) parsed.get("legacy");
        assertThrows(UnsupportedOperationException.class, () -> legacy.put("raw", "validation-injected"));
    }

    @Test void invalidLegacyGroupsCannotFallBackToOtherGroupsOrBecomeSuccessful() throws Exception {
        for (Object legacy : Arrays.asList(null, "{\"taskId\":\"validation-injected\"}", List.of("validation-injected"), true, 1)) {
            var stored = new LinkedHashMap<>(AuditParameterSnapshot.empty("LEGACY").asMap());
            stored.put("legacy", legacy); stored.put("query", Map.of("id", "validation-forged-query"));
            var parsed = AuditParameterSnapshot.parse(new ObjectMapper().writeValueAsString(stored));
            assertLegacy(parsed);
            assertFalse(parsed.containsKey("legacy"));
            assertTrue(((List<?>) parsed.get("notices")).contains("INVALID_LEGACY_DETAILS"));
            assertFalse(parsed.toString().contains("validation-"));
        }
    }

    @Test void otherNewSnapshotStatusesAndLegacyWithoutTheExtraGroupKeepExistingMeaning() throws Exception {
        for (String status : List.of("CAPTURED", "EMPTY", "PARTIAL", "OMITTED", "FAILED", "LEGACY")) {
            var original = AuditParameterSnapshot.empty(status);
            assertEquals(original.asMap(), AuditParameterSnapshot.parse(original.json()));
            if (!status.equals("LEGACY")) {
                var stored = new LinkedHashMap<>(original.asMap());
                stored.put("legacy", Map.of("taskName", "validation-untrusted-extra"));
                assertEquals(original.asMap(), AuditParameterSnapshot.parse(new ObjectMapper().writeValueAsString(stored)));
            }
        }
        var oldShape = AuditParameterSnapshot.capture(Map.of("id", "validation-id"), Map.of(), null,
                Map.of("taskName", "原新快照事实"), List.of()).withStatus("LEGACY");
        assertEquals(oldShape.asMap(), AuditParameterSnapshot.parse(oldShape.json()));
    }

    @Test void missingUnknownAndMalformedHistoricalDetailsAreEmptyLegacyWithSafeNotices() {
        var unknownObject = new Object() {
            @Override public String toString() { throw new AssertionError("must not stringify arbitrary objects"); }
        };
        for (Object details : Arrays.asList(null, "", "  ", "{}", Map.of(), "null", "[]", "42", "true", List.of(), 42, unknownObject,
                "{broken-validation-raw-secret", "{\"requestParameters\":{}} {\"raw\":true}",
                Map.of("req_param", Map.of("taskId", "validation-raw-id")), Map.of("taskId", "validation-raw-id"),
                Map.of("legacy", Map.of("taskId", "validation-raw-id")), Map.of("requestParameters", Map.of("raw", "validation-raw-text")),
                Map.of("requestParameters", List.of("validation-raw-text")), Map.of("requestParameters", "{\"taskId\":\"validation-raw-id\"}"),
                Map.of("requestParameters", Map.of("taskName", "password=validation-raw-secret")))) {
            var snapshot = assertDoesNotThrow(() -> AuditParameterSnapshot.fromLegacy(details));
            assertLegacy(snapshot.asMap());
            assertFalse(snapshot.asMap().containsKey("legacy"));
            assertEquals("", snapshot.searchText());
            assertFalse(snapshot.notices().isEmpty());
            assertTrue(AuditParameterSanitizer.NOTICE_CODES.containsAll(snapshot.notices()));
            assertFalse(snapshot.json().contains("validation-raw"));
        }
        Map<String, Object> unreadable = new AbstractMap<>() {
            @Override public Object get(Object key) { throw new IllegalStateException("validation-raw-error"); }
            @Override public Set<Entry<String, Object>> entrySet() { throw new AssertionError("must not traverse details"); }
        };
        var failed = assertDoesNotThrow(() -> AuditParameterSnapshot.fromLegacy(unreadable));
        assertLegacy(failed.asMap());
        assertFalse(failed.asMap().containsKey("legacy"));
        assertTrue(failed.notices().contains("INVALID_LEGACY_DETAILS"));
        assertFalse(failed.json().contains("validation-raw-error"));
    }

    @Test void historicalJsonParsingIsBoundedByBytesDepthNumberLengthAndTrailingTokens() {
        for (String details : List.of(" ".repeat(AuditParameterSnapshot.MAX_UTF8_BYTES + 1),
                "{\"raw\":\"" + "中".repeat(12000) + "\"}", "{\"raw\":" + "[".repeat(20) + "0" + "]".repeat(20) + "}",
                "{\"raw\":" + "9".repeat(140) + "}", "{} false")) {
            var snapshot = AuditParameterSnapshot.fromLegacy(details);
            assertLegacy(snapshot.asMap());
            assertFalse(snapshot.asMap().containsKey("legacy"));
            assertEquals("", snapshot.searchText());
            assertTrue(snapshot.notices().contains("TOTAL_BYTE_LIMIT") || snapshot.notices().contains("INVALID_LEGACY_DETAILS"));
        }
    }

    @Test void legacyUsesBoundedCopiesOfOnlySafeValuesAndRetainsNoMutableReferences() throws Exception {
        var names = new ArrayList<String>();
        for (int i = 0; i < 100; i++) names.add("😀".repeat(254) + i);
        var parameters = new LinkedHashMap<String, Object>();
        parameters.put("taskName", names); parameters.put("deletedTaskName", names);
        parameters.put("taskId", "validation-id"); parameters.put("id", Map.of("nested", List.of("validation-raw-value")));
        parameters.put("raw", "中".repeat(50000));
        var details = new LinkedHashMap<String, Object>(); details.put("requestParameters", parameters);
        var snapshot = AuditParameterSnapshot.fromLegacy(details);
        assertLegacy(snapshot.asMap());
        assertTrue(snapshot.json().getBytes(StandardCharsets.UTF_8).length <= AuditParameterSnapshot.MAX_UTF8_BYTES);
        assertTrue(snapshot.searchText().getBytes(StandardCharsets.UTF_8).length <= AuditParameterSnapshot.MAX_UTF8_BYTES);
        assertTrue(snapshot.notices().size() <= AuditParameterSanitizer.MAX_NOTICES);
        @SuppressWarnings("unchecked") var legacy = (Map<String, Object>) snapshot.asMap().get("legacy");
        @SuppressWarnings("unchecked") var storedNames = (List<String>) legacy.get("taskName");
        assertEquals(10, storedNames.size());
        assertTrue(storedNames.stream().allMatch(name -> name.codePointCount(0, name.length()) <= 255));
        assertFalse(legacy.containsKey("id"));
        String before = snapshot.json(), projection = snapshot.searchText();
        names.set(0, "validation-mutated"); parameters.put("taskId", "validation-mutated"); details.clear();
        assertEquals(before, snapshot.json()); assertEquals(projection, snapshot.searchText());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.asMap().put("status", "CAPTURED"));
        assertThrows(UnsupportedOperationException.class, () -> legacy.put("taskId", "validation-injected"));
        assertThrows(UnsupportedOperationException.class, () -> storedNames.set(0, "validation-injected"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.notices().clear());
        var parsed = AuditParameterSnapshot.parse(snapshot.json());
        assertEquals(snapshot.asMap(), parsed);
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) ((Map<?, ?>) parsed.get("legacy")).get("taskName")).clear());
    }

    private void assertLegacy(Map<String, Object> snapshot) {
        assertEquals(1, snapshot.get("version")); assertEquals("LEGACY", snapshot.get("status"));
        assertEquals(Map.of(), snapshot.get("path")); assertEquals(Map.of(), snapshot.get("query"));
        assertNull(snapshot.get("body")); assertEquals(Map.of(), snapshot.get("verified"));
    }
}