{
  "id": "log_tenant_legacy_response_v1",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "92.行业日志原响应",
  "path": "/logresponse",
  "parameters": [
    {"name": "action", "required": true, "dataType": "Object", "type": "java.lang.Object"}
  ],
  "options": [],
  "description": "行业日志入口共用原 HTTP 成功、校验失败和权限拒绝响应。"
}
================================
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.web.error.GlobalExceptionHandler
import java.lang.Exception
import java.lang.SecurityException
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException

// /common/logresponse 调用已进入行业会话的业务操作，保留原 MVC JSON 响应字段和业务错误。
var response = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
try {
    return response.body({code: 0, success: true, data: action()})
} catch (failure) {
    var cause = failure
    while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException)
        && cause.getCause() != null && cause.getCause() != cause) {
        cause = cause.getCause()
    }
    var handler = new GlobalExceptionHandler()
    if (cause instanceof TenantAccessException) {
        return response.body(handler.handleTenantAccessException(cause))
    }
    if (cause instanceof SecurityException) {
        return response.body(handler.handleSecurityException(cause))
    }
    if (cause instanceof Exception) {
        return response.body(handler.handleException(cause))
    }
    throw failure
}
