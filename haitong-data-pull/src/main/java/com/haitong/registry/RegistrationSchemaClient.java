package com.haitong.registry;

import com.haitong.config.RelayProperties;
import com.haitong.config.Secrets;
import com.haitong.core.Json;
import com.haitong.core.Models.Kind;
import com.haitong.core.RelayException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Fail-closed bridge to the data platform's published registration schema.
 * The caller identity is supplied by the local API-key authenticator; the
 * upstream body never carries a tenant id or destination address.
 */
@Component
public class RegistrationSchemaClient {
    private final RelayProperties properties;
    private final Json json;
    private final Secrets secrets;
    public RegistrationSchemaClient(RelayProperties properties, Json json, Secrets secrets) {
        this.properties = properties;
        this.json = json;
        this.secrets = secrets;
    }

    public Registration validate(String callerId, String channel, byte[] input) {
        RelayProperties.RegistrationRegistry registry = properties.getRegistrationRegistry();
        if (!registry.isEnabled()) {
            throw new RelayException(503, "REGISTRATION_REGISTRY_NOT_READY", "登记推送校验服务尚未启用");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("channel", channel);
            body.put("batch", json.object(input));
            String url = registry.getBaseUrl().replaceAll("/+$", "") + "/dws/push/internal/validate";
            Response response = post(url, json.bytes(body), registry, callerId);
            Map<String, Object> payload = json.object(response.body());
            if (response.status() / 100 != 2 || platformFailure(payload)) {
                String code = text(payload.get("code"), "REGISTRATION_VALIDATION_REJECTED");
                String message = text(payload.get("message"), text(payload.get("msg"), "数据源登记校验未通过"));
                int status = response.status() == 401 || response.status() == 403
                        ? 503
                        : destinationUnavailable(message) ? 503 : 422;
                throw new RelayException(status, code, message);
            }
            Object nested = payload.get("data");
            Map<String, Object> data = nested instanceof Map<?, ?> map ? cast(map) : payload;
            String destination = text(data.get("destination"), "");
            String targetType = text(data.get("targetType"), "");
            Kind kind = switch (targetType.toUpperCase()) {
                case "FTP" -> Kind.FTP;
                case "PLATFORM_FTP" -> Kind.PLATFORM_FTP;
                case "KAFKA" -> Kind.KAFKA;
                default -> throw new RelayException(502, "REGISTRATION_RESPONSE_INVALID", "登记校验服务未返回有效投递通道");
            };
            Map<String, Object> metadata = new LinkedHashMap<>();
            for (String key : new String[] {"datasourceId", "tableId", "tableName", "schemaVersion", "schemaHash", "recordCount"}) {
                if (data.containsKey(key)) metadata.put(key, data.get(key));
            }
            return new Registration(kind, destination, Map.copyOf(metadata));
        } catch (RelayException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RelayException(503, "REGISTRATION_REGISTRY_UNAVAILABLE", "登记推送校验服务暂不可用，请使用原幂等键重试");
        } catch (Exception exception) {
            throw new RelayException(503, "REGISTRATION_REGISTRY_UNAVAILABLE", "登记推送校验服务暂不可用，请使用原幂等键重试");
        }
    }

    public Map<String, Object> deliverFtp(String callerId, String deliveryId, byte[] batch) throws Exception {
        RelayProperties.RegistrationRegistry registry = properties.getRegistrationRegistry();
        try {
            String url = registry.getBaseUrl().replaceAll("/+$", "") + "/dws/push/internal/ftp-deliver";
            Map<String, Object> request = new LinkedHashMap<>(); request.put("deliveryId", deliveryId); request.put("batch", json.object(batch));
            Response response = post(url, json.bytes(request), registry, callerId);
            Map<String, Object> payload = json.object(response.body());
            if (response.status() / 100 != 2 || platformFailure(payload)) throw new RelayException(503, "FJGHCC_DELIVERY_FAILED", text(payload.get("message"), "FJGHCC 文件投递失败"));
            Object data = payload.get("data"); return data instanceof Map<?, ?> map ? cast(map) : payload;
        } catch (RelayException exception) { throw exception; }
        catch (Exception exception) { throw new RelayException(503, "FJGHCC_DELIVERY_UNAVAILABLE", "FJGHCC 文件投递暂不可用，请使用原幂等键重试"); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Map<?, ?> value) { return new LinkedHashMap<>((Map<String, Object>) value); }
    private String text(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim();
    }
    /**
     * The platform's low-code API envelope can carry a business failure with
     * HTTP 200 ({@code success:false}/{@code code!=0}).  Treat that envelope
     * exactly as a failed registry response; otherwise a missing registered
     * FTP root or Kafka topic is misleadingly reported as a malformed reply.
     */
    private boolean platformFailure(Map<String, Object> payload) {
        Object success = payload.get("success");
        if (success instanceof Boolean value) return !value;
        Object code = payload.get("code");
        if (code instanceof Number value) return value.longValue() != 0L;
        return "false".equalsIgnoreCase(String.valueOf(success));
    }
    private boolean destinationUnavailable(String message) {
        return message.contains("FTP 输出根目录") || message.contains("Kafka Topic") || message.contains("投递目标");
    }

    private Response post(String url, byte[] body, RelayProperties.RegistrationRegistry registry, String callerId) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        int timeoutMillis = Math.max(1, registry.getTimeoutSeconds()) * 1_000;
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(timeoutMillis);
        connection.setReadTimeout(timeoutMillis);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("X-Relay-Registry-Key", secrets.resolve(registry.getSharedKey()));
        connection.setRequestProperty("X-Relay-Client-Id", callerId);
        try {
            try (OutputStream output = connection.getOutputStream()) { output.write(body); }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            byte[] responseBody;
            if (stream == null) responseBody = new byte[0];
            else try (InputStream input = stream) { responseBody = input.readAllBytes(); }
            return new Response(status, responseBody);
        } finally {
            connection.disconnect();
        }
    }

    private record Response(int status, byte[] body) { }
    public record Registration(Kind kind, String destination, Map<String, Object> metadata) { }
}
