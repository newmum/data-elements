package com.linewell.dataelement.feature.diagnostics;

import java.net.URI;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Server-only configuration. Never expose the gateway key through system settings. */
@Component
@ConfigurationProperties(prefix = "ai.diagnostics")
@Getter
@Setter
public class DiagnosticProperties {
    private boolean enabled;
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "";
    private int timeoutSeconds = 120;
    private int maxConcurrent = 4;

    public boolean available() {
        if (!enabled || apiKey.isBlank() || model.isBlank()) return false;
        try { endpoint(); return true; } catch (IllegalArgumentException invalid) { return false; }
    }

    public URI endpoint() {
        URI uri = URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions");
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("请配置有效的服务端模型网关地址");
        return uri;
    }
    public int deadlineSeconds() { return Math.max(15, Math.min(180, timeoutSeconds)); }
    public int concurrency() { return Math.max(1, Math.min(16, maxConcurrent)); }
}
