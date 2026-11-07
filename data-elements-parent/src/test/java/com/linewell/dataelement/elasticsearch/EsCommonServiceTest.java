package com.linewell.dataelement.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.SocketException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.NoSuchIndexException;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.ByQueryResponse;
import org.springframework.data.elasticsearch.core.query.BulkOptions;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.data.elasticsearch.core.query.UpdateResponse;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;

class EsCommonServiceTest {

    private ElasticsearchOperations operations;
    private IndexOperations indexOperations;
    private EsCommonService service;

    @BeforeEach
    void setUp() {
        operations = mock(ElasticsearchOperations.class);
        indexOperations = mock(IndexOperations.class);
        when(operations.indexOps(any(IndexCoordinates.class))).thenReturn(indexOperations);

        EsProperties properties = new EsProperties();
        properties.setPrefix("elements_");
        service = new EsCommonService(operations, properties);
    }

    @Test
    void createsNewDataassetsIndexWithKeywordStringTemplate() {
        when(indexOperations.exists()).thenReturn(false);
        when(indexOperations.create(anyMap(), any(Document.class))).thenReturn(true);

        assertTrue(service.ensureIndexReadyForWrite("dataassets"));

        verify(indexOperations).create(anyMap(), any(Document.class));
    }

    @Test
    void keepsLegacyTextFieldsCompatible() {
        when(indexOperations.exists()).thenReturn(true);
        when(indexOperations.getMapping()).thenReturn(Map.of(
                "properties", Map.of(
                        "updatedTime", Map.of("type", "keyword"),
                        "connectionStatus", Map.of(
                                "type", "text",
                                "fields", Map.of("keyword", Map.of("type", "keyword"))))));
        assertDoesNotThrow(() -> service.ensureIndexReadyForWrite("dataassets"));

        verify(indexOperations, never()).putMapping(any(Document.class));
    }

    @Test
    void keepsDefaultTenantOnLegacyIndexAndSeparatesOtherTenants() {
        assertTrue(service.resolveTenantIndexName("dataassets").equals("elements_dataassets"));

        try (TenantContext.Scope ignored =
                     TenantContext.use("10000000000000000000000000000002")) {
            assertTrue(service.resolveTenantIndexName("dataassets").equals(
                    "elements_dataassets__tenant_10000000000000000000000000000002"
            ));
        }
    }

    @Test
    void preparesAQueriedTenantIndexOnlyOnce() {
        when(indexOperations.exists()).thenReturn(false);
        when(indexOperations.create()).thenReturn(true);

        try (TenantContext.Scope ignored =
                     TenantContext.use("10000000000000000000000000000002")) {
            assertTrue(service.ensureTenantIndexReady("other").endsWith(
                    "__tenant_10000000000000000000000000000002"
            ));
            service.ensureTenantIndexReady("other");
        }

        verify(indexOperations, times(1)).exists();
        verify(indexOperations, times(1)).create();
    }

    @Test
    void changingDefaultTenantDoesNotTransferHistoricalIndexOwnership() {
        EsProperties properties = new EsProperties();
        properties.setPrefix("elements_");
        properties.getTenant().setLegacyTenantId("2084109831682699264");
        service = new EsCommonService(operations, properties);
        TenantProperties tenants = new TenantProperties();
        tenants.setDefaultTenantId("2084109831682699267");
        service.setTenantProperties(tenants);

        // With no context the new default still resolves to its own index.
        assertEquals("elements_dataassets__tenant_2084109831682699267",
                service.resolveTenantIndexName("dataassets"));
        try (TenantContext.Scope ignored = TenantContext.use("2084109831682699264")) {
            assertEquals("elements_dataassets", service.resolveTenantIndexName("dataassets"));
        }
        try (TenantContext.Scope ignored = TenantContext.use("2084109831682699267")) {
            assertEquals("elements_dataassets__tenant_2084109831682699267",
                    service.resolveTenantIndexName("dataassets"));
        }
        try (TenantContext.Scope ignored = TenantContext.use("another-tenant")) {
            assertEquals("elements_dataassets__tenant_another-tenant",
                    service.resolveTenantIndexName("dataassets"));
        }
    }

    @Test
    void explicitLegacyOwnerStillHonorsDisabledLegacyMode() {
        EsProperties properties = new EsProperties();
        properties.setPrefix("elements_");
        properties.getTenant().setLegacyTenantId("2084109831682699264");
        properties.getTenant().setDefaultTenantUsesLegacyIndex(false);
        service = new EsCommonService(operations, properties);
        try (TenantContext.Scope ignored = TenantContext.use("2084109831682699264")) {
            assertEquals("elements_dataassets__tenant_2084109831682699264",
                    service.resolveTenantIndexName("dataassets"));
        }
    }

    @Test
    void keepsExistingLongMappingWhenBulkJsonContainsAnIntegralValue() {
        when(indexOperations.exists()).thenReturn(true);
        when(indexOperations.getMapping()).thenReturn(Map.of(
                "properties", Map.of("recordCount", Map.of("type", "long"))));
        when(operations.bulkIndex(anyList(), any(IndexCoordinates.class))).thenReturn(List.of());

        assertDoesNotThrow(
                () -> service.bulkSaveJsonAutoMapping(
                        "dataassets",
                        List.of("table-1"),
                        List.of(Map.of("recordCount", 1))));

        verify(indexOperations, never()).putMapping(any(Document.class));
        verify(operations).bulkIndex(anyList(), any(IndexCoordinates.class));
    }

    @Test
    void retriesIndexPreparationAfterTransientConnectionReset() {
        when(indexOperations.exists())
                .thenThrow(new RuntimeException(new SocketException("Connection reset")))
                .thenReturn(false);
        when(indexOperations.create(anyMap(), any(Document.class))).thenReturn(true);

        assertTrue(service.ensureIndexReadyForWrite("dataassets"));

        verify(indexOperations, times(2)).exists();
        verify(indexOperations).create(anyMap(), any(Document.class));
    }

