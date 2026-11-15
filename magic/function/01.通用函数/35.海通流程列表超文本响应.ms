{
  "properties": {},
  "id": "nifi_pipe_list_response_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "35.海通流程列表超文本响应",
  "path": "/nifiPipelineListResponse",
  "description": "为普通列表、运行列表和模板保留原始 JSON、显式 HTTP 状态及应用异常契约。",
  "parameters": [
    {
      "name": "action",
      "description": "调用方的业务参数",
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

// @/common/nifiPipelineListResponse 共享列表及模板的原始 JSON 和旧 MVC 错误响应。
// action 在已建立的请求及租户上下文执行，查询和返回字段由业务函数定义。
try {
    var result = action()
    return result instanceof ResponseEntity ? result : ResponseEntity.ok(result)
} catch (failure) {
    var cause = failure
    while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException)
        && cause.getCause() != null) {
        cause = cause.getCause()
    }
    if (cause instanceof ResponseStatusException) {
        var message = cause.getReason() == null ? '流程配置校验失败' : cause.getReason()
        var status = cause.getStatusCode()
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
