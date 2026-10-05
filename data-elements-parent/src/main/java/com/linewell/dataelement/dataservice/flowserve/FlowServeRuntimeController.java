package com.linewell.dataelement.dataservice.flowserve;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 远程云梯运行节点部署协议；业务管理接口全部由 Magic 06.数据服务承载。 */
@RestController
@RequestMapping("/api/v1/runtime")
public class FlowServeRuntimeController {
    private final FlowServeRuntimeAdapter runtime;
    private final String deployToken;

    public FlowServeRuntimeController(
            FlowServeRuntimeAdapter runtime,
            @Value("${flowserve.runtime.deploy-token:}") String deployToken) {
        this.runtime = runtime;
        this.deployToken = deployToken == null ? "" : deployToken;
    }

    @PostMapping("/deploy")
    public Map<String, Object> deploy(
            @RequestHeader(value = "X-FlowServe-Deploy-Token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        requireToken(token);
        runtime.deployFromRequest(request);
        return ok(Map.of(
                "resourceId", request.get("resourceId"),
                "path", request.get("path")));
    }

    @DeleteMapping("/deploy/{resourceId}")
    public Map<String, Object> undeploy(
            @RequestHeader(value = "X-FlowServe-Deploy-Token", required = false) String token,
            @PathVariable String resourceId) {
        requireToken(token);
        runtime.undeployFromRequest(resourceId);
        return ok(Map.of("resourceId", resourceId, "status", "deleted"));
    }

    @GetMapping("/health")
    public Map<String, Object> health(
            @RequestHeader(value = "X-FlowServe-Deploy-Token", required = false) String token) {
        requireToken(token);
        return ok(Map.of("ok", true, "runtime", "data-elements", "ts", System.currentTimeMillis()));
    }

    private Map<String, Object> ok(Object data) {
        return Map.of("code", 0, "message", "success", "data", data);
    }

    private void requireToken(String token) {
        if (!deployToken.isBlank() && !deployToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "部署令牌无效");
        }
    }
}
