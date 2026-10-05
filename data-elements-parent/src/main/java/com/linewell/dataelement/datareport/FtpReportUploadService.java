package com.linewell.dataelement.datareport;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPSClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Server-side adapter for report-file delivery.
 *
 * <p>It resolves the active tenant's ODS / FJGHCC domain binding on every
 * request, then reads the associated FTP-family datasource from the
 * authoritative datasource table.  Credentials stay inside the backend and
 * no status is reported as successful until the remote upload API confirms it.
 * It intentionally does not write report rows into the platform's test1
 * report tables.</p>
 */
@Slf4j
@Service
public class FtpReportUploadService {

    static final String ODS_LAYER_CODE = "ods";
    static final String NON_STRUCTURED_DOMAIN_CODE = "fjghcc";
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final DateTimeFormatter REMOTE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbcTemplate;
    private final DataSourceConnectionPropertyResolver connectionProperties;

    public FtpReportUploadService(
            JdbcTemplate jdbcTemplate,
            DataSourceConnectionPropertyResolver connectionProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.connectionProperties = connectionProperties;
    }

    public Map<String, Object> upload(String reportDatasourceId, String tableName, MultipartFile file) {
        String transferId = UUID.randomUUID().toString().replace("-", "");
        String tenantId = TenantContext.requireTenantId();
        log.info("[report-ftp][{}] Received report upload: tenantId={}, reportDatasourceId={}, requestedTableName={}, originalFileName={}, contentType={}, sizeBytes={}",
                transferId, tenantId, safeLogValue(reportDatasourceId), safeLogValue(tableName),
                file == null ? "" : safeLogValue(file.getOriginalFilename()),
                file == null ? "" : safeLogValue(file.getContentType()), file == null ? 0 : file.getSize());
        try {
            validateFile(file);
        } catch (RuntimeException exception) {
            log.warn("[report-ftp][{}] Upload validation failed: errorType={}, message={}",
                    transferId, exception.getClass().getSimpleName(), safeLogValue(exception.getMessage()));
            throw exception;
        }
        requireUploadMode(tenantId, reportDatasourceId);
        String registeredTableName = requireRegisteredTable(tenantId, reportDatasourceId, tableName);
        log.info("[report-ftp][{}] Upload source validated: tenantId={}, reportDatasourceId={}, registeredTableName={}",
                transferId, tenantId, reportDatasourceId, registeredTableName);

        TargetDatasource target = resolveFjghccTarget(tenantId);
        Map<String, Object> targetConfig = connectionProperties.resolve(tenantId, target.datasourceId());
        if (!"ftp".equalsIgnoreCase(target.dbType())) {
            throw new IllegalStateException("ODS 数据域 FJGHCC 关联的数据源不是 FTP 数据源，不能接收上报文件");
        }

        String protocol = lower(text(targetConfig, "ftpProtocol", "protocol"));
        if (protocol.isBlank()) protocol = "ftp";
        if (!List.of("ftp", "ftps", "sftp").contains(protocol)) {
            throw new IllegalStateException("ODS 数据域 FJGHCC 的 FTP 协议配置不受支持");
        }
        String host = required(targetConfig, "FTP 服务地址未配置", "ftpHost", "hostname", "host");
        int port = port(targetConfig, "sftp".equals(protocol) ? 22 : 21);
        String remoteDirectory = reportDirectory(
                remoteDirectory(text(targetConfig, "ftpPath", "remotePath", "path")),
                reportDatasourceId,
                registeredTableName
        );
        String remoteFileName = remoteFileName(file.getOriginalFilename());
        String configuredUsername = text(targetConfig, "ftpUsername", "username");
        String effectiveUsername = configuredUsername.isBlank() && !"sftp".equals(protocol)
                ? "anonymous" : configuredUsername;
        boolean passwordConfigured = !text(targetConfig, "ftpPassword", "password").isBlank();

        log.info("[report-ftp][{}] Resolved delivery target: targetDatasourceId={}, targetDatasourceName={}, protocol={}, endpoint={}, username={}, passwordConfigured={}, remoteDirectory={}, remoteFilePath={}, passiveMode={}, charset={}, connectTimeoutMillis={}, dataTimeoutSeconds={}",
                transferId, target.datasourceId(), safeLogValue(target.datasourceName()), protocol,
                endpoint(protocol, host, port), maskAccount(effectiveUsername), passwordConfigured,
                remoteDirectory, joinRemotePath(remoteDirectory, remoteFileName),
                "sftp".equals(protocol) ? "n/a" : bool(targetConfig, true, "ftpPassiveMode", "passiveMode"),
                "sftp".equals(protocol) ? "n/a" : defaultLogValue(text(targetConfig, "ftpCharset", "charset"), "platform-default"),
                CONNECT_TIMEOUT_MILLIS, "sftp".equals(protocol) ? "n/a" : 30);

        try {
            if ("sftp".equals(protocol)) {
                uploadSftp(transferId, targetConfig, host, port, remoteDirectory, remoteFileName, file);
            } else {
                uploadFtp(transferId, targetConfig, protocol, host, port, remoteDirectory, remoteFileName, file);
            }
        } catch (Exception exception) {
            // Keep transport details out of the API response; the server log retains safe diagnostics.
            log.warn("[report-ftp][{}] Remote delivery failed: tenantId={}, reportDatasourceId={}, targetDatasourceId={}, protocol={}, endpoint={}, remoteFilePath={}, errorType={}, message={}",
                    transferId, tenantId, reportDatasourceId, target.datasourceId(), protocol, endpoint(protocol, host, port),
                    joinRemotePath(remoteDirectory, remoteFileName), exception.getClass().getSimpleName(),
                    safeLogValue(exception.getMessage()), exception);
            throw new IllegalStateException("上报文件未能传入 FJGHCC 数据域的 FTP 存储，请检查目标数据源连通性、目录权限和可用空间");
        }

        log.info("[report-ftp][{}] Remote delivery succeeded: tenantId={}, reportDatasourceId={}, targetDatasourceId={}, endpoint={}, remoteFilePath={}, sizeBytes={}",
                transferId, tenantId, reportDatasourceId, target.datasourceId(), endpoint(protocol, host, port),
                joinRemotePath(remoteDirectory, remoteFileName), file.getSize());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("syncStatus", "success");
        result.put("targetDomainCode", "FJGHCC");
        result.put("targetDomainName", "非结构化存储");
        result.put("targetDatasourceId", target.datasourceId());
        result.put("targetDatasourceName", target.datasourceName());
        result.put("remoteFileName", remoteFileName);
        result.put("uploadedAt", Instant.now().toString());
        return result;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择需要上报的数据文件");
        if (file.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("上报文件不能超过 10 MiB");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (!(lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") || lowerName.endsWith(".csv"))) {
            throw new IllegalArgumentException("只支持上传 XLSX、XLS 或 CSV 数据文件");
        }
    }

    private void requireUploadMode(String tenantId, String datasourceId) {
        if (datasourceId == null || datasourceId.isBlank()) throw new IllegalArgumentException("数据源不能为空");
        List<Map<String, Object>> sources = jdbcTemplate.queryForList("""
                select tid, db_name, db_type
                  from db_datasource_t
                 where tid = ? and tenant_id = ? and is_del = 0
                """, datasourceId.trim(), tenantId);
        if (sources.isEmpty()) throw new IllegalArgumentException("数据源不存在或不属于当前租户");
        String accessMode = lower(text(connectionProperties.resolve(tenantId, datasourceId), "accessMode", "access_mode"));
        if ("report".equals(accessMode)) accessMode = "upload";
        if (!"upload".equals(accessMode)) {
            throw new IllegalArgumentException("只有数据上报方式的数据源可以上传文件到 FTP 存储");
        }
    }

    /**
     * Accept only a registered table that belongs to the source selected in
     * the reporting workbench. The resulting identifier becomes a remote
     * directory segment, so it is constrained to an ordinary English table
     * name rather than allowing an arbitrary browser-supplied path.
     */
    private String requireRegisteredTable(String tenantId, String datasourceId, String tableName) {
        String normalized = tableName == null ? "" : tableName.trim();
        if (!normalized.matches("[A-Za-z][A-Za-z0-9_]{0,127}")) {
            throw new IllegalArgumentException("数据表英文名不正确，无法确定上报目录");
        }
        List<String> registered = jdbcTemplate.queryForList("""
                select table_name
                  from db_table_t
                 where datasource_id = ? and tenant_id = ? and is_del = 0
                   and lower(trim(table_name)) = lower(?)
                """, String.class, datasourceId.trim(), tenantId, normalized);
        if (registered.isEmpty()) {
            throw new IllegalArgumentException("所选数据表未登记在当前数据源下，无法上报文件");
        }
        return registered.getFirst();
    }

    private TargetDatasource resolveFjghccTarget(String tenantId) {
        List<String> domains = jdbcTemplate.queryForList("""
                select domain_node.tid
                  from sym_dict_t domain_node
                  join sym_dict_t ods_node on ods_node.tid = domain_node.parent_id
                 where domain_node.tenant_id = ? and domain_node.is_del = 0
                   and ods_node.tenant_id = domain_node.tenant_id and ods_node.is_del = 0
                   and lower(trim(ods_node.dict_code)) = ?
                   and lower(trim(domain_node.dict_code)) = ?
                 order by domain_node.sort_no asc, domain_node.tid asc
                """, String.class, tenantId, ODS_LAYER_CODE, NON_STRUCTURED_DOMAIN_CODE);
        if (domains.isEmpty()) {
            throw new IllegalStateException("未配置 ODS 数据域 FJGHCC（非结构化存储）");
        }
        List<TargetDatasource> targets = jdbcTemplate.query("""
                select datasource.tid, datasource.db_name, datasource.db_type
                  from dwm_center_layer_source_t relation
                  join db_datasource_t datasource
                    on datasource.tid = relation.datasource_id
                   and datasource.tenant_id = relation.tenant_id
                   and datasource.is_del = 0
                   and coalesce(datasource.show_connect, 1) = 1
                 where relation.tenant_id = ? and relation.is_del = 0
                   and relation.target_type = 'domain' and relation.target_id = ?
                 order by relation.updated_time desc, relation.tid desc
                """, (rs, row) -> new TargetDatasource(rs.getString(1), rs.getString(2), rs.getString(3)), tenantId, domains.getFirst());
        if (targets.isEmpty()) {
            throw new IllegalStateException("ODS 数据域 FJGHCC 尚未关联可连接的 FTP 数据源");
        }
        return targets.getFirst();
    }

    private void uploadSftp(
            String transferId,
            Map<String, Object> config,
            String host,
            int port,
            String directory,
            String remoteFileName,
            MultipartFile file
    ) throws Exception {
        String username = required(config, "SFTP 用户名未配置", "ftpUsername", "username");
        String password = text(config, "ftpPassword", "password");
        Session session = null;
        ChannelSftp channel = null;
        try {
            log.info("[report-ftp][{}] SFTP session connect started: endpoint={}, username={}",
                    transferId, endpoint("sftp", host, port), maskAccount(username));
            session = new JSch().getSession(username, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(CONNECT_TIMEOUT_MILLIS);
            log.info("[report-ftp][{}] SFTP session connected: endpoint={}", transferId, endpoint("sftp", host, port));
            log.info("[report-ftp][{}] SFTP channel connect started", transferId);
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(CONNECT_TIMEOUT_MILLIS);
            log.info("[report-ftp][{}] SFTP channel connected", transferId);
            ensureSftpDirectory(transferId, channel, directory);
            Instant startedAt = Instant.now();
            String remotePath = joinRemotePath(directory, remoteFileName);
            log.info("[report-ftp][{}] SFTP file upload started: remotePath={}, sizeBytes={}",
                    transferId, remotePath, file.getSize());
            try (InputStream input = file.getInputStream()) {
                channel.put(input, remoteFileName, ChannelSftp.OVERWRITE);
            }
            log.info("[report-ftp][{}] SFTP file upload completed: remotePath={}, sizeBytes={}, elapsedMillis={}",
                    transferId, remotePath, file.getSize(), Duration.between(startedAt, Instant.now()).toMillis());
        } finally {
            if (channel != null && channel.isConnected()) {
                log.info("[report-ftp][{}] SFTP channel disconnecting", transferId);
                channel.disconnect();
                log.info("[report-ftp][{}] SFTP channel disconnected", transferId);
            }
            if (session != null && session.isConnected()) {
                log.info("[report-ftp][{}] SFTP session disconnecting", transferId);
                session.disconnect();
                log.info("[report-ftp][{}] SFTP session disconnected", transferId);
            }
        }
    }

    private void uploadFtp(
            String transferId,
            Map<String, Object> config,
            String protocol,
            String host,
            int port,
            String directory,
            String remoteFileName,
            MultipartFile file
    ) throws Exception {
        FTPClient client = "ftps".equals(protocol) ? new FTPSClient(port == 990) : new FTPClient();
        try {
            client.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            client.setDataTimeout(Duration.ofSeconds(30));
            log.info("[report-ftp][{}] {} connection started: endpoint={}, clientType={}",
                    transferId, protocol.toUpperCase(Locale.ROOT), endpoint(protocol, host, port), client.getClass().getSimpleName());
            client.connect(host, port);
            log.info("[report-ftp][{}] {} connection established: {}", transferId,
                    protocol.toUpperCase(Locale.ROOT), ftpReply(client));
            String username = text(config, "ftpUsername", "username");
            String password = text(config, "ftpPassword", "password");
            boolean configuredPassword = !password.isBlank();
            boolean anonymousLogin = username.isBlank();
            if (username.isBlank()) username = "anonymous";
            if (password.isBlank()) password = "anonymous@";
            log.info("[report-ftp][{}] {} authentication started: username={}, passwordConfigured={}, anonymousLogin={}",
                    transferId, protocol.toUpperCase(Locale.ROOT), maskAccount(username), configuredPassword, anonymousLogin);
            if (!client.login(username, password)) {
                throw new IllegalStateException("FTP login failed: " + ftpReply(client));
            }
            log.info("[report-ftp][{}] {} authentication succeeded: {}", transferId,
                    protocol.toUpperCase(Locale.ROOT), ftpReply(client));
            if (client instanceof FTPSClient ftps) {
                log.info("[report-ftp][{}] FTPS protection negotiation started", transferId);
                ftps.execPBSZ(0);
                log.info("[report-ftp][{}] FTPS PBSZ configured: {}", transferId, ftpReply(client));
                ftps.execPROT("P");
                log.info("[report-ftp][{}] FTPS data-channel protection configured: {}", transferId, ftpReply(client));
            }
            boolean passiveMode = bool(config, true, "ftpPassiveMode", "passiveMode");
            if (passiveMode) client.enterLocalPassiveMode();
            log.info("[report-ftp][{}] {} transfer mode configured: passiveMode={}", transferId,
                    protocol.toUpperCase(Locale.ROOT), passiveMode);
            client.setFileType(FTP.BINARY_FILE_TYPE);
            log.info("[report-ftp][{}] {} binary transfer type configured: {}", transferId,
                    protocol.toUpperCase(Locale.ROOT), ftpReply(client));
            String charset = text(config, "ftpCharset", "charset");
            if (!charset.isBlank()) {
                client.setControlEncoding(charset);
                log.info("[report-ftp][{}] {} control encoding configured: charset={}", transferId,
                        protocol.toUpperCase(Locale.ROOT), safeLogValue(charset));
            }
            ensureFtpDirectory(transferId, client, directory);
            Instant startedAt = Instant.now();
            String remotePath = joinRemotePath(directory, remoteFileName);
            log.info("[report-ftp][{}] {} file upload started: remotePath={}, sizeBytes={}", transferId,
                    protocol.toUpperCase(Locale.ROOT), remotePath, file.getSize());
            try (InputStream input = file.getInputStream()) {
                if (!client.storeFile(remoteFileName, input)) {
                    throw new IllegalStateException("FTP storeFile rejected the upload: " + ftpReply(client));
                }
            }
            log.info("[report-ftp][{}] {} file upload completed: remotePath={}, sizeBytes={}, elapsedMillis={}, {}", transferId,
                    protocol.toUpperCase(Locale.ROOT), remotePath, file.getSize(),
                    Duration.between(startedAt, Instant.now()).toMillis(), ftpReply(client));
        } finally {
            if (client.isConnected()) {
                try {
                    log.info("[report-ftp][{}] {} logout started", transferId, protocol.toUpperCase(Locale.ROOT));
                    boolean loggedOut = client.logout();
                    log.info("[report-ftp][{}] {} logout completed: success={}, {}", transferId,
                            protocol.toUpperCase(Locale.ROOT), loggedOut, ftpReply(client));
                } finally {
                    log.info("[report-ftp][{}] {} disconnecting", transferId, protocol.toUpperCase(Locale.ROOT));
                    client.disconnect();
                    log.info("[report-ftp][{}] {} disconnected", transferId, protocol.toUpperCase(Locale.ROOT));
                }
            }
        }
    }

    private String remoteFileName(String originalName) {
        String clean = originalName == null ? "" : originalName.replace('\\', '/');
        int slash = clean.lastIndexOf('/');
        if (slash >= 0) clean = clean.substring(slash + 1);
        clean = clean.replaceAll("[\\r\\n\\u0000]", "_").trim();
        int dot = clean.lastIndexOf('.');
        String extension = dot >= 0 ? clean.substring(dot).toLowerCase(Locale.ROOT) : ".xlsx";
        if (!List.of(".xlsx", ".xls", ".csv").contains(extension)) extension = ".xlsx";
        // The original filename remains in the report record.  The FTP name
        // must be portable across legacy servers whose control-channel charset
        // cannot reliably accept Chinese, spaces, or punctuation in STOR.
        return "report_" + REMOTE_TIME.format(Instant.now()) + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + extension;
    }

    private String remoteDirectory(String value) {
        String directory = value == null ? "" : value.trim().replace('\\', '/');
        if (directory.isBlank()) return "/";
        String absolute = directory.startsWith("/") ? directory : "/" + directory;
        StringBuilder result = new StringBuilder();
        for (String segment : absolute.split("/")) {
            if (segment.isBlank()) continue;
            if (".".equals(segment) || "..".equals(segment) || segment.chars().anyMatch(character -> character == 0)) {
                throw new IllegalArgumentException("FTP 根目录配置不正确");
            }
            result.append('/').append(segment);
        }
        return result.isEmpty() ? "/" : result.toString();
    }

    private String reportDirectory(String configuredRoot, String datasourceId, String tableName) {
        return joinRemotePath(joinRemotePath(configuredRoot, safeDirectorySegment(datasourceId, "数据源 ID")),
                safeDirectorySegment(tableName, "数据表英文名"));
    }

    private String safeDirectorySegment(String value, String label) {
        String segment = value == null ? "" : value.trim();
        if (!segment.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException(label + "不正确，无法确定上报目录");
        }
        return segment;
    }

    private void ensureSftpDirectory(String transferId, ChannelSftp channel, String directory) throws Exception {
        String current = "/";
        for (String segment : remoteDirectory(directory).split("/")) {
            if (segment.isBlank()) continue;
            current = joinRemotePath(current, segment);
            log.info("[report-ftp][{}] SFTP directory check: path={}", transferId, current);
            try {
                channel.cd(current);
                log.info("[report-ftp][{}] SFTP directory ready: path={}, created=false", transferId, current);
            } catch (SftpException missing) {
                if (missing.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw missing;
                log.info("[report-ftp][{}] SFTP directory missing; creating: path={}", transferId, current);
                channel.mkdir(current);
                channel.cd(current);
                log.info("[report-ftp][{}] SFTP directory ready: path={}, created=true", transferId, current);
            }
        }
    }

    /**
     * Enters and provisions one directory level at a time.
     *
     * <p>Some FTP servers reject {@code MKD /a/b/c} even when every parent is
     * writable, while accepting {@code CWD a; MKD b; CWD b}.  Do not rely on a
     * non-standard {@code MKD -p} extension: Apache Commons Net sends standard
     * FTP commands, so every {@code MKD} here deliberately has one segment.
     * The final working directory also lets {@link #uploadFtp} store the file
     * by its safe relative filename.</p>
     */
    void ensureFtpDirectory(String transferId, FTPClient client, String directory) throws Exception {
        String normalizedDirectory = remoteDirectory(directory);
        if (!client.changeWorkingDirectory("/")) {
            throw new IllegalStateException("FTP root directory cannot be opened: " + ftpReply(client));
        }
        String currentPath = "/";
        for (String segment : normalizedDirectory.split("/")) {
            if (segment.isBlank()) continue;
            currentPath = joinRemotePath(currentPath, segment);
            log.info("[report-ftp][{}] FTP directory check: path={}, segment={}", transferId, currentPath, segment);
            if (client.changeWorkingDirectory(segment)) {
                log.info("[report-ftp][{}] FTP directory ready: path={}, created=false, {}", transferId, currentPath, ftpReply(client));
                continue;
            }
            log.info("[report-ftp][{}] FTP directory missing; creating one level: path={}, segment={}, {}",
                    transferId, currentPath, segment, ftpReply(client));
            boolean created = client.makeDirectory(segment);
            // A second worker can create this level between CWD and MKD.  The
            // authoritative check is that we can enter it afterwards.
            if (!client.changeWorkingDirectory(segment)) {
                throw new IllegalStateException("FTP directory is unavailable: path=" + currentPath + ", " + ftpReply(client));
            }
            log.info("[report-ftp][{}] FTP directory ready: path={}, created={}, {}",
                    transferId, currentPath, created, ftpReply(client));
        }
    }

    private String joinRemotePath(String directory, String fileName) {
        return (directory.endsWith("/") ? directory : directory + "/") + fileName;
    }

    private int port(Map<String, Object> source, int fallback) {
        String value = text(source, "ftpPort", "port");
        if (value.isBlank()) return fallback;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1 || parsed > 65535) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("FTP 端口配置不正确");
        }
    }

    private String required(Map<String, Object> source, String message, String... keys) {
        String value = text(source, keys);
        if (value.isBlank()) throw new IllegalArgumentException(message);
        return value;
    }

    private String text(Map<String, Object> source, String... keys) {
        if (source == null) return "";
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value).trim();
        }
        return "";
    }

