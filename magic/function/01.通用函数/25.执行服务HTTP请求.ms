{
  "properties": {},
  "id": "f3a8100dc6474a759ec52ec6d2b1f116",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "25.执行服务HTTP请求",
  "path": "/serviceHttp",
  "description": "在当前租户会话中对部署许可的HTTP地址发起有界请求，供服务登记调试与云梯HTTP节点复用。",
  "parameters": [
    {"name":"options","description":"请求对象","required":true,"dataType":"Object","type":"java.lang.Object"}
  ],
  "options": []
}
================================
import org.springframework.core.env.Environment as environment
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import cn.hutool.json.JSONUtil
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.io.ByteArrayOutputStream
import java.lang.System
import java.lang.String
import java.util.Map
import java.util.List
import tenantRuntime

// 功能：执行服务HTTP请求
// 输入为已授权服务的请求选项。认证字段仅在本次调用中使用。
var text = value => value == null ? '' : value + ''
var tenantId = text(tenantRuntime.id())
if (tenantId == '') { exit 401,'当前租户会话无效' }
if (!(options instanceof Map)) { exit 400,'请求配置必须为对象' }
var address = text(options.address).trim()
var method = text(options.method || 'GET').toUpperCase()
if (!['GET','POST','PUT','PATCH','DELETE','HEAD','OPTIONS'].contains(method)) { exit 400,'请求方式无效' }
if (address == '' || address.length() > 2048) { exit 400,'请填写有效的服务地址' }
var platformBase = URI.create(environment.getProperty('flowserve.http.platform-base-url','http://127.0.0.1:8088'))
var uri
try {
    uri = URI.create(address)
    if (address.startsWith('/') && !address.startsWith('//')) { uri = platformBase.resolve(uri) }
} catch (e) { exit 400,'服务地址格式不正确' }
var scheme = text(uri.getScheme()).toLowerCase()
var host = text(uri.getHost()).toLowerCase()
var rawPath = text(uri.getRawPath())
if (!['http','https'].contains(scheme) || host == '' || uri.getRawUserInfo() != null || uri.getRawFragment() != null
    || !uri.normalize().equals(uri) || rawPath.matches('(?i).*(%25|%2e|%2f|%5c|%3b|;|//).*')) {
    exit 400,'请填写规范的HTTP(S)服务地址'
}
var origin = scheme + '://' + text(uri.getRawAuthority()).toLowerCase()
var platformOrigin = text(platformBase.getScheme()).toLowerCase() + '://' + text(platformBase.getRawAuthority()).toLowerCase()
// 平台同源只允许选定的集成接口，其余地址须由部署配置按租户明确授权。
var builtIn = {
    '/portal/login':'POST',
    '/sym/tenant/current':'GET',
    '/dws/flowserve/gateway/api/list':'POST',
    '/idaas/oauth2/token':'POST',
    '/idaas/api/whoami':'GET'
}
var allowed = origin == platformOrigin && builtIn[rawPath] == method
if (!allowed) {
    var targets = text(environment.getProperty('flowserve.http.allowed-targets.' + tenantId,''))
    for (entry in targets.split(',')) {
        var target = entry.trim()
        if (target != '' && (origin + rawPath == target || (target.endsWith('/') && (origin + rawPath).startsWith(target)))) {
            allowed = true
        }
    }
}
if (!allowed) { exit 403,'此地址不在当前租户的服务调用范围内' }
var query = options.query == null ? {} : options.query
var headers = options.headers == null ? {} : options.headers
if (!(query instanceof Map) || !(headers instanceof Map) || query.size() > 100 || headers.size() > 40) {
    exit 400,'请求参数或请求头的格式或数量无效'
}
var url = new URIBuilder(uri)
var sensitive = key => text(key).matches('(?i).*(authorization|token|password|passwd|secret|api.?key|credential|cookie).*')
for (pair in url.getQueryParams()) {
    if (sensitive(pair.getName())) { exit 400,'请将认证信息放在请求头或请求体中' }
}
for (key in query.keySet()) {
    if (sensitive(key)) { exit 400,'请将认证信息放在请求头或请求体中' }
    var value = query[key]
    if (value instanceof List) {
        if (value.size() > 20) { exit 400,'同名查询参数最多20项' }
        for (item in value) { url.addParameter(text(key),text(item)) }
    } else { url.addParameter(text(key),text(value)) }
}
if (url.toString().length() > 8192) { exit 413,'请求地址和查询参数超限' }
var builder = RequestBuilder.create(method).setUri(url.build())
var contentType = ''
for (key in headers.keySet()) {
    var name = text(key)
    var value = text(headers[key])
    if (!name.matches('[A-Za-z0-9_-]{1,80}') || value.length() > 8192 || value.contains('\r') || value.contains('\n')
        || ['host','connection','content-length','transfer-encoding','upgrade','proxy-authorization','forwarded','x-forwarded-host','cookie'].contains(name.toLowerCase())) {
        exit 400,'请求头包含不支持的名称或内容'
    }
    if (name.toLowerCase() == 'content-type') { contentType = value }
    builder.setHeader(name,value)
}
var body = options.payload
if (body != null && !['GET','HEAD'].contains(method)) {
    var value = body instanceof String ? body : JSONUtil.toJsonStr(body)
    var bytes = value.getBytes(StandardCharsets.UTF_8)
    if (bytes.length > 65536) { exit 413,'请求体最多64 KiB' }
    builder.setEntity(new StringEntity(value,StandardCharsets.UTF_8))
    if (contentType == '') { builder.setHeader('Content-Type','application/json; charset=UTF-8') }
}
var config = RequestConfig.custom().setConnectTimeout(4000).setConnectionRequestTimeout(4000).setSocketTimeout(5000).build()
var started = System.currentTimeMillis()
var client = HttpClients.custom().setDefaultRequestConfig(config).disableRedirectHandling()
    .disableAutomaticRetries().disableCookieManagement().build()
