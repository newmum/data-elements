package com.linewell.dataelement.metautil.structured;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.dto.SampleDataResult;
import com.linewell.dataelement.metautil.model.dto.TableInfo;
import com.linewell.dataelement.metautil.model.dto.TablePageResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Probes non-JDBC sources and exposes them as stable table/column/sample data.
 * The five-step registration flow consumes this contract exactly like JDBC metadata.
 */
@Service
public class StructuredSourceProbeService {

    private static final int DEFAULT_SAMPLE_ROWS = 100;
    private static final int MAX_SAMPLE_ROWS = 500;
    /**
     * Connection testing only needs a small, representative sample to infer a
     * schema.  Keep this deliberately lower than preview sampling: a remote
     * file can be many gigabytes and must not be downloaded during step one.
     */
    private static final int FTP_PROBE_SAMPLE_ROWS = 10;
    private static final int MAX_DOWNLOAD_BYTES = 20 * 1024 * 1024;
    private static final int MAX_RESPONSE_BYTES = 5 * 1024 * 1024;
    /**
     * Compatibility guard for explicitly configured object-storage sources.
     * A non-positive value means no artificial discovery cap: table pages are
     * still returned in bounded slices by {@link #getTablesPage(Map, Integer,
     * Integer, String)}.  This prevents the 501st file from disappearing from
     * registration solely because it falls outside an old probe limit.
     */
    private static final int DEFAULT_MAX_DISCOVERED_FILES = 0;
    private static final long CACHE_TTL_MILLIS = 2 * 60 * 1000L;
    private static final Pattern SAFE_NAME = Pattern.compile("[^a-zA-Z0-9_]+");
    private static final Set<String> FILE_EXTENSIONS = Set.of("csv", "xls", "xlsx", "json", "txt");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, CachedProbe> cache = new ConcurrentHashMap<>();

