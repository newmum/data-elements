{
  "properties": {},
  "id": "d410778b200d4fcca1d3f6da2a68fbd3",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "37.NiFi画布API来源模板",
  "path": "/nifiApiTemplateConfig",
  "createTime": 1794629895263,
  "updateTime": null,
  "parameters": [
    {
      "name": "datasource",
      "description": "调用方已校验租户归属的 API 来源配置投影",
      "required": true,
      "dataType": "Object",
      "type": "java.util.Map"
    },
    {
      "name": "tableName",
      "description": "调用方已校验的逻辑来源表名称，旧登记可缺省",
      "required": false,
      "dataType": "String",
      "type": "java.lang.String"
    }
  ],
  "options": [],
  "description": "全画布及单节点模板共用的 API 来源配置解析。从已授权来源投影选择逻辑表对应的 API，生成请求、分页、调度和凭据引用，过滤敏感请求头。只处理传入数据，无数据库或网络访问。"
}
================================
import com.fasterxml.jackson.databind.ObjectMapper
import com.linewell.dataelement.integration.nifi.canvas.pipeline.BatchSchedulePolicy
import java.lang.String
import java.util.LinkedHashMap
import java.util.List
import java.util.Locale
import java.util.Map
import java.util.Objects

// @/common/nifiApiTemplateConfig 供 NiFi 全画布及单节点模板生成 API 来源节点配置。
// datasource 和 tableName 来自调用方已按当前租户校验的记录。返回请求、凭据引用、响应抽取和调度配置。
// 只解析传入的登记定义，不读取数据库、不访问来源 API 或 NiFi，也不写入画布。
var mapper = new ObjectMapper()
var isBlank = value => value == null || value.isBlank()
var firstValue = (values, keys) => {
    for (key in keys) {
        var value = values.get(key)
        if (value != null) {
            return value
        }
    }
    return null
}
var firstText = (values, keys) => {
    for (key in keys) {
        var value = Objects.toString(values.get(key), null)
        if (!isBlank(value)) {
            return value
        }
    }
    return null
}
var textOr = (value, fallback) => isBlank(value) ? fallback : value
var copyWithTextKeys = value => {
    var copy = new LinkedHashMap()
    for (key in value.keySet()) {
        copy.put(Objects.toString(key), value.get(key))
    }
    return copy
}
var readObject = value => {
    if (value instanceof Map) {
        return copyWithTextKeys(value)
    }
    if (!(value instanceof String) || value.isBlank()) {
        return {}
    }
    try {
        var parsed = mapper.readValue(value, Map)
        return parsed == null ? {} : parsed
    } catch (error) {
        return {}
    }
}

// 顶层定义优先，旧资源也可从 poolCfg 读取。字符串列表整体解码，已解析列表只选择有效对象。
var rawItems = firstValue(datasource, ['apiPullItems', 'api_pull_items'])
if (rawItems == null) {
    var pool = readObject(firstValue(datasource, ['poolCfg', 'pool_cfg']))
    rawItems = firstValue(pool, ['apiPullItems', 'api_pull_items'])
}
if (rawItems instanceof String) {
    try {
        rawItems = mapper.readerForListOf(Map).readValue(rawItems)
    } catch (error) {
        rawItems = null
    }
}
var definitions = []
if (rawItems instanceof List) {
    for (entry in rawItems) {
        if (entry instanceof Map) {
            definitions.add(copyWithTextKeys(entry))
        }
    }
}

// 同名定义沿用第一项，只有唯一接口时才允许旧登记缺少表名或表名未匹配。
var api = null
if (!isBlank(tableName)) {
    for (definition in definitions) {
        var name = firstText(definition, ['tableName', 'table_name', 'sourceTableName', 'source_table_name'])
        if (tableName.equalsIgnoreCase(name)) {
            api = definition
            break
        }
    }
}
if (api == null) {
    api = definitions.size() == 1 ? definitions[0] : {}
}

