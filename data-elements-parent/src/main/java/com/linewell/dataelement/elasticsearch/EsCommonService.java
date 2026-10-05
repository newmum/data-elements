package com.linewell.dataelement.elasticsearch;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryStringQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.AggregationsContainer;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.IndexedObjectInformation;
import org.springframework.data.elasticsearch.core.RefreshPolicy;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;

/**
 * 通用 ES 封装：
 * - 索引的增删改查
 * - 通用分页查询
 * - 条件 + 动态聚合查询
 */
@Service
public class EsCommonService {

    private static final Logger log = LoggerFactory.getLogger(EsCommonService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String KEYWORD_STRING_TEMPLATE_NAME = "strings_as_keyword";
    private static final int ES_TRANSIENT_RETRY_ATTEMPTS = 3;
    private static final long ES_TRANSIENT_RETRY_DELAY_MILLIS = 250L;
    private static final int ES_BULK_WRITE_BATCH_SIZE = 250;
    private static final String KEYWORD_STRING_MAPPING_JSON = """
            {
              "dynamic_templates": [
                {
                  "strings_as_keyword": {
                    "match_mapping_type": "string",
                    "mapping": {
                      "type": "keyword",
                      "ignore_above": 2048
                    }
                  }
                }
              ]
            }
            """;
    private final java.util.Set<String> warnedLegacyKeywordIndices =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> preparedTenantIndices =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    {
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        //保留设置属性为空的字段
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    private final ElasticsearchOperations operations;
    private final EsProperties esProperties;
    private TenantProperties tenantProperties = new TenantProperties();

    public EsCommonService(ElasticsearchOperations operations, EsProperties esProperties) {
        this.operations = operations;
        this.esProperties = esProperties;
    }

    @Autowired(required = false)
    public void setTenantProperties(TenantProperties tenantProperties) {
        if (tenantProperties != null) {
            this.tenantProperties = tenantProperties;
        }
    }

    private IndexCoordinates index(String indexName) {
        return IndexCoordinates.of(resolveIndexName(indexName));
    }

    private String resolveIndexName(String indexName) {
        if (indexName == null || indexName.trim().isEmpty()) {
            throw new IllegalArgumentException("indexName 不能为空");
        }
        String prefix = esProperties == null ? null : esProperties.getPrefix();
        String baseName = prefix == null || prefix.trim().isEmpty() || indexName.startsWith(prefix)
                ? indexName
                : prefix + indexName;
        return applyTenantIndex(baseName);
    }

    /**
     * Resolves the physical tenant index for framework and Magic API callers.
     *
     * <p>The default tenant intentionally keeps the historical index name so
     * existing installations do not need a destructive reindex. Other tenants
     * receive independent indices and therefore cannot query each other's
     * documents even when a caller supplies an unrestricted DSL query.</p>
     */
    public String resolveTenantIndexName(String indexName) {
        return resolveIndexName(indexName);
    }

    /**
     * Ensures a tenant index exists before Magic API performs its first read.
     */
    public String ensureTenantIndexReady(String indexName) {
        String resolvedIndexName = resolveIndexName(indexName);
        if (!preparedTenantIndices.add(resolvedIndexName)) {
            return resolvedIndexName;
        }
        try {
            ensureIndexReadyForWrite(indexName);
            return resolvedIndexName;
        } catch (RuntimeException error) {
            preparedTenantIndices.remove(resolvedIndexName);
            throw error;
        }
    }

    private String applyTenantIndex(String baseName) {
        EsProperties.Tenant tenant = esProperties == null ? null : esProperties.getTenant();
        if (tenant == null || !tenant.isEnabled() || TenantContext.isIgnored()) {
            return baseName;
        }
        if (tenant.getGlobalIndices() != null
                && tenant.getGlobalIndices().stream().anyMatch(baseName::equalsIgnoreCase)) {
            return baseName;
        }
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = tenantProperties.getDefaultTenantId();
        }
        if (tenant.isDefaultTenantUsesLegacyIndex()
                && tenantProperties.getDefaultTenantId().equals(tenantId)) {
            return baseName;
        }
        String separator = tenant.getSeparator();
        if (separator == null || separator.isBlank()) {
            separator = "__tenant_";
        }
        return (baseName + separator + tenantId).toLowerCase(java.util.Locale.ROOT);
    }

    /* ================= 索引级操作 ================= */

    public boolean indexExists(String indexName) {
        IndexOperations indexOps = operations.indexOps(index(indexName));
        return indexOps.exists();
    }

    /**
     * 基于实体 class 创建索引及 mapping。
     */
    public boolean createIndex(String indexName, Class<?> entityClass) {
        IndexCoordinates index = index(indexName);
        IndexOperations indexOps = operations.indexOps(index);
        if (indexOps.exists()) {
            return true;
        }
        boolean created = indexOps.create();
        if (created && entityClass != null) {
            Document mapping = indexOps.createMapping(entityClass);
            indexOps.putMapping(mapping);
        }
        return created;
    }

    /**
     * Creates or validates an index before framework-managed writes.
     *
     * <p>Configured projection indices use a dynamic template that maps strings
     * as keyword. Existing text fields cannot be changed in place and are kept
     * for backward compatibility, with a one-time actionable warning.</p>
     */
    public boolean ensureIndexReadyForWrite(String indexName) {
        return executeWithTransientRetry(
                "prepare Elasticsearch index " + resolveIndexName(indexName),
                () -> ensureIndexReadyForWriteOnce(indexName));
    }

    private boolean ensureIndexReadyForWriteOnce(String indexName) {
        if (!usesKeywordStringMapping(indexName)) {
            return createIndex(indexName, null);
        }

        synchronized (this) {
            IndexOperations indexOps = operations.indexOps(index(indexName));
            Document keywordMapping = Document.parse(KEYWORD_STRING_MAPPING_JSON);
            if (!indexOps.exists()) {
                boolean created = indexOps.create(Collections.emptyMap(), keywordMapping);
                if (!created) {
                    throw new IllegalStateException(
                            "Failed to create Elasticsearch index [" + resolveIndexName(indexName) + "]");
                }
                return true;
            }

            Map<String, Object> existingMapping = indexOps.getMapping();
            List<String> textFields = findFieldsByType(existingMapping, "text");
            String resolvedIndexName = resolveIndexName(indexName);
            if (!textFields.isEmpty() && warnedLegacyKeywordIndices.add(resolvedIndexName)) {
                log.warn(
                        "Elasticsearch index [{}] keeps legacy text fields {}. "
                                + "Existing mappings are left unchanged for compatibility; "
                                + "sort and aggregation must use their keyword subfields when available.",
                        resolvedIndexName,
                        textFields);
            }
            if (!hasKeywordStringTemplate(existingMapping)
                    && (existingMapping == null || existingMapping.isEmpty())) {
                indexOps.putMapping(keywordMapping);
            }
            return true;
        }
    }

    private boolean usesKeywordStringMapping(String indexName) {
        EsProperties.Init init = esProperties == null ? null : esProperties.getInit();
        List<String> configuredIndices = init == null ? null : init.getKeywordStringIndices();
        if (configuredIndices == null || configuredIndices.isEmpty()) {
            return false;
        }
        String resolved = resolveIndexName(indexName);
        for (String configuredIndex : configuredIndices) {
            if (configuredIndex == null || configuredIndex.trim().isEmpty()) {
                continue;
            }
            if (resolved.equals(resolveIndexName(configuredIndex.trim()))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasKeywordStringTemplate(Map<String, Object> mapping) {
        if (mapping == null) {
            return false;
        }
        Object templates = mapping.get("dynamic_templates");
        if (!(templates instanceof List<?> templateList)) {
            return false;
        }
        for (Object template : templateList) {
            if (template instanceof Map<?, ?> templateMap
                    && templateMap.containsKey(KEYWORD_STRING_TEMPLATE_NAME)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<String> findFieldsByType(Map<String, Object> mapping, String expectedType) {
        List<String> fields = new ArrayList<>();
        if (mapping == null) {
            return fields;
        }
        Object properties = mapping.get("properties");
        if (properties instanceof Map<?, ?> propertiesMap) {
            collectFieldsByType("", (Map<String, Object>) propertiesMap, expectedType, fields);
        }
        return fields;
    }

    @SuppressWarnings("unchecked")
    private void collectFieldsByType(
            String prefix,
            Map<String, Object> properties,
            String expectedType,
            List<String> fields) {
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> rawFieldMapping)) {
                continue;
            }
            Map<String, Object> fieldMapping = (Map<String, Object>) rawFieldMapping;
            String fieldPath = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (expectedType.equals(fieldMapping.get("type"))) {
                fields.add(fieldPath);
            }
            Object nestedProperties = fieldMapping.get("properties");
            if (nestedProperties instanceof Map<?, ?> nestedMap) {
                collectFieldsByType(
                        fieldPath,
                        (Map<String, Object>) nestedMap,
                        expectedType,
                        fields);
            }
        }
    }

    private void prepareKeywordStringIndex(String indexName) {
        if (usesKeywordStringMapping(indexName)) {
            ensureIndexReadyForWrite(indexName);
        }
    }

    /**
     * Creates an index when needed and applies the supplied Elasticsearch
     * mapping document.
     */
    public boolean createOrUpdateIndexWithJsonMapping(String indexName, String mappingJson) {
        IndexCoordinates index = index(indexName);
        IndexOperations indexOps = operations.indexOps(index);
        if (!indexOps.exists()) {
            indexOps.create();
        }
        Document mapping = Document.parse(mappingJson);
        indexOps.putMapping(mapping);
        return true;
    }

    /**
     * 根据传入的一条 JSON 记录，按照 key 规则动态推断字段类型并创建 / 更新 mapping。
     * <p>
     * 规则：
     * - 默认字段类型：keyword
     * - 字段名以 "_date" 结尾：date
     * - 字段名以 "_num" 结尾：double
     *
     * @param indexName  索引名
     * @param sampleJson 例如：{"name":"tom","age_num":18,"create_date":"2020-01-01"}
     */
    public boolean createOrUpdateIndexMappingFromSampleJson(String indexName, String sampleJson) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(sampleJson);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("sampleJson 必须是一个 JSON 对象");
            }

            Document properties = buildExpectedPropertiesFromJsonObject(root);

            Document mapping = Document.create();
            mapping.put("properties", properties);

            return createOrUpdateIndexWithJsonMapping(indexName, mapping.toJson());
        } catch (Exception e) {
            throw new IllegalArgumentException("解析 sampleJson 失败", e);
        }
    }

    private String resolveTypeByFieldName(String fieldName, JsonNode valueNode) {
        if (fieldName != null && fieldName.endsWith("_date")) {
            return "date";
        }
        if (fieldName != null && fieldName.endsWith("_num")) {
            return "double";
        }
        if (fieldName != null && fieldName.endsWith("_json")) {
            return "nested";
        }
        if (valueNode != null) {
            if (valueNode.isIntegralNumber()) {
                return "long";
            }
            if (valueNode.isFloatingPointNumber()) {
                return "double";
            }
            if (valueNode.isBoolean()) {
                return "boolean";
            }
        }
        return "keyword";
    }

    private Document buildExpectedPropertiesFromJsonObject(JsonNode rootObject) {
        Document expectProps = Document.create();
        Iterator<String> fieldNames = rootObject.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode valueNode = rootObject.get(fieldName);
            String type = resolveTypeByFieldName(fieldName, valueNode);

            Document fieldDoc = Document.create();
            fieldDoc.put("type", type);
            if ("date".equals(type)) {
                fieldDoc.put("format", "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis");
            }
            // _json 结尾的字段，作为 nested，并解析子 JSON 的字段类型
            if ("nested".equals(type) && valueNode != null && valueNode.isObject()) {
                Document nestedProps = Document.create();
                Iterator<String> nestedNames = valueNode.fieldNames();
                while (nestedNames.hasNext()) {
                    String nestedName = nestedNames.next();
                    String nestedType = resolveTypeByFieldName(nestedName, valueNode.get(nestedName));
                    Document nestedField = Document.create();
                    nestedField.put("type", nestedType);
                    if ("date".equals(nestedType)) {
                        nestedField.put("format", "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis");
                    }
                    nestedProps.put(nestedName, nestedField);
                }
                fieldDoc.put("properties", nestedProps);
            }
            expectProps.put(fieldName, fieldDoc);
        }
        return expectProps;
    }

