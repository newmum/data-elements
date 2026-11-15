{
  "properties": {},
  "id": "nifi_mapping_read_http_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "61.海通映射读取响应",
  "path": "/nifiMappingReadHttp",
  "description": "映射读取入口共用请求参数和错误响应。",
  "parameters": [
    {
      "name": "action",
      "description": "当前入口动作",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import '@/common/nifiPipelineReadHttp' as withHttp
import com.linewell.dataelement.mapping.core.MappingException
import com.linewell.dataelement.model.common.CommonResponse
import com.linewell.dataelement.model.common.StatusCodeEnum
import java.lang.IllegalArgumentException
import org.springframework.http.ResponseEntity
import org.springframework.util.NumberUtils

// @/common/nifiMappingReadHttp 为映射版本、模板和字典读取复用 MVC 参数和错误契约。
// 各入口自行决定必填参数、权限、查询及返回字段。

return withHttp((parameterValues, textParameter, rootCause) => {
    var text = (name, fallback) => {
        var input = textParameter(name)
        return input == null || (input == '' && fallback != null) ? fallback : input
    }
    var numeric = (name, fallback) => {
        var values = parameterValues(name)
        if (values == null || values.isEmpty() || (values.size() == 1 && values[0] == '')) {
            return fallback
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
    var missing = name => ResponseEntity.ok(CommonResponse.fail(StatusCodeEnum.PARAMS_NOT_FOUND.getCode(),
        '缺少必要参数: ' + name, '参数缺失'))
    var invalid = name => ResponseEntity.ok(CommonResponse.fail(StatusCodeEnum.PARAMS_UNVALID.getCode(),
        '参数[' + name + ']类型错误，期望类型: int', '参数类型不匹配'))
    try {
        return action(text, numeric, missing, invalid)
    } catch (failure) {
        var cause = rootCause(failure)
        if (cause instanceof MappingException) {
            return ResponseEntity.unprocessableEntity().body({code: cause.code(), error: cause.getMessage()})
        }
        throw failure
    }
})