    @Autowired
    public StructuredSourceProbeService(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    StructuredSourceProbeService(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public boolean supports(Map<String, Object> source) {
        return Set.of("api", "ftp", "kafka", "minio").contains(sourceType(source));
    }

    public Map<String, Object> testAndProbe(Map<String, Object> source) {
        long startedAt = System.currentTimeMillis();
        StructuredProbeResult probe = probe(source, true);
        int fieldCount = probe.getTables().stream().mapToInt(item -> item.getColumns().size()).sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connected", true);
        result.put("success", true);
        result.put("product", probe.getProduct());
        result.put("url", probe.getEndpoint());
        result.put("version", probe.getTables().size() + " 张数据表，" + fieldCount + " 个字段");
        result.put("elapsedMs", System.currentTimeMillis() - startedAt);
        result.put("responsePreview", probe.getResponsePreview());
        result.put("tables", probe.getTables().stream().map(StructuredTableData::summary).toList());
        return result;
    }

    public TablePageResult getTablesPage(
            Map<String, Object> source,
            Integer pageNo,
            Integer pageSize,
            String keyword
    ) {
        // A datasource can already have registration snapshots while its API
        // endpoint is still being completed.  Reading the registration page
        // must not turn that incomplete optional connection setting into a
        // hard failure.  Callers will merge this empty physical-probe result
        // with the persisted table snapshots instead.
        if ("api".equals(sourceType(source)) && blank(first(source, "apiUrl", "url"))) {
            return emptyTablePage(pageNo, pageSize);
        }
        List<TableInfo> all = probe(source, false).getTables().stream()
                .map(StructuredTableData::getTable)
                .filter(table -> blank(keyword)
                        || containsIgnoreCase(table.getTableName(), keyword)
                        || containsIgnoreCase(table.getTableComment(), keyword))
                .toList();
        int normalizedPage = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int normalizedSize = pageSize == null || pageSize < 1 ? 50 : Math.min(pageSize, 500);
        int from = Math.min((normalizedPage - 1) * normalizedSize, all.size());
        int to = Math.min(from + normalizedSize, all.size());
        TablePageResult result = new TablePageResult();
        result.setPageNo(normalizedPage);
        result.setPageSize(normalizedSize);
        result.setTotal((long) all.size());
        result.setRows(new ArrayList<>(all.subList(from, to)));
        return result;
    }

    public List<ColumnInfo> getColumns(Map<String, Object> source, String tableName) {
        return findTable(source, tableName).getColumns();
    }

    public SampleDataResult sampleData(Map<String, Object> source, String tableName, Integer limit) {
        StructuredTableData table = findTable(source, tableName);
        int sampleSize = limit == null || limit < 1 ? 20 : Math.min(limit, MAX_SAMPLE_ROWS);
        List<Map<String, Object>> rows = table.getRows().stream().limit(sampleSize).toList();
        SampleDataResult result = new SampleDataResult();
        result.setTableName(tableName);
        result.setSampleSize(sampleSize);
        result.setActualSize(rows.size());
        result.setTotalCount(table.getTable().getRowCount());
        result.setColumnNames(table.getColumns().stream().map(ColumnInfo::getColumnName).toList());
        result.setColumnTypes(table.getColumns().stream().map(ColumnInfo::getColumnType).toList());
        result.setData(rows);
        result.setSampleMethod("FIRST");
        return result;
    }

    public StructuredProbeResult probe(Map<String, Object> source, boolean refresh) {
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("Datasource configuration cannot be empty");
        }
        String key = cacheKey(source);
        CachedProbe cached = cache.get(key);
        if (!refresh && cached != null && System.currentTimeMillis() - cached.createdAt < CACHE_TTL_MILLIS) {
            return cached.result;
        }
        StructuredProbeResult result = switch (sourceType(source)) {
            case "api" -> probeApi(source);
            case "ftp" -> probeFtp(source);
            case "kafka" -> probeKafka(source);
            case "minio" -> probeMinio(source);
            default -> throw new IllegalArgumentException("Unsupported structured source type: " + sourceType(source));
        };
        if (result.getTables().isEmpty()) {
            throw new IllegalStateException(noStructuredTableMessage(source));
        }
        cache.put(key, new CachedProbe(System.currentTimeMillis(), result));
        if (cache.size() > 256) {
            long now = System.currentTimeMillis();
            cache.entrySet().removeIf(entry -> now - entry.getValue().createdAt > CACHE_TTL_MILLIS);
        }
        return result;
    }

    private StructuredTableData findTable(Map<String, Object> source, String tableName) {
        if (blank(tableName)) {
            throw new IllegalArgumentException("tableName cannot be empty");
        }
        return probe(source, false).getTables().stream()
                .filter(item -> tableName.equalsIgnoreCase(item.getTable().getTableName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Structured table does not exist: " + tableName));
    }

    private StructuredProbeResult probeApi(Map<String, Object> source) {
        String url = required(source, "API request URL cannot be empty", "apiUrl", "url");
        String method = defaultText(first(source, "apiMethod", "method"), "GET").toUpperCase(Locale.ROOT);
        URI endpoint = URI.create(applyApiKeyQuery(source, url));
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(apiProbeTimeoutSeconds(source)));
        // API task templates persist service headers as commonHeadersJson,
        // whereas the generic structured-source connector uses apiHeaders.
        // Accept both names so field probing invokes the same registered API
        // contract as the actual NiFi processor.
        Map<String, String> headers = parseStringMap(first(source, "apiHeaders", "headers", "commonHeadersJson"));
        headers.forEach(request::header);
        if (!headers.keySet().stream().anyMatch(name -> "content-type".equalsIgnoreCase(name))) {
            request.header("Content-Type", "application/json");
        }
        applyApiAuthorization(source, request);
        applyCurrentSessionToken(source, endpoint, request);
        String requestBody = defaultText(first(source, "apiBody", "requestBody"), "");
        request.method(method, Set.of("GET", "HEAD").contains(method)
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
        try {
            HttpResponse<byte[]> response = httpClient.send(
                    request.build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 400) {
                throw new IllegalStateException("HTTP " + response.statusCode() + ": "
                        + preview(new String(response.body(), StandardCharsets.UTF_8), 2000));
            }
            if (response.body().length > MAX_RESPONSE_BYTES) {
                throw new IllegalStateException("API response exceeds 5 MB probe limit");
            }
            String content = new String(response.body(), StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(content);
            Map<String, JsonNode> discovered = discoverCollections(root);
            List<String> selectedPaths = parseStringList(first(source, "apiCollectionPaths", "collectionPaths"));
            if (!selectedPaths.isEmpty()) {
                Map<String, JsonNode> selected = new LinkedHashMap<>();
                for (String path : selectedPaths) {
                    JsonNode node = resolvePath(root, path);
                    if (node == null || node.isMissingNode() || node.isNull()) {
                        throw new IllegalArgumentException("Configured response collection path does not exist: " + path);
                    }
                    selected.put(path, node);
                }
                discovered = selected;
            }
            StructuredProbeResult result = baseResult("api", "HTTP API", url);
            result.setResponsePreview(preview(content, 4000));
            Set<String> names = new HashSet<>();
            for (Map.Entry<String, JsonNode> entry : discovered.entrySet()) {
                List<Map<String, Object>> rows = rowsFromJson(entry.getValue(), DEFAULT_SAMPLE_ROWS);
                if (rows.isEmpty() && entry.getValue().isArray()) {
                    rows = new ArrayList<>();
                }
                String name = uniqueName("api_" + pathName(entry.getKey()), names);
                result.getTables().add(buildTable(name, "API response " + entry.getKey(), entry.getKey(), rows));
            }
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("API probe was interrupted", exception);
        } catch (Exception exception) {
            throw propagate("API request or response parsing failed", exception);
        }
    }

    private StructuredProbeResult probeFtp(Map<String, Object> source) {
        String protocol = defaultText(first(source, "ftpProtocol", "protocol"), "ftp").toLowerCase(Locale.ROOT);
        String host = required(source, "FTP host cannot be empty", "ftpHost", "hostname", "host");
        int port = integer(first(source, "ftpPort", "port"), "sftp".equals(protocol) ? 22 : 21);
        String path = defaultText(first(source, "ftpPath", "remotePath", "path"), "/");
        StructuredProbeResult result = baseResult("ftp", protocol.toUpperCase(Locale.ROOT), protocol + "://" + host + ":" + port + path);
        try {
            RemoteFileProbeStats stats;
            if ("sftp".equals(protocol)) {
                stats = probeSftp(source, host, port, path, result);
            } else {
                stats = probeClassicFtp(source, protocol, host, port, path, result);
            }
            String preview = "在目录“" + path + "”下识别到 " + result.getTables().size() + " 份可解析数据文件";
            if (!stats.skippedFiles.isEmpty()) {
                preview += "；已跳过 " + stats.skippedFiles.size() + " 个无法读取的文件："
                        + String.join(", ", stats.skippedFiles.stream().limit(3).toList());
            }
            result.setResponsePreview(preview);
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException(ftpProbeFailureMessage(path, exception), exception);
        }
    }

    private String ftpProbeFailureMessage(String path, Exception exception) {
        Throwable cause = exception instanceof RemoteReadException remote ? remote.unwrap() : exception;
        String detail = defaultText(cause.getMessage(), "").toLowerCase(Locale.ROOT);
        if (detail.contains("no such file") || detail.contains("not found") || detail.contains("does not exist")
                || detail.contains("can't cd") || detail.contains("cannot change directory")) {
            return "已连接到文件服务器，但根目录“" + path + "”不存在或无法进入。请确认根目录填写的是服务器上的实际路径。";
        }
        if (detail.contains("permission denied") || detail.contains("access denied")) {
            return "已连接到文件服务器，但当前账号没有读取根目录“" + path + "”的权限。请联系管理员授予目录读取权限后重试。";
        }
        if (detail.contains("auth fail") || detail.contains("authentication failed") || detail.contains("login failed")) {
            return "文件服务器认证未通过，请检查用户名、密码或密钥配置后重试。";
        }
        if (detail.contains("timed out") || detail.contains("timeout")) {
            return "文件服务器连接超时，请检查主机地址、端口、网络连通性和防火墙策略后重试。";
        }
        if (detail.contains("connection refused") || detail.contains("connect exception") || detail.contains("connection reset")) {
            return "无法建立文件服务器连接，请检查主机地址、端口和网络连通性后重试。";
        }
        return "文件服务器目录探查未通过，请检查根目录、文件匹配规则、递归扫描设置和账号读取权限后重试。";
    }

    /**
     * Registers each readable object in the chosen bucket/prefix as one
     * logical table.  The actual parser is shared with FTP so the same CSV,
     * JSON, TXT and Excel file gives the same fields regardless of transport.
     */
    private StructuredProbeResult probeMinio(Map<String, Object> source) {
        String endpoint = required(source, "MinIO service endpoint cannot be empty", "minioEndpoint", "endpoint", "host");
        String accessKey = required(source, "MinIO Access Key cannot be empty", "minioAccessKey", "accessKey", "username");
        String secretKey = required(source, "MinIO Secret Key cannot be empty", "minioSecretKey", "secretKey", "password");
        String bucket = required(source, "MinIO bucket cannot be empty", "minioBucket", "bucket", "database", "dbName");
        String prefix = defaultText(first(source, "minioPrefix", "objectPrefix", "prefix", "path"), "");
        StructuredProbeResult result = baseResult("minio", "MinIO", endpoint + "/" + bucket
                + (prefix.isBlank() ? "" : "/" + prefix));
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(normalizeMinioEndpoint(endpoint, source))
                    .credentials(accessKey, secretKey)
                    .build();
            List<RemoteFile> files = new ArrayList<>();
            int discoveryLimit = discoveryLimit(source);
            for (Result<Item> itemResult : client.listObjects(io.minio.ListObjectsArgs.builder()
                    .bucket(bucket).prefix(prefix).recursive(true).build())) {
                Item item = itemResult.get();
                String objectName = item.objectName();
                if (objectName == null || objectName.endsWith("/") || !isSupportedFile(objectName)) continue;
                files.add(new RemoteFile(objectName, objectFileName(objectName), item.size()));
                if (discoveryLimit > 0 && files.size() >= discoveryLimit) break;
            }
            RemoteFileProbeStats stats = parseRemoteFiles(source, files, file -> {
                try {
                    return client.getObject(GetObjectArgs.builder().bucket(bucket).object(file.path).build());
                } catch (Exception exception) {
                    throw new RemoteReadException(exception);
                }
            }, result);
            String preview = result.getTables().size() + " structured dataset(s) discovered in bucket " + bucket;
            if (!prefix.isBlank()) preview += " under " + prefix;
            if (discoveryLimit > 0 && files.size() >= discoveryLimit) {
                preview += "; discovery is limited to the first " + discoveryLimit + " supported files";
            }
            if (!stats.skippedFiles.isEmpty()) {
                preview += "; skipped " + stats.skippedFiles.size() + " unreadable file(s): "
                        + String.join(", ", stats.skippedFiles.stream().limit(3).toList());
            }
            result.setResponsePreview(preview);
            return result;
        } catch (Exception exception) {
            throw propagate("MinIO object discovery or parsing failed", exception);
        }
    }

    private String normalizeMinioEndpoint(String endpoint, Map<String, Object> source) {
        String normalized = endpoint.trim();
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) return normalized;
        return bool(source, "minioUseSSL", false) ? "https://" + normalized : "http://" + normalized;
    }

    private String objectFileName(String objectName) {
        int index = objectName.lastIndexOf('/');
        return index < 0 ? objectName : objectName.substring(index + 1);
    }

    private RemoteFileProbeStats probeSftp(
            Map<String, Object> source,
            String host,
            int port,
            String path,
            StructuredProbeResult result
    ) throws Exception {
        String username = required(source, "SFTP username cannot be empty", "ftpUsername", "username");
        String password = defaultText(first(source, "ftpPassword", "password"), "");
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = new JSch().getSession(username, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(10_000);
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(10_000);
            List<RemoteFile> files = new ArrayList<>();
            collectSftpFiles(channel, path, bool(source, "ftpRecursive", false), files);
            ChannelSftp activeChannel = channel;
            return parseRemoteFiles(source, files, file -> {
                try {
                    return activeChannel.get(file.path);
                } catch (Exception exception) {
                    throw new RemoteReadException(exception);
                }
            }, result);
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }

    @SuppressWarnings("unchecked")
    private void collectSftpFiles(ChannelSftp channel, String path, boolean recursive, List<RemoteFile> files)
            throws Exception {
        for (ChannelSftp.LsEntry entry : (Collection<ChannelSftp.LsEntry>) channel.ls(path)) {
            if (".".equals(entry.getFilename()) || "..".equals(entry.getFilename())) continue;
            String child = joinPath(path, entry.getFilename());
            if (entry.getAttrs().isDir()) {
                if (recursive) collectSftpFiles(channel, child, true, files);
            } else if (isSupportedFile(entry.getFilename())) {
                files.add(new RemoteFile(child, entry.getFilename(), entry.getAttrs().getSize()));
            }
        }
    }

    private RemoteFileProbeStats probeClassicFtp(
            Map<String, Object> source,
            String protocol,
            String host,
            int port,
            String path,
            StructuredProbeResult result
    ) throws Exception {
        FTPClient client = "ftps".equals(protocol) ? new FTPSClient(port == 990) : new FTPClient();
        try {
            client.setConnectTimeout(10_000);
            client.setDataTimeout(Duration.ofSeconds(20));
            client.connect(host, port);
            String username = defaultText(first(source, "ftpUsername", "username"), "anonymous");
            String password = defaultText(first(source, "ftpPassword", "password"), "anonymous@");
            if (!client.login(username, password)) {
                throw new IllegalStateException("FTP login failed: " + client.getReplyString());
            }
            if (client instanceof FTPSClient ftpsClient) {
                ftpsClient.execPBSZ(0);
                ftpsClient.execPROT("P");
            }
            if (bool(source, "ftpPassiveMode", true)) client.enterLocalPassiveMode();
            client.setFileType(FTP.BINARY_FILE_TYPE);
            List<RemoteFile> files = new ArrayList<>();
            collectFtpFiles(client, path, bool(source, "ftpRecursive", false), files);
            return parseRemoteFiles(source, files, file -> {
                try {
                    InputStream stream = client.retrieveFileStream(file.path);
                    if (stream == null) throw new IllegalStateException("Cannot download " + file.path + ": " + client.getReplyString());
                    return new PendingFtpInputStream(stream, client);
                } catch (Exception exception) {
                    throw new RemoteReadException(exception);
                }
            }, result);
        } finally {
            if (client.isConnected()) {
                try { client.logout(); } finally { client.disconnect(); }
            }
        }
    }

    private void collectFtpFiles(FTPClient client, String path, boolean recursive, List<RemoteFile> files)
            throws Exception {
        for (FTPFile entry : client.listFiles(path)) {
            String child = joinPath(path, entry.getName());
            if (entry.isDirectory()) {
                if (recursive && !".".equals(entry.getName()) && !"..".equals(entry.getName())) {
                    collectFtpFiles(client, child, true, files);
                }
            } else if (entry.isFile() && isSupportedFile(entry.getName())) {
                files.add(new RemoteFile(child, entry.getName(), entry.getSize()));
            }
        }
    }

    RemoteFileProbeStats parseRemoteFiles(
            Map<String, Object> source,
            List<RemoteFile> files,
            RemoteStreamOpener opener,
            StructuredProbeResult result
    ) throws Exception {
        String niFiRegex = first(source, "fileFilterRegex");
        String patternText = defaultText(first(source,
                "minio".equals(sourceType(source)) ? "minioFilePattern" : "ftpFilePattern"), "*");
        Predicate<String> pattern = blank(niFiRegex)
                ? glob(patternText)
                : regex(niFiRegex);
        Set<String> names = new HashSet<>();
        int matchedFiles = 0;
        List<String> skippedFiles = new ArrayList<>();
        List<RemoteFile> matched = files.stream()
                .filter(item -> pattern.test(item.name))
                .sorted(Comparator.comparing(item -> item.path))
                .toList();
        if ((bool(source, "ftpSingleFile", false) || bool(source, "minioSingleFile", false))
                && !matched.isEmpty()) {
            // A daily/drop folder commonly contains partitions of one logical
            // table.  In this mode use the stable first matching path only;
            // it controls registration-time schema discovery, not FTP login
            // or directory access validation.
            matched = matched.subList(0, 1);
        }
        for (RemoteFile file : matched) {
            matchedFiles++;
            try (InputStream input = opener.open(file)) {
                parseRemoteFileSample(source, input, file, names, result);
            } catch (Exception exception) {
                // File content is not a connection prerequisite.  One damaged
                // JSON/CSV/XLS file must not make an otherwise reachable FTP
                // source fail its first-step connectivity test.
                Throwable cause = exception instanceof RemoteReadException remote ? remote.unwrap() : exception;
                skippedFiles.add(file.name + " (" + preview(defaultText(cause.getMessage(),
                        cause.getClass().getSimpleName()), 120) + ")");
            }
        }
        return new RemoteFileProbeStats(matchedFiles, skippedFiles);
    }

    /**
     * Parses remote files directly from their transfer stream.  This is
     * intentionally separate from {@link #parseStructuredFile(Map, byte[],
     * RemoteFile, Set, StructuredProbeResult)}: callers that already own a
     * local byte array can retain the richer preview sample, while an FTP/SFTP
     * connectivity check must stop as soon as it has enough rows to discover
     * the dataset schema.
     */
    private void parseRemoteFileSample(
            Map<String, Object> source,
            InputStream input,
            RemoteFile file,
            Set<String> names,
            StructuredProbeResult result
    ) throws Exception {
        switch (extension(file.name)) {
            case "csv" -> addDelimitedTable(source, new InputStreamReader(input, ftpCharset(source)), file, names, result,
                    false, FTP_PROBE_SAMPLE_ROWS);
            case "txt" -> parseRemoteTextFile(source, input, file, names, result);
            case "json" -> parseRemoteJsonFile(input, file, names, result);
            case "xls", "xlsx" -> {
                // Office workbooks are ZIP/OLE containers: POI must access
                // their directory before it can read a sheet, so a byte-range
                // sample is not valid. Keep their existing bounded behaviour
                // rather than silently downloading an unbounded workbook.
                if (file.size > MAX_DOWNLOAD_BYTES) {
                    throw new IllegalStateException("Excel probe file exceeds 20 MB limit: " + file.path
                            + "; CSV, TXT and JSON files are sampled to the first " + FTP_PROBE_SAMPLE_ROWS + " rows");
                }
                parseWorkbook(readLimited(input, MAX_DOWNLOAD_BYTES), file, names, result, FTP_PROBE_SAMPLE_ROWS);
            }
            default -> throw new IllegalArgumentException("Unsupported structured file: " + file.name);
        }
    }

    private void parseRemoteTextFile(
            Map<String, Object> source,
            InputStream input,
            RemoteFile file,
            Set<String> names,
            StructuredProbeResult result
    ) throws Exception {
        BufferedInputStream buffered = new BufferedInputStream(input);
        buffered.mark(8192);
        int first = firstNonWhitespaceByte(buffered);
        buffered.reset();
        if (first == '{' || first == '[') {
            parseRemoteJsonFile(buffered, file, names, result);
            return;
        }
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(buffered, ftpCharset(source)))) {
            String line;
            int lines = 0;
            while (lines < FTP_PROBE_SAMPLE_ROWS + 1 && (line = reader.readLine()) != null) {
                text.append(line).append('\n');
                lines++;
            }
        }
        parseTextFile(source, text.toString().getBytes(ftpCharset(source)), file, names, result, FTP_PROBE_SAMPLE_ROWS);
    }

    private int firstNonWhitespaceByte(InputStream input) throws Exception {
        int value;
        while ((value = input.read()) >= 0) {
            if (!Character.isWhitespace((char) value)) return value;
        }
        return -1;
    }

    private void parseStructuredFile(
            Map<String, Object> source,
            byte[] content,
            RemoteFile file,
            Set<String> names,
            StructuredProbeResult result
    ) throws Exception {
        switch (extension(file.name)) {
            case "csv" -> addDelimitedTable(source, content, file, names, result, false);
            case "txt" -> parseTextFile(source, content, file, names, result);
            case "json" -> parseJsonFile(content, file, names, result);
            case "xls", "xlsx" -> parseWorkbook(content, file, names, result);
            default -> throw new IllegalArgumentException("Unsupported structured file: " + file.name);
        }
    }

    private void addDelimitedTable(
            Map<String, Object> source,
            byte[] content,
            RemoteFile file,
            Set<String> names,
            StructuredProbeResult result,
            boolean autoDetectDelimiter
    ) throws Exception {
        DelimitedData data = parseDelimited(source, content, autoDetectDelimiter, MAX_SAMPLE_ROWS);
        String tableName = uniqueName(fileStem(file.name), names);
        result.getTables().add(buildTable(tableName, file.name, file.path, data.rows, data.headers));
    }

    private void addDelimitedTable(
            Map<String, Object> source,
            Reader reader,
            RemoteFile file,
            Set<String> names,
            StructuredProbeResult result,
            boolean autoDetectDelimiter,
            int rowLimit
    ) throws Exception {
        DelimitedData data = parseDelimited(source, reader, autoDetectDelimiter, rowLimit);
        String tableName = uniqueName(fileStem(file.name), names);
        result.getTables().add(buildTable(tableName, file.name, file.path, data.rows, data.headers));
    }

    private void parseTextFile(
            Map<String, Object> source,
            byte[] content,
            RemoteFile file,
            Set<String> names,
            StructuredProbeResult result
    ) throws Exception {
        Charset charset = Charset.forName(defaultText(first(source, "ftpCharset"), "UTF-8"));
        String text = stripBom(new String(content, charset)).trim();
        if (text.startsWith("{") || text.startsWith("[")) {
            try {
                parseJsonFile(text.getBytes(StandardCharsets.UTF_8), file, names, result);
                return;
            } catch (Exception ignored) {
                // Fall through to JSON Lines or delimited text.
            }
        }
        List<Map<String, Object>> jsonLines = parseJsonLines(text, MAX_SAMPLE_ROWS);
        if (!jsonLines.isEmpty()) {
            String tableName = uniqueName(fileStem(file.name), names);
            result.getTables().add(buildTable(tableName, file.name, file.path, jsonLines));
            return;
        }
        addDelimitedTable(source, content, file, names, result, true);
    }

    private void parseTextFile(
            Map<String, Object> source,
            byte[] content,
            RemoteFile file,
            Set<String> names,
            StructuredProbeResult result,
            int rowLimit
    ) throws Exception {
        Charset charset = ftpCharset(source);
        String text = stripBom(new String(content, charset)).trim();
        if (text.startsWith("{") || text.startsWith("[")) {
            try {
                parseJsonFile(text.getBytes(StandardCharsets.UTF_8), file, names, result, rowLimit);
                return;
            } catch (Exception ignored) {
                // Fall through to JSON Lines or delimited text.
            }
        }
        List<Map<String, Object>> jsonLines = parseJsonLines(text, rowLimit);
        if (!jsonLines.isEmpty()) {
            String tableName = uniqueName(fileStem(file.name), names);
            result.getTables().add(buildTable(tableName, file.name, file.path, jsonLines));
            return;
        }
        addDelimitedTable(source, new InputStreamReader(new ByteArrayInputStream(content), charset), file, names, result,
                true, rowLimit);
    }

    private void parseJsonFile(
            byte[] content,
            RemoteFile file,
            Set<String> names,
            StructuredProbeResult result
    ) throws Exception {
        parseJsonFile(content, file, names, result, MAX_SAMPLE_ROWS);
    }

    private void parseJsonFile(
            byte[] content,
            RemoteFile file,
            Set<String> names,
            StructuredProbeResult result,
            int rowLimit
    ) throws Exception {
        JsonNode root = objectMapper.reader()
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(content);
        if (root == null) throw new IllegalArgumentException("JSON file is empty: " + file.path);
        Map<String, JsonNode> collections = discoverCollections(root);
        if (collections.isEmpty() && root.isArray()) collections = Map.of("$", root);
        for (Map.Entry<String, JsonNode> entry : collections.entrySet()) {
            String path = entry.getKey();
            String collectionName = pathName(path);
            String suffix = "$".equals(path) || safeName(fileStem(file.name)).equals(safeName(collectionName))
                    ? ""
                    : "_" + collectionName;
            String tableName = uniqueName(fileStem(file.name) + suffix, names);
            String sourcePath = file.path + ("$".equals(path) ? "" : "#" + path);
            String comment = file.name + ("$".equals(path) ? "" : " / " + path);
            StructuredTableData table = buildTable(
                    tableName,
                    comment,
                    sourcePath,
                    rowsFromJson(entry.getValue(), rowLimit)
            );
            if (!table.getColumns().isEmpty()) result.getTables().add(table);
        }
    }

    /**
     * Reads the first JSON collection encountered and stops after its first
     * ten entries.  Do not call skipChildren here: skipping a large JSON array
     * still transfers every remaining byte over FTP/SFTP.
     */
    private void parseRemoteJsonFile(
            InputStream input,
            RemoteFile file,
            Set<String> names,
            StructuredProbeResult result
    ) throws Exception {
        try (JsonParser parser = objectMapper.getFactory().createParser(input)) {
            JsonToken firstToken = parser.nextToken();
            if (firstToken == null) {
                throw new IllegalArgumentException("JSON file is empty: " + file.path);
            }
            JsonSampleCollection collection;
            if (firstToken == JsonToken.START_ARRAY) {
                collection = new JsonSampleCollection("$", sampleJsonArray(parser));
            } else if (firstToken == JsonToken.START_OBJECT) {
                Map<String, Object> objectRow = new LinkedHashMap<>();
                collection = findFirstJsonArray(parser, "$", "", 0, objectRow);
                if (collection == null) {
                    // A root object without a nested collection is one record.
                    // Local JSON parsing already supports this form; keep FTP's
                    // streamed probe consistent instead of rejecting valid JSON.
                    collection = new JsonSampleCollection("$", List.of(objectRow));
                }
            } else {
                throw new IllegalArgumentException("JSON probe needs an object or array: " + file.path);
            }
            String collectionName = pathName(collection.path);
            String suffix = "$".equals(collection.path)
                    || safeName(fileStem(file.name)).equals(safeName(collectionName))
                    ? ""
                    : "_" + collectionName;
            String tableName = uniqueName(fileStem(file.name) + suffix, names);
            String sourcePath = file.path + ("$".equals(collection.path) ? "" : "#" + collection.path);
            String comment = file.name + ("$".equals(collection.path) ? "" : " / " + collection.path);
            StructuredTableData table = buildTable(tableName, comment, sourcePath, collection.rows);
            if (!table.getColumns().isEmpty()) result.getTables().add(table);
        }
    }

    private JsonSampleCollection findFirstJsonArray(
            JsonParser parser,
            String path,
            String columnPrefix,
            int depth,
            Map<String, Object> objectRow
    ) throws Exception {
        if (depth > 8) {
            JsonNode nested = objectMapper.readTree(parser);
            flattenJson(columnPrefix, nested, objectRow);
            return null;
        }
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            JsonToken valueToken = parser.nextToken();
            String childPath = path + "." + fieldName;
            String childPrefix = columnPrefix.isEmpty() ? fieldName : columnPrefix + "_" + fieldName;
            if (valueToken == JsonToken.START_ARRAY) {
                return new JsonSampleCollection(childPath, sampleJsonArray(parser));
            }
            if (valueToken == JsonToken.START_OBJECT) {
                JsonSampleCollection nested = findFirstJsonArray(
                        parser, childPath, childPrefix, depth + 1, objectRow);
                if (nested != null) return nested;
            } else {
                objectRow.put(cleanColumnName(childPrefix), jsonScalar(parser, valueToken));
            }
        }
        return null;
    }

