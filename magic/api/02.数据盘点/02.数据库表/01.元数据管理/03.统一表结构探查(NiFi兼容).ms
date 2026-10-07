{
  "properties": {},
  "id": "metadata_nifi_tables_probe_01",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "03.统一表结构探查(NiFi兼容)",
  "createTime": 1783639496542,
  "updateTime": 1784437772003,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/tables-probe",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"manifestKey\": \"source.mysql\",\n    \"config\": {\n        \"host\": \"127.0.0.1\",\n        \"port\": 3306,\n        \"database\": \"test\",\n        \"username\": \"root\",\n        \"password\": \"***\"\n    },\n    \"keyword\": \"测试\",\n    \"limit\": 200,\n    \"columns\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "关系型数据库仍复用 NiFi JDBC 元数据能力；API/FTP/SFTP/Kafka 这类非表型数据源在 Magic API 中生成虚拟结构，便于后续按业务规则调整；使用请求参数 config、manifestKey、columns。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.统一表结构探查(NiFi兼容)
// 路由：POST /dst/database/metadata/tables-probe
// 业务说明：关系型数据库仍复用 NiFi JDBC 元数据能力；API/FTP/SFTP/Kafka 这类非表型数据源在 Magic API 中生成虚拟结构，便于后续按业务规则调整；使用请求参数 config、manifestKey、columns。
import com.linewell.dataelement.integrated.nifi.canvas.controller.ConnectionTestController as connectionTestController
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Map
import java.util.List

// 统一表/结构探查。
// 关系型数据库仍复用 NiFi JDBC 元数据能力；API/FTP/SFTP/Kafka 这类非表型数据源在 Magic API 中生成虚拟结构，便于后续按业务规则调整。

var mapper = new ObjectMapper()
var blank = (value) => value == null || String(value).trim() == ''
// 解析请求体并为可选参数设置默认值。
var cfg = body?.config == null ? {} : body.config
var manifestKey = body?.manifestKey == null ? '' : String(body.manifestKey).toLowerCase()

var first = (keys) => {
    for (key in keys) {
        var value = cfg[key]
        if (blank(value) && body != null) {
            value = body[key]
        }
        if (!blank(value)) {
            // 返回当前业务分支的标准结果。
            return value
        }
    }
    // 返回当前业务分支的标准结果。
    return null
}

var parseJson = (text) => {
    if (blank(text)) {
        // 返回当前业务分支的标准结果。
        return null
    }
    try {
        // 返回当前业务分支的标准结果。
        return mapper.readValue(String(text), Object.class)
    } catch (e) {
        // 返回当前业务分支的标准结果。
        return null
    }
}

var scalarType = (value) => {
    if (value == null) { return 'VARCHAR' }
    var s = String(value)
    if (s == 'true' || s == 'false') { return 'BOOLEAN' }
    if (s.matches('^-?\\d+$')) { return 'BIGINT' }
    if (s.matches('^-?\\d+\\.\\d+$')) { return 'DECIMAL' }
    if (s.matches('^\\d{4}-\\d{2}-\\d{2}.*')) { return 'TIMESTAMP' }
    // 返回当前业务分支的标准结果。
    return 'VARCHAR'
}

var countJsonFields = null
countJsonFields = (obj) => {
    if (obj == null) { return 0 }
    if (obj instanceof Map) {
        var total = 0
        for (entry in obj.entrySet()) {
            var child = entry.getValue()
            if (child instanceof Map) {
                var childCount = countJsonFields(child)
                total = total + (childCount == 0 ? 1 : childCount)
            } else if (child instanceof List) {
                if (child.size() > 0) {
                    var childCount2 = countJsonFields(child[0])
                    total = total + (childCount2 == 0 ? 1 : childCount2)
                } else {
                    total = total + 1
                }
            } else {
                total = total + 1
            }
        }
        // 返回当前业务分支的标准结果。
        return total
    }
    if (obj instanceof List && obj.size() > 0) {
        // 返回当前业务分支的标准结果。
        return countJsonFields(obj[0])
    }
    // 返回当前业务分支的标准结果。
    return 1
}

var splitHeader = (text) => {
    if (blank(text)) { return [] }
    var firstLine = String(text).split('\\r?\\n')[0]
    var delimiter = firstLine.indexOf('\t') >= 0 ? '\t' : (firstLine.indexOf('|') >= 0 ? '\\|' : ',')
    var arr = firstLine.split(delimiter)
    var names = []
    for (name in arr) {
        if (!blank(name)) { names.add(String(name).trim()) }
    }
    // 返回当前业务分支的标准结果。
    return names
}

var columnCount = () => {
    if (body?.columns != null) { return body.columns.size() }
    if (cfg.columns != null) { return cfg.columns.size() }
    var jsonSample = first(['responseSample', 'requestBody', 'apiBody', 'sampleMessage', 'kafkaSampleMessage', 'sampleJson'])
    var parsed = parseJson(jsonSample)
    if (parsed != null) { return countJsonFields(parsed) }
    var header = first(['headerRow', 'sampleText', 'fileContent', 'csvSample'])
    // 返回当前业务分支的标准结果。
    return splitHeader(header).size()
}

var virtualTable = (name, comment, kind) => {
    // 返回当前业务分支的标准结果。
    return {
        tableName: name,
        tableComment: comment,
        schemaName: kind,
        tableType: kind,
        metadataKind: kind,
        virtualTable: true,
        fieldCount: columnCount()
    }
}

if (manifestKey == 'source.api' || manifestKey == 'api') {
    var url = first(['apiUrl', 'url'])
    var name = first(['resourceName', 'apiName', 'tableName', 'name'])
    if (blank(name) && !blank(url)) {
        var parts = String(url).split('/')
        name = parts[parts.length - 1]
    }
    if (blank(name)) { name = 'api_payload' }
    var item = virtualTable(name, 'HTTP API 请求/响应数据结构', 'API')
    item.url = url
    item.method = first(['apiMethod', 'method']) == null ? 'GET' : first(['apiMethod', 'method'])
    // 返回当前业务分支的标准结果。
    return { success: true, tables: [item], total: 1, sourceType: 'api' }
}

if (manifestKey == 'source.kafka' || manifestKey == 'kafka') {
    var topic = first(['kafkaTopic', 'topic'])
    if (blank(topic)) { topic = 'kafka_message' }
    var item = virtualTable(topic, 'Kafka Topic 消息结构', 'KAFKA')
    item.bootstrapServers = first(['kafkaBootstrapServers', 'bootstrapServers'])
    // 返回当前业务分支的标准结果。
    return { success: true, tables: [item], total: 1, sourceType: 'kafka' }
}

if (manifestKey == 'source.ftp' || manifestKey == 'ftp' || manifestKey == 'sftp') {
    var protocol = first(['ftpProtocol', 'protocol'])
    if (blank(protocol)) { protocol = 'ftp' }
    var path = first(['ftpPath', 'path', 'fileName'])
    var name = first(['resourceName', 'tableName', 'fileName'])
    if (blank(name) && !blank(path)) {
        var parts = String(path).split('/')
        name = parts[parts.length - 1]
    }
    if (blank(name)) { name = protocol + '_file' }
    var item = virtualTable(name, String(protocol).toUpperCase() + ' 文件结构', String(protocol).toUpperCase())
    item.path = path
    item.host = first(['ftpHost', 'hostname', 'host'])
    // 返回当前业务分支的标准结果。
    return { success: true, tables: [item], total: 1, sourceType: String(protocol).toLowerCase() }
}

var response = connectionTestController.tablesProbe(body)
// 返回当前业务分支的标准结果。
return response.getBody()
