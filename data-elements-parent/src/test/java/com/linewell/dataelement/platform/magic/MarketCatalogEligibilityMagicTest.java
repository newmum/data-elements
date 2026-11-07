package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.elasticsearch.EsCommonService;
import com.linewell.dataelement.elasticsearch.EsProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.ssssssss.magicapi.elasticsearch.ElasticSearchModule;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

/** Executes the canonical shared eligibility function through the deployed Elasticsearch plugin. */
class MarketCatalogEligibilityMagicTest {
    private static final String PATH = "function/01.通用函数/27.超市目录发布资格.ms";
    private static final String ID = "09ecb205502244aba26408368d56aad3";
    private static final String TENANT = "validation-tenant";
    private static final String FOREIGN = "validation-other-tenant";
    private static final String SEARCH_PATH = "/elements_dataassets__tenant_" + TENANT + "/_search";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void canonicalFunctionCompilesWithRuntimeImports() throws Exception {
        MagicScript.create(CanonicalMagicSources.byPath(PATH, ID), null).compile();
    }

    @Test
    void emptyOrInvalidInputsNeverReachElasticsearch() throws Exception {
        var fixture = new Fixture();
        assertThat(fixture.eligible(List.of())).isEmpty();
        for (var invalid : List.of("invalid", List.of("raw-id"), List.of(Map.of("tid", "with,comma")),
                List.of(Map.of("tid", "internal space")), List.of(Map.of("tid", "x".repeat(33))),
                Collections.nCopies(101, Map.of("tid", "validation-id")))) {
            assertCode(fixture.run(invalid), 400);
        }
        assertCode(fixture.run(null), 400);
        fixture.tenant = "";
        assertCode(fixture.run(List.of(Map.of("tid", "validation-id"))), 401);
        assertThat(fixture.requests).isEmpty();
        verifyNoInteractions(fixture.operations);
    }

    @Test
    void twentyAndHundredMakeOneExactTenantScopedSearch() throws Exception {
        for (int count : List.of(20, 100)) {
            var fixture = new Fixture();
            var ids = IntStream.range(0, count).mapToObj(index -> "validation-catalog-%03d".formatted(index)).toList();
            var candidates = ids.stream().map(id -> Map.of("tid", id, "targetTableId", "validation-table",
                    "tenantId", FOREIGN, "indexType", "untrusted-index")).toList();
            var hits = ids.reversed().stream().map(id -> hit(id, Map.of())).toList();
            fixture.respond(hits);

            assertThat(fixture.eligible(candidates)).containsExactlyElementsOf(ids);

            assertThat(fixture.requests).singleElement().satisfies(request -> {
                assertThat(request.getMethod()).isEqualTo("POST");
                assertThat(request.getEndpoint()).isEqualTo(SEARCH_PATH);
                assertThat(request.getParameters()).isEmpty();
            });
            JsonNode body = fixture.requestBody();
            assertThat(body.get("size").asInt()).isEqualTo(count);
            assertThat(body.get("track_total_hits").asBoolean()).isTrue();
            assertThat(body.get("_source")).isEqualTo(JSON.valueToTree(List.of("tid", "assetType", "publishStatus",
                    "tenantId", "shareType", "targetTableId", "apiId", "fileId", "dirId")));
            assertThat(body.at("/query/bool/filter")).isEqualTo(JSON.valueToTree(List.of(
                    Map.of("ids", Map.of("values", ids)), Map.of("term", Map.of("assetType", "catalog")),
                    Map.of("term", Map.of("publishStatus", 1)), Map.of("bool", Map.of("should", List.of(
                            Map.of("term", Map.of("tenantId", TENANT)),
                            Map.of("bool", Map.of("must_not", List.of(Map.of("exists", Map.of("field", "tenantId")))))),
                            "minimum_should_match", 1)))));
            verifyNoInteractions(fixture.operations);
            System.out.printf("Market eligibility %d: 1 exact ES search, 0 SQL, 0 writes%n", count);
        }
    }

