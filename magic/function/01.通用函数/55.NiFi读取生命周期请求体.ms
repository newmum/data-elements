{
  "properties": {},
  "id": "nifi_lifecycle_body_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "55.NiFi读取生命周期请求体",
  "path": "/nifiLifecycleBody",
  "description": "为运行控制和映射入口读取 JSON，保留类型校验、数值精度和错误诊断。",
  "parameters": [
    {"name":"input","description":"Magic 已解析的 HTTP 请求体","required":false,"dataType":"Object","type":"java.lang.Object"},
    {"name":"type","description":"入口使用的参数类型","required":true,"dataType":"Object","type":"java.lang.Object"},
    {"name":"oldType","description":"既有错误中的参数类型名","required":true,"dataType":"String","type":"java.lang.String"},
    {"name":"requiredSignature","description":"必传请求体的既有错误方法签名","required":false,"dataType":"String","type":"java.lang.String"}
  ],
  "options": []
}
================================
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper as mapper
import com.linewell.dataelement.platform.audit.AuditIdentityResolver
import com.linewell.dataelement.platform.configuration.MagicBootConfiguration
import java.lang.Object
import java.lang.String
import java.util.Map
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.WebUtils
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException

// @/common/nifiLifecycleBody 为部署、候选切换、属性同步和映射模板导入还原 MVC 的 JSON 参数校验。
// 映射文档使用精确的 JSON 树，运行控制优先读取原始请求以保留旧诊断。

var servlet = AuditIdentityResolver.request()
var cached = servlet == null ? null : WebUtils.getNativeRequest(servlet, ContentCachingRequestWrapper)
var bytes = cached?.getContentAsByteArray()
var value = input
try {
    var error = servlet?.getAttribute(MagicBootConfiguration.MAGIC_MAPPING_JSON_ERROR)
    if (error != null) {
        var cause = error.getCause()
        if (!(cause instanceof MismatchedInputException) || !cause.getOriginalMessage().startsWith('No content to map')) {
            throw cause == null ? error : cause
        }
        // MVC 将空字节流视为缺少请求体。
        value = null
    } else if (bytes != null && bytes.length > 0 && bytes.length < 65536) {
        value = mapper.readValue(bytes, type)
    } else if (type == JsonNode && input instanceof JsonNode) {
        value = input
    } else if (input != null) {
        var reader = type == Map ? mapper.readerFor(mapper.getTypeFactory().constructMapType(Map, String, Object))
            : mapper.readerFor(type)
        if (type == JsonNode || input instanceof JsonNode) {
            reader = reader.with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        }
        value = reader.readValue(mapper.writeValueAsString(input))
    }
} catch (failure) {
    var cause = failure
    while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException) && cause.getCause() != null) {
        cause = cause.getCause()
    }
    if (cause instanceof JsonProcessingException) {
        throw new HttpMessageNotReadableException('JSON parse error: ' + cause.getOriginalMessage().replace(type.getName(), oldType))
    }
    throw failure
}
if (value == null && requiredSignature != null) {
    throw new HttpMessageNotReadableException('Required request body is missing: public ' + requiredSignature)
}
return value