    @ParameterizedTest
    @CsvSource({"20,replace", "100,replace", "20,merge", "100,merge"})
    void projectsTwentyAndHundredCatalogsWithOneBulkRequest(int count, String mode) {
        when(indexOperations.exists()).thenReturn(true);
        when(indexOperations.getMapping()).thenReturn(Map.of("properties", Map.of(
                "createdTime", Map.of("type", "date"), "updatedTime", Map.of("type", "keyword"),
                "businessTime", Map.of("type", "date"))));
        List<String> ids = IntStream.range(0, count).mapToObj(i -> "validation-" + i).toList();
        List<Map<String, Object>> documents = ids.stream()
                .map(id -> Map.<String, Object>of("tid", id, "assetType", "catalog", "viewCount", 99999,
                        "createdTime", "2026-11-07 13:14:15", "updatedTime", "2026-11-07 13:14:15",
                        "businessTime", "2026-11-07 13:14:15")).toList();
        List<Long> counters = IntStream.range(0, count).mapToObj(i -> (long) i).toList();
        AtomicReference<List<UpdateQuery>> submitted = new AtomicReference<>();
        doAnswer(call -> {
            submitted.set(call.getArgument(0));
            assertEquals(org.springframework.data.elasticsearch.core.RefreshPolicy.IMMEDIATE,
                    call.<BulkOptions>getArgument(1).getRefreshPolicy());
            return null;
        }).when(operations).bulkUpdate(anyList(), any(BulkOptions.class), any(IndexCoordinates.class));

        try (TenantContext.Scope ignored = TenantContext.use("validation-tenant-a")) {
            assertEquals(ids, "replace".equals(mode)
                    ? service.bulkReplaceCatalogProjections("dataassets", ids, documents, counters)
                    : service.bulkMergeCatalogProjections("dataassets", ids, documents, counters));
        }
        assertEquals(count, submitted.get().size());
        for (int position = 0; position < count; position++) {
            UpdateQuery query = submitted.get().get(position);
            assertEquals(ids.get(position), query.getId());
            assertEquals(counters.get(position), query.getParams().get("incoming"));
            assertEquals(mode, query.getParams().get("mode"));
            assertEquals(true, query.getScriptedUpsert());
            assertEquals(5, query.getRetryOnConflict());
            assertEquals(Document.create(), query.getUpsert());
            Map<?, ?> projected = (Map<?, ?>) query.getParams().get("document");
            assertFalse(projected.containsKey("viewCount"));
            assertEquals("validation-tenant-a", projected.get("tenantId"));
            assertEquals("2026-11-07T13:14:15", projected.get("createdTime"));
            assertEquals("2026-11-07 13:14:15", projected.get("updatedTime"));
            assertEquals("2026-11-07 13:14:15", projected.get("businessTime"));
        }
        verify(operations).bulkUpdate(anyList(), any(BulkOptions.class), any(IndexCoordinates.class));
        verify(operations, never()).exists(any(String.class), any(IndexCoordinates.class));
        verify(operations, never()).bulkIndex(anyList(), any(IndexCoordinates.class));
        verify(operations, never()).get(any(String.class), any(Class.class), any(IndexCoordinates.class));
        verify(indexOperations).exists();
        verify(indexOperations).getMapping();
        assertEquals(99999, documents.get(0).get("viewCount"));
    }

    @Test
    void boundedBulkMergeRetainsThePartialDocumentContract() {
        when(indexOperations.exists()).thenReturn(true);
        AtomicReference<List<UpdateQuery>> submitted = new AtomicReference<>();
        doAnswer(call -> {
            submitted.set(call.getArgument(0));
            return null;
        }).when(operations).bulkUpdate(anyList(), any(BulkOptions.class), any(IndexCoordinates.class));
        List<String> ids = IntStream.range(0, 251).mapToObj(i -> "validation-" + i).toList();
        List<Map<String, Object>> documents = ids.stream()
                .map(id -> Map.<String, Object>of("assetType", "catalog", "accessTaskId", "validation-task")).toList();
        try (TenantContext.Scope ignored = TenantContext.use("validation-tenant-a")) {
            assertEquals(ids, service.bulkMergeCatalogProjections(
                    "dataassets", ids, documents, java.util.Collections.nCopies(ids.size(), 41L)));
        }
        verify(operations, times(2)).bulkUpdate(anyList(), any(BulkOptions.class), any(IndexCoordinates.class));
        assertEquals(1, submitted.get().size());
        assertEquals("merge", submitted.get().get(0).getParams().get("mode"));
        assertEquals("validation-task", ((Map<?, ?>) submitted.get().get(0).getParams().get("document")).get("accessTaskId"));
    }

    @Test
    void rejectsAllInvalidBulkEntriesBeforePreparingOrWritingTheIndex() {
        try (TenantContext.Scope ignored = TenantContext.use("validation-tenant-a")) {
            assertThrows(IllegalArgumentException.class, () -> service.bulkReplaceCatalogProjections(
                    "dataassets", List.of("validation-a", "validation-b"),
                    List.of(catalog("validation-a"), Map.of("assetType", "db")), List.of(1L, 2L)));
            assertThrows(IllegalArgumentException.class, () -> service.bulkReplaceCatalogProjections(
                    "dataassets", List.of("validation-a", "validation-a"),
                    List.of(catalog("validation-a"), catalog("validation-a")), List.of(1L, 2L)));
            assertThrows(IllegalArgumentException.class, () -> service.bulkReplaceCatalogProjections(
                    "dataassets", List.of("validation-a"), List.of(), List.of(1L)));
            assertThrows(IllegalArgumentException.class, () -> service.bulkReplaceCatalogProjections(
                    "dataassets", java.util.Collections.nCopies(1001, "validation-a"),
                    java.util.Collections.nCopies(1001, catalog("validation-a")),
                    java.util.Collections.nCopies(1001, 1L)));
            assertEquals(List.of(), service.bulkReplaceCatalogProjections("dataassets", List.of(), List.of(), List.of()));
        }
        verify(operations, never()).indexOps(any(IndexCoordinates.class));
        verify(operations, never()).bulkUpdate(anyList(), any(BulkOptions.class), any(IndexCoordinates.class));
    }

