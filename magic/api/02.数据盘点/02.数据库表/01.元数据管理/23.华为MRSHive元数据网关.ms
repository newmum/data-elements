{
  "properties": {},
  "id": "mrs_hive_jdbc_debug_23",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "23.华为MRSHive元数据网关",
  "createTime": 1788220800000,
  "updateTime": 1788699901492,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/huaweiMrsHiveJdbcDebug",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"action\": \"jdbcExample\",\n  \"database\": \"Hive 数据库（tables/columns 等动作需要）\",\n  \"table\": \"Hive 表名（表级动作需要）\",\n  \"limit\": 100,\n  \"confirm\": \"EXECUTE_JDBC_EXAMPLE\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 400,\n    \"msg\": \"执行 JDBC 示例需要 confirm=EXECUTE_JDBC_EXAMPLE 才会执行\",\n    \"message\": \"执行 JDBC 示例需要 confirm=EXECUTE_JDBC_EXAMPLE 才会执行\",\n    \"success\": false,\n    \"data\": null,\n    \"timestamp\": 1788699858978,\n    \"error\": {\n        \"code\": \"MAGIC-BUSINESS-400\",\n        \"message\": \"执行 JDBC 示例需要 confirm=EXECUTE_JDBC_EXAMPLE 才会执行\",\n        \"detail\": \"执行 JDBC 示例需要 confirm=EXECUTE_JDBC_EXAMPLE 才会执行\",\n        \"traceId\": \"4EFDAA27E9AE4077\",\n        \"type\": \"BusinessException\",\n        \"method\": \"POST\",\n        \"path\": \"/dst/database/metadata/huaweiMrsHiveJdbcDebug\"\n    }\n}",
  "description": "华为 MRS Hive 统一元数据网关（POST /dst/database/metadata/huaweiMrsHiveJdbcDebug）。登记五步和 NiFi 可使用同一套动作完成连通性、库表字段、建表 DDL、抽样、统计与只读查询。接口只使用服务端 hwhive 连接池；不接受任意写入 SQL。建表、加字段、删表及 JDBC 示例必须携带明确确认值。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": {
    "name": "",
    "value": "",
    "description": "",
    "required": false,
    "dataType": "Object",
    "type": null,
    "defaultValue": null,
    "validateType": "",
    "error": "",
    "expression": "",
    "children": [
      {
        "name": "code",
        "value": "400",
        "description": "",
        "required": false,
        "dataType": "Integer",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "msg",
        "value": "不支持的 action：health | databases | tables | columns | tableDetail | ddl | sample | count | query | createTable | addColumns | dropTable | jdbcExample",
        "description": "",
        "required": false,
        "dataType": "String",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "message",
        "value": "不支持的 action：health | databases | tables | columns | tableDetail | ddl | sample | count | query | createTable | addColumns | dropTable | jdbcExample",
        "description": "",
        "required": false,
        "dataType": "String",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "success",
        "value": "false",
        "description": "",
        "required": false,
        "dataType": "Boolean",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "data",
        "value": "null",
        "description": "",
        "required": false,
        "dataType": "Object",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "timestamp",
        "value": "1788697654787",
        "description": "",
        "required": false,
        "dataType": "Long",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "error",
        "value": "",
        "description": "",
        "required": false,
        "dataType": "Object",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": [
          {
            "name": "code",
            "value": "MAGIC-BUSINESS-400",
            "description": "",
            "required": false,
            "dataType": "String",
            "type": null,
            "defaultValue": null,
            "validateType": "",
            "error": "",
            "expression": "",
            "children": []
          },
          {
            "name": "message",
            "value": "不支持的 action：health | databases | tables | columns | tableDetail | ddl | sample | count | query | createTable | addColumns | dropTable | jdbcExample",
            "description": "",
            "required": false,
            "dataType": "String",
            "type": null,
            "defaultValue": null,
            "validateType": "",
            "error": "",
            "expression": "",
            "children": []
          },
          {
            "name": "detail",
            "value": "不支持的 action：health | databases | tables | columns | tableDetail | ddl | sample | count | query | createTable | addColumns | dropTable | jdbcExample",
            "description": "",
            "required": false,
            "dataType": "String",
            "type": null,
            "defaultValue": null,
            "validateType": "",
            "error": "",
            "expression": "",
            "children": []
          },
          {
            "name": "traceId",
            "value": "6C18B0B4D2A24604",
            "description": "",
            "required": false,
            "dataType": "String",
            "type": null,
            "defaultValue": null,
            "validateType": "",
            "error": "",
            "expression": "",
            "children": []
          },
          {
            "name": "type",
            "value": "BusinessException",
            "description": "",
            "required": false,
            "dataType": "String",
            "type": null,
            "defaultValue": null,
            "validateType": "",
            "error": "",
            "expression": "",
            "children": []
          },
          {
            "name": "method",
            "value": "POST",
            "description": "",
            "required": false,
            "dataType": "String",
            "type": null,
            "defaultValue": null,
            "validateType": "",
            "error": "",
            "expression": "",
            "children": []
          },
          {
            "name": "path",
            "value": "/dst/database/metadata/huaweiMrsHiveJdbcDebug",
            "description": "",
            "required": false,
            "dataType": "String",
            "type": null,
            "defaultValue": null,
            "validateType": "",
            "error": "",
            "expression": "",
            "children": []
          }
        ]
      }
    ]
  }
}
================================
// 接口：23.华为MRSHive元数据网关
// 路由：POST /dst/database/metadata/huaweiMrsHiveJdbcDebug
// 业务说明：华为 MRS Hive 统一元数据网关（POST /dst/database/metadata/huaweiMrsHiveJdbcDebug）。登记五步和 NiFi 可使用同一套动作完成连通性、库表字段、建表 DDL、抽样、统计与只读查询。接口只使用服务端 hwhive 连接池；不接受任意写入 SQL。建表、加字段、删表及 JDBC 示例必须携带明确确认值。
import hwhive
import tenantRuntime
import java.util.Map

