{
  "properties": {},
  "id": "nifi_pipeline_read_http_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "29.海通流程只读接口响应",
  "path": "/nifiPipelineReadHttp",
  "description": "为 NiFi 流程分页和运行总览提供旧 HTTP 参数读取及原始 JSON 和错误响应。调用方 action 负责本接口的权限与查询。",
  "parameters": [
    {
      "name": "action",
      "description": "接收原始参数值、文本参数及异常解包函数，执行当前接口的只读业务",
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
import java.lang.StringBuilder
import org.springframework.http.ResponseEntity
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException
import request

// @/common/nifiPipelineReadHttp 为流程分页和运行总览复用旧 HTTP 参数绑定及错误处理。
// action 接收参数读取函数和异常解包函数，返回当前接口的 ResponseEntity。

// Spring 先取原参数名，再尝试带方括号的同名参数。
var parameterValues = name => {
    var values = request.getValues(name)
    return values == null ? request.getValues(name + '[]') : values
}
var textParameter = name => {
    var values = parameterValues(name)
    if (values == null) {
        return null
    }
    // Spring 将重复文本参数以逗号合并，原样保留空元素和空白。
    var joined = new StringBuilder()
    var separator = ''
    for (value in values) {
        joined.append(separator)
        joined.append(value)
        separator = ','
    }
    return joined.toString()
}
var rootCause = failure => {
    var cause = failure
    while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException)
        && cause.getCause() != null) {
        cause = cause.getCause()
    }
    return cause
}

try {
    return action(parameterValues, textParameter, rootCause)
} catch (failure) {
    // 恢复原 MVC 业务错误和诊断结构，保留缺少租户时的 403 业务码。
    var cause = rootCause(failure)
    var errors = new GlobalExceptionHandler()
    if (cause instanceof TenantAccessException) {
        return ResponseEntity.ok(errors.handleTenantAccessException(cause))
    }
    if (cause instanceof Exception) {
        return ResponseEntity.ok(errors.handleException(cause))
    }
    throw failure
}