    @Test
    void rejectsCounterIdentityAndTenantBoundaryViolationsBeforeAnyEsCall() {
        assertThrows(RuntimeException.class, () -> service.advanceCatalogCounter("dataassets", "validation-a", 1));
        try (TenantContext.Scope ignored = TenantContext.use("validation-tenant-a")) {
            assertThrows(IllegalArgumentException.class, () -> service.advanceCatalogCounter("other", "validation-a", 1));
            assertThrows(IllegalArgumentException.class, () -> service.advanceCatalogCounter("dataassets", "../bad", 1));
            assertThrows(IllegalArgumentException.class, () -> service.mergeCatalogProjection(
                    "dataassets", "validation-a", Map.of("assetType", "catalog", "tid", "validation-b"), 1));
            assertThrows(IllegalArgumentException.class, () -> service.mergeCatalogProjection(
                    "dataassets", "validation-a", Map.of("assetType", "catalog", "tenantId", "validation-tenant-b"), 1));
            assertThrows(IllegalArgumentException.class, () -> service.mergeCatalogProjection(
                    "dataassets", "validation-a", Map.of("assetType", "catalog", "tenant_id", "validation-tenant-b"), 1));
            try (TenantContext.Scope control = TenantContext.control()) {
                assertThrows(IllegalArgumentException.class, () -> service.advanceCatalogCounter("dataassets", "validation-a", 1));
            }
            try (TenantContext.Scope bypassed = TenantContext.ignore()) {
                assertThrows(IllegalArgumentException.class, () -> service.advanceCatalogCounter("dataassets", "validation-a", 1));
            }
        }
        verify(operations, never()).indexOps(any(IndexCoordinates.class));
        verify(operations, never()).updateByQuery(any(UpdateQuery.class), any(IndexCoordinates.class));
    }

    @Test
    void validatesExactSignedLongCountersAndDropsAllCopiedCounterAliases() {
        when(indexOperations.exists()).thenReturn(true);
        when(operations.update(any(UpdateQuery.class), any(IndexCoordinates.class)))
                .thenReturn(UpdateResponse.of(UpdateResponse.Result.UPDATED));
        Map<String, Object> document = new LinkedHashMap<>(catalog("validation-a"));
        document.put("viewCount", 900000L);
        document.put("market_view_count", 900000L);
        document.put("marketViewCount", 900000L);
        document.put("view_count", 900000L);
        try (TenantContext.Scope ignored = TenantContext.use("validation-tenant-a")) {
            for (Object invalid : Arrays.asList(null, -1L, 1.0d, Double.NaN, Double.POSITIVE_INFINITY,
                    new BigDecimal("1.1"), new BigInteger("9223372036854775808"),
                    "9223372036854775808", "1.0", "-1", "1e3", true)) {
                assertThrows(IllegalArgumentException.class,
                        () -> service.mergeCatalogProjection("dataassets", "validation-a", document, invalid));
            }
            assertEquals("validation-a", service.mergeCatalogProjection(
                    "dataassets", "validation-a", document, new BigDecimal("9223372036854775807")));
        }
        ArgumentCaptor<UpdateQuery> captured = ArgumentCaptor.forClass(UpdateQuery.class);
        verify(operations).update(captured.capture(), any(IndexCoordinates.class));
        assertEquals(Long.MAX_VALUE, captured.getValue().getParams().get("incoming"));
        Map<?, ?> submitted = (Map<?, ?>) captured.getValue().getParams().get("document");
        assertFalse(submitted.containsKey("viewCount"));
        assertFalse(submitted.containsKey("view_count"));
        assertFalse(submitted.containsKey("marketViewCount"));
        assertFalse(submitted.containsKey("market_view_count"));
        assertEquals(900000L, document.get("viewCount"));
    }

    @Test
    void jsonMergePreservesExistingDateAndNullValuesWithoutTrustingIdentityOrCopiedCount() {
        when(indexOperations.exists()).thenReturn(true);
        when(operations.update(any(UpdateQuery.class), any(IndexCoordinates.class)))
                .thenReturn(UpdateResponse.of(UpdateResponse.Result.UPDATED));
        String json = "{\"assetType\":\"catalog\",\"tid\":\"validation-a\",\"viewCount\":9999,"
                + "\"market_view_count\":9999,\"createdTime\":\"2026-11-07 13:14:15\",\"optional\":null}";
        try (TenantContext.Scope ignored = TenantContext.use("validation-tenant-a")) {
            assertThrows(IllegalArgumentException.class, () -> service.mergeCatalogProjection(
                    "dataassets", "validation-a", json.replace("validation-a", "validation-b"), 7));
            assertThrows(IllegalArgumentException.class, () -> service.mergeCatalogProjection(
                    "dataassets", "validation-a", "{\"assetType\":\"catalog\",\"tenantId\":\"validation-other\"}", 7));
            assertThrows(IllegalArgumentException.class, () -> service.mergeCatalogProjection(
                    "dataassets", "validation-a", "[]", 7));
            assertThrows(IllegalArgumentException.class, () -> service.mergeCatalogProjection(
                    "dataassets", "validation-a", "null", 7));
            assertThrows(IllegalArgumentException.class, () -> service.mergeCatalogProjection(
                    "dataassets", "validation-a", "{", 7));
            assertEquals("validation-a", service.mergeCatalogProjection("dataassets", "validation-a", json, 7));
        }
        ArgumentCaptor<UpdateQuery> submitted = ArgumentCaptor.forClass(UpdateQuery.class);
        verify(operations).update(submitted.capture(), any(IndexCoordinates.class));
        assertEquals(7L, submitted.getValue().getParams().get("incoming"));
        Map<?, ?> document = (Map<?, ?>) submitted.getValue().getParams().get("document");
        assertFalse(document.containsKey("viewCount"));
        assertFalse(document.containsKey("market_view_count"));
        assertEquals("2026-11-07 13:14:15", document.get("createdTime"));
        assertTrue(document.containsKey("optional"));
        assertEquals(null, document.get("optional"));
    }

