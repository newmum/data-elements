package com.linewell.dataelement.platform.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditRequestParametersTest {
    private MockHttpServletRequest request() { return new MockHttpServletRequest("POST", "/validation/parameters"); }

    @Test void separatesSourcesPreservesJsonValuesAndDoesNotTrustRequestBusinessFacts() {
        var request = request();
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("enabled", false); nested.put("count", 0); nested.put("optional", null);
        nested.put("items", Arrays.asList("中文检索", null, false, 7));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("filters", nested); body.put("taskName", "请求任务"); body.put("deletedTaskName", "请求伪造删除事实");
        body.put("verified", Map.of("taskName", "请求伪造核验事实"));
        AuditRequestParameters.capture(request, Map.of("keyword", "查询中文"), Map.of("id", "validation-id"), body);
        var safe = AuditRequestParameters.safeSnapshot(request);
        assertEquals("CAPTURED", safe.status());
        assertEquals(Map.of("id", "validation-id"), safe.asMap().get("path"));
        assertEquals(Map.of("keyword", "查询中文"), safe.asMap().get("query"));
        assertEquals(Map.of(), safe.asMap().get("verified"));
        assertTrue(AuditRequestParameters.snapshot(request).containsKey("id"));
        assertFalse(AuditRequestParameters.snapshot(request).containsKey("deletedTaskName"));
        assertFalse(AuditRequestParameters.snapshot(request).containsKey("taskName"));
        assertTrue(safe.searchText().contains("body.filters.enabled=false"));
        assertTrue(safe.searchText().contains("body.filters.count=0"));
        assertTrue(safe.searchText().contains("body.filters.optional=null"));
        assertTrue(safe.searchText().contains("body.filters.items[0]=中文检索"));
        assertTrue(safe.searchText().contains("body.filters.items[1]=null"));
        assertFalse(safe.searchText().contains("\"filters\""));
    }

    @Test void recursivelyFreezesCopiesAndNeverRetainsRequestOrVerifiedReferences() {
        var request = request();
        var items = new ArrayList<Object>(Arrays.asList("原始中文", null, false));
        var body = new LinkedHashMap<String, Object>(); body.put("items", items);
        AuditRequestParameters.capture(request, Map.of(), Map.of(), body);
        var facts = new ArrayList<>(List.of("已核验任务"));
        AuditRequestParameters.enrich(request, Map.of("taskName", facts));
        var safe = AuditRequestParameters.safeSnapshot(request);
        String before = safe.json();
        items.set(0, "并发改动"); body.put("extra", "并发新增"); facts.set(0, "未核验替换");
        assertEquals(before, safe.json());
        assertFalse(safe.searchText().contains("并发"));
        assertThrows(UnsupportedOperationException.class, () -> safe.asMap().put("status", "SUCCESS"));
        @SuppressWarnings("unchecked") var savedBody = (Map<String, Object>) safe.asMap().get("body");
        assertThrows(UnsupportedOperationException.class, () -> savedBody.put("injected", true));
        @SuppressWarnings("unchecked") var savedItems = (List<Object>) savedBody.get("items");
        assertThrows(UnsupportedOperationException.class, () -> savedItems.set(0, "injected"));
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) safe.asMap().get("notices")).clear());
    }

    @Test void onlyEnrichAddsVerifiedDeletionFactsAndLegacyWhiteListStillWorks() {
        var request = request();
        AuditRequestParameters.capture(request, Map.of("taskId", "validation-id", "dryRun", false,
                "taskName", "伪造任务", "deletedTaskId", "forged-id"), Map.of(), Map.of());
        AuditRequestParameters.enrich(request, Map.of("taskName", List.of("核验任务_%"), "taskId", "validation-id"));
        var facts = (Map<?, ?>) AuditRequestParameters.safeSnapshot(request).asMap().get("verified");
        assertEquals(List.of("核验任务_%"), facts.get("taskName"));
        assertFalse(facts.containsKey("deletedTaskId"));
        AuditRequestParameters.enrich(request, Map.of("deletedTaskId", List.of("validation-id"), "deletedTaskName", List.of("实际删除任务")));
        assertEquals("false", AuditRequestParameters.snapshot(request).get("dryRun"));
        assertTrue(AuditRequestParameters.index(AuditRequestParameters.snapshot(request)).keySet().stream()
                .anyMatch(key -> AuditRequestParameters.exactMatchPattern("taskName", "核验任务_%").contains(key)));
        assertEquals(Map.of("dryRun", "false"), AuditRequestParameters.display(Map.of("dryRun", false, "sql", "select raw")));
        assertThrows(IllegalArgumentException.class, () -> AuditRequestParameters.exactMatchPattern("password", "unsafe"));
    }

        @Test void legacyExactSearchCannotNormalizeSensitiveOriginalsIntoMatchingMaskedValues() {
        var request = request();
        AuditRequestParameters.enrich(request, Map.of("taskName", "联系13800138000"));
        var stored = AuditRequestParameters.snapshot(request);
        assertEquals("联系138****8000", stored.get("taskName"));
        assertThrows(IllegalArgumentException.class,
            () -> AuditRequestParameters.exactMatchPattern("taskName", "联系13800138000"));
        assertThrows(IllegalArgumentException.class,
            () -> AuditRequestParameters.exactMatchPattern("taskName", null));
        assertTrue(AuditRequestParameters.index(stored).keySet().stream()
            .anyMatch(key -> AuditRequestParameters.exactMatchPattern("taskName", "联系138****8000").contains(key)));
        }

        @Test void secretsHighRiskTextAndPersonalDataNeverReachJsonOrSearch() {
        var request = request();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "业务中文"); body.put("password", "raw-pass-value"); body.put("nested", Map.of("access_token", "raw-access-value"));
        for (String key : List.of("sql", "script", "code", "connection", "config")) body.put(key, "raw-" + key + "-value");
        body.put("notes", List.of("password=raw-free-pass", "Bearer raw-bearer", "cookie: raw-cookie", "https://u:raw-uri-pass@host/a",
                "-----BEGIN PRIVATE KEY-----raw-key", "https%3A%2F%2Fu%3Araw-encoded-pass%40host"));
        body.put("contact", "联系13800138000，validation@example.test，110101199001012345");
        body.put("mobile", 13800138000L); body.put("description", "select raw_table_value from raw_table");
        AuditRequestParameters.capture(request, Map.of(), Map.of(), body);
        var safe = AuditRequestParameters.safeSnapshot(request);
        assertEquals("PARTIAL", safe.status());
        String output = safe.json() + safe.searchText();
        for (String raw : List.of("raw-pass", "raw-access", "raw-sql", "raw-script", "raw-code", "raw-connection", "raw-config",
                "raw-free", "raw-bearer", "raw-cookie", "raw-uri", "raw-key", "raw-encoded", "raw_table", "13800138000", "validation@example.test", "110101199001012345")) {
            assertFalse(output.contains(raw), raw);
        }
        assertTrue(output.contains("业务中文"));
        assertTrue(safe.searchText().contains("138****8000"));
        assertFalse(safe.searchText().contains("[REDACTED]"));
    }

    @Test void filesOnlyExposeMetadataAndStreamsServletObjectsAndBinaryAreNeverRead() throws Exception {
        var request = request();
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("C:\\upload\\validation.csv");
        when(file.getContentType()).thenReturn("text/csv"); when(file.getSize()).thenReturn(42L);
        InputStream stream = new InputStream() { @Override public int read() { throw new AssertionError("must not read"); } };
        AuditRequestParameters.capture(request, Map.of(), Map.of(), Map.of("file", file, "stream", stream, "servlet", request,
                "binary", new byte[] {65, 66}, "name", "安全文件"));
        var safe = AuditRequestParameters.safeSnapshot(request);
        assertEquals("PARTIAL", safe.status());
        assertTrue(safe.json().contains("validation.csv")); assertTrue(safe.searchText().contains("size=42"));
        assertFalse(safe.json().contains("upload")); assertFalse(safe.json().contains("servlet"));
        verify(file, never()).getBytes(); verify(file, never()).getInputStream();
    }

    @Test void dtoAndRecordFieldsAreCapturedWithoutInvokingGettersOrArbitraryToString() {
        var request = request();
        AuditRequestParameters.capture(request, Map.of(), Map.of(), new Dto());
        var safe = AuditRequestParameters.safeSnapshot(request);
        assertTrue(safe.searchText().contains("body.name=正常DTO"));
        assertTrue(safe.searchText().contains("body.item.enabled=false"));
        assertTrue(safe.searchText().contains("body.item.count=0"));
        assertFalse(safe.json().contains("raw-dto-pass"));
        assertFalse(safe.json().contains("raw-hidden"));
    }

    @Test void enforcesDepthItemsNodesStringsNoticesAndTotalUtf8Budgets() {
        var request = request();
        Map<String, Object> deep = Map.of("leaf", "不可达深层");
        for (int i = 0; i < 20; i++) deep = Map.of("child", deep);
        List<Object> many = new ArrayList<>();
        for (int i = 0; i < 100; i++) many.add(Map.of("items", Collections.nCopies(100, "普通短文本")));
        AuditRequestParameters.capture(request, Map.of(), Map.of(), Map.of("deep", deep, "many", many, "longText", "😀".repeat(2000)));
        var limited = AuditRequestParameters.safeSnapshot(request);
        assertNotEquals("CAPTURED", limited.status());
        assertTrue(limited.json().getBytes(StandardCharsets.UTF_8).length <= AuditParameterSnapshot.MAX_UTF8_BYTES);
        assertTrue(((List<?>) limited.asMap().get("notices")).size() <= AuditParameterSanitizer.MAX_NOTICES);
        assertFalse(limited.json().contains("不可达深层"));
        var totalRequest = request();
        AuditRequestParameters.capture(totalRequest, Map.of(), Map.of(), Collections.nCopies(64, "中".repeat(900)));
        var total = AuditRequestParameters.safeSnapshot(totalRequest);
        assertEquals("OMITTED", total.status());
        assertTrue(total.json().contains("TOTAL_BYTE_LIMIT"));
        assertEquals("", total.searchText());
    }

    @Test void cyclesCaptureFailuresAndMissingCaptureHaveExplicitNonFabricatedStatuses() {
        assertEquals("LEGACY", AuditRequestParameters.safeSnapshot(request()).status());
        assertEquals("LEGACY", AuditRequestParameters.safeSnapshot(null).status());
        var empty = request(); AuditRequestParameters.capture(empty, Map.of(), Map.of(), null);
        assertEquals("EMPTY", AuditRequestParameters.safeSnapshot(empty).status());
        assertEquals("", AuditRequestParameters.safeSnapshot(empty).searchText());
        var omitted = request(); AuditRequestParameters.capture(omitted, Map.of(), Map.of(), Map.of("sql", "raw-value"));
        assertEquals("OMITTED", AuditRequestParameters.safeSnapshot(omitted).status());
        var cycle = new LinkedHashMap<String, Object>(); cycle.put("name", "有界循环"); cycle.put("self", cycle);
        AuditRequestParameters.capture(empty, Map.of(), Map.of(), cycle);
        assertEquals("PARTIAL", AuditRequestParameters.safeSnapshot(empty).status());
        var broken = request();
        Map<String, Object> bad = new AbstractMap<>() {
            @Override public Set<Entry<String, Object>> entrySet() { throw new IllegalStateException("raw-error-password"); }
        };
        assertDoesNotThrow(() -> AuditRequestParameters.capture(broken, Map.of(), Map.of(), bad));
        assertEquals("FAILED", AuditRequestParameters.safeSnapshot(broken).status());
        assertFalse(AuditRequestParameters.safeSnapshot(broken).json().contains("raw-error"));
    }

    @Test void detailAdapterReturnsSafeObjectsAndKeepsNullFalseZeroAndLegacyMeaning() throws Exception {
        var request = request();
        var body = new ObjectMapper().readValue("{\"optional\":null,\"flag\":false,\"count\":0}", Map.class);
        AuditRequestParameters.capture(request, Map.of(), Map.of(), body);
        var safe = AuditRequestParameters.safeSnapshot(request);
        assertEquals(safe.asMap(), AuditParameterSnapshot.parse(safe.json()));
        assertEquals("LEGACY", AuditParameterSnapshot.parse(null).get("status"));
        assertEquals("FAILED", AuditParameterSnapshot.parse("not-json raw-error").get("status"));
        assertEquals("FAILED", AuditParameterSnapshot.parse("{}").get("status"));
        assertFalse(AuditParameterSnapshot.parse("not-json raw-error").toString().contains("raw-error"));
    }

    @Test void scalarBodiesRetainFalseAndZero() {
        for (Object value : List.of(false, 0, "中文正文", List.of("中文数组", false, 0))) {
            var request = request(); AuditRequestParameters.capture(request, Map.of(), Map.of(), value);
            assertEquals(value, AuditRequestParameters.safeSnapshot(request).asMap().get("body"));
            assertEquals("CAPTURED", AuditRequestParameters.safeSnapshot(request).status());
        }
    }

    @Test void knownDateAndUuidScalarsAreCopiedWithoutArbitraryObjectSerialization() {
        var request = request();
        AuditRequestParameters.capture(request, Map.of(), Map.of(), Map.of("date", java.time.LocalDate.of(2026, 11, 13),
                "reference", UUID.fromString("00000000-0000-0000-0000-000000000001")));
        var safe = AuditRequestParameters.safeSnapshot(request);
        assertEquals("CAPTURED", safe.status());
        assertTrue(safe.searchText().contains("body.date=2026-11-13"));
        assertTrue(safe.searchText().contains("body.reference=00000000-0000-0000-0000-000000000001"));
    }

    @Test void aliasesCannotDeclassifyPersonalFieldsAndLegacyParametersCannotBypassMasking() {
        var request = request();
        AuditRequestParameters.capture(request, Map.of("id", "13800138000"), Map.of(), new AliasedDto());
        var safe = AuditRequestParameters.safeSnapshot(request);
        assertFalse(safe.json().contains("个人姓名甲"));
        assertFalse(safe.searchText().contains("个人姓名甲"));
        assertFalse(AuditRequestParameters.snapshot(request).containsKey("id"));
        assertFalse(new AuditSanitizer(new ObjectMapper()).json(null, AuditRequestParameters.snapshot(request)).contains("13800138000"));
        assertEquals("2084109831682699264", AuditRequestParameters.display(Map.of("id", "2084109831682699264")).get("id"));
        assertDoesNotThrow(() -> AuditRequestParameters.exactMatchPattern("taskName", "验证任务_%"));
    }

    @Test void personalReplacementNodesAlsoConsumeTheGlobalBudget() {
        List<Object> body = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Map<String, Object> phones = new LinkedHashMap<>();
            for (int bits = 0; bits < 32; bits++) {
                StringBuilder key = new StringBuilder();
                for (int j = 0; j < 5; j++) key.append((bits & 1 << j) == 0 ? "phone".charAt(j) : Character.toUpperCase("phone".charAt(j)));
                phones.put(key.toString(), "13800138000");
            }
            body.add(phones);
        }
        var request = request(); AuditRequestParameters.capture(request, Map.of(), Map.of(), body);
        var safe = AuditRequestParameters.safeSnapshot(request);
        assertTrue(nodes(safe.asMap().get("body")) <= AuditParameterSanitizer.MAX_NODES);
        assertTrue(safe.json().contains("ITEM_LIMIT") || safe.json().contains("NODE_LIMIT"));
    }

    @Test void storedCapturedStatusCannotOverrideFailedConversionEvidence() {
        var stored = "{\"version\":1,\"status\":\"CAPTURED\",\"path\":{},\"query\":{},\"body\":null,\"verified\":{},\"notices\":[\"BODY_CONVERSION_FAILED\"]}";
        assertEquals("FAILED", AuditParameterSnapshot.parse(stored).get("status"));
    }

    private int nodes(Object value) {
        if (value instanceof Map<?, ?> map) return 1 + map.values().stream().mapToInt(this::nodes).sum();
        if (value instanceof List<?> list) return 1 + list.stream().mapToInt(this::nodes).sum();
        return 1;
    }

    private static final class AliasedDto {
        @com.fasterxml.jackson.annotation.JsonProperty("value") private final String realName = "个人姓名甲";
        private final String businessName = "正常业务名称";
    }

    private record Item(boolean enabled, int count) { }
    private static final class Dto {
        private final String name = "正常DTO";
        private final String password = "raw-dto-pass";
        @com.fasterxml.jackson.annotation.JsonIgnore private final String hidden = "raw-hidden";
        private final Item item = new Item(false, 0);
        public String getName() { throw new AssertionError("DTO getters must not execute"); }
        @Override public String toString() { throw new AssertionError("DTO toString must not execute"); }
    }
}
