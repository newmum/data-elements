{
  "id": "portal_fn_response_v1",
  "groupId": "ga_local_accounts_functions_01",
  "name": "03.门户旧响应信封",
  "path": "/response",
  "parameters": [
    {
      "name": "action",
      "dataType": "Object",
      "required": false,
      "description": "当前门户入口的业务闭包"
    }
  ],
  "description": "门户选项、配置、会话、可用租户和切换共用原 Java HTTP 响应及异常格式。"
}
================================
import cn.hutool.extra.spring.SpringUtil
import com.linewell.dataelement.model.common.CommonResponse
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.http.ResponseEntity

// /tenant-account/response 统一原门户响应结构。入参为相应 API 的业务闭包。
var errors = SpringUtil.getBean('globalExceptionHandler')
try {
    var result = action()
    return ResponseEntity.ok(result instanceof CommonResponse ? result : CommonResponse.success(result))
} catch (error) {
    var cause = error
    while (!(cause instanceof TenantAccessException) && !(cause instanceof HttpMessageNotReadableException)
            && cause.getCause() != null && cause.getCause() != cause) {
        cause = cause.getCause()
    }
    if (cause instanceof TenantAccessException) {
        return ResponseEntity.ok(errors.handleTenantAccessException(cause))
    }
    return ResponseEntity.ok(errors.handleException(cause instanceof Exception ? cause : error))
}