    @Test
    void metadataTimeCompatibilityUsesTheActualFieldTypeAndRecognizedFormat() {
        when(indexOperations.exists()).thenReturn(true);
        when(operations.update(any(UpdateQuery.class), any(IndexCoordinates.class)))
                .thenReturn(UpdateResponse.of(UpdateResponse.Result.UPDATED));
        AtomicReference<UpdateQuery> submitted = new AtomicReference<>();
        when(operations.update(any(UpdateQuery.class), any(IndexCoordinates.class))).thenAnswer(call -> {
            submitted.set(call.getArgument(0));
            return UpdateResponse.of(UpdateResponse.Result.UPDATED);
        });
        try (TenantContext.Scope ignored = TenantContext.use("validation-tenant-a")) {
            for (String type : List.of("date", "date_nanos")) {
                when(indexOperations.getMapping()).thenReturn(Map.of("properties", Map.of("regTime_date", Map.of("type", type))));
                service.mergeCatalogProjection("dataassets", "validation-a", Map.of(
                        "assetType", "catalog", "regTime_date", "2026-11-07 13:14:15"), 1);
                assertEquals("2026-11-07T13:14:15", ((Map<?, ?>) submitted.get().getParams().get("document")).get("regTime_date"));
            }
            when(indexOperations.getMapping()).thenReturn(Map.of("properties", Map.of("regTime_date", Map.of(
                    "type", "date", "format", "yyyy-MM-dd HH:mm:ss||strict_date_optional_time"))));
            service.mergeCatalogProjection("dataassets", "validation-a", Map.of(
                    "assetType", "catalog", "regTime_date", "2026-11-07 13:14:15"), 1);
            assertEquals("2026-11-07 13:14:15", ((Map<?, ?>) submitted.get().getParams().get("document")).get("regTime_date"));
        }
        verify(indexOperations, times(3)).getMapping();
    }

    @Test
    void invalidCalendarValuesAndUnsupportedDateFormatsAreNotGuessedOrWritten() {
        when(indexOperations.exists()).thenReturn(true);
        try (TenantContext.Scope ignored = TenantContext.use("validation-tenant-a")) {
            when(indexOperations.getMapping()).thenReturn(Map.of("properties", Map.of("createdTime", Map.of("type", "date"))));
            assertThrows(java.time.format.DateTimeParseException.class, () -> service.mergeCatalogProjection(
                    "dataassets", "validation-a", Map.of("assetType", "catalog", "createdTime", "2026-02-30 13:14:15"), 1));
            for (String format : List.of("epoch_millis", "strict_date_time", "dd/MM/yyyy HH:mm:ss")) {
                when(indexOperations.getMapping()).thenReturn(Map.of("properties", Map.of("createdTime", Map.of("type", "date", "format", format))));
                assertThrows(IllegalArgumentException.class, () -> service.mergeCatalogProjection(
                        "dataassets", "validation-a", Map.of("assetType", "catalog", "createdTime", "2026-11-07 13:14:15"), 1));
            }
        }
        verify(operations, never()).update(any(UpdateQuery.class), any(IndexCoordinates.class));
    }

    @Test
    void existingOnlyCounterUsesOneScopedUpdateByQueryWithoutCreatingAnIndex() {
        when(operations.updateByQuery(any(UpdateQuery.class), any(IndexCoordinates.class)))
                .thenReturn(counterResponse(1, 1, 0, 0));
        try (TenantContext.Scope ignored = TenantContext.use("validation-tenant-a")) {
            assertTrue(service.advanceCatalogCounter("dataassets", "validation-a", 41L));
        }
        ArgumentCaptor<UpdateQuery> captured = ArgumentCaptor.forClass(UpdateQuery.class);
        verify(operations).updateByQuery(captured.capture(), any(IndexCoordinates.class));
        UpdateQuery query = captured.getValue();
        assertEquals(1, query.getMaxDocs());
        assertEquals(null, query.getUpsert());
        assertEquals(null, query.getScriptedUpsert());
        assertEquals("advance", query.getParams().get("mode"));
        var filters = ((NativeQuery) query.getQuery()).getQuery().bool().filter();
        assertEquals(List.of("validation-a"), filters.get(0).ids().values());
        assertEquals("tid", filters.get(1).term().field());
        assertEquals("assetType", filters.get(2).term().field());
        assertEquals("1", filters.get(3).bool().minimumShouldMatch());
        assertEquals("tenantId", filters.get(3).bool().should().get(0).term().field());
        verify(operations, never()).indexOps(any(IndexCoordinates.class));
        verify(operations, never()).update(any(UpdateQuery.class), any(IndexCoordinates.class));
        verify(operations, never()).index(any(IndexQuery.class), any(IndexCoordinates.class));
    }

    @Test
    void existingOnlyReturnsFalseForMissingDocumentAndMissingIndex() {
        when(operations.updateByQuery(any(UpdateQuery.class), any(IndexCoordinates.class)))
                .thenReturn(counterResponse(0, 0, 0, 0))
                .thenThrow(new NoSuchIndexException("validation-missing"));
        try (TenantContext.Scope ignored = TenantContext.use("validation-tenant-a")) {
            assertFalse(service.advanceCatalogCounter("dataassets", "validation-a", 1));
            assertFalse(service.advanceCatalogCounter("dataassets", "validation-a", 1));
        }
        verify(operations, never()).indexOps(any(IndexCoordinates.class));
        verify(operations, never()).index(any(IndexQuery.class), any(IndexCoordinates.class));
    }

