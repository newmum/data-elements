{
  "properties": {},
  "id": "dfd3f3a9d4fa46c8a6f9565bf6948b8a",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "34.NiFi拉取来源配置",
  "path": "/nifiApiSource",
  "createTime": 1794626241066,
  "updateTime": null,
  "parameters": [
    {
      "name": "source",
      "description": "已授权读取的来源记录",
      "required": true,
      "dataType": "Object",
      "type": "java.util.Map"
    },
    {
      "name": "datasourceId",
      "description": "来源标识",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    },
    {
      "name": "tableName",
      "description": "逻辑表名",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    }
  ],
  "options": [],
  "description": "由已校验租户归属的来源记录和逻辑表名解析 NiFi 来源配置，包含拉取地址、分页、调度及凭据引用。"
}
================================
import com.fasterxml.jackson.databind.ObjectMapper
import com.linewell.dataelement.integration.nifi.canvas.pipeline.BatchSchedulePolicy
import java.lang.IllegalArgumentException
import java.lang.Integer
import java.util.List
import java.util.Locale
import java.util.Map
import java.util.Objects

// @/common/nifiApiSource 由已按租户读取的数据源和逻辑表名称解析 NiFi 画布的 API 来源配置。
// 数据源参数仅由接入任务脚本传入。使用原始 pool_cfg 保留历史定义的解释，返回凭据引用。
// 函数不读取数据库、不发送网络请求。
if (datasourceId == null || datasourceId.isBlank()) {
    throw new IllegalArgumentException('数据源标识不能为空')
}
if (tableName == null || tableName.isBlank()) {
    throw new IllegalArgumentException('逻辑表名称不能为空')
}
if (source == null) {
    throw new IllegalArgumentException('数据源不存在或不属于当前租户')
}

var text = (value, fallback) => Objects.toString(value, fallback).trim()
var sourceField = (camel, snake) => {
    var value = source.get(camel)
    return value == null ? source.get(snake) : value
}
var dbType = text(sourceField('dbType', 'db_type'), '').toLowerCase(Locale.ROOT)
var raw = text(sourceField('poolCfg', 'pool_cfg'), '')

// 保留旧记录使用的接入方式判断和错误顺序，再用原 JSON 解析器读取 API 定义。
var accessModeText = raw.replaceAll('\\s', '').toLowerCase(Locale.ROOT)
if (dbType != 'api' || (!accessModeText.contains('"accessmode":"capture"')
    && !accessModeText.contains('"accessmode":"pull"'))) {
    throw new IllegalArgumentException('API 拉取规则仅适用于接入方式为“数据拉取”的 API 数据源')
}
var items
try {
    var pool = new ObjectMapper().readValue(raw, Map)
    items = pool.get('apiPullItems')
} catch (e) {
    throw new IllegalArgumentException('数据源中的 API 拉取配置不是有效 JSON', e)
}
var item = null
if (items instanceof List) {
    for (candidate in items) {
        if (candidate instanceof Map && tableName.equalsIgnoreCase(text(candidate.get('tableName'), ''))) {
            item = candidate
            break
        }
    }
}
if (item == null) {
    throw new IllegalArgumentException('第一步未找到与逻辑表匹配的 API 定义：' + tableName)
}
var baseUrl = text(item.get('baseUrl'), '')
if (baseUrl.isBlank()) {
    throw new IllegalArgumentException('API 定义缺少 Base URL')
}
baseUrl = baseUrl.replaceAll('/+$', '')
var endpointPath = text(item.get('endpointPath'), '')
if (endpointPath.isBlank()) {
    throw new IllegalArgumentException('API 定义缺少接口路径')
}
var protocol = text(item.get('apiProtocol'), 'REST')
var contentType = protocol.equalsIgnoreCase('SOAP') ? 'text/xml' : 'application/json'
var headers = text(item.get('commonHeadersJson'), '')
var normalizedHeaders = headers.toLowerCase(Locale.ROOT)
for (term in ['authorization', 'token', 'api-key', 'password', 'secret']) {
    if (normalizedHeaders.contains(term)) {
        throw new IllegalArgumentException('通用请求头不能保存认证或密钥字段，请使用凭据引用')
    }
}

// 历史定义可能存绝对接口地址或未带斜线的相对路径。保留既有 URL 组装。
var relativePath = endpointPath.startsWith('/') ? endpointPath : '/' + endpointPath
var url = endpointPath.startsWith('http://') || endpointPath.startsWith('https://')
    ? endpointPath : baseUrl + relativePath
var config = {
    url: url,
    method: text(item.get('endpointMethod'), 'GET').toUpperCase(Locale.ROOT),
    apiProtocol: protocol,
    captureScope: text(item.get('captureScope'), 'full'),
    paginationMode: text(item.get('paginationMode'), 'none'),
    contentType: contentType,
    requestBody: text(item.get('requestTemplateJson'), ''),
    commonHeadersJson: headers,
    responseExtractPath: text(item.get('responseExtractPath'), ''),
    responseConfigJson: text(item.get('responseConfigJson'), ''),
    paginationConfigJson: text(item.get('paginationConfigJson'), ''),
    incrementalConfigJson: text(item.get('incrementalConfigJson'), ''),
    runtimeConfigJson: text(item.get('runtimeConfigJson'), ''),
    authType: text(item.get('authType'), 'NONE').toUpperCase(Locale.ROOT),
    credentialRef: text(item.get('credentialRef'), '')
}

// 以登记保存的 Cron、间隔或单次优先，其余走统一的批源默认调度。
var saved = item.get('captureSchedule')
var schedule = saved instanceof Map ? saved : {}
var mode = text(schedule.get('mode'), text(item.get('scheduleMode'), '')).toLowerCase(Locale.ROOT)
var cron = text(schedule.get('cronExpression'), text(item.get('cronExpression'), ''))
if (!cron.isBlank()) {
    config.batchSchedulePolicy = 'CUSTOM'
    config.scheduleMode = 'cron'
    config.schedulingStrategy = 'CRON_DRIVEN'
    config.schedulingPeriod = cron
} else if (mode == 'interval') {
    var minutes = text(schedule.get('intervalMinutes'), text(item.get('scheduleIntervalMinutes'), ''))
    var interval = Integer.parseInt(minutes)
    if (interval < 1 || interval > 10080) {
        throw new IllegalArgumentException('API 抓取间隔必须为 1 至 10080 分钟')
    }
    config.batchSchedulePolicy = 'CUSTOM'
    config.scheduleMode = 'interval'
    config.schedulingStrategy = 'TIMER_DRIVEN'
    config.schedulingPeriod = interval + ' min'
} else if (mode == 'once') {
    config.batchSchedulePolicy = 'CUSTOM'
    config.scheduleMode = 'once'
    config.schedulingStrategy = 'TIMER_DRIVEN'
    config.schedulingPeriod = '0 sec'
} else if (mode == 'time' || 'CRON'.equalsIgnoreCase(text(item.get('triggerMode'), ''))) {
    throw new IllegalArgumentException('定时 API 缺少 Cron 表达式，请重新保存抓取配置')
} else {
    return BatchSchedulePolicy.markDefault(config)
}
return config
