package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Same-origin proxy for the native NiFi application. Authentication is supplied
 * by {@link NifiClient}, so NiFi credentials never reach the browser.
 */
@RestController
public class NifiNativeUiProxyController {

    private final NifiClient nifi;

    public NifiNativeUiProxyController(NifiClient nifi) {
        this.nifi = nifi;
    }

    @RequestMapping(
            value = {"/nifi-ui", "/nifi-ui/", "/nifi-ui/**", "/nifi-api/**"},
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.OPTIONS}
    )
    public ResponseEntity<?> proxy(HttpServletRequest request,
                                   @RequestBody(required = false) byte[] body) {
        String path = request.getRequestURI();
        if (path.equals("/nifi-ui") || path.startsWith("/nifi-ui/")) {
            path = "/nifi" + path.substring("/nifi-ui".length());
        }
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            path += "?" + request.getQueryString();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            request.getHeaderNames().asIterator().forEachRemaining(name ->
                    request.getHeaders(name).asIterator().forEachRemaining(value -> headers.add(name, value)));
            return nifi.proxy(HttpMethod.valueOf(request.getMethod()), path, headers, body);
        } catch (NifiException e) {
            int status = e.status() > 0 ? e.status() : 502;
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", e.getMessage(), "status", status));
        }
    }
}