/*
 * 元数据管理 23：华为 MRS Hive 统一元数据网关。
 *
 * 登记五步需要的动作：
 *   health、databases、tables、columns、tableDetail、ddl、sample、count、createTable、addColumns
 * NiFi 建模需要的动作：
 *   health、databases、tables、columns、tableDetail、sample、count、query
 *
 * hwhive 是服务端 MRS Hive 连接池模块，连接参数、Kerberos 配置与 keytab 均只从后端部署节点读取。
 * 本接口不接收 JDBC URL、账号、密码、keytab、Kerberos 配置路径，也不暴露任意 DDL/DML 直通执行。
 */

var textOf = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
var tenantId = tenantRuntime.id()
if (blank(tenantId)) {
    exit 401, '当前登录状态已失效，请重新登录后访问华为 MRS Hive 元数据'
}
if (body == null) {
    exit 400, '请求体不能为空'
}

var action = blank(body.action) ? 'health' : textOf(body.action).trim()
var allowedActions = [
    'health', 'databases', 'tables', 'columns', 'tableDetail', 'ddl', 'sample', 'count', 'query',
    'createTable', 'addColumns', 'dropTable', 'jdbcExample'
]
if (allowedActions.indexOf(action) < 0) {
    exit 400, '不支持的 action：' + action
}