var firstJsonText = (node, keys) => {
    for (key in keys) {
        var value = node.get(key)
        if (value != null && !value.asText('').isBlank()) {
            return value.asText()
        }
    }
    return null
}
var addHeader = (headers, name, value) => {
    if (isBlank(name)) {
        return
    }
    var trimmed = name.trim()
    var key = trimmed.toLowerCase(Locale.ROOT)
    // 只保留非敏感请求头，认证由登记的凭据引用提供。
    if (key == 'authorization' || key.contains('token') || key.contains('secret')
        || key.contains('password') || key.contains('api-key') || key.contains('apikey')) {
        return
    }
    headers.put(trimmed, value == null ? '' : value)
}
var readHeaders = raw => {
    var headers = new LinkedHashMap()
    var names = ['key', 'name', 'headerName']
    var values = ['value', 'headerValue']
    // JSON 字符串沿用 Jackson 节点的标量文本规则，已解析 Map 或 List 沿用原值的文本表示。
    if (raw instanceof String) {
        try {
            var parsed = mapper.readTree(raw)
            if (parsed != null && parsed.isObject()) {
                for (name in parsed.fieldNames()) {
                    addHeader(headers, name, parsed.get(name).asText(''))
                }
            } else if (parsed != null && parsed.isArray()) {
                for (row in parsed) {
                    addHeader(headers, firstJsonText(row, names), firstJsonText(row, values))
                }
            }
        } catch (error) {
            return new LinkedHashMap()
        }
    } else if (raw instanceof Map) {
        for (name in raw.keySet()) {
            addHeader(headers, Objects.toString(name), Objects.toString(raw.get(name), ''))
        }
    } else if (raw instanceof List) {
        for (row in raw) {
            if (row instanceof Map) {
                addHeader(headers, firstText(row, names), firstText(row, values))
            }
        }
    }
    return headers
}

var resolveUrl = () => {
    var direct = firstText(api, ['url', 'requestUrl', 'request_url', 'apiUrl', 'api_url', 'endpointUrl', 'endpoint_url'])
    if (!isBlank(direct)) {
        return direct
    }
    var base = firstText(api, ['baseUrl', 'base_url'])
    var path = firstText(api, ['endpointPath', 'endpoint_path', 'path'])
    if (isBlank(base)) {
        return path
    }
    if (isBlank(path)) {
        return base
    }
    return base.replaceAll('/+$', '') + (path.startsWith('/') ? path : '/' + path)
}
var duration = keys => {
    var value = textOr(firstText(api, keys), firstText(datasource, keys))
    if (!isBlank(value)) {
        return value
    }
    var seconds = firstText(datasource, ['apiTimeoutSeconds', 'api_timeout_seconds'])
    return isBlank(seconds) ? null : seconds + ' sec'
}

var headers = readHeaders(firstValue(api, ['requestHeadersJson', 'commonHeadersJson', 'requestHeaders']))
var headerJson = ''
var contentType = textOr(firstText(api, ['contentType', 'content_type', 'requestContentType', 'request_content_type']),
    'application/json')
if (!headers.isEmpty()) {
    try {
        headerJson = mapper.writeValueAsString(headers)
        for (name in headers.keySet()) {
            if ('content-type'.equalsIgnoreCase(name)) {
                contentType = headers.get(name)
                break
            }
        }
    } catch (error) {
        headerJson = ''
    }
}
var url = textOr(resolveUrl(), firstText(datasource, ['apiUrl', 'api_url', 'url']))
var method = textOr(firstText(api, ['method', 'endpointMethod', 'endpoint_method', 'apiMethod', 'api_method']),
    firstText(datasource, ['apiMethod', 'api_method', 'method']))
var requestBody = textOr(firstText(api, ['requestTemplateJson', 'request_template_json', 'requestBody', 'request_body',
    'apiBody', 'api_body']), firstText(datasource, ['apiBody', 'api_body', 'requestBody', 'request_body']))

