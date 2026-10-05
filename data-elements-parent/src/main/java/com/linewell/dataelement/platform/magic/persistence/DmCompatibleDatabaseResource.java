package com.linewell.dataelement.platform.magic.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.ssssssss.magicapi.core.resource.KeyValueResource;
import org.ssssssss.magicapi.core.resource.Resource;
import org.ssssssss.magicapi.utils.IoUtils;

/**
 * 兼容达梦数据库的 Magic API 数据库资源实现。
 *
 * <p>相对于默认的数据库资源实现，这个类主要补了三类能力：
 * <ol>
 *     <li>兼容达梦中 {@code file_content} 可能返回的 String / byte[] / Blob / Clob 类型。</li>
 *     <li>兼容资源路径带前导斜杠和不带前导斜杠的两种历史存储形式。</li>
 *     <li>在读取资源时维护一份内存缓存，减少重复查库。</li>
 * </ol>
 */
public class DmCompatibleDatabaseResource extends KeyValueResource {

    private static final Logger log = LoggerFactory.getLogger(DmCompatibleDatabaseResource.class);

    private final JdbcTemplate template;
    private final String tableName;
    private final Map<String, String> cachedContent;
    private final Object refreshLock;

    /**
     * 创建根资源节点。
     */
    public DmCompatibleDatabaseResource(JdbcTemplate template, String tableName, String path, boolean readonly) {
        this(template, tableName, path, readonly, new ConcurrentHashMap<>(), new Object(), null);
    }

    /**
     * 创建子资源节点。
     *
     * <p>子节点与父节点共享同一个缓存 Map，保证整个资源树视图一致。
     */
    private DmCompatibleDatabaseResource(JdbcTemplate template,
                                         String tableName,
                                         String path,
                                         boolean readonly,
                                         Map<String, String> cachedContent,
                                         Object refreshLock,
                                         KeyValueResource parent) {
        super("/", path, readonly, parent);
        this.template = template;
        this.tableName = tableName;
        this.cachedContent = cachedContent;
        this.refreshLock = refreshLock;
    }

