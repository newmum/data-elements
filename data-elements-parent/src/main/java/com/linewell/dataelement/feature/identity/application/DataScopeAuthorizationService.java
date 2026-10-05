package com.linewell.dataelement.feature.identity.application;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityUserLookupMapper;
import com.linewell.dataelement.platform.configuration.SystemConfigService;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Resolves configurable resource data scopes for the current tenant.
 *
 * <p>The role-to-scope mapping is maintained by the low-code authorization
 * screen in {@code sym_config_t}; this service deliberately does not inspect
 * role names or role codes.  The only built-in concepts are reusable scope
 * values: ALL, ORG and OWNER.</p>
 */
@Service
public class DataScopeAuthorizationService {

    public static final String DATA_SOURCE_RESOURCE = "DATA_SOURCE";
    public static final String CONFIG_GROUP = "AUTH_DATA_SCOPE";
    public static final String CONFIG_CODE = "RESOURCE_SCOPES";

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
            new TypeReference<>() { };

    private final SystemConfigService systemConfigService;
    private final IdentityUserLookupMapper identityMapper;
    private final ObjectMapper objectMapper;

    public DataScopeAuthorizationService(
            SystemConfigService systemConfigService,
            IdentityUserLookupMapper identityMapper,
            ObjectMapper objectMapper
    ) {
        this.systemConfigService = systemConfigService;
        this.identityMapper = identityMapper;
        this.objectMapper = objectMapper;
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

    /** Returns the configured value for one role. Unconfigured roles are safely owner-only. */
    public Map<String, Object> roleScope(String roleId, String resourceCode) {
        requireRoleId(roleId);
        Scope configured = Scope.optional(identityMapper.selectRoleDataScope(roleId, TenantContext.requireTenantId()));
        Scope value = configured == null ? Scope.OWNER : configured;
        return Map.of(
                "resourceCode", normalizeResource(resourceCode),
                "roleId", roleId,
                "scope", value.name(),
                "configured", configured != null
        );
    }

    /**
     * Saves a role data-scope selection.  The current tenant is always used;
     * callers cannot provide a tenant id.
     */
    public Map<String, Object> saveRoleScope(String roleId, String resourceCode, String requestedScope) {
        requireRoleId(roleId);
        String tenantId = TenantContext.requireTenantId();
        // A role must be from the active tenant before it may receive a policy.
        if (identityMapper.countActiveRole(roleId, tenantId) == 0) {
            throw new IllegalArgumentException("角色不存在或不属于当前租户");
        }
        Scope scope = Scope.require(requestedScope);
        if (identityMapper.updateRoleDataScope(roleId, tenantId, scope.name()) != 1) {
            throw new IllegalArgumentException("角色不存在或不属于当前租户");
        }
        return roleScope(roleId, resourceCode);
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

    private Map<String, Object> roleScopes(String resourceCode) {
        Map<String, Object> resources = map(allResourceScopes().get("resources"));
        String resourceCodeValue = normalizeResource(resourceCode);
        Map<String, Object> resource = map(resources.get(resourceCodeValue));
        Map<String, Object> roles = map(resource.get("roles"));
        // Existing DATA_SOURCE policies remain a conservative compatibility
        // baseline while administrators progressively configure concrete menu /
        // component targets. A target-specific policy always overrides it.
        if (roles.isEmpty() && !DATA_SOURCE_RESOURCE.equals(resourceCodeValue)) {
            return map(map(resources.get(DATA_SOURCE_RESOURCE)).get("roles"));
        }
        return roles;
    }

    private Map<String, Object> allResourceScopes() {
        Object value = systemConfigService.value(CONFIG_GROUP, CONFIG_CODE);
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>(cast(map));
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return objectMapper.readValue(text, MAP_TYPE);
            } catch (Exception ignored) {
                return new LinkedHashMap<>();
            }
        }
        return new LinkedHashMap<>();
    }

    private Scope configuredScope(Object value) {
        if (value instanceof Map<?, ?> map) {
            value = map.get("scope");
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Scope.optional(String.valueOf(value));
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(cast(map));
    }

    private Map<String, Object> cast(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private String normalizeResource(String resourceCode) {
        String value = resourceCode == null || resourceCode.isBlank()
                ? DATA_SOURCE_RESOURCE
                : resourceCode.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9_:.\\-]{1,160}")) {
            throw new IllegalArgumentException("资源标识不合法");
        }
        return value;
    }

    private void requireRoleId(String roleId) {
        if (roleId == null || roleId.isBlank()) {
            throw new IllegalArgumentException("角色ID不能为空");
        }
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

        static Scope require(String value) {
            Scope scope = optional(value);
            if (scope == null) {
                throw new IllegalArgumentException("数据范围仅支持 ALL、ORG 或 OWNER");
            }
            return scope;
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
