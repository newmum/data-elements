package com.linewell.dataelement.dataservice.push;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Public publish action plus a separately protected internal Relay validation endpoint. */
@RestController
@RequestMapping("/dws/push")
public class DataPushRegistryController {
    private final DataPushSchemaService schemas;
    private final RelayClientRegistry clients;
    private final DataPushRegistryProperties properties;
    private final HaitongPushCredentialClient credentials;
    private final HaitongStandaloneRegistryClient standaloneRegistry;

    public DataPushRegistryController(DataPushSchemaService schemas, RelayClientRegistry clients, DataPushRegistryProperties properties, HaitongPushCredentialClient credentials, HaitongStandaloneRegistryClient standaloneRegistry) {
        this.schemas = schemas; this.clients = clients; this.properties = properties; this.credentials = credentials; this.standaloneRegistry = standaloneRegistry;
    }

    @PostMapping("/schema/publish")
    public Map<String, Object> publish(@RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.requireTenantId();
        String datasourceId = required(body.get("datasourceId"), "datasourceId");
        List<Map<String, Object>> published = schemas.publish(tenantId, datasourceId);
        return ok(Map.of("datasourceId", datasourceId, "schemas", published));
    }

    /** Authenticated registration-page view. This deliberately returns no relay credentials or destination connection details. */
    @GetMapping("/schema/contracts")
    public Map<String, Object> contract(@RequestParam String datasourceId) {
        return ok(schemas.contract(TenantContext.requireTenantId(), required(datasourceId, "datasourceId")));
    }

    /**
     * Issues one data-source-level credential.  The plaintext key is delivered
     * only to the authenticated registration view on the first issue; the
     * platform stores just the caller-to-source authorization relationship.
     */
    @PostMapping("/credential/issue")
    public Map<String, Object> issueCredential(@RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.requireTenantId();
        String datasourceId = required(body.get("datasourceId"), "datasourceId");
        schemas.requirePushDatasource(tenantId, datasourceId);
        String clientId = "push-" + datasourceId;
        standaloneRegistry.synchronize(datasourceId, schemas.standaloneSnapshot(tenantId, datasourceId, clientId));
        HaitongPushCredentialClient.IssuedCredential issued = credentials.issue(clientId, false);
        clients.bindDatasource(clientId, tenantId, datasourceId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clientId", issued.clientId());
        result.put("datasourceId", datasourceId);
        result.put("revealed", issued.revealed());
        if (issued.revealed()) result.put("apiKey", issued.apiKey());
        return ok(result);
    }

    /** Explicit key rotation; the former plaintext key becomes invalid immediately. */
    @PostMapping("/credential/rotate")
    public Map<String, Object> rotateCredential(@RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.requireTenantId();
        String datasourceId = required(body.get("datasourceId"), "datasourceId");
        schemas.requirePushDatasource(tenantId, datasourceId);
        String clientId = "push-" + datasourceId;
        standaloneRegistry.synchronize(datasourceId, schemas.standaloneSnapshot(tenantId, datasourceId, clientId));
        HaitongPushCredentialClient.IssuedCredential issued = credentials.issue(clientId, true);
        clients.bindDatasource(clientId, tenantId, datasourceId);
        return ok(Map.of("clientId", issued.clientId(), "datasourceId", datasourceId, "revealed", true, "apiKey", issued.apiKey()));
    }

    @PostMapping("/internal/validate")
    public Map<String, Object> validate(
            @RequestHeader(value = "X-Relay-Registry-Key", required = false) String key,
            @RequestHeader(value = "X-Relay-Client-Id", required = false) String clientId,
            @RequestBody Map<String, Object> request) {
        requireKey(key);
        Map<String, Object> batch = map(request.get("batch"), "batch");
        String datasourceId = required(batch.get("datasourceId"), "datasourceId");
        String caller = required(clientId, "X-Relay-Client-Id");
        RelayClientRegistry.ClientScope scope;
        try {
            scope = clients.resolve(caller, datasourceId);
            Map<String, Object> answer = schemas.validate(scope.tenantId(), caller, required(request.get("channel"), "channel"), batch);
            return ok(answer);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    /** The relay queues a delivery, while this platform endpoint resolves and writes to the current tenant's FJGHCC binding. */
    @PostMapping("/internal/ftp-deliver")
    public Map<String, Object> deliverFtp(
            @RequestHeader(value = "X-Relay-Registry-Key", required = false) String key,
            @RequestHeader(value = "X-Relay-Client-Id", required = false) String clientId,
            @RequestBody Map<String, Object> request) {
        requireKey(key);
        Map<String, Object> batch = map(request.get("batch"), "batch");
        String datasourceId = required(batch.get("datasourceId"), "datasourceId");
        String caller = required(clientId, "X-Relay-Client-Id");
        try {
            RelayClientRegistry.ClientScope scope = clients.resolve(caller, datasourceId);
            return ok(schemas.deliverFtp(scope.tenantId(), caller, batch, required(request.get("deliveryId"), "deliveryId")));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    private void requireKey(String value) {
        if (properties.getSharedKey().isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "推送登记校验服务尚未配置");
        if (value == null || !MessageDigest.isEqual(
                properties.getSharedKey().getBytes(StandardCharsets.UTF_8),
                value.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "服务间校验密钥无效");
        }
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " 必须是对象");
        return new LinkedHashMap<>((Map<String, Object>) map);
    }
    private String required(Object value, String field) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (!text.matches("[A-Za-z0-9_.-]{1,255}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " 格式不正确");
        return text;
    }
    private Map<String, Object> ok(Object data) { return Map.of("code", 0, "message", "success", "data", data); }
}
