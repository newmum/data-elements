package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiNativeView;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException;
import com.linewell.dataelement.platform.web.security.ProtocolAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Same-origin proxy for the native NiFi application. Authentication is supplied
 * by the session-bound native view, so NiFi credentials never reach the browser.
 */
@RestController
@ProtocolAccess(bindTenant = false)
@PropertySource("classpath:nifi-native-proxy.properties")
public class NifiNativeUiProxyController {

    private final NifiNativeView nativeView;
    private final String uiPath;
    private final String apiPath;
    private final String upstreamUiPath;
    private final String upstreamApiPath;

    public NifiNativeUiProxyController(NifiNativeView nativeView,
                                      @Value("${nifi.native-proxy.ui-path}") String uiPath,
                                      @Value("${nifi.native-proxy.api-path}") String apiPath,
                                      @Value("${nifi.native-proxy.upstream-ui-path}") String upstreamUiPath,
                                      @Value("${nifi.native-proxy.upstream-api-path}") String upstreamApiPath) {
        this.nativeView = nativeView;
        this.uiPath = protocolPath(uiPath);
        this.apiPath = protocolPath(apiPath);
        this.upstreamUiPath = protocolPath(upstreamUiPath);
        this.upstreamApiPath = protocolPath(upstreamApiPath);
        if (uiPath.equals(apiPath) || uiPath.startsWith(apiPath + "/") || apiPath.startsWith(uiPath + "/")) {
            throw new IllegalArgumentException("NiFi 原生界面与 API 路径不能重叠");
        }
    }

    private static String protocolPath(String value) {
        if (!value.matches("/[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*")) {
            throw new IllegalArgumentException("NiFi 原生协议路径必须使用具体的绝对路径");
        }
        return value;
    }

    @RequestMapping(
            value = {"${nifi.native-proxy.ui-path}", "${nifi.native-proxy.ui-path}/",
                    "${nifi.native-proxy.ui-path}/**", "${nifi.native-proxy.api-path}/**"},
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.OPTIONS}
    )
    public ResponseEntity<?> proxy(HttpServletRequest request,
                                   @RequestBody(required = false) byte[] body) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals(uiPath) || path.startsWith(uiPath + "/")) {
            path = upstreamUiPath + path.substring(uiPath.length());
        } else if (path.equals(apiPath) || path.startsWith(apiPath + "/")) {
            path = upstreamApiPath + path.substring(apiPath.length());
        }
        try {
            return nativeView.proxy(request, path, path.equals(upstreamUiPath) || path.startsWith(upstreamUiPath + "/"));
        } catch (org.springframework.web.server.ResponseStatusException rejected) {
            return ResponseEntity.status(rejected.getStatusCode()).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", rejected.getReason() == null ? "原生详情访问被拒绝" : rejected.getReason(),
                            "status", rejected.getStatusCode().value()));
        } catch (NifiException e) {
            int status = e.status() > 0 ? e.status() : 502;
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", e.getMessage(), "status", status));
        }
    }
}
