package com.linewell.dataelement.feature.approval.application;

import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityUserLookupMapper;
import com.linewell.dataelement.model.common.BizException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Associates an engine business ID with its authoritative tenant business record. */
@Service
public class ApprovalBusinessAssociationService {
    private static final String SQL = """
        SELECT a.tid AS business_id, a.tenant_id, 'applyForm' AS business_type,
               a.apply_name AS business_name, a.apply_org_id AS org_id,
               a.apply_user_id AS owner_id, a.created_by, a.type AS request_type
          FROM data_apply_form_t a
         WHERE a.tenant_id=? AND a.tid=? AND COALESCE(a.is_del,0)=0
        UNION ALL
        SELECT p.tid AS business_id, p.tenant_id, 'publication' AS business_type,
               c.catalog_name AS business_name, c.org_id, p.created_by AS owner_id,
               p.created_by, 'haoyuePublication' AS request_type
          FROM res_catalog_publish_request p
          JOIN da_catalog_t c ON c.tenant_id=p.tenant_id AND c.tid=p.catalog_id
                             AND COALESCE(c.is_del,0)=0
         WHERE p.tenant_id=? AND p.tid=? AND COALESCE(p.is_del,0)=0
        """;

    private final JdbcTemplate jdbc;
    private final IdentityUserLookupMapper identities;

    public ApprovalBusinessAssociationService(JdbcTemplate jdbc, IdentityUserLookupMapper identities) {
        this.jdbc = jdbc;
        this.identities = identities;
    }

    public Business load(String businessId) {
        if (businessId == null || businessId.isBlank()) throw new BizException(-1, "业务单号不能为空");
        String tenant = TenantContext.requireTenantId();
        List<Map<String, Object>> rows = jdbc.queryForList(SQL, tenant, businessId, tenant, businessId);
        if (rows.size() != 1) throw new BizException(404, "审批业务单不存在或无法唯一关联");
        Map<String, Object> row = new LinkedHashMap<>();
        rows.get(0).forEach((key, value) -> row.put(key.toLowerCase(Locale.ROOT), value));
        if (!tenant.equals(text(row, "tenant_id")) || !businessId.equals(text(row, "business_id"))) {
            throw new BizException(403, "审批业务单不属于当前租户");
        }
        return new Business(businessId, tenant, text(row, "business_type"), text(row, "business_name"),
                text(row, "org_id"), text(row, "owner_id"), text(row, "created_by"), text(row, "request_type"));
    }

    public void requireInitiator(Business business, String userId) {
        if (userId == null || userId.isBlank()) throw new BizException(401, "当前用户未认证");
        if (userId != null && (userId.equals(business.ownerId()) || userId.equals(business.createdBy()))) return;
        if (!business.orgId().isBlank() && identities.selectOrganizationIds(userId, business.tenantId())
                .contains(business.orgId())) return;
        throw new BizException(403, "只能操作本人或所属部门的审批业务单");
    }

    public void requireCompatible(Business business, String approveType) {
        if ("checkIn".equals(approveType) || "assetUpdate".equals(approveType)) {
            throw new BizException(409, "资产登记和更新直接保存，不使用资产审批关联");
        }
        boolean publication = "publication".equals(business.type());
        boolean subscription = "CATALOG_SUBSCRIPTION".equals(business.requestType());
        if (publication != "haoyuePublication".equals(approveType)
                || subscription != "haoyueSubscription".equals(approveType)) {
            throw new BizException(409, "审批类型与业务单类型不一致");
        }
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    public record Business(String id, String tenantId, String type, String name, String orgId,
                           String ownerId, String createdBy, String requestType) {
        public String approveType() {
            if ("publication".equals(type)) return "haoyuePublication";
            return "CATALOG_SUBSCRIPTION".equals(requestType) ? "haoyueSubscription" : requestType;
        }
    }
}
