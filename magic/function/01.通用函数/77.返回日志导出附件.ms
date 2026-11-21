{
  "id": "log_export_attachment_v1",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "77.返回日志导出附件",
  "path": "/logfile",
  "parameters": [
    {"name": "id", "dataType": "Object"},
    {"name": "resolve", "dataType": "Object", "required": true}
  ],
  "description": "行业和平台导出共用附件传输及原 MVC 错误响应。调用方负责身份域及对象授权。"
}
================================
import com.linewell.dataelement.model.common.CommonResponse
import com.linewell.dataelement.model.common.StatusCodeEnum
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.web.error.GlobalExceptionHandler
import java.lang.Exception
import java.lang.SecurityException
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException

// 两个下载入口提供各自的文件授权。返回已验证的文件资源，由 HTTP 消息转换器流式处理附件。
var json = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
if (id == null) {
    return json.body(CommonResponse.fail(StatusCodeEnum.PARAMS_NOT_FOUND.getCode(), '缺少必要参数: id', '参数缺失'))
}
try {
    var path = resolve(id)
    var resource = new FileSystemResource(path)
    var name = resource.getFilename()
    var type = name.endsWith('.csv') ? 'text/csv;charset=UTF-8' : 'application/x-ndjson;charset=UTF-8'
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(type))
        .header('Content-Disposition', 'attachment; filename="' + name + '"')
        .header('Cache-Control', 'no-store').body(resource)
} catch (failure) {
    var cause = failure
    while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException)
        && cause.getCause() != null && cause.getCause() != cause) {
        cause = cause.getCause()
    }
    var handler = new GlobalExceptionHandler()
    if (cause instanceof TenantAccessException) { return json.body(handler.handleTenantAccessException(cause)) }
    if (cause instanceof SecurityException) { return json.body(handler.handleSecurityException(cause)) }
    if (cause instanceof Exception) { return json.body(handler.handleException(cause)) }
    throw failure
}
