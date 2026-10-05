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
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Writes the current registered datasource/table/column snapshot to the
 * standalone Haitong relay.  This uses a dedicated service-to-service key;
 * it never sends the business caller's X-Api-Key over this endpoint.
 */
@Component
public class HaitongStandaloneRegistryClient {
    private final DataPushRegistryProperties properties;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    public HaitongStandaloneRegistryClient(DataPushRegistryProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    public void synchronize(String datasourceId, Map<String, Object> datasourceSnapshot) {
        if (properties.getRelayBaseUrl().isBlank() || properties.getStandaloneSyncKey().isBlank()) {
            throw new IllegalStateException("推送登记同步服务尚未配置");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("deliveryType", "REGISTRY_SYNC");
            body.put("datasource", datasourceSnapshot);
            String url = properties.getRelayBaseUrl().replaceAll("/+$", "") + "/api/v1/data-push";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getRelayTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .header("X-Standalone-Registry-Key", properties.getStandaloneSyncKey())
                    .header("Idempotency-Key", "registry-sync-" + datasourceId + "-" + UUID.randomUUID())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)))
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("推送登记同步服务暂不可用");
            }
            Map<String, Object> payload = json.readValue(response.body(), new TypeReference<LinkedHashMap<String, Object>>() { });
            String returnedSource = String.valueOf(payload.getOrDefault("datasourceId", "")).trim();
            if (!datasourceId.equals(returnedSource)) {
                throw new IllegalStateException("推送登记同步服务返回数据源不匹配");
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("推送登记同步服务暂不可用");
        }
    }
}