    @Test
    void confirmsExistingNoopAndRetriesOnlyExplicitEsVersionConflicts() {
        when(operations.updateByQuery(any(UpdateQuery.class), any(IndexCoordinates.class)))
                .thenReturn(counterResponse(1, 0, 0, 1))
                .thenReturn(counterResponse(1, 0, 1, 0));
        try (TenantContext.Scope ignored = TenantContext.use("validation-tenant-a")) {
            assertTrue(service.advanceCatalogCounter("dataassets", "validation-a", 41));
        }
        verify(operations, times(2)).updateByQuery(any(UpdateQuery.class), any(IndexCoordinates.class));
        verify(operations, never()).update(any(UpdateQuery.class), any(IndexCoordinates.class));
    }

    @Test
    void bestEffortCounterDoesNotRepeatATransportFailureAfterTheDbCommit() {
        when(operations.updateByQuery(any(UpdateQuery.class), any(IndexCoordinates.class)))
                .thenThrow(new RuntimeException(new SocketException("validation connection reset")));
        try (TenantContext.Scope ignored = TenantContext.use("validation-tenant-a")) {
            assertThrows(RuntimeException.class,
                    () -> service.advanceCatalogCounter("dataassets", "validation-a", 41));
        }
        verify(operations).updateByQuery(any(UpdateQuery.class), any(IndexCoordinates.class));
    }

    @Test
    void nativeClientPreservesTheUpdatedFieldOmittedByTheSpringDataConverter() throws Exception {
        ElasticsearchTemplate template = mock(ElasticsearchTemplate.class);
        ElasticsearchClient client = mock(ElasticsearchClient.class);
        var originalOptions = org.elasticsearch.client.RequestOptions.DEFAULT.toBuilder()
                .addHeader("X-Validation-Header", "preserved")
                .setRequestConfig(org.apache.http.client.config.RequestConfig.custom().setSocketTimeout(30000).build())
                .build();
        when(client._transportOptions()).thenReturn(new co.elastic.clients.transport.rest_client.RestClientOptions(originalOptions));
        when(template.execute(any())).thenAnswer(call ->
                call.<ElasticsearchTemplate.ClientCallback<?>>getArgument(0).doWithClient(client));
        when(client.withTransportOptions(any(co.elastic.clients.transport.TransportOptions.class))).thenReturn(client);
        when(client.updateByQuery(any(co.elastic.clients.elasticsearch.core.UpdateByQueryRequest.class)))
                .thenReturn(co.elastic.clients.elasticsearch.core.UpdateByQueryResponse.of(response -> response
                        .total(1L).updated(1L).noops(0L).deleted(0L).versionConflicts(0L).timedOut(false)))
                .thenReturn(co.elastic.clients.elasticsearch.core.UpdateByQueryResponse.of(response -> response
                        .total(1L).noops(0L).deleted(0L).versionConflicts(0L).timedOut(false)));
        EsProperties properties = new EsProperties();
        properties.setPrefix("elements_");
        EsCommonService nativeService = new EsCommonService(template, properties);
        try (TenantContext.Scope ignored = TenantContext.use("validation-tenant-a")) {
            assertTrue(nativeService.advanceCatalogCounter("dataassets", "validation-a", 41));
            assertThrows(IllegalStateException.class,
                    () -> nativeService.advanceCatalogCounter("dataassets", "validation-a", 41));
        }
        verify(template, never()).updateByQuery(any(UpdateQuery.class), any(IndexCoordinates.class));
        verify(template, never()).indexOps(any(IndexCoordinates.class));
        ArgumentCaptor<co.elastic.clients.elasticsearch.core.UpdateByQueryRequest> request = ArgumentCaptor.forClass(
                co.elastic.clients.elasticsearch.core.UpdateByQueryRequest.class);
        verify(client, times(2)).updateByQuery(request.capture());
        assertEquals(List.of("elements_dataassets__tenant_validation-tenant-a"), request.getValue().index());
        assertEquals(1L, request.getValue().maxDocs());
        assertEquals(List.of("validation-a"), request.getValue().query().bool().filter().get(0).ids().values());
        assertEquals(true, request.getValue().waitForCompletion());
        assertEquals(true, request.getValue().refresh());
        ArgumentCaptor<co.elastic.clients.transport.TransportOptions> transport = ArgumentCaptor.forClass(
                co.elastic.clients.transport.TransportOptions.class);
        verify(client, times(2)).withTransportOptions(transport.capture());
        var budget = ((co.elastic.clients.transport.rest_client.RestClientOptions) transport.getValue())
                .restClientRequestOptions().getRequestConfig();
        assertEquals(2000, budget.getSocketTimeout());
        assertEquals(500, budget.getConnectTimeout());
        assertEquals(500, budget.getConnectionRequestTimeout());
        assertTrue(((co.elastic.clients.transport.rest_client.RestClientOptions) transport.getValue())
                .restClientRequestOptions().getHeaders().stream().anyMatch(header ->
                "X-Validation-Header".equals(header.getName()) && "preserved".equals(header.getValue())));
        assertEquals(30000, originalOptions.getRequestConfig().getSocketTimeout());
    }

