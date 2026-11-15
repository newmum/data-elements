{
  "properties": {},
  "id": "c24248f72ceb4fdebc665422701e7c34",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "77.元数据目录请求参数",
  "path": "/metadataCatalogRequest",
  "description": "保留目录 GET 与 HEAD 的参数默认值和异常契约。",
  "returnType": "Object",
  "parameters": [
    {
      "name": "required",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "defaults",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import com.linewell.dataelement.platform.audit.AuditIdentityResolver
import org.springframework.util.NumberUtils
import org.springframework.util.StringUtils
import java.lang.Integer

// 元数据目录各 GET 和 HEAD 复用原 MVC 的必传参数、整数默认值及重复查询参数表示。
var servlet = AuditIdentityResolver.request()
var firstOrJoined = name => {
    var values = servlet.getParameterValues(name)
    if (values == null) {
        values = servlet.getParameterValues(name + '[]')
    }
    if (values == null) {
        return null
    }
    var text = ''
    var delimiter = ''
    for (value in values) {
        text += delimiter + value
        delimiter = ','
    }
    return text
}
var identity = required == null ? null : firstOrJoined(required)
if (required != null && identity == null) {
    return {code: 20001, msg: '缺少必要参数: ' + required}
}
var numbers = {}
for (name, fallback in defaults) {
    var raw = firstOrJoined(name)
    try {
        numbers.put(name, raw == null || raw == '' ? fallback : NumberUtils.parseNumber(raw, Integer))
    } catch (ignored) {
        return {code: 20002, msg: '参数[' + name + ']类型错误，期望类型: int'}
    }
}
var sourceIds = servlet.getParameterValues('sourceIds')
if (sourceIds == null) {
    sourceIds = servlet.getParameterValues('sourceIds[]')
}
var sources = null
if (sourceIds != null) {
    sources = []
    if (sourceIds.length == 1) {
        for (source in StringUtils.commaDelimitedListToStringArray(sourceIds[0])) {
            sources.add(source.trim())
        }
    } else {
        for (source in sourceIds) {
            sources.add(source)
        }
    }
}
return {
    code: 0, identity: identity, numbers: numbers, sourceIds: sources,
    tableId: firstOrJoined('tableId'), kind: firstOrJoined('kind'), keyword: firstOrJoined('keyword'),
    sortField: firstOrJoined('sortField'), sortDir: firstOrJoined('sortDir')
}
