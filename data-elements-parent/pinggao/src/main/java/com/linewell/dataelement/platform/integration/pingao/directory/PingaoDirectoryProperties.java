package com.linewell.dataelement.platform.integration.pingao.directory;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Separate credentials and switches from the already deployed application catalogue. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "data-element.pingao-directory")
public class PingaoDirectoryProperties {
    private boolean enabled;
    private String tokenUrl = "";
    private String usersUrl = "";
    private String organizationsUrl = "";
    private String clientId = "";
    private String clientSecret = "";
    private String externalTenantCode = "";
    private String localTenantId = "";
    private int pageSize = 100;
    private int maxPages = 10000;
    private int maxRecords = 1000000;
    private Duration timeout = Duration.ofSeconds(30);
    private Set<String> administratorUserIds = new HashSet<>();

    public void requireConfigured() {
        if (!enabled) throw new IllegalStateException("统一用户目录预检未启用");
        if (tokenUrl.isBlank() || usersUrl.isBlank() || organizationsUrl.isBlank()
                || clientId.isBlank() || clientSecret.isBlank()
                || externalTenantCode.isBlank() || localTenantId.isBlank()) {
            throw new IllegalStateException("统一用户目录配置不完整");
        }
        if (pageSize < 1 || pageSize > 1000 || maxPages < 1 || maxRecords < 1
                || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalStateException("统一用户目录分页或超时配置无效");
        }
    }
}
