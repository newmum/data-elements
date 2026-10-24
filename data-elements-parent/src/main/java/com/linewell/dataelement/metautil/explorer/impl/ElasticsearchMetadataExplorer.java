package com.linewell.dataelement.metautil.explorer.impl;

import com.linewell.dataelement.metautil.exception.DataSourceConnectionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.metautil.explorer.MetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.dto.DatabaseInfo;
import com.linewell.dataelement.metautil.model.dto.IndexInfo;
import com.linewell.dataelement.metautil.model.dto.SampleDataResult;
import com.linewell.dataelement.metautil.model.dto.SampleRequest;
import com.linewell.dataelement.metautil.model.dto.TableInfo;
import com.linewell.dataelement.metautil.model.dto.TablePageResult;
import com.linewell.dataelement.metautil.model.dto.SqlValidationResult;
import com.linewell.dataelement.metautil.model.dto.PageQueryRequest;
import com.linewell.dataelement.metautil.model.dto.PageQueryResult;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.metautil.query.DataQueryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Elasticsearch/OpenSearch只读协议适配：普通索引对应表，mapping对应字段。
 * 输入为调用方已授权的连接与索引，输出统一元数据/分页文档；不读写平台业务表，不执行SQL。
 * HttpClient可并发复用，请求有超时且完整消费响应；无文档、索引或mapping写入副作用。
 * 隐藏索引不参与登记，索引范围来自database，显示名称不得充当索引名。
 */
