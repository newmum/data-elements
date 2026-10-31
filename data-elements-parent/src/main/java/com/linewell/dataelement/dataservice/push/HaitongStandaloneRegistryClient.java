package com.linewell.dataelement.dataservice.push;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.model.common.BizException;
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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HaitongStandaloneRegistryClient.class);

    public HaitongStandaloneRegistryClient(DataPushRegistryProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    public void synchronize(String datasourceId, Map<String, Object> datasourceSnapshot) {
        if (properties.getRelayBaseUrl().isBlank() || properties.getStandaloneSyncKey().isBlank()) {
            throw new BizException(503, "推送登记同步服务尚未配置，请检查平台 data-element.push-registry 的 relay-base-url 和 standalone-sync-key");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("deliveryType", "REGISTRY_SYNC");
            body.put("datasource", datasourceSnapshot);
            String url = properties.getRelayBaseUrl().replaceAll("/+$", "") + "/api/v1/data-push";
            var response = HaitongRelayHttp.post(url, Map.of(
                    "X-Standalone-Registry-Key", properties.getStandaloneSyncKey(),
                    "Idempotency-Key", "registry-sync-" + datasourceId + "-" + UUID.randomUUID()),
                    json.writeValueAsBytes(body), properties.getRelayTimeoutSeconds());
            if (response.status() / 100 != 2) {
                throw new BizException(502, "推送登记同步被海通拒绝（HTTP " + response.status()
                        + "），请检查海通同步开关及两端登记同步密钥");
            }
            Map<String, Object> payload = json.readValue(response.body(), new TypeReference<LinkedHashMap<String, Object>>() { });
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
            log.warn("Haitong registration sync transport failed: errorClass={}", exception.getClass().getSimpleName());
            throw new BizException(502, "无法完成海通登记同步连接，请检查服务地址、端口和平台网络（"
                    + exception.getClass().getSimpleName() + "）");
        }
    }
}
