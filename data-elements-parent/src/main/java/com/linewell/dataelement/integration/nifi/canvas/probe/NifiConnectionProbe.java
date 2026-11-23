package com.linewell.dataelement.integration.nifi.canvas.probe;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.mysql.cj.MysqlType;
import com.linewell.dataelement.integration.nifi.canvas.compile.RegisteredDatasourceType;
import com.linewell.dataelement.magic.runtime.route.RegisteredMagicApiInvoker;
import com.linewell.dataelement.metautil.explorer.impl.ElasticsearchMetadataExplorer;
import com.linewell.dataelement.metautil.explorer.impl.MongoDbMetadataExplorer;
import com.linewell.dataelement.metautil.explorer.impl.MySqlMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.metautil.jdbc.JdbcDriverPropertyResolver;
import com.linewell.dataelement.metautil.jdbc.JdbcMetadataScope;
import com.linewell.dataelement.metautil.structured.StructuredSourceProbeService;
import com.linewell.dataelement.integration.transfer.FtpsClientFactory;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.exception.MagicScriptException;
import org.ssssssss.script.exception.MagicScriptRuntimeException;
import org.ssssssss.magicapi.core.model.JsonBean;
import org.ssssssss.script.runtime.ExitValue;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.model.common.CommonResponse;

import java.io.File;
import java.io.BufferedReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Protocol probes shared by NiFi and datasource registration. Magic owns request handling. */
@Component
@MagicModule("nifiConnectionProbe")
public class NifiConnectionProbe {

    private static final ObjectMapper MAGIC_RESPONSE_MAPPER = new ObjectMapper();
    private static final int JDBC_CONNECT_TIMEOUT_SECONDS = 30;
    private static final String HIVE_GATEWAY_RESOURCE_ID = "mrs_hive_jdbc_debug_23";
    private final Map<String, Driver> driverCache = new ConcurrentHashMap<>();
    private final ElasticsearchMetadataExplorer elasticsearch = new ElasticsearchMetadataExplorer();
    private final StructuredSourceProbeService structuredSourceProbeService;
    private final RegisteredMagicApiInvoker magicApi;

    public NifiConnectionProbe(StructuredSourceProbeService structuredSourceProbeService,
                               @Lazy RegisteredMagicApiInvoker magicApi) {
        this.structuredSourceProbeService = structuredSourceProbeService;
        this.magicApi = magicApi;
    }

    public Map<String, Object> connect(String manifestKey, Map<String, Object> config) throws Exception {
        if (isMongo(manifestKey)) {
            return Map.of("success", new MongoDbMetadataExplorer().testConnection(mongoConfig(config)));
        }
        if ("source.elasticsearch".equals(manifestKey)) {
            return testElasticsearch(config);
        }
        if ("source.kafka".equals(manifestKey)) {
            return testKafka(config);
        }
        if ("source.ftp".equals(manifestKey) || "source.sftp".equals(manifestKey)) {
            return testFtp(manifestKey, config);
        }
        if ("source.api".equals(manifestKey)) {
            return testApi(config);
        }
        if ("source.hdfs".equals(manifestKey) || "source.hbase".equals(manifestKey)) {
            return testTcpReachability(manifestKey, config);
        }
        ResolvedJdbc r = resolve(manifestKey, config);
        long t0 = System.currentTimeMillis();
        Driver driver = loadProbeDriver(r, config, manifestKey);
        Properties props = new Properties();
        if (r.username != null) props.setProperty("user", r.username);
        if (r.password != null) props.setProperty("password", r.password);
        applyHuaweiMrsJdbcProperties(props, config, manifestKey);
        JdbcDriverPropertyResolver.apply(props, config);
        DriverManager.setLoginTimeout(JDBC_CONNECT_TIMEOUT_SECONDS);
        try (Connection conn = driver.connect(r.url, props)) {
            if (conn == null) {
                throw new IllegalArgumentException("Driver returned no connection (check JDBC URL or driver class)");
            }
            boolean valid = conn.isValid(15);
            String product;
            String version;
            try {
                product = conn.getMetaData().getDatabaseProductName();
                version = conn.getMetaData().getDatabaseProductVersion();
            } catch (SQLException ex) {
                product = "(unknown)";
                version = ex.getMessage();
            }
            long elapsed = System.currentTimeMillis() - t0;
            return Map.of(
                    "success", valid,
                    "url", r.url,
                    "product", product,
                    "version", version,
                    "elapsedMs", elapsed);
        }
    }

    public Map<String, Object> columns(String manifestKey, Map<String, Object> config, String table) throws Exception {
        if ("source.elasticsearch".equals(manifestKey)) {
            var columns = elasticsearch.getColumns(elasticsearchConfig(config), table);
            return Map.of("success", !columns.isEmpty(), "table", table, "columns", columns,
                    "error", columns.isEmpty() ? "索引 mapping 未定义字段" : "");
        }
        if (isMongo(manifestKey)) {
            var columns = new MongoDbMetadataExplorer().getColumns(mongoConfig(config), table);
            return Map.of("success", !columns.isEmpty(), "table", table, "columns", columns,
                    "error", columns.isEmpty() ? "集合为空或未探查到字段" : "");
        }
        // File/API/Kafka sources expose resource-directory datasets instead of JDBC metadata.
        if (isStructuredSource(manifestKey)) {
            Map<String, Object> structuredSource = toStructuredSource(manifestKey, config);
            List<ColumnInfo> columns;
            try {
                columns = structuredSourceProbeService.getColumns(structuredSource, table);
            } catch (IllegalArgumentException missingTable) {
                // API registration keeps a business/logical table name (for
                // example "apitest1"), while a JSON response is discovered
                // as a collection path (for example "api_data_records").
                // When the configured request yields one collection, it is
                // unambiguous: return its fresh fields under the registered
                // table name rather than forcing the user to replace the
                // registration name with an implementation detail.
                if (!"source.api".equals(manifestKey)) throw missingTable;
                var probe = structuredSourceProbeService.probe(structuredSource, false);
                if (probe.getTables().size() != 1) throw missingTable;
                columns = probe.getTables().get(0).getColumns();
            }
            return Map.of(
                    "success", !columns.isEmpty(),
                    "table", table,
                    "columns", columns,
                    "error", columns.isEmpty() ? "No source fields discovered" : "");
        }
        ResolvedJdbc r = resolve(manifestKey, config);
        Driver driver = loadProbeDriver(r, config, manifestKey);
        Properties props = new Properties();
        if (r.username != null) props.setProperty("user", r.username);
        if (r.password != null) props.setProperty("password", r.password);
        applyHuaweiMrsJdbcProperties(props, config, manifestKey);
        JdbcDriverPropertyResolver.apply(props, config);
        DriverManager.setLoginTimeout(JDBC_CONNECT_TIMEOUT_SECONDS);
        try (Connection conn = driver.connect(r.url, props)) {
            if (conn == null) {
                throw new IllegalArgumentException("Driver returned no connection (check JDBC URL or driver class)");
            }
            ProbeTableName name = splitTableName(table);
            if (name.schema() == null) name = new ProbeTableName(metadataSchema(conn, config, manifestKey), name.table());
            List<Map<String, Object>> columns = readColumns(conn, name);
            if (columns.isEmpty()) {
                return Map.of(
                        "success", false,
                        "table", table,
                        "url", r.url,
                        "columns", columns,
                        "error", "未探查到字段,请确认表名、Schema 或当前用户权限");
            }
            return Map.of(
                    "success", true,
                    "table", table,
                    "url", r.url,
                    "columns", columns);
        }
    }

    /** MySQL protocol read for one already authorized, server-resolved datasource. */
    public Map<String, Object> mysqlColumnsByTable(Map<String, Object> registered, List<?> tables) throws SQLException {
        if (tables.isEmpty() || tables.size() > 100) {
            throw new IllegalArgumentException("单次须探查 1 至 100 张表");
        }
        String url = str(registered.get("jdbcURL"));
        if (!"mysql".equalsIgnoreCase(str(registered.get("dbType"))) || url == null || !url.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("批量字段探查需要已登记的 MySQL 连接");
        }
        Map<String, ProbeTableName> requested = new LinkedHashMap<>();
        for (Object item : tables) {
            if (!(item instanceof String table) || table.isBlank() || table.length() > 256) {
                throw new IllegalArgumentException("表名无效");
            }
            ProbeTableName name = splitTableName(table);
            if (name.table().isBlank()) throw new IllegalArgumentException("表名无效");
            requested.putIfAbsent(table.trim(), name);
        }
        Properties properties = new Properties();
        properties.setProperty("connectTimeout", "30000");
        properties.setProperty("socketTimeout", "30000");
        if (registered.get("username") != null) properties.setProperty("user", str(registered.get("username")));
        if (registered.get("password") != null) properties.setProperty("password", str(registered.get("password")));
        try (Connection conn = new com.mysql.cj.jdbc.Driver().connect(url, properties)) {
            if (conn == null) throw new SQLException("无法连接已登记的 MySQL 数据源");
            String catalog = conn.getCatalog();
            if (catalog == null || catalog.isBlank()) throw new IllegalArgumentException("登记连接缺少数据库名");
            return mysqlCatalogColumns(conn, catalog, requested);
        }
    }

