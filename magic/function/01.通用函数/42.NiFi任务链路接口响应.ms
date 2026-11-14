{
  "properties": {},
  "id": "729cd64e9cfa4b52b6f6739437e72749",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "42.NiFi任务链路接口响应",
  "path": "/nifiAccessTaskChainHttp",
  "parameters": [
    {
      "name": "identifier",
      "description": "任务或流程标识，缺省查询参数时为空值",
      "required": false,
      "dataType": "String",
      "type": "java.lang.String"
    },
    {
      "name": "byPipeline",
      "description": "是否按流程标识读取",
      "required": true,
      "dataType": "Boolean",
      "type": "java.lang.Boolean"
    },
    {
      "name": "nativeView",
      "description": "是否按需核验 NiFi 原生入口",
      "required": true,
      "dataType": "Boolean",
      "type": "java.lang.Boolean"
    }
  ],
  "options": [],
  "description": "为三个 NiFi 任务链路 HTTP 入口验证行业租户并保留原始 JSON、缺参、错误和状态码契约。函数仅查询，不启动、部署或修改流程。"
}
================================
import '@/common/nifiAccessTaskChain' as loadChain
import com.linewell.dataelement.model.common.CommonResponse
import com.linewell.dataelement.model.common.StatusCodeEnum
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import com.linewell.dataelement.platform.web.error.GlobalExceptionHandler
import java.lang.Exception
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException
import tenantRuntime

// @/common/nifiAccessTaskChainHttp 为三个 HTTP 入口核验 identifier 和当前行业租户，按 byPipeline 或 nativeView 读取链路。
// 保留原画布所需的 JSON 与状态码。只读业务数据，原生入口按需读取 NiFi，不改变流程。

var reject = (status, message) => ResponseEntity.status(status).body({
    code: status.value(), success: false, msg: message, error: message
})

// 缺省查询参数继续使用已发布的业务码，显式传入空白则是 HTTP 400。
if (identifier == null && !byPipeline) {
    var missing = CommonResponse.fail(StatusCodeEnum.PARAMS_NOT_FOUND.getCode(), '缺少必要参数: accessTaskId', '参数缺失')
    return ResponseEntity.ok(missing)
}
if (identifier == null || identifier.isBlank()) {
    return reject(HttpStatus.BAD_REQUEST, '缺少任务或画布标识')
}

try {
    // 拒绝控制库与忽略租户上下文；共享查询仍从同一可信上下文按各资源过滤。
    tenantRuntime.id()
    if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
        return reject(HttpStatus.FORBIDDEN, '流程必须在当前租户数据库中读取')
    }
    return ResponseEntity.ok(loadChain(identifier.trim(), byPipeline, nativeView))
} catch (failure) {
    var cause = failure
    while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException)
        && cause.getCause() != null) {
        cause = cause.getCause()
    }
    if (cause instanceof ResponseStatusException) {
        var message = cause.getReason() == null ? '流程配置校验失败' : cause.getReason()
        return reject(cause.getStatusCode(), message)
    }
    if (cause instanceof TenantAccessException) {
        return ResponseEntity.ok(CommonResponse.fail(403, cause.getMessage(), cause.getCode()))
    }
    if (cause instanceof Exception) {
        // 沿用平台的异常诊断与脱敏，避免因 HTTP 入口迁移改变旧错误响应。
        return ResponseEntity.ok(new GlobalExceptionHandler().handleException(cause))
    }
    throw failure
}