    /**
     * 确保索引存在且 mapping 与期望字段兼容：
     * - 索引不存在：创建索引并写入完整 mapping
     * - 索引存在：仅允许新增字段或类型一致；遇到类型冲突直接抛异常
     */
    private void ensureIndexAndCompatibleMapping(String indexName, Document expectProps) {
        IndexOperations indexOps = operations.indexOps(index(indexName));

        if (!indexOps.exists()) {
            Document mapping = Document.create();
            mapping.put("properties", expectProps);
            indexOps.create();
            indexOps.putMapping(mapping);
            return;
        }

        Map<String, Object> mapping = indexOps.getMapping();
        Document existingProps = null;
        if (mapping != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) mapping.get("properties");
            if (props != null) {
                existingProps = Document.from(props);
            }
        }
        if (existingProps == null) {
            existingProps = Document.create();
        }

        Document toAdd = Document.create();
        for (String fieldName : expectProps.keySet()) {
            Document newField = (Document) expectProps.get(fieldName);
            Object oldObj = existingProps.get(fieldName);
            Document oldField = null;
            if (oldObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> oldMap = (Map<String, Object>) oldObj;
                oldField = Document.from(oldMap);
            } else if (oldObj instanceof Document) {
                oldField = (Document) oldObj;
            }

            if (oldField == null) {
                toAdd.put(fieldName, newField);
                continue;
            }

            String newType = (String) newField.get("type");
            String oldType = (String) oldField.get("type");
            if (!isCompatibleMappingType(oldType, newType)) {
                throw new IllegalArgumentException(
                        "索引 [" + resolveIndexName(indexName) + "] 字段 [" + fieldName +
                                "] 类型不兼容，已存在类型=" + oldType + "，本次期望类型=" + newType);
            }
        }

