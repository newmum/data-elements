package com.haitong.magic;

import com.haitong.config.RelayProperties;
import com.haitong.config.Secrets;
import com.haitong.core.Json;
import com.haitong.core.RelayException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.springframework.stereotype.Component;

/**
 * Java implementation behind the three Magic-API gateway scripts.  The
 * scripts deliberately contain no endpoint credential: deployment-specific
 * OAuth settings stay in RelayProperties and are resolved only at runtime.
 */
@Component("zeroTrustGateway")
@MagicModule("zeroTrustGateway")
public final class ZeroTrustGatewayApi {
    private final RelayProperties properties;
    private final Secrets secrets;
    private final Json json;
    private volatile HttpClient http;

    public ZeroTrustGatewayApi(RelayProperties properties, Secrets secrets, Json json) {
        this.properties = properties;
        this.secrets = secrets;
        this.json = json;
    }

    /** Read-only deployment readiness probe. It intentionally never returns clientSecret or accessToken. */
    public Map<String, Object> configCheck() {
        var gateway = properties.getZeroTrustGateway();
        List<String> missing = missing(gateway);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", gateway.isEnabled());
        result.put("ready", gateway.isEnabled() && missing.isEmpty());
        result.put("missing", missing);
        result.put("tokenConfigured", !gateway.getTokenUrl().isBlank());
        result.put("pushConfigured", !gateway.getPushUrl().isBlank());
        result.put("timeoutSeconds", gateway.getTimeoutSeconds());
        result.put("auditDefaultKeys", gateway.getAuditDefaults().keySet());
        return result;
    }

    /** Verify OAuth configuration. dryRun never requests a token from the gateway. */
    public Map<String, Object> tokenStatus(Map<String, Object> input) {
        boolean dryRun = flag(input.get("dryRun"));
        Map<String, Object> result = new LinkedHashMap<>(configCheck());
        result.put("dryRun", dryRun);
        if (dryRun || !Boolean.TRUE.equals(result.get("ready"))) return result;
        Token token = token();
        result.put("tokenAvailable", true);
        result.put("tokenType", token.type());
        result.put("expiresIn", token.expiresIn());
        return result;
    }