    private static Map<String, Object> mysqlCatalogColumns(Connection conn, String catalog,
                                                            Map<String, ProbeTableName> requested) throws SQLException {
        for (ProbeTableName name : requested.values()) {
            if (name.schema() != null && !catalog.equals(name.schema())) {
                throw new IllegalArgumentException("表所属数据库与所选数据源不一致");
            }
        }
        List<String> tableNames = requested.values().stream().map(ProbeTableName::table).distinct().toList();
        var columnsByTable = MySqlMetadataExplorer.getColumnsInCatalog(conn, catalog, tableNames);
        List<Map<String, Object>> results = new ArrayList<>();
        for (var entry : requested.entrySet()) {
            var columns = columnsByTable.get(entry.getValue().table());
            if (columns.isEmpty()) {
                return Map.of("success", false, "error", entry.getKey() + ": 未探查到字段，请确认表名或当前用户权限");
            }
            results.add(Map.of("success", true, "table", entry.getKey(),
                    "columns", columns.stream().map(NifiConnectionProbe::mysqlProbeColumn).toList()));
        }
        return Map.of("success", true, "results", results);
    }

    private static Map<String, Object> mysqlProbeColumn(ColumnInfo column) {
        MysqlType type = MysqlType.getByName(column.getColumnType());
        String typeName = type.getName();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columnName", column.getColumnName());
        result.put("columnComment", firstNonBlank(column.getColumnComment(), column.getColumnName()));
        result.put("dataType", jdbcTypeName(type.getJdbcType(), typeName));
        result.put("typeName", typeName);
        result.put("columnSize", column.getLength() > 0 ? column.getLength() : column.getPrecision());
        result.put("decimalDigits", column.getScale());
        result.put("nullable", column.getNullable());
        result.put("primaryKey", column.getPrimaryKey());
        result.put("ordinalPosition", column.getOrdinalPosition());
        result.put("defaultValue", column.getDefaultValue());
        return result;
    }

    public Map<String, Object> tables(String manifestKey, Map<String, Object> config, String keyword, int limit) throws Exception {
        if (isMongo(manifestKey)) {
            var search = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
            var matches = new MongoDbMetadataExplorer().getTableInventory(mongoConfig(config)).stream()
                    .filter(table -> table.getTableName().toLowerCase(Locale.ROOT).contains(search))
                    .toList();
            return Map.of("success", true, "tables", matches.subList(0, Math.min(limit, matches.size())),
                    "total", matches.size());
        }
        if (isStructuredSource(manifestKey)) {
            var page = structuredSourceProbeService.getTablesPage(
                    toStructuredSource(manifestKey, config), 1, limit, keyword);
            return Map.of(
                    "success", true,
                    "tables", page.getRows(),
                    "total", page.getTotal());
        }
        ResolvedJdbc r = resolve(manifestKey, config);
        Driver driver = loadProbeDriver(r, config, manifestKey);
        Properties props = new Properties();
        if (r.username != null) props.setProperty("user", r.username);
        if (r.password != null) props.setProperty("password", r.password);
        applyHuaweiMrsJdbcProperties(props, config, manifestKey);
        JdbcDriverPropertyResolver.apply(props, config);
        DriverManager.setLoginTimeout(JDBC_CONNECT_TIMEOUT_SECONDS);
        try (Connection conn = driver.connect(r.url, props)) {
            if (conn == null) {
                throw new IllegalArgumentException("Driver returned no connection (check JDBC URL or driver class)");
            }
            List<Map<String, Object>> tables = readTables(conn, keyword, limit, metadataSchema(conn, config, manifestKey));
            return Map.of(
                    "success", true,
                    "url", r.url,
                    "tables", tables,
                    "total", tables.size());
        }
    }

    /** Execute the bound Magic gateway using the current servlet and normalize its protocol payload. */
    public Map<String, Object> hiveGateway(Map<String, Object> request) {
        String action = str(request.get("action"));
        String database = str(request.get("database"));
        String keyword = str(request.get("keyword"));
        Integer limit = request.get("limit") instanceof Number value ? value.intValue() : null;
        Object raw = magicApi.call(HIVE_GATEWAY_RESOURCE_ID, Map.of("body", request));
        Map<String, Object> response = asMap(unwrapMagicData(raw));
        if (response == null) {
            return Map.of("success", false, "error", "华为 MRS Hive 元数据网关返回格式异常");
        }
        return switch (action) {
            case "columns" -> normalizeHuaweiMrsColumns(response);
            case "tables" -> normalizeHuaweiMrsTables(response, database, keyword, limit);
            default -> response;
        };
    }

    public Map<String, String> tableIdentifier(String table) {
        ProbeTableName name = splitTableName(table);
        return name.schema() == null ? Map.of("table", name.table()) : Map.of("schema", name.schema(), "table", name.table());
    }

    /** Unwrap script/reflective invocation errors while retaining the original protocol diagnostic. */
    public Map<String, Object> failure(Throwable error) {
        Throwable problem = error;
        while ((problem instanceof MagicScriptException || problem instanceof MagicScriptRuntimeException
                || problem instanceof InvocationTargetException) && problem.getCause() != null) {
            problem = problem.getCause();
        }
        boolean invalid = problem instanceof IllegalArgumentException;
        String message = problem.getMessage() != null ? problem.getMessage() : problem.getClass().getSimpleName();
        if (!invalid && problem.getCause() != null && problem.getCause().getMessage() != null) {
            message += " | cause: " + problem.getCause().getMessage();
        }
        return Map.of("invalid", invalid, "message", message);
    }

