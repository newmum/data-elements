{
  "properties": {},
  "id": "44856f9262b98c0fb011f77d4c78abf1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "45.NiFi画布写入请求",
  "path": "/nifiCanvasWriteRequest",
  "description": "复用创建和保存的 HTTP 参数绑定、JSON 反序列化及异常契约。",
  "parameters": [
    {
      "name": "bodySignature",
      "description": "原入口空请求体的兼容诊断签名",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "input",
      "description": "已解析 JSON",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "action",
      "description": "接收已绑定画布和请求参数读取器的业务实现",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import '@/common/nifiPipelineListResponse' as respond
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper as mapper
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import java.lang.StringBuilder
import org.springframework.http.converter.HttpMessageNotReadableException
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException
import request
import tenantRuntime

// @/common/nifiCanvasWriteRequest 为创建和保存绑定旧 HTTP 参数及画布 JSON。
// 返回与原端点一致的业务错误信封，业务回调在可信租户上下文执行。
var parameterValues = name => {
    var values = request.getValues(name)
    return values == null ? request.getValues(name + '[]') : values
}
var textParameter = name => {
    var values = parameterValues(name)
    if (values == null) {
        return null
    }
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
    while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException) && cause.getCause() != null) {
        cause = cause.getCause()
    }
    return cause
}

return respond(() => {
    if (input == null) {
        // 保留旧端点对空请求体的诊断文本，客户端仍收到原有的业务码和错误信封。
        throw new HttpMessageNotReadableException('Required request body is missing: public ' + bodySignature)
    }
    var submitted = null
    try {
        submitted = input instanceof Pipeline ? input : mapper.readValue(mapper.writeValueAsString(input), Pipeline)
    } catch (failure) {
        if (rootCause(failure) instanceof JsonProcessingException) {
            throw new HttpMessageNotReadableException('JSON parse error: ' + rootCause(failure).getOriginalMessage())
        }
        throw failure
    }
    // 入参不能指定租户，只有行业业务库中的合法身份可执行写入。
    var tenantId = tenantRuntime.id()
    if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
        throw new TenantAccessException('TENANT-DATABASE-REQUIRED', '流程数据必须在当前租户数据库中访问')
    }
    return action(submitted, tenantId, parameterValues, textParameter, rootCause)
})