    /**
     * Proxy an authorised table batch to the configured zero-trust gateway.
     * A caller may use dryRun=true to validate the envelope without reaching
     * the external endpoint. The original X-Api-Key is never persisted here.
     */
    public Map<String, Object> dataPush(Map<String, Object> input, String apiKey, String idempotencyKey) {
        if (apiKey == null || apiKey.isBlank()) throw new RelayException(401, "UNAUTHENTICATED", "缺少 X-Api-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 200)
            throw RelayException.invalid("Idempotency-Key 必须为 1 到 200 个字符");
        require(input, "deliveryType", "datasourceId", "tableName", "records");
        if (!(input.get("records") instanceof List<?> records) || records.isEmpty())
            throw RelayException.invalid("records 必须为非空数组");
        boolean dryRun = flag(input.get("dryRun"));
        Map<String, Object> readiness = configCheck();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dryRun", dryRun);
        result.put("valid", Boolean.TRUE.equals(readiness.get("ready")));
        result.put("recordCount", records.size());
        result.put("datasourceId", text(input.get("datasourceId")));
        result.put("tableName", text(input.get("tableName")));
        result.put("deliveryType", text(input.get("deliveryType")));
        result.put("missing", readiness.get("missing"));
        if (dryRun || !Boolean.TRUE.equals(readiness.get("ready"))) return result;

        Map<String, Object> outbound = new LinkedHashMap<>(input);
        outbound.remove("dryRun");
        Map<String, Object> audit = new LinkedHashMap<>(properties.getZeroTrustGateway().getAuditDefaults());
        if (input.get("gatewayAudit") instanceof Map<?, ?> supplied) {
            for (Map.Entry<?, ?> entry : supplied.entrySet()) if (entry.getKey() instanceof String key) audit.put(key, entry.getValue());
        }
        outbound.remove("gatewayAudit");
        audit.putIfAbsent("Request_ID", UUID.randomUUID().toString());
        audit.putIfAbsent("Requester_IP", properties.getZeroTrustGateway().getRequesterIp());
        audit.putIfAbsent("Query_Datetime", Instant.now().toString());
        outbound.put("gatewayAudit", audit);
        Token token = token();
        HttpResponse<String> response;
        try {
            response = http().send(HttpRequest.newBuilder(URI.create(properties.getZeroTrustGateway().getPushUrl()))
                    .timeout(Duration.ofSeconds(properties.getZeroTrustGateway().getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", token.type() + " " + token.value())
                    .header("X-Api-Key", apiKey)
                    .header("Idempotency-Key", idempotencyKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json.write(outbound), StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RelayException(502, "ZERO_TRUST_GATEWAY_UNAVAILABLE", "零信任网关暂时不可用");
        }
        result.put("delivered", response.statusCode() >= 200 && response.statusCode() < 300);
        result.put("downstreamStatus", response.statusCode());
        // Do not reflect an arbitrary upstream body, which could contain credentials or HTML error pages.
        return result;
    }

    private Token token() {
        var gateway = properties.getZeroTrustGateway();
        List<String> missing = missing(gateway);
        if (!gateway.isEnabled() || !missing.isEmpty())
            throw new RelayException(503, "ZERO_TRUST_GATEWAY_NOT_READY", "零信任网关配置尚未就绪");
        String basic = Base64.getEncoder().encodeToString((gateway.getClientId() + ":" + secrets.resolve(gateway.getClientSecret())).getBytes(StandardCharsets.UTF_8));
        try {
            HttpResponse<String> response = http().send(HttpRequest.newBuilder(URI.create(gateway.getTokenUrl()))
                    .timeout(Duration.ofSeconds(gateway.getTimeoutSeconds()))
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials", StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new RelayException(502, "ZERO_TRUST_TOKEN_REJECTED", "零信任网关令牌请求被拒绝");
            Map<String, Object> value = json.object(response.body().getBytes(StandardCharsets.UTF_8));
            String accessToken = text(value.get("access_token"));
            if (accessToken.isBlank()) throw new RelayException(502, "ZERO_TRUST_TOKEN_INVALID", "零信任网关令牌响应无效");
            String type = text(value.get("token_type"));
            Object expires = value.get("expires_in");
            return new Token(accessToken, type.isBlank() ? "Bearer" : type, expires instanceof Number n ? n.longValue() : null);
        } catch (RelayException e) {
            throw e;
        } catch (Exception e) {
            throw new RelayException(502, "ZERO_TRUST_TOKEN_UNAVAILABLE", "零信任网关令牌服务暂时不可用");
        }
    }

    private List<String> missing(RelayProperties.ZeroTrustGateway gateway) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        if (!gateway.isEnabled()) result.add("enabled");
        if (gateway.getTokenUrl().isBlank()) result.add("tokenUrl");
        if (gateway.getPushUrl().isBlank()) result.add("pushUrl");
        if (gateway.getClientId().isBlank()) result.add("clientId");
        try { if (secrets.resolve(gateway.getClientSecret()).isBlank()) result.add("clientSecret"); }
        catch (IllegalStateException e) { result.add("clientSecret"); }
        if (gateway.getTimeoutSeconds() < 1 || gateway.getTimeoutSeconds() > 120) result.add("timeoutSeconds");
        return List.copyOf(result);
    }
    private HttpClient http() {
        HttpClient value = http;
        if (value != null) return value;
        synchronized (this) {
            if (http == null) http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
            return http;
        }
    }
    private static void require(Map<String, Object> input, String... names) { for (String name : names) if (text(input.get(name)).isBlank()) throw RelayException.invalid("缺少必填字段：" + name); }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static boolean flag(Object value) { return value instanceof Boolean b && b; }
    private record Token(String value, String type, Long expiresIn) { }
}
