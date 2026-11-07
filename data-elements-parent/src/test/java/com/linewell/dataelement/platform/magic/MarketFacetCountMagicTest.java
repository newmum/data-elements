package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.elasticsearch.EsCommonService;
import com.linewell.dataelement.elasticsearch.EsProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.http.HttpVersion;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.ssssssss.magicapi.elasticsearch.ElasticSearchModule;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class MarketFacetCountMagicTest {
    private static final String RESOURCE_ID = "7ddfe8a23a524cc0ad8f801cb57b7381";
    private static final String LEGACY_TENANT = "legacy-validation";
    private static final String TENANT = "tenant-validation";
    private static final Map<String, String> USER = Map.of("id", "validation-user");
    private static final Map<String, Object> ORG_FACET = Map.of("orgPath",
            Map.of("terms", Map.of("field", "orgPath", "size", 10_000)));
    private static final List<Map<String, Object>> PUBLISHED_CATALOG = List.of(
            Map.of("term", Map.of("publishStatus", 1)), Map.of("term", Map.of("assetType", "catalog")));
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void modernAndLegacyRequestsUseOneServerOwnedSearchOnTheCorrectTenantIndex() throws Exception {
        var fixture = new Fixture();
        var counts = Map.of("orgPath", Map.of("org-a", 3, "org-b", 7));
        assertEquals(counts, fixture.run(Map.of()));
        assertEquals(counts, fixture.run(legacy(PUBLISHED_CATALOG, ORG_FACET), LEGACY_TENANT, USER));
        assertEquals(2, fixture.meCalls);
        assertEquals(2, fixture.requests.size());
        assertEquals("/elements_dataassets__tenant_tenant-validation/_search", fixture.requests.get(0).getEndpoint());
        assertEquals("/elements_dataassets/_search", fixture.requests.get(1).getEndpoint());
        for (int i = 0; i < fixture.requests.size(); i++) {
            var request = fixture.requests.get(i);
            String tenant = i == 0 ? TENANT : LEGACY_TENANT;
            assertEquals("POST", request.getMethod());
            assertEquals(JSON.readTree("""
                    {"size":0,"track_total_hits":false,"query":{"bool":{"filter":[
                        {"term":{"assetType":"catalog"}},{"term":{"publishStatus":1}},
                        {"bool":{"should":[{"term":{"tenantId":"%s"}},
                            {"bool":{"must_not":[{"exists":{"field":"tenantId"}}]}}],"minimum_should_match":1}}
                    ]}},"aggs":{"orgPath":{"terms":{"field":"orgPath","size":10000}}}}
                    """.formatted(tenant)), JSON.readTree(EntityUtils.toString(request.getEntity())));
        }
        verifyNoInteractions(fixture.indexOperations);
    }

    @Test
    void rejectsUnknownPropertiesIndexOverridesAndNestedDslBeforeSearch() throws Exception {
        var fixture = new Fixture();
        var bodies = List.of(
                List.of(Map.of()),
                Map.of("indexType", "elements_dataassets__tenant_other"),
                Map.of("query", PUBLISHED_CATALOG, "aggs", ORG_FACET, "unknown", true),
                legacy(PUBLISHED_CATALOG, Map.of("orgPath",
                        Map.of("terms", Map.of("field", "orgPath", "size", 50_000)))),
                legacy(PUBLISHED_CATALOG, Map.of("orgPath",
                        Map.of("terms", Map.of("field", "orgPath", "size", 10_000, "script", "validation")))),
                legacy(PUBLISHED_CATALOG, Map.of("orgPath",
                        Map.of("terms", Map.of("field", "orgPath", "size", 10_000), "aggs", Map.of()))),
                legacy(PUBLISHED_CATALOG, Map.of("policeType",
                        Map.of("terms", Map.of("field", "policeType", "size", 10_000)))),
                legacy(List.of(Map.of("term", Map.of("publishStatus", 0)), PUBLISHED_CATALOG.getLast()), ORG_FACET),
                legacy(List.of(Map.of("term", Map.of("publishStatus", "1")), PUBLISHED_CATALOG.getLast()), ORG_FACET),
                legacy(List.of(Map.of("match_all", Map.of()), PUBLISHED_CATALOG.getLast()), ORG_FACET),
                legacy(List.of(Map.of("term", Map.of("publishStatus", 1, "script", "validation")),
                        PUBLISHED_CATALOG.getLast()), ORG_FACET),
                legacy(List.of(PUBLISHED_CATALOG.getFirst(), PUBLISHED_CATALOG.getFirst()), ORG_FACET));
        for (Object body : bodies) {
            assertExit(400, fixture.run(body));
        }
        assertEquals(bodies.size(), fixture.meCalls);
        assertTrue(fixture.requests.isEmpty());
        verifyNoInteractions(fixture.indexOperations);
    }

    @Test
    void authenticatesBeforeValidationAndSearch() throws Exception {
        var fixture = new Fixture();
        assertExit(401, fixture.run(Map.of("indexType", "invalid"), "", USER));
        assertEquals(0, fixture.meCalls);
        assertExit(401, fixture.run(Map.of("indexType", "invalid"), TENANT, Map.of()));
        assertEquals(1, fixture.meCalls);
        assertTrue(fixture.requests.isEmpty());
        verifyNoInteractions(fixture.indexOperations);
    }

    @Test
    void missingIndexAndEmptyBucketsAreEmptyButSearchFailuresPropagate() throws Exception {
        var fixture = new Fixture();
        fixture.error = new IOException("index_not_found_exception");
        assertEquals(Map.of("orgPath", Map.of()), fixture.run(Map.of()));
        fixture.error = null;
        fixture.reply = "{\"aggregations\":{\"orgPath\":{\"buckets\":[]}}}";
        assertEquals(Map.of("orgPath", Map.of()), fixture.run(Map.of()));
        fixture.error = new IOException("connection reset");
        assertTrue(assertThrows(RuntimeException.class, () -> fixture.run(Map.of()))
                .getMessage().contains("connection reset"));
        assertEquals(3, fixture.requests.size());
        verifyNoInteractions(fixture.indexOperations);
    }

    @Test
    void doesNotPresentPartialOrMalformedSearchResultsAsZeroCounts() throws Exception {
        var fixture = new Fixture();
        for (String reply : List.of(
                "{\"timed_out\":true,\"aggregations\":{\"orgPath\":{\"buckets\":[]}}}",
                "{\"_shards\":{\"failed\":1},\"aggregations\":{\"orgPath\":{\"buckets\":[]}}}",
                "{\"aggregations\":{\"orgPath\":{}}}")) {
            fixture.reply = reply;
            assertExit(502, fixture.run(Map.of()));
        }
        assertEquals(3, fixture.requests.size());
    }

    private static Map<String, Object> legacy(List<?> query, Map<String, ?> aggs) {
        return Map.of("query", query, "aggs", aggs);
    }

    private static void assertExit(int code, Object result) {
        var exit = assertInstanceOf(ExitValue.class, result);
        assertEquals(code, ((Number) exit.getValues()[0]).intValue());
    }

    public record Tenant(String id) {}

    private static final class Fixture {
        final ElasticsearchOperations indexOperations = mock(ElasticsearchOperations.class);
        final List<Request> requests = new ArrayList<>();
        private final EsCommonService indexResolver;
        private final ElasticSearchModule elasticsearch;
        private final MagicScript script;
        String reply = """
                {"timed_out":false,"_shards":{"failed":0},"hits":{"hits":[]},"aggregations":{"orgPath":{
                    "buckets":[{"key":"org-a","doc_count":3},{"key":"org-b","doc_count":7}]
                }}}
                """;
        IOException error;
        int meCalls;

        Fixture() throws Exception {
            var config = new EsProperties();
            config.setPrefix("elements_");
            config.getTenant().setLegacyTenantId(LEGACY_TENANT);
            indexResolver = new EsCommonService(indexOperations, config);
            var client = mock(RestClient.class);
            when(client.performRequest(any(Request.class))).thenAnswer(invocation -> {
                requests.add(invocation.getArgument(0));
                if (error != null) {
                    throw error;
                }
                var response = mock(Response.class);
                when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));
                when(response.getEntity()).thenReturn(new StringEntity(reply, ContentType.APPLICATION_JSON));
                return response;
            });
            elasticsearch = new ElasticSearchModule(client);
            script = MagicScript.create("var me = () => authUser(body)\n" + CanonicalMagicSources.byId(RESOURCE_ID)
                    .replaceAll("(?m)^import [^\\r\\n]*\\R", ""), null);
        }

        Object run(Object body) {
            return run(body, TENANT, USER);
        }

        Object run(Object body, String tenant, Map<String, String> user) {
            try (var ignored = TenantContext.use(tenant.isEmpty() ? TENANT : tenant)) {
                return script.execute(new MagicScriptContext(Map.of(
                        "body", body, "tenantRuntime", new Tenant(tenant), "elasticsearch", elasticsearch,
                        "esCommonService", indexResolver,
                        "configData", (Function<Object[], Object>) arguments -> Map.of("es_dataassets", "dataassets"),
                        "authUser", (Function<Object[], Object>) arguments -> {
                            meCalls++;
                            assertEquals(Map.of(), arguments[0]);
                            return user;
                        })));
            }
        }
    }
}
