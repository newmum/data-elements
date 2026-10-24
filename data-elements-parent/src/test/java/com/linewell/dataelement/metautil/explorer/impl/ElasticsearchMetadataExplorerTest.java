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
    private final ElasticsearchMetadataExplorer explorer = new ElasticsearchMetadataExplorer();

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath(); paths.add(path);
            auth.add(exchange.getRequestHeaders().getFirst("Authorization"));
            Object response;
            if (path.startsWith("/_cat/indices")) {
                var indices = new ArrayList<Map<String,Object>>();
                indices.add(Map.of("index", ".internal", "docs.count", "1"));
                for (int i=0;i<650;i++) indices.add(Map.of("index",String.format("visible-%03d",i),"docs.count","20","store.size","1024"));
                response = indices;
            } else if (path.endsWith("/_mapping")) {
                var mappings = new LinkedHashMap<String,Object>();
                for (String index : path.substring(1,path.length()-9).split(",")) {
                    mappings.put(index,Map.of("mappings",Map.of("properties",Map.of(
                            "name",Map.of("type","text","fields",Map.of("keyword",Map.of("type","keyword"))),
                            "time",Map.of("type","date"),"location",Map.of("properties",Map.of("city",Map.of("type","keyword"))),
                            "users",Map.of("type","nested","properties",Map.of("name",Map.of("type","keyword")))))));
                }
                response=mappings;
            } else if (path.endsWith("/_search")) {
                search=json.readTree(exchange.getRequestBody());
                var hits=new ArrayList<Map<String,Object>>();
                for(int i=0;i<search.path("size").asInt();i++) hits.add(Map.of("_id","doc-"+i,"_source",Map.of("name","中文名称","time","2026-10-01","location",Map.of("city","福州"),"users",List.of(Map.of("name","甲"),Map.of("name","乙")))));
                response=Map.of("hits",Map.of("total",Map.of("value",20001,"relation","eq"),"hits",hits));
            } else response=Map.of("version",Map.of("number","7.16.2"));
            byte[] bytes=json.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        config=new DataSourceConfig();config.setDatabaseType(DatabaseType.ELASTICSEARCH);
        config.setJdbcUrl("http://127.0.0.1:"+server.getAddress().getPort());config.setDatabase("visible-*");
    }
    @AfterEach void stop(){server.stop(0);}

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