@Slf4j
@Component
public class ElasticsearchMetadataExplorer implements MetadataExplorer, DataQueryExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_TIMEOUT_SECONDS = 15;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
            .build();

    private Map<String, String> options(DataSourceConfig config) {
        Map<String, String> map = new LinkedHashMap<>();
        if (config.getConnectorProperties() != null) {
            config.getConnectorProperties().forEach((key, value) -> {
                if (value != null) map.put(key, String.valueOf(value));
            });
        }
        String extra = config.getExtraParams();
        if (extra == null || extra.isBlank()) {
            return map;
        }
        String clean = extra.startsWith("?") ? extra.substring(1) : extra;
        for (String item : clean.split("&")) {
            int idx = item.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            map.put(URLDecoder.decode(item.substring(0, idx), StandardCharsets.UTF_8),
                    URLDecoder.decode(item.substring(idx + 1), StandardCharsets.UTF_8));
        }
        return map;
    }

    private String baseUrl(DataSourceConfig config) {
        if (config.getJdbcUrl() != null && !config.getJdbcUrl().isBlank()) {
            String url = config.getJdbcUrl().trim();
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return trimSlash(url);
            }
        }
        String nodes = options(config).get("nodes");
        if (nodes != null && !nodes.isBlank()) {
            String node = nodes.split(",")[0].trim();
            return trimSlash(node.startsWith("http://") || node.startsWith("https://") ? node
                    : options(config).getOrDefault("protocol", "http") + "://" + node);
        }
        String host = config.getHost() == null ? "" : config.getHost().trim();
        if (host.startsWith("http://") || host.startsWith("https://")) {
            return trimSlash(host);
        }
        Map<String, String> opts = options(config);
        String protocol = opts.getOrDefault("protocol", opts.getOrDefault("scheme", "http"));
        int port = config.getPort() == null ? 9200 : config.getPort();
        return protocol + "://" + host + ":" + port;
    }

    private String trimSlash(String value) {
        String v = value;
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private String indexPattern(DataSourceConfig config) {
        String pattern = config.getDatabase();
        if (pattern == null || pattern.isBlank()) {
            return "";
        }
        return pattern.trim();
    }

    private HttpRequest.Builder requestBuilder(DataSourceConfig config, String path) {
        String url = baseUrl(config) + path;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getConnectionTimeout() == null
                        ? DEFAULT_TIMEOUT_SECONDS
                        : Math.max(3, config.getConnectionTimeout())));

        Map<String, String> opts = options(config);
        String token = opts.get("token");
        String apiKey = opts.get("apiKey");
        String method = opts.getOrDefault("authMethod", "").toUpperCase(Locale.ROOT);
        boolean automatic = method.isBlank();
        if ((automatic || method.equals("API_KEY")) && apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "ApiKey " + apiKey);
        } else if ((automatic || method.equals("TOKEN") || method.equals("BEARER")) && token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        } else if ((automatic || method.equals("BASIC")) && config.getUsername() != null && !config.getUsername().isBlank()) {
            String raw = config.getUsername() + ":" + (config.getPassword() == null ? "" : config.getPassword());
            builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
        }
        builder.header("Accept", "application/json");
        return builder;
    }

    private JsonNode json(DataSourceConfig config, String path) {
        try {
            HttpRequest request = requestBuilder(config, path).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Elasticsearch读取失败，HTTP " + status);
            }
            String body = response.body();
            if (body == null || body.isBlank()) {
                return MAPPER.createObjectNode();
            }
            return MAPPER.readTree(body);
        } catch (IOException e) {
            throw new RuntimeException("读取 Elasticsearch 响应失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("读取 Elasticsearch 响应被中断", e);
        }
    }

    private JsonNode postJson(DataSourceConfig config, String path, String body) {
        try {
            HttpRequest request = requestBuilder(config, path)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Elasticsearch查询失败，HTTP " + status);
            }
            return MAPPER.readTree(response.body());
        } catch (IOException e) {
            throw new RuntimeException("读取 Elasticsearch 响应失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("读取 Elasticsearch 响应被中断", e);
        }
    }

    @Override
    public boolean testConnection(DataSourceConfig config) {
        try {
            JsonNode root = json(config, "/");
            return root.has("version") || root.has("cluster_name");
        } catch (Exception e) {
            log.warn("Elasticsearch connection test failed: {}", e.getMessage());
            throw new DataSourceConnectionException("Elasticsearch 连接测试失败", e);
        }
    }

    @Override
    public DatabaseInfo getDatabaseInfo(DataSourceConfig config) {
        JsonNode root = json(config, "/");
        TablePageResult page = getTablesPage(config, 1, 1, null);
        DatabaseInfo info = new DatabaseInfo();
        info.setDatabaseName(indexPattern(config).isBlank() ? "Elasticsearch" : indexPattern(config));
        info.setDatabaseType("Elasticsearch");
        JsonNode version = root.path("version").path("number");
        info.setVersion(version.isMissingNode() ? null : version.asText());
        info.setTableCount(page.getTotal() == null ? 0 : page.getTotal().intValue());
        info.setViewCount(0);
        info.setProcedureCount(0);
        info.setUserCount(0);
        info.setSchemas(Collections.emptyList());
        info.setDatabases(Collections.emptyList());
        return info;
    }

    @Override
    public List<String> getDatabases(DataSourceConfig config) {
        List<String> names = new ArrayList<>();
        for (TableInfo table : getTables(config)) {
            names.add(table.getTableName());
        }
        return names;
    }

    @Override
    public List<String> getSchemas(DataSourceConfig config) {
        return Collections.emptyList();
    }

    @Override
    public List<TableInfo> getTables(DataSourceConfig config) {
        // 后台采集需要完整名称清单，不沿用交互页500条上限截断；字段在打开表时按需读取。
        return discoverTables(config, null);
    }

    @Override
    public TablePageResult getTablesPage(DataSourceConfig config, Integer pageNo, Integer pageSize, String keyword) {
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 50 : Math.min(pageSize, 500);
        List<TableInfo> all = discoverTables(config, keyword);
        int fromIndex = (int) Math.min((long) (safePageNo - 1) * safePageSize, all.size());
        int toIndex = Math.min(fromIndex + safePageSize, all.size());
        List<TableInfo> page = new ArrayList<>(all.subList(fromIndex, toIndex));
        Map<String, List<ColumnInfo>> columns = getColumnsByTables(config,
                page.stream().map(TableInfo::getTableName).toList());
        page.forEach(table -> table.setColumnCount(columns.get(table.getTableName().toLowerCase(Locale.ROOT)).size()));
        TablePageResult result = new TablePageResult();
        result.setPageNo(safePageNo);
        result.setPageSize(safePageSize);
        result.setTotal((long) all.size());
        result.setRows(page);
        return result;
    }

    private List<TableInfo> discoverTables(DataSourceConfig config, String keyword) {
        String pattern = indexPattern(config);
        String path = "/_cat/indices";
        if (!pattern.isBlank()) {
            path += "/" + encodePath(pattern);
        }
        path += "?format=json&bytes=b&s=index&h=index,docs.count,store.size,pri.store.size";

        JsonNode array = json(config, path);
        List<TableInfo> all = new ArrayList<>();
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (array.isArray()) {
            for (JsonNode node : array) {
                String index = text(node, "index");
                if (index == null || index.isBlank() || index.startsWith(".")) {
                    continue;
                }
                if (!kw.isBlank() && !index.toLowerCase(Locale.ROOT).contains(kw)) {
                    continue;
                }
                TableInfo table = new TableInfo();
                table.setTableName(index);
                table.setTableComment(index);
                table.setTableType("INDEX");
                table.setEngine("Elasticsearch");
                table.setRowCount(longValue(text(node, "docs.count")));
                Long size = sizeBytes(firstText(node, "store.size", "pri.store.size", "dataset.size"));
                table.setDataSizeBytes(size);
                table.setDataSizeFormatted(formatBytes(size));
                table.setTotalSizeBytes(size);
                table.setTotalSizeFormatted(formatBytes(size));
                all.add(table);
                if (all.size() > 10000) throw new IllegalArgumentException("可见索引超过10000个，请缩小索引范围");
            }
        }

        return all;
    }

    @Override
    public TableInfo getTableInfo(DataSourceConfig config, String tableName) {
        TableInfo info = new TableInfo();
        info.setTableName(tableName);
        info.setTableComment(tableName);
        info.setTableType("INDEX");
        info.setEngine("Elasticsearch");
        info.setColumns(getColumns(config, tableName));
        info.setIndexes(getIndexes(config, tableName));
        info.setColumnCount(info.getColumns() == null ? 0 : info.getColumns().size());
        try {
            info.setRowCount(getTableRowCount(config, tableName));
        } catch (Exception e) {
            log.debug("Read ES count failed for index {}: {}", tableName, e.getMessage());
        }
        return info;
    }

    @Override
    public List<ColumnInfo> getColumns(DataSourceConfig config, String tableName) {
        return getColumnsByTables(config, List.of(tableName)).get(tableName.toLowerCase(Locale.ROOT));
    }

    /** 最多500个索引；每批最多100个且路径不超过3000字符，避免ES默认HTTP首行上限。 */
    @Override
    public Map<String, List<ColumnInfo>> getColumnsByTables(DataSourceConfig config, List<String> tableNames) {
        Map<String, List<ColumnInfo>> result = new LinkedHashMap<>();
        if (tableNames == null || tableNames.isEmpty()) return result;
        List<String> names = tableNames.stream().distinct().toList();
        if (names.size() > 500) throw new IllegalArgumentException("单批最多读取500个索引的字段");
        names.forEach(name -> checkIndexScope(config, name));
        List<List<String>> batches = new ArrayList<>();
        List<String> batch = new ArrayList<>();
        int pathLength = 0;
        for (String name : names) {
            int length = encodePath(name).length() + 1;
            if (!batch.isEmpty() && (batch.size() == 100 || pathLength + length > 3000)) {
                batches.add(batch); batch = new ArrayList<>(); pathLength = 0;
            }
            batch.add(name); pathLength += length;
        }
        if (!batch.isEmpty()) batches.add(batch);
        var mappings = MAPPER.createObjectNode();
        for (List<String> current : batches) {
            JsonNode response = json(config, "/" + String.join(",", current.stream().map(this::encodePath).toList()) + "/_mapping");
            response.fields().forEachRemaining(entry -> mappings.set(entry.getKey(), entry.getValue()));
        }
        for (String name : names) {
            JsonNode indexNode = mappings.path(name);
            if (indexNode.isMissingNode()) throw new IllegalStateException("未读取到索引mapping：" + name);
            result.put(name.toLowerCase(Locale.ROOT), columns(indexNode.path("mappings").path("properties")));
        }
        return result;
    }

    private List<ColumnInfo> columns(JsonNode properties) {
        List<ColumnInfo> columns = new ArrayList<>();
        collectProperties(properties, "", columns);
        return columns;
    }

    private void collectProperties(JsonNode properties, String prefix, List<ColumnInfo> columns) {
        if (properties == null || !properties.isObject()) {
            return;
        }
        var fields = properties.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String name = prefix.isBlank() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonNode def = entry.getValue();
            String type = def.path("type").asText("object");
            ColumnInfo column = new ColumnInfo();
            column.setColumnName(name);
            column.setColumnComment(name);
            column.setDataType(type);
            column.setColumnType(type);
            column.setNullable(true);
            column.setPrimaryKey("_id".equals(name));
            column.setOrdinalPosition(columns.size() + 1);
            columns.add(column);
            JsonNode nested = def.path("properties");
            if (nested.isObject()) {
                collectProperties(nested, name, columns);
            }
        }
    }

    @Override
    public List<IndexInfo> getIndexes(DataSourceConfig config, String tableName) {
        IndexInfo index = new IndexInfo();
        index.setIndexName(tableName);
        index.setIndexType("ELASTICSEARCH");
        index.setUnique(false);
        index.setPrimaryKey(false);
        index.setColumns(List.of("_id"));
        index.setComment("Elasticsearch index");
        return List.of(index);
    }

    @Override
    public Long getTableRowCount(DataSourceConfig config, String tableName) {
        checkIndexScope(config, tableName);
        JsonNode count = json(config, "/" + encodePath(tableName) + "/_count");
        return count.path("count").asLong(0L);
    }

    @Override
    public Long getTableSize(DataSourceConfig config, String tableName) {
        for (TableInfo table : discoverTables(config, tableName)) {
            if (tableName.equals(table.getTableName())) {
                return table.getDataSizeBytes();
            }
        }
        return 0L;
    }

    @Override
    public Long getDatabaseSize(DataSourceConfig config) {
        long total = 0L;
        for (TableInfo table : getTables(config)) {
            if (table.getDataSizeBytes() != null) {
                total += table.getDataSizeBytes();
            }
        }
        return total;
    }

    @Override
    public SampleDataResult sampleData(SampleRequest request) {
        checkIndexScope(request.getDataSource(), request.getTableName());
        int size = request.getSampleSize() == null ? 20 : Math.min(request.getSampleSize(), 200);
        JsonNode result = postJson(request.getDataSource(), "/" + encodePath(request.getTableName()) + "/_search",
                "{\"size\":" + size + ",\"track_total_hits\":true,\"query\":{\"match_all\":{}},\"sort\":[\"_doc\"]}");
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        JsonNode hits = result.path("hits").path("hits");
        if (hits.isArray()) {
            for (JsonNode hit : hits) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("_id", hit.path("_id").asText());
                flattenSource(hit.path("_source"), "", row);
                for (String key : row.keySet()) {
                    if (!columns.contains(key)) {
                        columns.add(key);
                    }
                }
                rows.add(row);
            }
        }
        SampleDataResult sample = new SampleDataResult();
        sample.setTableName(request.getTableName());
        sample.setSampleSize(size);
        sample.setActualSize(rows.size());
        sample.setTotalCount(result.path("hits").path("total").path("value").asLong(rows.size()));
        sample.setColumnNames(columns);
        sample.setColumnTypes(Collections.emptyList());
        sample.setData(rows);
        sample.setSampleMethod(request.getSampleMethod() == null ? "FIRST" : request.getSampleMethod().name());
        return sample;
    }

    private void flattenSource(JsonNode node, String prefix, Map<String, Object> row) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (!node.isObject()) {
            row.put(prefix, MAPPER.convertValue(node, Object.class));
            return;
        }
        var fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String name = prefix.isBlank() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonNode value = entry.getValue();
            if (value.isObject()) {
                flattenSource(value, name, row);
            } else {
                row.put(name, MAPPER.convertValue(value, Object.class));
            }
        }
    }

    @Override
    public boolean supports(DatabaseType type) {
        return type == DatabaseType.ELASTICSEARCH;
    }

    /**
     * 原生DSL分页预览：一次mapping和一次search，20/100条均不逐文档补查。
     * 当前租户和登记表归属由Magic入口校验；此处继续限定连接的索引范围与真实字段。
     * from/size受ES默认10000窗口约束，总数用track_total_hits精确返回；不执行SQL或写索引。
     */
    @Override
    public PageQueryResult queryPage(PageQueryRequest request) {
        DataSourceConfig config = request.getDataSource();
        String index = request.getTableName();
        checkIndexScope(config, index);
        int pageNo = request.getPageNo() == null ? 1 : request.getPageNo();
        int size = request.getPageSize() == null ? 20 : request.getPageSize();
        long offset = (long) (pageNo - 1) * size;
        if (pageNo < 1 || size < 1 || size > 200 || offset + size > 10000) {
            throw new IllegalArgumentException("ES预览最多读取前10000条，请缩小筛选范围");
        }
        JsonNode mapping = json(config, "/" + encodePath(index) + "/_mapping").path(index);
        if (mapping.isMissingNode()) throw new IllegalStateException("未读取到索引mapping：" + index);
        JsonNode properties = mapping.path("mappings").path("properties");
        Map<String, String> types = new LinkedHashMap<>();
        types.put("_id", "keyword");
        columns(properties).forEach(column -> types.put(column.getColumnName(), column.getDataType()));
        List<String> selected = request.getColumns() == null || request.getColumns().isEmpty()
                ? new ArrayList<>(types.keySet()) : request.getColumns().stream().distinct().toList();
        selected.forEach(field -> requireField(types, field));
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("from", offset);
        query.put("size", size);
        query.put("track_total_hits", true);
        query.put("_source", selected.stream().filter(field -> !field.equals("_id")).toList());
        List<Object> filters = new ArrayList<>();
        if (request.getConditions() != null) {
            for (var condition : request.getConditions()) {
                String field = condition.getField();
                requireField(types, field);
                Object value = condition.getValue();
                String op = condition.getOp() == null ? "eq" : condition.getOp().toLowerCase(Locale.ROOT);
                Object filter;
                if (op.equals("isnull") || op.equals("is_null") || value == null && List.of("eq", "=").contains(op)) {
                    filter = Map.of("bool", Map.of("must_not", List.of(Map.of("exists", Map.of("field", field)))));
                } else if (op.equals("notnull") || op.equals("is_not_null") || value == null && op.equals("ne")) {
                    filter = Map.of("exists", Map.of("field", field));
                } else {
                    String queryField = exactField(properties, field, types.get(field));
                    filter = switch (op) {
                        case "eq", "=" -> Map.of("term", Map.of(queryField, value));
                        case "ne", "neq", "<>" -> Map.of("bool", Map.of("must_not", List.of(Map.of("term", Map.of(queryField, value)))));
                        case "gt", "ge", "gte", "lt", "le", "lte" -> Map.of("range", Map.of(queryField,
                                Map.of(op.equals("ge") ? "gte" : op.equals("le") ? "lte" : op, value)));
                        case "in" -> Map.of("terms", Map.of(queryField, value));
                        case "like" -> Map.of("wildcard", Map.of(queryField, "*" + String.valueOf(value).replace("*", "\\*").replace("?", "\\?") + "*"));
                        default -> throw new IllegalArgumentException("ES预览不支持该过滤方式：" + op);
                    };
                }
                // nested文档必须在对应路径内匹配，不能当普通object扁平查询。
                JsonNode parent = properties;
                List<String> nestedPaths = new ArrayList<>();
                String path = "";
                for (String part : field.split("\\.")) {
                    path = path.isEmpty() ? part : path + "." + part;
                    JsonNode definition = parent.path(part);
                    if (definition.path("type").asText().equals("nested")) {
                        nestedPaths.add(path);
                    }
                    parent = definition.path("properties");
                }
                for (int i = nestedPaths.size() - 1; i >= 0; i--) {
                    filter = Map.of("nested", Map.of("path", nestedPaths.get(i), "query", filter));
                }
                filters.add(filter);
            }
        }
        query.put("query", filters.isEmpty() ? Map.of("match_all", Map.of()) : Map.of("bool", Map.of("filter", filters)));
        List<Object> sorts = new ArrayList<>();
        if (request.getSorts() != null) {
            for (var sort : request.getSorts()) {
                requireField(types, sort.getField());
                if (sort.getField().equals("_id")) throw new IllegalArgumentException("ES文档_id不支持排序，请选择业务字段");
                String direction = sort.getDirection() == null ? "asc" : sort.getDirection().toLowerCase(Locale.ROOT);
                if (!List.of("asc", "desc").contains(direction)) throw new IllegalArgumentException("排序方向无效");
                sorts.add(Map.of(exactField(properties, sort.getField(), types.get(sort.getField())),
                        Map.of("order", direction, "missing", "_last")));
            }
        }
        sorts.add("_doc");
        query.put("sort", sorts);
        JsonNode response = postJson(config, "/" + encodePath(index) + "/_search", jsonText(query));
        if (response.path("timed_out").asBoolean() || response.path("_shards").path("failed").asInt() > 0) {
            throw new IllegalStateException("ES预览查询未完整执行，请检查集群状态后重试");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode hit : response.path("hits").path("hits")) {
            Map<String, Object> row = new LinkedHashMap<>();
            selected.forEach(field -> row.put(field, field.equals("_id") ? hit.path("_id").asText()
                    : sourceValue(hit.path("_source"), field)));
            rows.add(row);
        }
        PageQueryResult result = new PageQueryResult();
        result.setPageNo(pageNo); result.setPageSize(size);
        JsonNode total = response.path("hits").path("total");
        result.setTotal(total.isNumber() ? total.asLong() : total.path("value").asLong());
        result.setColumns(selected); result.setColumnTypes(selected.stream().map(types::get).toList());
        result.setRows(rows);
        return result;
    }

    /** 保留object/数组原值，同时能读取对象数组中的叶字段，不把实际存在的数据展示为空。 */
    private Object sourceValue(JsonNode source, String field) {
        if (source == null || source.isMissingNode() || source.isNull()) return null;
        if (source.isArray()) {
            List<Object> values = new ArrayList<>();
            source.forEach(item -> values.add(sourceValue(item, field)));
            return values;
        }
        JsonNode literal = source.get(field);
        if (literal != null) return MAPPER.convertValue(literal, Object.class);
        int dot = field.indexOf('.');
        return dot < 0 ? null : sourceValue(source.path(field.substring(0, dot)), field.substring(dot + 1));
    }

    private void requireField(Map<String, String> fields, String name) {
        if (!fields.containsKey(name)) throw new IllegalArgumentException("索引中不存在该字段：" + name);
    }

    private String exactField(JsonNode properties, String field, String type) {
        if (List.of("object", "nested").contains(type)) throw new IllegalArgumentException("请选择具体的标量字段：" + field);
        if (!"text".equals(type)) return field;
        JsonNode node = properties;
        String[] parts = field.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            node = node.path(parts[i]);
            if (i < parts.length - 1) node = node.path("properties");
        }
        var subfields = node.path("fields").fields();
        while (subfields.hasNext()) {
            var subfield = subfields.next();
            if ("keyword".equals(subfield.getValue().path("type").asText())) return field + "." + subfield.getKey();
        }
        throw new IllegalArgumentException("文本字段缺少keyword子字段，无法精确过滤或排序：" + field);
    }

    @Override
    public List<Map<String, Object>> queryGroupedValues(PageQueryRequest request, String groupColumn, String nameColumn) {
        throw new IllegalArgumentException("ES预览暂不提供字典类别分组，请使用普通数据预览");
    }

    private String jsonText(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (IOException error) { throw new IllegalArgumentException("ES查询参数无法序列化", error); }
    }

    private void checkIndexScope(DataSourceConfig config, String name) {
        if (name == null || name.isBlank() || name.startsWith(".") || name.matches(".*[,*/?#].*")) {
            throw new IllegalArgumentException("请选择一个普通索引");
        }
        String scope = indexPattern(config);
        if (scope.isBlank()) return;
        boolean allowed = false;
        for (String item : scope.split(",")) {
            String pattern = item.trim();
            boolean excluded = pattern.startsWith("-");
            if (excluded) pattern = pattern.substring(1);
            String regex = Pattern.quote(pattern).replace("*", "\\E.*\\Q").replace("?", "\\E.\\Q");
            if (name.matches(regex)) {
                if (excluded) throw new IllegalArgumentException("索引不在当前连接范围内");
                allowed = true;
            }
        }
        if (!allowed) throw new IllegalArgumentException("索引不在当前连接范围内");
    }

    @Override
    public SqlValidationResult validateSql(DataSourceConfig config, String sql) {
        return SqlValidationResult.failure("Elasticsearch 数据源请使用 DSL，不支持 SQL 校验", "DSL", "Elasticsearch");
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || fields == null) {
            return null;
        }
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isBlank() && !"-".equals(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private Long sizeBytes(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return 0L;
        }
        String text = value.trim().replace(",", "");
        try {
            return Long.parseLong(text);
        } catch (Exception ignored) {
            // ES cat APIs may return human-readable values such as 12.3kb.
        }
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        double multiplier = 1D;
        if (normalized.endsWith("kb") || normalized.endsWith("k")) {
            multiplier = 1024D;
            normalized = normalized.replaceAll("kb$|k$", "");
        } else if (normalized.endsWith("mb") || normalized.endsWith("m")) {
            multiplier = Math.pow(1024D, 2);
            normalized = normalized.replaceAll("mb$|m$", "");
        } else if (normalized.endsWith("gb") || normalized.endsWith("g")) {
            multiplier = Math.pow(1024D, 3);
            normalized = normalized.replaceAll("gb$|g$", "");
        } else if (normalized.endsWith("tb") || normalized.endsWith("t")) {
            multiplier = Math.pow(1024D, 4);
            normalized = normalized.replaceAll("tb$|t$", "");
        } else if (normalized.endsWith("pb") || normalized.endsWith("p")) {
            multiplier = Math.pow(1024D, 5);
            normalized = normalized.replaceAll("pb$|p$", "");
        } else if (normalized.endsWith("b")) {
            normalized = normalized.replaceAll("b$", "");
        }
        try {
            return Math.round(Double.parseDouble(normalized) * multiplier);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private Long longValue(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("%2A", "*");
    }

    private String formatBytes(Long bytes) {
        if (bytes == null || bytes <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB", "PB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);
        return String.format("%.2f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