var reply = null
try {
    reply = client.execute(builder.build())
    var status = reply.getStatusLine().getStatusCode()
    var bodyBytes = new ByteArrayOutputStream()
    var entity = reply.getEntity()
    var truncated = false
    if (entity != null) {
        var stream = entity.getContent()
        var buffer = ByteBuffer.allocate(4096).array()
        var count = stream.read(buffer)
        while (count > 0) {
            if (System.currentTimeMillis() - started > 12000) {
                return {ok:false,kind:'timeout',status:status,durationMs:System.currentTimeMillis()-started}
            }
            var remaining = 65536 - bodyBytes.size()
            if (remaining < count) {
                if (remaining > 0) { bodyBytes.write(buffer,0,remaining) }
                truncated = true
                break
            }
            bodyBytes.write(buffer,0,count)
            count = stream.read(buffer)
        }
    }
    var raw = new String(bodyBytes.toByteArray(),StandardCharsets.UTF_8)
    var data = raw
    if (!truncated && JSONUtil.isTypeJSON(raw)) {
        try { data = JSONUtil.parse(raw) } catch (e) { data = raw }
    }
    var businessCode = data instanceof Map ? data['code'] : null
    var businessOk = !(data instanceof Map) || (data['success'] != false
        && (businessCode == null || ['0','1','200'].contains(text(businessCode))))
    var result = {ok:status >= 200 && status < 300 && businessOk,kind:'response',status:status,
        businessCode:businessCode,durationMs:System.currentTimeMillis()-started,truncated:truncated,headers:{},body:data}
    for (name in ['Content-Type','Content-Length','Date','Retry-After']) {
        var header = reply.getFirstHeader(name)
        if (header != null) { result.headers[name] = header.getValue() }
    }
    return result
} catch (e) {
    var timedOut = text(e.getClass().getSimpleName()).contains('Timeout')
    return {ok:false,kind:timedOut?'timeout':'network',durationMs:System.currentTimeMillis()-started}
} finally {
    if (reply != null) { reply.close() }
    client.close()
}
