package com.linewell.dataelement.dataservice.push;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.model.common.BizException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Protected platform-to-relay credential issue call. */
@Component
public class HaitongPushCredentialClient {
    private final DataPushRegistryProperties properties;
    private final ObjectMapper json;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HaitongPushCredentialClient.class);

    public HaitongPushCredentialClient(DataPushRegistryProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    public IssuedCredential issue(String clientId, boolean rotate) {
        if (properties.getSharedKey().isBlank() || properties.getRelayBaseUrl().isBlank())
            throw new BizException(503, "推送密钥签发服务尚未配置，请检查平台 data-element.push-registry 的 relay-base-url 和 shared-key");
        try {
            Map<String, Object> body = Map.of("clientId", clientId, "rotate", rotate);
            String url = properties.getRelayBaseUrl().replaceAll("/+$", "") + "/internal/push-credentials/issue";
            var response = HaitongRelayHttp.post(url, Map.of("X-Relay-Registry-Key", properties.getSharedKey()),
                    json.writeValueAsBytes(body), properties.getRelayTimeoutSeconds());
            if (response.status() / 100 != 2) throw new BizException(502, "推送密钥签发被海通拒绝（HTTP "
                    + response.status() + "），请检查两端密钥签发 shared-key 及海通同步开关");
            Map<String, Object> payload = json.readValue(response.body(), new TypeReference<LinkedHashMap<String, Object>>() { });
            Object nested = payload.get("data");
            if (!(nested instanceof Map<?, ?> value)) throw new BizException(502, "推送密钥签发服务返回无效结果");
            String issuedClientId = text(value.get("clientId"));
            boolean revealed = Boolean.TRUE.equals(value.get("revealed"));
            String apiKey = text(value.get("apiKey"));
            if (!clientId.equals(issuedClientId)) throw new BizException(502, "推送密钥签发服务返回调用方不匹配");
            if (revealed && apiKey.isBlank()) throw new BizException(502, "推送密钥签发服务未返回密钥");
            return new IssuedCredential(issuedClientId, apiKey, revealed);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Haitong credential issue transport failed: errorClass={}", exception.getClass().getSimpleName());
            throw new BizException(502, "无法完成海通密钥签发连接，请检查服务地址、端口和平台网络（"
                    + exception.getClass().getSimpleName() + "）");
        }
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    public record IssuedCredential(String clientId, String apiKey, boolean revealed) { }
}
