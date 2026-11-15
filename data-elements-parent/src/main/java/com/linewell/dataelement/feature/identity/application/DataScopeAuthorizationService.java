package com.linewell.dataelement.feature.identity.application;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityUserLookupMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Resolves the current user's role scope for shared Java and Magic consumers. */
@Service
public class DataScopeAuthorizationService {

    public static final String DATA_SOURCE_RESOURCE = "DATA_SOURCE";
    private final IdentityUserLookupMapper identityMapper;

    public DataScopeAuthorizationService(IdentityUserLookupMapper identityMapper) {
        this.identityMapper = identityMapper;
    }

    /** Returns the currently effective scope for a resource. */
    public ScopeDecision currentScope(String resourceCode) {
        String tenantId = TenantContext.requireTenantId();
        String userId = currentUserId();
        List<String> roleIds = identityMapper.selectRoleIds(userId, tenantId);
        Scope scope = Scope.OWNER;
        boolean configured = false;
        for (String value : identityMapper.selectRoleDataScopes(roleIds, tenantId)) {
            Scope candidate = Scope.optional(value);
            if (candidate == null) {
                continue;
            }
            configured = true;
            if (candidate.rank > scope.rank) {
                scope = candidate;
            }
        }
        List<String> organizationIds = scope == Scope.ORG
                ? currentUserOrganizationIds(userId, tenantId)
                : List.of();
        return new ScopeDecision(resourceCode, scope, userId, organizationIds, roleIds, configured);
    }

    /** Applies the current legacy DATA_SOURCE decision to a source master row. */
    public boolean canReadDataSource(Map<String, ?> dataSource) {
        return canRead(dataSource, currentScope(DATA_SOURCE_RESOURCE));
    }

    /** Applies a menu- or component-specific scope to a source master row. */
    public boolean canReadDataSource(String resourceCode, Map<String, ?> dataSource) {
        return canRead(dataSource, currentScope(resourceCode));
    }

    /** Applies an already-resolved decision, useful when filtering an entire page. */
    public boolean canRead(Map<String, ?> dataSource, ScopeDecision decision) {
        if (dataSource == null || decision == null) {
            return false;
        }
        return switch (decision.scope()) {
            case ALL -> true;
            case OWNER -> decision.userId().equals(value(dataSource, "created_by", "createdBy"));
            case ORG -> decision.organizationIds().contains(value(dataSource, "org_id", "orgId"));
        };
    }

    public List<Map<String, Object>> visibleDataSources(Collection<? extends Map<String, ?>> values) {
        ScopeDecision decision = currentScope(DATA_SOURCE_RESOURCE);
        return visibleDataSources(values, decision);
    }

    /**
     * Filters source master rows using a scope attached to one concrete menu or
     * low-code component. Resource codes such as {@code MENU:123} and
     * {@code COMPONENT:MY-DB} are configuration keys, not role-name rules.
     */
    public List<Map<String, Object>> visibleDataSources(
            String resourceCode,
            Collection<? extends Map<String, ?>> values
    ) {
        return visibleDataSources(values, currentScope(resourceCode));
    }

    /**
     * Filters application-system options for the first step of data-source
     * registration.  This selector intentionally has one narrower policy than
     * the ordinary data-source list: a user whose general data scope is OWNER
     * may register a source for an application belonging to the user's own
     * department.  The exception is contained here so it cannot accidentally
     * broaden any other OWNER-scoped page.
     *
     * <p>ALL remains unrestricted.  Every other effective scope is resolved
     * against the authenticated user's department memberships rather than a
     * client-supplied organization id.</p>
     */
    public List<Map<String, Object>> visibleRegistrationApplications(
            String resourceCode,
            Collection<? extends Map<String, ?>> applications
    ) {
        ScopeDecision decision = currentScope(resourceCode);
        if (decision.scope() == Scope.ALL) {
            return copyValues(applications);
        }
        String tenantId = TenantContext.requireTenantId();
        ScopeDecision departmentDecision = new ScopeDecision(
                decision.resourceCode(),
                Scope.ORG,
                decision.userId(),
                currentUserOrganizationIds(decision.userId(), tenantId),
                decision.roleIds(),
                decision.configured()
        );
        return visibleDataSources(applications, departmentDecision);
    }

    private List<Map<String, Object>> visibleDataSources(
            Collection<? extends Map<String, ?>> values,
            ScopeDecision decision
    ) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, ?> value : values) {
            if (canRead(value, decision)) {
                result.add(new LinkedHashMap<>(value));
            }
        }
        return result;
    }

    private List<Map<String, Object>> copyValues(Collection<? extends Map<String, ?>> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, ?> value : values) {
            if (value != null) {
                result.add(new LinkedHashMap<>(value));
            }
        }
        return result;
    }

    private List<String> currentUserOrganizationIds(String userId, String tenantId) {
        List<String> organizationIds = identityMapper.selectOrganizationIds(userId, tenantId);
        if (organizationIds == null || organizationIds.isEmpty()) {
            return List.of();
        }
        return organizationIds.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String currentUserId() {
        if (!StpUtil.isLogin()) {
            throw new IllegalStateException("登录已过期，请重新登录");
        }
        return String.valueOf(StpUtil.getLoginId());
    }

    private String value(Map<String, ?> source, String... names) {
        for (String name : names) {
            Object value = source.get(name);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        // JDBC/MagicScript row maps normalize aliases differently by driver
        // (for example orgId becomes orgid). Data-scope ownership checks must
        // not turn that representation detail into a false denial.
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            for (String name : names) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name) && entry.getValue() != null) {
                    return String.valueOf(entry.getValue());
                }
            }
        }
        return "";
    }

    public enum Scope {
        OWNER(1), ORG(2), ALL(3);

        private final int rank;

        Scope(int rank) {
            this.rank = rank;
        }

        static Scope optional(String value) {
            try {
                return Scope.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                return null;
            }
        }

    }

    public record ScopeDecision(
            String resourceCode,
            Scope scope,
            String userId,
            List<String> organizationIds,
            List<String> roleIds,
            boolean configured
    ) {
        public Map<String, Object> asMap() {
            return Map.of(
                    "resourceCode", resourceCode,
                    "scope", scope.name(),
                    "userId", userId,
                    "organizationIds", organizationIds,
                    "roleIds", roleIds,
                    "configured", configured
            );
        }
    }
}
