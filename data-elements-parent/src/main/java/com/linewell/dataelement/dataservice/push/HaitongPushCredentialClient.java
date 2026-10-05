package com.linewell.dataelement.dataservice.push;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Protected platform-to-relay credential issue call. */
@Component
public class HaitongPushCredentialClient {
    private final DataPushRegistryProperties properties;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    public HaitongPushCredentialClient(DataPushRegistryProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    public IssuedCredential issue(String clientId, boolean rotate) {
        if (properties.getSharedKey().isBlank() || properties.getRelayBaseUrl().isBlank())
            throw new IllegalStateException("推送密钥签发服务尚未配置");
        try {
            Map<String, Object> body = Map.of("clientId", clientId, "rotate", rotate);
            String url = properties.getRelayBaseUrl().replaceAll("/+$", "") + "/internal/push-credentials/issue";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getRelayTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .header("X-Relay-Registry-Key", properties.getSharedKey())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)))
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("推送密钥签发服务暂不可用");
            Map<String, Object> payload = json.readValue(response.body(), new TypeReference<LinkedHashMap<String, Object>>() { });
            Object nested = payload.get("data");
            if (!(nested instanceof Map<?, ?> value)) throw new IllegalStateException("推送密钥签发服务返回无效结果");
            String issuedClientId = text(value.get("clientId"));
            boolean revealed = Boolean.TRUE.equals(value.get("revealed"));
            String apiKey = text(value.get("apiKey"));
            if (!clientId.equals(issuedClientId)) throw new IllegalStateException("推送密钥签发服务返回调用方不匹配");
            if (revealed && apiKey.isBlank()) throw new IllegalStateException("推送密钥签发服务未返回密钥");
            return new IssuedCredential(issuedClientId, apiKey, revealed);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("推送密钥签发服务暂不可用");
        }
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    public record IssuedCredential(String clientId, String apiKey, boolean revealed) { }
}
