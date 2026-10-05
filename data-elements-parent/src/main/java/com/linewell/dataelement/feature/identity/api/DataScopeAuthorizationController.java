package com.linewell.dataelement.feature.identity.api;

import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.model.common.CommonResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Low-code authorization-page endpoints for configurable resource data scopes. */
@RestController
@RequestMapping("/sym/auth")
public class DataScopeAuthorizationController {

    private final DataScopeAuthorizationService dataScopeAuthorizationService;

    public DataScopeAuthorizationController(DataScopeAuthorizationService dataScopeAuthorizationService) {
        this.dataScopeAuthorizationService = dataScopeAuthorizationService;
    }

    @PostMapping("/getRoleDataScope")
    public CommonResponse<?> getRoleDataScope(@RequestBody Map<String, Object> body) {
        return CommonResponse.success(dataScopeAuthorizationService.roleScope(
                text(body, "roleId"),
                text(body, "resourceCode")
        ));
    }

    @PostMapping("/saveRoleDataScope")
    public CommonResponse<?> saveRoleDataScope(@RequestBody Map<String, Object> body) {
        return CommonResponse.success(dataScopeAuthorizationService.saveRoleScope(
                text(body, "roleId"),
                text(body, "resourceCode"),
                text(body, "scope")
        ));
    }

    @PostMapping("/currentDataScope")
    public CommonResponse<?> currentDataScope(@RequestBody(required = false) Map<String, Object> body) {
        return CommonResponse.success(dataScopeAuthorizationService
                .currentScope(text(body, "resourceCode")).asMap());
    }

    private String text(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            return "";
        }
        return String.valueOf(body.get(key));
    }
}