    private String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean bool(Map<String, Object> source, boolean fallback, String... keys) {
        String value = lower(text(source, keys));
        if (value.isBlank()) return fallback;
        return List.of("true", "1", "yes", "y", "是").contains(value);
    }

    private String endpoint(String protocol, String host, int port) {
        return protocol + "://" + safeLogValue(host) + ":" + port;
    }

    private String maskAccount(String username) {
        String value = safeLogValue(username);
        if (value.isBlank()) return "<not-configured>";
        if (value.length() == 1) return "*";
        if (value.length() == 2) return value.charAt(0) + "*";
        return value.charAt(0) + "***" + value.charAt(value.length() - 1);
    }

    private String ftpReply(FTPClient client) {
        return "replyCode=" + client.getReplyCode() + ", replyMessage=" + safeLogValue(client.getReplyString());
    }

    private String defaultLogValue(String value, String fallback) {
        String result = safeLogValue(value);
        return result.isBlank() ? fallback : result;
    }

    /** Prevent line injection and keep values that can originate remotely out of credential-shaped log text. */
    private String safeLogValue(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("[\\r\\n\\u0000]+", " ").trim();
        normalized = normalized.replaceAll("(?i)(password|passwd|pwd)\\s*[=:]\\s*[^\\s,;]+", "$1=******");
        return normalized.length() > 500 ? normalized.substring(0, 500) + "..." : normalized;
    }

    private record TargetDatasource(String datasourceId, String datasourceName, String dbType) { }
}
