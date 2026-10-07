{
  "properties": {},
  "id": "metadata_remote_table_data_01",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "19.远程表数据操作",
  "createTime": 1784419200000,
  "updateTime": 1784437772101,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/remote-table/data",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNo\": 1,\n    \"pageSize\": 20,\n    \"sortField\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "根据数据源连接、表名和 action 执行远程表数据查询或写入；校验批次、关键字段及目标库类型后返回操作结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：19.远程表数据操作
// 路由：POST /dst/database/metadata/remote-table/data
// 业务说明：根据数据源连接、表名和 action 执行远程表数据查询或写入；校验批次、关键字段及目标库类型后返回操作结果。
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig
import com.linewell.dataelement.metautil.model.dto.PageQueryRequest
import com.linewell.dataelement.metautil.model.dto.PageQuerySort
import com.linewell.dataelement.metautil.model.enums.DatabaseType
import com.linewell.dataelement.metautil.service.MetadataDataQueryService as metadataDataQueryService
import com.linewell.dataelement.metautil.service.RemoteTableDataService as remoteTableDataService

/**
 * 连接参数兼容旧文件资源接口中的 user/dbType 命名，也接受标准
 * username/databaseType 命名。密码只用于本次 JDBC 调用，不写入日志。
 */
var connection = body?.connection || body?.dataSource || {}
var rawType = (body?.dbType || connection?.dbType || connection?.databaseType || "").toString().toLowerCase()
var typeNames = {
    mysql: "MYSQL",
    postgresql: "POSTGRESQL",
    postgres: "POSTGRESQL",
    oracle: "ORACLE",
    dameng: "DAMENG",
    dm: "DAMENG",
    gaussdb: "GAUSSDB",
    kingbase: "KINGBASE",
    hive: "HIVE",
    oceanbase_mysql: "OCEANBASE_MYSQL",
    oceanbase_oracle: "OCEANBASE_ORACLE"
}
var typeName = typeNames[rawType]
if (!typeName) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, "不支持的数据库类型: " + rawType
}

var config = new DataSourceConfig()
config.setDatabaseType(DatabaseType.valueOf(typeName))
config.setHost(connection?.host)
config.setPort(connection?.port == null ? null : connection.port.toString()::int())
config.setDatabase(connection?.database || connection?.serviceName)
config.setSchema(connection?.schema)
config.setUsername(connection?.username || connection?.user)
config.setPassword(connection?.password)
// Reuse the exact JDBC URL entered by the user, including Oracle connection mode.
config.setJdbcUrl(connection?.jdbcURL || connection?.jdbcUrl)
config.setExtraParams(connection?.extraParams)

if (!config.host || !config.username || config.password == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, "远程数据库 host、username/user、password 不能为空"
}

var action = body?.action
var tableName = body?.tableName

if (action == "count") {
    // 返回当前业务分支的标准结果。
    return remoteTableDataService.count(config, tableName)
}

if (action == "listTables") {
    // 返回当前业务分支的标准结果。
    return remoteTableDataService.listTables(config)
}

if (action == "batchInsert") {
    // 返回当前业务分支的标准结果。
    return remoteTableDataService.batchInsert(config, tableName, body?.rows || [], body?.batchSize || 500)
}

if (action == "batchResetInsert") {
    var rows = body?.rows || []
    var key = body?.key
    if (!key) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 500, "batchResetInsert 操作的 key 不能为空"
    }
    var values = []
    rows.each(row => {
        if (row[key] != null) {
            values.add(row[key])
        }
    })
    if (values.size() > 0) {
        remoteTableDataService.deleteByValues(config, tableName, key, values)
    }
    // 返回当前业务分支的标准结果。
    return remoteTableDataService.batchInsert(config, tableName, rows, body?.batchSize || 500)
}

if (action == "deleteByValues") {
    var values = body?.values
    if (values == null) {
        values = [body?.value]
    }
    // 返回当前业务分支的标准结果。
    return remoteTableDataService.deleteByValues(config, tableName, body?.key, values)
}

if (action == "deleteBefore") {
    // 返回当前业务分支的标准结果。
    return remoteTableDataService.deleteBefore(config, tableName, body?.key, body?.value)
}

if (action == "deleteAll") {
    // 返回当前业务分支的标准结果。
    return remoteTableDataService.deleteAll(config, body?.tableNames || [])
}

if (action == "preview") {
    var request = new PageQueryRequest()
    request.setDataSource(config)
    request.setTableName(tableName)
    request.setPageNo(body?.pageNo == null ? 1 : body.pageNo.toString()::int())
    request.setPageSize(body?.pageSize == null ? 20 : body.pageSize.toString()::int())
    if (body?.sortField) {
        var sort = new PageQuerySort()
        sort.setField(body.sortField)
        sort.setDirection(body?.sortDirection || "DESC")
        request.setSorts([sort])
    }
    // 返回当前业务分支的标准结果。
    return metadataDataQueryService.queryPage(request)
}

// 参数或业务状态不满足要求时立即终止请求。
exit 500, "不支持的远程表操作: " + action