    @Test
    void existingCounterTimesOutOnceWithinThePerRequestHttpBudget() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var handlers = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "validation-counter-timeout");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(handlers);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            try {
                Thread.sleep(3500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try (RestClientTransport transport = new RestClientTransport(RestClient.builder(
                new HttpHost("127.0.0.1", server.getAddress().getPort(), "http")).build(), new JacksonJsonpMapper())) {
            EsCommonService bounded = new EsCommonService(
                    new ElasticsearchTemplate(new ElasticsearchClient(transport)), new EsProperties());
            try (TenantContext.Scope ignored = TenantContext.use("validation-tenant-a")) {
                long started = System.nanoTime();
                assertThrows(RuntimeException.class,
                        () -> bounded.advanceCatalogCounter("dataassets", "validation-timeout", 41));
                long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                assertTrue(elapsed < 5000, "Best-effort projection exceeded its bounded HTTP wait");
                assertEquals(1, requests.get());
            }
        } finally {
            server.stop(0);
            handlers.shutdownNow();
        }
    }

    @Test
    void rejectsUnconfirmedTimedOutAndRepeatedlyConflictingCounterResults() {
        when(operations.updateByQuery(any(UpdateQuery.class), any(IndexCoordinates.class)))
                .thenReturn(counterResponse(1, 0, 0, 0))
                .thenReturn(ByQueryResponse.builder().withTimedOut(true).withTotal(1).withUpdated(1).build())
                .thenReturn(counterResponse(1, 0, 0, 1));
        try (TenantContext.Scope ignored = TenantContext.use("validation-tenant-a")) {
            assertThrows(IllegalStateException.class, () -> service.advanceCatalogCounter("dataassets", "validation-a", 1));
            assertThrows(IllegalStateException.class, () -> service.advanceCatalogCounter("dataassets", "validation-a", 1));
            assertThrows(IllegalStateException.class, () -> service.advanceCatalogCounter("dataassets", "validation-a", 1));
        }
        verify(operations, times(5)).updateByQuery(any(UpdateQuery.class), any(IndexCoordinates.class));
    }

    @Test
    void mergeRequiresAConfirmedWriteAndBulkFailuresRemainVisibleToTheCaller() {
        when(indexOperations.exists()).thenReturn(true);
        when(operations.update(any(UpdateQuery.class), any(IndexCoordinates.class)))
                .thenReturn(UpdateResponse.of(UpdateResponse.Result.NOT_FOUND));
        doThrow(new IllegalStateException("validation partial bulk failure"))
                .when(operations).bulkUpdate(anyList(), any(BulkOptions.class), any(IndexCoordinates.class));
        try (TenantContext.Scope ignored = TenantContext.use("validation-tenant-a")) {
            assertThrows(IllegalStateException.class, () -> service.mergeCatalogProjection("dataassets", "validation-a", catalog("validation-a"), 1));
            assertThrows(IllegalStateException.class, () -> service.bulkReplaceCatalogProjections(
                    "dataassets", List.of("validation-a"), List.of(catalog("validation-a")), List.of(1)));
        }
        verify(operations).bulkUpdate(anyList(), any(BulkOptions.class), any(IndexCoordinates.class));
    }

    @Test
    void originalMixedIndexWritesKeepDatabaseViewCountSemantics() {
        when(indexOperations.exists()).thenReturn(true);
        AtomicReference<List<IndexQuery>> submitted = new AtomicReference<>();
        when(operations.bulkIndex(anyList(), any(IndexCoordinates.class))).thenAnswer(call -> {
            submitted.set(call.getArgument(0));
            return List.of();
        });
        service.bulkSaveWithIds("dataassets", List.of("validation-db"), List.of(Map.of("assetType", "db", "viewCount", 2)));
        assertEquals(2, ((Map<?, ?>) submitted.get().get(0).getObject()).get("viewCount"));
        verify(operations, never()).bulkUpdate(anyList(), any(BulkOptions.class), any(IndexCoordinates.class));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DATA_ELEMENTS_ES_COUNTER_LIVE_CONFIG", matches = ".+")
    void verifiesRealPainlessAndConcurrentSnapshotsOnlyInANewValidationIndex() throws Exception {
        Map<String, String> config = new ObjectMapper().readValue(
                Path.of(System.getenv("DATA_ELEMENTS_ES_COUNTER_LIVE_CONFIG")).toFile(),
                new TypeReference<Map<String, String>>() {});
        BasicCredentialsProvider credentials = new BasicCredentialsProvider();
        credentials.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(config.get("username"), config.get("password")));
        RestClient rest = RestClient.builder(HttpHost.create(config.get("url")))
                .setHttpClientConfigCallback(builder -> builder.setDefaultCredentialsProvider(credentials)).build();
        ObjectMapper wireMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                .setTimeZone(TimeZone.getTimeZone("GMT+8"));
        wireMapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS);
        try (RestClientTransport transport = new RestClientTransport(rest, new JacksonJsonpMapper(wireMapper))) {
            ElasticsearchClient client = new ElasticsearchClient(transport);
            EsProperties properties = new EsProperties();
            properties.setPrefix("validation_market_counter_b22_" + UUID.randomUUID().toString().replace("-", "") + "_");
            EsCommonService live = new EsCommonService(new ElasticsearchTemplate(client), properties);
            EsProperties dateProperties = new EsProperties();
            dateProperties.setPrefix(properties.getPrefix() + "ct_date_");
            EsCommonService liveDate = new EsCommonService(new ElasticsearchTemplate(client), dateProperties);
            String tenant = "validation-catalog-a";
            try (TenantContext.Scope ignored = TenantContext.use(tenant)) {
                String target = live.resolveTenantIndexName("dataassets");
                String dateTarget = liveDate.resolveTenantIndexName("dataassets");
                assertTrue(target.startsWith("validation_market_counter_b22_"));
                // Refuse to touch an existing index, even an unexpected name collision.
                assertFalse(client.indices().exists(query -> query.index(target)).value());
                assertFalse(client.indices().exists(query -> query.index(dateTarget)).value());
                try {
                    assertFalse(live.advanceCatalogCounter("dataassets", "validation-a", 1));
                    assertFalse(client.indices().exists(query -> query.index(target)).value());

                    // Existing legacy projections map these literals as keyword. A fresh
                    // date-detected ISO field would reject the old space-separated JSON
                    // format even through the pre-existing saveOrUpdate path.
                    assertTrue(live.ensureIndexReadyForWrite("dataassets"));
                    client.indices().putMapping(mapping -> mapping.index(target)
                            .properties("createdTime", field -> field.keyword(keyword -> keyword))
                            .properties("updatedTime", field -> field.keyword(keyword -> keyword)));

                    Map<String, Object> dates = new LinkedHashMap<>(catalog("validation-date-original"));
                    dates.put("createdTime", Timestamp.valueOf("2026-11-07 13:14:15.123456"));
                    dates.put("updatedTime", LocalDateTime.of(2026, 11, 7, 13, 14, 15, 123456000));
                    dates.put("optional", null);
                    live.save("dataassets", "validation-date-original", new LinkedHashMap<>(dates));
                    Map<String, Object> originalDates = liveDocument(client, target, "validation-date-original");
                    dates.put("tid", "validation-date-map");
                    live.mergeCatalogProjection("dataassets", "validation-date-map", dates, 2);
                    Map<String, Object> mappedDates = liveDocument(client, target, "validation-date-map");
                    for (String field : List.of("createdTime", "updatedTime", "optional")) {
                        assertTrue(originalDates.containsKey(field));
                        assertTrue(mappedDates.containsKey(field));
                        assertEquals(originalDates.get(field), mappedDates.get(field));
                    }
                    dates.put("tid", "validation-date-bulk");
                    live.bulkReplaceCatalogProjections("dataassets", List.of("validation-date-bulk"), List.of(dates), List.of(3));
                    Map<String, Object> bulkDates = liveDocument(client, target, "validation-date-bulk");
                    for (String field : List.of("createdTime", "updatedTime", "optional")) {
                        assertTrue(bulkDates.containsKey(field));
                        assertEquals(originalDates.get(field), bulkDates.get(field));
                    }
                    dates.put("tid", "validation-date-json");
                    String legacyJson = new com.linewell.dataelement.platform.magic.module.JsonModule().toJSONStr(dates);
                    Map<String, Object> legacyDates = new ObjectMapper().readValue(legacyJson, new TypeReference<Map<String, Object>>() {});
                    live.mergeCatalogProjection("dataassets", "validation-date-json", legacyJson, 4);
                    Map<String, Object> jsonDates = liveDocument(client, target, "validation-date-json");
                    for (String field : List.of("createdTime", "updatedTime", "optional")) {
                        assertTrue(jsonDates.containsKey(field));
                        assertEquals(legacyDates.get(field), jsonDates.get(field));
                    }
                    assertEquals("2026-11-07 13:14:15", jsonDates.get("updatedTime"));

                    assertTrue(liveDate.ensureIndexReadyForWrite("dataassets"));
                    client.indices().putMapping(mapping -> mapping.index(dateTarget)
                            .properties("createdTime", field -> field.date(date -> date))
                            .properties("updatedTime", field -> field.date(date -> date.format("strict_date_optional_time||epoch_millis")))
                            .properties("regTime", field -> field.date(date -> date))
                            .properties("createdTime_date", field -> field.dateNanos(date -> date))
                            .properties("updatedTime_date", field -> field.keyword(keyword -> keyword))
                            .properties("regTime_date", field -> field.date(date -> date)));
                    Map<String, Object> ctDates = new LinkedHashMap<>(catalog("validation-ct-date"));
                    ctDates.put("createdTime", Timestamp.valueOf("2026-11-07 13:14:15"));
                    ctDates.put("updatedTime", LocalDateTime.of(2026, 11, 7, 13, 14, 15));
                    ctDates.put("regTime", null);
                    ctDates.put("createdTime_date", "2026-11-07 13:14:15");
                    ctDates.put("updatedTime_date", "2026-11-07 13:14:15");
                    ctDates.put("regTime_date", "2026-11-07 13:14:15");
                    ctDates.put("optional", null);
                    liveDate.mergeCatalogProjection("dataassets", "validation-ct-date", ctDates, 41);
                    String ctLegacyJson = new com.linewell.dataelement.platform.magic.module.JsonModule().toJSONStr(ctDates);
                    liveDate.mergeCatalogProjection("dataassets", "validation-ct-date", ctLegacyJson, 0);
                    liveDate.bulkReplaceCatalogProjections("dataassets", List.of("validation-ct-date"), List.of(ctDates), List.of(0));
                    liveDate.bulkMergeCatalogProjections("dataassets", List.of("validation-ct-date"), List.of(Map.of(
                            "assetType", "catalog", "createdTime", "2026-11-07 13:14:15")), List.of(0));
                    Map<String, Object> ctProjected = liveDocument(client, dateTarget, "validation-ct-date");
                    for (String field : List.of("createdTime", "updatedTime", "createdTime_date", "regTime_date")) {
                        assertEquals("2026-11-07T13:14:15", ctProjected.get(field));
                    }
                    assertEquals("2026-11-07 13:14:15", ctProjected.get("updatedTime_date"));
                    assertTrue(ctProjected.containsKey("regTime"));
                    assertEquals(null, ctProjected.get("regTime"));
                    assertTrue(ctProjected.containsKey("optional"));
                    assertEquals(null, ctProjected.get("optional"));
                    assertEquals(41L, liveCounter(client, dateTarget, "validation-ct-date"));

                    Map<String, Object> initial = new LinkedHashMap<>(catalog("validation-a"));
                    initial.put("viewCount", Long.MAX_VALUE);
                    initial.put("publishStatus", 1);
                    initial.put("previousField", "validation-present");
                    assertEquals("validation-a", live.mergeCatalogProjection("dataassets", "validation-a", initial, 41));
                    assertEquals(41L, liveCounter(client, target, "validation-a"));
                    live.mergeCatalogProjection("dataassets", "validation-a", Map.of(
                            "assetType", "catalog", "catalogName", "validation merged"), 4);
                    Map<String, Object> merged = liveDocument(client, target, "validation-a");
                    assertEquals(1, ((Number) merged.get("publishStatus")).intValue());
                    assertEquals("validation-present", merged.get("previousField"));
                    assertEquals(41L, liveCounter(client, target, "validation-a"));
                    live.bulkReplaceCatalogProjections("dataassets", List.of("validation-a"),
                            List.of(catalog("validation-a")), List.of(7));
                    assertFalse(liveDocument(client, target, "validation-a").containsKey("previousField"));
                    assertEquals(41L, liveCounter(client, target, "validation-a"));
                    assertTrue(live.advanceCatalogCounter("dataassets", "validation-a", 80));
                    assertTrue(live.advanceCatalogCounter("dataassets", "validation-a", 79));
                    assertFalse(live.advanceCatalogCounter("dataassets", "validation-missing", 100));
                    assertFalse(client.get(query -> query.index(target).id("validation-missing"), Map.class).found());
                    assertEquals(80L, liveCounter(client, target, "validation-a"));

                    client.index(query -> query.index(target).id("validation-db").refresh(co.elastic.clients.elasticsearch._types.Refresh.True)
                            .document(Map.of("tid", "validation-db", "assetType", "db", "tenantId", tenant, "viewCount", 2)));
                    assertFalse(live.advanceCatalogCounter("dataassets", "validation-db", 99));
                    assertThrows(RuntimeException.class, () -> live.mergeCatalogProjection(
                            "dataassets", "validation-db", catalog("validation-db"), 99));
                    assertEquals(2L, liveCounter(client, target, "validation-db"));
                    client.index(query -> query.index(target).id("validation-foreign").refresh(co.elastic.clients.elasticsearch._types.Refresh.True)
                            .document(Map.of("tid", "validation-foreign", "assetType", "catalog", "tenantId", "validation-other", "viewCount", 3)));
                    assertFalse(live.advanceCatalogCounter("dataassets", "validation-foreign", 99));
                    assertThrows(RuntimeException.class, () -> live.mergeCatalogProjection(
                            "dataassets", "validation-foreign", catalog("validation-foreign"), 99));
                    assertEquals(3L, liveCounter(client, target, "validation-foreign"));
                    client.index(query -> query.index(target).id("validation-legacy").refresh(co.elastic.clients.elasticsearch._types.Refresh.True)
                            .document(Map.of("tid", "validation-legacy", "assetType", "catalog", "viewCount", "15")));
                    live.mergeCatalogProjection("dataassets", "validation-legacy", catalog("validation-legacy"), 10);
                    assertEquals(15L, liveCounter(client, target, "validation-legacy"));
                    assertEquals(tenant, liveDocument(client, target, "validation-legacy").get("tenantId"));
                    client.index(query -> query.index(target).id("validation-integral").refresh(co.elastic.clients.elasticsearch._types.Refresh.True)
                            .document(Map.of("tid", "validation-integral", "assetType", "catalog", "tenantId", tenant, "viewCount", 19.0d)));
                    live.mergeCatalogProjection("dataassets", "validation-integral", catalog("validation-integral"), 0);
                    assertEquals(19L, liveCounter(client, target, "validation-integral"));
                    live.mergeCatalogProjection("dataassets", "validation-max", catalog("validation-max"), Long.MAX_VALUE);
                    assertTrue(live.advanceCatalogCounter("dataassets", "validation-max", Long.MAX_VALUE));
                    assertEquals(Long.MAX_VALUE, liveCounter(client, target, "validation-max"));

                    // Independent DB snapshots arrive out of order while older full/partial
                    // projections race them. The final upper bound must retain every maximum.
                    live.mergeCatalogProjection("dataassets", "validation-race", catalog("validation-race"), 0);
                    List<Callable<Void>> writes = new ArrayList<>();
                    for (int next = 1; next <= 100; next++) {
                        long snapshot = next;
                        writes.add(() -> {
                            try (TenantContext.Scope worker = TenantContext.use(tenant)) {
                                if (snapshot % 2 == 0) {
                                    live.bulkReplaceCatalogProjections("dataassets", List.of("validation-race"),
                                            List.of(catalog("validation-race")), List.of(snapshot));
                                } else {
                                    live.mergeCatalogProjection("dataassets", "validation-race", catalog("validation-race"), snapshot);
                                }
                            }
                            return null;
                        });
                        if (next % 5 == 0) {
                            writes.add(() -> {
                                try (TenantContext.Scope worker = TenantContext.use(tenant)) {
                                    live.bulkMergeCatalogProjections("dataassets", List.of("validation-race"),
                                            List.of(Map.of("assetType", "catalog", "accessTaskId", "validation-task", "viewCount", Long.MAX_VALUE)), List.of(0));
                                }
                                return null;
                            });
                        }
                    }
                    try (var workers = Executors.newFixedThreadPool(4)) {
                        for (var result : workers.invokeAll(writes, 90, TimeUnit.SECONDS)) {
                            result.get();
                        }
                    }
                    assertEquals(100L, liveCounter(client, target, "validation-race"));
                    assertTrue(live.advanceCatalogCounter("dataassets", "validation-race", 101));
                    assertEquals(101L, liveCounter(client, target, "validation-race"));
                } finally {
                    for (String cleanup : List.of(target, dateTarget)) {
                        if (client.indices().exists(query -> query.index(cleanup)).value()) {
                            client.indices().delete(query -> query.index(cleanup));
                        }
                        assertFalse(client.indices().exists(query -> query.index(cleanup)).value());
                    }
                }
            }
        }
    }

    private Map<String, Object> liveDocument(ElasticsearchClient client, String index, String id) throws Exception {
        var response = client.get(query -> query.index(index).id(id), Map.class);
        assertTrue(response.found());
        return com.linewell.dataelement.utils.StructuredValues.objectMap(response.source());
    }

    private long liveCounter(ElasticsearchClient client, String index, String id) throws Exception {
        return ((Number) liveDocument(client, index, id).get("viewCount")).longValue();
    }

    private Map<String, Object> catalog(String id) {
        return Map.of("tid", id, "assetType", "catalog", "catalogName", "validation catalog");
    }

    private ByQueryResponse counterResponse(long total, long updated, long noops, long conflicts) {
        return ByQueryResponse.builder().withTotal(total).withUpdated(updated).withNoops(noops)
                .withVersionConflicts(conflicts).build();
    }
}
