package com.linewell.dataelement.feature.identity.application;

import com.linewell.dataelement.platform.tenant.infrastructure.datasource.UseControlDataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;

/**
 * Explicit pre-migration compatibility only. Disabled after independent local
 * account migration; platform and subject accounts never replace local login.
 */
@Service
@UseControlDataSource
public class ControlIdentityService {

    private final JdbcTemplate jdbcTemplate;
    @Value("${idaas.legacy-control-accounts.enabled:false}")
    private boolean legacyControlAccountsEnabled;

    private void requireLegacyCompatibility() {
        if (!legacyControlAccountsEnabled) throw new TenantAccessException("TENANT-ACCOUNT-SOURCE-RETIRED", "共享账号来源已退役，请使用所属应用的本地账号接口");
    }

    public ControlIdentityService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> page(
            String tenantId,
            long pageNum,
            long pageSize,
            List<String> ids,
            String userName,
            String realName
    ) {
        requireLegacyCompatibility();
        long safePage = Math.max(pageNum, 1L);
        long safeSize = Math.min(Math.max(pageSize, 1L), 200L);
        List<Object> params = new ArrayList<>();
        String where = activeMembershipWhere(tenantId, ids, userName, realName, params);
        Long total = jdbcTemplate.queryForObject(
                "select count(1) " + where,
                Long.class,
                params.toArray()
        );
        List<Object> listParams = new ArrayList<>(params);
        listParams.add(safeSize);
        listParams.add((safePage - 1) * safeSize);
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "select u.id, u.real_name as realName, u.user_name as userName, u.phone, u.gender, "
                        + "u.app_file_id as appFileId, u.create_id as createId, u.status, "
                        + "u.create_time as createTime, u.update_time as updateTime "
                        + where + " order by u.create_time, u.id limit ? offset ?",
                listParams.toArray()
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total == null ? 0L : total);
        result.put("pageNum", safePage);
        result.put("pageSize", safeSize);
        return result;
    }

    public List<Map<String, Object>> list(String tenantId, String userName) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) page(
                tenantId, 1L, 200L, null, userName, null
        ).get("list");
        return list;
    }

    public boolean hasMembership(String tenantId, String userId) {
        requireLegacyCompatibility();
        if (blank(tenantId) || blank(userId)) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "select count(1) from rm_user_tenant_rela_t "
                        + "where tenant_id = ? and user_id = ? and status = 1 and is_del = 0",
                Integer.class,
                tenantId.trim(), userId.trim()
        );
        return count != null && count > 0;
    }

    /** Returns one active control-plane account only when it belongs to the tenant. */
    public Map<String, Object> find(String tenantId, String userId) {
        requireLegacyCompatibility();
        if (blank(tenantId) || blank(userId)) {
            return null;
        }
        Map<String, Object> page = page(tenantId, 1L, 1L, List.of(userId), null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) page.get("list");
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** Retires one membership and only retires the account after its final active membership. */
    @Transactional
    public Map<String, Object> removeMembership(String tenantId, String userId, String operator) {
        requireLegacyCompatibility();
        Map<String, Object> account = find(tenantId, userId);
        if (account == null) {
            throw new IllegalArgumentException("active tenant account was not found");
        }
        int membershipUpdated = jdbcTemplate.update(
                "update rm_user_tenant_rela_t set is_del = 1, status = 0, updated_by = ?, "
                        + "updated_time = current_timestamp where tenant_id = ? and user_id = ? "
                        + "and status = 1 and is_del = 0",
                operator, tenantId, userId
        );
        Integer remaining = jdbcTemplate.queryForObject(
                "select count(1) from rm_user_tenant_rela_t "
                        + "where user_id = ? and status = 1 and is_del = 0",
                Integer.class, userId
        );
        boolean accountDeleted = remaining == null || remaining == 0;
        if (accountDeleted) {
            jdbcTemplate.update(
                    "update rm_user_t set deleted = 1, update_id = ?, update_time = current_timestamp "
                            + "where id = ? and deleted = 0",
                    operator, userId
            );
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", userId);
        result.put("membershipDeleted", membershipUpdated > 0);
        result.put("accountDeleted", accountDeleted);
        return result;
    }

    public Map<String, String> names(List<String> userIds) {
        requireLegacyCompatibility();
        List<String> ids = normalizeIds(userIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", ids.stream().map(value -> "?").toList());
        Map<String, String> result = new LinkedHashMap<>();
        jdbcTemplate.query(
                "select id, real_name from rm_user_t where deleted = 0 and id in (" + placeholders + ")",
                ids.toArray(),
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        result.put(rs.getString("id"), rs.getString("real_name"))
        );
        return result;
    }

    private String activeMembershipWhere(
            String tenantId,
            List<String> ids,
            String userName,
            String realName,
            List<Object> params
    ) {
        if (blank(tenantId)) {
            throw new IllegalArgumentException("tenantId is required");
        }
        StringBuilder where = new StringBuilder("from rm_user_t u where u.deleted = 0 and exists ("
                + "select 1 from rm_user_tenant_rela_t utr where utr.user_id = u.id "
                + "and utr.tenant_id = ? and utr.status = 1 and utr.is_del = 0)");
        params.add(tenantId.trim());
        List<String> normalizedIds = normalizeIds(ids);
        if (!normalizedIds.isEmpty()) {
            where.append(" and u.id in (")
                    .append(String.join(",", normalizedIds.stream().map(value -> "?").toList()))
                    .append(")");
            params.addAll(normalizedIds);
        }
        appendLike(where, params, "u.user_name", userName);
        appendLike(where, params, "u.real_name", realName);
        return where.toString();
    }

    private void appendLike(StringBuilder sql, List<Object> params, String column, String value) {
        if (!blank(value)) {
            sql.append(" and lower(").append(column).append(") like ?");
            params.add("%" + value.trim().toLowerCase(Locale.ROOT) + "%");
        }
    }

    private List<String> normalizeIds(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(value -> !blank(value))
                .map(String::trim)
                .distinct()
                .toList();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
