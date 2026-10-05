package com.linewell.dataelement.feature.identity.application;

import com.linewell.dataelement.platform.tenant.application.TenantAccountPolicy;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Read adapter for existing directory/approval callers; account writes remain in Magic API. */
@Service
public class TenantIdentityQueryService {
    private final ControlIdentityService control;
    private final TenantAccountPolicy policy;
    private final JdbcTemplate jdbc;
    private static final String COLUMNS = "id,real_name as realName,user_name as userName,phone,gender,status,"
            + "app_file_id as appFileId,create_id as createId,create_time as createTime,update_time as updateTime,"
            + "id_card as idCard,date_birth as dateBirth,nation,office_address as officeAddress,job_number as jobNumber,"
            + "authorized_strength as authorizedStrength,origin_position as originPosition,origin_rank as originRank";

    public TenantIdentityQueryService(ControlIdentityService control, TenantAccountPolicy policy, JdbcTemplate jdbc) {
        this.control = control; this.policy = policy; this.jdbc = jdbc;
    }

    public boolean isLocal(String tenantId) { return policy.isLocal(tenantId); }

    public Map<String, Object> page(String tenantId, long page, long size, List<String> ids, String account, String name) {
        requireScope(tenantId);
        if (!policy.isLocal(tenantId)) return control.page(tenantId, page, size, ids, account, name);
        page = Math.max(1, page); size = Math.min(200, Math.max(1, size));
        if (ids != null && ids.isEmpty()) return Map.of("list", List.of(), "total", 0L);
        List<Object> values = new ArrayList<>(); values.add(tenantId);
        String where = " from rm_user_t where tenant_id=? and deleted=0";
        if (ids != null) {
            where += " and id in (" + String.join(",", ids.stream().map(id -> "?").toList()) + ")";
            values.addAll(ids);
        }
        if (account != null && !account.isBlank()) { where += " and user_name like ?"; values.add("%"+account.trim()+"%"); }
        if (name != null && !name.isBlank()) { where += " and real_name like ?"; values.add("%"+name.trim()+"%"); }
        Long total = jdbc.queryForObject("select count(*)"+where, Long.class, values.toArray());
        values.add(size); values.add((page-1)*size);
        var rows = jdbc.queryForList("select "+COLUMNS+where+" order by create_time,id limit ? offset ?", values.toArray());
        rows.forEach(row -> row.put("password", "******"));
        return Map.of("list", rows, "total", total == null ? 0L : total, "pageNum", page, "pageSize", size);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> list(String tenantId, String name) {
        return (List<Map<String, Object>>) page(tenantId, 1, 200, null, name, null).get("list");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> find(String tenantId, String id) {
        if (id == null || id.isBlank()) return null;
        var rows = (List<Map<String, Object>>) page(tenantId, 1, 1, List.of(id), null, null).get("list");
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public boolean hasMembership(String tenantId, String id) {
        requireScope(tenantId);
        return policy.isLocal(tenantId) ? find(tenantId, id) != null : control.hasMembership(tenantId, id);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> names(List<String> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        String tenantId = TenantContext.requireTenantId();
        if (!policy.isLocal(tenantId)) return control.names(ids);
        Map<String, String> result = new LinkedHashMap<>();
        // Do not truncate workflow name lookups to a single page.
        for (int start=0; start<ids.size(); start+=200) {
            var chunk = ids.subList(start, Math.min(ids.size(), start+200));
            var rows = (List<Map<String, Object>>) page(tenantId, 1, 200, chunk, null, null).get("list");
            rows.forEach(row -> result.put(String.valueOf(row.get("id")), (String)row.get("realName")));
        }
        return result;
    }

    private void requireScope(String tenantId) {
        if (TenantContext.usesControlDatabase() || !TenantContext.requireTenantId().equals(tenantId)) {
            throw new TenantAccessException("TENANT-FORBIDDEN", "账号查询必须使用当前租户业务上下文");
        }
    }
}
