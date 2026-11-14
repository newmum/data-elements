{
  "properties": {},
  "id": "cd24c84c195a4320865914afd5dd4896",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "33.API拉取定义校验",
  "path": "/apiPullDefinitionError",
  "createTime": 1794626241066,
  "updateTime": null,
  "parameters": [
    {
      "name": "definitions",
      "description": "已登记的 API 定义列表",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": [],
  "description": "校验数据源登记与拉取同步的 API 定义列表。返回首个校验错误，通过时返回空值。"
}
================================
import java.net.URI
import java.util.HashSet
import java.util.List
import java.util.Locale
import java.util.Map
import java.util.Objects

// @/common/apiPullDefinitionError 校验已登记的 API 拉取定义，供保存数据源和服务规则同步共用。
// definitions 为 1 至 100 条定义。验证通过返回 null，否则返回首个校验错误。
// 仅检查地址、逻辑表名和凭据边界，不查询数据库、不发送网络请求。
if (!(definitions instanceof List) || definitions.isEmpty() || definitions.size() > 100) {
    return '请登记 1 至 100 条 API'
}

var text = (value, fallback) => Objects.toString(value, fallback).trim()
var tableNames = new HashSet()
var credentialFields = ['authPassword', 'authToken', 'apiKeyValue', 'oauthClientSecret']
var sensitiveHeaderTerms = ['authorization', 'token', 'api-key', 'password', 'secret']

for (item in definitions) {
    if (!(item instanceof Map)) {
        return 'API 定义格式无效'
    }
    var name = text(item.get('tableName'), '')
    if (!name.matches('[A-Za-z][A-Za-z0-9_]{0,95}') || !tableNames.add(name.toLowerCase(Locale.ROOT))) {
        return 'API 数据资源英文名无效或重复'
    }
    if (text(item.get('serviceName'), '').isBlank() || text(item.get('tableComment'), '').isBlank()) {
        return '请填写 API 服务名称和数据表中文名'
    }

    // 沿用 URI 解析行为，基础地址须有 HTTP(S) 主机且不能含用户信息。
    var base
    try {
        base = URI.create(text(item.get('baseUrl'), ''))
    } catch (e) {
        return e.message
    }
    var scheme = text(base.getScheme(), '').toLowerCase(Locale.ROOT)
    var path = text(item.get('endpointPath'), '')
    if (!['http', 'https'].contains(scheme) || base.getHost() == null || base.getUserInfo() != null
        || !path.startsWith('/') || path.startsWith('//')) {
        return '请填写有效的 HTTP(S) API 地址和相对路径'
    }

    // 认证值只由凭据引用提供，保留原请求头文本的大小写无关拦截。
    for (field in credentialFields) {
        if (!text(item.get(field), '').isBlank()) {
            return 'API 登记请使用凭据引用'
        }
    }
    for (field in ['commonHeadersJson', 'requestHeadersJson']) {
        var headers = text(item.get(field), '').toLowerCase(Locale.ROOT)
        for (term in sensitiveHeaderTerms) {
            if (headers.contains(term)) {
                return '通用请求头不能保存认证或密钥字段，请使用凭据引用'
            }
        }
    }
    var auth = text(item.get('authType'), 'NONE').toUpperCase(Locale.ROOT)
    if (auth != 'NONE' && text(item.get('credentialRef'), '').isBlank()) {
        return '启用 API 认证时请填写凭据引用'
    }
}

return null
