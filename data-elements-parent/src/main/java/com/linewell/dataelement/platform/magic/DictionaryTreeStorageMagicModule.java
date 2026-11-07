package com.linewell.dataelement.platform.magic;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.ssssssss.magicapi.core.annotation.MagicModule;

/** Transaction primitives for Magic callers that have already selected and validated dictionary rows. */
@Component
@MagicModule("dictionaryTreeStorage")
public class DictionaryTreeStorageMagicModule {
    private static final int MAX_GUARDS = 512;
    private static final int MAX_ROWS = 10_000;
    private static final int BATCH_SIZE = 100;
    private static final List<String> COLUMNS = List.of("parent_id", "sort_no", "level_no", "tree_path");
    private final JdbcTemplate jdbc;

    public DictionaryTreeStorageMagicModule(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Locks already selected snapshots and rejects changes since the Magic transaction read them. */
    public void requireUnchanged(List<Map<String, Object>> snapshots) {
        String tenantId = transactionTenant();
        if (snapshots.isEmpty() || snapshots.size() > MAX_GUARDS) {
            throw new IllegalArgumentException("字典树并发校验的节点数超过限制");
        }
        var byId = new java.util.HashMap<String, Map<String, Object>>();
        var args = new ArrayList<Object>();
        args.add(tenantId);
        for (var snapshot : snapshots) {
            String id = nodeId(snapshot);
            if (byId.putIfAbsent(id, snapshot) != null) throw new IllegalArgumentException("字典树并发校验的标识重复");
            args.add(id);
        }
        var locked = jdbc.queryForList("""
            SELECT tid, parent_id, sort_no, level_no, tree_path
              FROM sym_dict_t
             WHERE tenant_id = ? AND is_del = 0 AND tid IN (
            """ + placeholders(snapshots.size()) + ") ORDER BY tid FOR UPDATE", args.toArray());
        if (locked.size() != snapshots.size()) throw changed();
        for (var row : locked) {
            var expected = byId.get(row.get("tid"));
            if (expected == null || COLUMNS.stream().anyMatch(column -> !Objects.equals(
                    Objects.toString(row.get(column), null), Objects.toString(expected.get(column), null)))) {
                throw changed();
            }
        }
    }

    /** Applies distinct values in bounded SQL batches and checks the old values before each write. */
    public void applyChanges(List<Map<String, Object>> changes) {
        String tenantId = transactionTenant();
        if (changes.size() > MAX_ROWS) throw new IllegalArgumentException("单次拖拽调整的字典项超过 10000 项");
        var snapshots = new ArrayList<Map<?, ?>>();
        var patches = new ArrayList<Map<?, ?>>();
        var ids = new HashSet<String>();
        for (var change : changes) {
            if (!(change.get("before") instanceof Map<?, ?> before) || !(change.get("after") instanceof Map<?, ?> after)
                    || !before.keySet().containsAll(COLUMNS) || !after.keySet().containsAll(COLUMNS)
                    || !ids.add(nodeId(before))) {
                throw new IllegalArgumentException("待更新字典项的结构字段缺失或标识重复");
            }
            snapshots.add(before);
            patches.add(after);
        }
        for (int start = 0; start < changes.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, changes.size());
            writeBatch(tenantId, snapshots.subList(start, end), patches.subList(start, end));
        }
    }

    private void writeBatch(String tenantId, List<Map<?, ?>> before, List<Map<?, ?>> after) {
        var args = new ArrayList<Object>();
        var sql = new StringBuilder("UPDATE sym_dict_t SET ");
        for (int index = 0; index < COLUMNS.size(); index++) {
            if (index > 0) sql.append(", ");
            String column = COLUMNS.get(index);
            sql.append(column).append(" = CASE tid");
            for (int row = 0; row < before.size(); row++) {
                sql.append(" WHEN ? THEN ?");
                args.add(nodeId(before.get(row)));
                args.add(after.get(row).get(column));
            }
            sql.append(" ELSE ").append(column).append(" END");
        }
        sql.append(" WHERE tenant_id = ? AND is_del = 0 AND (");
        args.add(tenantId);
        for (int index = 0; index < before.size(); index++) {
            if (index > 0) sql.append(" OR ");
            var snapshot = before.get(index);
            sql.append("(tid = ?");
            args.add(nodeId(snapshot));
            for (String column : COLUMNS) {
                sql.append(" AND ").append(column);
                var expected = snapshot.get(column);
                if (expected == null) {
                    sql.append(" IS NULL");
                } else {
                    sql.append(" = ?");
                    args.add(expected);
                }
            }
            sql.append(')');
        }
        sql.append(')');
        if (jdbc.update(sql.toString(), args.toArray()) != before.size()) throw changed();
    }

    private String transactionTenant() {
        String tenantId = TenantContext.requireTenantId();
        if (TenantContext.usesControlDatabase() || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(jdbc.getDataSource())) {
            throw new IllegalStateException("字典树操作必须在当前租户的数据库事务中执行");
        }
        return tenantId;
    }

    private static String nodeId(Map<?, ?> row) {
        if (row.get("tid") instanceof String id && !id.isBlank()) return id;
        throw new IllegalArgumentException("字典项标识无效");
    }

    private static IllegalStateException changed() {
        return new IllegalStateException("字典项已变更，请刷新后重试");
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }
}
