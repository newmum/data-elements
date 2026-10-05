package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transparent passthrough: /nifi/api/nifi/** -> NiFi /nifi-api/**.
 * Adds auth header, lets the frontend talk to NiFi without dealing with CORS or token directly.
 */
@RestController
@RequestMapping("/nifi/api/nifi")
@Api(tags = "NiFi 代理转发")
public class NifiProxyController {

    private final NifiClient nifi;

    public NifiProxyController(NifiClient nifi) {
        this.nifi = nifi;
    }

    @RequestMapping(
            value = "/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.DELETE, RequestMethod.PATCH}
    )
    @Operation(summary = "NiFi代理转发(proxy)")
    public ResponseEntity<Object> proxy(HttpServletRequest req,
                                        @RequestBody(required = false) String body) {
        String prefix = "/nifi/api/nifi";
        String path = req.getRequestURI().substring(prefix.length());
        if (path.isEmpty()) path = "/";
        if (req.getQueryString() != null) {
            path = path + "?" + req.getQueryString();
        }

        try {
            Object data = switch (req.getMethod()) {
                case "GET"    -> nifi.get(path, Object.class);
                case "POST"   -> nifi.post(path, parseJson(body), Object.class);
                case "PUT"    -> nifi.put(path, parseJson(body), Object.class);
                case "PATCH"  -> nifi.put(path, parseJson(body), Object.class); // NiFi rarely uses PATCH
                case "DELETE" -> {
                    nifi.delete(path);
                    yield null;
                }
                default -> throw new UnsupportedOperationException(req.getMethod());
            };
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(data);
        } catch (NifiException e) {
            int status = e.status() > 0 ? e.status() : 502;
            java.util.Map<String, Object> err = new java.util.HashMap<>();
            err.put("error", e.getMessage());
            err.put("status", status);
            err.put("body", e.body());
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(err);
        }
    }

    private static Object parseJson(String body) {
        // Sending the raw String causes RestClient to wrap it as a JSON string. We let the
        // body be relayed as-is by deserialising via Jackson into a generic Object.
        if (body == null || body.isBlank()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(body, Object.class);
        } catch (Exception e) {
            // Not valid JSON, send as text.
            return body;
        }
    }
}