    static Map<String, Object> normalizeHuaweiMrsTables(Map<String, Object> response, String database,
                                                       String keyword, Integer limit) {
        Map<String, Object> normalized = new LinkedHashMap<>(response);
        if (Boolean.FALSE.equals(response.get("success"))) {
            normalized.put("tables", List.of());
            return normalized;
        }
        Object raw = response.get("tables");
        if (!(raw instanceof List<?>)) raw = response.get("rows");
        if (!(raw instanceof List<?> rows)) throw new IllegalArgumentException("Hive 表探查未返回 tables 或 rows 数组");
        Map<String, Map<String, Object>> tables = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Object row : rows) {
            Map<String, Object> item = row instanceof Map<?, ?> ? asMap(row) : null;
            String name = row instanceof String text ? str(text)
                    : item == null ? null : str(hiveColumnValue(item, "tableName", "tab_name", "table_name"));
            if (name == null) throw new IllegalArgumentException("Hive 表元数据缺少表名（tableName/tab_name）");
            if (keyword != null && !name.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) continue;
            Map<String, Object> table = item == null ? new LinkedHashMap<>() : new LinkedHashMap<>(item);
            table.put("tableName", name);
            table.putIfAbsent("schemaName", database);
            table.putIfAbsent("tableType", "TABLE");
            table.putIfAbsent("tableComment", "");
            tables.putIfAbsent(name, table);
        }
        normalized.put("tables", tables.values().stream().limit(limit == null ? 200 : limit).toList());
        normalized.put("total", tables.size());
        normalized.put("success", true);
        return normalized;
    }

    static Map<String, Object> normalizeHuaweiMrsColumns(Map<String, Object> response) {
        if (Boolean.FALSE.equals(response.get("success"))) return response;
        Object rawColumns = response.get("columns");
        if (!(rawColumns instanceof List<?>)) rawColumns = response.get("rows");
        if (!(rawColumns instanceof List<?> rows)) {
            throw new IllegalArgumentException("Hive 字段探查未返回 columns 或 rows 数组");
        }
        List<Map<String, Object>> columns = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        for (Object raw : rows) {
            Map<String, Object> row = asMap(raw);
            if (row == null) throw new IllegalArgumentException("Hive 字段元数据不是命名列结构");
            String name = str(hiveColumnValue(row, "columnName", "col_name", "column_name"));
            if (name == null) {
                if (row.values().stream().allMatch(value -> value == null || value.toString().isBlank())) continue;
                throw new IllegalArgumentException("Hive 字段元数据缺少字段名（columnName/col_name），请检查元数据网关响应");
            }
            name = name.trim();
            // DESCRIBE repeats partition columns below this section marker.
            if (name.startsWith("#")) break;
            if (!names.add(name.toLowerCase(Locale.ROOT))) continue;
            String type = str(hiveColumnValue(row, "typeName", "dataType", "data_type", "type_name"));
            if (type == null) throw new IllegalArgumentException("Hive 字段 " + name + " 缺少数据类型");
            type = type.trim();
            Map<String, Object> column = new LinkedHashMap<>();
            column.put("columnName", name);
            column.put("columnComment", hiveColumnValue(row, "columnComment", "comment", "remarks"));
            column.put("dataType", type);
            column.put("typeName", type);
            column.put("ordinalPosition", columns.size() + 1);
            column.put("columnSize", hiveColumnValue(row, "columnSize", "column_size"));
            column.put("decimalDigits", hiveColumnValue(row, "decimalDigits", "decimal_digits"));
            // Plain DESCRIBE does not report key/nullability constraints. Do not
            // invent "not a primary key" / "nullable" when metadata is unknown.
            column.put("primaryKey", hiveColumnBoolean(hiveColumnValue(row, "primaryKey")));
            column.put("nullable", hiveColumnBoolean(hiveColumnValue(row, "nullable", "is_nullable")));
            column.put("defaultValue", hiveColumnValue(row, "defaultValue", "column_def"));
            var sizedType = java.util.regex.Pattern.compile(
                    "(?i)^(?:varchar|char|decimal|numeric)\\s*\\(\\s*(\\d+)\\s*(?:,\\s*(\\d+)\\s*)?\\)$").matcher(type);
            if (sizedType.matches()) {
                column.putIfAbsent("columnSize", Integer.parseInt(sizedType.group(1)));
                if (sizedType.group(2) != null) {
                    column.putIfAbsent("decimalDigits", Integer.parseInt(sizedType.group(2)));
                }
            }
            columns.add(column);
        }
        Map<String, Object> normalized = new LinkedHashMap<>(response);
        normalized.put("columns", columns);
        normalized.put("success", !columns.isEmpty());
        if (columns.isEmpty()) normalized.put("error", "Hive 未返回有效字段，请检查元数据网关返回的字段名称和类型");
        return normalized;
    }

    private static Object hiveColumnValue(Map<String, Object> row, String... aliases) {
        for (String alias : aliases) {
            for (var entry : row.entrySet()) {
                Object value = entry.getValue();
                if (alias.equalsIgnoreCase(entry.getKey()) && value != null && !value.toString().isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private static Boolean hiveColumnBoolean(Object value) {
        if (value instanceof Boolean flag) return flag;
        if (value == null) return null;
        return switch (value.toString().trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1" -> true;
            case "false", "no", "0" -> false;
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        // MagicAPIService can return a JsonBean, a CommonResponse, a JSON string,
        // or an implementation-specific response DTO depending on the active
        // runtime. Normalize a DTO response rather than treating a successful
        // gateway result as a malformed response.
        try {
            return MAGIC_RESPONSE_MAPPER.convertValue(value, new TypeReference<Map<String, Object>>() { });
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Object unwrapMagicData(Object raw) {
        // MagicAPIService.execute evaluates a Magic Script directly.  A script
        // `return` is represented by ExitValue rather than JsonBean, and the
        // object otherwise serializes as {values, length} in Spring MVC.  The
        // canvas contract needs the single value supplied to `return`.
        if (raw instanceof ExitValue exitValue) {
            Object[] values = exitValue.getValues();
            if (values == null || values.length == 0) {
                return Map.of("success", false, "error", "华为 MRS Hive 元数据网关未返回结果");
            }
            return unwrapMagicData(values[values.length - 1]);
        }
        if (raw instanceof CharSequence text) {
            String responseText = text.toString().trim();
            try {
                return unwrapMagicData(MAGIC_RESPONSE_MAPPER.readValue(
                        responseText, new TypeReference<Map<String, Object>>() { }));
            } catch (Exception ignored) {
                // A script exception executed through MagicAPIService may be
                // returned as plain text rather than a JSON envelope.  Preserve
                // that safe server-side diagnostic for the canvas instead of
                // discarding it behind a misleading "format" error.
                return Map.of("success", false, "error", responseText.isEmpty()
                        ? "华为 MRS Hive 元数据网关未返回错误详情"
                        : responseText);
            }
        }
        Map<String, Object> envelope = asMap(raw);
        if (envelope == null) {
            return Map.of("success", false, "error", "华为 MRS Hive 元数据网关未返回结果");
        }
        Object code = envelope.get("code");
        boolean failedCode = code != null && code.toString().matches("-?\\d+")
                && !Set.of("0", "200").contains(code.toString());
        // A failed envelope may have data:null (or diagnostic data). Preserve
        // its message before unwrapping; otherwise the browser receives null
        // and loses the actual configuration/authentication/script error.
        if (Boolean.FALSE.equals(envelope.get("success")) || failedCode) {
            return hiveGatewayFailure(envelope);
        }
        if (raw instanceof JsonBean<?> response) {
            if (response.getData() != null) {
                // Magic can nest JsonBean responses (for example, when an API
                // is executed through MagicAPIService).  Returning the inner
                // JsonBean directly makes Jackson expose its implementation
                // fields such as `values`/`length` to the canvas instead of the
                // gateway contract.  Keep unwrapping until the real payload.
                return unwrapMagicData(response.getData());
            }
            return Map.of(
                    "success", false,
                    "error", firstNonBlank(response.getMessage(), "华为 MRS Hive 元数据网关未返回数据")
            );
        }
        if (raw instanceof CommonResponse<?> response) {
            if (response.getData() != null) {
                return unwrapMagicData(response.getData());
            }
            return Map.of(
                    "success", Boolean.TRUE.equals(response.getSuccess()),
                    "error", firstNonBlank(response.getMsg(), "华为 MRS Hive 元数据网关未返回数据")
            );
        }
        Object data = envelope.get("data");
        return data == null
                ? (envelope.containsKey("data") ? hiveGatewayFailure(envelope) : envelope)
                : unwrapMagicData(data);
    }

    private static Map<String, Object> hiveGatewayFailure(Map<String, Object> envelope) {
        Object rawError = envelope.get("error");
        Map<String, Object> nestedError = rawError instanceof Map<?, ?> ? asMap(rawError) : Map.of();
        String error = firstNonBlank(rawError instanceof String ? (String) rawError : null,
                str(nestedError.get("message")), str(envelope.get("msg")), str(envelope.get("message")),
                "华为 MRS Hive 元数据网关未返回有效数据");
        error = hiveGatewayDiagnostic(str(nestedError.get("detail")), error);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("error", error);
        Object errorCode = envelope.getOrDefault("errorCode", envelope.get("code"));
        if (errorCode != null) result.put("errorCode", errorCode);
        Object traceId = envelope.getOrDefault("traceId", envelope.get("requestId"));
        if (traceId != null) result.put("traceId", traceId);
        return result;
    }

    /**
     * Magic API protects its exception detail behind a generic 500 message.
     * Convert known deployment and HiveServer2 diagnostics into an operator
     * action without returning file paths, Kerberos identities or stack traces
     * to the browser.
     */
    private static String hiveGatewayDiagnostic(String detail, String fallback) {
        if (detail == null || detail.isBlank()) return fallback;
        String lower = detail.toLowerCase(Locale.ROOT);
        if (lower.contains("hive conf file not found") || lower.contains("hiveclient.properties")) {
            return "Hive 服务端 MRS 客户端配置缺失：请在后端部署节点配置 hive.conf.dir（或 /data/hive），"
                    + "并提供 hiveclient.properties、krb5.conf 及认证所需文件";
        }
        if (lower.contains("gss initiate failed") || lower.contains("krb_ap_err")
                || lower.contains("kerberos") || lower.contains("keytab")) {
            return "Hive Kerberos 认证失败：请检查后端部署节点的 krb5.conf、keytab、客户端 principal 和票据有效期";
        }
        if (lower.contains("permission denied") || lower.contains("accesscontrolexception")
                || lower.contains("authorizationexception")) {
            return "Hive 元数据权限不足：请为服务端 MRS 认证账号授予目标库和目标表的查看权限";
        }
        if (lower.contains("nosuchobjectexception") || lower.contains("table not found")
                || lower.contains("table or view not found")) {
            return "Hive 中未找到目标表：请确认所选数据库、目标表名称及大小写";
        }
        if (lower.contains("connection refused") || lower.contains("unknownhostexception")
                || lower.contains("sockettimeoutexception")) {
            return "HiveServer2/MRS 网络连接失败：请检查后端部署节点到 MRS 集群的网络和服务发现配置";
        }
        return fallback;
    }

    private static boolean isStructuredSource(String manifestKey) {
        return "source.ftp".equals(manifestKey)
                || "source.sftp".equals(manifestKey)
                || "source.api".equals(manifestKey)
                || "source.kafka".equals(manifestKey);
    }

    private static Map<String, Object> toStructuredSource(String manifestKey, Map<String, Object> config) {
        Map<String, Object> source = new LinkedHashMap<>(config == null ? Map.of() : config);
        switch (manifestKey) {
            case "source.ftp", "source.sftp" -> {
                source.put("dbType", "ftp");
                source.putIfAbsent("ftpProtocol", "source.sftp".equals(manifestKey) ? "sftp" : "ftp");
                copyIfAbsent(source, "ftpHost", "hostname", "host");
                copyIfAbsent(source, "ftpPort", "port");
                copyIfAbsent(source, "ftpUsername", "username");
                copyIfAbsent(source, "ftpPassword", "password");
                copyIfAbsent(source, "ftpPath", "remotePath", "path");
                copyIfAbsent(source, "ftpFilePattern", "fileFilterRegex", "filePattern");
            }
            case "source.api" -> source.put("dbType", "api");
            case "source.kafka" -> source.put("dbType", "kafka");
            default -> { }
        }
        return source;
    }

    private static void copyIfAbsent(Map<String, Object> target, String targetKey, String... sourceKeys) {
        Object current = target.get(targetKey);
        if (current != null && !String.valueOf(current).isBlank()) return;
        for (String sourceKey : sourceKeys) {
            Object value = target.get(sourceKey);
            if (value != null && !String.valueOf(value).isBlank()) {
                target.put(targetKey, value);
                return;
            }
        }
    }

    private static DataSourceConfig elasticsearchConfig(Map<String, Object> config) {
        var source = new DataSourceConfig();
        source.setDatabaseType(DatabaseType.ELASTICSEARCH);
        source.setDatabase(str(config.get("defaultIndex")));
        source.setUsername(str(config.get("username")));
        source.setPassword(str(config.get("password")));
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("nodes", req(config, "nodes"));
        for (String key : List.of("protocol", "authMethod", "apiKey", "token")) {
            String value = str(config.get(key));
            if (value != null && !value.isBlank()) options.put(key, value);
        }
        source.setConnectorProperties(options);
        return source;
    }

    private Map<String, Object> testElasticsearch(Map<String, Object> config) throws Exception {
        String nodes = req(config, "nodes");
        String firstNode = nodes.split(",")[0].trim();
        String protocol = str(config.getOrDefault("protocol", "http"));
        // 登记页提交完整Endpoint，画布仍可提交host:port；两种形式共用同一协议探测。
        String endpoint = firstNode.matches("(?i)^https?://.*") ? firstNode : protocol + "://" + firstNode;
        URI uri = URI.create(endpoint.endsWith("/") ? endpoint : endpoint + "/");
        long t0 = System.currentTimeMillis();
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofSeconds(8)).GET();
        String authMethod = str(config.getOrDefault("authMethod", "NONE"));
        if ("BASIC".equalsIgnoreCase(authMethod)) {
            String token = Base64.getEncoder().encodeToString((req(config, "username") + ":" + req(config, "password")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + token);
        } else if ("API_KEY".equalsIgnoreCase(authMethod)) {
            builder.header("Authorization", "ApiKey " + req(config, "apiKey"));
        }
        HttpResponse<String> resp = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build()
                .send(builder.build(), HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - t0;
        boolean ok = resp.statusCode() >= 200 && resp.statusCode() < 300;
        return Map.of(
                "success", ok,
                "url", uri.toString(),
                "product", "Elasticsearch/OpenSearch",
                "version", String.valueOf(resp.statusCode()),
                "elapsedMs", elapsed,
                "error", ok ? "" : resp.body());
    }

    private Map<String, Object> testKafka(Map<String, Object> config) throws Exception {
        long t0 = System.currentTimeMillis();
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, req(config, "bootstrapServers"));
        props.put(AdminClientConfig.CLIENT_ID_CONFIG, str(config.getOrDefault("clientId", "haitong-test")));
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "8000");
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "8000");
        String securityProtocol = str(config.getOrDefault("securityProtocol", "PLAINTEXT"));
        props.put("security.protocol", securityProtocol);
        if (securityProtocol.contains("SASL")) {
            String mechanism = str(config.getOrDefault("saslMechanism", "PLAIN"));
            props.put("sasl.mechanism", mechanism);
            if ("GSSAPI".equalsIgnoreCase(mechanism)) {
                props.put("sasl.kerberos.service.name", str(config.getOrDefault("kerberosServiceName", "kafka")));
                String principal = firstNonBlank(str(config.get("principal")), str(config.get("userPrincipal")), str(config.get("saslPrincipal")));
                String keytab = firstNonBlank(str(config.get("keytabPath")), str(config.get("keytab")), str(config.get("userKeytab")));
                if (principal != null && keytab != null) {
                    props.put("sasl.jaas.config",
                            "com.sun.security.auth.module.Krb5LoginModule required useKeyTab=true storeKey=true useTicketCache=false principal=\""
                                    + principal.replace("\\", "\\\\").replace("\"", "\\\"")
                                    + "\" keyTab=\""
                                    + keytab.replace("\\", "\\\\").replace("\"", "\\\"")
                                    + "\";");
                }
            } else {
                String module = mechanism.startsWith("SCRAM")
                        ? "org.apache.kafka.common.security.scram.ScramLoginModule"
                        : "org.apache.kafka.common.security.plain.PlainLoginModule";
                props.put("sasl.jaas.config", module + " required username=\"" + req(config, "saslUsername") + "\" password=\"" + req(config, "saslPassword") + "\";");
            }
        }
        try (AdminClient admin = AdminClient.create(props)) {
            var cluster = admin.describeCluster(new DescribeClusterOptions().timeoutMs(8000));
            String clusterId = cluster.clusterId().get(8, TimeUnit.SECONDS);
            int nodeCount = cluster.nodes().get(8, TimeUnit.SECONDS).size();
            String controller = cluster.controller().get(8, TimeUnit.SECONDS).host();
            long elapsed = System.currentTimeMillis() - t0;
            return Map.of(
                    "success", true,
                    "product", "Kafka",
                    "version", "clusterId=" + clusterId + ", nodes=" + nodeCount + ", controller=" + controller,
                    "elapsedMs", elapsed);
        }
    }

    private Map<String, Object> testFtp(String manifestKey, Map<String, Object> config) throws Exception {
        String protocol = str(config.getOrDefault("protocol", config.getOrDefault(
                "ftpProtocol", "source.sftp".equals(manifestKey) ? "sftp" : "ftp")));
        String host = firstNonBlank(str(config.get("hostname")), str(config.get("ftpHost")), str(config.get("host")));
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("缺少必填字段: hostname");
        }
        int defaultPort = "sftp".equalsIgnoreCase(protocol) ? 22
                : "ftps".equalsIgnoreCase(protocol) && "true".equalsIgnoreCase(str(config.get("ftpsImplicit"))) ? 990 : 21;
        int port = parseInt(config.getOrDefault("port", config.getOrDefault("ftpPort", defaultPort)), defaultPort);
        if ("sftp".equalsIgnoreCase(protocol)) {
            return testSftp(config, host, port);
        }
        if ("ftps".equalsIgnoreCase(protocol)) {
            return testFtps(config, host, port);
        }
        long t0 = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 8000);
            socket.setSoTimeout(8000);
            BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
            String welcome = reader.readLine();
            String username = firstNonBlank(str(config.get("username")), str(config.get("ftpUsername")));
            String password = firstNonBlank(str(config.get("password")), str(config.get("ftpPassword")));
            String reply = welcome;
            if (username != null && !username.isBlank()) {
                writer.write("USER " + username + "\r\n");
                writer.flush();
                reply = reader.readLine();
                if (password != null && !password.isBlank()) {
                    writer.write("PASS " + password + "\r\n");
                    writer.flush();
                    reply = reader.readLine();
                }
            }
            long elapsed = System.currentTimeMillis() - t0;
            boolean success = reply != null && (reply.startsWith("2") || reply.startsWith("3"));
            return Map.of(
                    "success", success,
                    "url", protocol + "://" + host + ":" + port,
                    "product", protocol.toUpperCase(Locale.ROOT),
                    "version", firstNonBlank(reply, welcome, ""),
                    "elapsedMs", elapsed,
                    "error", success ? "" : firstNonBlank(reply, "FTP 连接失败"));
        }
    }

    private Map<String, Object> testFtps(Map<String, Object> config, String host, int port) throws Exception {
        String username = firstNonBlank(str(config.get("username")), str(config.get("ftpUsername")));
        String password = firstNonBlank(str(config.get("password")), str(config.get("ftpPassword")), "");
        if (username == null) throw new IllegalArgumentException("缺少必填字段: username");
        var client = FtpsClientFactory.create(config, port);
        client.setConnectTimeout(8000);
        client.setDefaultTimeout(8000);
        long started = System.currentTimeMillis();
        try {
            // FTPSClient negotiates and verifies TLS before any USER/PASS command.
            client.connect(host, port);
            if (!client.login(username, password)) throw new IllegalArgumentException("FTPS 认证失败");
            client.execPBSZ(0);
            client.execPROT("P");
            String path = firstNonBlank(str(config.get("remotePath")), str(config.get("path")), str(config.get("ftpPath")), "/");
            if (!client.changeWorkingDirectory(path)) throw new IllegalArgumentException("FTPS 路径不存在或不可访问");
            client.logout();
            return Map.of("success", true, "url", "ftps://" + host + ":" + port,
                    "product", "FTPS", "version", "TLS / PROT P", "error", "",
                    "elapsedMs", System.currentTimeMillis() - started);
        } finally {
            if (client.isConnected()) client.disconnect();
        }
    }

    private Map<String, Object> testSftp(Map<String, Object> config, String host, int port) throws Exception {
        long t0 = System.currentTimeMillis();
        String username = firstNonBlank(str(config.get("username")), str(config.get("ftpUsername")));
        String password = firstNonBlank(str(config.get("password")), str(config.get("ftpPassword")));
        String path = firstNonBlank(str(config.get("path")), str(config.get("ftpPath")), "/");
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("缺少必填字段: username");
        }
        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = jsch.getSession(username, host, port);
            if (password != null) {
                session.setPassword(password);
            }
            Properties props = new Properties();
            props.put("StrictHostKeyChecking", "no");
            session.setConfig(props);
            session.connect(8000);
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(8000);
            if (path != null && !path.isBlank()) {
                channel.lstat(path);
            }
            long elapsed = System.currentTimeMillis() - t0;
            return Map.of(
                    "success", true,
                    "url", "sftp://" + host + ":" + port,
                    "product", "SFTP",
                    "version", "connected, path=" + firstNonBlank(path, "/"),
                    "elapsedMs", elapsed,
                    "error", "");
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private Map<String, Object> testApi(Map<String, Object> config) throws Exception {
        String url = firstNonBlank(str(config.get("url")), str(config.get("apiUrl")));
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("缺少必填字段: url");
        }
        // Reuse the structured probe rather than keeping a second HTTP client
        // implementation here.  API registration stores headers under
        // commonHeadersJson, while the generic probe also supports apiHeaders;
        // using the shared path guarantees that “测试连接” and “探查字段” make
        // the exact same request as the registered source contract.
        Map<String, Object> source = new LinkedHashMap<>(config);
        source.put("dbType", "api");
        long t0 = System.currentTimeMillis();
        var probe = structuredSourceProbeService.probe(source, true);
        long elapsed = System.currentTimeMillis() - t0;
        return Map.of(
                "success", true,
                "url", probe.getEndpoint(),
                "product", probe.getProduct(),
                "version", probe.getTables().size() + " 张数据表",
                "elapsedMs", elapsed,
                "error", "");
    }

    private Map<String, Object> testTcpReachability(String manifestKey, Map<String, Object> config) throws Exception {
        String host = firstNonBlank(str(config.get("host")), str(config.get("hostname")), str(config.get("zookeeperQuorum")));
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("缂哄皯蹇呭～瀛楁: host");
        }
        String firstHost = host.split(",", 2)[0].trim();
        if (firstHost.contains(":")) {
            String[] parts = firstHost.split(":", 2);
            firstHost = parts[0];
            config.putIfAbsent("port", parts[1]);
        }
        int defaultPort = "source.hbase".equals(manifestKey) ? 2181 : 8020;
        int port = parseInt(config.getOrDefault("port", defaultPort), defaultPort);
        long t0 = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(firstHost, port), 8000);
            long elapsed = System.currentTimeMillis() - t0;
            return Map.of(
                    "success", true,
                    "url", firstHost + ":" + port,
                    "product", "source.hbase".equals(manifestKey) ? "Huawei MRS HBase/ZooKeeper" : "Huawei MRS HDFS",
                    "version", "tcp reachable",
                    "elapsedMs", elapsed,
                    "error", "");
        }
    }

    private Driver loadProbeDriver(ResolvedJdbc jdbc, Map<String, Object> config, String manifestKey) throws Exception {
        if (("source.hive".equals(manifestKey) || "sink.hive".equals(manifestKey))
                && "open-source".equalsIgnoreCase(str(config.get("hiveConnectionMode")))) {
            return com.linewell.dataelement.metautil.explorer.AbstractMetadataExplorer.loadOpenSourceHiveDriver();
        }
        return loadDriver(jdbc.driverClass, jdbc.driverPath);
    }

    private Driver loadDriver(String driverClass, String driverPath) throws Exception {
        if (driverPath == null || driverPath.isBlank()) {
            try {
                // Prefer a bundled driver when the dependency is part of the backend artifact.
                return (Driver) Class.forName(driverClass).getDeclaredConstructor().newInstance();
            } catch (ClassNotFoundException missingBundledDriver) {
                // Vendor JDBC drivers are often supplied beside the service instead of Maven.
                File managedDriver = findManagedDriver(driverClass);
                if (managedDriver == null) {
                    throw new IllegalArgumentException(missingDriverMessage(driverClass), missingBundledDriver);
                }
                return loadDriver(driverClass, managedDriver.getAbsolutePath());
            }
        }
        File jar = new File(driverPath);
        if (!jar.isFile()) {
            throw new IllegalArgumentException("JDBC 驱动文件不存在: " + driverPath
                    + "(请检查后端服务器上的实际路径,该路径不是 NiFi 容器内的路径)");
        }
        String cacheKey = jar.getAbsolutePath() + "|" + driverClass;
        Driver cached = driverCache.get(cacheKey);
        if (cached != null) return cached;
        URLClassLoader cl = new URLClassLoader(
                new URL[]{jar.toURI().toURL()},
                NifiConnectionProbe.class.getClassLoader());
        Class<?> clazz = Class.forName(driverClass, true, cl);
        Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
        driverCache.put(cacheKey, driver);
        return driver;
    }

    /**
     * Locate a managed vendor driver for backend-side probing. The NiFi container has
     * its own mounted directory and must receive the same driver separately.
     */
    private File findManagedDriver(String driverClass) {
        String fileName = switch (driverClass) {
            case "com.gbase.jdbc.Driver" -> "gbase-connector-java.jar";
            case "com.gbasedbt.jdbc.IfxDriver" -> "gbasedbtjdbc.jar";
            case "com.huawei.opengauss.jdbc.Driver" -> "opengaussjdbc.jar";
            case "com.huawei.gaussdb.jdbc.Driver" -> "gaussdbjdbc.jar";
            case "org.opengauss.Driver" -> "opengauss-jdbc.jar";
            case "com.vertica.jdbc.Driver" -> "vertica-jdbc.jar";
            default -> null;
        };
        if (fileName == null) {
            return null;
        }
        Path workDir = Path.of(System.getProperty("user.dir"));
        String configuredDirectory = System.getProperty("data-elements.jdbc.driver-directory", System.getenv("JDBC_DRIVER_DIRECTORY"));
        List<Path> candidates = List.of(
                Path.of(configuredDirectory == null || configuredDirectory.isBlank() ? workDir.toString() : configuredDirectory).resolve(fileName),
                workDir.resolve("deploy/nifi/drivers").resolve(fileName),
                workDir.resolve("data-elements/deploy/nifi/drivers").resolve(fileName));
        return candidates.stream()
                .filter(Files::isRegularFile)
                .map(Path::toFile)
                .findFirst()
                .orElse(null);
    }

    private String missingDriverMessage(String driverClass) {
        if ("com.gbase.jdbc.Driver".equals(driverClass)) {
            return "未找到 GBase 8a JDBC 驱动 " + driverClass
                    + "。请将官方驱动部署为 data-elements/deploy/nifi/drivers/gbase-connector-java.jar，"
                    + "并在 NiFi 容器中挂载为 /opt/nifi/nifi-current/lib/jdbc/gbase-connector-java.jar。";
        }
        if ("com.gbasedbt.jdbc.IfxDriver".equals(driverClass)) {
            return "未找到 GBase 8s JDBC 驱动 " + driverClass
                    + "。请将驱动部署为 data-elements/deploy/nifi/drivers/gbasedbtjdbc.jar，"
                    + "并在 NiFi 容器中挂载为 /opt/nifi/nifi-current/lib/jdbc/gbasedbtjdbc.jar。";
        }
        return "未找到 JDBC 驱动 " + driverClass;
    }

    private ResolvedJdbc resolve(String manifestKey, Map<String, Object> c) {
        ResolvedJdbc r = new ResolvedJdbc();
        r.username = str(c.get("username"));
        r.password = str(c.get("password"));
        // The node's driverPath belongs to the NiFi container. Backend probe drivers
        // are resolved from the service classpath or deploy/nifi/drivers instead.
        r.driverPath = null;

        String jdbcManifest = manifestKey == null ? "" : manifestKey;
        if (("source.oceanbase".equals(jdbcManifest) || "source.mysql".equals(jdbcManifest))
                && RegisteredDatasourceType.isOceanBaseMysqlSource(jdbcManifest, c)) {
            r.url = RegisteredDatasourceType.oceanBaseMysqlSourceUrl(c);
            r.driverClass = driverClassForDbType("OCEANBASE_MYSQL", r.url);
            return r;
        }
        switch (jdbcManifest) {
            case "source.hive", "sink.hive" -> {
                r.url = firstNonBlank(str(c.get("jdbcUrl")), str(c.get("jdbcURL")));
                if (r.url == null) r.url = buildHiveJdbcUrl(c);
                r.driverClass = "org.apache.hive.jdbc.HiveDriver";
            }
            case "source.hetu" -> {
                r.url = firstNonBlank(str(c.get("jdbcUrl")), buildHetuJdbcUrl(c));
                r.driverClass = "io.trino.jdbc.TrinoDriver";
            }
            case "source.doris" -> {
                r.url = firstNonBlank(str(c.get("jdbcUrl")), buildMysqlCompatibleMrsUrl(c, 9030, true));
                r.driverClass = "com.mysql.cj.jdbc.Driver";
            }
            case "source.starrocks" -> {
                r.url = firstNonBlank(str(c.get("jdbcUrl")), buildMysqlCompatibleMrsUrl(c, 9030, false));
                r.driverClass = "com.mysql.cj.jdbc.Driver";
            }
            case "source.iotdb" -> {
                r.url = firstNonBlank(str(c.get("jdbcUrl")), "jdbc:iotdb://" + req(c, "host") + ":" + c.getOrDefault("port", 22260) + "/");
                r.driverClass = "org.apache.iotdb.jdbc.IoTDBDriver";
            }
            case "source.mysql" -> {
                String host = req(c, "host");
                Object port = c.getOrDefault("port", 3306);
                String db = req(c, "database");
                String timezone = str(c.getOrDefault("serverTimezone", "Asia/Shanghai"));
                String useSsl = str(c.getOrDefault("useSSL", false));
                r.url = "jdbc:mysql://" + host + ":" + port + "/" + db
                    + "?useSSL=" + useSsl + "&serverTimezone=" + timezone + "&connectTimeout=30000&socketTimeout=30000&allowPublicKeyRetrieval=true";
                r.driverClass = "com.mysql.cj.jdbc.Driver";
            }
            case "source.mariadb" -> {
                String host = req(c, "host");
                Object port = c.getOrDefault("port", 3306);
                String db = req(c, "database");
                r.url = "jdbc:mariadb://" + host + ":" + port + "/" + db
                    + "?connectTimeout=30000&socketTimeout=30000&useSsl=" + str(c.getOrDefault("useSSL", false));
                r.driverClass = "org.mariadb.jdbc.Driver";
            }
            case "source.oracle" -> {
                String host = req(c, "host");
                Object port = c.getOrDefault("port", 1521);
                String sid = req(c, "sid");
                // service-name style URL works for both SID and Service Name on modern drivers
                r.url = "jdbc:oracle:thin:@//" + host + ":" + port + "/" + sid;
                r.driverClass = "oracle.jdbc.OracleDriver";
            }
            case "source.gaussdb" -> {
                r.url = com.linewell.dataelement.integration.nifi.canvas.compile.RegisteredDatasourceType.jdbcUrl("gaussdb", c, Map.of());
                r.driverClass = com.linewell.dataelement.metautil.jdbc.GaussDbVariant.from(c).driverClass();
            }
            case "source.vertica" -> {
                r.url = com.linewell.dataelement.integration.nifi.canvas.compile.RegisteredDatasourceType.jdbcUrl("vertica", c, Map.of());
                r.driverClass = "com.vertica.jdbc.Driver";
            }
            case "source.postgresql", "source.tdsql-pg", "source.hailiang" -> {
                String host = req(c, "host");
                // GaussDB/openGauss defaults to the PostgreSQL-compatible port.
                // DWS deployments using 8000 remain supported when explicitly configured.
                Object port = c.getOrDefault("port", manifestKey.endsWith("tdsql-pg") ? 15432 : 5432);
                String db = req(c, "database");
                String schema = str(c.getOrDefault("currentSchema", "public"));
                String sslMode = str(c.getOrDefault("sslMode", "disable"));
                r.url = "jdbc:postgresql://" + host + ":" + port + "/" + db
                        + "?currentSchema=" + schema + "&ApplicationName=haitong&connectTimeout=30&socketTimeout=30&sslmode=" + sslMode;
                r.driverClass = "org.postgresql.Driver";
            }
            case "source.sqlserver" -> {
                String host = req(c, "host");
                Object port = c.getOrDefault("port", 1433);
                String db = req(c, "database");
                r.url = "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + db
                        + ";encrypt=" + str(c.getOrDefault("encrypt", true))
                        + ";trustServerCertificate=" + str(c.getOrDefault("trustServerCertificate", false))
                        + ";loginTimeout=30;socketTimeout=30000";
                r.driverClass = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            }
            case "source.db2" -> {
                String host = req(c, "host");
                Object port = c.getOrDefault("port", 50000);
                String db = req(c, "database");
                String schema = str(c.get("currentSchema"));
                r.url = "jdbc:db2://" + host + ":" + port + "/" + db
                        + (schema != null && !schema.isBlank() ? ":currentSchema=" + schema + ";" : ":;");
                r.driverClass = "com.ibm.db2.jcc.DB2Driver";
            }
            case "source.dameng" -> {
                String host = req(c, "host");
                Object port = c.getOrDefault("port", 5236);
                String database = req(c, "database");
                String schema = str(c.get("defaultSchema"));
                String mode = str(c.getOrDefault("compatibleMode", "ORACLE"));
                r.url = "jdbc:dm://" + host + ":" + port + "/" + database
                        + "?compatibleMode=" + mode
                        + (schema != null && !schema.isBlank() ? "&schema=" + schema : "");
                r.driverClass = "dm.jdbc.driver.DmDriver";
            }
            case "source.kingbase" -> {
                String host = req(c, "host");
                Object port = c.getOrDefault("port", 54321);
                String db = req(c, "database");
                String schema = str(c.get("defaultSchema"));
                r.url = "jdbc:kingbase8://" + host + ":" + port + "/" + db
                        + (schema != null && !schema.isBlank() ? "?currentSchema=" + schema : "");
                r.driverClass = "com.kingbase8.Driver";
            }
            case "source.gbase8a" -> {
                String host = req(c, "host");
                Object port = c.getOrDefault("port", 5258);
                String db = req(c, "database");
                r.url = "jdbc:gbase://" + host + ":" + port + "/" + db + "?characterEncoding=utf8";
                r.driverClass = "com.gbase.jdbc.Driver";
            }
            case "source.gbase8s" -> {
                String host = req(c, "host");
                Object port = c.getOrDefault("port", 9088);
                String db = req(c, "database");
                String serverName = req(c, "serverName");
                r.url = "jdbc:gbasedbt-sqli://" + host + ":" + port + "/" + db
                        + ":GBASEDBTSERVER=" + serverName
                        + ";DB_LOCALE=" + str(c.getOrDefault("dbLocale", "zh_CN.utf8"))
                        + ";CLIENT_LOCALE=" + str(c.getOrDefault("clientLocale", "zh_CN.utf8"));
                r.driverClass = "com.gbasedbt.jdbc.IfxDriver";
            }
            case "source.oscar" -> {
                String host = req(c, "host");
                Object port = c.getOrDefault("port", 2003);
                String db = req(c, "database");
                r.url = "jdbc:oscar://" + host + ":" + port + "/" + db;
                r.driverClass = "com.oscar.Driver";
            }
            case "source.highgo" -> {
                String host = req(c, "host");
                Object port = c.getOrDefault("port", 5866);
                String db = req(c, "database");
                String schema = str(c.get("defaultSchema"));
                r.url = "jdbc:highgo://" + host + ":" + port + "/" + db
                        + (schema != null && !schema.isBlank() ? "?currentSchema=" + schema : "");
                r.driverClass = "com.highgo.jdbc.Driver";
            }
            case "source.tdsql-mysql" -> {
                String host = req(c, "host");
                Object port = c.getOrDefault("port", 3306);
                String db = req(c, "database");
                r.url = "jdbc:mysql://" + host + ":" + port + "/" + db
                        + "?useSSL=false&serverTimezone=Asia/Shanghai&connectTimeout=30000&socketTimeout=30000&allowPublicKeyRetrieval=true";
                r.driverClass = "com.mysql.cj.jdbc.Driver";
            }
            case "source.oceanbase" -> {
                String dbType = oceanBaseType(c);
                String configuredUrl = firstNonBlank(str(c.get("jdbcUrl")), str(c.get("jdbcURL")));
                if (configuredUrl != null) {
                    r.url = normalizeSinkJdbcUrl(configuredUrl, dbType);
                } else {
                    String host = req(c, "host");
                    Object port = c.getOrDefault("port", 2881);
                    String db = req(c, "database");
                    r.url = ("OCEANBASE_ORACLE".equals(dbType) || "OCEANBASEORACLE".equals(dbType))
                            ? "jdbc:oceanbase:oracle://" + host + ":" + port + "/" + db
                            : RegisteredDatasourceType.oceanBaseMysqlSourceUrl(c);
                }
                r.driverClass = driverClassForDbType(dbType, r.url);
            }
            case "source.clickhouse" -> {
                String host = req(c, "host");
                Object port = c.getOrDefault("port", 8123);
                String db = req(c, "database");
                r.url = firstNonBlank(str(c.get("jdbcUrl")),
                        "jdbc:clickhouse://" + host + ":" + port + "/" + db + "?compress=1");
                r.driverClass = "com.clickhouse.jdbc.ClickHouseDriver";
            }
            case "sink.jdbc" -> {
                String dbType = str(c.getOrDefault("dbType", "MYSQL"));
                if (dbType.toUpperCase(Locale.ROOT).startsWith("OCEANBASE")) dbType = oceanBaseType(c);
                r.url = normalizeSinkJdbcUrl(req(c, "jdbcUrl"), dbType);
                r.driverClass = driverClassForDbType(dbType, r.url);
            }
            default -> throw new IllegalArgumentException("不支持测试连接的组件: " + manifestKey);
        }
        if (manifestKey.startsWith("source.")) r.url = com.linewell.dataelement.metautil.jdbc.MySqlIntegerReadPolicy.url(r.url);
        return r;
    }

    static String oceanBaseType(Map<String, Object> config) {
        String type = String.valueOf(config.getOrDefault("dbType", "")).replace("_", "").replace("-", "");
        String url = firstNonBlank(str(config.get("jdbcUrl")), str(config.get("jdbcURL")), "");
        return "OCEANBASEORACLE".equalsIgnoreCase(type)
                || "ORACLE".equalsIgnoreCase(str(config.get("compatibleMode")))
                || url.toLowerCase(Locale.ROOT).startsWith("jdbc:oceanbase:oracle:")
                || url.toLowerCase(Locale.ROOT).startsWith("jdbc:oceanbaseoracle:")
                || url.toLowerCase(Locale.ROOT).startsWith("jdbc:oracle:")
                ? "OCEANBASE_ORACLE" : "OCEANBASE_MYSQL";
    }

    static String metadataSchema(Connection connection, Map<String, Object> config, String manifestKey)throws SQLException {
        return JdbcMetadataScope.schema(connection,config,manifestKey);
    }

    private static String normalizeSinkJdbcUrl(String url, String dbType) {
        String type = dbType == null ? "MYSQL" : dbType.trim().toUpperCase(java.util.Locale.ROOT);
        if ("OCEANBASE_ORACLE".equals(type) || "OCEANBASEORACLE".equals(type)) {
            if (url.regionMatches(true, 0, "jdbc:oceanbaseoracle://", 0, "jdbc:oceanbaseoracle://".length())) {
                return "jdbc:oceanbase:oracle://" + url.substring("jdbc:oceanbaseoracle://".length());
            }
            return url;
        }
        if (("OCEANBASE_MYSQL".equals(type) || "OCEANBASEMYSQL".equals(type))
                && url.regionMatches(true, 0, "jdbc:oceanbasemysql://", 0, "jdbc:oceanbasemysql://".length())) {
            url = "jdbc:mysql://" + url.substring("jdbc:oceanbasemysql://".length());
        }
        if (!isMysqlCompatible(type, url)) return url;
        String normalized = appendJdbcParamIfMissing(url, "useSSL", "false");
        normalized = appendJdbcParamIfMissing(normalized, "serverTimezone", "Asia/Shanghai");
        normalized = appendJdbcParamIfMissing(normalized, "connectTimeout", "30000");
        normalized = appendJdbcParamIfMissing(normalized, "socketTimeout", "30000");
        normalized = appendJdbcParamIfMissing(normalized, "allowPublicKeyRetrieval", "true");
        return normalized;
    }

    static String buildHiveJdbcUrl(Map<String, Object> c) {
        if ("open-source".equalsIgnoreCase(str(c.get("hiveConnectionMode")))) {
            return com.linewell.dataelement.integration.nifi.canvas.compile.RegisteredDatasourceType.jdbcUrl("hive", c, Map.of());
        }
        String zkQuorum = firstNonBlank(str(c.get("zookeeperQuorum")), str(c.get("zkQuorum")));
        String db = firstNonBlank(str(c.get("database")), "default");
        StringBuilder url = new StringBuilder("jdbc:hive2://");
        if (zkQuorum != null) {
            url.append(zkQuorum).append("/").append(db);
        } else {
            url.append(req(c, "host")).append(":").append(c.getOrDefault("port", 10000)).append("/").append(db);
        }
        String authMode = firstNonBlank(str(c.get("authMode")), str(c.get("auth")), "none");
        String serviceDiscovery = firstNonBlank(str(c.get("serviceDiscoveryMode")), zkQuorum == null ? null : "zooKeeper");
        String namespace = firstNonBlank(str(c.get("zookeeperNamespace")), str(c.get("zooKeeperNamespace")), zkQuorum == null ? null : "hiveserver2");
        if (serviceDiscovery != null) url.append(";serviceDiscoveryMode=").append(serviceDiscovery);
        if (namespace != null) url.append(";zooKeeperNamespace=").append(namespace);
        if ("KERBEROS".equalsIgnoreCase(authMode)) {
            url.append(";auth=KERBEROS");
            appendSemicolonParam(url, "sasl.qop", firstNonBlank(str(c.get("saslQop")), str(c.get("sasl.qop"))));
            appendSemicolonParam(url, "principal", str(c.get("principal")));
            appendSemicolonParam(url, "ssl", str(c.get("ssl")));
            appendSemicolonParam(url, "user.principal", firstNonBlank(str(c.get("userPrincipal")), str(c.get("clientPrincipal"))));
            appendSemicolonParam(url, "user.keytab", firstNonBlank(str(c.get("keytabPath")), str(c.get("keytab"))));
        } else if (zkQuorum != null) {
            url.append(";auth=none");
        }
        String extra = str(c.get("extraParams"));
        if (extra != null && !extra.isBlank()) {
            url.append(extra.startsWith(";") ? extra : ";" + extra.replace("&", ";"));
        }
        return url.toString();
    }

    private static String buildHetuJdbcUrl(Map<String, Object> c) {
        String host = req(c, "host");
        Object port = c.getOrDefault("port", 29861);
        String catalogSchema = firstNonBlank(str(c.get("database")), "hive/default");
        String url = "jdbc:trino://" + host + ":" + port + "/" + catalogSchema;
        String serviceDiscovery = firstNonBlank(str(c.get("serviceDiscoveryMode")), str(c.get("serviceDiscovery")));
        if (serviceDiscovery != null) {
            url = appendJdbcParamIfMissing(url, "serviceDiscoveryMode", serviceDiscovery);
        }
        return url;
    }

    private static String buildMysqlCompatibleMrsUrl(Map<String, Object> c, int defaultPort, boolean doris) {
        String host = req(c, "host");
        Object port = c.getOrDefault("port", defaultPort);
        String db = firstNonBlank(str(c.get("database")), "");
        String url = "jdbc:mysql://" + host + ":" + port + (db.isBlank() ? "" : "/" + db);
        url = appendJdbcParamIfMissing(url, "characterEncoding", "utf-8");
        url = appendJdbcParamIfMissing(url, "serverTimezone", "Asia/Shanghai");
        url = appendJdbcParamIfMissing(url, "useSSL", "false");
        if (doris) {
            url = appendJdbcParamIfMissing(url, "rewriteBatchedStatements", "true");
        }
        return url;
    }

    private static void appendSemicolonParam(StringBuilder url, String key, String value) {
        if (value != null && !value.isBlank()) {
            url.append(";").append(key).append("=").append(value.trim());
        }
    }

    private static void applyHuaweiMrsJdbcProperties(Properties props, Map<String, Object> config, String manifestKey) {
        if (manifestKey == null || !Set.of("source.hive", "source.hetu", "source.iotdb", "source.clickhouse").contains(manifestKey)) {
            return;
        }
        com.linewell.dataelement.metautil.model.dto.DataSourceConfig dataSourceConfig =
                new com.linewell.dataelement.metautil.model.dto.DataSourceConfig();
        dataSourceConfig.setDatabaseType(switch (manifestKey) {
            case "source.hive" -> com.linewell.dataelement.metautil.model.enums.DatabaseType.HIVE;
            case "source.hetu" -> com.linewell.dataelement.metautil.model.enums.DatabaseType.HETU;
            case "source.iotdb" -> com.linewell.dataelement.metautil.model.enums.DatabaseType.IOTDB;
            case "source.clickhouse" -> com.linewell.dataelement.metautil.model.enums.DatabaseType.CLICKHOUSE;
            default -> com.linewell.dataelement.metautil.model.enums.DatabaseType.MYSQL;
        });
        dataSourceConfig.setUsername(str(config.get("username")));
        dataSourceConfig.setPassword(str(config.get("password")));
        dataSourceConfig.setAuthMode(firstNonBlank(str(config.get("authMode")), str(config.get("auth"))));
        dataSourceConfig.setPrincipal(str(config.get("principal")));
        dataSourceConfig.setUserPrincipal(firstNonBlank(str(config.get("userPrincipal")), str(config.get("clientPrincipal"))));
        dataSourceConfig.setKeytabPath(firstNonBlank(str(config.get("keytabPath")), str(config.get("keytab"))));
        dataSourceConfig.setKrb5ConfPath(firstNonBlank(str(config.get("krb5ConfPath")), str(config.get("krb5Conf"))));
        dataSourceConfig.setJaasConfPath(firstNonBlank(str(config.get("jaasConfPath")), str(config.get("jaasConfig"))));
        dataSourceConfig.setClientConfigDir(str(config.get("clientConfigDir")));
        dataSourceConfig.setSsl(parseBoolean(config.get("ssl")));
        dataSourceConfig.setTrustStorePath(firstNonBlank(str(config.get("trustStorePath")), str(config.get("iotdbSslTruststore"))));
        if (dataSourceConfig.getUsername() != null && !dataSourceConfig.getUsername().isBlank()) {
            props.setProperty("user", dataSourceConfig.getUsername());
        }
        if (dataSourceConfig.getPassword() != null && !dataSourceConfig.getPassword().isBlank()) {
            props.setProperty("password", dataSourceConfig.getPassword());
        }
        if (dataSourceConfig.getDatabaseType()
                == com.linewell.dataelement.metautil.model.enums.DatabaseType.HETU
                && dataSourceConfig.getSsl() != null) {
            props.setProperty("SSL", String.valueOf(dataSourceConfig.getSsl()));
        }
    }

    private static Boolean parseBoolean(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(raw));
    }

    private static boolean isMysqlCompatible(String dbType, String url) {
        String lower = url == null ? "" : url.toLowerCase(java.util.Locale.ROOT);
        return dbType == null
                || dbType.isBlank()
                || "MYSQL".equals(dbType)
                || "MARIADB".equals(dbType)
                || "TDSQL_MYSQL".equals(dbType)
                || "OCEANBASE".equals(dbType)
                || "OCEANBASE_MYSQL".equals(dbType)
                || "OCEANBASEMYSQL".equals(dbType)
                || lower.startsWith("jdbc:mysql:")
                || lower.startsWith("jdbc:mariadb:")
                || lower.startsWith("jdbc:oceanbase:");
    }

    private static String appendJdbcParamIfMissing(String url, String key, String value) {
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        if (lower.matches(".*([?&])" + java.util.regex.Pattern.quote(key.toLowerCase(java.util.Locale.ROOT)) + "=.*")) {
            return url;
        }
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + key + "=" + value;
    }

    private static boolean isMongo(String manifestKey) {
        return "source.mongodb".equals(manifestKey) || "sink.mongodb".equals(manifestKey);
    }

    private static DataSourceConfig mongoConfig(Map<String, Object> options) {
        var config = new DataSourceConfig();
        config.setDatabaseType(DatabaseType.MONGODB);
        config.setHost(str(options.get("host")));
        String port = str(options.get("port"));
        if (port != null && !port.isBlank()) config.setPort(Integer.valueOf(port));
        String database = str(options.get("database"));
        if (database == null || database.isBlank()) throw new IllegalArgumentException("请填写 MongoDB 数据库名");
        config.setDatabase(database);
        config.setUsername(str(options.get("username")));
        config.setPassword(str(options.get("password")));
        config.setConnectorProperties(options);
        return config;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static int parseInt(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static ProbeTableName splitTableName(String raw) {
        String text = raw == null ? "" : raw.trim();
        int dot = text.indexOf('.');
        if (dot > 0 && dot < text.length() - 1) {
            return new ProbeTableName(unquoteIdentifier(text.substring(0, dot)),
                    unquoteIdentifier(text.substring(dot + 1)));
        }
        return new ProbeTableName(null, unquoteIdentifier(text));
    }

    private static String unquoteIdentifier(String value) {
        String text = value == null ? "" : value.trim();
        while (text.length() >= 2) {
            char first = text.charAt(0);
            char last = text.charAt(text.length() - 1);
            if ((first == '`' && last == '`') || (first == '"' && last == '"') || (first == '[' && last == ']')) {
                text = text.substring(1, text.length() - 1).trim();
            } else {
                break;
            }
        }
        return text;
    }

    private static List<Map<String, Object>> readColumns(Connection conn, ProbeTableName name) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = safeCatalog(conn);
        Set<String> primaryKeys = readPrimaryKeys(meta, catalog, name);
        List<Map<String, Object>> columns = queryColumns(meta, catalog, name);
        if (columns.isEmpty()) {
            ProbeTableName upper = new ProbeTableName(
                    name.schema() == null ? null : name.schema().toUpperCase(Locale.ROOT),
                    name.table().toUpperCase(Locale.ROOT));
            primaryKeys = readPrimaryKeys(meta, catalog, upper);
            columns = queryColumns(meta, catalog, upper);
        }
        for (Map<String, Object> column : columns) {
            String columnName = str(column.get("columnName"));
            column.put("primaryKey", primaryKeys == null ? null : primaryKeys.contains(columnName));
        }
        return columns;
    }

    private static List<Map<String, Object>> readTables(Connection conn, String keyword, int limit, String schema) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = safeCatalog(conn);
        String pattern = keyword == null || keyword.isBlank() ? "%" : "%" + keyword.trim() + "%";
        LinkedHashMap<String, Map<String, Object>> map = new LinkedHashMap<>();
        queryTables(meta, catalog, schema, pattern, map, limit);
        if (map.isEmpty() && keyword != null && !keyword.isBlank()) {
            queryTables(meta, catalog, schema, pattern.toUpperCase(Locale.ROOT), map, limit);
        }
        List<Map<String, Object>> list = new ArrayList<>(map.values());
        list.sort((a, b) -> String.valueOf(a.get("tableName")).compareToIgnoreCase(String.valueOf(b.get("tableName"))));
        return list;
    }

    private static void queryTables(DatabaseMetaData meta, String catalog, String schema, String pattern,
                                    LinkedHashMap<String, Map<String, Object>> map, int limit) throws SQLException {
        try (ResultSet rs = meta.getTables(catalog, schema, pattern, new String[]{"TABLE", "VIEW"})) {
            while (rs.next() && map.size() < limit) {
                String tableName = rs.getString("TABLE_NAME");
                if (tableName == null || tableName.isBlank()) continue;
                String tableSchema = rs.getString("TABLE_SCHEM");
                String key = (tableSchema == null ? "" : tableSchema) + "." + tableName;
                if (map.containsKey(key)) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("tableName", tableName);
                item.put("tableComment", firstNonBlank(rs.getString("REMARKS"), ""));
                item.put("schemaName", tableSchema);
                item.put("tableType", rs.getString("TABLE_TYPE"));
                map.put(key, item);
            }
        }
    }

    private static String safeCatalog(Connection conn)throws SQLException {
        return JdbcMetadataScope.catalog(conn);
    }

    private static Set<String> readPrimaryKeys(DatabaseMetaData meta, String catalog, ProbeTableName name) throws SQLException {
        Set<String> keys = new LinkedHashSet<>();
        try (ResultSet rs = meta.getPrimaryKeys(catalog, name.schema(), name.table())) {
            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME");
                if (col != null && !col.isBlank()) keys.add(col);
            }
        } catch (java.sql.SQLFeatureNotSupportedException unsupported) {
            // Hive versions without constraint metadata still expose valid column metadata.
            return null;
        }
        return keys;
    }

    private static List<Map<String, Object>> queryColumns(DatabaseMetaData meta, String catalog, ProbeTableName name) throws SQLException {
        // Use LinkedHashMap keyed by columnName to deduplicate — some JDBC drivers
        // (Oracle, PostgreSQL, etc.) return duplicate rows when schema is null or
        // matches multiple schemas, causing the same column to appear more than once.
        LinkedHashMap<String, Map<String, Object>> map = new LinkedHashMap<>();
        String escape = meta.getSearchStringEscape();
        String tablePattern = name.table();
        if (escape != null && !escape.isEmpty()) {
            tablePattern = tablePattern.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%");
        }
        try (ResultSet rs = meta.getColumns(catalog, name.schema(), tablePattern, "%")) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                if (columnName == null || columnName.isBlank()) continue;
                if (map.containsKey(columnName)) continue; // deduplicate: keep first occurrence
                Map<String, Object> col = new LinkedHashMap<>();
                col.put("columnName", columnName);
                col.put("columnComment", firstNonBlank(rs.getString("REMARKS"), columnName));
                col.put("dataType", jdbcTypeName(rs.getInt("DATA_TYPE"), rs.getString("TYPE_NAME")));
                col.put("typeName", rs.getString("TYPE_NAME"));
                col.put("columnSize", rs.getObject("COLUMN_SIZE"));
                col.put("decimalDigits", rs.getObject("DECIMAL_DIGITS"));
                col.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                col.put("ordinalPosition", rs.getInt("ORDINAL_POSITION"));
                col.put("defaultValue", rs.getString("COLUMN_DEF"));
                map.put(columnName, col);
            }
        }
        List<Map<String, Object>> list = new ArrayList<>(map.values());
        list.sort((a, b) -> Integer.compare(
                ((Number) a.getOrDefault("ordinalPosition", 0)).intValue(),
                ((Number) b.getOrDefault("ordinalPosition", 0)).intValue()));
        return list;
    }

    private static String jdbcTypeName(int type, String fallback) {
        return switch (type) {
            case Types.BIGINT -> "BIGINT";
            case Types.INTEGER -> "INTEGER";
            case Types.SMALLINT -> "SMALLINT";
            case Types.TINYINT -> "TINYINT";
            case Types.DECIMAL, Types.NUMERIC -> "DECIMAL";
            case Types.DOUBLE, Types.FLOAT, Types.REAL -> "DOUBLE";
            case Types.DATE -> "DATE";
            case Types.TIME -> "TIME";
            case Types.TIMESTAMP, -101, -102 -> "TIMESTAMP";
            case Types.BOOLEAN, Types.BIT -> "BOOLEAN";
            case Types.CLOB, Types.NCLOB, Types.LONGVARCHAR, Types.LONGNVARCHAR -> "TEXT";
            case Types.BLOB, Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> "BINARY";
            default -> firstNonBlank(fallback, "VARCHAR");
        };
    }

    private static String driverClassForDbType(String dbType) {
        return driverClassForDbType(dbType, null);
    }

    private static String driverClassForDbType(String dbType, String jdbcUrl) {
        String type = dbType == null ? "MYSQL" : dbType.trim().toUpperCase(java.util.Locale.ROOT);
        String url = jdbcUrl == null ? "" : jdbcUrl.trim().toLowerCase(java.util.Locale.ROOT);
        if (("OCEANBASE_ORACLE".equals(type) || "OCEANBASEORACLE".equals(type))
                && url.startsWith("jdbc:oracle:")) {
            return "oracle.jdbc.OracleDriver";
        }
        if (("OCEANBASE_MYSQL".equals(type) || "OCEANBASEMYSQL".equals(type))
                && url.startsWith("jdbc:mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        if (("OCEANBASE_MYSQL".equals(type) || "OCEANBASEMYSQL".equals(type))
                && url.startsWith("jdbc:oceanbase:")) {
            return "com.oceanbase.jdbc.Driver";
        }
        return switch (type) {
            case "MARIADB" -> "org.mariadb.jdbc.Driver";
            case "POSTGRESQL", "TDSQL_PG", "GAUSSDB", "OPENGAUSS" -> "org.postgresql.Driver";
            case "ORACLE" -> "oracle.jdbc.OracleDriver";
            case "SQLSERVER" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case "DB2" -> "com.ibm.db2.jcc.DB2Driver";
            case "DM", "DAMENG" -> "dm.jdbc.driver.DmDriver";
            case "KINGBASE" -> "com.kingbase8.Driver";
            case "GBASE8A" -> "com.gbase.jdbc.Driver";
            case "GBASE8S" -> "com.gbasedbt.jdbc.IfxDriver";
            case "OSCAR" -> "com.oscar.Driver";
            case "HIGHGO" -> "com.highgo.jdbc.Driver";
            case "OCEANBASE" -> "com.oceanbase.jdbc.Driver";
            case "OCEANBASE_MYSQL", "OCEANBASEMYSQL" -> "com.mysql.cj.jdbc.Driver";
            case "OCEANBASE_ORACLE", "OCEANBASEORACLE" -> "com.oceanbase.jdbc.Driver";
            case "CLICKHOUSE" -> "com.clickhouse.jdbc.ClickHouseDriver";
            case "HIVE" -> "org.apache.hive.jdbc.HiveDriver";
            case "HETU", "TRINO", "PRESTO" -> "io.trino.jdbc.TrinoDriver";
            case "DORIS", "STARROCKS" -> "com.mysql.cj.jdbc.Driver";
            case "IOTDB" -> "org.apache.iotdb.jdbc.IoTDBDriver";
            case "ELASTICSEARCH" -> "org.elasticsearch.xpack.sql.jdbc.EsDriver";
            default -> "com.mysql.cj.jdbc.Driver";
        };
    }

    private static String req(Map<String, Object> c, String key) {
        Object v = c.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            throw new IllegalArgumentException("缺少必填字段: " + key);
        }
        return String.valueOf(v);
    }

    private static class ResolvedJdbc {
        String url;
        String driverClass;
        String driverPath;
        String username;
        String password;
    }

    private record ProbeTableName(String schema, String table) {
    }
}