        if (!toAdd.isEmpty()) {
            Document update = Document.create();
            update.put("properties", toAdd);
            indexOps.putMapping(update);
        }
    }

    /**
     * 单条 JSON 写入：自动建索引/补 mapping/做类型兼容校验，再写入。
     */
    private boolean isCompatibleMappingType(String oldType, String newType) {
        if (oldType == null || newType == null || oldType.equals(newType)) {
            return true;
        }
        // dataassets is a long-lived mixed projection index. Older documents may
        // have normal string fields mapped as text, while JSON auto-mapping infers
        // keyword for the same scalar value. Both can store strings, so do not
        // block idempotent projection updates on this harmless difference.
        return ("text".equals(oldType) && "keyword".equals(newType))
                || ("keyword".equals(oldType) && "text".equals(newType));
    }

    public String saveJsonAutoMapping(String indexName, String id, String json) {
        try {
            prepareKeywordStringIndex(indexName);
            JsonNode root = OBJECT_MAPPER.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("json 必须是一个 JSON 对象");
            }

            Document expectProps = buildExpectedPropertiesFromJsonObject(root);
            ensureIndexAndCompatibleMapping(indexName, expectProps);

            Object source = OBJECT_MAPPER.readValue(json, Object.class);
            IndexQuery query = new IndexQueryBuilder()
                    .withId((id == null || id.trim().isEmpty()) ? null : id)
                    .withObject(source)
                    .build();
            return operations.withRefreshPolicy(RefreshPolicy.IMMEDIATE).index(query, index(indexName));
        } catch (Exception e) {
            throw new IllegalArgumentException("保存 JSON 到 ES 失败", e);
        }
    }

    /**
     * 批量 JSON 写入：使用第一条 JSON 的字段结构和值类型判断（建索引/补 mapping/校验），再批量写入。
     *
     * @param indexName 索引名（会自动应用 es.prefix）
     * @param ids       可选，自定义 id 列表（长度需与 jsonList 一致）；为空则由 ES 自动生成 id
     * @param jsonList  JSON 文本列表（每个元素必须是 JSON 对象）
     * @return 实际写入后的 id 列表
     */
    public List<String> bulkSaveJsonAutoMapping(String indexName, List<String> ids, List<Map<String, Object>> jsonList) {
        if (jsonList == null || jsonList.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        if (ids != null && !ids.isEmpty() && ids.size() != jsonList.size()) {
            throw new IllegalArgumentException("ids 和 jsonList 长度必须一致");
        }
        try {
            prepareKeywordStringIndex(indexName);
            // 仅用第一条作为字段结构判断
            JsonNode first = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(jsonList.get(0)));
            if (first == null || !first.isObject()) {
                throw new IllegalArgumentException("jsonList[0] 必须是一个 JSON 对象");
            }
            Document expectProps = buildExpectedPropertiesFromJsonObject(first);
            ensureIndexAndCompatibleMapping(indexName, expectProps);

            List<Object> sources = new ArrayList<>(jsonList.size());
            for (Map<String, Object> json : jsonList) {
                //JsonNode node = OBJECT_MAPPER.readTree(json);
                //                if (node == null || !node.isObject()) {
                //                    throw new IllegalArgumentException("jsonList 中每条都必须是 JSON 对象");
                //                }
                sources.add(json);
            }

            if (ids == null || ids.isEmpty()) {
                return bulkSave(indexName, sources);
            }
            return bulkSaveWithIds(indexName, ids, sources);
        } catch (Exception e) {
            String causeMessage = rootCauseMessage(e);
            log.error("批量保存 JSON 到 ES 失败: {}", causeMessage, e);
            throw new IllegalArgumentException("批量保存 JSON 到 ES 失败: " + causeMessage, e);
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }

    public boolean deleteIndex(String indexName) {
        IndexOperations indexOps = operations.indexOps(index(indexName));
        if (!indexOps.exists()) {
            return true;
        }
        return indexOps.delete();
    }

    /* ================= 文档级 CRUD ================= */

    public String save(String indexName, String id, Object source) {
        prepareKeywordStringIndex(indexName);
        IndexQuery query = new IndexQueryBuilder()
                .withId(id)
                .withObject(source)
                .build();
        return operations.index(query, index(indexName));
    }

    /**
     * 批量写入（常用于不关心 id 或使用实体上的 @Id 字段时）。
     * 如果实体上有 @Id 并已赋值，则会使用该值作为 ES 文档 id；
     * 否则由 ES 自动生成。
     */
    public List<String> bulkSave(String indexName, List<?> sources) {
        if (sources == null || sources.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        prepareKeywordStringIndex(indexName);
        List<IndexQuery> queries = new ArrayList<>(sources.size());
        for (Object source : sources) {
            IndexQuery query = new IndexQueryBuilder()
                    .withObject(source)
                    .build();
            queries.add(query);
        }
        List<IndexedObjectInformation> result = bulkIndexInBatches(indexName, queries);
        List<String> ids = new ArrayList<>(result.size());
        for (IndexedObjectInformation info : result) {
            ids.add(info.id());
        }
        return ids;
    }

    /**
     * 批量写入（显式传入自定义 id）。
     *
     * @param indexName 索引名
     * @param ids       与 sources 一一对应的自定义 id 列表
     * @param sources   文档对象列表
     * @return 实际写入后的 id 列表（正常情况下与传入 ids 一致）
     */
    public List<String> bulkSaveWithIds(String indexName, List<String> ids, List<?> sources) {
        prepareKeywordStringIndex(indexName);
        if (ids == null || sources == null || ids.isEmpty() || sources.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        if (ids.size() != sources.size()) {
            throw new IllegalArgumentException("ids 和 sources 长度必须一致");
        }
        List<IndexQuery> queries = new ArrayList<>(sources.size());
        for (int i = 0; i < sources.size(); i++) {
            IndexQuery query = new IndexQueryBuilder()
                    .withId(ids.get(i))
                    .withObject(sources.get(i))
                    .build();
            queries.add(query);
        }
        List<IndexedObjectInformation> result = bulkIndexInBatches(indexName, queries);
        List<String> out = new ArrayList<>(result.size());
        for (IndexedObjectInformation info : result) {
            out.add(info.id());
        }
        return out;
    }

    private List<IndexedObjectInformation> bulkIndexInBatches(String indexName, List<IndexQuery> queries) {
        IndexCoordinates index = index(indexName);
        List<IndexedObjectInformation> result = new ArrayList<>(queries.size());
        for (int start = 0; start < queries.size(); start += ES_BULK_WRITE_BATCH_SIZE) {
            int end = Math.min(start + ES_BULK_WRITE_BATCH_SIZE, queries.size());
            List<IndexQuery> batch = new ArrayList<>(queries.subList(start, end));
            int batchNumber = start / ES_BULK_WRITE_BATCH_SIZE + 1;
            result.addAll(executeWithTransientRetry(
                    "bulk write Elasticsearch index " + resolveIndexName(indexName) + " batch " + batchNumber,
                    () -> operations.bulkIndex(batch, index)));
        }
        return result;
    }

    private <T> T executeWithTransientRetry(String operation, Supplier<T> action) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= ES_TRANSIENT_RETRY_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException exception) {
                lastFailure = exception;
                if (!isTransientConnectionFailure(exception) || attempt == ES_TRANSIENT_RETRY_ATTEMPTS) {
                    throw exception;
                }
                log.warn("Elasticsearch {} failed on attempt {}/{} due to a transient connection error; retrying.",
                        operation, attempt, ES_TRANSIENT_RETRY_ATTEMPTS);
                try {
                    Thread.sleep(ES_TRANSIENT_RETRY_DELAY_MILLIS * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw exception;
                }
            }
        }
        throw lastFailure;
    }

    private boolean isTransientConnectionFailure(Throwable failure) {
        for (Throwable current = failure; current != null && current.getCause() != current; current = current.getCause()) {
            if (current instanceof SocketException
                    || current instanceof SocketTimeoutException
                    || current instanceof ConnectException
                    || current instanceof IOException) {
                return true;
            }
        }
        return false;
    }

    public <T> T getById(String indexName, String id, Class<T> clazz) {
        return operations.get(id, clazz, index(indexName));
    }

    /**
     * 获取指定索引下指定 id 的文档，以 Map 形式返回。
     *
     * @param indexName 索引名（会自动应用 es.prefix）
     * @param id        文档 id
     * @return 文档内容，不存在返回 null
     */
    public Map<String, Object> getDocument(String indexName, String id) {
        return operations.get(id, Map.class, index(indexName));
    }

    /**
     * 判断指定索引下指定 id 的文档是否存在。
     *
     * @param indexName 索引名（会自动应用 es.prefix）
     * @param id        文档 id
     * @return true 存在，false 不存在
     */
    public boolean existsById(String indexName, String id) {
        return operations.exists(id, index(indexName));
    }

    public void deleteById(String indexName, String id) {
        operations.delete(id, index(indexName));
    }

    /**
     * 按 id 批量删除文档（逐个删除，适合少量 id）。
     *
     * @param indexName 索引名（会自动应用 es.prefix）
     * @param ids       要删除的文档 id 列表
     * @return 实际删除的数量
     */
    public long deleteByIds(String indexName, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0L;
        }
        long count = 0;
        for (String id : ids) {
            String deletedId = operations.delete(id, index(indexName));
            if (deletedId != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * 指定索引下按 id 批量删除文档（使用 delete-by-query，一次请求，适合大量 id）。
     *
     * @param indexName 索引名（会自动应用 es.prefix）
     * @param ids       要删除的文档 id 列表
     * @return 实际删除的数量
     */
    public long bulkDeleteByIds(String indexName, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0L;
        }
        org.springframework.data.elasticsearch.core.query.Query idsQuery = operations.idsQuery(ids);
        DeleteQuery deleteQuery = DeleteQuery.builder(idsQuery).build();
        var response = operations.delete(deleteQuery, Map.class, index(indexName));
        return response != null ? response.getDeleted() : 0L;
    }

    /**
     * 简单 String Query 查询（适合已经拼好 JSON DSL 的情况）。
     *
     * @param jsonQuery 完整的 ES 查询 DSL，例如：{"query":{"match_all":{}}} 或 {"query":{"bool":{"must":[...]}}}
     */
    public <T> List<T> searchByJsonQuery(String indexName, String jsonQuery, Class<T> clazz) {
        //logSearchJsonQuery(indexName, jsonQuery, null, null);
        StringQuery query = new StringQuery(resolveStringQuerySource(jsonQuery));
        SearchHits<T> hits = operations.search(query, clazz, index(indexName));
        return hits.map(SearchHit::getContent).toList();
    }

    /**
     * 简单 String Query 分页查询（适合已经拼好 JSON DSL 的情况）。
     *
     * @param indexName 索引名（会自动应用 es.prefix）
     * @param jsonQuery 完整的 ES 查询 DSL，例如：{"query":{"match_all":{}}} 或 {"query":{"bool":{"must":[...]}},"sort":[...]}
     *                  注意：必须包含 "query" 字段
     * @param clazz     结果映射类型
     * @param pageNo    页码（从 1 开始）
     * @param pageSize  每页条数
     * @return 自定义分页结果 EsPageInfo
     */
    public <T> EsPageInfo<T> searchByJsonQuery(String indexName,
                                               String jsonQuery,
                                               Class<T> clazz,
                                               int pageNo,
                                               int pageSize) {
        if (pageNo <= 0) {
            pageNo = 1;
        }
        if (pageSize <= 0) {
            pageSize = 10;
        }
        //logSearchJsonQuery(indexName, jsonQuery, pageNo, pageSize);
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize);
        StringQuery query = new StringQuery(resolveStringQuerySource(jsonQuery));
        query.setPageable(pageable);

        SearchHits<T> hits = operations.search(query, clazz, index(indexName));
        if (hits.getSearchHits().isEmpty()) {
            return EsPageInfo.empty(pageNo, pageSize);
        }
        List<T> content = new ArrayList<>(hits.getSearchHits().size());
        for (SearchHit<T> hit : hits.getSearchHits()) {
            content.add(hit.getContent());
        }
        return new EsPageInfo<>(pageNo, pageSize, hits.getTotalHits(), content);
    }

    private void logSearchJsonQuery(String indexName, String jsonQuery, Integer pageNo, Integer pageSize) {
        String resolvedIndexName = resolveIndexName(indexName);
        if (pageNo == null || pageSize == null) {
            log.info("ES searchByJsonQuery index={} query={}", resolvedIndexName, jsonQuery);
            return;
        }
        int from = Math.max(pageNo - 1, 0) * pageSize;
        log.info("ES searchByJsonQuery index={} from={} size={} query={}", resolvedIndexName, from, pageSize, jsonQuery);
    }

    private String resolveStringQuerySource(String jsonQuery) {
        String resolvedDsl = (jsonQuery == null || jsonQuery.trim().isEmpty())
                ? "{\"match_all\":{}}"
                : jsonQuery.trim();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(resolvedDsl);
            if (root.isObject() && root.has("query")) {
                return OBJECT_MAPPER.writeValueAsString(root.get("query"));
            }
            return resolvedDsl;
        } catch (Exception e) {
            throw new IllegalArgumentException("解析 ES 查询 DSL 失败", e);
        }
    }

    /**
     * 基于动态 JSON Query 条件，统计并返回命中的文档 id 列表（使用 ES 文档 _id 字段）。
     *
     * @param indexName 索引名（会自动应用 es.prefix）
     * @param jsonQuery 完整的 ES 查询 DSL，例如：{"query":{"match_all":{}}} 或 {"query":{"bool":{"must":[...]}}}
     * @return 命中结果的 ES 文档 id 列表
     */
    public long countByJsonQuery(String indexName, String jsonQuery) {
        StringQuery query = new StringQuery(jsonQuery);
        SearchHits<Map> hits = operations.search(query, Map.class, index(indexName));
        return hits.getTotalHits();
    }

    /**
     * 保存 JSON 文档：
     * - 如果索引不存在：根据 JSON key 和值类型动态创建索引及 mapping 后再写入；
     * - 如果索引已存在：对比已有 mapping 与本次 JSON 推断出的字段类型，
     * 遇到类型不兼容则抛异常，只允许新增字段或类型一致。
     *
     * @param indexName 索引名（会自动应用 es.prefix）
     * @param id        可选文档 id，null/空则由 ES 自动生成
     * @param json      要保存的 JSON 文本
     * @return 实际写入后的文档 id
     */
    public String saveJsonWithDynamicIndex(String indexName, String id, String json) {
        // 兼容旧方法名：内部转调新实现
        return saveJsonAutoMapping(indexName, id, json);
    }

    public String saveOrUpdate(String indexName, String tid, String json, Boolean update) {
        //if (update) 处理数据库存在，es 不存在更新报错问题
        if (existsById(indexName, tid)) {
            return partialUpdateJson(indexName, tid, json);
        } else {
            return saveJsonAutoMapping(indexName, tid, json);
        }
    }

    /**
     * 部分更新：只更新 json 中出现的字段，其他字段保持不变。
     * 不会删除缺失字段。
     *
     * @param indexName 索引名（会自动应用 es.prefix）
     * @param id        文档 id（必须存在）
     * @param json      仅包含需要更新字段的 JSON 对象
     * @return 被更新文档的 id
     */
    public String partialUpdateJson(String indexName, String id, String json) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("partialUpdateJson 需要明确的文档 id");
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("json 必须是一个 JSON 对象");
            }

            // 可选：根据本次字段做一次 mapping 补充/校验（不会删除旧字段）
            Document expectProps = buildExpectedPropertiesFromJsonObject(root);
            ensureIndexAndCompatibleMapping(indexName, expectProps);

            Map<String, Object> fields = OBJECT_MAPPER.readValue(
                    json, new TypeReference<Map<String, Object>>() {
                    });
            Document doc = Document.from(fields);

            UpdateQuery updateQuery = UpdateQuery.builder(id)
                    .withDocument(doc)
                    .withRefreshPolicy(RefreshPolicy.IMMEDIATE)
                    .build();

            operations.update(updateQuery, index(indexName));
            return id;
        } catch (Exception e) {
            throw new IllegalArgumentException("部分更新 JSON 到 ES 失败", e);
        }
    }

    /**
     * Updates known fields without mapping inspection or an immediate index refresh.
     * Use this for lightweight status changes whose primary state is already stored in the database.
     */
    public String partialUpdateFieldsFast(
            String indexName,
            String id,
            Map<String, Object> fields
    ) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("partialUpdateFieldsFast requires a document id");
        }
        if (fields == null || fields.isEmpty()) {
            return id;
        }
        UpdateQuery updateQuery = UpdateQuery.builder(id)
                .withDocument(Document.from(fields))
                .withRefreshPolicy(RefreshPolicy.NONE)
                .build();
        operations.update(updateQuery, index(indexName));
        return id;
    }

    /**
     * 通用字段精准查询（term 查询，适合 keyword / 数值字段）。
     */
    public <T> List<T> searchByField(String indexName,
                                     String fieldName,
                                     Object value,
                                     Class<T> clazz) {
        if (fieldName == null || fieldName.trim().isEmpty() || value == null) {
            return Collections.emptyList();
        }
        String dsl = "{\"query\":{\"term\":{\"" + fieldName + "\":\"" + escapeJson(String.valueOf(value)) + "\"}}}";
        StringQuery query = new StringQuery(dsl);
        SearchHits<T> hits = operations.search(query, clazz, index(indexName));
        return hits.map(SearchHit::getContent).toList();
    }

    /* ================= 分页查询 ================= */

    public <T> EsPageInfo<T> searchPage(String indexName,
                                        Query boolQuery,
                                        Pageable pageable,
                                        Class<T> clazz) {
        NativeQuery searchQuery = new NativeQueryBuilder()
                .withQuery(boolQuery)
                .withPageable(pageable)
                .build();
        SearchHits<T> hits = operations.search(searchQuery, clazz, index(indexName));
        if (hits.getSearchHits().isEmpty()) {
            return EsPageInfo.empty(pageable.getPageNumber(), pageable.getPageSize());
        }
        List<T> content = new ArrayList<>(hits.getSearchHits().size());
        for (SearchHit<T> hit : hits.getSearchHits()) {
            content.add(hit.getContent());
        }
        EsPageInfo<T> pageInfo = new EsPageInfo<>(pageable.getPageNumber() + 1, pageable.getPageSize(), hits.getTotalHits(),
                content);
        return pageInfo;
        //return new PageImpl<>(content, pageable, hits.getTotalHits());
    }

    /**
     * 分页查询（兼容 ES 7.10 - 8.x）
     *
     * @param queryDsl 查询 DSL，支持两种格式：
     *                 1. 仅查询条件：{"match_all":{}} 或 {"bool":{"must":[...]}}
     *                 2. 完整查询体：{"query":{"match_all":{}},"sort":[...]}
     */
    public <T> EsPageInfo<T> searchPage(String indexName,
                                        String queryDsl,
                                        Pageable pageable,
                                        Class<T> clazz) {
        String resolvedDsl = (queryDsl == null || queryDsl.trim().isEmpty())
                ? "{\"match_all\":{}}"
                : queryDsl.trim();

        // 使用 NativeQuery 替代 StringQuery，避免 Spring Data ES 5.x 的 JSON 解析兼容性问题
        // StringQuery 在 ES Java Client 8.x 中会再次包装 query，导致重复的 query 字段
//        co.elastic.clients.elasticsearch._types.query_dsl.Query queryDslObj;
//        try {
//            // 解析 JSON DSL 为 Query 对象
//            String queryJson;
//            // 兼容处理：检测是否为完整的查询 DSL（以 {"query" 开头）
//            if (resolvedDsl.startsWith("{\"query\"")) {
//                // 已是完整查询体，提取 query 部分
//                Map<String, Object> fullBody = OBJECT_MAPPER.readValue(resolvedDsl, new TypeReference<Map<String, Object>>() {});
//                Object queryPart = fullBody.get("query");
//                if (queryPart != null) {
//                    queryJson = OBJECT_MAPPER.writeValueAsString(queryPart);
//                } else {
//                    queryJson = "{\"match_all\":{}}";
//                }
//            } else {
//                // 只是查询条件，直接使用
//                queryJson = resolvedDsl;
//            }
//            // 使用 ES Java Client 8.x 的 JSON 解析器
//            // 通过 Jackson 将 JSON 字符串转换为 Map，再构建 Query 对象
//            Map<String, Object> queryMap = OBJECT_MAPPER.readValue(queryJson, new TypeReference<Map<String, Object>>() {});
//            queryDslObj = buildQueryFromMap(queryMap);
//        } catch (Exception e) {
//            log.warn("解析查询 DSL 失败，使用 match_all 查询: {}", e.getMessage());
//            queryDslObj = co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q.matchAll(m -> m));
//        }

//        NativeQuery query = NativeQuery.builder()
//            .withQuery(queryDsl)
//            .withPageable(pageable)
//            .build();
//
//        SearchHits<T> hits = operations.search(query, clazz, index(indexName));
//        if (hits.getSearchHits().isEmpty()) {
//            return EsPageInfo.empty(pageable.getPageNumber(), pageable.getPageSize());
//        }

        StringQuery query = new StringQuery(queryDsl);

        // 2. 设置分页信息
        query.setPageable(pageable);

        // 3. 执行查询 (注意：StringQuery 不需要 .builder())
        SearchHits<T> hits = operations.search(query, clazz, index(indexName));

        // 4. 处理结果
        if (hits.getSearchHits().isEmpty()) {
            return EsPageInfo.empty(pageable.getPageNumber(), pageable.getPageSize());
        }

        List<T> content = new ArrayList<>(hits.getSearchHits().size());
        for (SearchHit<T> hit : hits.getSearchHits()) {
            content.add(hit.getContent());
        }
        return new EsPageInfo<>(pageable.getPageNumber() + 1, pageable.getPageSize(), hits.getTotalHits(), content);
    }

    /**
     * 从 Map 构建 ES Query 对象，支持常见的查询类型。
     * 兼容 ES 7.x 和 8.x 版本。
     */
    @SuppressWarnings("unchecked")
    private co.elastic.clients.elasticsearch._types.query_dsl.Query buildQueryFromMap(Map<String, Object> queryMap) {
        if (queryMap == null || queryMap.isEmpty()) {
            return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q.matchAll(m -> m));
        }

        // 检测查询类型并构建对应的 Query
        if (queryMap.containsKey("match_all")) {
            return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q.matchAll(m -> m));
        } else if (queryMap.containsKey("bool")) {
            return buildBoolQuery((Map<String, Object>) queryMap.get("bool"));
        } else if (queryMap.containsKey("term")) {
            return buildTermQuery((Map<String, Object>) queryMap.get("term"));
        } else if (queryMap.containsKey("terms")) {
            return buildTermsQuery((Map<String, Object>) queryMap.get("terms"));
        } else if (queryMap.containsKey("match")) {
            return buildMatchQuery((Map<String, Object>) queryMap.get("match"));
        } else if (queryMap.containsKey("match_phrase")) {
            return buildMatchPhraseQuery((Map<String, Object>) queryMap.get("match_phrase"));
        } else if (queryMap.containsKey("range")) {
            return buildRangeQuery((Map<String, Object>) queryMap.get("range"));
        } else if (queryMap.containsKey("wildcard")) {
            return buildWildcardQuery((Map<String, Object>) queryMap.get("wildcard"));
        } else if (queryMap.containsKey("exists")) {
            return buildExistsQuery((Map<String, Object>) queryMap.get("exists"));
        } else {
            // 默认返回 match_all
            return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q.matchAll(m -> m));
        }
    }

    @SuppressWarnings("unchecked")
    private co.elastic.clients.elasticsearch._types.query_dsl.Query buildBoolQuery(Map<String, Object> boolMap) {
        return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q.bool(b -> {
            if (boolMap.containsKey("must")) {
                List<Map<String, Object>> mustList = (List<Map<String, Object>>) boolMap.get("must");
                for (Map<String, Object> item : mustList) {
                    b.must(buildQueryFromMap(item));
                }
            }
            if (boolMap.containsKey("should")) {
                List<Map<String, Object>> shouldList = (List<Map<String, Object>>) boolMap.get("should");
                for (Map<String, Object> item : shouldList) {
                    b.should(buildQueryFromMap(item));
                }
            }
            if (boolMap.containsKey("must_not")) {
                List<Map<String, Object>> mustNotList = (List<Map<String, Object>>) boolMap.get("must_not");
                for (Map<String, Object> item : mustNotList) {
                    b.mustNot(buildQueryFromMap(item));
                }
            }
            if (boolMap.containsKey("filter")) {
                List<Map<String, Object>> filterList = (List<Map<String, Object>>) boolMap.get("filter");
                for (Map<String, Object> item : filterList) {
                    b.filter(buildQueryFromMap(item));
                }
            }
            return b;
        }));
    }

    @SuppressWarnings("unchecked")
    private co.elastic.clients.elasticsearch._types.query_dsl.Query buildTermQuery(Map<String, Object> termMap) {
        return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> {
            for (Map.Entry<String, Object> entry : termMap.entrySet()) {
                String field = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof Map) {
                    Map<String, Object> valueMap = (Map<String, Object>) value;
                    q.term(t -> t.field(field).value(String.valueOf(valueMap.get("value"))));
                } else {
                    q.term(t -> t.field(field).value(String.valueOf(value)));
                }
            }
            return q;
        });
    }

    @SuppressWarnings("unchecked")
    private co.elastic.clients.elasticsearch._types.query_dsl.Query buildTermsQuery(Map<String, Object> termsMap) {
        return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> {
            for (Map.Entry<String, Object> entry : termsMap.entrySet()) {
                String field = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof List) {
                    List<String> values = ((List<?>) value).stream().map(String::valueOf).toList();
                    q.terms(t -> t.field(field).terms(tv -> tv.value(values.stream().map(v -> co.elastic.clients.elasticsearch._types.FieldValue.of(fv -> fv.stringValue(v))).toList())));
                }
            }
            return q;
        });
    }

    @SuppressWarnings("unchecked")
    private co.elastic.clients.elasticsearch._types.query_dsl.Query buildMatchQuery(Map<String, Object> matchMap) {
        return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> {
            for (Map.Entry<String, Object> entry : matchMap.entrySet()) {
                String field = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof Map) {
                    Map<String, Object> valueMap = (Map<String, Object>) value;
                    q.match(m -> m.field(field).query(String.valueOf(valueMap.get("query"))));
                } else {
                    q.match(m -> m.field(field).query(String.valueOf(value)));
                }
            }
            return q;
        });
    }

    @SuppressWarnings("unchecked")
    private co.elastic.clients.elasticsearch._types.query_dsl.Query buildMatchPhraseQuery(Map<String, Object> matchPhraseMap) {
        return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> {
            for (Map.Entry<String, Object> entry : matchPhraseMap.entrySet()) {
                String field = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof Map) {
                    Map<String, Object> valueMap = (Map<String, Object>) value;
                    q.matchPhrase(m -> m.field(field).query(String.valueOf(valueMap.get("query"))));
                } else {
                    q.matchPhrase(m -> m.field(field).query(String.valueOf(value)));
                }
            }
            return q;
        });
    }

    @SuppressWarnings("unchecked")
    private co.elastic.clients.elasticsearch._types.query_dsl.Query buildRangeQuery(Map<String, Object> rangeMap) {
        return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> {
            for (Map.Entry<String, Object> entry : rangeMap.entrySet()) {
                String field = entry.getKey();
                Map<String, Object> rangeValue = (Map<String, Object>) entry.getValue();
                q.range(r -> {
                    r.field(field);
                    if (rangeValue.containsKey("gt")) {
                        r.gt(co.elastic.clients.json.JsonData.of(String.valueOf(rangeValue.get("gt"))));
                    }
                    if (rangeValue.containsKey("gte")) {
                        r.gte(co.elastic.clients.json.JsonData.of(String.valueOf(rangeValue.get("gte"))));
                    }
                    if (rangeValue.containsKey("lt")) {
                        r.lt(co.elastic.clients.json.JsonData.of(String.valueOf(rangeValue.get("lt"))));
                    }
                    if (rangeValue.containsKey("lte")) {
                        r.lte(co.elastic.clients.json.JsonData.of(String.valueOf(rangeValue.get("lte"))));
                    }
                    return r;
                });
            }
            return q;
        });
    }

    @SuppressWarnings("unchecked")
    private co.elastic.clients.elasticsearch._types.query_dsl.Query buildWildcardQuery(Map<String, Object> wildcardMap) {
        return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> {
            for (Map.Entry<String, Object> entry : wildcardMap.entrySet()) {
                String field = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof Map) {
                    Map<String, Object> valueMap = (Map<String, Object>) value;
                    q.wildcard(w -> w.field(field).value(String.valueOf(valueMap.get("value"))));
                } else {
                    q.wildcard(w -> w.field(field).value(String.valueOf(value)));
                }
            }
            return q;
        });
    }

    private co.elastic.clients.elasticsearch._types.query_dsl.Query buildExistsQuery(Map<String, Object> existsMap) {
        return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> {
            String field = (String) existsMap.get("field");
            if (field != null) {
                q.exists(e -> e.field(field));
            }
            return q;
        });
    }

    /* ================= 条件 + 动态聚合 ================= */

    public <T> AggregationsContainer<?> aggregate(String indexName,
                                                  Query boolQuery,
                                                  Map<String, Aggregation> aggregationMap,
                                                  Class<T> clazz) {
        NativeQueryBuilder builder = new NativeQueryBuilder()
                .withQuery(boolQuery)
                .withMaxResults(0); // 只关心聚合结果

        if (aggregationMap != null) {
            for (Map.Entry<String, Aggregation> entry : aggregationMap.entrySet()) {
                builder.withAggregation(entry.getKey(), entry.getValue());
            }
        }

        NativeQuery query = builder.build();
        SearchHits<T> hits = operations.search(query, clazz, index(indexName));
        return hits.getAggregations();
    }

    public String buildMultiDynamicQueryDsl(List<ConditionDTO> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return "{\"match_all\":{}}";
        }
        List<String> mustQueries = new ArrayList<>();
        for (ConditionDTO item : conditions) {
            String field = item.getField();
            Object value = item.getValue();
            String type = item.getType();

            // 基础校验
            if (field == null || field.trim().isEmpty()) {
                continue;
            }

            // 字段不为空 / 不为 null 查询：使用 field:* 语法
            if ("notEmpty".equalsIgnoreCase(type) || "notNull".equalsIgnoreCase(type)) {
                String queryString = field + ":*";
                mustQueries.add("{\"query_string\":{\"query\":\"" + escapeJson(queryString) + "\",\"analyze_wildcard\":true}}");
                continue;
            }

            // 字段为 null 或空字符串查询：使用 bool should 组合 exists 取反 + term ""
            if ("null".equalsIgnoreCase(type)) {
                String nullQuery = "{\"bool\":{\"should\":["
                        + "{\"bool\":{\"must_not\":{\"exists\":{\"field\":\"" + escapeJson(field) + "\"}}}}"
                        + ",{\"term\":{\"" + escapeJson(field) + "\":\"\"}}"
                        + "],\"minimum_should_match\":1}}";
                mustQueries.add(nullQuery);
                continue;
            }

            if (value == null) {
                continue;
            }

            if ("in".equalsIgnoreCase(type)) {
                List<?> values;
                if (value instanceof List<?> listValue) {
                    values = listValue;
                } else if (value.getClass().isArray()) {
                    values = java.util.Arrays.asList((Object[]) value);
                } else {
                    values = Collections.singletonList(value);
                }
                if (values.isEmpty()) {
                    continue;
                }
                try {
                    Map<String, Object> termsBody = new java.util.LinkedHashMap<>();
                    termsBody.put(field, values);
                    Map<String, Object> termsQuery = new java.util.LinkedHashMap<>();
                    termsQuery.put("terms", termsBody);
                    mustQueries.add(OBJECT_MAPPER.writeValueAsString(termsQuery));
                } catch (Exception e) {
                    throw new IllegalArgumentException("构建 in 查询条件失败", e);
                }
                continue;
            }

            String stringValue = String.valueOf(value);

            // 2. 对用户输入进行转义，防止 Lucene 语法注入报错
            // 例如将 "title:abc" 转义为 "title\:abc"
            String escapedValue = QueryParser.escape(stringValue);

            String queryString;
            if ("match".equals(type)) {
                // 精确匹配语法：field:"value"
                queryString = field + ":\"" + escapedValue + "\"";
            } else {
                // 模糊匹配语法：field:*value*
                queryString = field + ":*" + escapedValue + "*";
            }

            // 3. 构建 QueryStringQuery 并加入 bool 结构的 must 中 (AND关系)
            mustQueries.add("{\"query_string\":{\"query\":\"" + escapeJson(queryString) + "\",\"analyze_wildcard\":true}}");
        }
        if (mustQueries.isEmpty()) {
            return "{\"match_all\":{}}";
        }
        return "{\"bool\":{\"must\":[" + String.join(",", mustQueries) + "]}}";
    }

    /**
     * 基于 Map 条件的分页查询，key 作为字段名，value 作为查询值，默认精确匹配。
     *
     * @param index      索引名（会自动应用 es.prefix）
     * @param conditions 字段名 -> 查询值，默认精确匹配（term）
     * @return 分页结果
     */
    public List<Map> list(String index, Map<String, Object> conditions) {
        EsPageInfo<Map> pageInfo = list(index, conditions, 1, 10000, "createdTime", "asc");
        return pageInfo == null ? new ArrayList() : pageInfo.getList();
    }

    /**
     * 基于 Map 条件的分页查询，key 作为字段名，value 作为查询值，默认精确匹配。
     *
     * @param index      索引名（会自动应用 es.prefix）
     * @param conditions 字段名 -> 查询值，默认精确匹配
     * @param pageNum    页码（从 1 开始）
     * @param pageSize   每页条数
     * @return 分页结果
     */
    public EsPageInfo<Map> list(String index, Map<String, Object> conditions, int pageNum, int pageSize) {
        return list(index, conditions, pageNum, pageSize, null, null);
    }

    /**
     * 基于 Map 条件的分页查询，key 作为字段名，value 作为查询值，默认精确匹配。
     *
     * @param index      索引名（会自动应用 es.prefix）
     * @param conditions 字段名 -> 查询值，默认精确匹配
     * @param pageNum    页码（从 1 开始）
     * @param pageSize   每页条数
     * @param sortField  排序字段，null 则不排序
     * @param sortDir    排序方向 asc/desc，null 则不排序
     * @return 分页结果
     */
    public EsPageInfo<Map> list(String index, Map<String, Object> conditions, int pageNum, int pageSize,
                                String sortField, String sortDir) {
        List<ConditionDTO> conditionList = new ArrayList<>();
        if (conditions != null) {
            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    conditionList.add(new ConditionDTO(entry.getKey(), String.valueOf(entry.getValue()), "match"));
                }
            }
        }
        EsPageParam param = new EsPageParam();
        param.setIndex(index);
        param.setConditions(conditionList);
        param.setPageNum(pageNum);
        param.setPageSize(pageSize);
        param.setSortField(sortField);
        param.setSortDir(sortDir);
        return page(param);
    }

    /**
     * 分页查询，支持多 dbId 的 terms 过滤（用于 nodeId -> table 查询转换）。
     * 在普通 conditions 基础上，额外追加 dbId 的 terms filter 子句。
     *
     * @param esPageParam 分页参数（不含 dbId 条件）
     * @param dbIds       要过滤的 dbId 列表
     */
    public EsPageInfo<Map> pageWithDbIds(EsPageParam esPageParam, List<String> dbIds) {
        int pageNum = esPageParam.getPageNum();
        if (pageNum <= 0) {
            pageNum = 0;
        } else {
            pageNum = pageNum - 1;
        }
        // 基础 bool 条件（来自普通 conditions）
        String baseDsl = buildMultiDynamicQueryDsl(esPageParam.getConditions());

        // 将 dbIds 拼成 terms 过滤子句
        StringBuilder dbIdTerms = new StringBuilder();
        dbIdTerms.append("{\"terms\":{\"dbId\":[");
        for (int i = 0; i < dbIds.size(); i++) {
            if (i > 0) dbIdTerms.append(",");
            dbIdTerms.append("\"").append(escapeJson(dbIds.get(i))).append("\"");
        }
        dbIdTerms.append("]}}");

        String combinedDsl;
        if ("{\"match_all\":{}}".equals(baseDsl)) {
            combinedDsl = "{\"bool\":{\"filter\":[" + dbIdTerms + "]}}";
        } else {
            combinedDsl = "{\"bool\":{\"must\":[" + baseDsl + "],\"filter\":[" + dbIdTerms + "]}}";
        }

        Sort sort = Sort.unsorted();
        if (esPageParam.getSortField() != null && !esPageParam.getSortField().trim().isEmpty()) {
            sort = "desc".equalsIgnoreCase(esPageParam.getSortDir())
                    ? Sort.by(esPageParam.getSortField()).descending()
                    : Sort.by(esPageParam.getSortField()).ascending();
        }
        printEsSearchParams(esPageParam.getIndex(), combinedDsl, pageNum, esPageParam.getPageSize(), sort);
        return searchPage(esPageParam.getIndex(), combinedDsl,
                PageRequest.of(pageNum, esPageParam.getPageSize(), sort), Map.class);
    }

    //magic 调用分装
    public EsPageInfo<Map> page(EsPageParam esPageParam) {
        if (esPageParam.getPageNum() <= 0) {
            esPageParam.setPageNum(0);
        } else {
            esPageParam.setPageNum(esPageParam.getPageNum() - 1);
        }
        String boolQueryDsl = buildMultiDynamicQueryDsl(esPageParam.getConditions());
        Sort sort = Sort.unsorted();
        if (esPageParam.getSortField() != null && !esPageParam.getSortField().trim().isEmpty()) {
            sort = "desc".equalsIgnoreCase(esPageParam.getSortDir())
                    ? Sort.by(esPageParam.getSortField()).descending()
                    : Sort.by(esPageParam.getSortField()).ascending();
        }
        // 打印 ES 查询参数，便于复制到 ES 接口（Kibana Dev Tools / curl）直接查询
        printEsSearchParams(esPageParam.getIndex(), boolQueryDsl, esPageParam.getPageNum(),
                esPageParam.getPageSize(), sort);
        return searchPage(esPageParam.getIndex(), boolQueryDsl, PageRequest.of(esPageParam.getPageNum(), esPageParam.getPageSize(),
                sort), Map.class);
    }

    /**
     * 打印 ES 查询参数，输出可直接用于 ES REST API 的 JSON，便于在 Kibana Dev Tools 或 curl 中调试。
     */
    private void printEsSearchParams(String indexName, String queryDsl, int from, int size, Sort sort) {
        try {
            int fromOffset = from * size;
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("from", fromOffset);
            body.put("size", size);
            Map<String, Object> queryMap = OBJECT_MAPPER.readValue(
                    (queryDsl == null || queryDsl.trim().isEmpty()) ? "{\"match_all\":{}}" : queryDsl,
                    new TypeReference<Map<String, Object>>() {
                    });
            body.put("query", queryMap);
            if (sort.isSorted()) {
                List<Map<String, String>> sortList = new ArrayList<>();
                for (Sort.Order order : sort) {
                    Map<String, String> sortItem = new java.util.LinkedHashMap<>();
                    sortItem.put(order.getProperty(), order.getDirection().name().toLowerCase());
                    sortList.add(sortItem);
                }
                body.put("sort", sortList);
            }
            String index = resolveIndexName(indexName);
            String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(body);
            log.info("ES 分页查询参数 - 索引: {} | 接口: GET /{}/_search\n请求体:\n{}", index, index, json);
        } catch (Exception e) {
            log.warn("打印 ES 查询参数失败: {}", e.getMessage());
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    /**
     * 将 Elasticsearch Query 序列化为 Map，用于日志输出。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> queryToMap(Query query) {
        try {
            JacksonJsonpMapper mapper = new JacksonJsonpMapper();
            java.io.StringWriter sw = new java.io.StringWriter();
            try (jakarta.json.stream.JsonGenerator generator = mapper.jsonProvider().createGenerator(sw)) {
                query.serialize(generator, mapper);
            }
            return OBJECT_MAPPER.readValue(sw.toString(), Map.class);
        } catch (Exception e) {
            log.warn("Query 序列化失败: {}", e.getMessage());
            return java.util.Collections.singletonMap("_error", "serialization failed");
        }
    }

}


