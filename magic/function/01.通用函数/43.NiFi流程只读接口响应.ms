{
  "properties": {},
  "id": "nifi_pipeline_read_http_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "43.NiFi流程只读接口响应",
  "path": "/nifiPipelineReadHttp",
  "description": "为 NiFi 流程分页和运行总览的 GET 与 HEAD 入口读取查询参数，沿用原有转换、行业租户校验、原始 JSON 和错误格式。pageRequested 为真时按页读取，为假时统计并返回前五条流程。只读当前租户库，无 NiFi 调用。",
  "parameters": [
    {"name": "pageRequested", "description": "是否执行流程分页，否时读取运行总览", "required": true, "dataType": "Boolean", "type": "java.lang.Boolean"}
  ],
  "options": []
}
================================
import '@/common/nifiPipelinePage' as loadPage
import '@/common/nifiRuntimeOverview' as loadOverview
import com.linewell.dataelement.model.common.CommonResponse
import com.linewell.dataelement.model.common.StatusCodeEnum
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import com.linewell.dataelement.platform.web.error.GlobalExceptionHandler
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.lang.StringBuilder
import org.springframework.http.ResponseEntity
import org.springframework.util.NumberUtils
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException
import request
import tenantRuntime

// @/common/nifiPipelineReadHttp 处理流程分页和运行总览的 GET 与 HEAD 请求。
// pageRequested 选择分页或总览，从 HTTP 请求读取相应参数，返回原始 JSON 或现有业务错误。
// 数据查询由共享函数按可信行业租户执行，无写入或 NiFi 调用。

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

var integerParameter = (name, defaultValue) => {
    var values = parameterValues(name)
    // 单个空值使用默认值。重复参数沿用 Spring 数组到整数的首值转换。
    if (values == null || values.isEmpty() || (values.size() == 1 && values[0] == '')) {
        return defaultValue
    }
    try {
        return NumberUtils.parseNumber(values[0], Integer)
    } catch (failure) {
        if (!(rootCause(failure) instanceof IllegalArgumentException)) {
            throw failure
        }
        return null
    }
}
var invalidInteger = name => ResponseEntity.ok(CommonResponse.fail(StatusCodeEnum.PARAMS_UNVALID.getCode(),
    '参数[' + name + ']类型错误，期望类型: int', '参数类型不匹配'))

try {
    if (pageRequested) {
        // 按旧入口的绑定顺序先检查数字字段，再验证租户并执行分页。
        var page = integerParameter('page', 1)
        if (page == null) {
            return invalidInteger('page')
        }
        var size = integerParameter('size', 20)
        if (size == null) {
            return invalidInteger('size')
        }
        if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
            tenantRuntime.id()
            throw new IllegalStateException('流程查询必须绑定行业租户')
        }
        return ResponseEntity.ok(loadPage(page, size, textParameter('sortField'), textParameter('sortDir'),
            textParameter('q')))
    }
    return ResponseEntity.ok(loadOverview(textParameter('sortField'), textParameter('sortDir')))
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
