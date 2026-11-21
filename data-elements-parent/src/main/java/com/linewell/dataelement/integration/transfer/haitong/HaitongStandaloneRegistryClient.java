package com.linewell.dataelement.integration.transfer.haitong;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.config.DataPushRegistryProperties;
import com.linewell.dataelement.model.common.BizException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Platform-to-relay calls for registration snapshots and legacy credential issue.
 * Each operation sends its own service key and never forwards a business key.
 */
public class HaitongStandaloneRegistryClient {
    private static final Logger LOG = LoggerFactory.getLogger(HaitongStandaloneRegistryClient.class);
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() { };
    private final DataPushRegistryProperties properties;
    private final ObjectMapper json;

    public HaitongStandaloneRegistryClient(DataPushRegistryProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    public void synchronize(String datasourceId, Map<String, Object> datasourceSnapshot) {
        if (properties.getRelayBaseUrl().isBlank() || properties.getStandaloneSyncKey().isBlank()) {
            throw new BizException(503, "推送登记同步服务尚未配置，请检查平台 data-element.push-registry 的 relay-base-url 和 standalone-sync-key");
        }
        String url = registryEndpoint(properties.getRelayBaseUrl());
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("deliveryType", "REGISTRY_SYNC");
            body.put("datasource", datasourceSnapshot);
            var response = HaitongRelayHttp.post(url, Map.of(
                    "X-Standalone-Registry-Key", properties.getStandaloneSyncKey(),
                    "Idempotency-Key", "registry-sync-" + datasourceId + "-" + UUID.randomUUID()),
                    json.writeValueAsBytes(body), properties.getRelayTimeoutSeconds());
            if (response.status() / 100 != 2) {
                throw new BizException(502, "推送登记同步被海通拒绝（HTTP " + response.status()
                        + "），请检查海通同步开关及两端登记同步密钥");
            }
            Map<String, Object> payload = json.readValue(response.body(), JSON_OBJECT);
            String returnedSource = String.valueOf(payload.getOrDefault("datasourceId", "")).trim();
            if (!datasourceId.equals(returnedSource)) {
                throw new BizException(502, "推送登记同步服务返回数据源不匹配");
            }
            if (datasourceSnapshot.containsKey("credential") && !Integer.valueOf(1).equals(payload.get("credentialCount"))) {
                throw new BizException(502, "海通尚未确认平台密钥摘要，请更新海通登记同步脚本后重试同步");
            }
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            LOG.warn("Haitong registration sync transport failed: errorClass={}", exception.getClass().getSimpleName());
            throw new BizException(502, "无法完成海通登记同步连接，请检查服务地址、端口和平台网络（"
                    + exception.getClass().getSimpleName() + "）");
        }
    }

    public IssuedCredential issue(String clientId, boolean rotate) {
        if (properties.getSharedKey().isBlank() || properties.getRelayBaseUrl().isBlank()) {
            throw new BizException(503, "推送密钥签发服务尚未配置，请检查平台 data-element.push-registry 的 relay-base-url 和 shared-key");
        }
        String url = endpoint(properties.getRelayBaseUrl(), properties.getCredentialIssuePath(), "relay-base-url", "credential-issue-path");
        try {
            Map<String, Object> body = Map.of("clientId", clientId, "rotate", rotate);
            var response = HaitongRelayHttp.post(url, Map.of("X-Relay-Registry-Key", properties.getSharedKey()),
                    json.writeValueAsBytes(body), properties.getRelayTimeoutSeconds());
            if (response.status() / 100 != 2) {
                throw new BizException(502, "推送密钥签发被海通拒绝（HTTP " + response.status()
                        + "），请检查两端密钥签发 shared-key 及海通同步开关");
            }
            Map<String, Object> payload = json.readValue(response.body(), JSON_OBJECT);
            if (!(payload.get("data") instanceof Map<?, ?> value)) {
                throw new BizException(502, "推送密钥签发服务返回无效结果");
            }
            String issuedClientId = text(value.get("clientId"));
            boolean revealed = Boolean.TRUE.equals(value.get("revealed"));
            String apiKey = text(value.get("apiKey"));
            if (!clientId.equals(issuedClientId)) throw new BizException(502, "推送密钥签发服务返回调用方不匹配");
            if (revealed && apiKey.isBlank()) throw new BizException(502, "推送密钥签发服务未返回密钥");
            return new IssuedCredential(issuedClientId, apiKey, revealed);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            LOG.warn("Haitong credential issue transport failed: errorClass={}", exception.getClass().getSimpleName());
            throw new BizException(502, "无法完成海通密钥签发连接，请检查服务地址、端口和平台网络（"
                    + exception.getClass().getSimpleName() + "）");
        }
    }

    /** Resolves the registry protocol for the configured relay or a node already selected by the caller. */
    public String registryEndpoint(String baseUrl) {
        return endpoint(baseUrl, properties.getRegistrySyncPath(), "节点或默认海通服务地址", "registry-sync-path");
    }

    private String endpoint(String baseUrl, String path, String baseName, String propertyName) {
        try {
            URI base = URI.create(baseUrl);
            URI suffix = URI.create(path);
            if (!("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))
                    || base.getHost() == null || base.getRawUserInfo() != null
                    || base.getRawQuery() != null || base.getRawFragment() != null
                    || !path.startsWith("/") || path.length() < 2 || suffix.getRawAuthority() != null
                    || !path.equals(suffix.getRawPath()) || !suffix.equals(suffix.normalize())) {
                throw new IllegalArgumentException("Invalid relay endpoint");
            }
            return base.toString().replaceAll("/+$", "") + path;
        } catch (IllegalArgumentException invalid) {
            throw new BizException(503, "海通服务地址配置无效，请检查 " + baseName + " 和 " + propertyName);
        }
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    public record IssuedCredential(String clientId, String apiKey, boolean revealed) { }
}