    /**
     * 读取当前路径资源内容。
     *
     * <p>优先从缓存取；缓存不存在时，再按候选路径逐个查库。
     */
    @Override
    public byte[] read() {
        String value = cachedContent.get(path);
        if (value == null) {
            String sql = String.format(
                    "select file_content from %s where file_path = ? and %s",
                    tableName,
                    activeRowPredicate());
            for (String candidatePath : pathCandidates(path)) {
                // 逐个尝试兼容路径，适配带 / 与不带 / 的历史数据。
                value = template.query(sql, ps -> ps.setString(1, candidatePath), rs -> rs.next() ? readContent(rs, 1) : null);
                if (value != null) {
                    cachedContent.put(candidatePath, value);
                    if (!candidatePath.equals(path)) {
                        cachedContent.put(path, value);
                    }
                    break;
                }
            }
        }
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 预加载当前路径下的所有资源到缓存。
     */
    @Override
    public void readAll() {
        synchronized (refreshLock) {
            List<String> prefixes = List.copyOf(pathCandidates(path));
            String predicates = prefixes.stream()
                    .map(ignored -> "file_path like ?")
                    .collect(Collectors.joining(" or "));
            String sql = String.format(
                    "select file_path, file_content from %s where (%s) and is_del = 0 and file_content is not null order by file_path",
                    tableName,
                    predicates);
            Map<String, String> snapshot = readSnapshot(sql, prefixes);

            // Publish only a complete read. Failed attempts leave the previous tree available.
            cachedContent.entrySet().removeIf(
                    entry -> prefixes.stream().anyMatch(prefix -> entry.getKey().startsWith(prefix)));
            cachedContent.putAll(snapshot);
            log.info("DmCompatibleDatabaseResource readAll cached path={} rowCount={} cacheSize={} sample={}",
                    path,
                    snapshot.size(),
                    cachedContent.size(),
                    snapshot.keySet().stream().limit(12).collect(Collectors.toList()));
        }
    }

    private Map<String, String> readSnapshot(String sql, List<String> prefixes) {
        for (int attempt = 1; ; attempt++) {
            try {
                return querySnapshot(sql, prefixes);
            } catch (TransientDataAccessException | RecoverableDataAccessException | DataAccessResourceFailureException failure) {
                if (attempt >= 3) {
                    throw failure;
                }
                log.warn("Magic resource read failed, retrying path={} attempt={}/3 failure={}",
                        path, attempt, failure.getClass().getSimpleName());
                try {
                    Thread.sleep(attempt * 1000L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    failure.addSuppressed(interrupted);
                    throw failure;
                }
            }
        }
    }

    private Map<String, String> querySnapshot(String sql, List<String> prefixes) {
        return template.query(sql, ps -> {
                for (int index = 0; index < prefixes.size(); index++) {
                    ps.setString(index + 1, prefixes.get(index) + "%");
                }
            }, (ResultSet rs) -> {
                Map<String, String> items = new java.util.LinkedHashMap<>();
                while (rs.next()) {
                    String filePath = rs.getString(1);
                    String content = readContent(rs, 2);
                    if (content == null || content.isBlank()) {
                        log.warn("Magic API resource content is blank, skip path={}", filePath);
                        continue;
                    }
                    // Magic uses this marker to reconstruct directory nodes; other directory rows are invalid resources.
                    if (filePath.endsWith(separator) && !"this is directory".equals(content)) {
                        continue;
                    }
                    items.putIfAbsent(filePath, content);
                }
                return items;
            });
    }

    /**
     * 判断当前路径资源是否存在。
     */
    @Override
    public boolean exists() {
        if (cachedContent.get(path) != null) {
            return true;
        }
        String sql = String.format(
                "select count(*) from %s where file_path = ? and %s",
                tableName,
                activeRowPredicate());
        return pathCandidates(path).stream().anyMatch(candidatePath -> {
            Long value = template.queryForObject(sql, Long.class, candidatePath);
            return value != null && value > 0;
        });
    }

    /**
     * 写入资源内容。
     *
     * <p>存在则更新，不存在则插入。
     */
    @Override
    public boolean write(String content) {
        String updateSql = String.format(
                "update %s set file_content = ? where file_path = ? and %s",
                tableName,
                activeRowPredicate());
        if (exists() && template.update(updateSql, content, path) > 0) {
            cachedContent.put(path, content);
            return true;
        }
        String insertSql = String.format(
                "insert into %s (file_path,file_content,is_del) values(?,?,0)",
                tableName);
        if (template.update(insertSql, path, content) > 0) {
            cachedContent.put(path, content);
            return true;
        }
        return false;
    }

    /**
     * 列出当前目录下的所有 key。
     */
    @Override
    protected Set<String> keys() {
        String prefix = isDirectory() ? path : (path + separator);
        if (!cachedContent.isEmpty()) {
            Set<String> prefixes = pathCandidates(prefix);
            return cachedContent.keySet().stream()
                    .filter(it -> prefixes.stream().anyMatch(it::startsWith))
                    .collect(Collectors.toSet());
        }
        String sql = String.format(
                "select file_path from %s where file_path like ? and is_del = 0",
                tableName);
        return pathCandidates(prefix).stream()
                .flatMap(candidatePrefix -> template.queryForList(sql, String.class, candidatePrefix + "%").stream())
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * 批量重命名路径。
     */
    @Override
    protected boolean renameTo(Map<String, String> renameKeys) {
        String sql = String.format(
                "update %s set file_path = ? where file_path = ? and is_del = 0",
                tableName);
        int affectedRows = Arrays.stream(template.batchUpdate(sql, renameKeys.entrySet().stream()
                .map(entry -> new Object[]{entry.getValue(), entry.getKey()})
                .collect(Collectors.toList()))).sum();
        if (affectedRows > 0 || affectedRows == -2) {
            renameKeys.forEach((oldKey, newKey) -> cachedContent.put(newKey, cachedContent.remove(oldKey)));
            return true;
        }
        return false;
    }

    /**
     * 删除当前路径及其子路径下的资源。
     */
    @Override
    public boolean delete() {
        String sql = String.format("delete from %s where file_path = ? or file_path like ?", tableName);
        int affectedRows = pathCandidates(path).stream()
                .mapToInt(candidatePath -> template.update(sql, candidatePath, candidatePath + "%"))
                .sum();
        if (affectedRows > 0) {
            Set<String> prefixes = pathCandidates(path);
            cachedContent.entrySet().removeIf(entry -> prefixes.stream().anyMatch(prefix -> entry.getKey().startsWith(prefix)));
            return true;
        }
        return false;
    }

    /**
     * 创建子 Resource 节点。
     */
    @Override
    protected Function<String, Resource> mappedFunction() {
        return it -> new DmCompatibleDatabaseResource(
                template, tableName, it, readonly, cachedContent, refreshLock, this);
    }

    @Override
    public String toString() {
        return String.format("db://%s/%s", tableName, Objects.toString(path, ""));
    }

    /**
     * 生成路径候选值，兼容：
     * <ul>
     *     <li>/magic-api/api/demo.ms</li>
     *     <li>magic-api/api/demo.ms</li>
     * </ul>
     */
    private Set<String> pathCandidates(String value) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(value);
        if (value.startsWith("/")) {
            candidates.add(value.substring(1));
        } else {
            candidates.add("/" + value);
        }
        return candidates;
    }

    private String activeRowPredicate() {
        String normalized = path.startsWith("/") ? path : "/" + path;
        return Set.of(
                "/magic-api/api/",
                "/magic-api/datasource/",
                "/magic-api/function/",
                "/magic-api/task/"
        ).contains(normalized)
                ? "(is_del = 0 or is_del is null)"
                : "is_del = 0";
    }

    /**
     * 从 ResultSet 中读取脚本内容。
     *
     * <p>达梦场景下 {@code file_content} 的 JDBC 返回类型不稳定，
     * 因此这里统一兼容 String、byte[]、Blob、Clob。
     */
    private String readContent(ResultSet rs, int columnIndex) throws SQLException {
        Object object = rs.getObject(columnIndex);
        if (object == null) {
            return null;
        }
        if (object instanceof String) {
            return object.toString();
        }
        if (object instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (object instanceof Blob blob) {
            try (InputStream is = blob.getBinaryStream()) {
                return new String(IoUtils.bytes(is), StandardCharsets.UTF_8);
            } catch (SQLException | IOException ex) {
                throw new SQLException("Failed to read blob content", ex);
            }
        }
        if (object instanceof Clob clob) {
            return clob.getSubString(1, (int) clob.length());
        }
        return Objects.toString(object, null);
    }
}