var identifier = (value, label) => {
    var normalized = textOf(value).trim()
    if (!normalized.matches('[A-Za-z_][A-Za-z0-9_]*')) {
        exit 400, label + '只能包含字母、数字和下划线，且必须以字母或下划线开头'
    }
    return normalized
}
var quoted = (value, label) => '`' + identifier(value, label) + '`'
var databaseOf = () => identifier(body.database, 'database')
var tableOf = () => identifier(body.table, 'table')
var qualifiedTable = () => quoted(databaseOf(), 'database') + '.' + quoted(tableOf(), 'table')
var limitOf = (fallback, max) => {
    var value = body.limit == null ? fallback : body.limit::int
    if (value < 1) { return 1 }
    return value > max ? max : value
}
var escapeLiteral = (value) => textOf(value).replaceAll("'", "''")
var valuesOf = (rows) => {
    var values = []
    for (row in rows == null ? [] : rows) {
        if (row instanceof Map && !row.isEmpty()) {
            for (entry in row.entrySet()) {
                values.add(entry.getValue())
                break
            }
        }
    }
    return values
}
var requireConfirm = (expected, label) => {
    if (textOf(body.confirm).trim() != expected) {
        exit 400, label + '需要 confirm=' + expected + ' 才会执行'
    }
}
var requireReadSql = (sql) => {
    var statement = textOf(sql).trim()
    if (blank(statement) || statement.indexOf(';') >= 0) {
        exit 400, '只读查询必须是一条非空 SQL，且不能包含分号'
    }
    var upper = statement.toUpperCase()
    if (!(upper.startsWith('SELECT ') || upper.startsWith('WITH ') || upper.startsWith('SHOW ') || upper.startsWith('DESCRIBE ') || upper.startsWith('EXPLAIN '))) {
        exit 400, 'query 仅支持 SELECT、WITH、SHOW、DESCRIBE 或 EXPLAIN'
    }
    var forbidden = ['INSERT ', 'UPDATE ', 'DELETE ', 'MERGE ', 'CREATE ', 'ALTER ', 'DROP ', 'TRUNCATE ', 'GRANT ', 'REVOKE ', 'SET ', 'USE ']
    for (keyword in forbidden) {
        if (upper.indexOf(keyword) >= 0) {
            exit 400, 'query 不能包含写操作关键字：' + keyword.trim()
        }
    }
    return statement
}
var hiveType = (value) => {
    var normalized = textOf(value).trim().toUpperCase().replaceAll('\\s+', ' ')
    if (blank(normalized) || normalized.indexOf(';') >= 0 || normalized.indexOf("'") >= 0
        || normalized.indexOf('"') >= 0 || !normalized.matches('[A-Z][A-Z0-9_() ,<>]*')) {
        exit 400, '字段类型不合法：' + textOf(value)
    }
    return normalized
}
var columnDefinitions = (columns, label) => {
    if (columns == null || columns.size() == 0) {
        exit 400, label + '不能为空'
    }
    var definitions = []
    for (column in columns) {
        var name = column instanceof Map ? column.get('name') : column.name
        var type = column instanceof Map ? column.get('type') : column.type
        var comment = column instanceof Map ? column.get('comment') : column.comment
        var definition = quoted(name, '字段名') + ' ' + hiveType(type)
        if (!blank(comment)) {
            definition = definition + " COMMENT '" + escapeLiteral(comment) + "'"
        }
        definitions.add(definition)
    }
    return definitions
}

// 连通性探测只从 hwhive 的服务端连接池取连接，close() 表示归还连接池。
if (action == 'health') {
    var connection = null
    try {
        connection = hwhive.getConnection()
        return { success: true, tenantId: tenantId, action: action, message: '华为 MRS Hive 连接正常' }
    } finally {
        if (connection != null) { connection.close() }
    }
}

if (action == 'databases') {
    var rows = hwhive.queryTable('SHOW DATABASES')
    return { success: true, action: action, rows: rows, databases: valuesOf(rows) }
}

if (action == 'tables') {
    var database = databaseOf()
    var rows = hwhive.queryTable('SHOW TABLES IN ' + quoted(database, 'database'))
    return { success: true, action: action, database: database, rows: rows, tables: valuesOf(rows) }
}

if (action == 'columns') {
    var database = databaseOf()
    var table = tableOf()
    return {
        success: true,
        action: action,
        database: database,
        table: table,
        columns: hwhive.queryTable('DESCRIBE ' + qualifiedTable())
    }
}

if (action == 'tableDetail') {
    var database = databaseOf()
    var table = tableOf()
    return {
        success: true,
        action: action,
        database: database,
        table: table,
        details: hwhive.queryTable('DESCRIBE FORMATTED ' + qualifiedTable())
    }
}

