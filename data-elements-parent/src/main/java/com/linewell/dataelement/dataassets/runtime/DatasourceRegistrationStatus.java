package com.linewell.dataelement.dataassets.runtime;

import com.linewell.dataelement.model.common.BizException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Datasource lifecycle is independent of table annotation and approval workflows. */
public final class DatasourceRegistrationStatus {
    private DatasourceRegistrationStatus() { }

    public static Map<String, Object> refresh(DataSource dataSource, String tenantId,
                                               String datasourceId, boolean finish) {
        return evaluate(dataSource, tenantId, datasourceId, finish, false);
    }

    public static Map<String, Object> evaluate(DataSource dataSource, String tenantId,
                                               String datasourceId, boolean finish, boolean dryRun) {
        return evaluateMany(dataSource, tenantId, List.of(Objects.requireNonNullElse(datasourceId, "")), finish, dryRun).getFirst();
    }

    public static List<Map<String, Object>> evaluateMany(DataSource dataSource, String tenantId,
                                  Collection<String> datasourceIds, boolean finish, boolean dryRun) {
        if (tenantId == null || tenantId.isBlank() || datasourceIds == null) {
            throw new BizException(401, "无法识别当前租户或数据源");
        }
        var ids = new ArrayList<>(new LinkedHashSet<>(datasourceIds));
        if (ids.isEmpty()) return List.of();
        if (ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id.isBlank()))
            throw new BizException(400, "每次最多同步100个有效数据源");
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(ids);
        var jdbc = new JdbcTemplate(dataSource);
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(transaction -> {
            // Serialize lifecycle updates for this source. Every lookup and write retains tenant scope.
            var sources = jdbc.queryForList("""
                    select tid,asset_status from db_datasource_t
                     where tenant_id=? and is_del=0 and tid in (
                    """ + placeholders + ") order by tid" + (dryRun ? "" : " for update"), args.toArray());
            if (sources.size() != ids.size()) throw new BizException(404, "数据源不存在或不属于当前租户");
            var countRows = jdbc.queryForList("""
                    select datasource_id,count(*) as table_count,
                           coalesce(sum(case when asset_status=2 then 1 else 0 end),0) as registered_count
                      from db_table_t where tenant_id=? and is_del=0 and datasource_id in (
                    """ + placeholders + ") group by datasource_id", args.toArray());
            Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
            for (var counts : countRows) byId.put(String.valueOf(counts.get("datasource_id")), counts);
            List<Map<String, Object>> results = new ArrayList<>();
            List<Object[]> updates = new ArrayList<>();
            for (var source : sources) {
            String datasourceId = String.valueOf(source.get("tid"));
            int oldStatus = source.get("asset_status") instanceof Number value ? value.intValue() : 0;
            var counts = byId.get(datasourceId);
            long total = counts == null ? 0 : ((Number) counts.get("table_count")).longValue();
            long registered = counts == null ? 0 : ((Number) counts.get("registered_count")).longValue();
            int status = !finish && oldStatus != 1 && oldStatus != 2 ? 0
                    : total > 0 && total == registered ? 2 : 1;
            if (!dryRun && (finish || status != oldStatus)) {
                updates.add(new Object[]{status, status == 0 ? 0 : 2, datasourceId, tenantId});
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tid", datasourceId);
            result.put("assetStatus", status);
            result.put("displayStatus", status);
            result.put("tableCount", total);
            result.put("registeredTableCount", registered);
            result.put("pendingTableCount", total - registered);
            result.put("tableReviewReady", status == 2);
            result.put("completionEligible", true);
            result.put("dryRun", dryRun);
            results.add(result);
            }
            if (!updates.isEmpty()) jdbc.batchUpdate("""
                    update db_datasource_t set asset_status=?,flow_status=?,updated_time=CURRENT_TIMESTAMP
                     where tid=? and tenant_id=? and is_del=0
                    """, updates);
            return results;
        });
    }
}
