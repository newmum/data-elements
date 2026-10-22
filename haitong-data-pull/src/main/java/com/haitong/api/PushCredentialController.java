package com.haitong.api;

import com.haitong.security.PushCredentialStore;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal-only credential issuer. Authentication is enforced by
 * {@link AuthenticationFilter} with the platform/registry shared key.
 */
@RestController
@RequestMapping("/internal/push-credentials")
public class PushCredentialController {
    private final PushCredentialStore credentials;

    public PushCredentialController(PushCredentialStore credentials) { this.credentials = credentials; }

    @PostMapping("/issue")
    public Map<String, Object> issue(@RequestBody Map<String, Object> body) {
        String clientId = text(body.get("clientId"));
        boolean rotate = Boolean.TRUE.equals(body.get("rotate"));
        PushCredentialStore.IssuedCredential issued = credentials.issue(clientId, rotate);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("clientId", issued.clientId());
        data.put("revealed", issued.revealed());
        if (issued.revealed()) data.put("apiKey", issued.apiKey());
        return Map.of("code", 0, "message", "success", "data", data);
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