if (action == 'ddl') {
    var database = databaseOf()
    var table = tableOf()
    return {
        success: true,
        action: action,
        database: database,
        table: table,
        ddl: hwhive.queryTable('SHOW CREATE TABLE ' + qualifiedTable())
    }
}

if (action == 'sample') {
    var database = databaseOf()
    var table = tableOf()
    var limit = limitOf(100, 1000)
    return {
        success: true,
        action: action,
        database: database,
        table: table,
        limit: limit,
        rows: hwhive.queryTable('SELECT * FROM ' + qualifiedTable() + ' LIMIT ' + limit)
    }
}

if (action == 'count') {
    var database = databaseOf()
    var table = tableOf()
    return {
        success: true,
        action: action,
        database: database,
        table: table,
        rows: hwhive.queryTable('SELECT COUNT(*) AS row_count FROM ' + qualifiedTable())
    }
}

if (action == 'query') {
    var sql = requireReadSql(body.sql)
    return { success: true, action: action, rows: hwhive.queryTable(sql) }
}

if (action == 'createTable') {
    var database = databaseOf()
    var table = tableOf()
    requireConfirm('CREATE_TABLE:' + database + '.' + table, '创建 Hive 表')
    var definitions = columnDefinitions(body.columns, 'columns')
    var ddl = 'CREATE TABLE IF NOT EXISTS ' + qualifiedTable() + ' (' + definitions.join(', ') + ')'
    if (body.partitionColumns != null && body.partitionColumns.size() > 0) {
        ddl = ddl + ' PARTITIONED BY (' + columnDefinitions(body.partitionColumns, 'partitionColumns').join(', ') + ')'
    }
    var storedAs = blank(body.storedAs) ? '' : textOf(body.storedAs).trim().toUpperCase()
    if (!blank(storedAs)) {
        if (['ORC', 'PARQUET', 'TEXTFILE', 'AVRO'].indexOf(storedAs) < 0) {
            exit 400, 'storedAs 仅支持 ORC、PARQUET、TEXTFILE 或 AVRO'
        }
        ddl = ddl + ' STORED AS ' + storedAs
    }
    if (!blank(body.tableComment)) {
        ddl = ddl + " COMMENT '" + escapeLiteral(body.tableComment) + "'"
    }
    hwhive.execDDLSql(ddl)
    return { success: true, action: action, database: database, table: table, ddl: ddl }
}

if (action == 'addColumns') {
    var database = databaseOf()
    var table = tableOf()
    requireConfirm('ADD_COLUMNS:' + database + '.' + table, '为 Hive 表增加字段')
    var ddl = 'ALTER TABLE ' + qualifiedTable() + ' ADD COLUMNS (' + columnDefinitions(body.columns, 'columns').join(', ') + ')'
    hwhive.execDDLSql(ddl)
    return { success: true, action: action, database: database, table: table, ddl: ddl }
}

if (action == 'dropTable') {
    var database = databaseOf()
    var table = tableOf()
    requireConfirm('DROP_TABLE:' + database + '.' + table, '删除 Hive 表')
    var ddl = 'DROP TABLE IF EXISTS ' + qualifiedTable()
    hwhive.execDDLSql(ddl)
    return { success: true, action: action, database: database, table: table, ddl: ddl }
}

// 兼容图片中的 JDBC 最小验证流程。默认不会运行，且建表必须由调用方显式确认。
if (action == 'jdbcExample') {
    requireConfirm('EXECUTE_JDBC_EXAMPLE', '执行 JDBC 示例')
    var connection = null
    try {
        connection = hwhive.getConnection()
        hwhive.execDDLSql('CREATE TABLE IF NOT EXISTS employees_info(id INT,name STRING)')
        hwhive.execDMLSql('SELECT COUNT(*) FROM employees_info')
        return { success: true, action: action, message: 'JDBC 示例已执行；已按图片依次完成连接、建表和计数查询' }
    } finally {
        if (connection != null) { connection.close() }
    }
}

exit 400, '未处理的 action：' + action