    private Object jsonScalar(JsonParser parser, JsonToken token) throws Exception {
        return switch (token) {
            case VALUE_NULL -> null;
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            case VALUE_NUMBER_INT -> parser.getLongValue();
            case VALUE_NUMBER_FLOAT -> parser.getDecimalValue();
            default -> parser.getValueAsString();
        };
    }

    private List<Map<String, Object>> sampleJsonArray(JsonParser parser) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rows.size() < FTP_PROBE_SAMPLE_ROWS && parser.nextToken() != JsonToken.END_ARRAY) {
            rows.add(rowFromJson(objectMapper.readTree(parser)));
        }
        return rows;
    }

    private List<Map<String, Object>> parseJsonLines(String text, int rowLimit) {
        if (blank(text)) return List.of();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String line : text.split("\\R")) {
            if (line.isBlank()) continue;
            String normalizedLine = line.trim();
            if (!normalizedLine.startsWith("{") && !normalizedLine.startsWith("[")) return List.of();
            try {
                JsonNode node = objectMapper.reader()
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .readTree(normalizedLine);
                if (node == null || (!node.isObject() && !node.isValueNode())) return List.of();
                rows.add(rowFromJson(node));
                if (rows.size() >= rowLimit) break;
            } catch (Exception exception) {
                return List.of();
            }
        }
        return rows;
    }

    private DelimitedData parseDelimited(
            Map<String, Object> source,
            byte[] content,
            boolean autoDetectDelimiter,
            int rowLimit
    ) throws Exception {
        Charset charset = ftpCharset(source);
        String decoded = stripBom(new String(content, charset));
        return parseDelimited(source, new InputStreamReader(new ByteArrayInputStream(decoded.getBytes(charset)), charset),
                autoDetectDelimiter, rowLimit, decoded);
    }

    private DelimitedData parseDelimited(
            Map<String, Object> source,
            Reader reader,
            boolean autoDetectDelimiter,
            int rowLimit
    ) throws Exception {
        // CSV delimiter auto-detection needs only the header. BufferedReader
        // avoids pulling the rest of a remote file into memory.
        BufferedReader buffered = reader instanceof BufferedReader value ? value : new BufferedReader(reader);
        buffered.mark(64 * 1024);
        String header = buffered.readLine();
        buffered.reset();
        return parseDelimited(source, buffered, autoDetectDelimiter, rowLimit, defaultText(header, ""));
    }

    private DelimitedData parseDelimited(
            Map<String, Object> source,
            Reader reader,
            boolean autoDetectDelimiter,
            int rowLimit,
            String delimiterDetectionText
    ) throws Exception {
        String delimiterText = defaultText(first(source, "ftpDelimiter"), ",");
        char configuredDelimiter = "\\t".equals(delimiterText) ? '\t' : delimiterText.charAt(0);
        char delimiter = autoDetectDelimiter ? detectDelimiter(delimiterDetectionText, configuredDelimiter) : configuredDelimiter;
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> headers;
        try (CSVParser parser = CSVParser.parse(reader, format)) {
            headers = parser.getHeaderNames().stream().map(this::cleanColumnName).toList();
            for (CSVRecord record : parser) {
                if (rows.size() >= rowLimit) break;
                Map<String, Object> row = new LinkedHashMap<>();
                for (String header : parser.getHeaderNames()) row.put(cleanColumnName(header), record.get(header));
                rows.add(row);
            }
        }
        return new DelimitedData(headers, rows);
    }

    private char detectDelimiter(String text, char configuredDelimiter) {
        String header = text.lines().filter(line -> !line.isBlank()).findFirst().orElse("");
        List<Character> candidates = new ArrayList<>(List.of('\t', '|', ',', ';'));
        if (!candidates.contains(configuredDelimiter)) candidates.add(configuredDelimiter);
        char selected = configuredDelimiter;
        int bestCount = -1;
        for (char candidate : candidates) {
            int count = 0;
            boolean quoted = false;
            for (int index = 0; index < header.length(); index++) {
                char current = header.charAt(index);
                if (current == '"') quoted = !quoted;
                else if (!quoted && current == candidate) count++;
            }
            if (count > bestCount) {
                selected = candidate;
                bestCount = count;
            }
        }
        return selected;
    }

    private String stripBom(String value) {
        return value != null && !value.isEmpty() && value.charAt(0) == '\ufeff' ? value.substring(1) : value;
    }

    StructuredProbeResult parseFileForTest(String fileName, byte[] content) throws Exception {
        StructuredProbeResult result = baseResult("ftp", "FILE", fileName);
        parseStructuredFile(Map.of("ftpCharset", "UTF-8", "ftpDelimiter", ","), content,
                new RemoteFile("/" + fileName, fileName, content.length), new HashSet<>(), result);
        return result;
    }

    private TablePageResult emptyTablePage(Integer pageNo, Integer pageSize) {
        int normalizedPage = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int normalizedSize = pageSize == null || pageSize < 1 ? 50 : Math.min(pageSize, 500);
        TablePageResult result = new TablePageResult();
        result.setPageNo(normalizedPage);
        result.setPageSize(normalizedSize);
        result.setTotal(0L);
        result.setRows(new ArrayList<>());
        return result;
    }

    /**
     * A successful transport connection does not mean that the configured
     * source location contains usable datasets. Return an operator-facing
     * diagnosis rather than exposing an English implementation exception.
     */
    private String noStructuredTableMessage(Map<String, Object> source) {
        return switch (sourceType(source)) {
            case "ftp" -> {
                String path = defaultText(first(source, "ftpPath", "remotePath", "path"), "/");
                String match = defaultText(first(source, "ftpFileMatch", "fileMatch", "filePattern"), "");
                String matchHint = blank(match) ? "" : "同时请确认文件匹配规则“" + match + "”能够匹配实际文件。";
                yield "已成功连接文件服务器，但在根目录“" + path
                        + "”中未发现可解析的数据文件。请优先核对根目录是否正确、当前账号是否具有读取权限、是否需要开启递归扫描子目录。"
                        + matchHint;
            }
            case "api" -> "接口已连通，但响应内容中未识别到可登记的数据对象或数组。请检查接口地址、请求参数和响应结构。";
            case "kafka" -> "消息服务已连通，但指定主题中未读取到可解析的消息数据。请检查主题名称、消费权限和消息格式。";
            case "minio" -> "对象存储已连通，但存储桶或对象前缀中未发现可解析的数据文件。请检查存储桶、对象前缀、文件匹配规则和读取权限。";
            default -> "连接已建立，但未发现可登记的数据表。请检查数据源范围、筛选条件和读取权限。";
        };
    }

    StructuredProbeResult parseRemoteFileSampleForTest(String fileName, long declaredSize, byte[] content) throws Exception {
        StructuredProbeResult result = baseResult("ftp", "FILE", fileName);
        parseRemoteFileSample(Map.of("ftpCharset", "UTF-8", "ftpDelimiter", ","), new ByteArrayInputStream(content),
                new RemoteFile("/" + fileName, fileName, declaredSize), new HashSet<>(), result);
        return result;
    }

    private void parseWorkbook(
            byte[] content,
            RemoteFile file,
            Set<String> names,
            StructuredProbeResult result
    ) throws Exception {
        parseWorkbook(content, file, names, result, MAX_SAMPLE_ROWS);
    }

    private void parseWorkbook(
            byte[] content,
            RemoteFile file,
            Set<String> names,
            StructuredProbeResult result,
            int rowLimit
    ) throws Exception {
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            for (Sheet sheet : workbook) {
                Row header = sheet.getRow(sheet.getFirstRowNum());
                if (header == null) continue;
                List<String> headers = new ArrayList<>();
                for (int i = 0; i < header.getLastCellNum(); i++) {
                    String name = cleanColumnName(formatter.formatCellValue(header.getCell(i)));
                    headers.add(blank(name) ? "column_" + (i + 1) : uniqueColumn(name, headers));
                }
                List<Map<String, Object>> rows = new ArrayList<>();
                for (int rowIndex = header.getRowNum() + 1; rowIndex <= sheet.getLastRowNum() && rows.size() < rowLimit; rowIndex++) {
                    Row sourceRow = sheet.getRow(rowIndex);
                    if (sourceRow == null) continue;
                    Map<String, Object> row = new LinkedHashMap<>();
                    boolean populated = false;
                    for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                        Cell cell = sourceRow.getCell(columnIndex);
                        String value = cell == null ? "" : formatter.formatCellValue(cell);
                        if (!value.isBlank()) populated = true;
                        row.put(headers.get(columnIndex), value);
                    }
                    if (populated) rows.add(row);
                }
                String sourcePath = file.path + "#" + sheet.getSheetName();
                String tableName = uniqueName(fileStem(file.name) + "_" + sheet.getSheetName(), names);
                result.getTables().add(buildTable(tableName, file.name + " / " + sheet.getSheetName(), sourcePath, rows));
            }
        }
    }

    private StructuredProbeResult probeKafka(Map<String, Object> source) {
        String bootstrap = required(source, "Kafka bootstrap servers cannot be empty", "kafkaBootstrapServers", "bootstrapServers");
        String topic = required(source, "Kafka topic cannot be empty", "kafkaTopic", "topic");
        int sampleSize = Math.min(integer(first(source, "kafkaSampleSize"), 50), MAX_SAMPLE_ROWS);
        int pollTimeout = integer(first(source, "kafkaPollTimeoutMs"), 8_000);
        Properties properties = kafkaProperties(source, bootstrap);
        try (AdminClient admin = AdminClient.create(properties)) {
            if (!admin.listTopics().names().get(8, TimeUnit.SECONDS).contains(topic)) {
                throw new IllegalArgumentException("Kafka topic does not exist: " + topic);
            }
        } catch (Exception exception) {
            throw propagate("Kafka cluster or topic validation failed", exception);
        }
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, defaultText(first(source, "kafkaGroupId"), "metadata-probe-" + UUID.randomUUID()));
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, String.valueOf(sampleSize));
        List<Map<String, Object>> rows = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            List<PartitionInfo> infos = consumer.partitionsFor(topic, Duration.ofSeconds(8));
            List<TopicPartition> partitions = infos.stream().map(info -> new TopicPartition(topic, info.partition())).toList();
            consumer.assign(partitions);
            Map<TopicPartition, Long> beginning = consumer.beginningOffsets(partitions, Duration.ofSeconds(8));
            Map<TopicPartition, Long> end = consumer.endOffsets(partitions, Duration.ofSeconds(8));
            int perPartition = Math.max(1, (int) Math.ceil((double) sampleSize / Math.max(1, partitions.size())));
            for (TopicPartition partition : partitions) {
                consumer.seek(partition, Math.max(beginning.get(partition), end.get(partition) - perPartition));
            }
            long deadline = System.currentTimeMillis() + pollTimeout;
            while (rows.size() < sampleSize && System.currentTimeMillis() < deadline) {
                var records = consumer.poll(Duration.ofMillis(Math.min(1000, Math.max(1, deadline - System.currentTimeMillis()))));
                for (var record : records) {
                    for (Map<String, Object> payload : kafkaPayloadRows(record.value())) {
                        payload.put("_kafka_key", record.key());
                        payload.put("_kafka_partition", record.partition());
                        payload.put("_kafka_offset", record.offset());
                        payload.put("_kafka_timestamp", Instant.ofEpochMilli(record.timestamp()).toString());
                        rows.add(payload);
                        if (rows.size() >= sampleSize) break;
                    }
                    if (rows.size() >= sampleSize) break;
                }
                if (records.isEmpty()) break;
            }
        } catch (Exception exception) {
            throw propagate("Kafka message sampling failed", exception);
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("Kafka connection succeeded, but the topic has no readable sample messages");
        }
        StructuredProbeResult result = baseResult("kafka", "Kafka", bootstrap + "/" + topic);
        result.setResponsePreview(preview(writeJson(rows.get(0)), 4000));
        result.getTables().add(buildTable(safeName(topic), "Kafka topic " + topic, topic, rows));
        return result;
    }

    private Properties kafkaProperties(Map<String, Object> source, String bootstrap) {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "8000");
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "8000");
        String protocol = defaultText(first(source, "kafkaSecurityProtocol", "securityProtocol"), "PLAINTEXT");
        properties.put("security.protocol", protocol);
        if (protocol.contains("SASL")) {
            String mechanism = defaultText(first(source, "kafkaSaslMechanism", "saslMechanism"), "PLAIN");
            String username = required(source, "Kafka SASL username cannot be empty", "kafkaUsername", "username");
            String password = required(source, "Kafka SASL password cannot be empty", "kafkaPassword", "password");
            String module = mechanism.startsWith("SCRAM")
                    ? "org.apache.kafka.common.security.scram.ScramLoginModule"
                    : "org.apache.kafka.common.security.plain.PlainLoginModule";
            properties.put("sasl.mechanism", mechanism);
            properties.put("sasl.jaas.config", module + " required username=\"" + escapeJaas(username)
                    + "\" password=\"" + escapeJaas(password) + "\";");
        }
        return properties;
    }

    private Map<String, JsonNode> discoverCollections(JsonNode root) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        discoverCollections(root, "$", 0, result);
        if (result.isEmpty() && root != null && (root.isObject() || root.isValueNode())) result.put("$", root);
        return result;
    }

    private void discoverCollections(JsonNode node, String path, int depth, Map<String, JsonNode> result) {
        if (node == null || depth > 8) return;
        if (node.isArray()) {
            result.put(path, node);
            return;
        }
        if (!node.isObject()) return;
        node.fields().forEachRemaining(entry -> discoverCollections(entry.getValue(), path + "." + entry.getKey(), depth + 1, result));
    }

    private JsonNode resolvePath(JsonNode root, String path) {
        if (blank(path) || "$".equals(path)) return root;
        String normalized = path.startsWith("$.") ? path.substring(2) : path;
        JsonNode current = root;
        for (String segment : normalized.split("\\.")) {
            if (blank(segment)) continue;
            current = current == null ? null : current.path(segment);
        }
        return current;
    }

    private List<Map<String, Object>> rowsFromJson(JsonNode node, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (node == null || node.isNull()) return rows;
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (rows.size() >= limit) break;
                rows.add(rowFromJson(child));
            }
        } else {
            rows.add(rowFromJson(node));
        }
        return rows;
    }

    private Map<String, Object> rowFromJson(JsonNode node) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            flattenJson("", node, row);
        } else {
            row.put("value", jsonScalar(node));
        }
        return row;
    }

    private void flattenJson(String prefix, JsonNode node, Map<String, Object> target) {
        node.fields().forEachRemaining(entry -> {
            String key = cleanColumnName(prefix.isEmpty() ? entry.getKey() : prefix + "_" + entry.getKey());
            JsonNode value = entry.getValue();
            if (value.isObject()) flattenJson(key, value, target);
            else if (value.isArray()) target.put(key, writeJson(value));
            else target.put(key, jsonScalar(value));
        });
    }

    private Object jsonScalar(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isBoolean()) return node.booleanValue();
        if (node.isIntegralNumber()) return node.longValue();
        if (node.isFloatingPointNumber()) return node.decimalValue();
        return node.asText();
    }

    private List<Map<String, Object>> kafkaPayloadRows(String value) {
        if (blank(value)) return List.of(Map.of("value", ""));
        try {
            return rowsFromJson(objectMapper.readTree(value), MAX_SAMPLE_ROWS);
        } catch (Exception ignored) {
            return List.of(new LinkedHashMap<>(Map.of("value", value)));
        }
    }

    private StructuredTableData buildTable(String name, String comment, String sourcePath, List<Map<String, Object>> rows) {
        StructuredTableData data = new StructuredTableData();
        data.setSourcePath(sourcePath);
        data.setRows(rows);
        data.setColumns(inferColumns(rows));
        TableInfo table = new TableInfo();
        table.setTableName(safeName(name));
        table.setTableComment(comment);
        table.setSchemaName(sourcePath);
        table.setTableType("TABLE");
        table.setColumnCount(data.getColumns().size());
        table.setRowCount((long) rows.size());
        table.setDataSizeBytes(0L);
        table.setIndexSizeBytes(0L);
        table.setTotalSizeBytes(0L);
        table.setDataSizeFormatted("0 B");
        table.setIndexSizeFormatted("0 B");
        table.setTotalSizeFormatted("0 B");
        table.setColumns(data.getColumns());
        data.setTable(table);
        return data;
    }

    private StructuredTableData buildTable(
            String name,
            String comment,
            String sourcePath,
            List<Map<String, Object>> rows,
            List<String> headers
    ) {
        StructuredTableData data = buildTable(name, comment, sourcePath, rows);
        if (data.getColumns().isEmpty() && headers != null && !headers.isEmpty()) {
            Map<String, Object> schemaRow = new LinkedHashMap<>();
            headers.forEach(header -> schemaRow.put(header, null));
            data.setColumns(inferColumns(List.of(schemaRow)));
            data.getTable().setColumns(data.getColumns());
            data.getTable().setColumnCount(data.getColumns().size());
        }
        return data;
    }

    private List<ColumnInfo> inferColumns(List<Map<String, Object>> rows) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        rows.stream().limit(DEFAULT_SAMPLE_ROWS).forEach(row -> names.addAll(row.keySet()));
        List<ColumnInfo> columns = new ArrayList<>();
        int ordinal = 1;
        for (String name : names) {
            List<Object> values = rows.stream().map(row -> row.get(name)).filter(value -> value != null && !String.valueOf(value).isBlank()).limit(100).toList();
            String type = inferType(name, values);
            ColumnInfo column = new ColumnInfo();
            column.setColumnName(name);
            column.setColumnComment(name);
            column.setDataType(type);
            column.setColumnType(type);
            column.setLength(maxLength(values));
            column.setNullable(rows.stream().anyMatch(row -> row.get(name) == null || String.valueOf(row.get(name)).isBlank()));
            column.setPrimaryKey(false);
            column.setAutoIncrement(false);
            column.setUnique(false);
            column.setIndexed(false);
            column.setOrdinalPosition(ordinal++);
            columns.add(column);
        }
        return columns;
    }

    private String inferType(String columnName, List<Object> values) {
        if (values.isEmpty()) return "VARCHAR";
        String normalizedName = columnName == null ? "" : columnName.toLowerCase(Locale.ROOT);
        if (normalizedName.equals("id")
                || normalizedName.endsWith("_id")
                || normalizedName.endsWith("id")
                || normalizedName.endsWith("_code")
                || normalizedName.endsWith("code")
                || normalizedName.endsWith("_no")
                || normalizedName.contains("card")
                || normalizedName.contains("phone")) {
            return "VARCHAR";
        }
        if (values.stream().allMatch(value -> value instanceof Boolean || isBooleanText(value))) return "BOOLEAN";
        if (values.stream().allMatch(value -> value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || isLongText(value))) return "BIGINT";
        if (values.stream().allMatch(value -> value instanceof Number || isDecimalText(value))) return "DECIMAL";
        if (values.stream().allMatch(this::isDateTime)) return "TIMESTAMP";
        return "VARCHAR";
    }

    private long maxLength(List<Object> values) {
        return Math.max(1, values.stream().map(String::valueOf).mapToInt(String::length).max().orElse(255));
    }

    private boolean isBooleanText(Object value) {
        String text = String.valueOf(value).toLowerCase(Locale.ROOT);
        return "true".equals(text) || "false".equals(text);
    }

    private boolean isLongText(Object value) {
        try { Long.parseLong(String.valueOf(value)); return true; } catch (NumberFormatException ignored) { return false; }
    }

    private boolean isDecimalText(Object value) {
        try { new java.math.BigDecimal(String.valueOf(value)); return true; } catch (NumberFormatException ignored) { return false; }
    }

    private boolean isDateTime(Object value) {
        String text = String.valueOf(value);
        try { Instant.parse(text); return true; } catch (DateTimeParseException ignored) { }
        try { OffsetDateTime.parse(text); return true; } catch (DateTimeParseException ignored) { }
        try { LocalDateTime.parse(text); return true; } catch (DateTimeParseException ignored) { }
        try { LocalDate.parse(text); return true; } catch (DateTimeParseException ignored) { return false; }
    }

    private void applyApiAuthorization(Map<String, Object> source, HttpRequest.Builder request) {
        String authType = defaultText(first(source, "apiAuthType"), "none");
        if ("basic".equalsIgnoreCase(authType)) {
            String credential = required(source, "API Basic username cannot be empty", "apiUsername") + ":"
                    + required(source, "API Basic password cannot be empty", "apiPassword");
            request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8)));
        } else if ("bearer".equalsIgnoreCase(authType)) {
            String token = required(source, "API Bearer token cannot be empty", "apiToken");
            request.header("Authorization", token.startsWith("Bearer ") ? token : "Bearer " + token);
        } else if ("apiKey".equalsIgnoreCase(authType) && !"query".equalsIgnoreCase(first(source, "apiKeyPosition"))) {
            request.header(required(source, "API Key name cannot be empty", "apiKeyName"), required(source, "API Key value cannot be empty", "apiKeyValue"));
        } else if ("oauth2ClientCredentials".equalsIgnoreCase(authType)
                || "oauth2_client_credentials".equalsIgnoreCase(authType)) {
            request.header("Authorization", "Bearer " + requestOAuth2ClientCredentialsToken(source));
        }
    }

    /**
     * Exchanges a client-credentials grant only for the current connection
     * test. The client secret and returned access token are deliberately not
     * cached or persisted with the data-source configuration.
     */
    private String requestOAuth2ClientCredentialsToken(Map<String, Object> source) {
        String tokenUrl = required(source, "OAuth2 token service URL cannot be empty", "apiOAuthTokenUrl", "oauthTokenUrl");
        String clientId = required(source, "OAuth2 client ID cannot be empty", "apiOAuthClientId", "oauthClientId");
        String clientSecret = required(source, "OAuth2 client secret cannot be empty", "apiOAuthClientSecret", "oauthClientSecret");
        String scope = defaultText(first(source, "apiOAuthScope", "oauthScope"), "");
        String form = "grant_type=client_credentials" + (blank(scope) ? ""
                : "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8));
        String basicCredential = clientId + ":" + clientSecret;
        HttpRequest tokenRequest = HttpRequest.newBuilder(URI.create(tokenUrl))
                .timeout(Duration.ofSeconds(integer(first(source, "apiTimeoutSeconds"), 30)))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(basicCredential.getBytes(StandardCharsets.UTF_8)))
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OAuth2 token service returned HTTP " + response.statusCode());
            }
            String accessToken = objectMapper.readTree(response.body()).path("access_token").asText("").trim();
            if (accessToken.isEmpty()) throw new IllegalStateException("OAuth2 token service response does not contain access_token");
            return accessToken;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OAuth2 token request was interrupted", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) throw (IllegalStateException) e;
            throw new IllegalStateException("OAuth2 client credentials authentication failed", e);
        }
    }

    private String applyApiKeyQuery(Map<String, Object> source, String url) {
        if (!"apiKey".equalsIgnoreCase(first(source, "apiAuthType")) || !"query".equalsIgnoreCase(first(source, "apiKeyPosition"))) return url;
        String name = required(source, "API Key name cannot be empty", "apiKeyName");
        String value = required(source, "API Key value cannot be empty", "apiKeyValue");
        return url + (url.contains("?") ? "&" : "?") + URLEncoder.encode(name, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private StructuredProbeResult baseResult(String type, String product, String endpoint) {
        StructuredProbeResult result = new StructuredProbeResult();
        result.setSourceType(type);
        result.setProduct(product);
        result.setEndpoint(endpoint);
        return result;
    }

    private Map<String, String> parseStringMap(String json) {
        if (blank(json)) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() { });
        } catch (Exception exception) {
            throw new IllegalArgumentException("API headers must be a JSON object", exception);
        }
    }

    private List<String> parseStringList(String value) {
        if (blank(value)) return List.of();
        try {
            if (value.trim().startsWith("[")) return objectMapper.readValue(value, new TypeReference<List<String>>() { });
            return Pattern.compile("[,\\r\\n]").splitAsStream(value).map(String::trim).filter(item -> !item.isEmpty()).toList();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Collection paths must be a JSON array or comma-separated text", exception);
        }
    }

    private String cacheKey(Map<String, Object> source) {
        try {
            Map<String, String> connector = new TreeMap<>();
            String type = sourceType(source);
            connector.put("sourceType", type);
            switch (type) {
                case "api" -> {
                    putCacheValue(connector, source, "method", "apiMethod", "method");
                    putCacheValue(connector, source, "url", "apiUrl", "url");
                    putCacheValue(connector, source, "headers", "apiHeaders", "headers");
                    putCacheValue(connector, source, "body", "apiBody", "requestBody");
                    putCacheValue(connector, source, "authType", "apiAuthType");
                    putCacheValue(connector, source, "username", "apiUsername");
                    putCacheValue(connector, source, "password", "apiPassword");
                    putCacheValue(connector, source, "token", "apiToken");
                    putCacheValue(connector, source, "apiKeyName", "apiKeyName");
                    putCacheValue(connector, source, "apiKeyValue", "apiKeyValue");
                    putCacheValue(connector, source, "apiKeyPosition", "apiKeyPosition");
                    putCacheValue(connector, source, "collectionPaths", "apiCollectionPaths", "collectionPaths");
                    putCacheValue(connector, source, "timeoutSeconds", "apiTimeoutSeconds");
                }
                case "ftp" -> {
                    putCacheValue(connector, source, "protocol", "ftpProtocol", "protocol");
                    putCacheValue(connector, source, "host", "ftpHost", "hostname", "host");
                    putCacheValue(connector, source, "port", "ftpPort", "port");
                    putCacheValue(connector, source, "path", "ftpPath", "remotePath", "path");
                    putCacheValue(connector, source, "username", "ftpUsername", "username");
                    putCacheValue(connector, source, "password", "ftpPassword", "password");
                    putCacheValue(connector, source, "passiveMode", "ftpPassiveMode");
                    putCacheValue(connector, source, "recursive", "ftpRecursive");
                    putCacheValue(connector, source, "singleFile", "ftpSingleFile");
                    putCacheValue(connector, source, "filePattern", "ftpFilePattern");
                    putCacheValue(connector, source, "charset", "ftpCharset");
                    putCacheValue(connector, source, "delimiter", "ftpDelimiter");
                }
                case "minio" -> {
                    putCacheValue(connector, source, "endpoint", "minioEndpoint", "endpoint", "host");
                    putCacheValue(connector, source, "bucket", "minioBucket", "bucket", "database", "dbName");
                    putCacheValue(connector, source, "prefix", "minioPrefix", "objectPrefix", "prefix", "path");
                    putCacheValue(connector, source, "accessKey", "minioAccessKey", "accessKey", "username");
                    putCacheValue(connector, source, "secretKey", "minioSecretKey", "secretKey", "password");
                    putCacheValue(connector, source, "useSSL", "minioUseSSL");
                    putCacheValue(connector, source, "singleFile", "minioSingleFile");
                    putCacheValue(connector, source, "filePattern", "minioFilePattern");
                    putCacheValue(connector, source, "charset", "ftpCharset");
                    putCacheValue(connector, source, "delimiter", "ftpDelimiter");
                }
                case "kafka" -> {
                    putCacheValue(connector, source, "bootstrapServers", "kafkaBootstrapServers", "bootstrapServers");
                    putCacheValue(connector, source, "topic", "kafkaTopic", "topic");
                    putCacheValue(connector, source, "groupId", "kafkaGroupId");
                    putCacheValue(connector, source, "securityProtocol", "kafkaSecurityProtocol", "securityProtocol");
                    putCacheValue(connector, source, "saslMechanism", "kafkaSaslMechanism", "saslMechanism");
                    putCacheValue(connector, source, "username", "kafkaUsername", "username");
                    putCacheValue(connector, source, "password", "kafkaPassword", "password");
                    putCacheValue(connector, source, "sampleSize", "kafkaSampleSize");
                    putCacheValue(connector, source, "pollTimeoutMs", "kafkaPollTimeoutMs");
                }
                default -> connector.put("configuration", objectMapper.writeValueAsString(new TreeMap<>(source)));
            }
            byte[] bytes = objectMapper.writeValueAsBytes(connector);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot build structured source cache key", exception);
        }
    }

    private void putCacheValue(
            Map<String, String> target,
            Map<String, Object> source,
            String canonicalName,
            String... aliases
    ) {
        target.put(canonicalName, defaultText(first(source, aliases), ""));
    }

    private byte[] readLimited(InputStream input, int maxBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > maxBytes) throw new IllegalStateException("Probe content exceeds " + maxBytes + " bytes");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private Predicate<String> glob(String glob) {
        String regex = "^" + Pattern.quote(glob).replace("*", "\\E.*\\Q").replace("?", "\\E.\\Q") + "$";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        return value -> pattern.matcher(value).matches();
    }

    private Predicate<String> regex(String expression) {
        Pattern pattern = Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
        return value -> pattern.matcher(value).matches();
    }

    private Charset ftpCharset(Map<String, Object> source) {
        return Charset.forName(defaultText(first(source, "ftpCharset"), "UTF-8"));
    }

    /**
     * Opt-in support for a registered API that intentionally targets this same
     * platform.  It never persists a browser token and refuses to forward one
     * to any non-loopback destination, so an external connector cannot obtain
     * a user's platform session by setting this flag.
     */
    private void applyCurrentSessionToken(Map<String, Object> source, URI endpoint, HttpRequest.Builder request) {
        if (!bool(source, "apiUseCurrentSession", false)) return;
        String host = defaultText(endpoint.getHost(), "").toLowerCase(Locale.ROOT);
        if (!Set.of("localhost", "127.0.0.1", "::1").contains(host)) {
            throw new IllegalArgumentException("当前会话令牌只能转发至本机平台接口");
        }
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            throw new IllegalStateException("当前会话令牌不可用，请在已登录的平台页面中操作");
        }
        String token = servletAttributes.getRequest().getHeader("token");
        if (blank(token)) {
            throw new IllegalStateException("当前会话令牌不可用，请重新登录后重试");
        }
        request.header("token", token.trim());
    }

    private boolean isSupportedFile(String name) { return FILE_EXTENSIONS.contains(extension(name)); }
    private String extension(String name) { int index = name.lastIndexOf('.'); return index < 0 ? "" : name.substring(index + 1).toLowerCase(Locale.ROOT); }
    private String fileStem(String name) { int index = name.lastIndexOf('.'); return index < 0 ? name : name.substring(0, index); }
    private String joinPath(String parent, String child) { return (parent.endsWith("/") ? parent : parent + "/") + child; }
    private String pathName(String path) { if ("$".equals(path)) return "response"; int index = path.lastIndexOf('.'); return index < 0 ? path : path.substring(index + 1); }
    private String cleanColumnName(String value) {
        String normalized = defaultText(value, "column").trim()
                .replaceAll("[^\\p{L}\\p{N}_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) normalized = "column";
        if (Character.isDigit(normalized.charAt(0))) normalized = "f_" + normalized;
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    private String safeName(String value) {
        String normalized = SAFE_NAME.matcher(defaultText(value, "data")).replaceAll("_").replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "").toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) normalized = "data";
        if (Character.isDigit(normalized.charAt(0))) normalized = "f_" + normalized;
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    private String uniqueName(String candidate, Set<String> names) {
        String base = safeName(candidate);
        String value = base;
        int suffix = 2;
        while (!names.add(value)) value = base + "_" + suffix++;
        return value;
    }

    private String uniqueColumn(String candidate, List<String> names) {
        String value = candidate;
        int suffix = 2;
        while (names.contains(value)) value = candidate + "_" + suffix++;
        return value;
    }

    private String escapeJaas(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private String preview(String value, int length) { return value == null || value.length() <= length ? value : value.substring(0, length) + "..."; }
    private boolean containsIgnoreCase(String value, String keyword) { return value != null && value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)); }
    private String writeJson(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception exception) { return String.valueOf(value); } }
    private String sourceType(Map<String, Object> source) { return defaultText(first(source, "dbType", "db_type", "databaseType", "dataSourceType"), "").toLowerCase(Locale.ROOT); }
    private String required(Map<String, Object> source, String message, String... keys) { String value = first(source, keys); if (blank(value)) throw new IllegalArgumentException(message); return value; }
    private String first(Map<String, Object> source, String... keys) { for (String key : keys) { Object value = source.get(key); if (value != null && !String.valueOf(value).trim().isEmpty()) return String.valueOf(value).trim(); } return null; }
    private String defaultText(String value, String fallback) { return blank(value) ? fallback : value; }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private boolean bool(Map<String, Object> source, String key, boolean fallback) { String value = first(source, key); return blank(value) ? fallback : Set.of("true", "1", "yes", "on").contains(value.toLowerCase(Locale.ROOT)); }
    private int discoveryLimit(Map<String, Object> source) {
        int configured = integer(first(source, "structuredDiscoveryLimit", "discoveryLimit", "maxDiscoveredFiles"),
                DEFAULT_MAX_DISCOVERED_FILES);
        return Math.max(configured, 0);
    }
    private int apiProbeTimeoutSeconds(Map<String, Object> source) {
        String value = first(source, "apiTimeoutSeconds", "connectTimeoutSeconds", "connectTimeout", "readTimeout");
        if (blank(value)) return 30;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(\\d+)").matcher(value.trim());
        if (!matcher.find()) return 30;
        try {
            return Math.max(1, Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException ignored) {
            return 30;
        }
    }
    private int integer(String value, int fallback) { try { return blank(value) ? fallback : Integer.parseInt(value); } catch (NumberFormatException exception) { throw new IllegalArgumentException("Invalid numeric value: " + value, exception); } }
    private IllegalStateException propagate(String message, Exception exception) { Throwable cause = exception instanceof RemoteReadException remote ? remote.unwrap() : exception; return new IllegalStateException(message + ": " + defaultText(cause.getMessage(), cause.getClass().getSimpleName()), cause); }

    private record CachedProbe(long createdAt, StructuredProbeResult result) { }
    record RemoteFile(String path, String name, long size) { }
    private record DelimitedData(List<String> headers, List<Map<String, Object>> rows) { }
    private record JsonSampleCollection(String path, List<Map<String, Object>> rows) { }
    record RemoteFileProbeStats(int matchedFiles, List<String> skippedFiles) { }
    @FunctionalInterface interface RemoteStreamOpener { InputStream open(RemoteFile file); }
    private static final class RemoteReadException extends RuntimeException { private RemoteReadException(Exception cause) { super(cause); } private Exception unwrap() { return (Exception) getCause(); } }

    static final class PendingFtpInputStream extends java.io.FilterInputStream {
        private final FTPClient client;
        private boolean reachedEnd;
        private boolean closed;
        PendingFtpInputStream(InputStream input, FTPClient client) { super(input); this.client = client; }
        @Override public int read() throws java.io.IOException {
            int value = super.read();
            reachedEnd = value < 0;
            return value;
        }
        @Override public int read(byte[] buffer, int offset, int length) throws java.io.IOException {
            int count = super.read(buffer, offset, length);
            reachedEnd = count < 0;
            return count;
        }
        @Override public void close() throws java.io.IOException {
            if (closed) return;
            closed = true;
            super.close();
            if (reachedEnd) {
                if (!client.completePendingCommand()) {
                    throw new java.io.IOException("FTP transfer did not complete: " + client.getReplyString());
                }
            } else if (!client.abort()) {
                // RFC 959 permits a server to reply 426 (transfer aborted)
                // followed by a positive completion response to ABOR.  Commons
                // Net's abort() returns false for that first, expected reply;
                // consume the final reply so it is not mistaken for the next
                // RETR and do not turn an intentional sample stop into a
                // failed connectivity test.
                int abortReply = client.getReplyCode();
                if (abortReply != 426 || !FTPReply.isPositiveCompletion(client.getReply())) {
                    throw new java.io.IOException("FTP sample transfer could not be aborted: " + client.getReplyString());
                }
            }
        }
    }
}