    @Test
    void preservesWideHistoricalIdsAndOnlyReturnsQualifiedCatalogsInRequestOrder() throws Exception {
        var fixture = new Fixture();
        var candidates = List.of(Map.of("tid", "cat%x"), Map.of("tid", "cat\\x", "targetTableId", "legacy-table"),
                Map.of("tid", "cat_x"), Map.of("tid", "not-published"), Map.of("tid", "no-resources"),
                Map.of("tid", "other-tenant"), Map.of("tid", "identity-conflict"), Map.of("tid", "legacy"),
                Map.of("tid", "no-doc"), Map.of("tid", "cat\\x"), Map.of("tid", "zero-link", "targetTableId", 0));
        var legacy = new LinkedHashMap<String, Object>();
        legacy.put("tenantId", null);
        legacy.put("publishStatus", "1");
        legacy.put("dirId", "legacy-dir");
        fixture.respond(List.of(hit("legacy", legacy),
                hit("identity-conflict", Map.of("tid", "another-catalog", "apiId", "api")),
                hit("other-tenant", Map.of("tenantId", FOREIGN, "apiId", "api")),
                hit("no-resources", Map.of("apiId", " ")), hit("not-published", Map.of("publishStatus", 0, "apiId", "api")),
                hit("cat_x", Map.of("shareType", " 3 ", "apiId", "api")), hit("cat\\x", Map.of()),
                hit("cat%x", Map.of("fileId", "file")), hit("zero-link", Map.of("apiId", 0))));

        assertThat(fixture.eligible(candidates)).containsExactly("cat%x", "cat\\x", "legacy");
        assertThat(fixture.requestBody().at("/query/bool/filter/0/ids/values")).isEqualTo(JSON.valueToTree(List.of(
                "cat%x", "cat\\x", "cat_x", "not-published", "no-resources", "other-tenant", "identity-conflict", "legacy", "no-doc", "zero-link")));
    }

    @Test
    void missingIndexMeansNoEligibleIdsWhileTransportFailuresSurface() throws Exception {
        var fixture = new Fixture();
        var input = List.of(Map.of("tid", "validation-catalog", "targetTableId", "table"));
        fixture.status = 404;
        fixture.reply = "{\"error\":{\"type\":\"index_not_found_exception\"},\"status\":404}";
        assertThat(fixture.eligible(input)).isEmpty();
        fixture.error = new IOException("validation socket reset");
        assertThatThrownBy(() -> fixture.run(input)).isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("validation socket reset");
    }

    @Test
    void incompleteOrUnexpectedSearchResponseIs502() throws Exception {
        var fixture = new Fixture();
        String first = "validation-catalog-a";
        String second = "validation-catalog-b";
        var input = List.of(Map.of("tid", first), Map.of("tid", second));
        var matched = hit(first, Map.of("apiId", "api"));
        for (var malformed : List.of(
                Map.of("hits", Map.of("hits", List.of())),
                envelope(true, 0, 1, "eq", List.of(matched)),
                envelope(false, 1, 1, "eq", List.of(matched)),
                envelope(false, 0, 1, "gte", List.of(matched)),
                envelope(false, 0, 2, "eq", List.of(matched)),
                envelope(false, 0, 2, "eq", List.of(matched, matched)),
                envelope(false, 0, 1, "eq", List.of(Map.of("_id", first))),
                envelope(false, 0, 1, "eq", List.of(hit("unexpected", Map.of("apiId", "api")))))) {
            fixture.reply = JSON.writeValueAsString(malformed);
            assertCode(fixture.run(input), 502);
        }
        fixture.reply = "{malformed-json";
        assertCode(fixture.run(input), 502);
    }

