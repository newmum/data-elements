package com.linewell.dataelement.platform.integration.pingao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Calls Pingao only from the server side and never logs credentials or payloads. */
@Component
public class PingaoApplicationClient implements PingaoApplicationGateway {

    private final PingaoApplicationProperties properties;
    private final ObjectMapper objectMapper;
    /** Optional test/client override; production clients are created on first use. */
    private final HttpClient suppliedHttpClient;
    private volatile HttpClient generatedHttpClient;
    private volatile Token token;

    @Autowired
    public PingaoApplicationClient(
            PingaoApplicationProperties properties,
            ObjectMapper objectMapper
    ) {
        this(properties, objectMapper, null);
    }

    PingaoApplicationClient(
            PingaoApplicationProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.suppliedHttpClient = httpClient;
    }

    @Override
    public List<PingaoAbility> listApplications() {
        requireConfigured();
        List<PingaoAbility> result = new ArrayList<>();
        int page = 1;
        int pageSize = properties.getPageSize();
        while (true) {
            List<PingaoAbility> current = requestPage(page, pageSize);
            result.addAll(current);
            if (current.size() < pageSize) return result;
            page++;
        }
    }

    private List<PingaoAbility> requestPage(int page, int pageSize) {
        try {
            String separator = properties.getApplicationUrl().contains("?") ? "&" : "?";
            String url = properties.getApplicationUrl() + separator
                    + "page=" + page + "&size=" + pageSize;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(properties.getRequestTimeout())
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("品高应用接口请求失败，HTTP " + response.statusCode());
            }
            return parseAbilities(response.body());
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("品高应用接口调用失败", exception);
        }
    }

    private synchronized String accessToken() {
        Token cached = token;
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plus(properties.getTokenRefreshSkew()))) {
            return cached.value();
        }
        try {
            String basic = Base64.getEncoder().encodeToString(
                    (properties.getClientId() + ":" + properties.getClientSecret())
                            .getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl()))
                    .timeout(properties.getRequestTimeout())
                    .header("Accept", "application/json")
                    .header("Authorization", "Basic " + basic)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("品高令牌申请失败，HTTP " + response.statusCode());
            }
            JsonNode payload = objectMapper.readTree(response.body());
            String value = payload.path("access_token").asText();
            if (value.isBlank()) throw new IllegalStateException("品高令牌响应缺少 access_token");
            long seconds = payload.path("expires_in").asLong(3600);
            token = new Token(value, Instant.now().plusSeconds(Math.max(seconds, 1)));
            return value;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(tokenFailureMessage(exception), exception);
        }
    }

    private String tokenFailureMessage(Exception exception) {
        if (exception instanceof java.net.http.HttpTimeoutException) {
            return "品高令牌申请超时";
        }
        if (exception instanceof java.net.ConnectException) {
            return "无法连接品高令牌接口";
        }
        if (exception instanceof com.fasterxml.jackson.core.JsonProcessingException) {
            return "品高令牌响应不是有效 JSON";
        }
        return "品高令牌申请失败（" + exception.getClass().getSimpleName() + "）";
    }

    /**
     * HttpClient starts an internal selector thread.  Delay its creation until
     * this optional integration is actually invoked so application startup and
     * Spring wiring remain independent of the host's loopback capabilities.
     */
    private HttpClient httpClient() {
        if (suppliedHttpClient != null) return suppliedHttpClient;
        HttpClient existing = generatedHttpClient;
        if (existing != null) return existing;
        synchronized (this) {
            if (generatedHttpClient == null) {
                generatedHttpClient = HttpClient.newBuilder()
                        .connectTimeout(properties.getConnectTimeout())
                        .version(HttpClient.Version.HTTP_1_1)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
            }
            return generatedHttpClient;
        }
    }

    private List<PingaoAbility> parseAbilities(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode items = root.isArray() ? root : root.path("data");
        if (!items.isArray()) {
            throw new IllegalStateException("品高应用接口响应不是数组");
        }
        List<PingaoAbility> result = new ArrayList<>();
        for (JsonNode item : items) {
            result.add(new PingaoAbility(
                    item.path("id").asText(),
                    item.path("abilityName").asText(),
                    item.path("status").asText()));
        }
        return result;
    }

    private String tokenUrl() {
        return properties.getTokenUrl().contains("grant_type=")
                ? properties.getTokenUrl()
                : properties.getTokenUrl() + (properties.getTokenUrl().contains("?") ? "&" : "?")
                + "grant_type=" + URLEncoder.encode("client_credentials", StandardCharsets.UTF_8);
    }

    private void requireConfigured() {
        if (!properties.isEnabled()) throw new IllegalStateException("品高应用同步未启用");
        if (!properties.isConfigured()) throw new IllegalStateException("品高应用同步配置不完整");
    }

    private record Token(String value, Instant expiresAt) { }
}
