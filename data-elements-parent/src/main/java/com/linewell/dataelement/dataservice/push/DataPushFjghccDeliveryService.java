package com.linewell.dataelement.dataservice.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPSClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The platform owns the FJGHCC lookup and performs the file write itself.
 * This prevents the relay from carrying an environment-specific FTP address,
 * directory, username, or password.  The active tenant's ODS/FJGHCC binding
 * is looked up for every delivery, so a production binding can differ from
 * development without changing relay configuration.
 */
@Service
public class DataPushFjghccDeliveryService {
    private static final String ODS = "ods";
    private static final String FJGHCC = "fjghcc";
    private static final int TIMEOUT = 15_000;
    private final TenantDataSourceRegistry tenants;
    private final DataSourceConnectionPropertyResolver connections;
    private final ObjectMapper json;

    public DataPushFjghccDeliveryService(TenantDataSourceRegistry tenants, DataSourceConnectionPropertyResolver connections, ObjectMapper json) {
        this.tenants = tenants;
        this.connections = connections;
        this.json = json;
    }

    public void requireTarget(String tenantId) { target(tenantId); }

    public Map<String, Object> deliver(String tenantId, String datasourceId, String tableName, String deliveryId,
                                       List<Map<String, Object>> records, long schemaVersion) {
        Target target = target(tenantId);
        String directory = join(join(root(target.config()), segment(datasourceId, "数据源 ID")), segment(tableName, "数据表英文名"));
        String fileName = segment(deliveryId, "投递 ID") + ".json";
        byte[] rows = bytes(records);
        String hash = sha256(rows);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("deliveryId", deliveryId); manifest.put("fileName", fileName); manifest.put("format", "JSON");
        manifest.put("size", rows.length); manifest.put("sha256", hash); manifest.put("datasourceId", datasourceId);
        manifest.put("tableName", tableName); manifest.put("schemaVersion", schemaVersion); manifest.put("recordCount", records.size());
        try {
            if ("sftp".equals(target.protocol())) {
                deliverSftp(target, directory, fileName, rows, bytes(manifest), hash);
            } else {
                deliverFtp(target, directory, fileName, rows, bytes(manifest), hash);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("数据未能写入 ODS/FJGHCC 关联的文件存储，请检查该数据源连通性、目录权限和可用空间", exception);
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("completion", "FILE_AND_MARKER_VERIFIED"); answer.put("fileName", fileName);
        answer.put("completionMarker", fileName + ".done"); answer.put("bytes", rows.length); answer.put("sha256", hash);
        answer.put("targetDomainCode", "FJGHCC"); answer.put("targetDatasourceId", target.id()); answer.put("targetDatasourceName", target.name());
        return answer;
    }

    private Target target(String tenantId) {
        JdbcTemplate jdbc = new JdbcTemplate(tenants.dataSourceForTenant(tenantId));
        List<String> domains = jdbc.queryForList("""
                select domain_node.tid from sym_dict_t domain_node
                 join sym_dict_t ods_node on ods_node.tid=domain_node.parent_id
                 where domain_node.tenant_id=? and domain_node.is_del=0
                   and ods_node.tenant_id=domain_node.tenant_id and ods_node.is_del=0
                   and lower(trim(ods_node.dict_code))=? and lower(trim(domain_node.dict_code))=?
                 order by domain_node.sort_no asc, domain_node.tid asc
                """, String.class, tenantId, ODS, FJGHCC);
        if (domains.isEmpty()) throw new IllegalStateException("未配置 ODS 数据域 FJGHCC（非结构化存储）");
        List<Map<String, Object>> sources = jdbc.queryForList("""
                select datasource.tid, datasource.db_name, datasource.db_type from dwm_center_layer_source_t relation
                 join db_datasource_t datasource on datasource.tid=relation.datasource_id and datasource.tenant_id=relation.tenant_id
                  and datasource.is_del=0 and coalesce(datasource.show_connect,1)=1
                 where relation.tenant_id=? and relation.is_del=0 and relation.target_type='domain' and relation.target_id=?
                 order by relation.updated_time desc, relation.tid desc
                """, tenantId, domains.getFirst());
        if (sources.isEmpty()) throw new IllegalStateException("ODS 数据域 FJGHCC 尚未关联可连接的文件存储数据源");
        Map<String, Object> source = sources.getFirst();
        if (!"ftp".equalsIgnoreCase(text(source, "db_type"))) throw new IllegalStateException("ODS 数据域 FJGHCC 关联的数据源不是 FTP 数据源");
        String id = text(source, "tid");
        Map<String, Object> config = connections.resolve(tenantId, id);
        String protocol = text(config, "ftpProtocol", "protocol").toLowerCase(Locale.ROOT);
        if (protocol.isBlank()) protocol = "ftp";
        if (!List.of("ftp", "ftps", "sftp").contains(protocol)) throw new IllegalStateException("ODS 数据域 FJGHCC 的文件传输协议不受支持");
        String host = required(config, "FJGHCC 文件存储主机未配置", "ftpHost", "hostname", "host");
        int port = port(config, "sftp".equals(protocol) ? 22 : 21);
        String username = required(config, "FJGHCC 文件存储用户名未配置", "ftpUsername", "username");
        String password = required(config, "FJGHCC 文件存储密码未配置", "ftpPassword", "password");
        return new Target(id, text(source, "db_name"), protocol, host, port, username, password, config);
    }

    private void deliverSftp(Target target, String dir, String name, byte[] rows, byte[] marker, String hash) throws Exception {
        Session session = null; ChannelSftp sftp = null;
        try {
            session = new JSch().getSession(target.username(), target.host(), target.port());
            session.setPassword(target.password()); session.setConfig("StrictHostKeyChecking", "no"); session.connect(TIMEOUT);
            sftp = (ChannelSftp) session.openChannel("sftp"); sftp.connect(TIMEOUT); ensureSftpDirectory(sftp, dir);
            String path = join(dir, name); putSftp(sftp, path, rows, hash); putSftp(sftp, path + ".done", marker, sha256(marker));
        } finally { if (sftp != null && sftp.isConnected()) sftp.disconnect(); if (session != null && session.isConnected()) session.disconnect(); }
    }
    private void putSftp(ChannelSftp sftp, String path, byte[] content, String hash) throws Exception {
        if (sftpExists(sftp, path)) { verifySftp(sftp, path, content.length, hash); return; }
        String temp = path + "." + UUID.randomUUID() + ".tmp";
        try { sftp.put(new ByteArrayInputStream(content), temp, ChannelSftp.OVERWRITE); verifySftp(sftp, temp, content.length, hash);
            if (sftpExists(sftp, path)) verifySftp(sftp, path, content.length, hash); else sftp.rename(temp, path);
        } finally { try { sftp.rm(temp); } catch (SftpException ignored) { } }
    }
    private void verifySftp(ChannelSftp sftp, String path, int size, String hash) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); int total = 0;
        try (InputStream input = sftp.get(path)) { byte[] buffer = new byte[8192]; int read;
            while ((read = input.read(buffer)) >= 0) { total += read; if (total > size) throw new IllegalStateException("文件内容长度不一致"); digest.update(buffer, 0, read); } }
        if (total != size || !hex(digest.digest()).equals(hash)) throw new IllegalStateException("文件内容摘要不一致");
    }
    private boolean sftpExists(ChannelSftp sftp, String path) throws Exception { try { SftpATTRS attrs = sftp.lstat(path); if (attrs.isDir()) throw new IllegalStateException("目标路径不是文件"); return true; }
        catch (SftpException missing) { if (missing.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) return false; throw missing; } }
    private void ensureSftpDirectory(ChannelSftp sftp, String dir) throws Exception { String current = "";
        for (String part : dir.split("/")) if (!part.isBlank()) { current += "/" + part; try { sftp.stat(current); }
            catch (SftpException missing) { if (missing.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw missing; sftp.mkdir(current); } } }

    private void deliverFtp(Target target, String dir, String name, byte[] rows, byte[] marker, String hash) throws Exception {
        FTPClient ftp = "ftps".equals(target.protocol()) ? new FTPSClient(target.port() == 990) : new FTPClient();
        try { ftp.setConnectTimeout(TIMEOUT); ftp.connect(target.host(), target.port()); if (!ftp.login(target.username(), target.password())) throw new IllegalStateException("FTP 认证失败");
            if (ftp instanceof FTPSClient secure) { secure.execPBSZ(0); secure.execPROT("P"); }
            if (bool(target.config(), true, "ftpPassiveMode", "passiveMode")) ftp.enterLocalPassiveMode(); ftp.setFileType(FTP.BINARY_FILE_TYPE); ensureFtpDirectory(ftp, dir);
            // ensureFtpDirectory leaves the session in dir.  This FTP server
            // rejects absolute multi-level path operands, so keep STOR/RNFR
            // relative to that working directory as well.
            putFtp(ftp, name, rows, hash); putFtp(ftp, name + ".done", marker, sha256(marker));
        } finally { if (ftp.isConnected()) { try { ftp.logout(); } finally { ftp.disconnect(); } } }
    }
    private void putFtp(FTPClient ftp, String path, byte[] content, String hash) throws Exception { if (ftpExists(ftp, path)) { verifyFtp(ftp, path, content.length, hash); return; }
        String temp = path + "." + UUID.randomUUID() + ".tmp";
        try { if (!ftp.storeFile(temp, new ByteArrayInputStream(content))) throw new IllegalStateException("FTP 写入失败"); verifyFtp(ftp, temp, content.length, hash);
            if (ftpExists(ftp, path)) verifyFtp(ftp, path, content.length, hash); else if (!ftp.rename(temp, path)) throw new IllegalStateException("FTP 文件发布失败");
        } finally { try { ftp.deleteFile(temp); } catch (Exception ignored) { } } }
    private boolean ftpExists(FTPClient ftp, String path) throws Exception { var files = ftp.listFiles(path); return files != null && files.length > 0 && files[0].isFile(); }
    private void verifyFtp(FTPClient ftp, String path, int size, String hash) throws Exception { MessageDigest digest = MessageDigest.getInstance("SHA-256"); int[] total = {0};
        if (!ftp.retrieveFile(path, new java.io.OutputStream() { public void write(int value) { total[0]++; digest.update((byte) value); } public void write(byte[] value, int offset, int length) { total[0] += length; digest.update(value, offset, length); } })) throw new IllegalStateException("FTP 读取校验失败");
        if (total[0] != size || !hex(digest.digest()).equals(hash)) throw new IllegalStateException("FTP 文件内容摘要不一致"); }
    private void ensureFtpDirectory(FTPClient ftp, String dir) throws Exception {
        if (!ftp.changeWorkingDirectory("/")) throw new IllegalStateException("FTP 根目录不可用");
        for (String part : dir.split("/")) if (!part.isBlank()) {
            if (!ftp.changeWorkingDirectory(part)) {
                ftp.makeDirectory(part);
                // The directory may have been created concurrently; opening it
                // is the only success condition that matters.
                if (!ftp.changeWorkingDirectory(part)) throw new IllegalStateException("FTP 目录不可用");
            }
        }
    }

    private String root(Map<String, Object> config) { String value = text(config, "ftpPath", "remotePath", "path"); return remote(value.isBlank() ? "/" : value); }
    private String remote(String value) { String result = ""; for (String part : value.trim().replace('\\', '/').split("/")) if (!part.isBlank()) { if (part.equals(".") || part.equals("..")) throw new IllegalArgumentException("FTP 根目录不正确"); result += "/" + part; } return result.isBlank() ? "/" : result; }
    private String join(String left, String right) { return left.endsWith("/") ? left + right : left + "/" + right; }
    private String segment(String value, String label) { String result = value == null ? "" : value.trim(); if (!result.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException(label + "不正确"); return result; }
    private int port(Map<String, Object> config, int fallback) { try { String value = text(config, "ftpPort", "port"); int port = value.isBlank() ? fallback : Integer.parseInt(value); if (port < 1 || port > 65535) throw new NumberFormatException(); return port; } catch (NumberFormatException exception) { throw new IllegalArgumentException("FTP 端口不正确"); } }
    private String required(Map<String, Object> config, String message, String... keys) { String value = text(config, keys); if (value.isBlank()) throw new IllegalStateException(message); return value; }
    private String text(Map<String, Object> values, String... keys) { if (values != null) for (String key : keys) { Object value = values.get(key); if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value).trim(); } return ""; }
    private boolean bool(Map<String, Object> values, boolean fallback, String... keys) { String value = text(values, keys).toLowerCase(Locale.ROOT); return value.isBlank() ? fallback : List.of("true", "1", "yes", "y", "是").contains(value); }
    private byte[] bytes(Object value) { try { return json.writeValueAsBytes(value); } catch (Exception exception) { throw new IllegalStateException("无法生成投递文件", exception); } }
    private String sha256(byte[] value) { try { return hex(MessageDigest.getInstance("SHA-256").digest(value)); } catch (Exception exception) { throw new IllegalStateException(exception); } }
    private String hex(byte[] value) { return java.util.HexFormat.of().formatHex(value); }
    private record Target(String id, String name, String protocol, String host, int port, String username, String password, Map<String, Object> config) { }
}
