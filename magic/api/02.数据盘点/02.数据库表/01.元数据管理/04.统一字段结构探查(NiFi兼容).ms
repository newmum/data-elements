{
  "properties": {},
  "id": "metadata_nifi_columns_probe_01",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "04.统一字段结构探查(NiFi兼容)",
  "createTime": 1783639496542,
  "updateTime": 1784437772005,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/columns-probe",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"manifestKey\": \"source.mysql\",\n    \"config\": {\n        \"host\": \"127.0.0.1\",\n        \"port\": 3306,\n        \"database\": \"test\",\n        \"username\": \"root\",\n        \"password\": \"***\"\n    },\n    \"table\": \"sys_user\",\n    \"columns\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "API/Kafka 支持从 JSON 请求体、响应样例、消息样例推导字段；FTP/SFTP 支持从 CSV/TSV 表头或前端传入 columns 推导字段；使用请求参数 config、manifestKey、columns、table。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.统一字段结构探查(NiFi兼容)
// 路由：POST /dst/database/metadata/columns-probe
// 业务说明：API/Kafka 支持从 JSON 请求体、响应样例、消息样例推导字段；FTP/SFTP 支持从 CSV/TSV 表头或前端传入 columns 推导字段；使用请求参数 config、manifestKey、columns、table。
import com.linewell.dataelement.integrated.nifi.canvas.controller.ConnectionTestController as connectionTestController
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Map
import java.util.List

// 统一字段/结构探查。
// API/Kafka 支持从 JSON 请求体、响应样例、消息样例推导字段；FTP/SFTP 支持从 CSV/TSV 表头或前端传入 columns 推导字段。
// 关系型数据库继续走 NiFi JDBC DatabaseMetaData，覆盖 OB、人大金仓、达梦、高斯、DB2、ClickHouse、GBase、Oscar、HighGo、TDSQL 等驱动型元数据。

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

var guessType = (value) => {
    if (value == null) { return 'VARCHAR' }
    if (value instanceof Map) { return 'OBJECT' }
    if (value instanceof List) { return 'ARRAY' }
    var s = String(value)
    if (s == 'true' || s == 'false') { return 'BOOLEAN' }
    if (s.matches('^-?\\d+$')) { return 'BIGINT' }
    if (s.matches('^-?\\d+\\.\\d+$')) { return 'DECIMAL' }
    if (s.matches('^\\d{4}-\\d{2}-\\d{2}.*')) { return 'TIMESTAMP' }
    // 返回当前业务分支的标准结果。
    return 'VARCHAR'
}

var addColumn = (columns, name, value, comment) => {
    if (blank(name)) { return }
    columns.add({
        columnName: String(name),
        columnComment: blank(comment) ? String(name) : comment,
        dataType: guessType(value),
        typeName: guessType(value),
        nullable: true,
        primaryKey: false,
        ordinalPosition: columns.size() + 1,
        virtualColumn: true
    })
}

var walk = null
walk = (columns, prefix, value) => {
    if (value instanceof Map) {
        for (entry in value.entrySet()) {
            var name = blank(prefix) ? String(entry.getKey()) : prefix + '.' + entry.getKey()
            var child = entry.getValue()
            if (child instanceof Map) {
                walk(columns, name, child)
            } else if (child instanceof List) {
                if (child.size() > 0 && child[0] instanceof Map) {
                    walk(columns, name, child[0])
                } else {
                    addColumn(columns, name, child, name)
                }
            } else {
                addColumn(columns, name, child, name)
            }
        }
    } else if (value instanceof List) {
        if (value.size() > 0) {
            walk(columns, prefix, value[0])
        }
    }
}

var normalizeColumns = (rawColumns) => {
    var columns = []
    if (rawColumns == null) { return columns }
    for (item in rawColumns) {
        if (item instanceof Map) {
            var name = item.columnName == null ? (item.name == null ? item.colName : item.name) : item.columnName
            columns.add({
                columnName: name,
                columnComment: item.columnComment == null ? (item.comment == null ? name : item.comment) : item.columnComment,
                dataType: item.dataType == null ? (item.type == null ? 'VARCHAR' : item.type) : item.dataType,
                typeName: item.typeName == null ? (item.type == null ? 'VARCHAR' : item.type) : item.typeName,
                nullable: item.nullable == null ? true : item.nullable,
                primaryKey: item.primaryKey == null ? false : item.primaryKey,
                ordinalPosition: columns.size() + 1,
                virtualColumn: true
            })
        } else if (!blank(item)) {
            addColumn(columns, String(item), null, String(item))
        }
    }
    // 返回当前业务分支的标准结果。
    return columns
}

var splitHeaderColumns = (text) => {
    var columns = []
    if (blank(text)) { return columns }
    var firstLine = String(text).split('\\r?\\n')[0]
    var delimiter = firstLine.indexOf('\t') >= 0 ? '\t' : (firstLine.indexOf('|') >= 0 ? '\\|' : ',')
    var arr = firstLine.split(delimiter)
    for (name in arr) {
        if (!blank(name)) {
            addColumn(columns, String(name).trim(), null, String(name).trim())
        }
    }
    // 返回当前业务分支的标准结果。
    return columns
}

var nonTableColumns = () => {
    var raw = body?.columns == null ? cfg.columns : body.columns
    var columns = normalizeColumns(raw)
    if (columns.size() > 0) { return columns }

    var sample = first(['responseSample', 'requestBody', 'apiBody', 'sampleMessage', 'kafkaSampleMessage', 'sampleJson'])
    var parsed = parseJson(sample)
    if (parsed != null) {
        walk(columns, '', parsed)
        if (columns.size() > 0) { return columns }
    }

    var header = first(['headerRow', 'sampleText', 'fileContent', 'csvSample'])
    columns = splitHeaderColumns(header)
    // 返回当前业务分支的标准结果。
    return columns
}

var virtualTableName = () => {
    var name = body?.table
    if (!blank(name)) { return name }
    name = first(['tableName', 'resourceName', 'apiName', 'kafkaTopic', 'topic', 'fileName'])
    if (!blank(name)) { return name }
    if (manifestKey == 'source.api' || manifestKey == 'api') { return 'api_payload' }
    if (manifestKey == 'source.kafka' || manifestKey == 'kafka') { return 'kafka_message' }
    if (manifestKey == 'source.ftp' || manifestKey == 'ftp' || manifestKey == 'sftp') { return 'ftp_file' }
    // 返回当前业务分支的标准结果。
    return 'virtual_structure'
}

if (manifestKey == 'source.api' || manifestKey == 'api' || manifestKey == 'source.kafka' || manifestKey == 'kafka' || manifestKey == 'source.ftp' || manifestKey == 'ftp' || manifestKey == 'sftp') {
    var columns = nonTableColumns()
    // 返回当前业务分支的标准结果。
    return {
        success: true,
        table: virtualTableName(),
        columns: columns,
        total: columns.size(),
        sourceType: manifestKey,
        virtualTable: true,
        error: columns.size() == 0 ? '未提供可识别的样例数据或表头；请上传样例文件、填写请求/响应样例，或传入 columns。' : ''
    }
}

var response = connectionTestController.columnsProbe(body)
// 返回当前业务分支的标准结果。
return response.getBody()
