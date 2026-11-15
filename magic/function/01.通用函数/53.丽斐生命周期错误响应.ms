{
  "properties": {},
  "id": "nifi_lifecycle_response_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "53.丽斐生命周期错误响应",
  "path": "/nifiLifecycleResponse",
  "description": "为部署、候选切换、启动、停止、卸载和属性同步保留原有 HTTP 错误信封。",
  "parameters": [
    {
      "name": "action",
      "description": "执行当前入口的生命周期操作，接收异常解包函数",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.web.error.GlobalExceptionHandler
import java.lang.Exception
import java.lang.SecurityException
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException

// @/common/nifiLifecycleResponse 为多个生命周期 HTTP 入口复用状态码和错误信封。
// 入口自身决定请求校验、租户权限、锁和事务位置。

var rootCause = failure => {
    var cause = failure
    while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException) && cause.getCause() != null) {
        cause = cause.getCause()
    }
    return cause
}
try {
    return action(rootCause)
} catch (failure) {
    var cause = rootCause(failure)
    if (cause instanceof ResponseStatusException) {
        var status = cause.getStatusCode()
        var message = cause.getReason() == null ? '流程配置校验失败' : cause.getReason()
        return ResponseEntity.status(status).body({code: status.value(), success: false, msg: message, error: message})
    }
    var handler = new GlobalExceptionHandler()
    if (cause instanceof TenantAccessException) {
        return ResponseEntity.ok(handler.handleTenantAccessException(cause))
    }
    if (cause instanceof SecurityException) {
        return ResponseEntity.ok(handler.handleSecurityException(cause))
    }
    if (cause instanceof Exception) {
        return ResponseEntity.ok(handler.handleException(cause))
    }
    throw failure
}
