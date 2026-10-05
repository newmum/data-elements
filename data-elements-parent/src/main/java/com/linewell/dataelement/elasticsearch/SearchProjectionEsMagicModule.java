package com.linewell.dataelement.elasticsearch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/**
 * Narrow Elasticsearch projection bridge for Magic API resources.
 *
 * <p>This component deliberately contains no domain mapping or database
 * orchestration. Magic API owns those concerns; this bridge only performs
 * bounded index, alias, and document operations for the search application.
 * Keeping the ES client here also prevents endpoint scripts from ever seeing
 * connection credentials.</p>
 */
@Component
@MagicModule("searchEs")
public class SearchProjectionEsMagicModule {

    private static final String MANAGED_PREFIX = "jcc-search-";
    private static final Pattern MANAGED_NAME =
            Pattern.compile("jcc-search-[a-z0-9][a-z0-9._-]{0,240}");
    private static final Pattern DOCUMENT_ID = Pattern.compile("[A-Za-z0-9._:-]{1,512}");

    private final RestHighLevelClient client;
    private final ObjectMapper objectMapper;

    public SearchProjectionEsMagicModule(
            @Qualifier("highLevelClient") RestHighLevelClient client,
            ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Comment("创建一个智能搜索投影索引；如果已存在则校验后直接复用")
    public Map<String, Object> createIndex(String indexName, Map<String, Object> definition)
            throws IOException {
        validateManagedName(indexName, "索引名");
        if (exists(indexName)) {
            return result("created", false, "index", indexName);
        }
        Request request = jsonRequest("PUT", "/" + indexName, definition == null ? Map.of() : definition);
        Map<String, Object> response = perform(request);
        response.put("created", true);
        response.put("index", indexName);
        return response;
    }

    @Comment("批量写入智能搜索投影文档；每条文档必须带 id")
    public Map<String, Object> bulkUpsert(String indexName, List<Map<String, Object>> documents)
            throws IOException {
        validateManagedName(indexName, "索引名");
        if (documents == null || documents.isEmpty()) {
            return result("requested", 0, "indexed", 0);
        }

        StringBuilder ndjson = new StringBuilder();
        for (Map<String, Object> document : documents) {
            if (document == null || document.get("id") == null) {
                throw new IllegalArgumentException("ES 投影文档缺少 id");
            }
            String id = String.valueOf(document.get("id"));
            validateDocumentId(id);
            ndjson.append(objectMapper.writeValueAsString(Map.of(
                    "index", Map.of("_index", indexName, "_id", id))))
                    .append('\n');
            ndjson.append(objectMapper.writeValueAsString(document)).append('\n');
        }

        Request request = new Request("POST", "/_bulk");
        request.addParameter("refresh", "false");
        request.setEntity(new StringEntity(
                ndjson.toString(), ContentType.create("application/x-ndjson", StandardCharsets.UTF_8)));
        Map<String, Object> response = perform(request);
        if (Boolean.TRUE.equals(response.get("errors"))) {
            throw new IllegalStateException("ES 批量写入存在失败记录: " + summarizeBulkErrors(response));
        }
        response.put("requested", documents.size());
        response.put("indexed", documents.size());
        return response;
    }

    @Comment("写入或覆盖一条智能搜索投影文档")
    public Map<String, Object> upsert(String indexOrAlias, Map<String, Object> document) throws IOException {
        validateManagedName(indexOrAlias, "索引或别名");
        if (document == null || document.get("id") == null) {
            throw new IllegalArgumentException("ES 投影文档缺少 id");
        }
        String id = String.valueOf(document.get("id"));
        validateDocumentId(id);
        Map<String, Object> response = perform(jsonRequest(
                "PUT", "/" + indexOrAlias + "/_doc/" + id, document));
        response.put("id", id);
        return response;
    }

    @Comment("删除一条智能搜索投影文档；不存在时返回 deleted=false")
    public Map<String, Object> delete(String indexOrAlias, String id) throws IOException {
        validateManagedName(indexOrAlias, "索引或别名");
        validateDocumentId(id);
        try {
            Map<String, Object> response = perform(new Request("DELETE", "/" + indexOrAlias + "/_doc/" + id));
            response.put("id", id);
            return response;
        } catch (ResponseException error) {
            if (error.getResponse().getStatusLine().getStatusCode() == 404) {
                return result("id", id, "result", "not_found", "deleted", false);
            }
            throw error;
        }
    }

    @Comment("刷新智能搜索投影索引，使刚写入的数据可被立即检索")
    public Map<String, Object> refresh(String indexOrAlias) throws IOException {
        validateManagedName(indexOrAlias, "索引或别名");
        return perform(new Request("POST", "/" + indexOrAlias + "/_refresh"));
    }

    @Comment("把一个智能搜索别名原子切换到新的投影索引")
    public Map<String, Object> replaceAlias(String alias, String newIndex) throws IOException {
        validateManagedName(alias, "别名");
        validateManagedName(newIndex, "索引名");
        if (!exists(newIndex)) {
            throw new IllegalArgumentException("目标 ES 索引不存在: " + newIndex);
        }

        List<Map<String, Object>> actions = new ArrayList<>();
        for (String oldIndex : aliasIndices(alias)) {
            actions.add(Map.of("remove", Map.of("index", oldIndex, "alias", alias)));
        }
        actions.add(Map.of("add", Map.of("index", newIndex, "alias", alias)));
        Map<String, Object> response = perform(jsonRequest(
                "POST", "/_aliases", Map.of("actions", actions)));
        response.put("alias", alias);
        response.put("index", newIndex);
        return response;
    }

    @Comment("查询智能搜索投影索引，查询体使用 Elasticsearch search JSON")
    public Map<String, Object> search(String indexOrAlias, Map<String, Object> query) throws IOException {
        validateManagedName(indexOrAlias, "索引或别名");
        return perform(jsonRequest(
                "POST", "/" + indexOrAlias + "/_search", query == null ? Map.of() : query));
    }

    @Comment("统计智能搜索投影索引中的文档数")
    public Map<String, Object> count(String indexOrAlias) throws IOException {
        validateManagedName(indexOrAlias, "索引或别名");
        return perform(new Request("GET", "/" + indexOrAlias + "/_count"));
    }

    @Comment("查看一个智能搜索别名当前指向的物理索引")
    public List<String> aliasIndices(String alias) throws IOException {
        validateManagedName(alias, "别名");
        try {
            Map<String, Object> response = perform(new Request("GET", "/_alias/" + alias));
            return new ArrayList<>(response.keySet());
        } catch (ResponseException error) {
            if (error.getResponse().getStatusLine().getStatusCode() == 404) {
                return List.of();
            }
            throw error;
        }
    }

    @Comment("检查智能搜索投影索引或别名是否存在")
    public boolean exists(String indexOrAlias) throws IOException {
        validateManagedName(indexOrAlias, "索引或别名");
        try {
            perform(new Request("HEAD", "/" + indexOrAlias));
            return true;
        } catch (ResponseException error) {
            if (error.getResponse().getStatusLine().getStatusCode() == 404) {
                return false;
            }
            throw error;
        }
    }

    private Request jsonRequest(String method, String endpoint, Object body) throws IOException {
        Request request = new Request(method, endpoint);
        request.setJsonEntity(objectMapper.writeValueAsString(body));
        return request;
    }

    private Map<String, Object> perform(Request request) throws IOException {
        Response response = client.getLowLevelClient().performRequest(request);
        if (response.getEntity() == null) {
            return new LinkedHashMap<>();
        }
        String payload = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        if (payload == null || payload.isBlank()) {
            return new LinkedHashMap<>();
        }
        return objectMapper.readValue(payload, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    @SuppressWarnings("unchecked")
    private String summarizeBulkErrors(Map<String, Object> response) {
        Object rawItems = response.get("items");
        if (!(rawItems instanceof List<?> items)) {
            return "unknown bulk response";
        }
        List<String> failures = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                continue;
            }
            for (Object rawResult : itemMap.values()) {
                if (rawResult instanceof Map<?, ?> result) {
                    Object status = result.get("status");
                    if (status instanceof Number number && number.intValue() >= 300) {
                        failures.add(String.valueOf(result.get("_id")) + ":" + status);
                    }
                }
            }
        }
        return failures.isEmpty() ? "unknown bulk item failure" : String.join(",", failures);
    }

    private void validateManagedName(String value, String field) {
        if (value == null || !MANAGED_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException(field + "必须是受管的 jcc-search- ES 名称");
        }
    }

    private void validateDocumentId(String id) {
        if (id == null || !DOCUMENT_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("ES 文档 id 含有不支持的字符");
        }
    }

    private Map<String, Object> result(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            result.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return result;
    }
}
