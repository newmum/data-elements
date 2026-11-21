{
  "properties": {},
  "id": "f10c3898f07848f0975a9dc1baa426ac",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "60.元数据目录查询响应",
  "path": "/metadataCatalogResponse",
  "description": "保留目录与字段浏览的 MVC JSON 信封。",
  "returnType": "Object",
  "parameters": [
    {
      "name": "code",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "value",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import com.linewell.dataelement.model.common.CommonResponse
import com.linewell.dataelement.platform.web.error.GlobalExceptionHandler
import java.lang.IllegalArgumentException
import java.lang.Throwable
import org.springframework.http.ResponseEntity

// 元数据目录和字段浏览共用已公开的 MVC 状态与响应信封，HEAD 由 HTTP 层抑制响应体。
if (code == 0) {
    return ResponseEntity.ok({code: 0, msg: 'success', data: value})
}
if (code == 500) {
    var advice = new GlobalExceptionHandler()
    var failure = value instanceof Throwable ? value : new IllegalArgumentException(value)
    return ResponseEntity.ok(advice.handleException(failure))
}
return ResponseEntity.ok(CommonResponse.fail(code, value, null))
