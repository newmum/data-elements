package com.linewell.dataelement.metautil.explorer.impl;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.metautil.model.dto.*;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** 协议级回归：批量请求数、实际DSL、索引范围和认证；不创建业务表或写ES。 */
class ElasticsearchMetadataExplorerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final List<String> paths = new ArrayList<>();
    private final List<String> auth = new ArrayList<>();
    private HttpServer server;
    private DataSourceConfig config;
    private JsonNode search;
    private int major = 7;
    private int catStatus;
    private int aliasStatus;
    private boolean malformedAliasResponse;
    private boolean conflictingLegacyTypes;
    private final ElasticsearchMetadataExplorer explorer = new ElasticsearchMetadataExplorer();

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath(); paths.add(path);
            auth.add(exchange.getRequestHeaders().getFirst("Authorization"));
            boolean aliasRequest = path.endsWith("/_alias");
            if (aliasRequest && (!"GET".equals(exchange.getRequestMethod())
                    || !"expand_wildcards=open".equals(exchange.getRequestURI().getRawQuery()))) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
                return;
            }
            int status = path.startsWith("/_cat/indices") ? catStatus : aliasRequest ? aliasStatus : 0;
            if (status != 0) {
                String action = aliasRequest ? "indices:admin/aliases/get" : "indices:monitor/stats";
                byte[] bytes = json.writeValueAsBytes(Map.of("error", Map.of("type", "security_exception",
                        "reason", "action [" + action + "] is unauthorized"), "status", status));
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
                return;
            }
            Object response;
            if (path.startsWith("/_cat/indices")) {
                var indices = new ArrayList<Map<String,Object>>();
                indices.add(Map.of("index", ".internal", "docs.count", "1"));
                for (int i=0;i<650;i++) indices.add(Map.of("index",String.format("visible-%03d",i),"docs.count","20","store.size","1024"));
                response = indices;
            } else if (aliasRequest) {
                var indices = new LinkedHashMap<String, Object>();
                for (int i = 649; i >= 0; i--) {
                    indices.put(String.format("visible-%03d", i), Map.of("aliases", Map.of()));
                }
                indices.put(".internal", Map.of("aliases", Map.of()));
                indices.put("foreign-index", Map.of("aliases", Map.of()));
                indices.put("visible-secret", Map.of("aliases", Map.of()));
                response = malformedAliasResponse ? Map.of("error", Map.of("type", "proxy_error")) : indices;
            } else if (path.endsWith("/_mapping")) {
                var mappings = new LinkedHashMap<String,Object>();
                for (String index : path.substring(1,path.length()-9).split(",")) {
                    var fields = Map.of("properties",Map.of(
                            "name",Map.of("type","text","fields",Map.of("keyword",Map.of("type","keyword"))),
                            "time",Map.of("type","date"),"location",Map.of("properties",Map.of("city",Map.of("type","keyword"))),
                            "users",Map.of("type","nested","properties",Map.of("name",Map.of("type","keyword")))));
                    Object mapping = major == 6 ? Map.of("legacy_type", fields) : fields;
                    if (conflictingLegacyTypes) mapping = Map.of("one", fields, "two", Map.of("properties", Map.of("name", Map.of("type", "long"))));
                    mappings.put(index, Map.of("mappings", mapping));
                }
                response=mappings;
            } else if (path.endsWith("/_search")) {
                search=json.readTree(exchange.getRequestBody());
                var hits=new ArrayList<Map<String,Object>>();
                for(int i=0;i<search.path("size").asInt();i++) hits.add(Map.of("_id","doc-"+i,"_source",Map.of("name","中文名称","time","2026-10-01","location",Map.of("city","福州"),"users",List.of(Map.of("name","甲"),Map.of("name","乙")))));
                Object total = major == 6 ? 20001 : Map.of("value",20001,"relation","eq");
                response=Map.of("hits",Map.of("total",total,"hits",hits));
            } else response=Map.of("version",Map.of("number",major + ".0.0"));
            byte[] bytes=json.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        config=new DataSourceConfig();config.setDatabaseType(DatabaseType.ELASTICSEARCH);
        config.setJdbcUrl("http://127.0.0.1:"+server.getAddress().getPort());config.setDatabase("visible-*");
    }
    @AfterEach void stop(){server.stop(0);}

    @ParameterizedTest @ValueSource(ints={6,8})
    void legacyAndModernClustersSupportConnectionCollectionMappingsAndPreview(int version) {
        major = version;
        assertThat(explorer.testConnection(config)).isTrue();
        var tables = explorer.getTables(config);
        assertThat(tables).hasSize(650);
        assertThat(tables.getFirst().getRowCount()).isEqualTo(20L);
        assertThat(tables.getFirst().getDataSizeBytes()).isEqualTo(1024L);
        assertThat(explorer.getColumns(config,"visible-001")).hasSize(6);
        var request = new PageQueryRequest();request.setDataSource(config);request.setTableName("visible-001");
        request.setPageSize(20);request.setColumns(List.of("name", "users.name"));
        var result=explorer.queryPage(request);
        assertThat(result.getTotal()).isEqualTo(20001);
        assertThat(result.getRows().getFirst()).containsEntry("name","中文名称");
        assertThat(search.has("track_total_hits")).isEqualTo(version != 6);
        var sample=new SampleRequest();sample.setDataSource(config);sample.setTableName("visible-001");sample.setSampleSize(10);
        assertThat(explorer.sampleData(sample).getTotalCount()).isEqualTo(20001);
        assertThat(search.has("track_total_hits")).isEqualTo(version != 6);
        for(int size:List.of(20,100)) {
            paths.clear();
            var page=explorer.getTablesPage(config,1,size,null);
            assertThat(page.getRows()).hasSize(size).allSatisfy(row -> assertThat(row.getColumnCount()).isEqualTo(6));
            assertThat(paths).hasSize(2);
        }
        paths.clear();
        assertThatThrownBy(()->explorer.getColumnsByTables(config,List.of("visible-001","foreign-index"))).hasMessageContaining("范围");
        assertThat(paths).isEmpty();
    }

    @ParameterizedTest @ValueSource(ints={6,8})
    void missingCatStatsPrivilegeFallsBackToScopedIndexMetadata(int version) {
        major = version;
        catStatus = 403;
        config.setDatabase("visible-*,-visible-secret");
        config.setUsername("restricted-reader");
        config.setPassword("test-only");
        var tables = explorer.getTables(config);
        assertThat(tables).hasSize(650);
        assertThat(tables.getFirst().getTableName()).isEqualTo("visible-000");
        assertThat(tables).allSatisfy(table -> {
            assertThat(table.getTableName()).matches("visible-\\d{3}");
            assertThat(table.getRowCount()).isNull();
            assertThat(table.getDataSizeBytes()).isNull();
        });
        assertThat(paths).containsExactly("/_cat/indices/visible-*,-visible-secret", "/visible-*,-visible-secret/_alias");
        String expected = "Basic " + Base64.getEncoder()
                .encodeToString("restricted-reader:test-only".getBytes(StandardCharsets.UTF_8));
        assertThat(auth).containsExactly(expected, expected);
    }

    @ParameterizedTest @ValueSource(ints={20,100})
    void fallbackStillReadsOnlyTheCurrentPageOfMappingsInOneBatch(int size) {
        catStatus = 403;
        config.setDatabase("visible-*,-visible-secret");
        var page = explorer.getTablesPage(config, 2, size, null);
        assertThat(page.getTotal()).isEqualTo(650);
        assertThat(page.getRows()).hasSize(size).allSatisfy(row -> assertThat(row.getColumnCount()).isEqualTo(6));
        assertThat(paths).hasSize(3);
        assertThat(paths.getLast()).contains(String.format("visible-%03d",size)).doesNotContain("visible-000");
        assertThat(paths.getLast().split(",")).hasSize(size);
    }

    @Test void forbiddenMetadataRemainsAnErrorRatherThanAnEmptySuccessfulCollection() {
        catStatus = 403;
        aliasStatus = 403;
        assertThatThrownBy(()->explorer.getTables(config)).hasMessageContaining("HTTP 403").hasMessageContaining("view_index_metadata");
        assertThat(paths).containsExactly("/_cat/indices/visible-*", "/visible-*/_alias");
    }

    @Test void authenticationFailureIsNotRetriedAndInvalidMetadataFails() {
        catStatus = 401;
        assertThatThrownBy(() -> explorer.getTables(config)).hasMessageContaining("HTTP 401");
        assertThat(paths).containsExactly("/_cat/indices/visible-*");
        catStatus = 403;
        malformedAliasResponse = true;
        assertThatThrownBy(() -> explorer.getTables(config)).hasMessageContaining("返回格式异常");
    }

    @Test void ambiguousLegacyMappingsAreRejectedInsteadOfSilentlyLosingFields() {
        major=6;conflictingLegacyTypes=true;
        assertThatThrownBy(()->explorer.getColumns(config,"visible-001")).hasMessageContaining("冲突字段");
    }

    @ParameterizedTest @ValueSource(ints={20,100})
    void tablePageReadsOnlyCurrentPageMappingsInOneBatch(int size) {
        var page=explorer.getTablesPage(config,2,size,null);
        assertThat(page.getTotal()).isEqualTo(650);
        assertThat(page.getRows()).hasSize(size).allSatisfy(row->assertThat(row.getColumnCount()).isEqualTo(6));
        assertThat(paths).hasSize(2);
        assertThat(paths.get(1)).contains(String.format("visible-%03d",size)).doesNotContain("visible-000");
        assertThat(paths.get(1).split(",")).hasSize(size);
    }

    @Test void fullCollectionDoesNotStopAfterFiveHundredIndices() {
        assertThat(explorer.getTables(config)).hasSize(650);
        assertThat(paths).hasSize(1);
    }

    @ParameterizedTest @ValueSource(ints={20,100})
    void documentPreviewUsesBoundedNativePagingAndExactTotals(int size) {
        var request=new PageQueryRequest();request.setDataSource(config);request.setTableName("visible-001");
        request.setPageNo(2);request.setPageSize(size);request.setColumns(List.of("_id","name","location.city","location","users.name"));
        var condition=new PageQueryCondition();condition.setField("name");condition.setOp("eq");condition.setValue("中文名称");request.setConditions(List.of(condition));
        var sort=new PageQuerySort();sort.setField("name");sort.setDirection("DESC");request.setSorts(List.of(sort));
        var result=explorer.queryPage(request);
        assertThat(paths).hasSize(2);
        assertThat(result.getTotal()).isEqualTo(20001);assertThat(result.getRows()).hasSize(size);
        assertThat(result.getRows().getFirst()).containsEntry("location.city","福州");
        assertThat(result.getRows().getFirst()).containsEntry("location",Map.of("city","福州")).containsEntry("users.name",List.of("甲","乙"));
        assertThat(search.path("from").asInt()).isEqualTo(size);
        assertThat(search.path("track_total_hits").asBoolean()).isTrue();
        assertThat(search.at("/query/bool/filter/0/term/name.keyword").asText()).isEqualTo("中文名称");
        assertThat(search.at("/sort/0/name.keyword/order").asText()).isEqualTo("desc");
    }

    @Test void outOfScopeBatchFailsBeforeSendingAnyRequest() {
        assertThatThrownBy(()->explorer.getColumnsByTables(config,List.of("visible-001","foreign-index"))).hasMessageContaining("范围");
        assertThat(paths).isEmpty();
        config.setDatabase("visible-*,-visible-secret");
        assertThatThrownBy(()->explorer.getColumns(config,"visible-secret")).hasMessageContaining("范围");
        assertThatThrownBy(()->explorer.getColumns(config,".security")).hasMessageContaining("普通索引");
    }

    @Test void authenticationOptionsUseSavedConnectorValuesWithoutUrlCredentials() {
        config.setConnectorProperties(Map.of("apiKey","encoded-key==","authMethod","API_KEY"));
        assertThat(explorer.testConnection(config)).isTrue();assertThat(auth.getLast()).isEqualTo("ApiKey encoded-key==");
        config.setExtraParams("apiKey=extra%2Bkey%3D%3D");
        assertThat(explorer.testConnection(config)).isTrue();assertThat(auth.getLast()).isEqualTo("ApiKey extra+key==");
        config.setConnectorProperties(Map.of("authMethod","NONE"));
        assertThat(explorer.testConnection(config)).isTrue();assertThat(auth.getLast()).isNull();
        config.setUsername("registered-user");config.setPassword("test-only");
        config.setConnectorProperties(Map.of("authMethod","BASIC"));
        assertThat(explorer.testConnection(config)).isTrue();assertThat(auth.getLast()).startsWith("Basic ");
    }
}
