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
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
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

/**
 * Elasticsearch/OpenSearch metadata explorer.
 *
 * <p>The registration workflow treats one Elasticsearch index as one data table
 * and index mapping properties as data items. Hidden/system indices that start
 * with "." are excluded by default so business registration does not count ES
 * internal indices.</p>
 */
@Slf4j
public class ElasticsearchMetadataExplorer implements MetadataExplorer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_TIMEOUT_SECONDS = 15;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
            .build();

    private Map<String, String> options(DataSourceConfig config) {
        Map<String, String> map = new LinkedHashMap<>();
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
            map.put(item.substring(0, idx), item.substring(idx + 1));
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
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "ApiKey " + apiKey);
        } else if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        } else if (config.getUsername() != null && !config.getUsername().isBlank()) {
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
                throw new RuntimeException("HTTP " + status + " " + response.body());
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
                throw new RuntimeException("HTTP " + status + " " + response.body());
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
        return getTablesPage(config, 1, 500, null).getRows();
    }

    @Override
    public TablePageResult getTablesPage(DataSourceConfig config, Integer pageNo, Integer pageSize, String keyword) {
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 50 : Math.min(pageSize, 500);
        String pattern = indexPattern(config);
        String path = "/_cat/indices";
        if (!pattern.isBlank()) {
            path += "/" + encodePath(pattern);
        }
        path += "?format=json&bytes=b&s=index";

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
                try {
                    table.setColumnCount(getColumns(config, index).size());
                } catch (Exception e) {
                    log.debug("Read ES mapping failed for index {}: {}", index, e.getMessage());
                    table.setColumnCount(0);
                }
                all.add(table);
            }
        }

        int fromIndex = Math.min((safePageNo - 1) * safePageSize, all.size());
        int toIndex = Math.min(fromIndex + safePageSize, all.size());
        TablePageResult result = new TablePageResult();
        result.setPageNo(safePageNo);
        result.setPageSize(safePageSize);
        result.setTotal((long) all.size());
        result.setRows(new ArrayList<>(all.subList(fromIndex, toIndex)));
        return result;
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
        JsonNode mapping = json(config, "/" + encodePath(tableName) + "/_mapping");
        JsonNode indexNode = mapping.path(tableName);
        if (indexNode.isMissingNode() && mapping.fields().hasNext()) {
            indexNode = mapping.fields().next().getValue();
        }
        JsonNode properties = indexNode.path("mappings").path("properties");
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
        JsonNode count = json(config, "/" + encodePath(tableName) + "/_count");
        return count.path("count").asLong(0L);
    }

    @Override
    public Long getTableSize(DataSourceConfig config, String tableName) {
        for (TableInfo table : getTablesPage(config, 1, 500, tableName).getRows()) {
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
        int size = request.getSampleSize() == null ? 20 : Math.min(request.getSampleSize(), 200);
        JsonNode result = postJson(request.getDataSource(), "/" + encodePath(request.getTableName()) + "/_search",
                "{\"size\":" + size + ",\"query\":{\"match_all\":{}},\"sort\":[\"_doc\"]}");
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
