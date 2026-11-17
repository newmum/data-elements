package com.linewell.dataelement.integration.transfer.haitong;

import com.linewell.dataelement.config.DataPushRegistryProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 共享平台接收海通推送回调时验证既有服务间凭据。 */
public final class RelayRequestAuthentication {

    public static final String KEY_HEADER = "X-Relay-Registry-Key";

    private RelayRequestAuthentication() { }

    public static void requireKey(DataPushRegistryProperties properties, String provided) {
        String expected = properties.getSharedKey();
        if (expected.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "推送登记校验服务尚未配置");
        }
        if (provided == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "服务间校验密钥无效");
        }
    }
}