    @Test
    void onlyExplicitReadRequestCanKeepUnconfirmedEligibility() throws Exception {
        var fixture = new Fixture();
        var candidates = List.of(Map.of("tid", "validation-catalog", "targetTableId", "table"));
        var readRequest = Map.of("candidates", candidates, "allowUnconfirmed", true);
        fixture.reply = JSON.writeValueAsString(envelope(false, 1, 0, "eq", List.of()));

        assertThat(fixture.run(readRequest)).isEqualTo(Collections.singletonMap("eligibleIds", null));
        assertCode(fixture.run(candidates), 502);
        assertCode(fixture.run(Map.of("candidates", candidates, "allowUnconfirmed", false)), 400);
        assertCode(fixture.run(Map.of("candidates", Collections.nCopies(101, candidates.getFirst()),
                "allowUnconfirmed", true)), 400);
    }

    private static Map<String, Object> hit(String id, Map<String, ?> overrides) {
        var source = new LinkedHashMap<String, Object>(Map.of("tid", id, "assetType", "catalog", "publishStatus", 1,
                "tenantId", TENANT));
        source.putAll(overrides);
        return Map.of("_id", id, "_source", source);
    }

    private static Map<String, Object> envelope(boolean timeout, int failed, int total, String relation, List<?> hits) {
        return Map.of("timed_out", timeout, "_shards", Map.of("failed", failed),
                "hits", Map.of("total", Map.of("value", total, "relation", relation), "hits", hits));
    }

    private static void assertCode(Object result, int expected) {
        assertThat(result).isInstanceOfSatisfying(ExitValue.class, exit -> assertThat(exit.getValues()[0]).isEqualTo(expected));
    }

    public record Scope(String id) {}

    private static final class Fixture {
        final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        final List<Request> requests = new ArrayList<>();
        final EsCommonService resolver;
        final ElasticSearchModule elasticsearch;
        final MagicScript script;
        String tenant = TENANT;
        int status = 200;
        IOException error;
        String reply;

        Fixture() throws Exception {
            var config = new EsProperties();
            config.setPrefix("elements_");
            config.getTenant().setLegacyTenantId("validation-legacy-tenant");
            resolver = new EsCommonService(operations, config);
            var client = mock(RestClient.class);
            when(client.performRequest(any(Request.class))).thenAnswer(invocation -> {
                Request request = invocation.getArgument(0);
                requests.add(request);
                if (error != null) throw error;
                var response = mock(Response.class);
                when(response.getHost()).thenReturn(new HttpHost("127.0.0.1"));
                when(response.getRequestLine()).thenReturn(new BasicRequestLine(request.getMethod(), request.getEndpoint(),
                        HttpVersion.HTTP_1_1));
                when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, status, "validation"));
                when(response.getEntity()).thenReturn(new StringEntity(reply, ContentType.APPLICATION_JSON));
                if (status >= 400) throw new ResponseException(response);
                return response;
            });
            elasticsearch = new ElasticSearchModule(client);
            script = MagicScript.create(CanonicalMagicSources.byPath(PATH, ID).replaceAll("(?m)^import [^\\r\\n]+\\R", ""), null);
        }

        void respond(List<?> hits) throws Exception {
            reply = JSON.writeValueAsString(envelope(false, 0, hits.size(), "eq", hits));
        }

        JsonNode requestBody() throws Exception {
            return JSON.readTree(EntityUtils.toString(requests.getLast().getEntity()));
        }

        List<String> eligible(Object rows) {
            Object output = run(rows);
            assertThat(output).isInstanceOf(Map.class);
            Object eligibleIds = ((Map<?, ?>) output).get("eligibleIds");
            assertThat(eligibleIds).isInstanceOf(List.class);
            return ((List<?>) eligibleIds).stream().map(String.class::cast).toList();
        }

        Object run(Object rows) {
            var variables = new LinkedHashMap<String, Object>(Map.of("esCommonService", resolver, "elasticsearch", elasticsearch,
                    "tenantRuntime", new Scope(tenant), "configData", (Function<Object[], Object>) args -> Map.of("es_dataassets", "dataassets")));
            variables.put("rows", rows);
            try (var ignored = TenantContext.use(tenant.isEmpty() ? TENANT : tenant)) {
                return script.execute(new MagicScriptContext(variables));
            }
        }
    }
}
