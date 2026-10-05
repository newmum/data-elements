package com.linewell.dataelement.platform.configuration;

import com.linewell.dataelement.platform.tenant.infrastructure.datasource.UseControlDataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import com.linewell.dataelement.platform.persistence.id.NumericId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Control-database operations for low-code component definitions and history. */
@Service
@UseControlDataSource
public class UiComponentAdminService {

    private static final String GLOBAL_TENANT_ID = "00000000000000000000000000000000";
    private static final String SYSTEM_USER_ID = "1";

    private final JdbcTemplate jdbcTemplate;
    private final UiComponentCacheService cacheService;

    public UiComponentAdminService(JdbcTemplate jdbcTemplate, UiComponentCacheService cacheService) {
        this.jdbcTemplate = jdbcTemplate;
        this.cacheService = cacheService;
    }

    public Map<String, Object> tree() {
        List<Map<String, Object>> rows = jdbcTemplate.query("""
                select tid, name as real_name, remark, type as component_type, pid
                  from ui_component_t
                 where is_del = 0
                 order by coalesce(sort_order, 2147483647), remark, tid
                """, (resultSet, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("tid", resultSet.getString("tid"));
                    row.put("realName", resultSet.getString("real_name"));
                    row.put("remark", resultSet.getString("remark"));
                    row.put("componentType", resultSet.getString("component_type"));
                    row.put("pid", resultSet.getString("pid"));
                    return row;
                });
        Map<String, List<Map<String, Object>>> children = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String type = text(row, "componentType", "COMPONENT_TYPE", "component_type");
            String realName = text(row, "realName", "REAL_NAME", "real_name");
            String remark = text(row, "remark", "REMARK");
            row.put("name", "1".equals(type) ? displayName(remark, realName) : realName);
            row.put("isGroup", "0".equals(type) ? 1 : 0);
            children.computeIfAbsent(text(row, "pid", "PID"), ignored -> new ArrayList<>()).add(row);
        }
        List<Map<String, Object>> roots = attachChildren(children, "0");
        return Map.of("list", roots, "total", roots.size());
    }

    public String duplicateMessage(Map<String, Object> body) {
        String tid = text(body, "tid", "TID");
        String type = text(body, "type", "TYPE");
        String name = text(body, "name", "NAME");
        if (name.isBlank()) {
            return "组件名称不能为空";
        }
        if ("1".equals(type)) {
            int count = tid.isBlank()
                    ? count("select count(1) from ui_component_t where is_del=0 and type='1' and name=?", name)
                    : count("select count(1) from ui_component_t where is_del=0 and type='1' and name=? and tid<>?", name, tid);
            return count > 0 ? "组件名称不能重复" : "";
        }
        String pid = text(body, "pid", "PID");
        if (!tid.isBlank() && pid.isBlank()) {
            pid = scalar("select pid from ui_component_t where is_del=0 and tid=?", tid);
        }
        int count = tid.isBlank()
                ? count("select count(1) from ui_component_t where is_del=0 and type='0' and pid=? and name=?", pid, name)
                : count("select count(1) from ui_component_t where is_del=0 and type='0' and pid=? and name=? and tid<>?", pid, name, tid);
        return count > 0 ? "当前分组下已存在相同分组名称" : "";
    }

    @Transactional
    public String save(Map<String, Object> body) {
        String tid = text(body, "tid", "TID");
        if (tid.isBlank()) {
            tid = id();
            String parentId = text(body, "pid", "PID");
            validateParent(tid, parentId);
            jdbcTemplate.update("""
                    insert into ui_component_t
                      (tid, pid, name, source_code, compile_js, compile_css, type, remark,
                       sort_order, created_by, created_time, updated_by, updated_time, is_del, tenant_id)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, ?, current_timestamp, 0, ?)
                    """,
                    tid, value(body, "pid"), value(body, "name"), value(body, "sourceCode", "source_code"),
                    value(body, "compileJs", "compile_js"), value(body, "compileCss", "compile_css"),
                    value(body, "type"), value(body, "remark"), nextSortOrder(parentId),
                    SYSTEM_USER_ID, SYSTEM_USER_ID, GLOBAL_TENANT_ID);
        } else {
            // Metadata edits (including inline renames) do not carry a parent id.
            // Treat omitted fields as unchanged; null must never orphan a component.
            String parentId = text(body, "pid", "PID");
            String previousParent = scalar("select pid from ui_component_t where tid=? and is_del=0", tid);
            if (body.containsKey("pid") || body.containsKey("PID")) {
                validateParent(tid, parentId);
            }
            Integer movedOrder = !parentId.isBlank() && !parentId.equals(previousParent)
                    ? nextSortOrder(parentId) : null;
            jdbcTemplate.update("""
                    update ui_component_t
                       set pid=coalesce(?, pid), name=coalesce(?, name),
                           type=coalesce(?, type), remark=coalesce(?, remark),
                           sort_order=coalesce(?, sort_order),
                           updated_by=?, updated_time=current_timestamp
                     where tid=? and is_del=0
                    """,
                    value(body, "pid"), value(body, "name"), value(body, "type"), value(body, "remark"),
                    movedOrder, SYSTEM_USER_ID, tid);
        }
        cacheService.evict();
        return tid;
    }

    @Transactional
    public Map<String, Object> saveCode(Map<String, Object> body) {
        if (body == null) {
            throw new IllegalArgumentException("Component code payload cannot be empty");
        }
        String tid = text(body, "tid", "TID");
        if (tid.isBlank() || count("select count(1) from ui_component_t where tid=? and is_del=0", tid) == 0) {
            throw new IllegalArgumentException("文件不存在");
        }
        jdbcTemplate.update("""
                insert into ui_component_history_t
                  (tid, tenant_id, component_id, source_code, created_time, updated_time, created_by, updated_by, is_del)
                values (?, ?, ?, ?, current_timestamp, current_timestamp, ?, ?, 0)
                """, id(), GLOBAL_TENANT_ID, tid, text(body, "sourceCode", "source_code"), SYSTEM_USER_ID, SYSTEM_USER_ID);
        jdbcTemplate.update("""
                update ui_component_t
                   set source_code=?, compile_js=?, compile_css=?, updated_by=?, updated_time=current_timestamp
                 where tid=? and is_del=0
                """, text(body, "sourceCode", "source_code"), text(body, "compileJs", "compile_js"),
                text(body, "compileCss", "compile_css"), SYSTEM_USER_ID, tid);
        cacheService.evict();
        return Map.of("tid", tid);
    }

    @Transactional
    public int delete(String rootId) {
        if (rootId == null || rootId.isBlank()) {
            return 0;
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (!ids.add(id)) {
                continue;
            }
            queue.addAll(jdbcTemplate.queryForList(
                    "select tid from ui_component_t where is_del=0 and pid=?", String.class, id));
        }
        int updated = 0;
        for (String id : ids) {
            updated += jdbcTemplate.update(
                    "update ui_component_t set is_del=1, updated_by=?, updated_time=current_timestamp where tid=? and is_del=0",
                    SYSTEM_USER_ID, id);
        }
        cacheService.evict();
        return updated;
    }

    public String sourceCode(String tid) {
        return textValue("select source_code from ui_component_t where is_del=0 and tid=?", tid);
    }

    public Map<String, Object> history(String componentId) {
        List<Map<String, Object>> rows = jdbcTemplate.query("""
                select tid, created_time
                  from ui_component_history_t
                 where component_id=? and is_del=0
                 order by created_time desc
                 fetch first 200 rows only
                """, (resultSet, rowNum) -> Map.of(
                        "tid", resultSet.getString("tid"),
                        "createdTime", resultSet.getTimestamp("created_time")), componentId);
        return Map.of("list", rows, "total", rows.size());
    }

    public String historyDetail(String tid) {
        return textValue("select source_code from ui_component_history_t where tid=? and is_del=0", tid);
    }

    public Map<String, Object> lastCode(String componentId) {
        List<Map<String, Object>> rows = jdbcTemplate.query("""
                select source_code, created_time
                  from ui_component_history_t
                 where component_id=? and is_del=0
                 order by created_time desc
                 fetch first 1 rows only
                """, (resultSet, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("sourceCode", readText(resultSet, 1));
                    row.put("createdTime", resultSet.getTimestamp(2));
                    return row;
                }, componentId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> enrichRepositoryTabs(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return rows == null ? List.of() : rows;
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            int type = integer(row, "tabType", "TAB_TYPE", "tab_type");
            String relaId = text(row, "relaId", "RELA_ID", "rela_id");
            if ((type == 0 || type == 1) && !relaId.isBlank()) {
                ids.add(relaId);
            }
        }
        Map<String, Map<String, Object>> summaries = summaries(ids);
        for (Map<String, Object> row : rows) {
            int type = integer(row, "tabType", "TAB_TYPE", "tab_type");
            Map<String, Object> summary = summaries.get(text(row, "relaId", "RELA_ID", "rela_id"));
            if ((type == 0 || type == 1) && summary != null) {
                row.put("name", summary.get("name"));
                row.put("nameCn", summary.get("remark"));
                row.put("relaName", summary.get("name"));
                row.put("relaNameCn", summary.get("remark"));
            }
        }
        return rows;
    }

    private Map<String, Map<String, Object>> summaries(Collection<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", ids.stream().map(ignored -> "?").toList());
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        jdbcTemplate.query("select tid, name, remark from ui_component_t where is_del=0 and tid in ("
                        + placeholders + ")",
                preparedStatement -> {
                    int index = 1;
                    for (String id : ids) {
                        preparedStatement.setString(index++, id);
                    }
                },
                resultSet -> {
                    while (resultSet.next()) {
                        result.put(resultSet.getString(1), Map.of(
                                "name", nullToEmpty(resultSet.getString(2)),
                                "remark", nullToEmpty(resultSet.getString(3))));
                    }
                    return result;
                });
        return result;
    }

    private List<Map<String, Object>> attachChildren(
            Map<String, List<Map<String, Object>>> indexed, String pid) {
        List<Map<String, Object>> rows = indexed.getOrDefault(pid, List.of());
        for (Map<String, Object> row : rows) {
            row.put("children", attachChildren(indexed, text(row, "tid", "TID")));
        }
        return rows;
    }

    private int count(String sql, Object... args) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return result == null ? 0 : result;
    }

    private int nextSortOrder(String parentId) {
        Integer order = jdbcTemplate.queryForObject(
                "select coalesce(max(sort_order), 0) + 1 from ui_component_t where pid=? and is_del=0",
                Integer.class, parentId);
        return order == null ? 1 : order;
    }

    private void validateParent(String componentId, String parentId) {
        if (parentId.isBlank()) {
            throw new IllegalArgumentException("父目录不能为空");
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        String ancestor = parentId;
        while (!"0".equals(ancestor)) {
            if (!seen.add(ancestor) || componentId.equals(ancestor)) {
                throw new IllegalArgumentException("父目录不能是组件自身或其子目录");
            }
            List<String> parents = jdbcTemplate.query(
                    "select pid from ui_component_t where tid=? and type='0' and is_del=0",
                    (resultSet, rowNum) -> nullToEmpty(resultSet.getString(1)), ancestor);
            if (parents.isEmpty() || parents.getFirst().isBlank()) {
                throw new IllegalArgumentException("父目录不存在");
            }
            ancestor = parents.getFirst();
        }
    }

    private String scalar(String sql, Object... args) {
        List<String> values = jdbcTemplate.query(sql,
                (resultSet, rowNum) -> nullToEmpty(resultSet.getString(1)), args);
        return values.isEmpty() ? "" : values.get(0);
    }

    private String textValue(String sql, Object... args) {
        List<String> values = jdbcTemplate.query(sql,
                (resultSet, rowNum) -> readText(resultSet, 1), args);
        return values.isEmpty() ? null : values.get(0);
    }

    private Object value(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private String text(Map<String, Object> row, String... keys) {
        Object value = value(row, keys);
        return value == null ? "" : value.toString();
    }

    private int integer(Map<String, Object> row, String... keys) {
        String value = text(row, keys);
        try {
            return value.isBlank() ? -1 : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private String displayName(String remark, String realName) {
        return remark.isBlank() ? realName : remark + "(" + realName + ")";
    }

    private String id() {
        return NumericId.nextId();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String readText(ResultSet resultSet, int column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (value instanceof Clob clob) {
            return clob.getSubString(1, Math.toIntExact(clob.length()));
        }
        if (value instanceof Blob blob) {
            try (InputStream input = blob.getBinaryStream()) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception exception) {
                throw new SQLException("Failed to read component content", exception);
            }
        }
        return value.toString();
    }
}
