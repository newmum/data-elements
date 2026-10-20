package com.linewell.dataelement.platform.integration.pingao.sso;

import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityUserLookupMapper;
import com.linewell.dataelement.platform.integration.pingao.directory.PingaoDirectoryClient;
import com.linewell.dataelement.platform.integration.pingao.directory.PingaoDirectoryProperties;
import com.linewell.dataelement.platform.integration.pingao.directory.PingaoIdentityMappingRepository;
import com.linewell.dataelement.platform.tenant.application.TenantAccessService;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class PingaoSsoIdentityService {
    private final PingaoSsoProperties properties;
    private final PingaoDirectoryProperties directoryProperties;
    private final PingaoDirectoryClient directory;
    private final PingaoIdentityMappingRepository mappings;
    private final TenantAccessService tenants;
    private final IdentityUserLookupMapper users;

    public PingaoSsoIdentityService(PingaoSsoProperties properties, PingaoDirectoryProperties directoryProperties,
            PingaoDirectoryClient directory, PingaoIdentityMappingRepository mappings,
            TenantAccessService tenants, IdentityUserLookupMapper users) {
        this.properties = properties;
        this.directoryProperties = directoryProperties;
        this.directory = directory;
        this.mappings = mappings;
        this.tenants = tenants;
        this.users = users;
    }

    public String resolve(PingaoSsoClient.Identity identity) {
        properties.requireConfigured();
        requireDirectoryScope();
        // Until persistent directory synchronization is commissioned, verify live status first.
        // This also keeps an administrator-confirmed mapping observed without making a 24-hour-old
        // map reject a user before the fresh directory result can be considered.
        Instant observedAt = Instant.now();
        var snapshot = directory.fetch(0);
        var remote = snapshot.users().stream().filter(u -> u.id().equals(identity.directoryUserId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("统一用户目录未返回该账号"));
        if (!remote.enabled() || remote.deleted()) throw new IllegalStateException("统一用户账号已停用");
        if (identity.externalOrganizationId().isBlank() || remote.memberships().stream()
                .noneMatch(membership -> membership.organizationId().equals(identity.externalOrganizationId()))) {
            throw new IllegalStateException("统一认证返回的机构不属于该目录用户");
        }
        var remoteOrganization = snapshot.organizations().stream()
                .filter(organization -> organization.id().equals(identity.externalOrganizationId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("统一用户目录未返回认证机构"));
        if (!remoteOrganization.enabled() || remoteOrganization.deleted()) {
            throw new IllegalStateException("统一认证所属机构已停用");
        }
        mappings.observeUser(properties.getLocalTenantId(), properties.getExternalTenantCode(), remote, observedAt);
        mappings.observeOrganization(properties.getLocalTenantId(), properties.getExternalTenantCode(), remoteOrganization,
                observedAt);
        String id = mappings.resolveUser(properties.getLocalTenantId(), properties.getExternalTenantCode(),
                identity.directoryUserId(), observedAt.minus(Duration.ofMinutes(1)));
        mappings.resolveOrganization(properties.getLocalTenantId(), properties.getExternalTenantCode(),
                identity.externalOrganizationId(), observedAt.minus(Duration.ofMinutes(1)));
        requireAuthorized(id);
        return id;
    }

    public void requireAuthorized(String id) {
        properties.requireConfigured();
        tenants.requireActiveTenant(properties.getLocalTenantId());
        tenants.requireMembership(id, properties.getLocalTenantId());
        if (!mappings.activeAccount(properties.getLocalTenantId(), id, properties.getLocalActiveAccountStatus())) {
            throw new IllegalStateException("本地账号已停用或尚未分配租户");
        }
        boolean hasRoles = TenantContext.call(properties.getLocalTenantId(), () ->
                !users.selectRoleCodes(id, properties.getLocalTenantId(), properties.getLocalAppId()).isEmpty());
        if (!hasRoles) throw new IllegalStateException("账号待分配本平台业务角色");
    }

    public void requireDirectoryScope() {
        if (!properties.getLocalTenantId().equals(directoryProperties.getLocalTenantId())
                || !properties.getExternalTenantCode().equals(directoryProperties.getExternalTenantCode())) {
            throw new IllegalStateException("SSO 与统一用户目录租户配置不一致");
        }
    }
}
