{
  "properties": {},
  "id": "c0161af8af91409c925d8ba40123d7c3",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "29.脱敏服务HTTP结果",
  "path": "/serviceHttpPreview",
  "description": "清理HTTP调试结果中的凭据及回显，限制显示深度和大小。",
  "parameters": [
    {"name":"options","description":"请求与响应","required":true,"dataType":"Object","type":"java.lang.Object"}
  ],
  "options": []
}
================================
import cn.hutool.json.JSONUtil
import java.util.Map
import java.util.List
import java.lang.String

// 功能：限制并脱敏服务调试响应，原始响应仅供服务端内部编排使用。
var sensitive = key => (key+'').matches('(?i).*(authorization|token|password|passwd|secret|api.?key|credential|cookie).*')
var secrets = []
var collect
collect = (value,depth) => {
    if (depth > 12 || secrets.size() >= 40) { return }
    if (value instanceof Map) {
        for (key in value.keySet()) {
            var item = value[key]
            if (sensitive(key) && item instanceof String && item.length() > 0 && secrets.size() < 40) {
                secrets.add(item)
                if (item.matches('(?i)^(Bearer|Basic) .+$') && secrets.size() < 40) { secrets.add(item.substring(item.indexOf(' ')+1)) }
            } else { collect(item,depth+1) }
        }
    } else if (value instanceof List) {
        for (item in value) { collect(item,depth+1) }
    }
}
var request = options.request || {}
collect(request.headers,0)
if (request.credential instanceof String && request.credential.length() > 0) { secrets.add(request.credential) }
var payload = request.payload
if (payload instanceof String && JSONUtil.isTypeJSON(payload)) { payload = JSONUtil.parse(payload) }
collect(payload,0)
var clean
clean = (value,depth) => {
    if (depth > 12) { return '[已省略]' }
    if (value instanceof Map) {
        var result = {}
        for (key in value.keySet()) {
            if (result.size() >= 100) { break }
            result[key] = sensitive(key) ? '[已隐藏]' : clean(value[key],depth+1)
        }
        return result
    }
    if (value instanceof List) {
        var result = []
        for (item in value) {
            if (result.size() >= 100) { break }
            result.add(clean(item,depth+1))
        }
        return result
    }
    if (value instanceof String) {
        var output = value
        for (secret in secrets) { output = output.replace(secret,'[已隐藏]') }
        output = output.replaceAll('(?i)(Bearer|Basic)\\s+[^\\s,;]+','$1 [已隐藏]')
        output = output.replaceAll('(?i)((?:password|passwd|token|secret|api.?key|credential)\\s*[:=]\\s*)[^\\s,;&]+','$1[已隐藏]')
        return output.length() > 8192 ? output.substring(0,8192) + '…' : output
    }
    return value
}
var response = options.response
if (response.truncated == true) {
    // 部分JSON不能可靠识别秘密字段，只返回HTTP状态和响应大小提示。
    response.body = '[响应超过64 KiB，已省略正文]'
}
if (response.body instanceof String && ((request.address || '') + '').matches('.*(/portal/login|/idaas/oauth2/token)([?].*)?')) {
    response.body = '[认证响应正文已隐藏]'
}
return clean(response,0)
