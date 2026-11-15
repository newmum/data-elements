{
  "properties": {},
  "id": "nifi_pipeline_start_post_v1",
  "script": null,
  "groupId": "56eecf4a0fcb5b6cac8df60e39962bb0",
  "name": "03.启动流程",
  "path": "/start",
  "method": "POST",
  "description": "启用已有 NiFi 流程。显式强制重建时重新验证清空策略和部署配置。",
  "parameters": [
    {
      "name": "id",
      "required": true,
      "dataType": "String",
      "description": "流程或映射资源标识"
    }
  ],
  "options": [
    {
      "name": "wrap_request_parameter",
      "value": "param"
    }
  ],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiStartCanvas' as startCanvas
import com.linewell.dataelement.model.common.CommonResponse
import com.linewell.dataelement.model.common.StatusCodeEnum
import java.util.Locale
import org.springframework.http.ResponseEntity
import request

// POST /nifi/life/start 启动流程。 查询参数 id。
// POST /nifi/life/start 先绑定旧布尔参数，再与登记表共用启动实现。
// @/common/nifiStartCanvas 核对可信租户、部署状态和清空确认，在共享 NiFi 锁内启动。

var booleanParameter = name => {
    var values = request.getValues(name)
    if (values == null) {
        values = request.getValues(name + '[]')
    }
    if (values == null || values.isEmpty() || (values.size() == 1 && values[0] == '')) {
        return false
    }
    var value = values[0].trim().toLowerCase(Locale.ROOT)
    if (['true', 'on', 'yes', '1'].contains(value)) {
        return true
    }
    if (['false', 'off', 'no', '0'].contains(value)) {
        return false
    }
    return null
}
var invalid = name => ResponseEntity.ok(CommonResponse.fail(StatusCodeEnum.PARAMS_UNVALID.getCode(),
    '参数[' + name + ']类型错误，期望类型: boolean', '参数类型不匹配'))
var force = booleanParameter('force')
if (force == null) {
    return invalid('force')
}
var confirmCleanup = booleanParameter('confirmCleanup')
if (confirmCleanup == null) {
    return invalid('confirmCleanup')
}
return startCanvas(param.id, force, confirmCleanup)
