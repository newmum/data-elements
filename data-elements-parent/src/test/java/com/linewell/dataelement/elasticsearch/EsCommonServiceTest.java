package com.linewell.dataelement.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.SocketException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;

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
}