// 画布、NiFi 编译及字段探查使用不同字段名，生成时写入同一份安全请求配置。
var config = {
    url: url,
    apiUrl: url,
    method: method,
    apiMethod: method,
    contentType: contentType,
    commonHeadersJson: headerJson,
    apiHeaders: headerJson,
    headers: headerJson,
    requestBody: requestBody,
    apiBody: requestBody,
    authType: firstText(api, ['authType', 'auth_type']),
    credentialRef: firstText(api, ['credentialRef', 'credential_ref']),
    connectTimeout: duration(['connectTimeout', 'connect_timeout', 'connectionTimeout']),
    readTimeout: duration(['readTimeout', 'read_timeout']),
    responseExtractPath: firstText(api, ['responseExtractPath', 'response_extract_path']),
    responseConfigJson: firstText(api, ['responseConfigJson', 'response_config_json']),
    paginationConfigJson: firstText(api, ['paginationConfigJson', 'pagination_config_json']),
    incrementalConfigJson: firstText(api, ['incrementalConfigJson', 'incremental_config_json'])
}

// 保存的 Cron 优先，旧定时字段仍能还原；未配置调度时沿用批来源默认策略。
var schedule = readObject(api.get('captureSchedule'))
if (schedule.isEmpty()) {
    schedule = readObject(api.get('capture_schedule'))
}
var mode = textOr(firstText(schedule, ['mode']), firstText(api, ['scheduleMode', 'schedule_mode', 'triggerMode', 'trigger_mode']))
var cron = textOr(firstText(schedule, ['cronExpression', 'cron_expression']), firstText(api, ['cronExpression', 'cron_expression']))
if (isBlank(mode) && isBlank(cron)) {
    return BatchSchedulePolicy.markDefault(config)
}
mode = textOr(mode, 'cron').trim().toLowerCase(Locale.ROOT)
config.batchSchedulePolicy = 'CUSTOM'
if (!isBlank(cron) || mode == 'cron' || mode == 'specified') {
    config.schedulingStrategy = 'CRON_DRIVEN'
    config.schedulingPeriod = textOr(cron, '0 0 9 * * ?')
    config.scheduleMode = 'cron'
} else if (mode == 'interval') {
    var minutes = textOr(firstText(schedule, ['intervalMinutes', 'interval_minutes']),
        firstText(api, ['scheduleIntervalMinutes', 'schedule_interval_minutes']))
    config.schedulingStrategy = 'TIMER_DRIVEN'
    config.schedulingPeriod = textOr(minutes, '60') + ' min'
    config.scheduleMode = 'interval'
} else if (mode == 'time') {
    var time = textOr(firstText(schedule, ['timeOfDay', 'time_of_day']), firstText(api, ['scheduleTimeOfDay', 'schedule_time_of_day']))
    var parts = textOr(time, '09:00').split(':', 2)
    var hour = parts.length > 0 && parts[0].matches('[0-9]{1,2}') ? parts[0] : '9'
    var minute = parts.length > 1 && parts[1].matches('[0-9]{1,2}') ? parts[1] : '0'
    var period = textOr(firstText(schedule, ['timePeriod', 'time_period']), firstText(api, ['scheduleTimePeriod', 'schedule_time_period']))
    var prefix = '0 ' + minute + ' ' + hour
    var expression = prefix + ' * * ?'
    if ('workday'.equalsIgnoreCase(period)) {
        expression = prefix + ' ? * MON-FRI'
    } else if ('weekly'.equalsIgnoreCase(period)) {
        var weekday = textOr(firstText(schedule, ['weekday']), firstText(api, ['scheduleWeekday', 'schedule_weekday']))
        expression = prefix + ' ? * ' + textOr(weekday, 'MON').toUpperCase(Locale.ROOT)
    }
    config.schedulingStrategy = 'CRON_DRIVEN'
    config.schedulingPeriod = expression
    config.scheduleMode = 'time'
} else {
    // 手动或历史未知触发类型由 NiFi 单次运行命令执行。
    config.schedulingStrategy = 'TIMER_DRIVEN'
    config.schedulingPeriod = '0 sec'
    config.scheduleMode = 'once'
}
return config
